package com.giadinh.tvguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.ParcelFileDescriptor
import java.io.FileInputStream

/**
 * Khoá Internet bằng VPN "hố đen": mọi gói tin của mọi ứng dụng đi vào đường hầm và bị bỏ,
 * RIÊNG ứng dụng này được loại trừ để vẫn nhận lệnh từ điện thoại người lớn.
 */
class NetBlockService : VpnService() {
    companion object { @Volatile var running = false }

    private var pfd: ParcelFileDescriptor? = null

    private fun notif(): Notification {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("guard", "TV Guard", NotificationManager.IMPORTANCE_MIN))
        return Notification.Builder(this, "guard")
            .setContentTitle("TV Guard: Internet đang bị khoá")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(1, notif())
        if (pfd == null) {
            pfd = try {
                Builder().setSession("TV Guard")
                    .addAddress("10.255.255.1", 32)
                    .addAddress("fd00:1::1", 128)
                    .addRoute("0.0.0.0", 0)
                    .addRoute("::", 0)
                    .addDisallowedApplication(packageName)
                    .setBlocking(true)
                    .establish()
            } catch (e: Exception) { null }
            val fd = pfd
            if (fd == null) {
                stopSelf()
                return START_NOT_STICKY
            }
            running = true
            Thread {
                val buf = ByteArray(32767)
                try {
                    val ins = FileInputStream(fd.fileDescriptor)
                    while (ins.read(buf) >= 0) { /* bỏ gói tin */ }
                } catch (e: Exception) { }
            }.apply { isDaemon = true; start() }
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        running = false
        try { pfd?.close() } catch (e: Exception) { }
        pfd = null
        super.onDestroy()
    }
}
