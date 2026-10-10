package no.skiltvarsler.car

import android.content.Intent
import android.text.SpannableString
import android.text.Spanned
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Distance
import androidx.car.app.model.DistanceSpan
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.skiltvarsler.R
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.AlertCopy
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.signs.SignRenderer
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import no.skiltvarsler.tracking.TrackingService
import no.skiltvarsler.tracking.UpcomingSign

private const val CAR_LOG_TAG = "SkiltCarApp"
private const val UPCOMING_SIGN_BITMAP_SIZE_PX = 128

class SkiltCarAppService : CarAppService() {
    override fun onCreate() {
        super.onCreate()
        Log.i(CAR_LOG_TAG, "CarAppService.onCreate")
    }

    override fun createHostValidator(): HostValidator {
        // Always allow while developing against DHU / unknown hosts.
        Log.i(CAR_LOG_TAG, "createHostValidator ALLOW_ALL")
        return HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session {
        Log.i(
            CAR_LOG_TAG,
            "onCreateSession displayType=${sessionInfo.displayType} " +
                "sessionId=${sessionInfo.sessionId}",
        )
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                Log.i(CAR_LOG_TAG, "onCreateScreen action=${intent.action}")
                return StatusScreen(carContext)
            }
        }
    }
}

/**
 * Android Auto home: upcoming signs with distances, plus Start/Stop and mute.
 * Uses [ListTemplate] under the IOT category (no host map surface).
 */
class StatusScreen(carContext: CarContext) : Screen(carContext) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val onStoreChanged: () -> Unit = {
        carContext.mainExecutor.execute { invalidate() }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                LastAlertStore.addListener(onStoreChanged)
            }

            override fun onStop(owner: LifecycleOwner) {
                LastAlertStore.removeListener(onStoreChanged)
            }

            override fun onDestroy(owner: LifecycleOwner) {
                LastAlertStore.removeListener(onStoreChanged)
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        return try {
            val template = buildUpcomingTemplate()
            Log.i(
                CAR_LOG_TAG,
                "onGetTemplate ok upcoming=${LastAlertStore.upcomingSigns().size} " +
                    "active=${LastAlertStore.trackingActive}",
            )
            template
        } catch (error: Throwable) {
            Log.e(CAR_LOG_TAG, "onGetTemplate failed", error)
            DebugLog.append("CAR template failed: ${error.javaClass.simpleName}: ${error.message}")
            fallbackMessageTemplate()
        }
    }

    private fun buildUpcomingTemplate(): Template {
        val muted = LastAlertStore.alertsMuted
        val tripActive = LastAlertStore.trackingActive
        val upcomingSigns = LastAlertStore.upcomingSigns()
            .sortedBy { sign -> sign.metersAhead }

        val itemListBuilder = ItemList.Builder()
        if (upcomingSigns.isEmpty()) {
            itemListBuilder.addItem(emptyStateRow(tripActive = tripActive, muted = muted))
        } else {
            upcomingSigns.forEach { sign ->
                itemListBuilder.addItem(upcomingSignRow(sign))
            }
        }

        val headerTitle = when {
            muted -> "Kommende skilt (dempet)"
            tripActive -> "Kommende skilt"
            else -> "Skilt-varsler"
        }

        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setStartHeaderAction(Action.APP_ICON)
                    .setTitle(headerTitle)
                    .build(),
            )
            .setSingleList(itemListBuilder.build())
            .setActionStrip(buildActionStrip(tripActive = tripActive, muted = muted))
            .build()
    }

    private fun emptyStateRow(tripActive: Boolean, muted: Boolean): Row {
        val title = when {
            !tripActive -> "Ingen aktiv kjøretur"
            muted -> "Varsler er dempet"
            else -> "Ingen skilt i horisonten"
        }
        val body = when {
            !tripActive -> "Trykk Start for å se skilt som kommer snart"
            muted -> "Slå på varsler for å få beskjed i bilen"
            else -> "Nye skilt dukker opp her mens du kjører"
        }
        return Row.Builder()
            .setTitle(title)
            .addText(body)
            .setImage(resourceIcon(R.drawable.ic_car_drive), Row.IMAGE_TYPE_ICON)
            .build()
    }

    private fun upcomingSignRow(sign: UpcomingSign): Row {
        val rowBuilder = Row.Builder()
            .setImage(signImageFor(sign), Row.IMAGE_TYPE_LARGE)
            .setTitle(titleWithDistance(sign.title, sign.metersAhead))

        val extraBody = AlertCopy.extraBody(sign.kind, sign.payload)
        if (extraBody.isNotBlank()) {
            rowBuilder.addText(extraBody)
        } else {
            rowBuilder.addText(plainDistanceLabel(sign.metersAhead))
        }
        return rowBuilder.build()
    }

    private fun signImageFor(sign: UpcomingSign): CarIcon {
        val bitmap = SignRenderer.bitmap(
            context = carContext,
            kind = sign.kind,
            payload = sign.payload,
            nvdbId = sign.nvdbId,
            sizePx = UPCOMING_SIGN_BITMAP_SIZE_PX,
        )
        if (bitmap != null) {
            return CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
        }
        return resourceIcon(AlertNotifier.iconRes(sign.kind))
    }

    private fun titleWithDistance(title: String, metersAhead: Int): CharSequence {
        val prefix = "  "
        val spannable = SpannableString(prefix + title)
        spannable.setSpan(
            DistanceSpan.create(carDistance(metersAhead)),
            0,
            1,
            Spanned.SPAN_INCLUSIVE_INCLUSIVE,
        )
        return spannable
    }

    private fun plainDistanceLabel(metersAhead: Int): String {
        val safeMeters = metersAhead.coerceAtLeast(0)
        return if (safeMeters >= 1000) {
            val kilometers = safeMeters / 1000.0
            String.format(java.util.Locale("nb", "NO"), "%.1f km frem", kilometers)
        } else {
            "$safeMeters m frem"
        }
    }

    private fun carDistance(metersAhead: Int): Distance {
        val safeMeters = metersAhead.coerceAtLeast(0)
        return if (safeMeters >= 1000) {
            Distance.create(safeMeters / 1000.0, Distance.UNIT_KILOMETERS_P1)
        } else {
            Distance.create(safeMeters.toDouble(), Distance.UNIT_METERS)
        }
    }

    private fun buildActionStrip(tripActive: Boolean, muted: Boolean): ActionStrip {
        // ListTemplate ActionStrip: max 2 actions, only one may have a title, no FLAG_PRIMARY.
        val driveAction = Action.Builder()
            .setTitle(if (tripActive) "Stopp" else "Start")
            .setIcon(resourceIcon(R.drawable.ic_car_drive))
            .setOnClickListener { toggleDriving() }
            .build()

        val muteAction = Action.Builder()
            .setIcon(
                resourceIcon(
                    if (muted) R.drawable.ic_car_unmute else R.drawable.ic_car_mute,
                ),
            )
            .setOnClickListener { toggleMute() }
            .build()

        return ActionStrip.Builder()
            .addAction(driveAction)
            .addAction(muteAction)
            .build()
    }

    private fun fallbackMessageTemplate(): Template {
        return androidx.car.app.model.MessageTemplate.Builder(
            "Kunne ikke vise listen. Varsler fungerer fortsatt over kartet.",
        )
            .setTitle("Skilt-varsler")
            .setHeaderAction(Action.APP_ICON)
            .build()
    }

    private fun resourceIcon(iconRes: Int): CarIcon {
        return CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build()
    }

    private fun toggleDriving() {
        try {
            if (LastAlertStore.trackingActive) {
                carContext.startService(
                    Intent(carContext, TrackingService::class.java)
                        .setAction(TrackingService.ACTION_STOP),
                )
            } else {
                ContextCompat.startForegroundService(
                    carContext,
                    Intent(carContext, TrackingService::class.java),
                )
            }
        } catch (error: Exception) {
            Log.e(CAR_LOG_TAG, "toggleDriving failed", error)
            DebugLog.append("CAR drive toggle failed: ${error.message}")
        }
        carContext.mainExecutor.execute { invalidate() }
    }

    private fun toggleMute() {
        val nextMuted = !LastAlertStore.alertsMuted
        LastAlertStore.setAlertsMuted(nextMuted)
        invalidate()
        scope.launch(Dispatchers.IO) {
            try {
                SettingsStore(carContext.applicationContext).setAlertsMuted(nextMuted)
            } catch (error: Exception) {
                DebugLog.append("CAR mute persist failed: ${error.message}")
            }
        }
    }
}
