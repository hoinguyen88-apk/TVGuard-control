package com.giadinh.tvguard

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.VpnService
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent

/**
 * Chạy nền (Trợ năng, hệ thống tự bật lại sau khi khởi động tivi):
 * - Mỗi lần BẬT tivi luôn về chế độ TRẺ EM.
 * - Đếm giờ khi màn hình bật; hết lượt xem -> bắt đầu NGHỈ: hiện màn hình nghỉ, ngắt Internet, thử tắt màn hình.
 * - Hết giờ nghỉ -> tự bắt đầu lượt mới.
 * - Nhận lệnh từ điện thoại, chặn vào Cài đặt hệ thống khi chưa nhập PIN.
 * - Phím tắt remote: bấm giữ Menu ~1,5 giây (hoặc phím Đỏ / Settings) -> màn hình chọn chế độ.
 */
class GuardService : AccessibilityService() {
    companion object {
        @Volatile private var inst: GuardService? = null

        /** Thử đưa tivi vào chế độ ngủ (khoá màn hình). Tuỳ tivi có hỗ trợ hay không. */
        fun sleepScreen(): Boolean {
            val s = inst ?: return false
            return if (Build.VERSION.SDK_INT >= 28) s.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) else false
        }
    }

    private val h = Handler(Looper.getMainLooper())
    private val remote by lazy { Remote(this) }
    private var last = 0L
    private var lastLaunch = 0L
    private var lastNetAct = 0L
    private var lastStatus = 0L
    private var pending = 0L
    private var keyFired = false
    private val gatePkgs = setOf(
        "com.android.tv.settings", "com.google.android.tvsettings", "com.android.settings",
        "com.google.android.packageinstaller", "com.android.packageinstaller"
    )

    // Tắt màn hình (chờ) -> lần bật sau luôn là chế độ Trẻ em.
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            if (Prefs.hasPin(c) && !Prefs.kids(c)) {
                Prefs.endFamily(c)
                remote.publishAsync()
            }
        }
    }

    private val tick = object : Runnable {
        override fun run() {
            val ctx = this@GuardService
            val now = SystemClock.elapsedRealtime()
            val dt = (now - last).coerceIn(0L, 10_000L)
            last = now
            val interactive = getSystemService(PowerManager::class.java).isInteractive
            if (Prefs.hasPin(ctx)) {
                Prefs.sync(ctx)
                if (interactive) {
                    if (!Prefs.locked(ctx) && !Prefs.exhausted(ctx, pending)) pending += dt
                    if (pending >= 5_000L || Prefs.exhausted(ctx, pending) || Prefs.familyDone(ctx, pending)) flush()
                    if (Prefs.familyDone(ctx)) { Prefs.endFamily(ctx); remote.publishAsync() }
                } else {
                    flush()
                }
                // Hết lượt xem -> bắt đầu giờ nghỉ
                if (Prefs.exhausted(ctx)) { Prefs.startRest(ctx); remote.publishAsync() }
                // Đang nghỉ mà màn hình đang bật -> hiện màn hình nghỉ
                if (interactive && Prefs.locked(ctx) && LockActivity.state != 2) openActivity(LockActivity::class.java)
                updateNet(now)
                val every = if (interactive) 30_000L else 300_000L
                if (now - lastStatus >= every) { lastStatus = now; remote.publishAsync() }
            } else {
                flush()
            }
            h.postDelayed(this, 1000)
        }
    }

    private fun flush() {
        if (pending > 0) { Prefs.addUsed(this, pending); pending = 0 }
    }

    private fun updateNet(now: Long) {
        val want = Prefs.netBlocked(this)
        if (want == NetBlockService.running || now - lastNetAct < 10_000L) return
        lastNetAct = now
        val i = Intent(this, NetBlockService::class.java)
        if (want) {
            if (VpnService.prepare(this) == null) startForegroundService(i)
        } else {
            stopService(i)
        }
        remote.publishAsync()
    }

    private fun openActivity(cls: Class<*>) {
        val n = SystemClock.elapsedRealtime()
        if (n - lastLaunch < 1500) return
        lastLaunch = n
        startActivity(
            Intent(this, cls).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
    }

    override fun onServiceConnected() {
        inst = this
        last = SystemClock.elapsedRealtime()
        // TV vừa khởi động lại -> luôn về chế độ Trẻ em.
        val bc = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, -1)
        if (bc != Prefs.lastBoot(this)) {
            Prefs.setLastBoot(this, bc)
            if (Prefs.hasPin(this)) Prefs.endFamily(this)
        }
        try { registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF)) } catch (e: Exception) { }
        remote.start()
        h.removeCallbacks(tick)
        h.post(tick)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null || event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        if (!Prefs.hasPin(this)) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg in gatePkgs &&
            System.currentTimeMillis() > Prefs.settingsUntil(this) &&
            LockActivity.state == 0
        ) openActivity(LockActivity::class.java)
    }

    /** Phím tắt trên remote để mở màn hình chọn chế độ Trẻ em / Gia đình. */
    override fun onKeyEvent(e: KeyEvent): Boolean {
        if (!Prefs.hasPin(this)) return false
        val k = e.keyCode
        if (k != KeyEvent.KEYCODE_MENU && k != KeyEvent.KEYCODE_PROG_RED && k != KeyEvent.KEYCODE_SETTINGS) return false
        if (e.action == KeyEvent.ACTION_UP) { keyFired = false; return false }
        val ok = if (k == KeyEvent.KEYCODE_MENU) e.eventTime - e.downTime >= 1500 else true
        if (ok && !keyFired) {
            keyFired = true
            openActivity(MainActivity::class.java)
            return k != KeyEvent.KEYCODE_MENU
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        inst = null
        flush()
        try { unregisterReceiver(screenOff) } catch (e: Exception) { }
        remote.stop()
        h.removeCallbacks(tick)
        super.onDestroy()
    }
}
