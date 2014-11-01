package edu.rice.cs.dynamicjava.kernel;

import org.json.JSONObject;
import org.json.JSONTokener;
import org.zeromq.ZFrame;
import org.zeromq.ZMsg;

import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.TimeZone;
import java.util.UUID;

public class Message {
  private static byte[] delimiter = { '<','I','D','S','|','M','S','G','>' };

  private LinkedList<byte[]> prefix;
  private JSONObject header;
  private JSONObject parent_header;
  private JSONObject metadata;
  private JSONObject content;

  public Message(LinkedList<byte[]> prefix,
                 JSONObject header,
                 JSONObject parentHeader,
                 JSONObject metadata,
                 JSONObject content) {
    this.prefix = prefix;
    this.header = header;
    parent_header = parentHeader;
    this.metadata = metadata;
    this.content = content;
  }

  public Message(ZMsg msg) {
    // Pop the parts of the message up to the message delimiter and save them in the prefix list.
    prefix = new LinkedList<byte[]>();
    while (true) {
      ZFrame identifier = msg.pop();
      if (identifier.streq("<IDS|MSG>")) {
        break;
      } else {
        prefix.add(identifier.getData());
      }
    }

    // Pop HMAC.
    msg.pop();

    header = new JSONObject(new JSONTokener(msg.popString()));
    parent_header = new JSONObject(new JSONTokener(msg.popString()));
    metadata = new JSONObject(new JSONTokener(msg.popString()));
    content = new JSONObject(new JSONTokener(msg.popString()));
  }

  public Message respond(UUID session, String msgType, JSONObject content) {
    JSONObject newHeader = new JSONObject();

    TimeZone tz = TimeZone.getTimeZone("UTC");
    DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
    df.setTimeZone(tz);
    String nowAsISO = df.format(new Date());

    newHeader.put("date", nowAsISO);
    newHeader.put("msg_id", UUID.randomUUID().toString());
    newHeader.put("username", System.getProperty("user.name"));
    newHeader.put("session", session.toString());
    newHeader.put("msg_type", msgType);
    return new Message(prefix, newHeader, header, metadata, content);
  }

  public ZMsg serialize() {
    ZMsg msg = new ZMsg();
    Charset utf8 = Charset.forName("UTF-8");
    for (byte[] identifier : prefix) {
      msg.push(identifier);
    }
    msg.add(delimiter);
    msg.add("".getBytes(utf8));
    msg.add(header.toString().getBytes(utf8));
    msg.add(parent_header.toString().getBytes(utf8));
    msg.add(metadata.toString().getBytes(utf8));
    msg.add(content.toString().getBytes(utf8));
    return msg;
  }

  @Override
  public String toString() {
    return String.format("{\n'header': %s\n'parent_header': %s\n'metadata': %s\n'content': %s\n}",
      getHeader().toString(), getParentHeader().toString(), getMetadata().toString(), getContent().toString());
  }

  public JSONObject getHeader() {
    return header;
  }

  public JSONObject getParentHeader() {
    return parent_header;
  }

  public JSONObject getMetadata() {
    return metadata;
  }

  public JSONObject getContent() {
    return content;
  }
}
