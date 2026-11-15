package com.melodia.app.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.melodia.app.MainActivity
import com.melodia.app.R
import com.melodia.app.Str

/** Mantiene vivo el ecualizador global mientras está activado (Android mata los efectos si la app se cierra). */
class GlobalEqService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Botón "X" de la notificación: apaga el ecualizador global y cierra este servicio.
        if (intent?.action == ACTION_STOP) {
            (application as com.melodia.app.MelodiaApp).equalizer.setGlobal(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_REPOST &&
            !(application as com.melodia.app.MelodiaApp).equalizer.global.value
        ) {
            stopSelf()
            return START_NOT_STICKY
        }
        // ACTION_REPOST llega cuando el usuario desliza la notificación (Android 14+ lo permite aunque
        // sea "ongoing"): mientras el ecualizador siga activo, la notificación vuelve a aparecer.
        // Solo se quita con la X, que además apaga el ecualizador.
        val nm = getSystemService(NotificationManager::class.java)
        // El canal antiguo era de importancia mínima (sin ícono en la barra); un canal no puede subir
        // de importancia, así que se usa uno nuevo y se borra el viejo.
        runCatching { nm.deleteNotificationChannel(OLD_CHANNEL) }
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, Str.get(R.string.eq_global_title), NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif_eq)
            .setContentTitle(Str.get(R.string.eq_global_title))
            .setContentText(Str.get(R.string.eq_global_notif))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setDeleteIntent(
                PendingIntent.getService(
                    this, 2, Intent(this, GlobalEqService::class.java).setAction(ACTION_REPOST),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            )
            .addAction(
                Notification.Action.Builder(
                    android.graphics.drawable.Icon.createWithResource(this, R.drawable.ic_notif_close),
                    Str.get(R.string.eq_global_close),
                    PendingIntent.getService(
                        this, 1, Intent(this, GlobalEqService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                ).build()
            )
            .build()
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(ID, n)
        }
        return START_STICKY
    }

    private companion object {
        const val ACTION_STOP = "com.lunaplayer.eq.STOP"
        const val ACTION_REPOST = "com.lunaplayer.eq.REPOST"
        const val OLD_CHANNEL = "global_eq"
        const val CHANNEL = "global_eq_on"
        const val ID = 4207
    }
}
