package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    alertsMuted: Boolean,
    onAlertsMutedChange: (Boolean) -> Unit,
    autoStartTracking: Boolean,
    onAutoStartTrackingChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Innstillinger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Kart hentes automatisk fra GitHub når du kjører.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Kjøretur", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Start automatisk", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Starter kjøretur når appen åpnes, hvis lokasjonstilgang er gitt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(
                checked = autoStartTracking,
                onCheckedChange = onAutoStartTrackingChange,
            )
        }
        Text("Android Auto", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Varsler", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Slår av heads-up i bilen og på telefonen. Listen over kommende skilt vises fortsatt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(
                checked = !alertsMuted,
                onCheckedChange = { enabled -> onAlertsMutedChange(!enabled) },
            )
        }
        Text(
            "Appen er sideloadet. I Android Auto-innstillinger på telefonen: slå på utviklerinnstillinger, tillat ukjente kilder, koble til bilen, og pin Skilt-varsler under Tilpass startside.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Personvern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Posisjon brukes bare på telefonen til å treffe vegnettet. Appen har ikke konto eller analyse. Telefonen treffer aldri NVDB.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}
