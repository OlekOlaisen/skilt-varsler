package no.skiltvarsler.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import no.skiltvarsler.MainActivity
import no.skiltvarsler.R
import no.skiltvarsler.car.SkiltCarAppService
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind

object AlertNotifier {
    const val CHANNEL_DRIVING = "driving"
    const val CHANNEL_ALERT = "alert"
    const val DRIVING_NOTIFICATION_ID = 10
    const val ALERT_NOTIFICATION_ID = 20

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DRIVING,
                context.getString(R.string.channel_driving),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                context.getString(R.string.channel_alert),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
                setBypassDnd(false)
            },
        )
    }

    fun drivingNotification(context: Context, status: String): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_DRIVING)
            .setSmallIcon(R.drawable.ic_alert_camera)
            .setContentTitle(context.getString(R.string.tracking_title))
            .setContentText(status)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    fun publishAlert(context: Context, alert: Alert) {
        LastAlertStore.update(alert)
        val openApp = PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val icon = iconRes(alert.kind)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(icon)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setContentIntent(openApp)
            .setTimeoutAfter(8_000)
        extendForCar(context, builder, alert, icon)
        try {
            CarNotificationManager.from(context).notify(ALERT_NOTIFICATION_ID, builder)
        } catch (_: Exception) {
            notifyOnPhone(context, builder)
        }
    }

    private fun extendForCar(
        context: Context,
        builder: NotificationCompat.Builder,
        alert: Alert,
        icon: Int,
    ) {
        val openCar = try {
            CarPendingIntent.getCarApp(
                context,
                2,
                Intent(Intent.ACTION_VIEW).setClass(context, SkiltCarAppService::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } catch (_: Exception) {
            null
        } ?: return
        builder.extend(
            CarAppExtender.Builder()
                .setContentTitle(alert.title)
                .setContentText(alert.body)
                .setSmallIcon(icon)
                .setImportance(NotificationManagerCompat.IMPORTANCE_HIGH)
                .setContentIntent(openCar)
                .build(),
        )
    }

    private fun notifyOnPhone(context: Context, builder: NotificationCompat.Builder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            NotificationManagerCompat.from(context).notify(ALERT_NOTIFICATION_ID, builder.build())
        } catch (_: SecurityException) {
            // Notification permission not granted yet.
        }
    }

    fun iconRes(kind: AlertKind): Int = when (kind) {
        AlertKind.SPEED_CAMERA, AlertKind.SECTION_ATK_START, AlertKind.SECTION_ATK_END ->
            R.drawable.ic_alert_camera
        AlertKind.STOP -> R.drawable.ic_alert_stop
        AlertKind.YIELD -> R.drawable.ic_alert_yield
        AlertKind.SPEED_LIMIT -> R.drawable.ic_alert_speed
        AlertKind.WILDLIFE -> R.drawable.ic_alert_wildlife
        AlertKind.RAILWAY -> R.drawable.ic_alert_rail
        AlertKind.FERRY -> R.drawable.ic_alert_ferry
        AlertKind.TOLL -> R.drawable.ic_alert_toll
        AlertKind.HAZARD -> R.drawable.ic_alert_hazard
        AlertKind.MUNICIPALITY, AlertKind.PRIORITY_ROAD -> R.drawable.ic_alert_border
    }
}
