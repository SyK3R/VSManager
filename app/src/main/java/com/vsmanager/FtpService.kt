package com.vsmanager

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

class FtpService : Service() {

    companion object {
        const val ACTION_STOP = "com.vsmanager.FTP_STOP"
        @Volatile var running = false
        @Volatile var address = ""
        @Volatile var error: String? = null
        var onChange: (() -> Unit)? = null

        fun localIp(): String? = try {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }?.hostAddress
        } catch (e: Exception) { null }
    }

    private var server: SimpleFtpServer? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { stopSelf(); return START_NOT_STICKY }
        if (running) return START_NOT_STICKY

        try {
            startInForeground()
        } catch (e: Exception) {
            fail("Erro ao iniciar o servico: ${e.message}")
            return START_NOT_STICKY
        }

        val ip = localIp()
        if (ip == null) { fail("Conecte-se a uma rede Wi-Fi primeiro"); return START_NOT_STICKY }

        val p = getSharedPreferences("ftp", Context.MODE_PRIVATE)
        val port = p.getInt("port", 2121)
        val user = p.getString("user", "admin") ?: "admin"
        val pass = p.getString("pass", "") ?: ""
        val anon = p.getBoolean("anon", false)
        val canWrite = p.getBoolean("write", true)
        val root = File(p.getString("root", Environment.getExternalStorageDirectory().absolutePath)!!)

        if (!anon && pass.isEmpty()) { fail("Defina uma senha ou ative o login anonimo"); return START_NOT_STICKY }

        try {
            val s = SimpleFtpServer(port, root, user, pass, anon, !canWrite)
            s.start()
            server = s
            running = true; error = null
            address = "ftp://$ip:$port"

            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "vsmanager:ftp").also { it.acquire() }
        } catch (e: Exception) {
            fail("Nao foi possivel iniciar: ${e.message}")
            return START_NOT_STICKY
        }
        notifyChange()
        return START_NOT_STICKY
    }

    private fun fail(msg: String) {
        running = false; error = msg
        notifyChange()
        stopSelf()
    }

    override fun onDestroy() {
        try { server?.stop() } catch (_: Exception) {}
        try { wifiLock?.release() } catch (_: Exception) {}
        server = null; running = false; address = ""
        notifyChange()
        super.onDestroy()
    }

    private fun notifyChange() {
        Handler(Looper.getMainLooper()).post { onChange?.invoke() }
        try {
            android.service.quicksettings.TileService.requestListeningState(
                applicationContext, android.content.ComponentName(applicationContext, FtpTileService::class.java))
        } catch (_: Exception) {}
    }

    private fun startInForeground() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel("ftp", "Servidor FTP", NotificationManager.IMPORTANCE_LOW))
        }
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val stop = PendingIntent.getService(this, 1,
            Intent(this, FtpService::class.java).setAction(ACTION_STOP), flags)

        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, "ftp") else Notification.Builder(this)
        val n = b.setContentTitle("Servidor FTP ligado")
            .setContentText("Toque para abrir o VSManager")
            .setSmallIcon(R.drawable.ic_storage)
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, "Desligar", stop).build())
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= 29) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else startForeground(1, n)
    }
}
