package no.skiltvarsler.car

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.car.app.notification.CarNotificationManager
import no.skiltvarsler.tracking.AlertNotifier

class CarMessageActionService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_REPLY, ACTION_MARK_AS_READ -> {
                CarNotificationManager.from(this).cancel(AlertNotifier.ALERT_NOTIFICATION_ID)
            }
        }
        stopSelf(startId)
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_REPLY = "no.skiltvarsler.car.ACTION_REPLY"
        const val ACTION_MARK_AS_READ = "no.skiltvarsler.car.ACTION_MARK_AS_READ"
        const val REMOTE_INPUT_RESULT_KEY = "skilt_car_reply_input"
    }
}
