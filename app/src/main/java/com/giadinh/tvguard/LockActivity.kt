package com.giadinh.tvguard

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView

/** Cổng PIN chặn trẻ vào Cài đặt hệ thống. (Hết giờ KHÔNG khoá màn hình, chỉ ngắt Internet.) state: 0 = không hiện, 1 = đang hiện. */
class LockActivity : Activity() {
    companion object { @Volatile var state = 0 }

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

    override fun onResume() { super.onResume(); state = 1 }
    override fun onPause() { state = 0; super.onPause() }

    override fun onBackPressed() {
        startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        finish()
    }

    private fun build() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#111827"))
            setPadding(dp(32), dp(16), dp(32), dp(16))
        }
        col.addView(label("🔒 Cần mã PIN để vào Cài đặt", 30f))
        col.addView(label("Chỉ phụ huynh được thay đổi cài đặt", 20f))
        val msg: TextView = label("", 18f)
        col.addView(msg)
        col.addView(pinPad { p ->
            if (Prefs.banSeconds(this) > 0) {
                msg.text = "Nhập sai nhiều lần, thử lại sau ${Prefs.banSeconds(this)} giây"
            } else if (Prefs.checkPin(this, p)) {
                Prefs.setSettingsUntil(this, System.currentTimeMillis() + 5 * 60_000L)
                finish()
            } else {
                msg.text = if (Prefs.banSeconds(this) > 0) "Sai quá nhiều, khoá nhập PIN 60 giây" else "Sai mã PIN"
            }
        })
        setContentView(col)
    }
}
