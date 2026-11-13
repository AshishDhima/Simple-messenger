package com.simple.messenger.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Talks to the Simple Messenger server over plain HTTP. */
public class Api {

    /** The server answered with an error message. */
    public static class ApiException extends IOException {
        public final int status;

        public ApiException(int status, String message) {
            super(message);
            this.status = status;
        }
    }

    private final String base;
    private final String token;

    public Api(String base, String token) {
        this.base = normalize(base);
        this.token = token;
    }

    /** Accepts "192.168.1.5:3000" as well as "http://192.168.1.5:3000/". */
    public static String normalize(String address) {
        String a = address.trim();
        if (!a.startsWith("http://") && !a.startsWith("https://")) a = "http://" + a;
        while (a.endsWith("/")) a = a.substring(0, a.length() - 1);
        return a;
    }

    /** Signs in, or creates the account if the name is new. */
    public Map<String, Object> join(String name, String password) throws IOException {
        return Json.obj(request("POST", "/api/join", Json.object("name", name, "password", password), 15000));
    }

    public Message send(String to, String text) throws IOException {
        return Message.from(Json.obj(request("POST", "/api/messages", Json.object("to", to, "text", text), 15000)));
    }

    public Map<String, Object> updates(long since, int waitSeconds) throws IOException {
        return Json.obj(request("GET", "/api/updates?since=" + since + "&wait=" + waitSeconds, null, (waitSeconds + 15) * 1000));
    }

    private Object request(String method, String path, String body, int readTimeoutMs) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        try {
            c.setRequestMethod(method);
            c.setConnectTimeout(8000);
            c.setReadTimeout(readTimeoutMs);
            c.setUseCaches(false);
            c.setRequestProperty("Accept", "application/json");
            if (token != null) c.setRequestProperty("Authorization", "Bearer " + token);
            if (body != null) {
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                c.setFixedLengthStreamingMode(bytes.length);
                try (OutputStream out = c.getOutputStream()) { out.write(bytes); }
            }
            int status = c.getResponseCode();
            InputStream in = status >= 400 ? c.getErrorStream() : c.getInputStream();
            String text = in == null ? "" : readAll(in);
            if (status >= 400) {
                String message = "Server error " + status;
                try { message = Json.str(Json.obj(Json.parse(text)), "error"); } catch (RuntimeException ignored) { }
                throw new ApiException(status, message);
            }
            try {
                return Json.parse(text);
            } catch (RuntimeException e) {
                throw new IOException("That address is not a Simple Messenger server");
            }
        } finally {
            c.disconnect();
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream s = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = s.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
