package no.skiltvarsler.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.model.Action
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Header
import androidx.car.app.model.ItemList
import androidx.car.app.model.ListTemplate
import androidx.car.app.model.Row
import androidx.car.app.model.Template
import androidx.car.app.validation.HostValidator
import androidx.core.graphics.drawable.IconCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import no.skiltvarsler.R
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.signs.SignRenderer
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import no.skiltvarsler.tracking.UpcomingSign

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

class StatusScreen(carContext: CarContext) : Screen(carContext) {
    private val store = SettingsStore(carContext)
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
        val muted = LastAlertStore.alertsMuted
        val upcoming = LastAlertStore.upcomingSigns()
        val rows = ItemList.Builder()
        if (upcoming.isEmpty()) {
            rows.addItem(
                Row.Builder()
                    .setTitle(emptyTitle())
                    .addText(emptySubtitle(muted))
                    .build(),
            )
        } else {
            upcoming.forEach { sign ->
                rows.addItem(rowFor(sign))
            }
        }
        val muteAction = Action.Builder()
            .setTitle(if (muted) "Slå på" else "Slå av")
            .setOnClickListener { toggleMute() }
            .build()
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(if (muted) "Varsler av" else "Skilt-varsler")
                    .setStartHeaderAction(Action.APP_ICON)
                    .addEndHeaderAction(muteAction)
                    .build(),
            )
            .setSingleList(rows.build())
            .build()
    }

    private fun rowFor(sign: UpcomingSign): Row {
        val builder = Row.Builder()
            .setTitle(sign.title)
            .addText(sign.distanceLabel)
        val bitmap = SignRenderer.bitmap(
            carContext,
            sign.kind,
            sign.payload,
            sign.nvdbId,
            128,
        )
        val icon = if (bitmap != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(bitmap)).build()
        } else {
            CarIcon.Builder(
                IconCompat.createWithResource(carContext, AlertNotifier.iconRes(sign.kind)),
            ).build()
        }
        builder.setImage(icon)
        return builder.build()
    }

    private fun emptyTitle(): String {
        if (!LastAlertStore.trackingActive) {
            return "Start sporing på telefonen"
        }
        val status = LastAlertStore.trackingStatus()
        if (status == "Ingen match" ||
            status.startsWith("Henter") ||
            status.startsWith("Kan ikke")
        ) {
            return status
        }
        return "Ingen skilt foran deg"
    }

    private fun emptySubtitle(muted: Boolean): String {
        return when {
            muted -> "Varsler er slått av"
            !LastAlertStore.trackingActive -> "Varsler kommer som heads-up over kartet"
            else -> "Neste skilt vises her underveis"
        }
    }

    private fun toggleMute() {
        val nextMuted = !LastAlertStore.alertsMuted
        LastAlertStore.setAlertsMuted(nextMuted)
        invalidate()
        scope.launch(Dispatchers.IO) {
            store.setAlertsMuted(nextMuted)
        }
    }
}
