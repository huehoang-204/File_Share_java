package com.fileshare.common;

import java.io.File;
import java.io.Serializable;

public class FileEntry implements Serializable {
    public String name;
    public boolean isDir;
    public long size;
    public long lastModified;

    public static FileEntry fromFile(File f) {
        FileEntry e = new FileEntry();
        e.name = f.getName();
        e.isDir = f.isDirectory();
        e.size = e.isDir ? 0 : f.length();
        e.lastModified = f.lastModified();
        return e;
    }
}