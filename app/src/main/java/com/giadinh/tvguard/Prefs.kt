package com.giadinh.tvguard

import android.content.Context
import com.giadinh.tvguard.shared.Proto
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * TRẺ EM: mỗi LƯỢT xem 60 phút (chỉnh được), hết lượt thì NGHỈ 60 phút (chỉnh được) rồi mới xem tiếp.
 * Mọi thứ lưu bền và tính theo giờ thực nên tắt/bật/khởi động lại TV không được cấp lại giờ.
 * GIA ĐÌNH: nhập PIN, không giới hạn (hoặc hẹn giờ). Hết hẹn giờ / tắt TV -> về TRẺ EM.
 */
object Prefs {
    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("guard", Context.MODE_PRIVATE)

    private fun hash(p: String) = MessageDigest.getInstance("SHA-256")
        .digest(("tvguard:$p").toByteArray()).joinToString("") { "%02x".format(it) }

    // ---- PIN ----
    fun hasPin(c: Context) = sp(c).contains("pin")
    fun setPin(c: Context, p: String) { sp(c).edit().putString("pin", hash(p)).apply() }

    fun banSeconds(c: Context): Long =
        maxOf(0L, (sp(c).getLong("banUntil", 0) - System.currentTimeMillis()) / 1000)

    /** Sai 5 lần -> khoá nhập PIN 60 giây. */
    fun checkPin(c: Context, p: String): Boolean {
        if (banSeconds(c) > 0) return false
        val ok = sp(c).getString("pin", "") == hash(p)
        val e = sp(c).edit()
        if (ok) {
            e.putInt("fails", 0)
        } else {
            val f = sp(c).getInt("fails", 0) + 1
            if (f >= 5) e.putLong("banUntil", System.currentTimeMillis() + 60_000).putInt("fails", 0)
            else e.putInt("fails", f)
        }
        e.apply()
        return ok
    }

    // ---- Chế độ ----
    fun kids(c: Context) = sp(c).getBoolean("kids", true)
    fun setKids(c: Context, v: Boolean) { sp(c).edit().putBoolean("kids", v).apply() }

    /** Giờ "hiệu lực": không bao giờ lùi lại, nên chỉnh lùi đồng hồ TV không rút ngắn được thời gian nghỉ. */
    private fun effNow(c: Context): Long =
        maxOf(System.currentTimeMillis(), sp(c).getLong("lastWall", 0L))

    /** Gọi định kỳ: hết giờ nghỉ -> bắt đầu lượt mới; sang ngày mới -> kết thúc phiên Gia đình. */
    fun sync(c: Context) {
        val s = sp(c)
        val eff = effNow(c)
        val e = s.edit()
        var dirty = false
        if (eff - s.getLong("lastWall", 0L) >= 30_000L) { e.putLong("lastWall", eff); dirty = true }
        val rest = s.getLong("restUntil", 0L)
        if (rest > 0 && eff >= rest) {
            e.putLong("used_kids", 0).putInt("sess_total", 0).putLong("restUntil", 0)
            dirty = true
        }
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(eff))
        val oldDay = s.getString("day", null)
        if (oldDay != today) {
            e.putString("day", today).putLong("fam_used", 0).putInt("fam_limit", 0)
            if (oldDay != null) e.putBoolean("kids", true)
            dirty = true
        }
        if (dirty) e.apply()
    }

    // ---- Lượt xem & giờ nghỉ của trẻ em ----
    fun limitKids(c: Context) = sp(c).getInt("limit_kids", 60)
    fun setLimitKids(c: Context, v: Int) { sp(c).edit().putInt("limit_kids", v.coerceIn(5, 240)).apply() }
    fun restMin(c: Context) = sp(c).getInt("rest_min", 60)
    fun setRestMin(c: Context, v: Int) { sp(c).edit().putInt("rest_min", v.coerceIn(5, 480)).apply() }

    fun usedKidsMs(c: Context) = sp(c).getLong("used_kids", 0)
    fun kidsTotalMin(c: Context): Int {
        val t = sp(c).getInt("sess_total", 0)
        return if (t > 0) t else limitKids(c)
    }

    private fun restUntil(c: Context) = sp(c).getLong("restUntil", 0L)
    fun resting(c: Context) = restUntil(c) > 0 && effNow(c) < restUntil(c)
    fun restLeftMs(c: Context) = maxOf(0L, restUntil(c) - effNow(c))
    fun startRest(c: Context) {
        sp(c).edit().putLong("restUntil", effNow(c) + restMin(c) * 60_000L).apply()
    }

    /** Bắt đầu lượt mới ngay (đầy giờ). */
    fun newSession(c: Context) {
        sp(c).edit().putLong("used_kids", 0).putInt("sess_total", 0).putLong("restUntil", 0).apply()
    }

    /** Người lớn cấp thêm m phút: đang nghỉ -> cho xem tiếp m phút; đang xem -> cộng thêm m phút. */
    fun grant(c: Context, m: Int) {
        if (restUntil(c) > 0) {
            sp(c).edit().putLong("used_kids", 0).putInt("sess_total", m).putLong("restUntil", 0).apply()
        } else {
            sp(c).edit().putInt("sess_total", kidsTotalMin(c) + m).apply()
        }
    }

    /** Lượt xem của trẻ đã đủ nhưng chưa bắt đầu nghỉ. */
    fun exhausted(c: Context, extraMs: Long = 0L): Boolean =
        hasPin(c) && kids(c) && restUntil(c) == 0L &&
            usedKidsMs(c) + extraMs >= kidsTotalMin(c) * 60_000L

    /** Đang trong giờ nghỉ (tivi bị khoá). */
    fun locked(c: Context): Boolean = hasPin(c) && kids(c) && resting(c)

    // ---- Phiên Gia đình ----
    fun familyLimitMin(c: Context) = sp(c).getInt("fam_limit", 0)
    fun famUsedMs(c: Context) = sp(c).getLong("fam_used", 0)
    fun startFamily(c: Context, minutes: Int) {
        sp(c).edit().putBoolean("kids", false).putInt("fam_limit", minutes).putLong("fam_used", 0).apply()
    }
    fun endFamily(c: Context) {
        sp(c).edit().putBoolean("kids", true).putLong("fam_used", 0).putInt("fam_limit", 0).apply()
    }
    fun familyDone(c: Context, extraMs: Long = 0L): Boolean =
        hasPin(c) && !kids(c) && familyLimitMin(c) > 0 &&
            famUsedMs(c) + extraMs >= familyLimitMin(c) * 60_000L

    fun addUsed(c: Context, ms: Long) {
        val k = if (kids(c)) "used_kids" else "fam_used"
        sp(c).edit().putLong(k, sp(c).getLong(k, 0) + ms).apply()
    }

    // ---- Khoá Internet ----
    fun manualNet(c: Context) = sp(c).getBoolean("manualNet", false)
    fun setManualNet(c: Context, v: Boolean) { sp(c).edit().putBoolean("manualNet", v).apply() }
    fun netBlocked(c: Context) = hasPin(c) && (manualNet(c) || locked(c))

    // ---- Ghép đôi với điện thoại ----
    fun pairCode(c: Context): String {
        var s = sp(c).getString("pair", null)
        if (s == null) {
            s = Proto.newCode()
            sp(c).edit().putString("pair", s).apply()
        }
        return s
    }
    fun resetPair(c: Context) {
        sp(c).edit().putString("pair", Proto.newCode()).putLong("lastCmd", 0).apply()
    }
    fun lastCmdTs(c: Context) = sp(c).getLong("lastCmd", 0)
    fun setLastCmdTs(c: Context, t: Long) { sp(c).edit().putLong("lastCmd", t).apply() }

    // ---- Nhận biết TV vừa khởi động lại ----
    fun lastBoot(c: Context) = sp(c).getInt("lastBoot", -2)
    fun setLastBoot(c: Context, v: Int) { sp(c).edit().putInt("lastBoot", v).apply() }

    // ---- Cổng PIN vào Cài đặt hệ thống ----
    fun settingsUntil(c: Context) = sp(c).getLong("settingsUntil", 0)
    fun setSettingsUntil(c: Context, t: Long) { sp(c).edit().putLong("settingsUntil", t).apply() }
}
