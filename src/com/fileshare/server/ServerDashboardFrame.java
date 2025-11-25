package com.fileshare.server;

import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.text.ParseException;
import java.io.File;
import java.util.List;
import java.util.*;

class ServerDashboardFrame extends JFrame {
    private final DataStore store; private final AdminStore admins; private final ActivityLog log;

 // THAY vì DefaultListModel<String> usersModel
    private final javax.swing.table.DefaultTableModel usersTableModel =
        new javax.swing.table.DefaultTableModel(new Object[]{"User","Trạng thái"}, 0) {
            public boolean isCellEditable(int r,int c){ return false; }
        };
        
    private final ServerCore core;


    private final DefaultListModel<String> groupsModel = new DefaultListModel<>();
    private final DefaultTableModel membersModel = new DefaultTableModel(new Object[]{"User","Leader"}, 0);
    private final DefaultListModel<String> activityModel = new DefaultListModel<>();

    // UI components kept as fields so refresh can preserve selection/state
    private JList<String> groupsList;
    private JTable usersTable;
    private JTable membersTable;
    private JList<String> activityList;
    private final JLabel statusLabel = new JLabel("Sẵn sàng");
    // Dashboard stat labels
    private final JLabel statUsers = new JLabel();
    private final JLabel statGroups = new JLabel();
    private final JLabel statFiles = new JLabel();
    private PieChartPanel pieChart;
    private LineChartPanel lineChart;

    private volatile boolean autoRefresh = true;
    private JCheckBox autoRefreshCheckbox;
    private JButton refreshNowButton;

    ServerDashboardFrame(DataStore store, AdminStore admins, ActivityLog log,ServerCore core) {
        this.store = store; this.admins = admins; this.log = log;this.core = core;
        setTitle("Server Dashboard — FileShare"); setSize(960, 620); setLocationRelativeTo(null);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Thống kê", buildStatsPanel());
        tabs.add("Người dùng", buildUsersPanel());
        tabs.add("Nhóm & Thư mục", buildGroupsPanel());
        tabs.add("Hoạt động", buildActivityPanel());
        add(tabs, BorderLayout.CENTER);
        statusLabel.setBorder(javax.swing.BorderFactory.createEmptyBorder(6,8,6,8));
        add(statusLabel, BorderLayout.SOUTH);
        refreshAll();
        new javax.swing.Timer(3000, e -> { if (autoRefresh) refreshAll(); }).start();
    }

    private JPanel buildUsersPanel() {
    	JPanel root = new JPanel(new BorderLayout());
        usersTable = new JTable(usersTableModel);
        usersTable.getTableHeader().setBackground(new java.awt.Color(0x0097A7));
        usersTable.getTableHeader().setForeground(java.awt.Color.WHITE);
        root.add(new JScrollPane(usersTable), BorderLayout.CENTER);

        JPanel actions = new JPanel();
        JButton add = new JButton("Tạo user");
        JButton del = new JButton("Xóa user");
        add.setBackground(new java.awt.Color(0x4CAF50)); add.setForeground(java.awt.Color.WHITE); add.setOpaque(true);
        del.setBackground(new java.awt.Color(0xF44336)); del.setForeground(java.awt.Color.WHITE); del.setOpaque(true);
        add.setToolTipText("Tạo user mới cho hệ thống");
        del.setToolTipText("Xóa user đã chọn (kèm loại khỏi nhóm)");
        actions.add(add); actions.add(del);
        root.add(actions, BorderLayout.SOUTH);

        add.addActionListener(e -> createUser());
        del.addActionListener(e -> {
            int r = usersTable.getSelectedRow();
            if (r >= 0) deleteUser((String) usersTableModel.getValueAt(r, 0));
        });
        return root;
    }

    private JPanel buildGroupsPanel() {
        JPanel root = new JPanel(new BorderLayout());
        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT);
        groupsList = new JList<>(groupsModel);
        groupsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        split.setLeftComponent(new JScrollPane(groupsList));
        membersTable = new JTable(membersModel);
        membersTable.getTableHeader().setBackground(new java.awt.Color(0x0097A7));
        membersTable.getTableHeader().setForeground(java.awt.Color.WHITE);
        split.setRightComponent(new JScrollPane(membersTable));
        split.setDividerLocation(260);
        root.add(split, BorderLayout.CENTER);
        JPanel actions = new JPanel();
        JButton addG = new JButton("Tạo nhóm");
        JButton addM = new JButton("Thêm thành viên");
        JButton setL = new JButton("Gán trưởng nhóm");
        JButton openDir = new JButton("Mở thư mục nhóm");
        addG.setBackground(new java.awt.Color(0x29B6F6)); addG.setForeground(java.awt.Color.WHITE); addG.setOpaque(true);
        addM.setBackground(new java.awt.Color(0x66BB6A)); addM.setForeground(java.awt.Color.WHITE); addM.setOpaque(true);
        setL.setBackground(new java.awt.Color(0xFFB300)); setL.setForeground(java.awt.Color.WHITE); setL.setOpaque(true);
        openDir.setBackground(new java.awt.Color(0x8E24AA)); openDir.setForeground(java.awt.Color.WHITE); openDir.setOpaque(true);
    addG.setToolTipText("Tạo một group mới (thư mục trên server)");
    addM.setToolTipText("Thêm user vào nhóm đã chọn");
    setL.setToolTipText("Gán một thành viên làm trưởng nhóm");
    openDir.setToolTipText("Mở thư mục nhóm trên hệ thống file");
        actions.add(addG); actions.add(addM); actions.add(setL); actions.add(openDir);
        root.add(actions, BorderLayout.SOUTH);

        groupsList.addListSelectionListener(e -> showMembers(groupsList.getSelectedValue()));
        addG.addActionListener(e -> createGroup());
        addM.addActionListener(e -> addMember(groupsList.getSelectedValue()));
        setL.addActionListener(e -> setLeader(groupsList.getSelectedValue()));
        openDir.addActionListener(e -> openGroupDir(groupsList.getSelectedValue()));
        return root;
    }

    private JPanel buildActivityPanel() {
        JPanel p = new JPanel(new BorderLayout());
        activityList = new JList<>(activityModel);
        p.add(new JScrollPane(activityList), BorderLayout.CENTER);
        return p;
    }

    private JPanel buildStatsPanel() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(12,12,12,12));

        // controls: auto refresh checkbox and manual refresh
        JPanel controls = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        autoRefreshCheckbox = new JCheckBox("Tự động làm mới", true);
        autoRefreshCheckbox.addActionListener(e -> autoRefresh = autoRefreshCheckbox.isSelected());
        refreshNowButton = new JButton("Làm mới");
        refreshNowButton.addActionListener(e -> refreshAll());
        controls.add(autoRefreshCheckbox); controls.add(refreshNowButton);
        controls.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(controls);
        p.add(Box.createVerticalStrut(8));

        JLabel title = new JLabel("Thống kê hệ thống");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 16f));
        title.setAlignmentX(Component.LEFT_ALIGNMENT);
        p.add(title);
        p.add(Box.createVerticalStrut(12));

        statUsers.setAlignmentX(Component.LEFT_ALIGNMENT);
        statGroups.setAlignmentX(Component.LEFT_ALIGNMENT);
        statFiles.setAlignmentX(Component.LEFT_ALIGNMENT);

        statUsers.setFont(statUsers.getFont().deriveFont(14f));
        statGroups.setFont(statGroups.getFont().deriveFont(14f));
        statFiles.setFont(statFiles.getFont().deriveFont(14f));

        p.add(statUsers);
        p.add(Box.createVerticalStrut(12));

        JPanel charts = new JPanel(new GridLayout(1,2,12,12));
        pieChart = new PieChartPanel();
        lineChart = new LineChartPanel();
        charts.add(pieChart);
        charts.add(lineChart);
        charts.setAlignmentX(Component.LEFT_ALIGNMENT);
        charts.setPreferredSize(new Dimension(960, 260));
        p.add(charts);

        return p;
    }
    

    private void refreshAll() {
        // Indicate refresh in status and preserve UI selection to avoid interrupting user actions
        statusLabel.setText("Đang làm mới...");
        String selectedUser = null;
        if (usersTable != null) {
            int r = usersTable.getSelectedRow();
            if (r >= 0) selectedUser = (String) usersTableModel.getValueAt(r, 0);
        }

        String selectedGroup = (groupsList == null) ? null : groupsList.getSelectedValue();

        // Bảng user + trạng thái (rebuild rows)
        usersTableModel.setRowCount(0);
        for (String u : store.users.keySet()) {
            boolean online = core.sessions.containsKey(u);
            usersTableModel.addRow(new Object[]{u, online ? "Online" : "Offline"});
        }
        // restore selected user if any
        if (selectedUser != null && usersTable != null) {
            for (int i = 0; i < usersTableModel.getRowCount(); i++) {
                if (selectedUser.equals(usersTableModel.getValueAt(i, 0))) {
                    usersTable.setRowSelectionInterval(i, i);
                    break;
                }
            }
        }

        // Danh sách nhóm (rebuild but try to preserve selection)
        List<String> groups = new ArrayList<>(store.groups.keySet());
        groupsModel.clear();
        for (String g : groups) groupsModel.addElement(g);
        if (selectedGroup != null && groupsModel.contains(selectedGroup)) {
            groupsList.setSelectedValue(selectedGroup, true);
        }

        // Log
        refreshActivity();
        // Cập nhật status bar
        statusLabel.setText("Users: " + store.users.size() + " | Groups: " + store.groups.size() + " | Sessions: " + core.sessions.size());
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new java.awt.Color(0xECEFF1));
        statusLabel.setForeground(new java.awt.Color(0x263238));

        // update stats tab labels
        statUsers.setText("Số người dùng: " + store.users.size());
        statGroups.setText("Số nhóm: " + store.groups.size());
        statFiles.setText("Số file hiện có trên server: " + countFilesInGroups());

        // update charts with latest counts
        if (pieChart != null) pieChart.setData(filesPerGroup());
        if (lineChart != null) lineChart.setData(uploadsTimeSeries(30));
    }

    private int countFilesInGroups() {
        File base = DataStore.GROUPS_DIR;
        if (!base.exists()) return 0;
        return countFilesRecursive(base);
    }

    private int countFilesRecursive(File dir) {
        int count = 0;
        File[] kids = dir.listFiles();
        if (kids == null) return 0;
        for (File f: kids) {
            if (f.isDirectory()) count += countFilesRecursive(f);
            else {
                if (!f.getName().equals(".placeholder")) count++;
            }
        }
        return count;
    }

    

    // Count files per group (for pie chart)
    private Map<String,Integer> filesPerGroup() {
        Map<String,Integer> m = new LinkedHashMap<>();
        File base = DataStore.GROUPS_DIR;
        if (!base.exists()) return m;
        File[] kids = base.listFiles(File::isDirectory);
        if (kids == null || kids.length == 0) { m.put("(no groups)", 0); return m; }
        for (File g : kids) {
            int cnt = countFilesRecursive(g);
            m.put(g.getName(), cnt);
        }
        return m;
    }

    // Time-series of uploads: bucket by minute for last N minutes
    private int[] uploadsTimeSeries(int minutes) {
        int[] buckets = new int[minutes];
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        long now = System.currentTimeMillis();
        List<String> lines = log.last(2000);
        for (String L : lines) {
            try {
                int sep = L.indexOf(" |");
                if (sep <= 0) continue;
                String ts = L.substring(0, sep);
                Date d = fmt.parse(ts);
                long diffMin = (now - d.getTime()) / 60000L;
                if (diffMin < 0 || diffMin >= minutes) continue;
                String s = L.toLowerCase();
                if (s.contains("upload")) {
                    int idx = (int) diffMin;
                    buckets[minutes - 1 - idx]++; // oldest at 0, newest at end
                }
            } catch (ParseException ignored) {}
        }
        return buckets;
    }

    // Simple pie chart panel
    private static class PieChartPanel extends JPanel {
        private Map<String,Integer> data = new LinkedHashMap<>();
        PieChartPanel() {
            setToolTipText("");
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
                public void mouseMoved(java.awt.event.MouseEvent me) {
                    updateTooltip(me.getPoint());
                }
            });
        }

        void setData(Map<String,Integer> d) { this.data = new LinkedHashMap<>(d); repaint(); }

        private void updateTooltip(Point p) {
            if (data==null || data.isEmpty()) { setToolTipText(null); return; }
            int w = getWidth(), h = getHeight();
            int size = Math.min(w,h) - 40; int cx = 20 + size/2, cy = 20 + size/2;
            int dx = p.x - cx, dy = p.y - cy;
            double distSq = dx*dx + dy*dy;
            if (distSq > (size/2)*(size/2)) { setToolTipText(null); return; }
            double angle = Math.toDegrees(Math.atan2(-dy, dx)); // invert y for typical pie
            if (angle < 0) angle += 360;
            int total = data.values().stream().mapToInt(Integer::intValue).sum(); if (total==0) total=1;
            int start = 0;
            for (Map.Entry<String,Integer> e: data.entrySet()) {
                int angleSpan = (int) Math.round(360.0 * e.getValue() / total);
                int end = (start + angleSpan) % 360;
                boolean contains;
                if (start <= end) contains = (angle >= start && angle < end);
                else contains = (angle >= start || angle < end);
                if (contains) {
                    double pct = 100.0 * e.getValue() / total;
                    setToolTipText(e.getKey() + " — " + e.getValue() + " (" + String.format("%.1f", pct) + "%)");
                    return;
                }
                start = (start + angleSpan) % 360;
            }
            setToolTipText(null);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (data==null || data.isEmpty()) return;
            int w = getWidth(), h = getHeight();
            // title
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("Phân bố file theo nhóm", 12, 16);

            int size = Math.min(w,h) - 60; int x = 20, y = 28;
            int total = data.values().stream().mapToInt(Integer::intValue).sum(); if (total==0) total=1;
            int start = 0; int i=0;
            Color[] palette = { new Color(0x42A5F5), new Color(0x66BB6A), new Color(0xFFCA28), new Color(0xEF5350), new Color(0x7E57C2), new Color(0x26A69A) };
            for (Map.Entry<String,Integer> e: data.entrySet()) {
                int angle = (int) Math.round(360.0 * e.getValue() / total);
                g2.setColor(palette[i % palette.length]);
                g2.fillArc(x, y, size, size, start, angle);
                start += angle; i++;
            }
            // legend with percentages
            int lx = x + size + 12; int ly = y;
            i=0;
            g2.setFont(g2.getFont().deriveFont(12f));
            for (Map.Entry<String,Integer> e: data.entrySet()) {
                g2.setColor(palette[i % palette.length]);
                g2.fillRect(lx, ly+4, 12, 12);
                g2.setColor(Color.DARK_GRAY);
                double pct = 100.0 * e.getValue() / total;
                g2.drawString(e.getKey() + " ("+e.getValue()+") — " + String.format("%.1f", pct) + "%", lx+18, ly+14);
                ly += 20; i++;
            }
        }
    }

    // Simple line/area chart panel
    private static class LineChartPanel extends JPanel {
        private int[] data = new int[0];
        void setData(int[] d) { this.data = d.clone(); repaint(); }

        LineChartPanel() {
            setToolTipText("");
            addMouseMotionListener(new java.awt.event.MouseMotionAdapter(){
                public void mouseMoved(java.awt.event.MouseEvent me) { updateTooltip(me.getPoint()); }
            });
        }

        private void updateTooltip(Point p) {
            if (data==null || data.length==0) { setToolTipText(null); return; }
            int w = getWidth(), h = getHeight();
            int left = 36; int aw = w - left - 16; int ah = h - 36;
            int n = data.length;
            int nearest = -1; double best = Double.MAX_VALUE;
            for (int i=0;i<n;i++) {
                int x = left + (int)((double)i/(n-1)*aw);
                int y = 8 + ah - (int)((double)data[i]/(double)Math.max(1, Arrays.stream(data).max().orElse(1))*ah);
                double d2 = (p.x - x)*(p.x - x) + (p.y - y)*(p.y - y);
                if (d2 < best) { best = d2; nearest = i; }
            }
            if (nearest >= 0) setToolTipText("T-"+(data.length-1-nearest)+"m: " + data[nearest] + " uploads");
            else setToolTipText(null);
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (data==null || data.length==0) return;
            int w = getWidth(), h = getHeight();
            int left = 36; int aw = w - left - 16; int ah = h - 36;
            int max = Arrays.stream(data).max().orElse(1);
            // title
            g2.setColor(Color.DARK_GRAY);
            g2.setFont(g2.getFont().deriveFont(Font.BOLD, 14f));
            g2.drawString("Upload theo thời gian (phút)", 8, 16);
            // axes
            g2.setColor(Color.LIGHT_GRAY); g2.drawLine(left, 28, left, 28+ah); g2.drawLine(left, 28+ah, left+aw, 28+ah);
            int n = data.length; if (n<2) return;
            int[] xs = new int[n]; int[] ys = new int[n];
            for (int i=0;i<n;i++) {
                xs[i] = left + (int)((double)i/(n-1)*aw);
                ys[i] = 28 + ah - (int)((double)data[i]/(double)max*ah);
            }
            // fill area
            g2.setColor(new Color(0x90CAF9));
            Polygon poly = new Polygon();
            for (int i=0;i<n;i++) poly.addPoint(xs[i], ys[i]);
            poly.addPoint(left+aw, 28+ah); poly.addPoint(left, 28+ah);
            g2.fill(poly);
            // draw line
            g2.setColor(new Color(0x1976D2));
            g2.setStroke(new BasicStroke(2f));
            for (int i=0;i<n-1;i++) g2.drawLine(xs[i], ys[i], xs[i+1], ys[i+1]);
            // draw points and labels
            g2.setColor(new Color(0x0D47A1));
            for (int i=0;i<n;i++) {
                g2.fillOval(xs[i]-3, ys[i]-3, 6, 6);
                if (i % Math.max(1,n/6) == 0) g2.drawString(""+data[i], xs[i]-6, ys[i]-6);
            }
            // x labels
            g2.setColor(Color.DARK_GRAY); g2.setFont(g2.getFont().deriveFont(10f));
            for (int i=0;i<n;i+=Math.max(1,n/6)) {
                g2.drawString("T-"+(n-1-i)+"m", xs[i]-10, 28+ah+16);
            }
        }
    }


    private void refreshActivity() {
        List<String> lines = log.last(500);
        activityModel.clear(); lines.forEach(activityModel::addElement);
    }

    private void showMembers(String groupId) {
        membersModel.setRowCount(0);
        if (groupId == null) return;
        Group g = store.groups.get(groupId);
        if (g == null) return;
        for (String u: g.members) {
            boolean leader = u.equals(g.leader);
            membersModel.addRow(new Object[]{u, leader});
        }
    }

    private void createUser() {
        boolean prevAuto = autoRefresh;
        autoRefresh = false;
        try {
            JTextField u = new JTextField();
            JPasswordField p = new JPasswordField();
            Object[] msg = {"Username:", u, "Password:", p};
            if (JOptionPane.showConfirmDialog(this, msg, "Tạo user", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                if (store.users.containsKey(u.getText())) { JOptionPane.showMessageDialog(this, "User đã tồn tại"); return; }
                String salt = Security.randomSalt();
                User X = new User(); X.username = u.getText().trim(); X.salt = salt; X.passwordHash = Security.hashPassword(new String(p.getPassword()), salt);
                store.users.put(X.username, X); store.save();
                new File(DataStore.GROUPS_DIR, ".placeholder").getParentFile().mkdirs();
                refreshAll();
            }
        } finally {
            autoRefresh = prevAuto;
        }
    }

    private void deleteUser(String username) {
        if (username == null) return;
        boolean prevAuto = autoRefresh; autoRefresh = false;
        try {
            if (JOptionPane.showConfirmDialog(this, "Xóa user " + username + "?", "Xác nhận", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                store.users.remove(username);
                for (Group g: store.groups.values()) { g.members.remove(username); if (username.equals(g.leader)) g.leader = null; }
                store.save(); refreshAll();
            }
        } finally { autoRefresh = prevAuto; }
    }

    private void createGroup() {
        boolean prevAuto = autoRefresh; autoRefresh = false;
        try {
            JTextField id = new JTextField();
            JTextField name = new JTextField();
            Object[] msg = {"Group ID (thư mục):", id, "Tên nhóm:", name};
            if (JOptionPane.showConfirmDialog(this, msg, "Tạo nhóm", JOptionPane.OK_CANCEL_OPTION)==JOptionPane.OK_OPTION) {
                if (store.groups.containsKey(id.getText())) { JOptionPane.showMessageDialog(this, "Group đã tồn tại"); return; }
                Group g = new Group(); g.id = id.getText().trim(); g.name = name.getText().trim();
                store.groups.put(g.id, g); store.save();
                new File(DataStore.GROUPS_DIR, g.id).mkdirs();
                refreshAll();
            }
        } finally { autoRefresh = prevAuto; }
    }

    private void addMember(String groupId) {
        if (groupId == null) return;
        boolean prevAuto = autoRefresh; autoRefresh = false;
        try {
            String user = (String) JOptionPane.showInputDialog(this, "Chọn user", "Thêm thành viên", JOptionPane.PLAIN_MESSAGE, null, store.users.keySet().toArray(), null);
            if (user == null) return;
            Group g = store.groups.get(groupId); if (g == null) return;
            g.members.add(user);
            store.users.get(user).groupIds.add(groupId);
            store.save(); showMembers(groupId); refreshAll();
        } finally { autoRefresh = prevAuto; }
    }

    private void setLeader(String groupId) {
        if (groupId == null) return;
        boolean prevAuto = autoRefresh; autoRefresh = false;
        try {
            Group g = store.groups.get(groupId); if (g == null) return;
            String user = (String) JOptionPane.showInputDialog(this, "Chọn trưởng nhóm", "Gán leader", JOptionPane.PLAIN_MESSAGE, null, g.members.toArray(), null);
            if (user == null) return;
            g.leader = user;
            for (String u: g.members) store.users.get(u).leaderByGroup.put(groupId, u.equals(user));
            store.save(); showMembers(groupId);
        } finally { autoRefresh = prevAuto; }
    }

    private void openGroupDir(String groupId) {
        if (groupId == null) return;
        File dir = new File(DataStore.GROUPS_DIR, groupId);
        dir.mkdirs();
        try {
            Desktop.getDesktop().open(dir);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Không mở được thư mục: " + e.getMessage());
        }
    }
}
