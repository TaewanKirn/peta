package com.peta.car

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class PetaNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_MONITOR, context.getString(R.string.channel_monitor), NotificationManager.IMPORTANCE_MIN)
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ALERT, context.getString(R.string.channel_alert), NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun monitorNotification(): Notification =
        NotificationCompat.Builder(context, CHANNEL_MONITOR)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.monitor_running))
            .setContentIntent(openApp())
            .setOngoing(true)
            .build()

    fun showLongStay(minutes: Long) = notify(
        ID_STAY,
        context.getString(R.string.stay_title, minutes),
        context.getString(R.string.stay_body),
    )

    /** 주행 중 알림 허용 여부는 Pleos 정책 확인 필요 — 정차 시 화면 카드가 기본 동선 */
    fun showRestDue(minutes: Long) = notify(
        ID_REST,
        context.getString(R.string.rest_title),
        context.getString(R.string.rest_body, minutes),
    )

    fun clear(id: Int) = manager.cancel(id)

    private fun notify(id: Int, title: String, body: String) {
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        context, 0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val ID_MONITOR = 1
        const val ID_STAY = 2
        const val ID_REST = 3
        private const val CHANNEL_MONITOR = "monitor"
        private const val CHANNEL_ALERT = "alert"
    }
}
