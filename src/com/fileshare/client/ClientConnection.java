package com.fileshare.client;

import com.fileshare.common.*;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.*;
class ClientConnection implements Closeable {
    private final Socket sock;
    private final ObjectOutputStream oos;
    private final ObjectInputStream ois;

    ClientConnection(String host, int port) throws IOException {
        // Hỗ trợ TLS (tùy chọn):
        // Nếu tồn tại file `client-truststore.jks` ở thư mục gốc dự án, chương trình
        // sẽ dùng truststore đó để tạo `SSLContext` và kết nối bằng `SSLSocket` (TLS).
        // Thiết kế này cho phép bật TLS trong môi trường thực tế, nhưng vẫn fallback
        // về kết nối thường trong môi trường phát triển nếu không có truststore hoặc
        // nếu quá trình khởi tạo TLS gặp lỗi.
        Socket tempSock = null;
        java.io.File tsf = new java.io.File("client-truststore.jks");
        if (tsf.exists()) {
            try {
                java.security.KeyStore ts = java.security.KeyStore.getInstance("JKS");
                try (java.io.FileInputStream fis = new java.io.FileInputStream(tsf)) {
                    ts.load(fis, "changeit".toCharArray());
                }
                javax.net.ssl.TrustManagerFactory tmf = javax.net.ssl.TrustManagerFactory.getInstance("SunX509");
                tmf.init(ts);
                javax.net.ssl.SSLContext ctx = javax.net.ssl.SSLContext.getInstance("TLS");
                ctx.init(null, tmf.getTrustManagers(), null);
                javax.net.ssl.SSLSocketFactory sf = ctx.getSocketFactory();
                tempSock = sf.createSocket(host, port);
            } catch (Exception e) {
                tempSock = new Socket(host, port);
            }
        } else {
            tempSock = new Socket(host, port);
        }
        this.sock = tempSock;
        this.oos = new ObjectOutputStream(sock.getOutputStream());
        this.ois = new ObjectInputStream(sock.getInputStream());
    }

    boolean login(String user, String pass) throws Exception {
        Packet p = new Packet(PacketType.CLIENT_LOGIN).withHeader("username", user).withHeader("password", pass);
        oos.writeObject(p); oos.reset();
        Object resp = ois.readObject();
        if (!(resp instanceof Packet)) return false;
        Packet r = (Packet) resp;
        return r.type == PacketType.CLIENT_LOGIN_OK;
    }

    List<String> listGroups() throws Exception {
        oos.writeObject(new Packet(PacketType.LIST_GROUPS)); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.GROUPS_LIST) throw new IOException("LIST_GROUPS failed");
        return (List<String>) r.body;
    }

    List<FileEntry> listEntries(String groupId, String rel) throws Exception {
        if (rel == null) rel = ".";
        Packet p = new Packet(PacketType.LIST_ENTRIES).withHeader("groupId", groupId).withHeader("path", rel);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.ENTRIES_LIST) throw new IOException("LIST_ENTRIES failed");
        return (List<FileEntry>) r.body;
    }
 // com.fileshare.client.ClientConnection.java
 // com.fileshare.client.ClientConnection.java
    public List<Map<String,String>> getUserList() throws Exception {
        Packet p = new Packet(PacketType.USER_LIST_REQUEST);
        oos.writeObject(p); 
        oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.USER_LIST) {
            throw new IOException("USER_LIST failed");
        }
        return (List<Map<String,String>>) r.body;
    }



    void createFolder(String groupId, String rel) throws Exception {
        Packet p = new Packet(PacketType.CREATE_FOLDER).withHeader("groupId", groupId).withHeader("path", rel);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.CREATE_FOLDER_OK) throw new IOException("CREATE_FOLDER failed: "+r.h("msg"));
    }

    void deleteEntry(String groupId, String rel) throws Exception {
        Packet p = new Packet(PacketType.DELETE_ENTRY).withHeader("groupId", groupId).withHeader("path", rel);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.DELETE_ENTRY_OK) throw new IOException("DELETE failed: "+r.h("msg"));
    }

    void upload(String groupId, String relPath, File file) throws Exception {
        // compute sha256 checksum to allow server-side verification
        String sha256 = computeSha256Hex(file);
        Packet p = new Packet(PacketType.START_UPLOAD)
                .withHeader("groupId", groupId)
                .withHeader("path", relPath)
                .withHeader("size", String.valueOf(file.length()))
                // Gửi thêm SHA-256 để server có thể kiểm tra toàn vẹn dữ liệu sau khi
                // nhận xong file (so sánh checksum của file nhận với checksum do client gửi).
            .withHeader("sha256", sha256);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.UPLOAD_READY) throw new IOException("UPLOAD not ready");
        byte[] buf = new byte[64 * 1024]; int n; int seq = 0; long sent = 0;
        try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            while ((n = bis.read(buf)) != -1) {
                boolean last = (sent + n) >= file.length();
                byte[] chunk = (n == buf.length) ? buf.clone() : java.util.Arrays.copyOf(buf, n);
                Packet pc = new Packet(PacketType.FILE_CHUNK);
                pc.body = new FileChunk(relPath, seq++, chunk, last);
                oos.writeObject(pc); oos.reset();
                sent += n;
            }
        }
        Packet done = (Packet) ois.readObject();
        if (done.type != PacketType.UPLOAD_DONE) throw new IOException("UPLOAD failed: "+done.h("msg"));
    }

    private static String computeSha256Hex(File f) throws Exception {
        // Hàm trợ giúp: tính SHA-256 (hex) cho một file. Dùng để gửi metadata toàn vẹn
        // lên server trước khi upload, giúp server xác minh file sau khi nhận xong.
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        try (java.io.InputStream in = new java.io.FileInputStream(f)) {
            byte[] buf = new byte[8192]; int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        byte[] digest = md.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b: digest) sb.append(String.format("%02x", b & 0xff));
        return sb.toString();
    }

    void download(String groupId, String relPath, File saveTo) throws Exception {
        Packet p = new Packet(PacketType.START_DOWNLOAD).withHeader("groupId", groupId).withHeader("path", relPath);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.DOWNLOAD_READY) throw new IOException("DOWNLOAD not ready: "+r.h("msg"));
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(saveTo))) {
            while (true) {
                Packet pc = (Packet) ois.readObject();
                if (pc.type == PacketType.DOWNLOAD_CHUNK) {
                    FileChunk fc = (FileChunk) pc.body;
                    bos.write(fc.bytes);
                    if (fc.last) break;
                } else if (pc.type == PacketType.DOWNLOAD_DONE) break;
                else throw new IOException("Unexpected packet: "+pc.type);
            }
        }
    }
    void inviteMember(String groupId, String username) throws Exception {
        Packet p = new Packet(PacketType.INVITE_MEMBER_REQUEST)
                .withHeader("groupId", groupId)
                .withHeader("invitee", username);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.INVITE_MEMBER_OK) {
            throw new IOException("INVITE_MEMBER failed: " + r.h("msg"));
        }
    }

 // com/fileshare/client/ClientConnection.java

    void createGroup(String groupId, String groupName) throws Exception {
        Packet p = new Packet(PacketType.CREATE_GROUP_REQUEST)
                .withHeader("groupId", groupId)
                .withHeader("groupName", groupName == null ? "" : groupName);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.CREATE_GROUP_OK) {
            throw new IOException("CREATE_GROUP failed: " + r.h("msg"));
        }
    }

    void joinGroup(String groupId) throws Exception {
        Packet p = new Packet(PacketType.JOIN_GROUP_REQUEST)
                .withHeader("groupId", groupId);
        oos.writeObject(p); oos.reset();
        Packet r = (Packet) ois.readObject();
        if (r.type != PacketType.JOIN_GROUP_OK) {
            throw new IOException("JOIN_GROUP failed: " + r.h("msg"));
        }
    }


    @Override public void close() throws IOException { try { oos.close(); } finally { try { ois.close(); } finally { sock.close(); } } }
}

