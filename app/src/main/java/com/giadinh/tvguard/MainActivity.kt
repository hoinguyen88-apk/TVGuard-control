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
 * Màn hình chính trên TV: bên trái là thẻ trạng thái, bên phải là các nút lớn điều khiển bằng remote.
 *  - TRẺ EM: không cần PIN.   - GIA ĐÌNH: nhập PIN.   - Cài đặt phụ huynh: nhập PIN.
 */
class MainActivity : Activity() {
    private var screen = "home" // home | pinFamily | pinSettings | settings | pairing
    private var authed = false
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

    override fun onBackPressed() {
        if (screen != "home" || changingPin) { changingPin = false; home() } else super.onBackPressed()
    }

    private fun home() { screen = "home"; msg = ""; show() }

    private fun qr(text: String): Bitmap {
        val size = 480
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val b = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) {
            b.setPixel(x, y, if (m.get(x, y)) Color.BLACK else Color.WHITE)
        }
        return b
    }

    private fun column(): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setBackgroundColor(Pal.BG)
        c.setPadding(dp(32), dp(24), dp(32), dp(24))
        return c
    }

    private fun row(): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.setBackgroundColor(Pal.BG)
        r.setPadding(dp(48), dp(32), dp(48), dp(32))
        return r
    }

    private fun show() {
        Prefs.sync(this)
        if (authed && System.currentTimeMillis() > Prefs.settingsUntil(this)) {
            authed = false
            if (screen == "settings" || screen == "pairing") screen = "home"
        }
        if (!Prefs.hasPin(this) || changingPin) { setContentView(pinSetup()); return }
        val v: View = when (screen) {
            "pinFamily" -> verify("🏠  Vào chế độ GIA ĐÌNH", "Nhập mã PIN để xem không giới hạn") {
                Prefs.startFamily(this, 0); home()
            }
            "pinSettings" -> verify("⚙  Cài đặt phụ huynh", "Nhập mã PIN phụ huynh") {
                authed = true; screen = "settings"; show()
            }
            "pairing" -> if (authed) pairingView() else homeView()
            "settings" -> if (authed) settingsView() else homeView()
            else -> homeView()
        }
        setContentView(v)
    }

    // ---------- Tạo PIN ----------
    private fun pinSetup(): View {
        val col = column()
        col.add(txt("Tạo mã PIN phụ huynh", 32f, Pal.TEXT, true, Gravity.CENTER))
        col.add(txt(
            if (first == null) "Đặt mã 4–8 chữ số. Đừng cho trẻ biết mã này." else "Nhập lại mã PIN để xác nhận",
            18f, Pal.MUTED, false, Gravity.CENTER), top = 6)
        col.add(txt(msg, 18f, Pal.RED, false, Gravity.CENTER), top = 6)
        col.add(pinPad { p ->
            if (first == null) {
                first = p
                msg = ""
            } else if (first == p) {
                Prefs.setPin(this, p)
                authed = changingPin
                screen = if (changingPin) "settings" else "home"
                changingPin = false
                first = null
                msg = ""
            } else {
                first = null
                msg = "Hai lần nhập không khớp, thử lại từ đầu"
            }
            show()
        }, top = 10)
        return col
    }

    // ---------- Nhập PIN xác nhận ----------
    private fun verify(title: String, sub: String, onOk: () -> Unit): View {
        val col = column()
        col.add(txt(title, 32f, Pal.TEXT, true, Gravity.CENTER))
        col.add(txt(sub, 18f, Pal.MUTED, false, Gravity.CENTER), top = 6)
        col.add(txt(msg, 18f, Pal.RED, false, Gravity.CENTER), top = 6)
        col.add(pinPad { p ->
            if (Prefs.banSeconds(this) > 0) {
                msg = "Thử lại sau ${Prefs.banSeconds(this)} giây"
                show()
            } else if (Prefs.checkPin(this, p)) {
                msg = ""
                onOk()
            } else {
                msg = if (Prefs.banSeconds(this) > 0) "Sai quá nhiều, đợi 60 giây" else "Sai mã PIN"
                show()
            }
        }, top = 10)
        col.add(btn("Quay lại", Kind.GHOST) { home() }, w = dp(360), top = 14)
        return col
    }

    // ---------- Màn hình chính ----------
    private fun homeView(): View {
        val kids = Prefs.kids(this)
        val root = row()

        val card = card(32)
        card.add(txt("TV Guard", 20f, Pal.MUTED, true))
        card.add(chip(if (kids) "🧒  CHẾ ĐỘ TRẺ EM" else "🏠  CHẾ ĐỘ GIA ĐÌNH", if (kids) Pal.BLUE else Pal.AMBER), w = WRAP, top = 14)
        if (kids) {
            val total = Prefs.kidsTotalMin(this)
            val used = Prefs.usedKidsMs(this)
            val leftMin = maxOf(0L, (total * 60_000L - used + 59_999L) / 60_000L)
            val frac = used.toFloat() / (total * 60_000f)
            card.add(txt("Còn $leftMin phút", 52f, Pal.TEXT, true), top = 20)
            card.add(txt("Đã xem ${used / 60_000} / $total phút trong lượt này", 18f, Pal.MUTED), top = 4)
            card.add(progress(frac, if (frac > 0.85f) Pal.AMBER else Pal.GREEN), h = dp(14), top = 16)
            card.add(txt("Hết lượt, tivi nghỉ ${Prefs.restMin(this)} phút rồi mới xem tiếp.", 16f, Pal.MUTED), top = 14)
        } else {
            val fl = Prefs.familyLimitMin(this)
            if (fl == 0) {
                card.add(txt("Không giới hạn", 46f, Pal.TEXT, true), top = 20)
                card.add(txt("Xem thoải mái. Tắt rồi bật lại tivi sẽ về chế độ Trẻ em.", 18f, Pal.MUTED), top = 4)
            } else {
                val used = Prefs.famUsedMs(this)
                val left = maxOf(0L, (fl * 60_000L - used + 59_999L) / 60_000L)
                card.add(txt("Còn $left phút", 52f, Pal.TEXT, true), top = 20)
                card.add(txt("Hết hẹn giờ sẽ tự về chế độ Trẻ em.", 18f, Pal.MUTED), top = 4)
                card.add(progress(used.toFloat() / (fl * 60_000f), Pal.AMBER), h = dp(14), top = 16)
            }
        }
        val net = NetBlockService.running
        card.add(chip(if (net) "🌐  Internet: đã ngắt" else "🌐  Internet: đang mở", if (net) Pal.RED else Pal.GREEN), w = WRAP, top = 22)
        root.add(card, w = 0, h = MATCH, weight = 1.1f)

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.CENTER_VERTICAL
        right.setPadding(dp(40), 0, 0, 0)
        right.add(txt("Chọn chế độ", 26f, Pal.TEXT, true))
        right.add(btn((if (kids) "✓  " else "") + "🧒  Chế độ TRẺ EM", if (kids) Kind.PRIMARY else Kind.SECONDARY) {
            Prefs.endFamily(this); show()
        }, top = 16)
        right.add(btn((if (!kids) "✓  " else "") + "🏠  Chế độ GIA ĐÌNH  (nhập PIN)", if (!kids) Kind.PRIMARY else Kind.SECONDARY) {
            screen = "pinFamily"; msg = ""; show()
        }, top = 12)
        right.add(btn("⚙  Cài đặt phụ huynh  (nhập PIN)", Kind.GHOST) {
            screen = "pinSettings"; msg = ""; show()
        }, top = 12)
        right.add(txt("Mẹo: bấm giữ phím Menu (☰) trên remote để mở nhanh màn hình này.", 15f, Pal.MUTED), top = 18)
        root.add(right, w = 0, h = MATCH, weight = 1f)
        return root
    }

    // ---------- Cài đặt phụ huynh ----------
    private fun stepRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.background = shape(Pal.CARD, 16, Pal.STROKE, 1)
        r.setPadding(dp(20), dp(10), dp(12), dp(10))
        r.add(txt(label, 20f, Pal.TEXT, true), w = 0, weight = 1f)
        r.add(btn("−", Kind.SECONDARY, 0, onMinus), w = dp(64))
        r.add(txt(value, 20f, Pal.TEXT, true, Gravity.CENTER), w = dp(120))
        r.add(btn("+", Kind.SECONDARY, 0, onPlus), w = dp(64))
        return r
    }

    private fun permRow(label: String, ok: Boolean, action: () -> Unit): LinearLayout {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.add(txt(label, 18f, Pal.TEXT, true), w = 0, weight = 1f)
        if (ok) r.add(chip("✓ Đã cấp", Pal.GREEN), w = WRAP)
        else r.add(btn("Cấp quyền", Kind.PRIMARY, 16, action), w = WRAP)
        return r
    }

    private fun trySettings(i: Intent, fail: String) {
        try { startActivity(i) } catch (e: ActivityNotFoundException) { msg = fail; show() }
    }

    private fun settingsView(): View {
        Prefs.setSettingsUntil(this, System.currentTimeMillis() + 10 * 60_000L)
        val kids = Prefs.kids(this)
        val limit = Prefs.limitKids(this)
        val restMin = Prefs.restMin(this)
        val accOn = (Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: "").contains(packageName)
        val overlayOn = Settings.canDrawOverlays(this)
        val vpnOn = VpnService.prepare(this) == null

        val root = row()
        val left = card(28)
        left.add(txt("Cài đặt phụ huynh", 28f, Pal.TEXT, true))
        left.add(chip(if (kids) "🧒  Đang ở chế độ Trẻ em" else "🏠  Đang ở chế độ Gia đình", if (kids) Pal.BLUE else Pal.AMBER), w = WRAP, top = 14)
        left.add(txt(
            "Lượt xem: ${Prefs.usedKidsMs(this) / 60_000} / ${Prefs.kidsTotalMin(this)} phút" +
                "\nMỗi lượt $limit phút · nghỉ $restMin phút" +
                "\nInternet: " + (if (NetBlockService.running) "đang ngắt" else "đang mở"),
            18f, Pal.MUTED), top = 16)
        if (msg.isNotEmpty()) left.add(txt(msg, 16f, Pal.AMBER), top = 10)
        left.add(txt("QUYỀN CẦN CẤP", 14f, Pal.MUTED, true), top = 22)
        left.add(permRow("Trợ năng", accOn) {
            trySettings(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS), "Không mở được Trợ năng, hãy dùng atvTools hoặc adb")
        }, top = 8)
        left.add(permRow("Hiển thị trên ứng dụng khác", overlayOn) {
            trySettings(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                "Tivi không có màn hình này, hãy dùng atvTools hoặc adb")
        }, top = 8)
        left.add(permRow("VPN (để ngắt Internet)", vpnOn) {
            val i = VpnService.prepare(this)
            if (i != null) startActivityForResult(i, 7) else show()
        }, top = 8)
        root.add(left, w = 0, h = MATCH, weight = 1f)

        val list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(dp(32), 0, dp(8), dp(24))
        list.add(txt("THỜI GIAN", 14f, Pal.MUTED, true))
        list.add(stepRow("Mỗi lượt xem", "$limit phút",
            { Prefs.setLimitKids(this, limit - 15); show() },
            { Prefs.setLimitKids(this, limit + 15); show() }), top = 8)
        list.add(stepRow("Nghỉ giữa các lượt", "$restMin phút",
            { Prefs.setRestMin(this, restMin - 15); show() },
            { Prefs.setRestMin(this, restMin + 15); show() }), top = 10)
        list.add(btn("▶  Cho xem thêm 20 phút", Kind.PRIMARY) { Prefs.grant(this, 20); show() }, top = 10)
        list.add(btn("↺  Bắt đầu lượt mới (đặt lại bộ đếm)") { Prefs.newSession(this); show() }, top = 10)
        list.add(txt("CHẾ ĐỘ", 14f, Pal.MUTED, true), top = 22)
        list.add(btn("🏠  Gia đình – hẹn giờ 60 phút") { Prefs.startFamily(this, 60); show() }, top = 8)
        list.add(btn("🏠  Gia đình – không giới hạn") { Prefs.startFamily(this, 0); show() }, top = 10)
        list.add(btn("🧒  Về chế độ Trẻ em") { Prefs.endFamily(this); show() }, top = 10)
        list.add(txt("INTERNET", 14f, Pal.MUTED, true), top = 22)
        list.add(btn(if (Prefs.manualNet(this)) "🌐  Mở lại Internet" else "🚫  Ngắt Internet ngay") {
            Prefs.setManualNet(this, !Prefs.manualNet(this)); show()
        }, top = 8)
        list.add(txt("KẾT NỐI & BẢO MẬT", 14f, Pal.MUTED, true), top = 22)
        list.add(btn("📱  Ghép đôi với điện thoại (mã QR)") { screen = "pairing"; show() }, top = 8)
        list.add(btn("🔑  Đổi mã PIN") { changingPin = true; first = null; msg = ""; show() }, top = 10)
        list.add(btn("Khoá lại & thoát", Kind.DANGER) {
            authed = false
            Prefs.setSettingsUntil(this, 0)
            home()
        }, top = 22)
        val sv = ScrollView(this)
        sv.addView(list)
        root.add(sv, w = 0, h = MATCH, weight = 1.15f)
        return root
    }

    // ---------- Ghép đôi ----------
    private fun pairingView(): View {
        val code = Prefs.pairCode(this)
        val root = row()
        val qrBox = LinearLayout(this)
        qrBox.background = shape(Color.WHITE, 20)
        qrBox.setPadding(dp(16), dp(16), dp(16), dp(16))
        val iv = ImageView(this)
        iv.setImageBitmap(qr(code))
        qrBox.addView(iv, LinearLayout.LayoutParams(dp(300), dp(300)))
        val leftWrap = LinearLayout(this)
        leftWrap.gravity = Gravity.CENTER
        leftWrap.addView(qrBox)
        root.add(leftWrap, w = 0, h = MATCH, weight = 1f)

        val right = LinearLayout(this)
        right.orientation = LinearLayout.VERTICAL
        right.gravity = Gravity.CENTER_VERTICAL
        right.setPadding(dp(32), 0, 0, 0)
        right.add(txt("Ghép đôi với điện thoại", 32f, Pal.TEXT, true))
        right.add(txt("1. Mở ứng dụng TV Guard Phụ huynh trên điện thoại.\n2. Bấm “Quét mã QR” và quét hình bên cạnh.\nKhông chia sẻ mã này cho trẻ.", 18f, Pal.MUTED), top = 10)
        right.add(txt("Hoặc nhập tay mã:", 16f, Pal.MUTED), top = 14)
        right.add(txt(Proto.pretty(code), 15f, Pal.TEXT), top = 4)
        right.add(btn("Tạo mã mới (huỷ ghép đôi cũ)") { Prefs.resetPair(this); show() }, top = 18)
        right.add(btn("Quay lại", Kind.GHOST) { screen = "settings"; show() }, top = 10)
        root.add(right, w = 0, h = MATCH, weight = 1.2f)
        return root
    }
}
