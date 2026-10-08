package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.legal.LegalCopy

@Composable
fun SettingsScreen(
    alertsMuted: Boolean,
    onAlertsMutedChange: (Boolean) -> Unit,
    combineAlerts: Boolean,
    onCombineAlertsChange: (Boolean) -> Unit,
    autoStartTracking: Boolean,
    onAutoStartTrackingChange: (Boolean) -> Unit,
    onOpenPrivacyPolicy: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Innstillinger",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        SettingsCard {
            SettingSwitch(
                title = "Start automatisk",
                subtitle = "Når appen åpnes",
                checked = autoStartTracking,
                onCheckedChange = onAutoStartTrackingChange,
            )
            SettingSwitch(
                title = "Vis varsler",
                subtitle = "På telefonen og i bilen",
                checked = !alertsMuted,
                onCheckedChange = { enabled -> onAlertsMutedChange(!enabled) },
            )
            SettingSwitch(
                title = "Kombiner varsler",
                subtitle = "Flere skilt samtidig i ett varsel",
                checked = combineAlerts,
                onCheckedChange = onCombineAlertsChange,
            )
        }
        Text(
            "I Android Auto fester du Skilt-varsler på startsiden.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text(
            LegalCopy.SHORT_DISCLAIMER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        TextButton(onClick = onOpenPrivacyPolicy, modifier = Modifier.fillMaxWidth()) {
            Text("Personvern")
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
