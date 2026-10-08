package no.skiltvarsler.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import no.skiltvarsler.billing.SubscriptionRepository
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
    Settings,
}

private enum class OverlayScreen {
    None,
    Privacy,
    Paywall,
    CustomerCenter,
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
    val subscription by SubscriptionRepository.state.collectAsState()
    val lastTripSummary by TripRecorder.lastSummary.collectAsState()
    val pendingTripSummary by TripRecorder.pendingDisplay.collectAsState()
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(AppTab.Home) }
    var tripStatus by remember { mutableStateOf(LastAlertStore.trackingStatus()) }
    var tripActive by remember { mutableStateOf(LastAlertStore.trackingActive) }
    var tileInventory by remember { mutableStateOf(LastAlertStore.tileInventory()) }
    var tileLoad by remember { mutableStateOf(LastAlertStore.tileLoad()) }
    var lastAlert by remember { mutableStateOf(LastAlertStore.current()) }
    var lastTitle by remember { mutableStateOf(LastAlertStore.current()?.title ?: "Ingen varsel ennå") }
    var lastBody by remember { mutableStateOf(LastAlertStore.current()?.body ?: "Start kjøretur eller test et varsel") }
    var autoStartAttempted by remember { mutableStateOf(false) }
    var overlay by remember { mutableStateOf(OverlayScreen.None) }
    var showTestScreen by remember { mutableStateOf(false) }
    var startAfterPurchase by remember { mutableStateOf(false) }

    LaunchedEffect(subscription.isLoading, autoStartTracking) {
        if (autoStartAttempted || subscription.isLoading) {
            return@LaunchedEffect
        }
        autoStartAttempted = true
        val shouldAutoStart = store.autoStartTracking.first()
        if (shouldAutoStart &&
            subscription.hasAccess &&
            !LastAlertStore.trackingActive
        ) {
            onStartTracking()
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
            tileInventory = LastAlertStore.tileInventory()
            tileLoad = LastAlertStore.tileLoad()
            LastAlertStore.current()?.let {
                lastAlert = it
                lastTitle = it.title
                lastBody = it.body
            }
            delay(400)
        }
    }

    when (overlay) {
        OverlayScreen.Privacy -> {
            PrivacyPolicyScreen(onClose = { overlay = OverlayScreen.None })
            return
        }
        OverlayScreen.Paywall -> {
            PaywallScreen(
                onDismiss = {
                    startAfterPurchase = false
                    overlay = OverlayScreen.None
                },
                onAccessGranted = {
                    overlay = OverlayScreen.None
                    if (startAfterPurchase) {
                        startAfterPurchase = false
                        onStartTracking()
                    }
                },
            )
            return
        }
        OverlayScreen.CustomerCenter -> {
            CustomerCenterScreen(onDismiss = { overlay = OverlayScreen.None })
            return
        }
        OverlayScreen.None -> Unit
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = selectedTab == AppTab.Home && !showTestScreen,
                    onClick = {
                        showTestScreen = false
                        selectedTab = AppTab.Home
                    },
                    icon = { Icon(Icons.Outlined.Home, contentDescription = "Hjem") },
                    label = { Text("Hjem") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Alerts && !showTestScreen,
                    onClick = {
                        showTestScreen = false
                        selectedTab = AppTab.Alerts
                    },
                    icon = { Icon(Icons.Outlined.Notifications, contentDescription = "Varsler") },
                    label = { Text("Varsler") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Stats && !showTestScreen,
                    onClick = {
                        showTestScreen = false
                        selectedTab = AppTab.Stats
                    },
                    icon = { Icon(Icons.Outlined.BarChart, contentDescription = "Statistikk") },
                    label = { Text("Statistikk") },
                )
                NavigationBarItem(
                    selected = selectedTab == AppTab.Settings || showTestScreen,
                    onClick = {
                        showTestScreen = false
                        selectedTab = AppTab.Settings
                    },
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = "Innstillinger") },
                    label = { Text("Innstillinger") },
                )
            }
        },
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            if (showTestScreen) {
                TestScreen(
                    onClose = { showTestScreen = false },
                    onReplayE6 = onReplayE6,
                    onReplayOsloRing2 = onReplayOsloRing2,
                    onReplayE6Jessheim = onReplayE6Jessheim,
                    onTestSign = { sign ->
                        onEnsureNotifications()
                        AlertNotifier.publishAlert(context, TestAlerts.alertFor(sign))
                    },
                )
            } else {
                when (selectedTab) {
                    AppTab.Home -> HomeScreen(
                        tripStatus = tripStatus,
                        tripActive = tripActive,
                        tileInventory = tileInventory,
                        tileLoad = tileLoad,
                        lastTitle = lastTitle,
                        lastBody = lastBody,
                        lastAlert = lastAlert,
                        onToggleTrip = {
                            if (tripActive) {
                                onStopTracking()
                            } else if (subscription.hasAccess) {
                                onStartTracking()
                            } else {
                                startAfterPurchase = true
                                overlay = OverlayScreen.Paywall
                            }
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
                    AppTab.Settings -> SettingsScreen(
                        alertsMuted = settings.alertsMuted,
                        onAlertsMutedChange = { muted ->
                            scope.launch { store.setAlertsMuted(muted) }
                        },
                        combineAlerts = settings.combineAlerts,
                        onCombineAlertsChange = { enabled ->
                            scope.launch { store.setCombineAlerts(enabled) }
                        },
                        autoStartTracking = autoStartTracking,
                        onAutoStartTrackingChange = { enabled ->
                            scope.launch { store.setAutoStartTracking(enabled) }
                        },
                        subscription = subscription,
                        onOpenPaywall = {
                            startAfterPurchase = false
                            overlay = OverlayScreen.Paywall
                        },
                        onOpenCustomerCenter = {
                            overlay = OverlayScreen.CustomerCenter
                        },
                        onRestorePurchases = {
                            scope.launch {
                                val result = SubscriptionRepository.restore()
                                val message = when {
                                    result.isFailure -> result.exceptionOrNull()?.message
                                        ?: "Klarte ikke å gjenopprette"
                                    result.getOrNull()?.let { SubscriptionRepository.hasEntitlement(it) } == true ->
                                        "Kjøp gjenopprettet"
                                    else -> "Fant ingen tidligere kjøp"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onOpenPrivacyPolicy = { overlay = OverlayScreen.Privacy },
                        onOpenTest = {
                            selectedTab = AppTab.Settings
                            showTestScreen = true
                        },
                    )
                }
            }
        }
    }
}
