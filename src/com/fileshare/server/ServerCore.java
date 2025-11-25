package com.fileshare.server;

import com.fileshare.common.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.net.ssl.*;
import java.security.KeyStore;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.*;

class ServerCore {
    private static final long MAX_UPLOAD_BYTES = 50L * 1024 * 1024; // 50 MB per file
    private static final long GROUP_QUOTA_BYTES = 1024L * 1024 * 1024; // 1 GB per group
    final int port;
    final DataStore store;
    final ActivityLog log;
    final FileService fs = new FileService();
    final Map<String, Socket> sessions = new ConcurrentHashMap<>(); // username -> socket
    volatile boolean running = true;

    ServerCore(int port, DataStore store, ActivityLog log) {
        this.port = port; this.store = store; this.log = log;
    }

    void startAsync() {
        Thread t = new Thread(this::acceptLoop, "Server-AcceptLoop");
        t.setDaemon(true); t.start();
    }
    //lắng nghe kết nối 
    void acceptLoop() {
        ServerSocket ss = null;
        try {
            java.io.File ksFile = new java.io.File("server.jks");
            if (ksFile.exists()) {
                // try create SSLServerSocket
                try {
                    // Tải keystore của server (server.jks) để bật TLS. Keystore này phải
                    // chứa private key và chứng chỉ của server. Mặc định mã sẽ tìm file
                    // `server.jks` ở thư mục gốc và dùng mật khẩu ví dụ `changeit`.
                    // Nếu keystore tồn tại, chương trình sẽ tạo `SSLServerSocket` để
                    // mã hóa các kết nối đến. Nếu quá trình khởi tạo TLS thất bại thì
                    // server sẽ chuyển về `ServerSocket` thường (tốt cho phát triển).
                    KeyStore ks = KeyStore.getInstance("JKS");
                    try (java.io.FileInputStream fis = new java.io.FileInputStream(ksFile)) { ks.load(fis, "changeit".toCharArray()); }
                    KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
                    kmf.init(ks, "changeit".toCharArray());
                    SSLContext ctx = SSLContext.getInstance("TLS");
                    ctx.init(kmf.getKeyManagers(), null, null);
                    SSLServerSocketFactory ssf = ctx.getServerSocketFactory();
                    ss = ssf.createServerSocket(port);
                    log.log("Server started (TLS) on port " + port);
                } catch (Exception ex) {
                    log.log("Failed to start TLS server, fallback to plain: " + ex.getMessage());
                    ss = new ServerSocket(port);
                }
            } else {
                ss = new ServerSocket(port);
                log.log("Server started on port " + port);
            }

            while (running) {
                Socket s = ss.accept();
                new Thread(() -> handleClient(s), "ClientHandler-"+s.getPort()).start();
            }
        } catch (IOException e) {
            log.log("Server stopped: " + e.getMessage());
        } finally {
            if (ss != null) try { ss.close(); } catch (IOException ignore) {}
        }
    }
    //đảm bảo Mỗi client chạy thread
    void handleClient(Socket sock) {
        String user = "?";
        try (ObjectOutputStream oos = new ObjectOutputStream(sock.getOutputStream());
             ObjectInputStream ois = new ObjectInputStream(sock.getInputStream())) {

            while (true) {
                Object obj = ois.readObject();
                if (!(obj instanceof Packet)) break;
                Packet p = (Packet) obj;
                switch (p.type) {
                    case CLIENT_LOGIN: {
                        String u = p.h("username");
                        String pw = p.h("password");
                        if (verifyClient(u, pw)) {
                            user = u; sessions.put(u, sock);
                            log.log(u + " logged in");
                            oos.writeObject(new Packet(PacketType.CLIENT_LOGIN_OK));
                        } else {
                            oos.writeObject(new Packet(PacketType.CLIENT_LOGIN_FAIL).withHeader("msg","Invalid credentials"));
                        }
                        oos.reset();
                        break;
                    }
                    case LIST_GROUPS: {
                        ensureLogged(user);
                        User U = store.users.get(user);
                        List<String> gids = new ArrayList<>(U.groupIds);

                        Packet resp = new Packet(PacketType.GROUPS_LIST);
                        resp.body = gids;                 // chỉ gán dữ liệu serializable
                        oos.writeObject(resp);
                        oos.reset();
                        break;
                    }

                    case LIST_ENTRIES: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String rel = p.h("path"); if (rel == null) rel = ".";
                        if (!inGroup(user, gid)) { sendError(oos, "Not member of group"); break; }
                        fs.ensureGroupDir(gid);
                        File dir = fs.resolvePath(gid, rel);
                        File[] files = dir.listFiles();
                        List<FileEntry> list = new ArrayList<>();
                        if (files != null) for (File f: files) list.add(FileEntry.fromFile(f));
                        Packet resp = new Packet(PacketType.ENTRIES_LIST); resp.body = list;
                        oos.writeObject(resp); oos.reset();
                        break;
                    }
                    case CREATE_FOLDER: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String rel = p.h("path");
                        if (!inGroup(user, gid)) { sendError(oos, "Not member of group"); break; }
                        fs.createFolder(gid, rel);
                        log.log(user + " mkdir ["+gid+"]:/" + rel);
                        oos.writeObject(new Packet(PacketType.CREATE_FOLDER_OK)); oos.reset();
                        break;
                    }
                    case DELETE_ENTRY: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String rel = p.h("path");
                        if (!isLeader(user, gid)) { sendFail(oos, PacketType.DELETE_ENTRY_FAIL, "Only leader can delete"); break; }
                        File target = fs.resolvePath(gid, rel);
                        if (!target.exists()) { sendFail(oos, PacketType.DELETE_ENTRY_FAIL, "Not found"); break; }
                        if (!new FileService().deleteRecursive(target)) { sendFail(oos, PacketType.DELETE_ENTRY_FAIL, "Delete failed"); break; }
                        log.log(user + " delete ["+gid+"]:/" + rel);
                        oos.writeObject(new Packet(PacketType.DELETE_ENTRY_OK)); oos.reset();
                        break;
                    }
                    case START_UPLOAD: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String rel = p.h("path"); // file path relative to group root
                        String sizeHeader = p.h("size");
                        long expectedSize = -1;
                        try { if (sizeHeader != null) expectedSize = Long.parseLong(sizeHeader); } catch (NumberFormatException ignored) {}
                        if (!inGroup(user, gid)) { sendError(oos, "Not member of group"); break; }
                            // resolve final path and write to a temporary file first
                            File out = fs.resolvePath(gid, rel);
                            // ensure parent exists
                            out.getParentFile().mkdirs();
                            // enforce per-file and per-group quotas if client provided size
                            if (expectedSize >= 0) {
                                if (expectedSize > MAX_UPLOAD_BYTES) { sendFail(oos, PacketType.UPLOAD_ERROR, "File too large (limit " + MAX_UPLOAD_BYTES + " bytes)"); break; }
                                long currentGroupSize = dirSize(fs.groupDir(gid));
                                long existing = out.exists() ? out.length() : 0L;
                                long delta = Math.max(0L, expectedSize - existing);
                                if (currentGroupSize + delta > GROUP_QUOTA_BYTES) { sendFail(oos, PacketType.UPLOAD_ERROR, "Group quota exceeded"); break; }
                            }
                            File tmp = new File(out.getParentFile(), out.getName() + ".uploading");
                            // ready the client
                            oos.writeObject(new Packet(PacketType.UPLOAD_READY)); oos.reset();
                            try (FileOutputStream fos = new FileOutputStream(tmp);
                                 FileLock lock = fs.lockExclusive(fos)) {
                                while (true) {
                                    Object ch = ois.readObject();
                                    if (!(ch instanceof Packet)) { break; }
                                    Packet cp = (Packet) ch;
                                    if (cp.type != PacketType.FILE_CHUNK) { break; }
                                    FileChunk fc = (FileChunk) cp.body;
                                    // simple safety: avoid overly large single chunk
                                    if (fc.bytes != null && fc.bytes.length > 0) fos.write(fc.bytes);
                                    if (fc.last) break;
                                }
                                fos.flush();
                            }
                            // atomic move: try rename, fallback to copy+delete
                            boolean moved = tmp.renameTo(out);
                            if (!moved) {
                                try (FileInputStream fin = new FileInputStream(tmp);
                                     FileOutputStream fout = new FileOutputStream(out)) {
                                    byte[] buf = new byte[64*1024]; int n;
                                    while ((n = fin.read(buf)) != -1) fout.write(buf, 0, n);
                                }
                                tmp.delete();
                            }
                            // verify checksum if provided
                            String expectedSha = p.h("sha256");
                            if (expectedSha != null && !expectedSha.trim().isEmpty()) {
                                String actual = computeSha256Hex(out);
                                if (!expectedSha.equalsIgnoreCase(actual)) {
                                    out.delete();
                                    log.log(user + " upload checksum mismatch ["+gid+"]:/'" + rel + " expected="+expectedSha+" actual="+actual);
                                    sendFail(oos, PacketType.UPLOAD_ERROR, "Checksum mismatch");
                                    break;
                                }
                            }
                            log.log(user + " upload ["+gid+"]:/'" + rel + " ("+out.length()+" bytes)");
                            oos.writeObject(new Packet(PacketType.UPLOAD_DONE)); oos.reset();
                        break;
                    }
                    case START_DOWNLOAD: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String rel = p.h("path");
                        if (!inGroup(user, gid)) { sendError(oos, "Not member of group"); break; }
                        File in = fs.resolvePath(gid, rel);
                        if (!in.exists() || !in.isFile()) { sendError(oos, "File not found"); break; }
                        oos.writeObject(new Packet(PacketType.DOWNLOAD_READY).withHeader("size", String.valueOf(in.length())));
                        oos.reset();
                        byte[] buf = new byte[64 * 1024];
                        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(in))) {
                            int n; int seq = 0; long sent = 0;
                            while ((n = bis.read(buf)) != -1) {
                                boolean last = (sent + n) >= in.length();
                                byte[] chunk = (n == buf.length) ? buf.clone() : java.util.Arrays.copyOf(buf, n);
                                Packet pc = new Packet(PacketType.DOWNLOAD_CHUNK);
                                pc.body = new FileChunk(rel, seq++, chunk, last);
                                oos.writeObject(pc); oos.reset();
                                sent += n;
                            }
                        }
                        log.log(user + " download ["+gid+"]:/" + rel + " ("+in.length()+" bytes)");
                        oos.writeObject(new Packet(PacketType.DOWNLOAD_DONE)); oos.reset();
                        break;
                    }
                 // com/fileshare/server/ServerCore.java  (bên trong switch)
                    case CREATE_GROUP_REQUEST: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String gname = p.h("groupName");

                        if (gid == null || gid.trim().isEmpty()) {
                            sendFail(oos, PacketType.CREATE_GROUP_FAIL, "groupId is empty");
                            break;
                        }
                        // hạn chế ký tự lạ để tránh path xấu
                        if (!gid.matches("[a-zA-Z0-9._-]+")) {
                            sendFail(oos, PacketType.CREATE_GROUP_FAIL, "groupId contains invalid characters");
                            break;
                        }
                        if (store.groups.containsKey(gid)) {
                            sendFail(oos, PacketType.CREATE_GROUP_FAIL, "Group already exists");
                            break;
                        }

                        Group g = new Group();
                        g.id = gid.trim();
                        g.name = (gname == null ? gid : gname.trim());
                        g.leader = user;
                        g.members.add(user);

                        store.groups.put(g.id, g);

                        User U = store.users.get(user);
                        U.groupIds.add(g.id);
                        U.leaderByGroup.put(g.id, true);

                        store.save();
                        fs.ensureGroupDir(g.id);

                        log.log(user + " created group [" + g.id + "] and became leader");
                        oos.writeObject(new Packet(PacketType.CREATE_GROUP_OK));
                        oos.reset();
                        break;
                    }

                    case JOIN_GROUP_REQUEST: {
                        ensureLogged(user);
                        String gid = p.h("groupId");

                        Group g = store.groups.get(gid);
                        if (g == null) {
                            sendFail(oos, PacketType.JOIN_GROUP_FAIL, "Group not found");
                            break;
                        }
                        if (!g.members.contains(user)) {
                            g.members.add(user);
                            User U = store.users.get(user);
                            U.groupIds.add(gid);
                            U.leaderByGroup.put(gid, Boolean.FALSE);
                            store.save();
                            log.log(user + " joined group [" + gid + "]");
                        }
                        oos.writeObject(new Packet(PacketType.JOIN_GROUP_OK));
                        oos.reset();
                        break;
                    }
                    case INVITE_MEMBER_REQUEST: {
                        ensureLogged(user);
                        String gid = p.h("groupId");
                        String invitee = p.h("invitee");

                        Group g = store.groups.get(gid);
                        if (g == null) {
                            sendFail(oos, PacketType.INVITE_MEMBER_FAIL, "Group not found");
                            break;
                        }
                        // chỉ leader mới được mời
                        if (!isLeader(user, gid)) {
                            sendFail(oos, PacketType.INVITE_MEMBER_FAIL, "Only leader can invite");
                            break;
                        }
                        // user được mời phải tồn tại
                        User target = store.users.get(invitee);
                        if (target == null) {
                            sendFail(oos, PacketType.INVITE_MEMBER_FAIL, "User not found");
                            break;
                        }
                        // thêm vào nhóm nếu chưa có
                        if (!g.members.contains(invitee)) {
                            g.members.add(invitee);
                            target.groupIds.add(gid);
                            target.leaderByGroup.put(gid, Boolean.FALSE);
                            store.save();
                            log.log(user + " invited " + invitee + " to group [" + gid + "]");
                        }
                        oos.writeObject(new Packet(PacketType.INVITE_MEMBER_OK));
                        oos.reset();
                        break;
                    }
                    case USER_LIST_REQUEST: {
                        List<Map<String,String>> list = new ArrayList<>();
                        for (User u : store.users.values()) {
                            Map<String,String> m = new HashMap<>();
                            m.put("username", u.username);
                            m.put("online", sessions.containsKey(u.username) ? "true" : "false");
                            list.add(m);
                        }
                        Packet resp = new Packet(PacketType.USER_LIST);
                        resp.body = list;
                        oos.writeObject(resp); oos.reset();
                        break;
                    }



                    default:
                        sendError(oos, "Unknown packet: " + p.type);
                }
            }
        } catch (EOFException eof) {
            // client disconnected
        } catch (Exception e) {
            log.log("Error with client "+user+": "+e.getMessage());
        } finally {
            if (user != null) sessions.remove(user);
            try { sock.close(); } catch (IOException ignore) {}
            log.log(user + " disconnected");
        }
    }

    private void ensureLogged(String user) throws IOException {
        if (user == null || user.equals("?")) throw new IOException("Not logged");
    }

    // compute total bytes used by a directory (recursive)
    private long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0L;
        long total = 0L;
        File[] kids = dir.listFiles();
        if (kids == null) return 0L;
        for (File f: kids) {
            if (f.isDirectory()) total += dirSize(f);
            else {
                String name = f.getName();
                if (name.endsWith(".uploading") || name.equals(".placeholder")) continue;
                total += f.length();
            }
        }
        return total;
    }

    private static String computeSha256Hex(File f) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (java.io.InputStream in = new java.io.FileInputStream(f)) {
                byte[] buf = new byte[8192]; int n;
                while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b: digest) sb.append(String.format("%02x", b & 0xff));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    private boolean verifyClient(String u, String pw) {
        User U = store.users.get(u);
        if (U == null) return false;
        return Security.hashPassword(pw, U.salt).equals(U.passwordHash);
    }

    private boolean inGroup(String user, String gid) {
        User U = store.users.get(user); return U != null && U.groupIds.contains(gid);
    }

    private boolean isLeader(String user, String gid) {
        User U = store.users.get(user); return U != null && Boolean.TRUE.equals(U.leaderByGroup.get(gid));
    }

    private void sendError(ObjectOutputStream oos, String msg) throws IOException {
        oos.writeObject(new Packet(PacketType.ERROR).withHeader("msg", msg)); oos.reset();
    }
    private void sendFail(ObjectOutputStream oos, PacketType type, String msg) throws IOException {
        oos.writeObject(new Packet(type).withHeader("msg", msg)); oos.reset();
    }
}
