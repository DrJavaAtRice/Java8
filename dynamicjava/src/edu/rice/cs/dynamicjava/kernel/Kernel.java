package edu.rice.cs.dynamicjava.kernel;

import edu.rice.cs.dynamicjava.interpreter.Interpreter;
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
  private UUID session;

  private Thread heartbeat;
  private Thread shell;
  private Thread control;

  private Interpreter interpreter;

  public Kernel(String configPath, Interpreter i) throws FileNotFoundException {
    session = UUID.randomUUID();
    interpreter = i;

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
    shell = new Shell(session, ctx, shellAddr);

    String controlAddr = String.format("%s://%s:%d", transport, ip, config.getInt("control_port"));
    control = new Control(ctx, controlAddr);

//    stdin = ctx.createSocket(ZMQ.ROUTER);
//    stdin.bind(String.format("%s://%s:%d", transport, ip, config.getInt("stdin_port")));
//
//    iopub = ctx.createSocket(ZMQ.PUB);
//    iopub.bind(String.format("%s://%s:%d", transport, ip, config.getInt("iopub_port")));
  }

  private static class Heartbeat extends Thread {
    private ZContext ctx;
    private ZMQ.Socket skt;

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

  private static class Shell extends Thread {
    private UUID session;
    private ZContext ctx;
    private ZMQ.Socket skt;

    public Shell(UUID session, ZContext ctx, String addr) {
      this.session = session;
      this.ctx = ctx;
      skt = ctx.createSocket(ZMQ.ROUTER);
      skt.bind(addr);
    }

    @Override
    public void run() {
      while (true) {
        Message msg = new Message(ZMsg.recvMsg(skt));
        System.out.println(msg.toString());

        String msgType = msg.getHeader().getString("msg_type");
        Message retMsg;

        if (msgType.equals("kernel_info_request")) {
          JSONObject content = new JSONObject();
          content.put("protocol_version", new JSONArray("[4,1]"));
          content.put("language", "java");
          content.put("language_version", new JSONArray("[1,7]"));
          retMsg = msg.respond(session, "kernel_info_reply", content);
        } else {
          retMsg = msg;
        }

        System.out.println(retMsg.toString());
        retMsg.serialize().send(skt);
      }
    }
  }

  private static class Control extends Thread {
    private ZContext ctx;
    private ZMQ.Socket skt;

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
    shell.start();
    control.start();
  }
}
