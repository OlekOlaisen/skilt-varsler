package no.skiltvarsler.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Innstillinger", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Kart hentes automatisk når du kjører.",
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text("Kombiner varsler", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Samtidige skilt vises i samme heads-up (f.eks. «Forkjørsveg · Fartsgrense 30»), ikke etter hverandre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            Switch(
                checked = combineAlerts,
                onCheckedChange = onCombineAlertsChange,
            )
        }
        Text(
            "Pin Skilt-varsler under Tilpass startside i Android Auto. Varsler vises som heads-up mens du navigerer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Ansvar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            LegalCopy.SHORT_DISCLAIMER,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Text("Personvern", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            LegalCopy.SHORT_PRIVACY,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        OutlinedButton(
            onClick = onOpenPrivacyPolicy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Les personvernerklæring")
        }
        TextButton(
            onClick = {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(LegalCopy.PRIVACY_POLICY_URL))
                context.startActivity(intent)
            },
        ) {
            Text("Åpne personvernerklæring på nettet")
        }
    }
}
