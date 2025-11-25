# FileShare — Hướng dẫn dự án

Mô tả ngắn
- Đây là một ứng dụng chia sẻ file đơn giản (Java + Swing) gồm client và server.
- Tính năng chính: đăng ký/đăng nhập, tạo/tham gia nhóm, upload/download file trong thư mục nhóm, quyền trưởng nhóm (xóa), dashboard thống kê trên server.

Yêu cầu môi trường
- Java 8+ (JDK 8 hoặc mới hơn).
- Trên Windows: PowerShell sẵn có.
- Lưu ý mã nguồn chứa tiếng Việt → biên dịch với encoding UTF-8.

Cấu trúc dự án (chính)
- `src/com/fileshare/client` — mã client (UI + `ClientConnection`).
- `src/com/fileshare/server` — mã server (core, dashboard, file service, datastore).
- `src/com/fileshare/common` — các lớp chia sẻ (Packet, PacketType, FileEntry, v.v.).
- `data/` — thư mục lưu trữ runtime (gồm `groups/` và `datastore.bin`).

Biên dịch (từ thư mục dự án gốc)
```powershell
$files = Get-ChildItem -Recurse -Filter *.java -Path src | ForEach-Object { $_.FullName }
javac -encoding UTF-8 -d bin $files
```

Chạy server
```powershell
java -cp bin com.fileshare.server.ServerApp
```
- Server sẽ tự phát hiện `server.jks` trong thư mục chạy để bật TLS. Nếu không có, server chạy không TLS (mạng plaintext).

Chạy client (một cửa sổ terminal riêng)
```powershell
java -cp bin com.fileshare.client.ClientApp
```
- Client sẽ tìm `client-truststore.jks` để kết nối TLS tới server (nếu server dùng TLS). Nếu không tìm thấy, client fallback về kết nối plain.

Tạo keystore / truststore (dùng cho TLS)
- Ví dụ nhanh (dùng `keytool`, mật khẩu là `changeit` trong ví dụ):
```powershell
# Tạo server keystore (self-signed) — chỉ cho test
keytool -genkeypair -alias fileshare-server -keyalg RSA -keysize 2048 `
 -keystore server.jks -storepass changeit -keypass changeit `
 -dname "CN=localhost, OU=Dev, O=MyOrg, L=City, S=State, C=VN"

# Xuất certificate
keytool -exportcert -alias fileshare-server -file server.cer -keystore server.jks -storepass changeit

# Tạo truststore cho client và import cert server
keytool -importcert -alias fileshare-server -file server.cer -keystore client-truststore.jks -storepass changeit -noprompt
```
- Sau khi có `server.jks` và `client-truststore.jks`, server/client sẽ kết nối qua TLS.
- Khuyến nghị: **đổi mật khẩu** `changeit` và không commit các file .jks/.cer vào repo.
- Nếu muốn chuyển sang PKCS12 (chuẩn hơn):
```powershell
keytool -importkeystore -srckeystore server.jks -destkeystore server.jks -deststoretype pkcs12 -srcstorepass changeit -deststorepass changeit
```

Cơ chế truyền file & bảo mật hiện tại
- Kênh truyền:
  - Nếu TLS bật: dữ liệu truyền qua socket được mã hoá (SSLSocket/SSLServerSocket).
  - Nếu TLS không bật: dữ liệu truyền qua TCP plaintext.
- Protocol:
  - Control messages (đăng nhập, list, start upload/download, v.v.) đóng gói trong `Packet` và gửi qua `ObjectOutputStream` (tuần tự hoá -> bytes).
  - File data truyền theo chunk: `FILE_CHUNK` chứa `byte[]` (nhị phân) được đóng gói trong `Packet`.
  - Client gửi header `sha256` (chuỗi hex) trong `START_UPLOAD` để server kiểm tra toàn vẹn sau khi nhận file.
- Atomic & an toàn:
  - Server ghi upload vào file tạm `*.uploading` rồi rename/copy thành file đích khi hoàn tất.
  - Server kiểm tra SHA-256 sau khi nhận; nếu mismatch thì xóa file tạm và trả lỗi.
  - Server kiểm tra kích thước file (MAX_UPLOAD_BYTES) và quota nhóm (GROUP_QUOTA_BYTES) trước khi nhận.

Các file khóa / key liên quan
- `server.jks`: keystore (JKS) chứa private key + cert X.509 của server (binary, không phải chuỗi). Dùng để bật TLS.
- `client-truststore.jks`: truststore chứa cert server để client tin server. Dùng cho TLS client.
- `server.cer`: certificate xuất từ keystore (có thể ở dạng DER biner hoặc PEM nếu dùng `-rfc`).
- (Tùy chọn) `keystore.jceks`: keystore kiểu JCEKS để lưu SecretKey AES nếu bạn triển khai mã hoá file at-rest.

Những thay đổi chính tôi đã thực hiện cho dự án
- Client:
  - Loại bỏ host/port khỏi UI login; thêm cố gắng kết nối TLS nếu `client-truststore.jks` tồn tại.
  - Trước khi upload tính SHA-256 và gửi trong header `START_UPLOAD`.
- Server:
  - Nếu `server.jks` tồn tại thì khởi tạo `SSLServerSocket` để bật TLS; nếu không thì fallback sang `ServerSocket` thường.
  - Upload: ghi vào file tạm `*.uploading`, kiểm tra checksum SHA-256, rename/copy sang file đích.
  - Thêm kiểm tra kích thước file và quota nhóm (mặc định 50MB/file, 1GB/group trong mã).
  - Dashboard: thêm tab Thống kê + biểu đồ (pie, line) và cải tiến refresh (giữ selection, option auto-refresh).

Kiểm thử nhanh
- Biên dịch + chạy server và client như trên.
- Tạo hoặc import `server.jks` và `client-truststore.jks` để bật TLS trước khi chạy server/client.
- Tạo group, tham gia group, thử upload file nhỏ, kiểm tra `data/groups/<groupId>` xuất hiện file, xem `ActivityLog`.
- Thử upload file lớn hơn giới hạn (50MB) để xem server từ chối.
- Thử tắt TLS (xóa/di chuyển `server.jks`) để kiểm tra fallback không mã hóa.

Lưu ý quan trọng về bảo mật
- Không commit các file keystore/truststore (.jks) hoặc private key vào hệ thống quản lý mã nguồn.
- Đổi mật khẩu mặc định; đặt quyền file để chỉ user chạy server có thể đọc.
- Với production: dùng chứng chỉ do CA cấp, quản lý keys bằng KMS (AWS KMS, Azure KeyVault) nếu có thể.

