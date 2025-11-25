package com.fileshare.client;

import com.fileshare.common.FileEntry;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.File;
import java.util.List;
import java.util.Map;


class ClientMainFrame extends JFrame {
    private final ClientConnection conn;
    private final String username;
    private final DefaultListModel<String> groupsModel = new DefaultListModel<>();
    private final DefaultTableModel usersSideModel = new DefaultTableModel(new Object[]{"Người dùng", "Trạng thái"}, 0) {
        public boolean isCellEditable(int r, int c) { return false; }
    };

    private final DefaultTableModel filesModel = new DefaultTableModel(new Object[]{"Tên","Thư mục","Kích thước","Cập nhật"}, 0) {
        public boolean isCellEditable(int r,int c){return false;}
    };

    private String currentGroup;
    private String currentPath = ".";
    private final JLabel statusLabel = new JLabel("Sẵn sàng");

    ClientMainFrame(ClientConnection conn, String username) throws Exception {
        this.conn = conn;
        this.username = username;
        setTitle("Client — " + username);
        setSize(960, 620);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        buildUI();
        refreshGroups();
        refreshUsers();
        new javax.swing.Timer(1500, e -> refreshUsers()).start();
    }

    private void buildUI() {
        // Left column: groups list + users
        JSplitPane leftSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JList<String> groups = new JList<>(groupsModel);
        groups.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        groups.setFixedCellHeight(28);
        groups.setBorder(BorderFactory.createEmptyBorder(6,6,6,6));
        groups.setFont(groups.getFont().deriveFont(Font.PLAIN, 13f));

        JTable usersSide = new JTable(usersSideModel);
        usersSide.setRowHeight(22);
        usersSide.setFillsViewportHeight(true);

        leftSplit.setTopComponent(new JScrollPane(groups));
        leftSplit.setBottomComponent(new JScrollPane(usersSide));
        leftSplit.setDividerLocation(220);

        // Main files table
        JTable files = new JTable(filesModel);
        files.setRowHeight(24);
        files.setFillsViewportHeight(true);
        files.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        files.getColumnModel().getColumn(2).setPreferredWidth(90); // size
        files.getColumnModel().getColumn(0).setPreferredWidth(300); // name
        files.getColumnModel().getColumn(1).setPreferredWidth(80); // dir flag
        files.getColumnModel().getColumn(3).setPreferredWidth(160); // modified

        files.getTableHeader().setBackground(new java.awt.Color(0x7E57C2));
        files.getTableHeader().setForeground(java.awt.Color.WHITE);
        usersSide.getTableHeader().setBackground(new java.awt.Color(0x7E57C2));
        usersSide.getTableHeader().setForeground(java.awt.Color.WHITE);

        JSplitPane mainSplit = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplit, new JScrollPane(files));
        mainSplit.setDividerLocation(260);
        add(mainSplit, BorderLayout.CENTER);

        // Header / toolbar area
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(8,8,8,8));
        header.setBackground(new java.awt.Color(0xFAFAFA));

        JLabel title = new JLabel("FileShare — " + username);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setBorder(BorderFactory.createEmptyBorder(4,8,4,8));

        JToolBar bar = new JToolBar(); bar.setFloatable(false);
        bar.setOpaque(false);
        java.awt.Color btnBg = new java.awt.Color(0xFFCA28); // amber
        java.awt.Color btnFg = java.awt.Color.BLACK;

        JButton upBtn = new JButton("⬆"); upBtn.setToolTipText("Quay lại");
        JButton newFolder = new JButton("Thư mục"); newFolder.setToolTipText("Tạo thư mục con");
        JButton upload = new JButton("↑"); upload.setToolTipText("Upload file");
        JButton download = new JButton("↓"); download.setToolTipText("Tải file");
        JButton del = new JButton("X"); del.setToolTipText("Xóa (leader)");
        JButton createGroupBtn = new JButton("Tạo nhóm"); createGroupBtn.setToolTipText("Tạo nhóm mới");
        JButton joinGroupBtn = new JButton("Tham gia"); joinGroupBtn.setToolTipText("Tham gia nhóm");
        JButton inviteBtn = new JButton("Mời"); inviteBtn.setToolTipText("Mời thành viên");

        // style buttons
        for (JButton b : new JButton[]{inviteBtn, createGroupBtn, joinGroupBtn, upBtn, newFolder, upload, download}) {
            b.setBackground(btnBg); b.setForeground(btnFg); b.setOpaque(true); b.setFocusPainted(false);
        }
        del.setBackground(new java.awt.Color(0xE53935)); del.setForeground(java.awt.Color.WHITE); del.setOpaque(true);

        bar.add(inviteBtn); bar.add(createGroupBtn); bar.add(joinGroupBtn); bar.addSeparator();
        bar.add(upBtn); bar.add(newFolder); bar.add(upload); bar.add(download); bar.add(del);

        // right side: search + status summary
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        JTextField search = new JTextField(18);
        search.setToolTipText("Tìm file hoặc thư mục trong nhóm");
        right.add(search);

        header.add(title, BorderLayout.WEST);
        header.add(bar, BorderLayout.CENTER);
        header.add(right, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // status bar
        statusLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(8,12,8,12));
        statusLabel.setOpaque(true); statusLabel.setBackground(new java.awt.Color(0xECEFF1)); statusLabel.setForeground(new java.awt.Color(0x263238));
        add(statusLabel, BorderLayout.SOUTH);

        // tooltips
        groups.setToolTipText("Danh sách nhóm của bạn. Chọn một nhóm để xem file.");
        usersSide.setToolTipText("Danh sách người dùng và trạng thái online/offline");
        files.setToolTipText("Danh sách file trong nhóm. Double-click để mở thư mục.");

        // listeners
        groups.addListSelectionListener(e -> { currentGroup = groups.getSelectedValue(); currentPath = "."; if (currentGroup != null) refreshFiles(); });
        files.addMouseListener(new java.awt.event.MouseAdapter(){ public void mouseClicked(java.awt.event.MouseEvent e){ if (e.getClickCount()==2) openSelected(files); } });
        upBtn.addActionListener(e -> goUp());
        newFolder.addActionListener(e -> doNewFolder());
        upload.addActionListener(e -> doUpload());
        download.addActionListener(e -> doDownload());
        del.addActionListener(e -> doDelete());
        createGroupBtn.addActionListener(e -> doCreateGroup());
        joinGroupBtn.addActionListener(e -> doJoinGroup());
        inviteBtn.addActionListener(e -> doInvite());
        search.addActionListener(e -> {
            String q = search.getText().trim();
            if (q.isEmpty()) { refreshFiles(); return; }
            // simple filter: reload and keep only rows matching name
            try {
                List<FileEntry> list = conn.listEntries(currentGroup, currentPath);
                filesModel.setRowCount(0);
                for (FileEntry fe: list) {
                    if (fe.name.toLowerCase().contains(q.toLowerCase())) filesModel.addRow(new Object[]{fe.name, fe.isDir, fe.size, new java.util.Date(fe.lastModified)});
                }
                statusLabel.setText("Kết quả tìm kiếm: " + filesModel.getRowCount() + " mục");
            } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Lỗi tìm kiếm: "+ex.getMessage()); }
        });

    }

    private void refreshUsers() {
        try {
            List<Map<String,String>> list = conn.getUserList();
            usersSideModel.setRowCount(0); // xoá dữ liệu cũ
            for (Map<String,String> m : list) {
                String u = m.get("username");
                boolean online = "true".equals(m.get("online"));
                usersSideModel.addRow(new Object[]{u, online ? "Online" : "Offline"});
            }
            statusLabel.setText("Cập nhật người dùng: " + new java.util.Date());
        } catch (Exception ignore) { }
    }


    private void doInvite() {
        if (currentGroup == null) return;
        String userToInvite = JOptionPane.showInputDialog(this, "Nhập username muốn mời vào nhóm:");
        if (userToInvite == null || userToInvite.trim().isEmpty()) return;

        try {
            conn.inviteMember(currentGroup, userToInvite.trim());
            JOptionPane.showMessageDialog(this, "Đã mời " + userToInvite + " vào nhóm " + currentGroup);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Mời thất bại: " + ex.getMessage());
        }
    }

    private void doCreateGroup() {
        JPanel panel = new JPanel(new java.awt.GridLayout(2,2,6,6));
        JTextField id = new JTextField();
        JTextField name = new JTextField();
        panel.add(new JLabel("Group ID:")); panel.add(id);
        panel.add(new JLabel("Tên nhóm:")); panel.add(name);

        int ok = JOptionPane.showConfirmDialog(this, panel, "Tạo nhóm mới", JOptionPane.OK_CANCEL_OPTION);
        if (ok != JOptionPane.OK_OPTION) return;

        String gid = id.getText().trim();
        String gname = name.getText().trim();
        if (gid.isEmpty()) { JOptionPane.showMessageDialog(this, "Group ID không được trống"); return; }

        try {
            conn.createGroup(gid, gname);
            JOptionPane.showMessageDialog(this, "Tạo nhóm thành công. Bạn là trưởng nhóm.");
            refreshGroups(); // cập nhật danh sách nhóm bên trái
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Tạo nhóm lỗi: " + ex.getMessage());
        }
    }

    private void doJoinGroup() {
        String gid = JOptionPane.showInputDialog(this, "Nhập Group ID muốn tham gia:");
        if (gid == null || gid.trim().isEmpty()) return;

        try {
            conn.joinGroup(gid.trim());
            JOptionPane.showMessageDialog(this, "Đã tham gia nhóm " + gid);
            refreshGroups();
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Không thể tham gia: " + ex.getMessage());
        }
    }

    private void refreshGroups() throws Exception {
        groupsModel.clear();
        for (String g: conn.listGroups()) groupsModel.addElement(g);
        statusLabel.setText("Nhóm: " + groupsModel.getSize() + " nhóm");
    }

    private void refreshFiles() {
        try {
            filesModel.setRowCount(0);
            List<FileEntry> list = conn.listEntries(currentGroup, currentPath);
            for (FileEntry fe: list) {
                filesModel.addRow(new Object[]{fe.name, fe.isDir, fe.size, new java.util.Date(fe.lastModified)});
            }
            statusLabel.setText("Nhóm: " + currentGroup + "  |  Đường dẫn: " + currentPath + "  |  " + list.size() + " mục");
        } catch (Exception e) { JOptionPane.showMessageDialog(this, "Lỗi load danh sách: "+e.getMessage()); }
    }

    private void openSelected(JTable files) {
        int r = files.getSelectedRow(); if (r<0) return;
        boolean isDir = (boolean) filesModel.getValueAt(r,1);
        String name = (String) filesModel.getValueAt(r,0);
        if (isDir) { currentPath = currentPath.equals(".")? name : currentPath+"/"+name; refreshFiles(); }
    }

    private void goUp() {
        if (currentPath.equals(".")) return;
        int idx = currentPath.lastIndexOf('/');
        currentPath = (idx==-1)? "." : currentPath.substring(0, idx);
        refreshFiles();
    }

    private void doNewFolder() {
        if (currentGroup==null) return;
        String name = JOptionPane.showInputDialog(this, "Tên thư mục con:");
        if (name==null||name.trim().isEmpty()) return;
        String rel = currentPath.equals(".")? name : currentPath+"/"+name;
        try { conn.createFolder(currentGroup, rel); refreshFiles(); }
        catch (Exception e){ JOptionPane.showMessageDialog(this, e.getMessage()); }
    }

    private void doUpload() {
        if (currentGroup==null) return;
        JFileChooser fc = new JFileChooser(); if (fc.showOpenDialog(this)==JFileChooser.APPROVE_OPTION) {
            File f = fc.getSelectedFile();
            String rel = currentPath.equals(".")? f.getName() : currentPath+"/"+f.getName();
            try { conn.upload(currentGroup, rel, f); refreshFiles(); JOptionPane.showMessageDialog(this, "Đã upload"); }
            catch (Exception e){ JOptionPane.showMessageDialog(this, "Upload lỗi: "+e.getMessage()); }
        }
    }

    private void doDownload() {
        if (currentGroup==null) return;
        int r = ((JTable)((JScrollPane)((JSplitPane)getContentPane().getComponent(0)).getRightComponent()).getViewport().getView()).getSelectedRow();
        if (r<0) return; boolean isDir = (boolean) filesModel.getValueAt(r,1);
        if (isDir) { JOptionPane.showMessageDialog(this, "Chỉ tải file"); return; }
        String name = (String) filesModel.getValueAt(r,0);
        String rel = currentPath.equals(".")? name : currentPath+"/"+name;
        JFileChooser fc = new JFileChooser(); fc.setSelectedFile(new File(name));
        if (fc.showSaveDialog(this)==JFileChooser.APPROVE_OPTION) {
            try { conn.download(currentGroup, rel, fc.getSelectedFile()); JOptionPane.showMessageDialog(this, "Đã tải về"); }
            catch(Exception e){ JOptionPane.showMessageDialog(this, "Download lỗi: "+e.getMessage()); }
        }
    }

    private void doDelete() {
        if (currentGroup==null) return;
        JTable files = (JTable)((JScrollPane)((JSplitPane)getContentPane().getComponent(0)).getRightComponent()).getViewport().getView();
        int r = files.getSelectedRow(); if (r<0) return;
        String name = (String) filesModel.getValueAt(r,0);
        boolean isDir = (boolean) filesModel.getValueAt(r,1);
        String rel = currentPath.equals(".")? name : currentPath+"/"+name;
        if (JOptionPane.showConfirmDialog(this, "Xóa "+(isDir?"thư mục":"file")+": "+name+"?","Xác nhận", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
            try { conn.deleteEntry(currentGroup, rel); refreshFiles(); }
            catch (Exception e){ JOptionPane.showMessageDialog(this, "Xóa thất bại (chỉ leader): "+e.getMessage()); }
        }
    }
}