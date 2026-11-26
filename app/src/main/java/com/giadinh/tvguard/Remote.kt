package com.giadinh.tvguard

import android.content.Context
import android.net.VpnService
import android.os.PowerManager
import com.giadinh.tvguard.shared.Proto
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.math.abs

/** Nhận lệnh đã mã hoá từ điện thoại người lớn và đăng trạng thái TV. */
class Remote(private val ctx: Context) {
    @Volatile private var running = false
    @Volatile private var ack: JSONObject? = null
    private val exec = Executors.newSingleThreadExecutor()

    fun start() {
        if (running) return
        running = true
        Thread { loop() }.apply { isDaemon = true; start() }
    }

    fun stop() { running = false }

    fun publishAsync() { exec.execute { publish() } }

    private fun keys(): Proto.Keys? =
        if (Prefs.hasPin(ctx)) Proto.parse(Prefs.pairCode(ctx)) else null

    private fun sleepQ(ms: Long) { try { Thread.sleep(ms) } catch (e: InterruptedException) { } }

    private fun loop() {
        var wait = 2000L
        while (running) {
            val k = keys()
            if (k == null) { sleepQ(5000); continue }
            try {
                Proto.stream(k.cmd, "15m") { msg, _ ->
                    handle(k, msg)
                    running && keys()?.code == k.code
                }
                wait = 2000L
            } catch (e: Exception) { }
            sleepQ(wait)
            wait = minOf(wait * 2, 60_000L)
        }
    }

    private fun handle(k: Proto.Keys, line: String) {
        val plain = Proto.dec(k, line) ?: return
        val j = try { JSONObject(plain) } catch (e: Exception) { return }
        val ts = j.optLong("ts")
        val now = System.currentTimeMillis()
        // Chống phát lại lệnh cũ: ts phải mới hơn lệnh trước và còn "tươi".
        if (ts <= Prefs.lastCmdTs(ctx) || abs(now - ts) > 30 * 60_000L) return
        Prefs.setLastCmdTs(ctx, ts)

        var ok = false
        var err = ""
        val cmd = j.optString("c")
        if (!Prefs.hasPin(ctx)) err = "nopin"
        else if (cmd != "kids" && Prefs.banSeconds(ctx) > 0) err = "ban"
        else if (cmd != "kids" && !Prefs.checkPin(ctx, j.optString("pin"))) err = "badpin"
        else {
            ok = true
            when (cmd) {
                "grant" -> Prefs.addKidsBonus(ctx, j.optInt("m", 20).coerceIn(1, 240))
                "family" -> Prefs.startFamily(ctx, j.optInt("m", 0).coerceIn(0, 720))
                "kids" -> Prefs.endFamily(ctx)
                "limit" -> Prefs.setLimitKids(ctx, j.optInt("m", 20))
                "net" -> Prefs.setManualNet(ctx, j.optBoolean("block"))
                "setpin" -> {
                    val n = j.optString("new")
                    if (n.matches(Regex("\\d{4,8}"))) Prefs.setPin(ctx, n) else { ok = false; err = "badnewpin" }
                }
                "ping" -> {}
                else -> { ok = false; err = "unknown" }
            }
        }
        ack = JSONObject().put("ts", ts).put("ok", ok).put("err", err)
        publish()
    }

    private fun status(): JSONObject {
        val pm = ctx.getSystemService(PowerManager::class.java)
        return JSONObject()
            .put("ts", System.currentTimeMillis())
            .put("kids", Prefs.kids(ctx))
            .put("locked", Prefs.exhausted(ctx))
            .put("net", NetBlockService.running)
            .put("manualNet", Prefs.manualNet(ctx))
            .put("vpnOk", VpnService.prepare(ctx) == null)
            .put("screen", pm.isInteractive)
            .put("kUsed", Prefs.usedKidsMs(ctx) / 60_000)
            .put("kTotal", Prefs.kidsTotalMin(ctx))
            .put("kLimit", Prefs.limitKids(ctx))
            .put("fUsed", Prefs.famUsedMs(ctx) / 60_000)
            .put("fLimit", Prefs.familyLimitMin(ctx))
            .put("ack", ack ?: JSONObject())
    }

    private fun publish() {
        val k = keys() ?: return
        try { Proto.post(k.st, Proto.enc(k, status().toString())) } catch (e: Exception) { }
    }
}
