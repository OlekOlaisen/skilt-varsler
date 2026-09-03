package no.skiltvarsler.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import androidx.core.graphics.drawable.IconCompat
import no.skiltvarsler.MainActivity
import no.skiltvarsler.R
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.car.CarMessageActionService
import no.skiltvarsler.car.SkiltCarAppService
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.signs.SignRenderer

object AlertNotifier {
    const val CHANNEL_DRIVING = "driving_local"
    const val CHANNEL_ALERT = "alert"
    const val DRIVING_NOTIFICATION_ID = 10
    const val ALERT_NOTIFICATION_ID = 20

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.deleteNotificationChannel("driving")
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_DRIVING,
                context.getString(R.string.channel_driving),
                NotificationManager.IMPORTANCE_MIN,
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
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

    fun drivingNotification(context: Context): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_DRIVING)
            .setSmallIcon(R.drawable.ic_alert_camera)
            .setContentTitle(context.getString(R.string.app_name))
            .setOngoing(true)
            .setSilent(true)
            .setLocalOnly(true)
            .setShowWhen(false)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(open)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_DEFERRED)
            .build()
    }

    fun publishAlert(context: Context, alert: Alert) {
        LastAlertStore.update(alert)
        DebugLog.appendAlert(alert)
        val icon = iconRes(alert.kind)
        val titleText = alert.title
        val subtitleText = alert.body
        val sign = SignRenderer.bitmap(context, alert, 192)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(icon)
            .setContentTitle(titleText)
            .setContentText(subtitleText.ifBlank { null })
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setOnlyAlertOnce(true)
            .setAutoCancel(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(phoneContentIntent(context))
            .addAction(replyAction(context))
            .addAction(markAsReadAction(context))
        if (sign != null) {
            val customView = alertRemoteViews(context, titleText, subtitleText, sign)
            builder
                .setStyle(messagingStyleFor(context, titleText, subtitleText, sign))
                .setLargeIcon(sign)
                .setCustomContentView(customView)
                .setCustomBigContentView(customView)
                .setCustomHeadsUpContentView(customView)
        }
        builder.extend(carAppExtender(context, titleText, subtitleText, icon, sign))
        try {
            CarNotificationManager.from(context).notify(ALERT_NOTIFICATION_ID, builder)
        } catch (_: Exception) {
            notifyOnPhone(context, builder)
        }
    }

    private fun alertRemoteViews(
        context: Context,
        titleText: String,
        subtitleText: String,
        sign: Bitmap,
    ): RemoteViews {
        return RemoteViews(context.packageName, R.layout.notification_alert).apply {
            setImageViewBitmap(R.id.notification_sign, sign)
            setTextViewText(R.id.notification_title, titleText)
            if (subtitleText.isBlank()) {
                setViewVisibility(R.id.notification_subtitle, View.GONE)
            } else {
                setViewVisibility(R.id.notification_subtitle, View.VISIBLE)
                setTextViewText(R.id.notification_subtitle, subtitleText)
            }
        }
    }

    private fun messagingStyleFor(
        context: Context,
        titleText: String,
        subtitleText: String,
        sign: Bitmap,
    ): NotificationCompat.MessagingStyle {
        val signIcon = IconCompat.createWithBitmap(sign)
        val driver = Person.Builder()
            .setName(context.getString(R.string.app_name))
            .setKey("driver")
            .build()
        val hasSubtitle = subtitleText.isNotBlank()
        val sender = Person.Builder()
            .setName(if (hasSubtitle) context.getString(R.string.app_name) else titleText)
            .setKey("skilt-varsler")
            .setIcon(signIcon)
            .setImportant(true)
            .build()
        val style = NotificationCompat.MessagingStyle(driver)
            .setGroupConversation(false)
        if (hasSubtitle) {
            style.setConversationTitle(titleText)
        }
        style.addMessage(
            NotificationCompat.MessagingStyle.Message(
                if (hasSubtitle) subtitleText else "\u200B",
                System.currentTimeMillis(),
                sender,
            ),
        )
        return style
    }

    private fun carAppExtender(
        context: Context,
        titleText: String,
        subtitleText: String,
        icon: Int,
        sign: Bitmap?,
    ): CarAppExtender {
        val extender = CarAppExtender.Builder()
            .setContentTitle(titleText)
            .setContentText(subtitleText)
            .setSmallIcon(icon)
            .setImportance(NotificationManager.IMPORTANCE_HIGH)
            .setContentIntent(carAppContentIntent(context))
        if (sign != null) {
            extender.setLargeIcon(sign)
        }
        return extender.build()
    }

    private fun phoneContentIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            1,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun carAppContentIntent(context: Context): PendingIntent {
        val carIntent = Intent(Intent.ACTION_VIEW).setComponent(
            ComponentName(context, SkiltCarAppService::class.java),
        )
        return CarPendingIntent.getCarApp(
            context,
            2,
            carIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * Android Auto only shows heads-up over other apps for messaging
     * notifications that include reply and mark-as-read actions.
     */
    private fun replyAction(context: Context): NotificationCompat.Action {
        val replyIntent = Intent(context, CarMessageActionService::class.java).apply {
            action = CarMessageActionService.ACTION_REPLY
        }
        val replyPending = PendingIntent.getService(
            context,
            11,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        val replyInput = RemoteInput.Builder(CarMessageActionService.REMOTE_INPUT_RESULT_KEY)
            .setLabel(context.getString(R.string.car_notification_reply))
            .build()
        return NotificationCompat.Action.Builder(
            iconRes(AlertKind.SPEED_CAMERA),
            context.getString(R.string.car_notification_reply),
            replyPending,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .addRemoteInput(replyInput)
            .build()
    }

    private fun markAsReadAction(context: Context): NotificationCompat.Action {
        val markIntent = Intent(context, CarMessageActionService::class.java).apply {
            action = CarMessageActionService.ACTION_MARK_AS_READ
        }
        val markPending = PendingIntent.getService(
            context,
            12,
            markIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Action.Builder(
            iconRes(AlertKind.SPEED_CAMERA),
            context.getString(R.string.car_notification_mark_as_read),
            markPending,
        )
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_MARK_AS_READ)
            .setShowsUserInterface(false)
            .build()
    }

    private fun notifyOnPhone(context: Context, builder: NotificationCompat.Builder) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return
        }
        try {
            NotificationManagerCompat.from(context)
                .notify(ALERT_NOTIFICATION_ID, builder.build())
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
