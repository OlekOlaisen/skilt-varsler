package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import no.skiltvarsler.tracking.TestAlerts
import no.skiltvarsler.tracking.TripRecorder

private enum class AppTab {
    Home,
    Alerts,
    Stats,
    Test,
    Settings,
}

@Composable
fun SkiltAppScreen(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onReplayE6: () -> Unit,
    onReplayOsloRing2: () -> Unit,
    onReplayE6Jessheim: () -> Unit,
    onEnsureNotifications: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsState(initial = AlertSettings.ALL_ON)
    val autoStartTracking by store.autoStartTracking.collectAsState(initial = true)
    val lastTripSummary by TripRecorder.lastSummary.collectAsState()
    val pendingTripSummary by TripRecorder.pendingDisplay.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var tripStatus by remember { mutableStateOf(LastAlertStore.trackingStatus()) }
    var tripActive by remember { mutableStateOf(LastAlertStore.trackingActive) }
    var tileStatus by remember { mutableStateOf(LastAlertStore.tileStatus()) }
    var lastAlert by remember { mutableStateOf(LastAlertStore.current()) }
    var lastTitle by remember { mutableStateOf(LastAlertStore.current()?.title ?: "Ingen varsel ennå") }
    var lastBody by remember { mutableStateOf(LastAlertStore.current()?.body ?: "Start kjøretur eller test et varsel") }
    var autoStartAttempted by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!autoStartAttempted) {
            autoStartAttempted = true
            val shouldAutoStart = store.autoStartTracking.first()
            if (shouldAutoStart && !LastAlertStore.trackingActive) {
                onStartTracking()
            }
        }
    }

    LaunchedEffect(pendingTripSummary, lastTripSummary) {
        if (pendingTripSummary && lastTripSummary != null) {
            selectedTab = AppTab.Stats
            TripRecorder.markSummarySeen()
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            tripStatus = LastAlertStore.trackingStatus()
            tripActive = LastAlertStore.trackingActive
            tileStatus = LastAlertStore.tileStatus()
            LastAlertStore.current()?.let {
                lastAlert = it
                lastTitle = it.title
                lastBody = it.body
            }
            delay(400)
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Home,
                    onClick = { selectedTab = AppTab.Home },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Hjem") },
                    label = { Text("Hjem") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Alerts,
                    onClick = { selectedTab = AppTab.Alerts },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = "Varsler") },
                    label = { Text("Varsler") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Stats,
                    onClick = { selectedTab = AppTab.Stats },
                    icon = { Icon(Icons.Outlined.BarChart, contentDescription = "Statistikk") },
                    label = { Text("Statistikk") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Test,
                    onClick = { selectedTab = AppTab.Test },
                    icon = { Icon(Icons.Outlined.Science, contentDescription = "Test") },
                    label = { Text("Test") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings,
                    onClick = { selectedTab = AppTab.Settings },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Innstillinger") },
                    label = { Text("Innstillinger") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (selectedTab) {
                AppTab.Home -> HomeScreen(
                    tripStatus = tripStatus,
                    tripActive = tripActive,
                    tileStatus = tileStatus,
                    lastTitle = lastTitle,
                    lastBody = lastBody,
                    lastAlert = lastAlert,
                    onToggleTrip = {
                        if (tripActive) onStopTracking() else onStartTracking()
                    },
                )
                AppTab.Alerts -> AlertsScreen(
                    settings = settings,
                    onToggleSign = { id, enabled ->
                        scope.launch { store.setSignEnabled(id, enabled) }
                    },
                    onToggleGroup = { group, enabled ->
                        scope.launch { store.setGroupEnabled(group, enabled) }
                    },
                )
                AppTab.Stats -> StatsScreen(summary = lastTripSummary)
                AppTab.Test -> TestScreen(
                    onReplayE6 = onReplayE6,
                    onReplayOsloRing2 = onReplayOsloRing2,
                    onReplayE6Jessheim = onReplayE6Jessheim,
                    onTestSign = { sign ->
                        onEnsureNotifications()
                        AlertNotifier.publishAlert(context, TestAlerts.alertFor(sign))
                    },
                )
                AppTab.Settings -> SettingsScreen(
                    alertsMuted = settings.alertsMuted,
                    onAlertsMutedChange = { muted ->
                        scope.launch { store.setAlertsMuted(muted) }
                    },
                    autoStartTracking = autoStartTracking,
                    onAutoStartTrackingChange = { enabled ->
                        scope.launch { store.setAutoStartTracking(enabled) }
                    },
                )
            }
        }
    }
}
