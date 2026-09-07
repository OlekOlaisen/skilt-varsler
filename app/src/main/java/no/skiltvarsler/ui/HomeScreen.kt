package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.Alert

@Composable
fun HomeScreen(
    tripStatus: String,
    tripActive: Boolean,
    tileStatus: String,
    lastTitle: String,
    lastBody: String,
    lastAlert: Alert?,
    onToggleTrip: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Skilt-varsler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Varsel langs NVDB-nettet, i retningen du kjører. Posisjon forlater ikke telefonen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Button(
            onClick = onToggleTrip,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
        ) {
            Text(
                if (tripActive) "Stopp kjøretur" else "Start kjøretur",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        StatusCard(title = "Kjøretur", value = tripStatus, subtitle = "Posisjon forlater ikke telefonen")
        StatusCard(title = "Siste varsel", value = lastTitle, subtitle = lastBody, alert = lastAlert)
        StatusCard(
            title = "Kart",
            value = tileStatus,
            subtitle = "Hentes automatisk for kommunene rundt deg.",
        )
        Text(
            "Android Auto: slå på Ukjente kilder i Auto-utviklerinnstillinger, og pin Skilt-varsler under Tilpass startside.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.align(Alignment.Start),
        )
    }
}
