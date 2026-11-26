package com.giadinh.tvguard

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.net.VpnService
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import com.giadinh.tvguard.shared.Proto
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Màn hình chính (không cần PIN để xem): chọn chế độ bằng remote.
 *  - Đang TRẺ EM: "Chuyển sang GIA ĐÌNH" cần PIN -> xem không giới hạn.
 *  - Đang GIA ĐÌNH: "Về TRẺ EM" không cần PIN.
 * Cài đặt nâng cao luôn cần PIN.
 */
class MainActivity : Activity() {
    private var screen = "home"          // home | pin | settings | pairing
    private var pinTitle = ""
    private var after: (() -> Unit)? = null
    private var authedUntil = 0L
    private var changingPin = false
    private var first: String? = null
    private var msg = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        show()
    }

    override fun onResume() {
        super.onResume()
        show()
    }

    private fun page(vararg views: View): ScrollView {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(48), dp(24), dp(48), dp(24))
        }
        views.forEach {
            col.addView(it, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        return ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#111827"))
            addView(col)
        }
    }

    private fun qr(text: String): Bitmap {
        val size = 480
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            b.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return b
    }

    private fun requirePin(title: String, action: () -> Unit) {
        pinTitle = title
        after = action
        msg = ""
        screen = "pin"
        show()
    }

    private fun show() {
        Prefs.sync(this)
        if (screen == "settings" && System.currentTimeMillis() > authedUntil) screen = "home"
        when {
            !Prefs.hasPin(this) || changingPin -> setContentView(page(
                label("Tạo mã PIN phụ huynh (4–8 số)", 26f),
                label(msg, 18f),
                pinPad { p ->
                    if (first == null) {
                        first = p
                        msg = "Nhập lại mã PIN để xác nhận"
                    } else if (first == p) {
                        Prefs.setPin(this, p)
                        changingPin = false
                        first = null
                        msg = ""
                    } else {
                        first = null
                        msg = "Không khớp, nhập lại từ đầu"
                    }
                    show()
                }
            ))
            screen == "pin" -> setContentView(page(
                label(pinTitle, 26f),
                label(msg, 18f),
                pinPad { p ->
                    if (Prefs.banSeconds(this) > 0) {
                        msg = "Thử lại sau ${Prefs.banSeconds(this)} giây"
                    } else if (Prefs.checkPin(this, p)) {
                        val a = after
                        after = null
                        screen = "home"
                        msg = ""
                        a?.invoke()
                    } else {
                        msg = if (Prefs.banSeconds(this) > 0) "Sai quá nhiều, đợi 60 giây" else "Sai mã PIN"
                    }
                    show()
                },
                btn("Huỷ") { after = null; screen = "home"; msg = ""; show() }
            ))
            screen == "pairing" -> showPairing()
            screen == "settings" -> showSettings()
            else -> showHome()
        }
    }

    private fun statusText(): String {
        val kids = Prefs.kids(this)
        val fl = Prefs.familyLimitMin(this)
        val net = if (NetBlockService.running) "\nInternet: ĐANG NGẮT (hết giờ)" else ""
        return if (kids) {
            val total = Prefs.kidsTotalMin(this)
            val used = Prefs.usedKidsMs(this) / 60_000
            "Chế độ: TRẺ EM\nĐã xem $used/$total phút" +
                (if (Prefs.exhausted(this)) " – HẾT GIỜ" else " (còn ${maxOf(0, total - used)} phút)") + net
        } else {
            "Chế độ: GIA ĐÌNH – " + (if (fl == 0) "không giới hạn" else "đã dùng ${Prefs.famUsedMs(this) / 60_000}/$fl phút") + net
        }
    }

    private fun showHome() {
        val kids = Prefs.kids(this)
        val views = ArrayList<View>()
        views.add(label("📺 TV Guard", 32f))
        views.add(label(statusText(), 22f))
        if (msg.isNotEmpty()) views.add(label(msg, 18f))
        if (kids) {
            views.add(btn("👨‍👩‍👧 Chuyển sang chế độ GIA ĐÌNH (nhập PIN – không giới hạn)") {
                requirePin("Nhập mã PIN để chuyển sang chế độ Gia đình") {
                    Prefs.startFamily(this, 0)
                    msg = "✅ Đã chuyển sang chế độ Gia đình"
                }
            })
            views.add(btn("➕ Cấp thêm 20 phút cho trẻ (nhập PIN)") {
                requirePin("Nhập mã PIN để cấp thêm 20 phút") {
                    Prefs.addKidsBonus(this, 20)
                    msg = "✅ Đã cấp thêm 20 phút"
                }
            })
        } else {
            views.add(btn("🧒 Về chế độ TRẺ EM") { Prefs.endFamily(this); msg = "Đã về chế độ Trẻ em"; show() })
        }
        views.add(btn("⚙ Cài đặt phụ huynh (nhập PIN)") {
            requirePin("Nhập mã PIN để vào Cài đặt") {
                authedUntil = System.currentTimeMillis() + 10 * 60_000L
                screen = "settings"
            }
        })
        views.add(btn("Thoát") { finish() })
        setContentView(page(*views.toTypedArray()))
    }

    private fun showPairing() {
        val code = Prefs.pairCode(this)
        setContentView(page(
            label("Ghép đôi với điện thoại người lớn", 26f),
            label("Mở ứng dụng TV Guard trên điện thoại > Quét mã QR (hoặc nhập mã bên dưới).\nKhông chia sẻ mã này cho trẻ.", 18f),
            ImageView(this).apply { setImageBitmap(qr(code)); setPadding(0, dp(12), 0, dp(12)) },
            label(Proto.pretty(code), 18f),
            btn("Tạo mã mới (huỷ ghép đôi cũ)") { Prefs.resetPair(this); show() },
            btn("Quay lại") { screen = "settings"; show() }
        ))
    }

    private fun showSettings() {
        Prefs.setSettingsUntil(this, System.currentTimeMillis() + 10 * 60_000L)
        val kl = Prefs.limitKids(this)
        val accOn = (Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(packageName)
        val overlayOn = Settings.canDrawOverlays(this)
        val vpnOn = VpnService.prepare(this) == null

        setContentView(page(
            label("TV Guard – Cài đặt phụ huynh", 28f),
            label(
                statusText() +
                    "\nTrợ năng: " + (if (accOn) "BẬT ✅" else "CHƯA ❌") +
                    "  |  Hiển thị: " + (if (overlayOn) "CÓ ✅" else "CHƯA ❌") +
                    "  |  VPN: " + (if (vpnOn) "CÓ ✅" else "CHƯA ❌") +
                    (if (msg.isNotEmpty()) "\n$msg" else ""),
                20f
            ),
            btn("Gia đình: 30 phút rồi tự về Trẻ em") { Prefs.startFamily(this, 30); show() },
            btn("Gia đình: 60 phút rồi tự về Trẻ em") { Prefs.startFamily(this, 60); show() },
            btn("Gia đình: 120 phút rồi tự về Trẻ em") { Prefs.startFamily(this, 120); show() },
            btn("Gia đình: không giới hạn") { Prefs.startFamily(this, 0); show() },
            btn("Về chế độ TRẺ EM") { Prefs.endFamily(this); show() },
            btn("Trẻ em: giảm 5 phút mỗi lượt (hiện $kl)") { Prefs.setLimitKids(this, kl - 5); show() },
            btn("Trẻ em: tăng 5 phút mỗi lượt (hiện $kl)") { Prefs.setLimitKids(this, kl + 5); show() },
            btn("Cấp thêm 20 phút cho trẻ") { Prefs.addKidsBonus(this, 20); show() },
            btn("Đặt lại bộ đếm trẻ em") { Prefs.resetKidsToday(this); show() },
            btn("Tự cấp lại lượt mỗi ngày: " + (if (Prefs.dailyRefresh(this)) "BẬT" else "TẮT")) {
                Prefs.setDailyRefresh(this, !Prefs.dailyRefresh(this)); show()
            },
            btn(if (Prefs.manualNet(this)) "Mở lại Internet" else "Ngắt Internet ngay") {
                Prefs.setManualNet(this, !Prefs.manualNet(this)); show()
            },
            btn("Ghép đôi với điện thoại (mã QR)") { screen = "pairing"; show() },
            btn("Đổi mã PIN") { changingPin = true; first = null; msg = ""; show() },
            btn("Cấp quyền VPN (để ngắt Internet)") {
                val i = VpnService.prepare(this)
                if (i != null) startActivityForResult(i, 7) else { msg = "VPN đã được cấp quyền ✅"; show() }
            },
            btn("Bật dịch vụ Trợ năng TV Guard") {
                try {
                    startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } catch (e: ActivityNotFoundException) {
                    msg = "Không mở được, hãy dùng lệnh adb (xem hướng dẫn)"; show()
                }
            },
            btn("Cấp quyền hiển thị trên ứng dụng khác") {
                try {
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")))
                } catch (e: ActivityNotFoundException) {
                    msg = "Tivi không có màn hình này, hãy dùng lệnh adb (xem hướng dẫn)"; show()
                }
            },
            btn("Xong – về màn hình chính") {
                screen = "home"
                authedUntil = 0
                Prefs.setSettingsUntil(this, 0)
                msg = ""
                show()
            }
        ))
    }
}
