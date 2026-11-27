package com.giadinh.tvguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Hai vai trò:
 *  - Màn hình NGHỈ (state 2): hết lượt xem, đếm ngược đến lúc xem tiếp, thử tắt màn hình tivi.
 *  - Cổng PIN (state 1): chặn trẻ vào Cài đặt hệ thống.
 */
class LockActivity : Activity() {
    companion object { @Volatile var state = 0 }

    private val h = Handler(Looper.getMainLooper())
    private var rest = false
    private var countdown: TextView? = null
    private var sleepAt = 0L
    private var msg = ""

    private val ticker = object : Runnable {
        override fun run() {
            val a = this@LockActivity
            Prefs.sync(a)
            if (rest && !Prefs.locked(a)) { a.finish(); return }
            countdown?.text = fmt(Prefs.restLeftMs(a))
            if (rest && sleepAt > 0 && SystemClock.elapsedRealtime() >= sleepAt) {
                sleepAt = 0
                GuardService.sleepScreen()
            }
            h.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_SECURE
        )
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        build()
    }

    override fun onResume() {
        super.onResume()
        state = if (Prefs.locked(this)) 2 else 1
        h.removeCallbacks(ticker)
        h.post(ticker)
    }

    override fun onPause() {
        state = 0
        h.removeCallbacks(ticker)
        super.onPause()
    }

    override fun onBackPressed() {
        if (rest) return
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun fmt(ms: Long): String {
        val s = (ms + 999) / 1000
        val hh = s / 3600
        val mm = (s % 3600) / 60
        val ss = s % 60
        return if (hh > 0) "%d:%02d:%02d".format(hh, mm, ss) else "%02d:%02d".format(mm, ss)
    }

    private fun column(bg: Int): LinearLayout {
        val c = LinearLayout(this)
        c.orientation = LinearLayout.VERTICAL
        c.gravity = Gravity.CENTER
        c.setBackgroundColor(bg)
        c.setPadding(dp(32), dp(24), dp(32), dp(24))
        return c
    }

    private fun build() {
        Prefs.sync(this)
        rest = Prefs.locked(this)
        msg = ""
        if (rest) showRest() else showGate()
    }

    // ---------- Màn hình nghỉ ----------
    private fun showRest() {
        val col = column(Color.parseColor("#070B14"))
        col.add(txt("😴", 72f, Pal.TEXT, false, Gravity.CENTER))
        col.add(txt("Đến giờ nghỉ rồi", 38f, Pal.TEXT, true, Gravity.CENTER), top = 8)
        col.add(txt("Tivi sẽ cho xem tiếp sau", 20f, Pal.MUTED, false, Gravity.CENTER), top = 6)
        val cd = txt(fmt(Prefs.restLeftMs(this)), 80f, Pal.AMBER, true, Gravity.CENTER)
        countdown = cd
        col.add(cd, top = 6)
        col.add(chip("🌐  Internet tạm ngắt trong giờ nghỉ", Pal.RED), w = WRAP, top = 14)
        col.add(txt("Màn hình sẽ tự tắt sau ít giây nếu tivi hỗ trợ", 16f, Pal.MUTED, false, Gravity.CENTER), top = 14)
        col.add(btn("🔑  Dành cho phụ huynh", Kind.GHOST) { showPin() }, w = dp(380), top = 24)
        sleepAt = SystemClock.elapsedRealtime() + 10_000L
        setContentView(col)
    }

    // ---------- Nhập PIN ----------
    private fun showPin() {
        sleepAt = 0
        val col = column(Pal.BG)
        col.add(txt(if (rest) "Phụ huynh – nhập mã PIN" else "🔒 Cần mã PIN để vào Cài đặt", 30f, Pal.TEXT, true, Gravity.CENTER))
        col.add(txt(if (rest) "Để cho xem tiếp hoặc đổi chế độ" else "Chỉ phụ huynh được thay đổi cài đặt", 18f, Pal.MUTED, false, Gravity.CENTER), top = 6)
        val m = txt(msg, 18f, Pal.RED, false, Gravity.CENTER)
        col.add(m, top = 6)
        col.add(pinPad { p -> onPin(p, m) }, top = 10)
        col.add(btn("Quay lại", Kind.GHOST) { if (rest) showRest() else onBackPressed() }, w = dp(360), top = 14)
        setContentView(col)
    }

    private fun showGate() = showPin()

    private fun onPin(p: String, m: TextView) {
        if (Prefs.banSeconds(this) > 0) {
            m.text = "Nhập sai nhiều lần, thử lại sau ${Prefs.banSeconds(this)} giây"
            return
        }
        if (Prefs.checkPin(this, p)) {
            if (rest) {
                showChoices()
            } else {
                Prefs.setSettingsUntil(this, System.currentTimeMillis() + 5 * 60_000L)
                finish()
            }
        } else {
            m.text = if (Prefs.banSeconds(this) > 0) "Sai quá nhiều, khoá nhập PIN 60 giây" else "Sai mã PIN"
        }
    }

    private fun after() {
        if (Prefs.locked(this)) showRest() else finish()
    }

    // ---------- Phụ huynh chọn ----------
    private fun showChoices() {
        val col = column(Pal.BG)
        col.add(txt("✅  Đã xác nhận", 30f, Pal.GREEN, true, Gravity.CENTER))
        col.add(btn("▶  Cho xem tiếp (20 phút)", Kind.PRIMARY) { Prefs.grant(this, 20); after() }, w = dp(460), top = 20)
        col.add(btn("🏠  Chế độ GIA ĐÌNH…") { showFamily() }, w = dp(460), top = 12)
        col.add(btn("Quay lại", Kind.GHOST) { showRest() }, w = dp(460), top = 12)
        setContentView(col)
    }

    private fun showFamily() {
        val col = column(Pal.BG)
        col.add(txt("Chế độ GIA ĐÌNH – chọn thời lượng", 28f, Pal.TEXT, true, Gravity.CENTER))
        for (m in listOf(30, 60, 120)) {
            col.add(btn("$m phút") { Prefs.startFamily(this, m); after() }, w = dp(460), top = 12)
        }
        col.add(btn("Không giới hạn", Kind.PRIMARY) { Prefs.startFamily(this, 0); after() }, w = dp(460), top = 12)
        col.add(btn("Quay lại", Kind.GHOST) { showChoices() }, w = dp(460), top = 12)
        setContentView(col)
    }
}
