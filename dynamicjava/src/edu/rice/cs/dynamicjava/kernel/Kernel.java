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

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.UUID;

public class Kernel {
  private final UUID session;

  private final Thread heartbeat;
  private final Thread executor;
  private final Thread control;

  public Kernel(String configPath, Interpreter i) throws FileNotFoundException {
    session = UUID.randomUUID();

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
    executor = new Executor(i, session, ctx, shellAddr, iopubAddr);

    String controlAddr = String.format("%s://%s:%d", transport, ip, config.getInt("control_port"));
    control = new Control(ctx, controlAddr);

//    stdin = ctx.createSocket(ZMQ.ROUTER);
//    stdin.bind(String.format("%s://%s:%d", transport, ip, config.getInt("stdin_port")));
//
//    iopub = ctx.createSocket(ZMQ.PUB);
//    iopub.bind();
  }

  private static class Heartbeat extends Thread {
    private final ZContext ctx;
    private final ZMQ.Socket skt;

    public Heartbeat(ZContext ctx, String addr) {
      this.ctx = ctx;
      skt = ctx.createSocket(ZMQ.REP);
      skt.bind(addr);
    }

    @Override
    public void run() {
      while (true) {
        byte[] msg = skt.recv();
        skt.send(msg);
      }
    }
  }

  private static class Executor extends Thread {
    private final Interpreter i;
    private final UUID session;
    private final ZMQ.Socket shellSkt;
    private final ZMQ.Socket iopubSkt;

    private int execCount = 1;

    public Executor(Interpreter i, UUID session, ZContext ctx, String shellAddr, String iopubAddr) {
      this.i = i;
      this.session = session;

      shellSkt = ctx.createSocket(ZMQ.ROUTER);
      shellSkt.bind(shellAddr);
      iopubSkt = ctx.createSocket(ZMQ.PUB);
      iopubSkt.bind(iopubAddr);
    }

    @Override
    public void run() {
      while (true) {
        Message msg = new Message(ZMsg.recvMsg(shellSkt));

        String msgType = msg.getHeader().getString("msg_type");
        JSONObject msgContent = msg.getContent();

        if (msgType.equals("kernel_info_request")) {
          JSONObject content = new JSONObject();
          content.put("protocol_version", new JSONArray("[4,1]"));
          content.put("language", "java");
          content.put("language_version", new JSONArray("[1,7]"));
          msg.respond(session, "kernel_info_reply", content).serialize().send(shellSkt);
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
            Option<Object> result = i.interpret(code);
            String resultStr = result.apply(new OptionVisitor<Object, String>() {
              public String forSome(Object o) { return TextUtil.toString(o); }
              public String forNone() { return ""; }
            });

            // Broadcast execution result as a string.
            retContent = new JSONObject();
            retContent.put("execution_count", execCount);
            JSONObject data = new JSONObject();
            data.put("text/plain", resultStr);
            retContent.put("data", data);
            retContent.put("metadata", new JSONObject());
            msg.respond(session, "pyout", retContent).serialize().send(iopubSkt);

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
            System.out.println(e.toString());

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
        } else {
          System.out.format("ERROR: Unknown message type: %s\n", msgType);
        }
      }
    }
  }

  private static class Control extends Thread {
    private final ZContext ctx;
    private final ZMQ.Socket skt;

    public Control(ZContext ctx, String addr) {
      this.ctx = ctx;
      skt = ctx.createSocket(ZMQ.ROUTER);
      skt.bind(addr);
    }

    @Override
    public void run() {
      Message msg = new Message(ZMsg.recvMsg(skt));
      System.out.println(msg.toString());
    }
  }

  public void start() {
    heartbeat.start();
    executor.start();
    control.start();
  }
}
