package com.simple.messenger.core;

import java.util.Map;

public class Message {
    public String id;
    public long seq;
    public String from;
    public String fromName;
    public String to;
    public String text;
    public long ts;

    public static Message from(Map<String, Object> m) {
        Message x = new Message();
        x.id = Json.str(m, "id");
        x.seq = Json.num(m, "seq");
        x.from = Json.str(m, "from");
        x.fromName = Json.str(m, "fromName");
        x.to = Json.str(m, "to");
        x.text = Json.str(m, "text");
        x.ts = Json.num(m, "ts");
        return x;
    }
}
