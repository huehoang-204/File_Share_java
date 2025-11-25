package com.fileshare.common;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class Packet implements Serializable {
    public PacketType type;
    public Map<String, String> headers = new HashMap<>();
    public Object body; // e.g., list of strings, FileEntry[], byte[], etc.

    public Packet(PacketType type) { this.type = type; }

    public Packet withHeader(String k, String v) { headers.put(k, v); return this; }
    public String h(String k) { return headers.get(k); }
}
