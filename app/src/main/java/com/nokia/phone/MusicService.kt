package com.nokia.phone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/** Dịch vụ nền cho nhạc đã tải: giữ tiến trình sống khi tắt màn hình. Âm thanh do [Music] phát. */
class MusicService : Service() {
    companion object {
        private const val CH = "nokia_music_play"
        private const val NOTI_ID = 3100
        fun start(ctx: Context, title: String) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, MusicService::class.java).putExtra("title", title))
        }
        fun stop(ctx: Context) { ctx.stopService(Intent(ctx, MusicService::class.java)) }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra("title").orEmpty().ifEmpty { "Đang phát nhạc" }
        if (Build.VERSION.SDK_INT >= 26) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(NotificationChannel(CH, "Đang phát nhạc", NotificationManager.IMPORTANCE_LOW))
        }
        val open = PendingIntent.getActivity(this, 5,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n: Notification = NotificationCompat.Builder(this, CH)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(title)
            .setContentText("Nhạc đã tải trong máy")
            .setContentIntent(open)
            .setOngoing(true)
            .setSilent(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) startForeground(NOTI_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            else startForeground(NOTI_ID, n)
        } catch (_: Exception) {}
        return START_NOT_STICKY
    }
}
