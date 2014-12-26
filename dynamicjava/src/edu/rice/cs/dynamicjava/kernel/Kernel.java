package edu.rice.cs.dynamicjava.kernel;

import edu.rice.cs.dynamicjava.interpreter.Interpreter;
import edu.rice.cs.dynamicjava.interpreter.InterpreterException;
import edu.rice.cs.plt.text.TextUtil;
import edu.rice.cs.plt.tuple.Option;
import edu.rice.cs.plt.tuple.OptionVisitor;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.zeromq.ZMQ;
import org.zeromq.ZContext;
import org.zeromq.ZMsg;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.PrintStream;
import java.net.URLClassLoader;
import java.util.UUID;
import java.util.concurrent.*;

public class Kernel {
  private final UUID session;

  private final IKernelRunnable heartbeat;
  private final IKernelRunnable executor;
  private final Thread control;

  private final ByteArrayOutputStream out;
  private final ByteArrayOutputStream err;

  public Kernel(String configPath, Interpreter i) throws FileNotFoundException {
    session = UUID.randomUUID();
    out = new ByteArrayOutputStream();
    err = new ByteArrayOutputStream();

    // Open JSON config file passed to kernel.
    JSONObject config = new JSONObject(new JSONTokener(new FileReader(configPath)));
    String transport = config.getString("transport");
    String ip = config.getString("ip");

    // Initialize ZMQ.
    ZContext ctx = new ZContext();

    // Bind ZMQ ports.
    String hbAddr = String.format("%s://%s:%d", transport, ip, config.getInt("hb_port"));
    heartbeat = new Heartbeat(ctx, hbAddr);

    String shellAddr = String.format("%s://%s:%d", transport, ip, config.getInt("shell_port"));
    String iopubAddr = String.format("%s://%s:%d", transport, ip, config.getInt("iopub_port"));
    executor = new Executor(i, session, ctx, shellAddr, iopubAddr, out, err);

    String controlAddr = String.format("%s://%s:%d", transport, ip, config.getInt("control_port"));
    control = new Control(heartbeat, executor, session, ctx, controlAddr);
  }

  private static class Heartbeat extends Thread implements IKernelRunnable {
    private final ZMQ.Socket skt;
    private volatile boolean isRunning = true;

    public Heartbeat(ZContext ctx, String addr) {
      skt = ctx.createSocket(ZMQ.REP);
      skt.bind(addr);
    }

    public void shutdown() {
      isRunning = false;
    }

    @Override
    public void run() {
      while (isRunning) {
        byte[] msg = skt.recv();
        skt.send(msg, msg.length);
      }
    }
  }

  private static class Executor extends Thread implements IKernelRunnable {
    private final Interpreter interpreter;
    private final UUID session;
    private final ZMQ.Socket shellSkt;
    private final ZMQ.Socket iopubSkt;
    private final ByteArrayOutputStream out;
    private final ByteArrayOutputStream err;
    private Future<Autocompleter> autocompleter;

    private int execCount = 1;
    private volatile boolean isRunning = true;

    public Executor(Interpreter i, UUID session, ZContext ctx, String shellAddr, String iopubAddr,
                    ByteArrayOutputStream out, ByteArrayOutputStream err) {
      this.interpreter = i;
      this.session = session;
      this.out = out;
      this.err = err;

      shellSkt = ctx.createSocket(ZMQ.ROUTER);
      shellSkt.bind(shellAddr);
      iopubSkt = ctx.createSocket(ZMQ.PUB);
      iopubSkt.bind(iopubAddr);

      // Creating the autocompleter takes a long time, so do that asynchronously.
      ExecutorService executor = Executors.newSingleThreadExecutor();
      autocompleter = executor.submit(new Callable<Autocompleter>() {
        @Override
        public Autocompleter call() throws Exception {
          return new Autocompleter(interpreter);
        }
      });
      executor.shutdown();
    }

    public void shutdown() {
      isRunning = false;
    }


    @Override
    public void run() {
      while (isRunning) {
        Message msg = new Message(ZMsg.recvMsg(shellSkt));

        String msgType = msg.getHeader().getString("msg_type");
        JSONObject msgContent = msg.getContent();

        if (msgType.equals("kernel_info_request")) {
          JSONObject content = new JSONObject();
          content.put("protocol_version", new JSONArray("[4,1]"));
          content.put("language", "java");
          content.put("language_version", new JSONArray("[1,7]"));
          msg.respond(session, "kernel_info_reply", content).serialize().send(shellSkt);
        } else if (msgType.equals("complete_request")) {
          String query = msgContent.getString("text");

          String[] results = {};
          if (autocompleter.isDone()) {
            try {
              results = autocompleter.get().complete(query);
            } catch (InterruptedException e) {
              e.printStackTrace();
            } catch (ExecutionException e) {
              e.printStackTrace();
            }
          }

          JSONObject retContent = new JSONObject();
          retContent.put("matches", new JSONArray(results));
          retContent.put("status", "ok");
          retContent.put("matched_text", query);

          msg.respond(session, "complete_reply", retContent).serialize().send(shellSkt);
        } else if (msgType.equals("execute_request")) {
          // Send a kernel status message that the kernel is busy executing.
          JSONObject retContent = new JSONObject();
          retContent.put("execution_state", "busy");
          msg.respond(session, "status", retContent).serialize().send(iopubSkt);

          String code = msgContent.getString("code");

          // Rebroadcast the code that is being executed.
          retContent = new JSONObject();
          retContent.put("code", code);
          retContent.put("execution_count", execCount);
          msg.respond(session, "pyin", retContent).serialize().send(iopubSkt);

          // Begin execution.
          try {
            Option<Object> result = interpreter.interpret(code);

            // Broadcast stdout and stderr.
            retContent = new JSONObject();
            retContent.put("name", "stdout");
            retContent.put("data", out.toString());
            out.reset();
            msg.respond(session, "stream", retContent).serialize().send(iopubSkt);

            retContent = new JSONObject();
            retContent.put("name", "stderr");
            retContent.put("data", err.toString());
            err.reset();
            msg.respond(session, "stream", retContent).serialize().send(iopubSkt);

            // Broadcast execution result as a string.
            if (result.isSome()) {
              String resultStr = result.apply(new OptionVisitor<Object, String>() {
                public String forSome(Object o) {
                  return TextUtil.toString(o);
                }

                public String forNone() {
                  return "";
                }
              });

              retContent = new JSONObject();
              retContent.put("execution_count", execCount);
              JSONObject data = new JSONObject();
              data.put("text/plain", resultStr);
              retContent.put("data", data);
              retContent.put("metadata", new JSONObject());
              msg.respond(session, "pyout", retContent).serialize().send(iopubSkt);
            }

            // Send a kernel status message that the kernel is now idle.
            retContent = new JSONObject();
            retContent.put("execution_state", "idle");
            msg.respond(session, "status", retContent).serialize().send(iopubSkt);

            // Send a reply message to the execution request.
            retContent = new JSONObject();
            retContent.put("execution_count", execCount);
            retContent.put("status", "ok");
            retContent.put("payload", new JSONArray());
            retContent.put("user_variables", new JSONObject());
            retContent.put("user_expressions", new JSONObject());
            msg.respond(session, "execute_reply", retContent).serialize().send(shellSkt);
          } catch (InterpreterException e) {
            // Send a kernel status message that the kernel is now idle.
            retContent = new JSONObject();
            retContent.put("execution_state", "idle");
            msg.respond(session, "status", retContent).serialize().send(iopubSkt);

            // Send a reply message to the execution request.
            retContent = new JSONObject();
            retContent.put("execution_count", execCount);
            retContent.put("status", "error");
            retContent.put("ename", e.getClass().getSimpleName());
            retContent.put("evalue", e.getUserMessage());
            JSONArray traceback = new JSONArray();
            for (StackTraceElement te : e.getStackTrace()) {
              traceback.put(te.toString());
            }
            retContent.put("traceback", traceback);
            msg.respond(session, "execute_reply", retContent).serialize().send(shellSkt);
          }
          execCount += 1;
        } else if (msgType.equals("shutdown_request")) {
        } else {
          System.out.format("ERROR: Unknown message type: %s\n", msgType);
        }
      }
    }
  }

  private static class Control extends Thread {
    private final UUID session;
    private final ZMQ.Socket skt;
    private final IKernelRunnable heartbeat;
    private final IKernelRunnable executor;

    public Control(IKernelRunnable heartbeat, IKernelRunnable executor, UUID session, ZContext ctx, String addr) {
      this.heartbeat = heartbeat;
      this.executor = executor;
      this.session = session;
      skt = ctx.createSocket(ZMQ.ROUTER);
      skt.bind(addr);
    }

    @Override
    public void run() {
      while (true) {
        Message msg = new Message(ZMsg.recvMsg(skt));
        String msgType = msg.getHeader().getString("msg_type");
        if (msgType.equals("shutdown_request")) {
          executor.shutdown();
          heartbeat.shutdown();

          JSONObject retContent = new JSONObject();
          retContent.put("restart", "false");
          msg.respond(session, "shutdown_reply", retContent).serialize().send(skt);
          return;
        }
      }
    }
  }

  public void start() {
    heartbeat.start();
    executor.start();
    control.start();

    // Replace System.out, System.err with custom streams so that we can capture their output.
    PrintStream kernelOut = new PrintStream(out, true);
    PrintStream kernelErr = new PrintStream(err, true);
    System.setOut(kernelOut);
    System.setErr(kernelErr);
  }
}
