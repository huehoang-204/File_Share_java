package com.fileshare.client;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

class ClientLoginFrame extends JFrame {
    JTextField user = new JTextField("", 16);
    JPasswordField pass = new JPasswordField("", 16);

    ClientLoginFrame() {
        setTitle("Đăng nhập Client");
        setSize(420, 260);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
    JPanel form = new JPanel(new GridLayout(2,2,8,8));
    form.setBorder(new EmptyBorder(8,8,8,8));
    JLabel lu = new JLabel("Tài khoản:"); lu.setFont(lu.getFont().deriveFont(13f));
    JLabel lp = new JLabel("Mật khẩu:"); lp.setFont(lp.getFont().deriveFont(13f));
    form.add(lu); form.add(user);
    user.setToolTipText("Tên người dùng của bạn"); user.setFont(user.getFont().deriveFont(13f));
    form.add(lp); form.add(pass);
    pass.setToolTipText("Mật khẩu"); pass.setFont(pass.getFont().deriveFont(13f));

        JPanel wrapper = new JPanel();
        wrapper.setBorder(new EmptyBorder(12,12,12,12));
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(new java.awt.Color(0xFFFDF6));

        JLabel logo = new JLabel("FileShare");
        logo.setAlignmentX(Component.CENTER_ALIGNMENT);
        logo.setFont(logo.getFont().deriveFont(Font.BOLD, 20f));
        logo.setBorder(new EmptyBorder(6,6,12,6));
        wrapper.add(logo);

        JPanel center = new JPanel(new BorderLayout());
        center.setOpaque(false);
        center.add(form, BorderLayout.CENTER);

        wrapper.add(center);

        JButton btn = new JButton("Đăng nhập");
        btn.setAlignmentX(Component.CENTER_ALIGNMENT);
        btn.setToolTipText("Nhấn Enter hoặc click để đăng nhập");
        btn.setBackground(new java.awt.Color(0x1976D2)); btn.setForeground(java.awt.Color.WHITE);
        btn.setFocusPainted(false);
        btn.setOpaque(true);
        btn.setBorderPainted(false);
        btn.setMaximumSize(new Dimension(160, 36));
        wrapper.add(Box.createVerticalStrut(12));
        wrapper.add(btn);

        add(wrapper);

        // Enter sẽ kích hoạt nút đăng nhập
        getRootPane().setDefaultButton(btn);

        btn.addActionListener(e -> doLogin());
    }

    void doLogin() {
        try {
            // Sử dụng host/port mặc định để đơn giản hoá giao diện
            ClientConnection conn = new ClientConnection("127.0.0.1", 5050);
            if (conn.login(user.getText().trim(), new String(pass.getPassword()))) {
                dispose(); new ClientMainFrame(conn, user.getText().trim()).setVisible(true);
            } else JOptionPane.showMessageDialog(this, "Sai thông tin đăng nhập", "Lỗi", JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Không kết nối được: "+ex.getMessage(), "Lỗi kết nối", JOptionPane.ERROR_MESSAGE);
        }
    }
}
