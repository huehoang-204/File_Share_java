package com.fileshare.server;

import java.io.*;
import java.util.*;

class AdminStore implements Serializable {
    Map<String, String[]> admins = new HashMap<>(); // username -> [salt, hash]
    static final File FILE = new File(DataStore.ROOT, "admins.bin");

    static AdminStore loadOrInit() {
        if (FILE.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(FILE))) {
                return (AdminStore) ois.readObject();
            } catch (Exception e) { e.printStackTrace(); }
        }
        AdminStore s = new AdminStore();
        String salt = Security.randomSalt();
        s.admins.put("admin", new String[]{ salt, Security.hashPassword("admin123", salt)});
        s.save();
        return s;
    }

    void save() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(FILE))) {
            oos.writeObject(this);
        } catch (IOException e) { throw new RuntimeException(e); }
    }

    boolean verify(String user, String pass) {
        String[] sh = admins.get(user);
        if (sh == null) return false;
        return Security.hashPassword(pass, sh[0]).equals(sh[1]);
    }

    void createAdmin(String user, String pass) {
        String salt = Security.randomSalt();
        admins.put(user, new String[]{ salt, Security.hashPassword(pass, salt)});
        save();
    }
}
