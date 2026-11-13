package com.simple.messenger.core;

public class User {
    public final String id;
    public final String name;
    public final boolean online;

    public User(String id, String name, boolean online) {
        this.id = id;
        this.name = name;
        this.online = online;
    }
}
