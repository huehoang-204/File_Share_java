package com.fileshare.server;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

class Security {
    static String randomSalt() {
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        return Base64.getEncoder().encodeToString(s);
    }

    static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            return Base64.getEncoder().encodeToString(d);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static String hashPassword(String raw, String salt) {
        return sha256(raw + ":" + salt);
    }
}
