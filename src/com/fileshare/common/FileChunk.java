package com.fileshare.common;

import java.io.Serializable;

public class FileChunk implements Serializable {
    public String path; // server-side relative path inside group
    public int seq;
    public byte[] bytes;
    public boolean last;

    public FileChunk(String path, int seq, byte[] bytes, boolean last) {
        this.path = path; this.seq = seq; this.bytes = bytes; this.last = last;
    }
}