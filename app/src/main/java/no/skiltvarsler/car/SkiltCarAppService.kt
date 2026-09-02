package no.skiltvarsler.car

import android.content.Intent
import androidx.car.app.CarAppService
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
import no.skiltvarsler.R
import no.skiltvarsler.signs.SignRenderer
import no.skiltvarsler.tracking.AlertNotifier
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

class StatusScreen(carContext: androidx.car.app.CarContext) : Screen(carContext) {
    override fun onGetTemplate(): Template {
        val alert = LastAlertStore.current()
        val sign = alert?.let { SignRenderer.bitmap(carContext, it, 256) }
        val icon = if (sign != null) {
            CarIcon.Builder(IconCompat.createWithBitmap(sign)).build()
        } else {
            val iconRes = alert?.let { AlertNotifier.iconRes(it.kind) } ?: R.drawable.ic_alert_camera
            CarIcon.Builder(IconCompat.createWithResource(carContext, iconRes)).build()
        }
        val rows = ItemList.Builder()
            .addItem(
                Row.Builder()
                    .setTitle(LastAlertStore.trackingStatus())
                    .addText("Posisjon forlater ikke telefonen")
                    .build(),
            )
        if (alert != null) {
            rows.addItem(
                Row.Builder()
                    .setTitle(alert.title)
                    .addText(alert.body)
                    .setImage(icon)
                    .build(),
            )
        }
        return ListTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle("Skilt-varsler")
                    .setStartHeaderAction(Action.APP_ICON)
                    .build(),
            )
            .setSingleList(rows.build())
            .build()
    }
}
