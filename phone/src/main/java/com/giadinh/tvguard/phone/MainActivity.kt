package com.giadinh.tvguard.phone

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
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

/** Ứng dụng điều khiển TV Guard cho người lớn (Android). */
class MainActivity : Activity() {
    private val sp by lazy { getSharedPreferences("phone", MODE_PRIVATE) }
    private var keys: Proto.Keys? = null
    private var status: JSONObject? = null
    private var statusTime = 0L
    private var pin: String? = null
    private var pinAt = 0L
    private var pendingTs = 0L
    @Volatile private var gen = 0
    private lateinit var col: LinearLayout

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(24))
        }
        setContentView(ScrollView(this).apply { addView(col) })
        keys = Proto.parse(sp.getString("code", "") ?: "")
        render()
    }

    override fun onResume() { super.onResume(); startStream() }
    override fun onPause() { gen++; super.onPause() }

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
            toast("✅ TV đã thực hiện")
        } else {
            when (a.optString("err")) {
                "badpin" -> { pin = null; toast("❌ Sai mã PIN") }
                "ban" -> toast("❌ TV đang khoá nhập PIN 60 giây")
                "badnewpin" -> toast("❌ PIN mới phải 4–8 chữ số")
                else -> toast("❌ TV từ chối lệnh")
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
            runOnUiThread { toast(if (ok) "Đã gửi, chờ TV phản hồi…" else "Gửi thất bại, kiểm tra mạng") }
        }.start()
    }

    private fun pinEdit(h: String) = EditText(this).apply {
        hint = h
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD // che số
        filters = arrayOf(InputFilter.LengthFilter(8))
    }

    private fun askNumber(title: String, hint: String, onOk: (Int) -> Unit) {
        val et = EditText(this).apply { this.hint = hint; inputType = InputType.TYPE_CLASS_NUMBER }
        AlertDialog.Builder(this).setTitle(title).setView(et)
            .setPositiveButton("OK") { _, _ ->
                val n = et.text.toString().toIntOrNull()
                if (n != null) onOk(n)
            }.setNegativeButton("Huỷ", null).show()
    }

    private fun changePin() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0) }
        val a = pinEdit("PIN mới (4–8 số)")
        val b = pinEdit("Nhập lại PIN mới")
        box.addView(a); box.addView(b)
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

    // ---------- giao diện ----------
    private fun text(t: String, size: Float) = TextView(this).apply {
        text = t; textSize = size; setPadding(0, dp(6), 0, dp(6))
    }

    private fun button(t: String, onClick: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setOnClickListener { onClick() }
    }

    private fun headline(s: JSONObject?): String {
        if (s == null) return "⏳ Đang chờ trạng thái từ TV…"
        val age = System.currentTimeMillis() / 1000 - statusTime
        return when {
            age > 600 -> "⚫ TV đang tắt hoặc mất kết nối (cập nhật cuối ${age / 60} phút trước)"
            s.optBoolean("locked") -> "🌐 Hết giờ: Internet TV đã bị ngắt"
            !s.optBoolean("screen") -> "🌙 TV đang chờ (màn hình tắt)"
            else -> "▶️ TV đang hoạt động"
        }
    }

    private fun detail(s: JSONObject?): String {
        if (s == null) return ""
        val kids = s.optBoolean("kids", true)
        val fl = s.optInt("fLimit")
        return "Chế độ: " + (if (kids) "TRẺ EM" else "GIA ĐÌNH") +
            "\nTrẻ em hôm nay: ${s.optInt("kUsed")}/${s.optInt("kTotal")} phút" +
            (if (!kids) "\nGia đình: " + (if (fl == 0) "không giới hạn" else "${s.optInt("fUsed")}/$fl phút") else "") +
            "\nInternet TV: " + (if (s.optBoolean("net")) "ĐANG CHẶN" else "đang mở") +
            (if (s.optBoolean("manualNet")) " (khoá thủ công)" else "") +
            (if (!s.optBoolean("vpnOk")) "\n⚠️ TV chưa cấp quyền VPN nên chưa ngắt được Internet" else "")
    }

    private fun render() {
        col.removeAllViews()
        col.addView(text("TV Guard – Phụ huynh", 24f))
        if (keys == null) {
            col.addView(text("Ghép đôi với TV: trên TV vào TV Guard > Ghép đôi, rồi quét mã QR hoặc nhập mã.", 16f))
            val et = EditText(this).apply { hint = "Mã ghép đôi" }
            col.addView(et)
            col.addView(button("Quét mã QR trên TV") { scan() })
            col.addView(button("Ghép đôi bằng mã đã nhập") { pair(et.text.toString()) })
            return
        }
        val s = status
        col.addView(text(headline(s), 22f))
        col.addView(text(detail(s), 16f))
        val kidsNow = s?.optBoolean("kids", true) != false
        col.addView(button((if (kidsNow) "✅ " else "") + "🧒 Chế độ TRẺ EM (20 phút/lượt)") { send("kids") })
        col.addView(button((if (!kidsNow) "✅ " else "") + "👨‍👩‍👧 Chế độ GIA ĐÌNH (không giới hạn)") {
            send("family") { put("m", 0) }
        })
        col.addView(button("👨‍👩‍👧 Gia đình – chọn thời gian rồi tự về Trẻ em…") {
            askNumber("Chế độ Gia đình", "Số phút (0 = không giới hạn)") { m -> send("family") { put("m", m) } }
        })
        col.addView(button("➕ Cấp thêm 20 phút cho trẻ") { send("grant") { put("m", 20) } })
        col.addView(button("⏱ Đặt giờ Trẻ em mỗi ngày") {
            askNumber("Giờ Trẻ em/ngày", "Số phút (5–180)") { m -> send("limit") { put("m", m) } }
        })
        val manual = s?.optBoolean("manualNet") == true
        col.addView(button(if (manual) "🌐 Mở lại Internet TV" else "🚫 Khoá Internet TV ngay") {
            send("net") { put("block", !manual) }
        })
        col.addView(button("🔑 Đổi mã PIN quản trị") { changePin() })
        col.addView(button("Huỷ ghép đôi") {
            sp.edit().remove("code").apply(); keys = null; status = null; gen++; render()
        })
    }
}
