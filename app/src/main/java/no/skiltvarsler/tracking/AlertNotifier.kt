package no.skiltvarsler.tracking

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.RemoteViews
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.car.app.notification.CarPendingIntent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.skiltvarsler.MainActivity
import no.skiltvarsler.R
import no.skiltvarsler.car.SkiltCarAppService
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertCombine
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.signs.SignRenderer
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

object AlertNotifier {
    const val CHANNEL_DRIVING = "driving_local"
    const val CHANNEL_ALERT = "alert"
    const val DRIVING_NOTIFICATION_ID = 10
    const val ALERT_NOTIFICATION_ID = 20
    /** How long each heads-up stays posted before dismiss or the next queued alert. */
    const val DISPLAY_SLOT_MS = 5_000L
    private const val ALERT_NOTIFICATION_POOL_SIZE = 16
    private const val SIGN_BITMAP_SIZE_PX = 192
    private const val PHONE_CONTENT_REQUEST_CODE = 0
    private const val CAR_CONTENT_REQUEST_CODE = 10
    private val alertNotificationSequence = AtomicInteger(0)
    private val lastPostedAlert = AtomicReference<PostedAlert?>(null)
    private val displayScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val displayLock = Any()
    private val pendingDisplay = ArrayDeque<DisplayItem>()
    private var displayDrainJob: Job? = null
    private var showingKey: String? = null

    private data class PostedAlert(
        val tag: String,
        val notificationId: Int,
    )

    private data class DisplayItem(
        val displayAlert: Alert,
        val logAlerts: List<Alert>,
    )

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

    fun publishAlert(
        context: Context,
        alert: Alert,
        settings: AlertSettings = AlertSettings.ALL_ON,
    ) {
        publishSequential(context, listOf(alert), settings)
    }

    /**
     * Queues each alert as its own heads-up slot (priority order).
     * Slot 1 shows for [DISPLAY_SLOT_MS], then slot 2, and so on; each slot is dismissed
     * before the next posts (or after the last one finishes).
     */
    fun publishSequential(
        context: Context,
        alerts: List<Alert>,
        settings: AlertSettings = AlertSettings.ALL_ON,
    ) {
        if (alerts.isEmpty()) {
            return
        }
        val ordered = AlertCombine.dedupeAndSort(alerts, settings)
        enqueueDisplay(
            context = context,
            items = ordered.map { alert ->
                DisplayItem(
                    displayAlert = alert,
                    logAlerts = listOf(alert),
                )
            },
        )
    }

    /**
     * Publishes one heads-up for one or more near-simultaneous alerts.
     * Multiple alerts become a single title like "Fartsgrense 40 · Forkjørsveg".
     */
    fun publishCombined(
        context: Context,
        alerts: List<Alert>,
        settings: AlertSettings = AlertSettings.ALL_ON,
    ) {
        if (alerts.isEmpty()) {
            return
        }
        val ordered = AlertCombine.dedupeAndSort(alerts, settings)
        val merged = AlertCombine.merge(ordered, settings)
        enqueueDisplay(
            context = context,
            items = listOf(
                DisplayItem(
                    displayAlert = merged,
                    logAlerts = ordered,
                ),
            ),
        )
    }

    /**
     * Cancels the active heads-up and drops any queued slots (e.g. trip end).
     */
    fun resetDisplay(context: Context) {
        synchronized(displayLock) {
            displayDrainJob?.cancel()
            displayDrainJob = null
            pendingDisplay.clear()
            showingKey = null
        }
        dismissCurrentAlert(context)
    }

    private fun enqueueDisplay(context: Context, items: List<DisplayItem>) {
        if (items.isEmpty()) {
            return
        }
        val appContext = context.applicationContext
        synchronized(displayLock) {
            for (item in items) {
                val key = displayKey(item.displayAlert)
                if (showingKey == key) {
                    continue
                }
                if (pendingDisplay.any { pending -> displayKey(pending.displayAlert) == key }) {
                    continue
                }
                pendingDisplay.addLast(item)
            }
            if (displayDrainJob?.isActive == true) {
                return
            }
            displayDrainJob = displayScope.launch {
                drainDisplayQueue(appContext)
            }
        }
    }

    private suspend fun drainDisplayQueue(context: Context) {
        while (true) {
            if (LastAlertStore.alertsMuted) {
                synchronized(displayLock) {
                    pendingDisplay.clear()
                    showingKey = null
                }
                dismissCurrentAlert(context)
                break
            }
            val item = synchronized(displayLock) {
                val next = pendingDisplay.removeFirstOrNull()
                showingKey = next?.let { candidate -> displayKey(candidate.displayAlert) }
                next
            } ?: break
            publishAlertContent(
                context = context,
                displayAlert = item.displayAlert,
                logAlerts = item.logAlerts,
            )
            delay(DISPLAY_SLOT_MS)
            dismissCurrentAlert(context)
            synchronized(displayLock) {
                showingKey = null
            }
        }
        synchronized(displayLock) {
            displayDrainJob = null
            if (pendingDisplay.isNotEmpty()) {
                val appContext = context.applicationContext
                displayDrainJob = displayScope.launch {
                    drainDisplayQueue(appContext)
                }
            }
        }
    }

    private fun displayKey(alert: Alert): String {
        return "${alert.kind}:${alert.nvdbId}:${alert.title}"
    }

    fun dismissCurrentAlert(context: Context) {
        try {
            val manager = CarNotificationManager.from(context)
            lastPostedAlert.getAndSet(null)?.let { previous ->
                manager.cancel(previous.tag, previous.notificationId)
            }
        } catch (_: SecurityException) {
            // Notification permission can disappear mid-trip; ignore.
        } catch (error: Exception) {
            DebugLog.append("NOTIFY dismiss failed: ${error.message}")
        }
    }

    /**
     * Posts a driving-relevant alert via [CarAppExtender] at [NotificationManager.IMPORTANCE_HIGH]
     * so Android Auto can show a heads-up without MessagingStyle / reply actions.
     */
    private fun publishAlertContent(
        context: Context,
        displayAlert: Alert,
        logAlerts: List<Alert>,
    ) {
        LastAlertStore.update(displayAlert)
        logAlerts.forEach { alert -> DebugLog.appendAlert(alert) }
        val icon = iconRes(displayAlert.kind)
        val titleText = displayAlert.title
        // Secondary must carry info only — never echo the title (Android Auto HUN looks broken).
        val subtitleText = displayAlert.body
            .takeIf { body -> body.isNotBlank() && !body.equals(titleText, ignoreCase = true) }
            .orEmpty()
        val sign = signBitmap(context, displayAlert, icon)
        val customView = alertRemoteViews(context, titleText, subtitleText, sign)
        val builder = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setSmallIcon(icon)
            .setContentTitle(titleText)
            .setContentText(subtitleText.ifBlank { null })
            .apply {
                val bigTextStyle = NotificationCompat.BigTextStyle()
                    .setBigContentTitle(titleText)
                if (subtitleText.isNotBlank()) {
                    bigTextStyle.bigText(subtitleText)
                }
                setStyle(bigTextStyle)
            }
            .setLargeIcon(sign)
            .setCustomContentView(customView)
            .setCustomBigContentView(customView)
            .setCustomHeadsUpContentView(customView)
            .setShowWhen(false)
            .setOnlyAlertOnce(false)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_VIBRATE)
            .setAutoCancel(true)
            .setContentIntent(phoneContentIntent(context))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .extend(carAppExtender(context, titleText, subtitleText, icon, sign))
        try {
            // Android Auto suppresses a new heads-up when the same (tag, id) is updated while a
            // HUN is still visible. Cancel the previous alert and post a fresh key each time.
            val manager = CarNotificationManager.from(context)
            lastPostedAlert.getAndSet(null)?.let { previous ->
                manager.cancel(previous.tag, previous.notificationId)
            }
            val postedAlert = PostedAlert(
                tag = alertNotificationTag(displayAlert),
                notificationId = nextAlertNotificationId(),
            )
            lastPostedAlert.set(postedAlert)
            manager.notify(postedAlert.tag, postedAlert.notificationId, builder)
        } catch (error: SecurityException) {
            DebugLog.append("NOTIFY permission denied")
        } catch (error: Exception) {
            DebugLog.append("NOTIFY car failed: ${error.message}")
        }
    }

    private fun nextAlertNotificationId(): Int {
        val slot = alertNotificationSequence.getAndIncrement().mod(ALERT_NOTIFICATION_POOL_SIZE)
        return ALERT_NOTIFICATION_ID + slot
    }

    private fun alertNotificationTag(alert: Alert): String {
        return "alert-${alert.sequenceId}-${alert.nvdbId}-${alert.kind.name}"
    }

    private fun signBitmap(context: Context, alert: Alert, icon: Int): Bitmap {
        SignRenderer.bitmap(context, alert, SIGN_BITMAP_SIZE_PX)?.let { bitmap ->
            return bitmap
        }
        val drawable = ContextCompat.getDrawable(context, icon)
            ?: error("Missing notification icon $icon")
        val bitmap = Bitmap.createBitmap(
            SIGN_BITMAP_SIZE_PX,
            SIGN_BITMAP_SIZE_PX,
            Bitmap.Config.ARGB_8888,
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, SIGN_BITMAP_SIZE_PX, SIGN_BITMAP_SIZE_PX)
        drawable.draw(canvas)
        return bitmap
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

    private fun carAppExtender(
        context: Context,
        titleText: String,
        subtitleText: String,
        icon: Int,
        sign: Bitmap,
    ): CarAppExtender {
        val extender = CarAppExtender.Builder()
            .setContentTitle(titleText)
            .setSmallIcon(icon)
            .setLargeIcon(sign)
            .setImportance(NotificationManager.IMPORTANCE_HIGH)
            .setContentIntent(carAppContentIntent(context))
        if (subtitleText.isNotBlank()) {
            extender.setContentText(subtitleText)
        }
        return extender.build()
    }

    private fun phoneContentIntent(context: Context): PendingIntent {
        return PendingIntent.getActivity(
            context,
            PHONE_CONTENT_REQUEST_CODE,
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
            CAR_CONTENT_REQUEST_CODE,
            carIntent,
            PendingIntent.FLAG_UPDATE_CURRENT,
        )
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
        AlertKind.ROADWORK -> R.drawable.ic_alert_hazard
        AlertKind.ACCIDENT -> R.drawable.ic_alert_hazard
        AlertKind.MUNICIPALITY, AlertKind.PRIORITY_ROAD -> R.drawable.ic_alert_border
    }
}
