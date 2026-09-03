package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.launch
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.prefetch.TilePrefetch
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tracking.AlertNotifier
import no.skiltvarsler.tracking.LastAlertStore
import no.skiltvarsler.tracking.TestAlerts

private enum class AppTab {
    Home,
    Alerts,
    Test,
    Settings,
}

@Composable
fun SkiltAppScreen(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onReplay: () -> Unit,
    onEnsureNotifications: () -> Unit = {},
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsState(initial = AlertSettings.ALL_ON)
    val tileUrl by store.tileBaseUrl.collectAsState(initial = SettingsStore.DEFAULT_TILE_BASE_URL)
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var tracking by remember { mutableStateOf(LastAlertStore.trackingStatus()) }
    var trackingActive by remember { mutableStateOf(LastAlertStore.trackingActive) }
    var tileStatus by remember { mutableStateOf(LastAlertStore.tileStatus()) }
    var lastAlert by remember { mutableStateOf(LastAlertStore.current()) }
    var lastTitle by remember { mutableStateOf(LastAlertStore.current()?.title ?: "Ingen varsel ennå") }
    var lastBody by remember { mutableStateOf(LastAlertStore.current()?.body ?: "Start sporing eller test et varsel") }
    var urlDraft by remember { mutableStateOf(tileUrl) }

    LaunchedEffect(tileUrl) {
        urlDraft = tileUrl
    }

    LaunchedEffect(Unit) {
        while (true) {
            tracking = LastAlertStore.trackingStatus()
            trackingActive = LastAlertStore.trackingActive
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
                    tracking = tracking,
                    trackingActive = trackingActive,
                    tileStatus = tileStatus,
                    lastTitle = lastTitle,
                    lastBody = lastBody,
                    lastAlert = lastAlert,
                    onToggleTracking = {
                        if (trackingActive) onStopTracking() else onStartTracking()
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
                AppTab.Test -> TestScreen(
                    onReplay = onReplay,
                    onTestSign = { sign ->
                        onEnsureNotifications()
                        AlertNotifier.publishAlert(context, TestAlerts.alertFor(sign))
                    },
                )
                AppTab.Settings -> SettingsScreen(
                    tileUrlDraft = urlDraft,
                    onTileUrlChange = { urlDraft = it },
                    onSaveTileUrl = {
                        scope.launch { store.setTileBaseUrl(urlDraft.trim()) }
                        TilePrefetch.enqueueNow(context)
                    },
                )
            }
        }
    }
}
