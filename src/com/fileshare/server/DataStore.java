package com.fileshare.server;

import java.io.*;
import java.util.*;

class DataStore implements Serializable {
    Map<String, User> users = new HashMap<>(); // username -> User
    Map<String, Group> groups = new HashMap<>(); // groupId -> Group

    static final File ROOT = new File("data");
    static final File GROUPS_DIR = new File(ROOT, "groups");
    static final File STORE = new File(ROOT, "datastore.bin");

    synchronized void save() {
        try {
            ROOT.mkdirs();
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(STORE))) {
                oos.writeObject(this);
            }
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    static synchronized DataStore loadOrInit() {
        ROOT.mkdirs(); GROUPS_DIR.mkdirs();
        if (STORE.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(STORE))) {
                return (DataStore) ois.readObject();
            } catch (Exception e) { e.printStackTrace(); }
        }

        // === Nếu chưa có file, tạo dữ liệu mặc định ===
        DataStore ds = new DataStore();

        // Tạo user mặc định
        ds.addUser("user1", "123");
        ds.addUser("user2", "123");
        ds.addUser("user3", "123");

        // Tạo nhóm mặc định
        ds.addGroup("g1", "Nhóm chia sẻ 1");
        ds.addGroup("g2", "Nhóm chia sẻ 2");

        // Gán user vào group
        ds.addUserToGroup("user1", "g1", true);   // user1 = leader group g1
        ds.addUserToGroup("user2", "g1", false);  // user2 = member group g1
        ds.addUserToGroup("user3", "g2", true);   // user3 = leader group g2

        // Lưu lại để lần sau load
        ds.save();
        return ds;
    }

    // === Helper methods để thêm user, group, join group ===
    void addUser(String username, String password) {
        User u = new User();
        u.username = username;
        u.salt = Security.randomSalt();
        u.passwordHash = Security.hashPassword(password, u.salt);
        users.put(username, u);
    }

    void addGroup(String gid, String name) {
        Group g = new Group();
        g.id = gid;
        g.name = name;
        groups.put(gid, g);
        new File(GROUPS_DIR, gid).mkdirs(); // tạo thư mục group
    }

    void addUserToGroup(String username, String gid, boolean leader) {
        User u = users.get(username);
        if (u == null) return;
        u.groupIds.add(gid);
        u.leaderByGroup.put(gid, leader);
    }
}
