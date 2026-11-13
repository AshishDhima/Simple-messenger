package com.simple.messenger;

import android.app.Application;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.simple.messenger.core.ChatCore;

import java.util.ArrayList;
import java.util.List;

/** Holds the signed-in session for as long as the app process lives. */
public class MessengerApp extends Application implements ChatCore.Listener {
    private static MessengerApp instance;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<Runnable> watchers = new ArrayList<>();
    private ChatCore core;
    boolean connected = true;
    String problem;

    static MessengerApp get() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        SharedPreferences p = prefs();
        String token = p.getString("token", null);
        if (token != null) start(p.getString("server", ""), token, p.getString("id", ""), p.getString("name", ""));
    }

    SharedPreferences prefs() {
        return getSharedPreferences("messenger", MODE_PRIVATE);
    }

    ChatCore core() {
        return core;
    }

    void signIn(String server, String id, String token, String name) {
        prefs().edit().putString("server", server).putString("id", id).putString("token", token).putString("name", name).apply();
        start(server, token, id, name);
    }

    void signOut() {
        if (core != null) core.stop();
        core = null;
        prefs().edit().remove("token").remove("id").apply();
        notifyWatchers();
    }

    private void start(String server, String token, String id, String name) {
        if (core != null) core.stop();
        connected = true;
        problem = null;
        core = new ChatCore(server, token, id, name, this);
        core.start();
    }

    /** Screens register here to redraw when anything changes. */
    void watch(Runnable r) {
        watchers.add(r);
    }

    void unwatch(Runnable r) {
        watchers.remove(r);
    }

    private void notifyWatchers() {
        for (Runnable r : new ArrayList<>(watchers)) r.run();
    }

    @Override
    public void onChanged() {
        main.post(this::notifyWatchers);
    }

    @Override
    public void onConnection(final boolean isConnected, final String why) {
        main.post(() -> {
            connected = isConnected;
            problem = why;
            notifyWatchers();
        });
    }

    @Override
    public void onSignedOut() {
        main.post(this::signOut);
    }
}
