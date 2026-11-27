package com.giadinh.tvguard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = -1
const val WRAP = -2

/** Bảng màu: nền xanh đen, thẻ nổi, nhấn xanh dương; xanh lá = ổn, vàng = cảnh báo, đỏ = ngắt/khoá. */
object Pal {
    val BG = Color.parseColor("#0B1220")
    val CARD = Color.parseColor("#131C30")
    val STROKE = Color.parseColor("#223152")
    val TEXT = Color.parseColor("#F3F6FB")
    val MUTED = Color.parseColor("#93A1BD")
    val BLUE = Color.parseColor("#3B82F6")
    val GREEN = Color.parseColor("#22C55E")
    val AMBER = Color.parseColor("#F59E0B")
    val RED = Color.parseColor("#EF4444")
    val KEY = Color.parseColor("#1B2742")
}

enum class Kind { PRIMARY, SECONDARY, DANGER, GHOST }

fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

fun Context.shape(fill: Int, radiusDp: Int, stroke: Int = 0, strokeDp: Int = 0): GradientDrawable {
    val g = GradientDrawable()
    g.setColor(fill)
    g.cornerRadius = dp(radiusDp).toFloat()
    if (strokeDp > 0) g.setStroke(dp(strokeDp), stroke)
    return g
}

/** Nút lớn cho remote: khi được chọn (focus) sáng lên và có viền trắng dày. */
fun Context.btn(t: String, kind: Kind = Kind.SECONDARY, padH: Int = 24, onClick: () -> Unit): Button {
    val base = when (kind) {
        Kind.PRIMARY -> Pal.BLUE
        Kind.DANGER -> Pal.RED
        Kind.SECONDARY -> Pal.KEY
        Kind.GHOST -> Color.TRANSPARENT
    }
    val focus = when (kind) {
        Kind.PRIMARY -> Color.parseColor("#6BA4FF")
        Kind.DANGER -> Color.parseColor("#FF7A7A")
        else -> Color.parseColor("#2F4170")
    }
    val sl = StateListDrawable()
    sl.addState(intArrayOf(android.R.attr.state_focused), shape(focus, 14, Color.WHITE, 3))
    sl.addState(intArrayOf(android.R.attr.state_pressed), shape(focus, 14, Color.WHITE, 3))
    sl.addState(intArrayOf(), shape(base, 14, Pal.STROKE, if (kind == Kind.GHOST) 1 else 0))
    val b = Button(this)
    b.text = t
    b.textSize = 20f
    b.isAllCaps = false
    b.setTextColor(Pal.TEXT)
    b.typeface = Typeface.DEFAULT_BOLD
    b.background = sl
    b.stateListAnimator = null
    b.isFocusable = true
    b.minimumHeight = dp(64)
    b.setPadding(dp(padH), dp(12), dp(padH), dp(12))
    b.setOnClickListener { onClick() }
    return b
}

fun Context.txt(
    t: String,
    size: Float = 18f,
    color: Int = Pal.TEXT,
    bold: Boolean = false,
    gravity: Int = Gravity.START
): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = size
    v.setTextColor(color)
    v.gravity = gravity
    if (bold) v.typeface = Typeface.DEFAULT_BOLD
    v.setLineSpacing(0f, 1.15f)
    return v
}

fun Context.chip(t: String, bg: Int, fg: Int = Color.WHITE): TextView {
    val v = TextView(this)
    v.text = t
    v.textSize = 16f
    v.setTextColor(fg)
    v.typeface = Typeface.DEFAULT_BOLD
    v.setPadding(dp(16), dp(6), dp(16), dp(6))
    v.background = shape(bg, 20)
    return v
}

fun Context.card(pad: Int = 24): LinearLayout {
    val l = LinearLayout(this)
    l.orientation = LinearLayout.VERTICAL
    l.background = shape(Pal.CARD, 20, Pal.STROKE, 1)
    l.setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    return l
}

/** Thanh tiến độ: phần đã dùng tô màu, phần còn lại màu nền. */
fun Context.progress(frac: Float, color: Int): LinearLayout {
    val track = LinearLayout(this)
    track.orientation = LinearLayout.HORIZONTAL
    track.weightSum = 100f
    track.background = shape(Pal.KEY, 8)
    val fill = View(this)
    fill.background = shape(color, 8)
    track.addView(fill, LinearLayout.LayoutParams(0, MATCH, frac.coerceIn(0f, 1f) * 100f))
    return track
}

fun LinearLayout.add(v: View, w: Int = MATCH, h: Int = WRAP, top: Int = 0, weight: Float = 0f): View {
    val lp = LinearLayout.LayoutParams(w, h, weight)
    lp.topMargin = context.dp(top)
    addView(v, lp)
    return v
}

/** Bàn phím số điều khiển bằng remote. PIN hiện dạng ● ○, không lộ chữ số. */
fun Context.pinPad(onSubmit: (String) -> Unit): LinearLayout {
    val root = LinearLayout(this)
    root.orientation = LinearLayout.VERTICAL
    root.gravity = Gravity.CENTER_HORIZONTAL
    val sb = StringBuilder()
    val dots = txt(" ", 34f, Pal.TEXT, true, Gravity.CENTER)
    root.add(dots, top = 0)
    fun refresh() {
        val n = maxOf(4, sb.length)
        dots.text = (0 until n).joinToString("  ") { if (it < sb.length) "●" else "○" }
    }
    refresh()
    val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "⌫", "0", "OK")
    for (r in 0 until 4) {
        val row = LinearLayout(this)
        row.gravity = Gravity.CENTER
        for (c in 0 until 3) {
            val k = keys[r * 3 + c]
            val b = btn(k, if (k == "OK") Kind.PRIMARY else Kind.SECONDARY, 0) {
                if (k == "⌫") {
                    if (sb.isNotEmpty()) sb.deleteCharAt(sb.length - 1)
                    refresh()
                } else if (k == "OK") {
                    val p = sb.toString()
                    sb.clear()
                    refresh()
                    if (p.length >= 4) onSubmit(p)
                } else {
                    if (sb.length < 8) sb.append(k)
                    refresh()
                }
            }
            b.textSize = 24f
            val lp = LinearLayout.LayoutParams(dp(104), dp(72))
            lp.setMargins(dp(5), dp(5), dp(5), dp(5))
            row.addView(b, lp)
        }
        root.addView(row)
    }
    return root
}
