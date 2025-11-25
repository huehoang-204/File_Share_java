package com.fileshare.client;

import javax.swing.*;

public class ClientApp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new ClientLoginFrame().setVisible(true));
    }
}