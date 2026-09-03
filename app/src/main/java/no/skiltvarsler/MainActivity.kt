package no.skiltvarsler

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import no.skiltvarsler.tracking.LastAlertStore
import no.skiltvarsler.tracking.ReplayRunner
import no.skiltvarsler.tracking.TrackingService
import no.skiltvarsler.ui.SkiltAppScreen
import no.skiltvarsler.ui.theme.SkiltTheme

class MainActivity : ComponentActivity() {
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            startTrackingService()
        } else {
            LastAlertStore.setTracking("Lokasjonstilgang trengs for sporing")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* Replay updates the UI even if notifications are denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SkiltTheme {
                SkiltAppScreen(
                    onStartTracking = ::startTracking,
                    onStopTracking = ::stopTracking,
                    onReplay = ::startReplay,
                    onEnsureNotifications = ::requestNotificationPermission,
                )
            }
        }
    }

    private fun startTracking() {
        val needed = buildList {
            add(Manifest.permission.ACCESS_FINE_LOCATION)
            add(Manifest.permission.ACCESS_COARSE_LOCATION)
            if (Build.VERSION.SDK_INT >= 33) add(Manifest.permission.POST_NOTIFICATIONS)
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            startTrackingService()
        } else {
            locationPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startReplay() {
        requestNotificationPermission()
        lifecycleScope.launch {
            ReplayRunner.run(applicationContext)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun startTrackingService() {
        ContextCompat.startForegroundService(this, Intent(this, TrackingService::class.java))
    }

    private fun stopTracking() {
        val intent = Intent(this, TrackingService::class.java).setAction(TrackingService.ACTION_STOP)
        startService(intent)
    }
}
