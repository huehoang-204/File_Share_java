package com.fileshare.server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class ServerLoginFrame extends JFrame {
    private final DataStore store;
    private final ActivityLog log;
    private final ServerCore core;
    private final AdminStore admins = AdminStore.loadOrInit();

    ServerLoginFrame(DataStore store, ActivityLog log, ServerCore core) {
        this.store = store;
        this.log = log;
        this.core = core;

        setTitle("Đăng nhập quản trị Server");
        setSize(380, 220);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        JTextField user = new JTextField("admin", 15);
        JPasswordField pass = new JPasswordField("admin123", 15);
        user.setToolTipText("Tài khoản quản trị");
        pass.setToolTipText("Mật khẩu quản trị");

        JPanel p = new JPanel(new GridLayout(3,2,8,8));
        p.setBorder(new EmptyBorder(12,12,12,12));
        p.add(new JLabel("Tài khoản")); p.add(user);
        p.add(new JLabel("Mật khẩu"));  p.add(pass);

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setBorder(new EmptyBorder(6,6,6,6));
        java.awt.Color panelBg = new java.awt.Color(0xE8F5E9);
        java.awt.Color btnColor = new java.awt.Color(0x43A047);
        java.awt.Color accent = new java.awt.Color(0x1E88E5);
        wrapper.setBackground(panelBg);
        p.setOpaque(false);
        wrapper.add(p, BorderLayout.CENTER);

        JButton btn = new JButton("Đăng nhập");
        btn.setToolTipText("Nhấn Enter hoặc click để đăng nhập vào bảng quản trị");
        btn.setBackground(btnColor); btn.setForeground(java.awt.Color.WHITE); btn.setOpaque(true); btn.setFocusPainted(false);
        wrapper.add(btn, BorderLayout.SOUTH);

        add(wrapper);

        getRootPane().setDefaultButton(btn);

        btn.addActionListener(e -> {
            if (admins.verify(user.getText().trim(), new String(pass.getPassword()))) {
                dispose();
                new ServerDashboardFrame(this.store, this.admins, this.log, this.core).setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, "Sai tài khoản/mật khẩu", "Lỗi đăng nhập", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
