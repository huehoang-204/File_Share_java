package com.fileshare.server;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;

class FileService {
    File groupDir(String groupId) {
        return new File(DataStore.GROUPS_DIR, groupId);
    }

    File resolvePath(String groupId, String relPath) throws IOException {
        File base = groupDir(groupId);
        File target = new File(base, relPath).getCanonicalFile();
        if (!target.getPath().startsWith(base.getCanonicalPath()))
            throw new IOException("Path traversal blocked");
        return target;
    }

    void ensureGroupDir(String groupId) { groupDir(groupId).mkdirs(); }

    void createFolder(String groupId, String relPath) throws IOException {
        File dir = resolvePath(groupId, relPath);
        if (!dir.exists() && !dir.mkdirs()) throw new IOException("Cannot create folder");
    }

    boolean deleteRecursive(File f) {
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k: kids) deleteRecursive(k);
        }
        return f.delete();
    }

    FileLock lockExclusive(FileOutputStream fos) throws IOException {
        FileChannel ch = fos.getChannel();
        return ch.lock();
    }
}
