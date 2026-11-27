# TV Guard v4 – TV + điện thoại người lớn

## Cách hoạt động
- Mỗi lần BẬT tivi (khởi động lại, hoặc tắt chờ rồi bật) luôn ở chế độ TRẺ EM.
- TRẺ EM: mỗi lượt xem 60 phút (chỉnh 5–240). Hết lượt -> tivi NGHỈ 60 phút (chỉnh 5–480):
  hiện màn hình nghỉ có đồng hồ đếm ngược, ngắt Internet (VPN hố đen) và thử tắt màn hình (tuỳ tivi có hỗ trợ).
  Hết giờ nghỉ tự bắt đầu lượt mới.
- Tắt/bật/khởi động lại tivi KHÔNG làm giờ nghỉ hết sớm: giờ nghỉ tính theo giờ thực và được lưu bền.
- Người lớn có thể: cho xem tiếp 20 phút, bắt đầu lượt mới, chuyển sang GIA ĐÌNH (nhập PIN) – từ điện thoại hoặc trên tivi.
- GIA ĐÌNH: nhập PIN để vào, xem không giới hạn (hoặc hẹn 30/60/120 phút rồi tự về TRẺ EM).
  Tắt tivi hoặc khởi động lại -> tự về TRẺ EM.
- Vào Cài đặt hệ thống / cài app đều phải nhập PIN. PIN hiện ● ○ (không lộ số), chặn chụp/ghi màn hình.
- Lưu ý: app không thể ngắt nguồn tivi thật sự. "Tivi tắt" = màn hình nghỉ + ngắt mạng + thử khoá màn hình.

## Chuyển chế độ dễ dàng
Bằng remote:
  - Mở app TV Guard (nên ghim lên hàng ứng dụng ở màn hình chính), hoặc
  - Bấm GIỮ phím Menu (☰) khoảng 1,5 giây (hoặc phím Đỏ / Settings nếu remote có).
  Màn hình chính có 3 nút to: Chế độ TRẺ EM (không cần PIN), Chế độ GIA ĐÌNH (nhập PIN), Cài đặt phụ huynh (nhập PIN).
Bằng điện thoại: app TV Guard Phụ huynh có 2 nút Trẻ em / Gia đình (nút đang dùng có màu xanh).
  Về TRẺ EM không cần PIN; sang GIA ĐÌNH và các lệnh khác cần PIN quản trị.

## 0. Build bằng GitHub Actions (không cần Android Studio)
1. Tạo kho GitHub mới. Giải nén zip, tải LÊN NỘI DUNG BÊN TRONG (settings.gradle.kts, app, phone, shared...) sao cho
   settings.gradle.kts nằm NGAY Ở TRANG CHỦ của kho (không nằm trong thư mục con).
2. Tạo file .github/workflows/build.yml (nếu web không nhận thư mục ẩn: Add file > Create new file, gõ tên
   ".github/workflows/build.yml" rồi dán nội dung của file build.yml.txt).
3. Tab Actions > Build APK > Run workflow. Xong tải gói "apks" (có 2 file APK: tivi và điện thoại).

## 1. Build 2 APK (Android Studio)
Open thư mục TVGuard > Build APK(s) cho module app (TV) và module phone (điện thoại Android).

## 2. Cài lên TV
Bật Gỡ lỗi USB/ADB trên tivi, rồi:
  adb connect IP_TIVI:5555
  adb install app-debug.apk
  adb shell appops set com.giadinh.tvguard SYSTEM_ALERT_WINDOW allow
  adb shell settings put secure enabled_accessibility_services com.giadinh.tvguard/com.giadinh.tvguard.GuardService
  adb shell settings put secure accessibility_enabled 1
(Nếu đã có dịch vụ Trợ năng khác thì nối thêm bằng ':' thay vì ghi đè.)
Mở TV Guard > tạo PIN > Cài đặt > "Cấp quyền VPN" (Đồng ý ở hộp thoại của tivi).
Nếu tivi không hiện hộp thoại VPN: adb shell appops set com.giadinh.tvguard ACTIVATE_VPN allow

## 3. Ghép đôi điện thoại
TV: TV Guard > Cài đặt (PIN) > Ghép đôi (mã QR). Điện thoại: TV Guard Phụ huynh > Quét mã QR.

## 4. Sau cùng
TẮT Gỡ lỗi USB trên tivi; bật "Ngày & giờ tự động" cho TV và điện thoại.

## Lưu ý
- Kênh điều khiển từ xa dùng dịch vụ công cộng ntfy.sh, nội dung mã hoá AES-GCM, có chống phát lại lệnh.
- Lệnh chỉ nhận khi TV đang bật và có mạng.
- Phím tắt remote phụ thuộc loại remote của Xiaomi; nếu phím không phản hồi, dùng app TV Guard ở hàng ứng dụng.
