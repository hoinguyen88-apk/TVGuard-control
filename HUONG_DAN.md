# TV Guard v3 – TV + điện thoại người lớn

## Cách hoạt động
- Mỗi lần BẬT tivi (khởi động lại, hoặc tắt chờ rồi bật) luôn ở chế độ TRẺ EM.
- TRẺ EM: mỗi lượt 20 phút. Hết lượt -> CHỈ NGẮT INTERNET (VPN hố đen), không khoá màn hình.
  Tắt/bật/khởi động lại vẫn bị ngắt, không tự được cấp lại. Muốn xem tiếp: người lớn cấp thêm 20 phút
  (điện thoại, hoặc app TV Guard trên tivi + PIN). Sang ngày mới tự có lượt mới (có thể tắt trong Cài đặt).
- GIA ĐÌNH: phải nhập PIN để vào, xem không giới hạn (hoặc đặt 30/60/120 phút rồi tự về Trẻ em).
  Tắt tivi hoặc khởi động lại -> tự về TRẺ EM.
- Vào Cài đặt hệ thống / cài app đều phải nhập PIN. PIN hiện ●●●● và chặn chụp màn hình.

## Chuyển chế độ dễ dàng
Bằng remote:
  - Mở app TV Guard (nên ghim lên hàng ứng dụng ở màn hình chính), hoặc
  - Bấm GIỮ phím Menu (☰) khoảng 1,5 giây, hoặc phím Đỏ / Settings nếu remote có.
  Màn hình chính có nút to: "Chuyển sang GIA ĐÌNH (nhập PIN)", "Cấp thêm 20 phút (nhập PIN)", "Về TRẺ EM" (không cần PIN).
Bằng điện thoại: app TV Guard Phụ huynh có nút Chế độ TRẺ EM / GIA ĐÌNH (✅ cho biết đang ở chế độ nào).
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
