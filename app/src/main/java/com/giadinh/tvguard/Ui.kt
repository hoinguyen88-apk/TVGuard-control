package com.giadinh.tvguard

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.btn(t: String, onClick: () -> Unit) = Button(this).apply {
    text = t
    textSize = 20f
    isAllCaps = false
    isFocusable = true
    setOnClickListener { onClick() }
}

fun Context.label(t: String, size: Float = 22f) = TextView(this).apply {
    text = t
    textSize = size
    setTextColor(Color.WHITE)
    gravity = Gravity.CENTER
    setPadding(dp(8), dp(8), dp(8), dp(8))
}

/** Bàn phím số điều khiển được bằng remote (D-pad). Bấm OK để gửi. */
fun Context.pinPad(onSubmit: (String) -> Unit): LinearLayout {
    val root = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
    }
    val sb = StringBuilder()
    val dots = label(" ", 34f)
    root.addView(dots)
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    for (r in 0 until 4) {
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (c in 0 until 3) {
            val k = keys[r * 3 + c]
            val b = btn(k) {
                if (k == "⌫") {
                    if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                } else if (k == "OK") {
                    val p = sb.toString()
                    sb.clear()
                    if (p.length >= 4) onSubmit(p)
                } else if (sb.length < 8) {
                    sb.append(k)
                }
                dots.text = if (sb.isEmpty()) " " else "●".repeat(sb.length)
            }
            row.addView(b, LinearLayout.LayoutParams(dp(110), dp(64)))
        }
        root.addView(row)
    }
    return root
}
