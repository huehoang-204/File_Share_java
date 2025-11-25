package com.fileshare.server;

import javax.swing.*;
import java.awt.*;

public class ServerApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            DataStore store = DataStore.loadOrInit();
            ActivityLog log = new ActivityLog();
            ServerCore core = new ServerCore(5050, store, log);
            core.startAsync();
            new ServerLoginFrame(store, log, core).setVisible(true);
        });
    }
}