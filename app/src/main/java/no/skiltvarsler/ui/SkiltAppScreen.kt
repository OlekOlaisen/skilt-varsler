package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.prefetch.TilePrefetch
import no.skiltvarsler.settings.SettingsStore
import no.skiltvarsler.tracking.LastAlertStore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkiltAppScreen(
    onStartTracking: () -> Unit,
    onStopTracking: () -> Unit,
    onReplay: () -> Unit,
) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val settings by store.settings.collectAsState(initial = AlertSettings.ALL_ON)
    val tileUrl by store.tileBaseUrl.collectAsState(initial = SettingsStore.DEFAULT_TILE_BASE_URL)
    val scope = rememberCoroutineScope()
    var tracking by remember { mutableStateOf(LastAlertStore.trackingStatus()) }
    var tileStatus by remember { mutableStateOf(LastAlertStore.tileStatus()) }
    var lastAlert by remember { mutableStateOf(LastAlertStore.current()) }
    var lastTitle by remember { mutableStateOf(LastAlertStore.current()?.title ?: "Ingen varsel ennå") }
    var lastBody by remember { mutableStateOf(LastAlertStore.current()?.body ?: "Trykk test for å spille av E6-replay") }
    var urlDraft by remember { mutableStateOf(tileUrl) }

    LaunchedEffect(tileUrl) {
        urlDraft = tileUrl
    }

    LaunchedEffect(Unit) {
        while (true) {
            tracking = LastAlertStore.trackingStatus()
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
        topBar = {
            TopAppBar(
                title = { Text("Skilt-varsler", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Varsel langs NVDB-nettet. Telefonen er innstillinger og status. I bilen er det heads-up over kartet du allerede bruker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            StatusCard(title = "Sporing", value = tracking, subtitle = "Posisjon forlater ikke telefonen")
            StatusCard(title = "Siste varsel", value = lastTitle, subtitle = lastBody, alert = lastAlert)
            StatusCard(title = "NVDB-fliser", value = tileStatus, subtitle = "Hentes automatisk for kommunen du er i. Telefonen treffer aldri NVDB.")
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStartTracking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Start")
                }
                OutlinedButton(onClick = onStopTracking, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Outlined.Stop, contentDescription = null)
                    Spacer(Modifier.padding(4.dp))
                    Text("Stopp")
                }
            }
            Button(
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Outlined.Science, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Test-replay (E6 fotoboks)")
            }
            OutlinedTextField(
                value = urlDraft,
                onValueChange = { urlDraft = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Flis-URL") },
                singleLine = true,
            )
            OutlinedButton(
                onClick = {
                    scope.launch { store.setTileBaseUrl(urlDraft.trim()) }
                    TilePrefetch.enqueueNow(context)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Hent fliser på nytt")
            }
            Text("Varsler", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            ToggleRow("Fotoboks", settings.speedCamera, AlertKind.SPEED_CAMERA) { scope.launch { store.setEnabled("speedCamera", it) } }
            ToggleRow("Fartsgrense", settings.speedLimit, AlertKind.SPEED_LIMIT, payload = "80", nvdbId = 80) {
                scope.launch { store.setEnabled("speedLimit", it) }
            }
            ToggleRow("Streknings-ATK", settings.sectionAtk, AlertKind.SECTION_ATK_START) { scope.launch { store.setEnabled("sectionAtk", it) } }
            ToggleRow("Bom", settings.toll, AlertKind.TOLL) { scope.launch { store.setEnabled("toll", it) } }
            ToggleRow("Viltfare", settings.wildlife, AlertKind.WILDLIFE, payload = "Elg") { scope.launch { store.setEnabled("wildlife", it) } }
            ToggleRow("Jernbane", settings.railway, AlertKind.RAILWAY) { scope.launch { store.setEnabled("railway", it) } }
            ToggleRow("Ferje", settings.ferry, AlertKind.FERRY) { scope.launch { store.setEnabled("ferry", it) } }
            ToggleRow("Stopp", settings.stop, AlertKind.STOP) { scope.launch { store.setEnabled("stop", it) } }
            ToggleRow("Vikeplikt", settings.yield, AlertKind.YIELD) { scope.launch { store.setEnabled("yield", it) } }
            ToggleRow("Fareskilt", settings.hazard, AlertKind.HAZARD) { scope.launch { store.setEnabled("hazard", it) } }
            ToggleRow("Forkjørsveg", settings.priorityRoad, AlertKind.PRIORITY_ROAD) { scope.launch { store.setEnabled("priorityRoad", it) } }
            ToggleRow("Kommunegrense", settings.municipality, AlertKind.MUNICIPALITY) { scope.launch { store.setEnabled("municipality", it) } }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun StatusCard(
    title: String,
    value: String,
    subtitle: String,
    alert: Alert? = null,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (alert != null) {
                TrafficSignImage(alert = alert, size = 72.dp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.secondary)
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    kind: AlertKind,
    payload: String = "",
    nvdbId: Long = 0,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TrafficSignImage(
                kind = kind,
                payload = payload,
                nvdbId = nvdbId,
                size = 48.dp,
                contentDescription = label,
            )
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.bodyLarge)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
