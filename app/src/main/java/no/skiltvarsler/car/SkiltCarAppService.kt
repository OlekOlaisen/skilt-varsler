package no.skiltvarsler.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tracking.LastAlertStore

class SkiltCarAppService : CarAppService() {
    override fun createHostValidator(): HostValidator = HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(): Session {
        return object : Session() {
            override fun onCreateScreen(intent: Intent): Screen {
                return StatusScreen(carContext)
            }
        }
    }
}

/**
 * Minimal Android Auto surface. Avoid Header (API 7), ActionStrip, and custom bitmaps —
 * several hosts reject those and show only «uventet feil» / a black screen.
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
            buildStatusTemplate()
        } catch (error: Throwable) {
            DebugLog.append("CAR template failed: ${error.javaClass.simpleName}: ${error.message}")
            // No custom actions here — title-only MessageTemplate actions are rejected by some hosts.
            MessageTemplate.Builder("Åpne telefonen for innstillinger. Varsler vises over kartet.")
                .setTitle("Skilt-varsler")
                .setHeaderAction(Action.APP_ICON)
                .build()
        }
    }

    private fun buildStatusTemplate(): Template {
        val muted = LastAlertStore.alertsMuted
        val upcoming = LastAlertStore.upcomingSigns()
        val rows = ItemList.Builder()

        rows.addItem(
            Row.Builder()
                .setTitle(if (muted) "Slå på varsler" else "Slå av varsler")
                .addText(if (muted) "Varsler er slått av" else "Varsler er på")
                .setOnClickListener { toggleMute() }
                .build(),
        )

        if (upcoming.isEmpty()) {
            rows.addItem(
                Row.Builder()
                    .setTitle(emptyTitle())
                    .addText(emptySubtitle(muted))
                    .build(),
            )
        } else {
            upcoming.forEach { sign ->
                val row = Row.Builder().setTitle(sign.title)
                val distance = sign.distanceLabel
                if (distance.isNotBlank()) {
                    row.addText(distance)
                } else {
                    row.addText("Skilt foran deg")
                }
                rows.addItem(row.build())
            }
        }

        return ListTemplate.Builder()
            .setTitle(if (muted) "Varsler av" else "Skilt-varsler")
            .setHeaderAction(Action.APP_ICON)
            .setSingleList(rows.build())
            .build()
    }

    private fun emptyTitle(): String {
        if (!LastAlertStore.trackingActive) {
            return "Start kjøretur på telefonen"
        }
        val status = LastAlertStore.trackingStatus()
        if (status.startsWith("Henter") ||
            status.startsWith("Finner") ||
            status.startsWith("Starter") ||
            status.startsWith("Kan ikke") ||
            status.startsWith("Venter")
        ) {
            return status
        }
        return "Ingen skilt foran deg"
    }

    private fun emptySubtitle(muted: Boolean): String {
        return when {
            muted -> "Varsler er slått av"
            !LastAlertStore.trackingActive -> "Varsler vises over kartet"
            else -> "Neste skilt vises her underveis"
        }
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
