package com.simple.messenger.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keeps the chat state and stays in sync with the server.
 * Plain Java, so it can be tested without Android.
 * Listener callbacks run on a background thread.
 */
public class ChatCore {
    /** The conversation everyone shares. */
    public static final String EVERYONE = "all";

    public interface Listener {
        void onChanged();
        void onConnection(boolean connected, String problem);
        void onSignedOut();
    }

    public interface SendCallback {
        void done(Message sent, String error);
    }

    public final String myId;
    public final String myName;
    private final Api api;
    private final Listener listener;
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private final Map<String, List<Message>> conversations = new HashMap<>();
    private final Map<String, Integer> unread = new HashMap<>();
    private final Set<String> seen = new HashSet<>();
    private final Map<String, User> users = new LinkedHashMap<>();
    private volatile boolean running;
    private volatile String openConversation;
    private boolean loaded;
    private long last;
    private Thread poller;

    public ChatCore(String server, String token, String myId, String myName, Listener listener) {
        this.api = new Api(server, token);
        this.myId = myId;
        this.myName = myName;
        this.listener = listener;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        poller = new Thread(this::pollLoop, "chat-poll");
        poller.setDaemon(true);
        poller.start();
    }

    public void stop() {
        running = false;
        if (poller != null) poller.interrupt();
        sender.shutdownNow();
    }

    private void pollLoop() {
        int failures = 0;
        while (running) {
            try {
                Map<String, Object> u = api.updates(last, loaded ? 25 : 0);
                if (!running) return; // stopped while waiting, e.g. after signing out
                apply(u);
                if (failures > 0 || !loaded) listener.onConnection(true, null);
                failures = 0;
                loaded = true;
            } catch (Api.ApiException e) {
                if (e.status == 401) {
                    running = false;
                    listener.onSignedOut();
                    return;
                }
                if (!running) return;
                failures++;
                listener.onConnection(false, e.getMessage());
            } catch (Exception e) {
                if (!running) return;
                failures++;
                listener.onConnection(false, "Can't reach the server. Retrying.");
            }
            if (failures > 0) {
                try {
                    Thread.sleep(Math.min(2000L * failures, 15000L));
                } catch (InterruptedException e) {
                    return;
                }
            }
        }
    }

    private void apply(Map<String, Object> u) {
        boolean changed = false;
        synchronized (this) {
            for (Object o : Json.list(u.get("messages"))) {
                changed |= add(Message.from(Json.obj(o)), loaded);
            }
            Map<String, User> next = new LinkedHashMap<>();
            for (Object o : Json.list(u.get("users"))) {
                Map<String, Object> m = Json.obj(o);
                User user = new User(Json.str(m, "id"), Json.str(m, "name"), Json.bool(m, "online"));
                User old = users.get(user.id);
                if (old == null || old.online != user.online) changed = true;
                next.put(user.id, user);
            }
            if (next.size() != users.size()) changed = true;
            users.clear();
            users.putAll(next);
            last = Math.max(last, Json.num(u, "last"));
        }
        if (changed) listener.onChanged();
    }

    /** Adds a message once. Returns true if it was new. */
    private synchronized boolean add(Message m, boolean countUnread) {
        if (!seen.add(m.id)) return false;
        String key = keyFor(m);
        List<Message> list = conversations.get(key);
        if (list == null) {
            list = new ArrayList<>();
            conversations.put(key, list);
        }
        list.add(m);
        Collections.sort(list, (a, b) -> Long.compare(a.seq, b.seq));
        if (countUnread && !m.from.equals(myId) && !key.equals(openConversation)) {
            Integer n = unread.get(key);
            unread.put(key, n == null ? 1 : n + 1);
        }
        return true;
    }

    public String keyFor(Message m) {
        if (EVERYONE.equals(m.to)) return EVERYONE;
        return m.from.equals(myId) ? m.to : m.from;
    }

    public void send(final String conversation, final String text, final SendCallback callback) {
        sender.execute(() -> {
            try {
                Message m = api.send(conversation, text);
                add(m, false);
                callback.done(m, null);
                listener.onChanged();
            } catch (Api.ApiException e) {
                callback.done(null, e.getMessage());
                if (e.status == 401) listener.onSignedOut();
            } catch (Exception e) {
                callback.done(null, "Not sent. Check your connection.");
            }
        });
    }

    public synchronized void setOpenConversation(String key) {
        openConversation = key;
        if (key != null) unread.remove(key);
    }

    public synchronized List<Message> messages(String key) {
        List<Message> list = conversations.get(key);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public synchronized Message lastMessage(String key) {
        List<Message> list = conversations.get(key);
        return list == null || list.isEmpty() ? null : list.get(list.size() - 1);
    }

    public synchronized int unread(String key) {
        Integer n = unread.get(key);
        return n == null ? 0 : n;
    }

    public synchronized int totalUnread() {
        int t = 0;
        for (int n : unread.values()) t += n;
        return t;
    }

    public synchronized User user(String id) {
        return users.get(id);
    }

    /** Everyone except me. */
    public synchronized List<User> others() {
        List<User> out = new ArrayList<>();
        for (User u : users.values()) if (!u.id.equals(myId)) out.add(u);
        return out;
    }

    public synchronized int onlineCount() {
        int n = 0;
        for (User u : users.values()) if (u.online) n++;
        return n;
    }
}
