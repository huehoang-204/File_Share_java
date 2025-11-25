package com.fileshare.server;

import java.text.SimpleDateFormat;
import java.util.*;

class ActivityLog {
    private final List<String> lines = new ArrayList<>();
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    synchronized void log(String msg) {
        lines.add(fmt.format(new Date()) + " | " + msg);
        if (lines.size() > 2000) lines.remove(0);
    }

    synchronized List<String> last(int n) {
        int from = Math.max(0, lines.size() - n);
        return new ArrayList<>(lines.subList(from, lines.size()));
    }
}
