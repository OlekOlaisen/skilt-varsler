package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.tracking.TileLoadProgress

@Composable
fun HomeScreen(
    tripStatus: String,
    tripActive: Boolean,
    tileStatus: String,
    tileLoad: TileLoadProgress?,
    lastTitle: String,
    lastBody: String,
    lastAlert: Alert?,
    onToggleTrip: () -> Unit,
) {
    val showBar = tileLoad != null || (tripActive && tripStatusLooksBusy(tripStatus))
    val fraction = tileLoad?.fraction
    val barLabel = when {
        tileLoad != null && tileLoad.label.isNotBlank() -> tileLoad.label
        tripStatusLooksBusy(tripStatus) -> tripStatus
        else -> ""
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Skilt-varsler",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (showBar) {
            LoadBar(fraction = fraction, label = barLabel)
        }
        Button(
            onClick = onToggleTrip,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 16.dp),
            colors = if (tripActive) {
                ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                )
            } else {
                ButtonDefaults.buttonColors()
            },
        ) {
            Text(
                if (tripActive) "Stopp kjøretur" else "Start kjøretur",
                style = MaterialTheme.typography.titleLarge,
            )
        }
        StatusCard(title = "Kjøretur", value = tripStatus)
        StatusCard(title = "Siste varsel", value = lastTitle, subtitle = lastBody, alert = lastAlert)
        StatusCard(title = "Kart", value = tileStatus)
    }
}

private fun tripStatusLooksBusy(status: String): Boolean {
    return status.startsWith("Starter") ||
        status.startsWith("Henter") ||
        status.startsWith("Venter") ||
        status.startsWith("Finner")
}
