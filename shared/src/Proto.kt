package com.giadinh.tvguard.shared

import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Kênh liên lạc TV <-> điện thoại qua dịch vụ pub/sub công khai ntfy.sh.
 * Mã ghép đôi = 32 byte ngẫu nhiên: 16 byte đầu đặt tên "topic" khó đoán, 16 byte sau là khoá AES-128-GCM.
 * Mọi tin nhắn đều được mã hoá + xác thực, người ngoài không đọc hay giả lệnh được.
 */
object Proto {
    const val BASE = "https://ntfy.sh"
    private val F = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP

    class Keys(val code: String, topicBase: String, val key: SecretKeySpec) {
        val cmd = "$topicBase-c"   // điện thoại -> TV
        val st = "$topicBase-s"    // TV -> điện thoại
    }

    fun newCode(): String {
        val b = ByteArray(32)
        SecureRandom().nextBytes(b)
        return Base64.encodeToString(b, F)
    }

    fun parse(raw: String): Keys? {
        val s = raw.filter { !it.isWhitespace() }
        val b = try { Base64.decode(s, F) } catch (e: Exception) { return null }
        if (b.size != 32) return null
        val hex = b.copyOfRange(0, 16).joinToString("") { "%02x".format(it) }
        return Keys(s, "tvg-$hex", SecretKeySpec(b.copyOfRange(16, 32), "AES"))
    }

    fun pretty(code: String) = code.chunked(4).joinToString(" ")

    fun enc(k: Keys, plain: String): String {
        val iv = ByteArray(12)
        SecureRandom().nextBytes(iv)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, k.key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(plain.toByteArray()), F)
    }

    fun dec(k: Keys, s: String): String? = try {
        val all = Base64.decode(s.trim(), F)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, k.key, GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)))
    } catch (e: Exception) { null }

    fun post(topic: String, body: String): Boolean = try {
        val c = URL("$BASE/$topic").openConnection() as HttpURLConnection
        c.requestMethod = "POST"
        c.doOutput = true
        c.connectTimeout = 15000
        c.readTimeout = 15000
        c.outputStream.use { it.write(body.toByteArray()) }
        val ok = c.responseCode in 200..299
        c.disconnect()
        ok
    } catch (e: Exception) { false }

    /** Nghe liên tục. onMsg(message, serverTimeSeconds) trả false để dừng. Chặn luồng gọi. */
    fun stream(topic: String, since: String, onMsg: (String, Long) -> Boolean) {
        val c = URL("$BASE/$topic/json?since=$since").openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 120000
        try {
            c.inputStream.bufferedReader().use { r ->
                while (true) {
                    val line = r.readLine() ?: break
                    if (line.isBlank()) continue
                    val j = try { JSONObject(line) } catch (e: Exception) { continue }
                    if (j.optString("event") == "message") {
                        if (!onMsg(j.optString("message"), j.optLong("time"))) return
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }
}
