package com.giadinh.tvguard.phone

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.giadinh.tvguard.shared.Proto
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/** Ứng dụng điều khiển TV Guard cho phụ huynh (Android). */
class MainActivity : Activity() {
    private val sp by lazy { getSharedPreferences("phone", MODE_PRIVATE) }
    private var keys: Proto.Keys? = null
    private var status: JSONObject? = null
    private var statusTime = 0L
    private var pin: String? = null
    private var pinAt = 0L
    private var pendingTs = 0L
    @Volatile private var gen = 0
    private val h = Handler(Looper.getMainLooper())
    private val ticker = object : Runnable {
        override fun run() { render(); h.postDelayed(this, 15_000) }
    }
    private lateinit var col: LinearLayout

    private val BG = Color.parseColor("#F4F6FA")
    private val INK = Color.parseColor("#0F172A")
    private val MUTED = Color.parseColor("#64748B")
    private val LINE = Color.parseColor("#E2E8F0")
    private val BLUE = Color.parseColor("#2563EB")
    private val GREEN = Color.parseColor("#16A34A")
    private val AMBER = Color.parseColor("#D97706")
    private val RED = Color.parseColor("#DC2626")
    private val GRAY = Color.parseColor("#64748B")

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(18), dp(22), dp(18), dp(28))
        val sv = ScrollView(this)
        sv.setBackgroundColor(BG)
        sv.addView(col)
        setContentView(sv)
        keys = Proto.parse(sp.getString("code", "") ?: "")
        render()
    }

    override fun onResume() {
        super.onResume()
        startStream()
        h.removeCallbacks(ticker)
        h.postDelayed(ticker, 15_000)
    }

    override fun onPause() {
        gen++
        h.removeCallbacks(ticker)
        super.onPause()
    }

    // ---------- nhận trạng thái từ TV ----------
    private fun startStream() {
        val k = keys ?: return
        val my = ++gen
        Thread {
            while (gen == my) {
                try {
                    Proto.stream(k.st, "latest") { msg, time ->
                        val plain = Proto.dec(k, msg)
                        if (plain != null) {
                            val j = try { JSONObject(plain) } catch (e: Exception) { null }
                            if (j != null) runOnUiThread { status = j; statusTime = time; onStatus(j); render() }
                        }
                        gen == my
                    }
                } catch (e: Exception) { }
                if (gen == my) try { Thread.sleep(3000) } catch (e: InterruptedException) { }
            }
        }.apply { isDaemon = true; start() }
    }

    private fun onStatus(j: JSONObject) {
        val a = j.optJSONObject("ack") ?: return
        if (pendingTs == 0L || a.optLong("ts") != pendingTs) return
        pendingTs = 0
        if (a.optBoolean("ok")) {
            toast("✅ Tivi đã thực hiện")
        } else {
            when (a.optString("err")) {
                "badpin" -> { pin = null; toast("❌ Sai mã PIN") }
                "ban" -> toast("❌ Tivi đang khoá nhập PIN 60 giây")
                "badnewpin" -> toast("❌ PIN mới phải gồm 4–8 chữ số")
                else -> toast("❌ Tivi từ chối lệnh")
            }
        }
    }

    // ---------- gửi lệnh ----------
    private fun withPin(go: (String) -> Unit) {
        val p = pin
        if (p != null && System.currentTimeMillis() - pinAt < 120_000) { go(p); return }
        val et = pinEdit("Mã PIN quản trị")
        AlertDialog.Builder(this).setTitle("Nhập mã PIN quản trị").setView(et)
            .setPositiveButton("OK") { _, _ ->
                val v = et.text.toString()
                if (v.length >= 4) { pin = v; pinAt = System.currentTimeMillis(); go(v) }
            }.setNegativeButton("Huỷ", null).show()
    }

    private fun send(c: String, extra: JSONObject.() -> Unit = {}) {
        // Về chế độ TRẺ EM không cần PIN; mọi lệnh khác cần PIN quản trị.
        if (c == "kids") post(c, "", extra) else withPin { p -> post(c, p, extra) }
    }

    private fun post(c: String, p: String, extra: JSONObject.() -> Unit) {
        val k = keys ?: return
        val j = JSONObject().put("c", c).put("pin", p).put("ts", System.currentTimeMillis())
        j.extra()
        pendingTs = j.getLong("ts")
        Thread {
            val ok = Proto.post(k.cmd, Proto.enc(k, j.toString()))
            runOnUiThread { toast(if (ok) "Đã gửi, chờ tivi phản hồi…" else "Gửi thất bại, kiểm tra mạng") }
        }.start()
    }

    private fun pinEdit(hint: String): EditText {
        val e = EditText(this)
        e.hint = hint
        e.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD // che số
        e.filters = arrayOf(InputFilter.LengthFilter(8))
        return e
    }

    private fun askNumber(title: String, hint: String, onOk: (Int) -> Unit) {
        val et = EditText(this)
        et.hint = hint
        et.inputType = InputType.TYPE_CLASS_NUMBER
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().toIntOrNull()
                if (n != null) onOk(n)
            }.setNegativeButton("Huỷ", null).show()
    }

    private fun changePin() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(20), dp(8), dp(20), 0)
        val a = pinEdit("PIN mới (4–8 số)")
        val b = pinEdit("Nhập lại PIN mới")
        box.addView(a)
        box.addView(b)
        AlertDialog.Builder(this).setTitle("Đổi mã PIN quản trị").setView(box)
            .setPositiveButton("OK") { _, _ ->
                val n = a.text.toString()
                if (n.length in 4..8 && n == b.text.toString()) {
                    send("setpin") { put("new", n) }
                    pin = null
                } else toast("PIN không hợp lệ hoặc không khớp")
            }.setNegativeButton("Huỷ", null).show()
    }

    // ---------- ghép đôi ----------
    private fun pair(code: String) {
        val k = Proto.parse(code)
        if (k == null) { toast("Mã ghép đôi không hợp lệ"); return }
        sp.edit().putString("code", k.code).apply()
        keys = k
        status = null
        startStream()
        render()
    }

    private fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        GmsBarcodeScanning.getClient(this, opts).startScan()
            .addOnSuccessListener { b -> pair(b.rawValue ?: "") }
            .addOnFailureListener { toast("Không quét được: ${it.message}") }
    }

    // ---------- thành phần giao diện ----------
    private fun shape(fill: Int, r: Int, stroke: Int = 0, sw: Int = 0): GradientDrawable {
        val g = GradientDrawable()
        g.setColor(fill)
        g.cornerRadius = dp(r).toFloat()
        if (sw > 0) g.setStroke(dp(sw), stroke)
        return g
    }

    private fun label(t: String, size: Float, color: Int = INK, bold: Boolean = false): TextView {
        val v = TextView(this)
        v.text = t
        v.textSize = size
        v.setTextColor(color)
        if (bold) v.typeface = Typeface.DEFAULT_BOLD
        return v
    }

    private fun chip(t: String, bg: Int): TextView {
        val v = label(t, 14f, Color.WHITE, true)
        v.setPadding(dp(14), dp(6), dp(14), dp(6))
        v.background = shape(bg, 20)
        return v
    }

    private fun card(): LinearLayout {
        val l = LinearLayout(this)
        l.orientation = LinearLayout.VERTICAL
        l.background = shape(Color.WHITE, 18, LINE, 1)
        l.setPadding(dp(18), dp(16), dp(18), dp(16))
        return l
    }

    private fun button(t: String, fill: Int, fg: Int, stroke: Int = 0, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.isAllCaps = false
        b.textSize = 15f
        b.setTextColor(fg)
        b.typeface = Typeface.DEFAULT_BOLD
        b.background = shape(fill, 14, stroke, if (stroke != 0) 1 else 0)
        b.stateListAnimator = null
        b.setPadding(dp(12), dp(14), dp(12), dp(14))
        b.setOnClickListener { onClick() }
        return b
    }

    private fun bar(frac: Float, color: Int): LinearLayout {
        val t = LinearLayout(this)
        t.orientation = LinearLayout.HORIZONTAL
        t.weightSum = 100f
        t.background = shape(LINE, 6)
        val f = View(this)
        f.background = shape(color, 6)
        t.addView(f, LinearLayout.LayoutParams(0, -1, frac.coerceIn(0f, 1f) * 100f))
        return t
    }

    private fun add(v: View, top: Int = 0, parent: LinearLayout = col, w: Int = -1, h: Int = -2, weight: Float = 0f, start: Int = 0) {
        val lp = LinearLayout.LayoutParams(w, h, weight)
        lp.topMargin = dp(top)
        lp.leftMargin = dp(start)
        parent.addView(v, lp)
    }

    private fun settingRow(title: String, value: String, onClick: () -> Unit): LinearLayout {
        val c = card()
        c.orientation = LinearLayout.HORIZONTAL
        c.gravity = Gravity.CENTER_VERTICAL
        add(label(title, 16f, INK, true), 0, c, 0, -2, 1f)
        add(label(value, 15f, MUTED), 0, c, -2)
        add(label("  ›", 20f, MUTED), 0, c, -2)
        c.setOnClickListener { onClick() }
        return c
    }

    private fun fmt(sec: Long): String {
        val s = maxOf(0L, sec)
        val hh = s / 3600
        val mm = (s % 3600) / 60
        val ss = s % 60
        return if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)
    }

    // ---------- màn hình ----------
    private fun render() {
        col.removeAllViews()
        add(label("TV Guard", 26f, INK, true))
        add(label("Bảng điều khiển phụ huynh", 14f, MUTED), 2)

        if (keys == null) { renderPair(); return }

        val s = status
        val age = System.currentTimeMillis() / 1000 - statusTime
        val off = s == null || age > 600
        val kids = s?.optBoolean("kids", true) != false
        val locked = s?.optBoolean("locked") == true
        val screenOn = s?.optBoolean("screen") == true
        val restLeft = if (s == null) 0L else maxOf(0L, s.optLong("restLeftSec") - age)

        val state = when {
            s == null -> Triple("Đang chờ tivi…", GRAY, "Hãy bật tivi và kiểm tra Wi-Fi")
            off -> Triple("Tivi đang tắt hoặc mất kết nối", GRAY, "Cập nhật cuối ${age / 60} phút trước")
            locked -> Triple("Đang nghỉ", AMBER, "Xem tiếp sau ${fmt(restLeft)}")
            !screenOn -> Triple("Tivi đang chờ", BLUE, "Màn hình đang tắt")
            else -> Triple("Tivi đang hoạt động", GREEN, "Đang có người xem")
        }

        // Thẻ trạng thái
        val st = card()
        add(chip("●  " + state.first, state.second), 0, st, -2)
        add(label(state.third, 15f, MUTED), 8, st)
        if (s != null) {
            val used = s.optInt("kUsed")
            val total = maxOf(1, s.optInt("kTotal"))
            add(label("Chế độ: " + (if (kids) "Trẻ em" else "Gia đình"), 15f, INK, true), 14, st)
            add(label("Lượt xem hiện tại: $used / $total phút", 14f, MUTED), 6, st)
            add(bar(used.toFloat() / total, if (locked) AMBER else GREEN), 8, st, -1, dp(10))
            add(label("Mỗi lượt ${s.optInt("kLimit", 60)} phút · nghỉ ${s.optInt("restMin", 60)} phút", 14f, MUTED), 8, st)
            val net = s.optBoolean("net")
            add(label("Internet tivi: " + (if (net) "đang ngắt" else "đang mở") +
                (if (s.optBoolean("manualNet")) " (ngắt thủ công)" else ""), 14f, if (net) RED else GREEN, true), 8, st)
            if (!s.optBoolean("vpnOk")) add(label("⚠ Tivi chưa cấp quyền VPN nên chưa ngắt được Internet", 13f, AMBER), 6, st)
        }
        add(st, 18)

        // Chọn chế độ
        add(label("Chế độ", 13f, MUTED, true), 20)
        val seg = LinearLayout(this)
        seg.orientation = LinearLayout.HORIZONTAL
        add(button("🧒  Trẻ em", if (kids) BLUE else Color.WHITE, if (kids) Color.WHITE else INK, if (kids) 0 else LINE) {
            send("kids")
        }, 0, seg, 0, -2, 1f)
        add(button("🏠  Gia đình", if (!kids) BLUE else Color.WHITE, if (!kids) Color.WHITE else INK, if (!kids) 0 else LINE) {
            send("family") { put("m", 0) }
        }, 0, seg, 0, -2, 1f, 10)
        add(seg, 8)
        add(label("Gia đình: xem không giới hạn, cần mã PIN. Bật lại tivi sẽ tự về Trẻ em.", 12f, MUTED), 6)

        // Thao tác nhanh
        add(label("Thao tác nhanh", 13f, MUTED, true), 20)
        val quick = LinearLayout(this)
        quick.orientation = LinearLayout.HORIZONTAL
        add(button(if (locked) "▶  Cho xem tiếp 20’" else "＋  Thêm 20 phút", GREEN, Color.WHITE) {
            send("grant") { put("m", 20) }
        }, 0, quick, 0, -2, 1f)
        val manual = s?.optBoolean("manualNet") == true
        add(button(if (manual) "🌐  Mở Internet" else "🚫  Ngắt Internet", if (manual) Color.WHITE else RED, if (manual) INK else Color.WHITE, if (manual) LINE else 0) {
            send("net") { put("block", !manual) }
        }, 0, quick, 0, -2, 1f, 10)
        add(quick, 8)

        // Cài đặt
        add(label("Cài đặt", 13f, MUTED, true), 20)
        add(settingRow("Thời gian mỗi lượt xem", "${s?.optInt("kLimit", 60) ?: 60} phút") {
            askNumber("Mỗi lượt xem", "Số phút (5–240)") { m -> send("limit") { put("m", m) } }
        }, 8)
        add(settingRow("Thời gian nghỉ giữa các lượt", "${s?.optInt("restMin", 60) ?: 60} phút") {
            askNumber("Giờ nghỉ", "Số phút (5–480)") { m -> send("rest") { put("m", m) } }
        }, 8)
        add(settingRow("Gia đình có hẹn giờ", "") {
            askNumber("Chế độ Gia đình", "Số phút (0 = không giới hạn)") { m -> send("family") { put("m", m) } }
        }, 8)
        add(settingRow("Đổi mã PIN quản trị", "") { changePin() }, 8)
        add(settingRow("Huỷ ghép đôi", "") {
            sp.edit().remove("code").apply(); keys = null; status = null; gen++; render()
        }, 8)
    }

    private fun renderPair() {
        val c = card()
        add(label("Ghép đôi với tivi", 18f, INK, true), 0, c)
        add(label("Trên tivi: mở TV Guard > Cài đặt phụ huynh > Ghép đôi với điện thoại. Rồi quét mã QR hoặc nhập mã bên dưới.", 14f, MUTED), 8, c)
        val et = EditText(this)
        et.hint = "Mã ghép đôi"
        add(et, 12, c)
        add(button("📷  Quét mã QR trên tivi", BLUE, Color.WHITE) { scan() }, 12, c)
        add(button("Ghép đôi bằng mã đã nhập", Color.WHITE, INK, LINE) { pair(et.text.toString()) }, 8, c)
        add(c, 18)
    }
}
