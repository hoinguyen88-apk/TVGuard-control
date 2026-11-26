package com.giadinh.tvguard

import android.content.Context
import com.giadinh.tvguard.shared.Proto
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * - TRẺ EM: ngân sách 20 phút/NGÀY (lưu bền, "hết giờ" được tính từ số phút đã xem nên khởi động lại không cấp lại giờ).
 * - GIA ĐÌNH: một phiên có thời lượng riêng (0 = không giới hạn). Hết phiên -> tự về TRẺ EM.
 * - Qua ngày mới: đặt lại bộ đếm và về TRẺ EM.
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

    /** Qua ngày mới: reset. Dùng max(giờ hiện tại, mốc giờ lớn nhất từng thấy) nên chỉnh lùi đồng hồ không reset được. */
    fun sync(c: Context) {
        val s = sp(c)
        val now = System.currentTimeMillis()
        val lastWall = s.getLong("lastWall", 0L)
        val eff = maxOf(now, lastWall)
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(eff))
        val oldDay = s.getString("day", null)
        if (oldDay != today) {
            val e = s.edit().putString("day", today).putLong("lastWall", eff)
                .putLong("fam_used", 0).putInt("fam_limit", 0)
            if (s.getBoolean("daily", true)) e.putLong("used_kids", 0).putInt("bonus_kids", 0)
            if (oldDay != null) e.putBoolean("kids", true)
            e.apply()
        } else if (eff - lastWall >= 30_000L) {
            s.edit().putLong("lastWall", eff).apply()
        }
    }

    // ---- Ngân sách trẻ em ----
    fun limitKids(c: Context) = sp(c).getInt("limit_kids", 20)
    fun setLimitKids(c: Context, v: Int) { sp(c).edit().putInt("limit_kids", v.coerceIn(5, 180)).apply() }
    fun usedKidsMs(c: Context) = sp(c).getLong("used_kids", 0)
    fun bonusKidsMin(c: Context) = sp(c).getInt("bonus_kids", 0)
    fun addKidsBonus(c: Context, m: Int) {
        sp(c).edit().putInt("bonus_kids", bonusKidsMin(c) + m).apply()
    }
    fun resetKidsToday(c: Context) {
        sp(c).edit().putLong("used_kids", 0).putInt("bonus_kids", 0).apply()
    }
    fun kidsTotalMin(c: Context) = limitKids(c) + bonusKidsMin(c)

    /** Chỉ "hết giờ" khi đang ở chế độ trẻ em và đã xem đủ. */
    fun exhausted(c: Context, extraMs: Long = 0L): Boolean =
        hasPin(c) && kids(c) && usedKidsMs(c) + extraMs >= kidsTotalMin(c) * 60_000L

    // ---- Phiên cả nhà ----
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
    fun netBlocked(c: Context) = hasPin(c) && (manualNet(c) || exhausted(c))

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

    // ---- Tự cấp lại lượt trẻ em mỗi ngày (mặc định bật) ----
    fun dailyRefresh(c: Context) = sp(c).getBoolean("daily", true)
    fun setDailyRefresh(c: Context, v: Boolean) { sp(c).edit().putBoolean("daily", v).apply() }

    // ---- Nhận biết TV vừa khởi động lại ----
    fun lastBoot(c: Context) = sp(c).getInt("lastBoot", -2)
    fun setLastBoot(c: Context, v: Int) { sp(c).edit().putInt("lastBoot", v).apply() }

    // ---- Cổng PIN vào Cài đặt hệ thống ----
    fun settingsUntil(c: Context) = sp(c).getLong("settingsUntil", 0)
    fun setSettingsUntil(c: Context, t: Long) { sp(c).edit().putLong("settingsUntil", t).apply() }
}
