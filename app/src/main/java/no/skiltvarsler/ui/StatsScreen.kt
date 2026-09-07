package no.skiltvarsler.ui

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.tracking.TripSummary
import no.skiltvarsler.tracking.distanceLabel
import no.skiltvarsler.tracking.durationLabel
import no.skiltvarsler.tracking.tollKronerLabel

@Composable
fun StatsScreen(
    summary: TripSummary?,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            "Statistikk",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (summary == null) {
            Text(
                "Ingen kjøretur ennå. Start en tur eller kjør en replay for å se fotobokser, bompenger og mer.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else {
            Text(
                "Oppsummering av det appen varslet underveis. Bomtakst er estimert med Skyttelpass-rabatt.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatRow(label = "Varighet", value = summary.durationLabel())
                StatRow(label = "Distanse", value = summary.distanceLabel())
                StatRow(
                    label = "Fotobokser",
                    value = summary.speedCameras.toString(),
                    kind = AlertKind.SPEED_CAMERA,
                )
                StatRow(
                    label = "Streknings-ATK",
                    value = summary.sectionAtkStarts.toString(),
                    kind = AlertKind.SECTION_ATK_START,
                )
                StatRow(
                    label = "Bomstasjoner",
                    value = summary.tollCount.toString(),
                    kind = AlertKind.TOLL,
                )
                StatRow(
                    label = "Bompenger (est.)",
                    value = summary.tollKronerLabel(),
                    kind = AlertKind.TOLL,
                )
                if (summary.tolls.isNotEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "Bompasseringer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        summary.tolls.forEach { toll ->
                            val price = toll.kroner?.let {
                                String.format(java.util.Locale("nb", "NO"), "%.2f kr", it)
                            } ?: "ukjent takst"
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                TrafficSignImage(
                                    kind = AlertKind.TOLL,
                                    nvdbId = toll.nvdbId,
                                    size = 36.dp,
                                    contentDescription = toll.name,
                                )
                                Text(
                                    "${toll.name} · $price",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
                StatRow(
                    label = "Fartsgrenser",
                    value = summary.speedLimits.toString(),
                    kind = AlertKind.SPEED_LIMIT,
                    payload = "80",
                )
                StatRow(label = "Stopp", value = summary.stops.toString(), kind = AlertKind.STOP)
                StatRow(label = "Vikeplikt", value = summary.yields.toString(), kind = AlertKind.YIELD)
                StatRow(
                    label = "Fareskilt",
                    value = summary.hazards.toString(),
                    kind = AlertKind.HAZARD,
                    payload = "156",
                )
                StatRow(
                    label = "Viltfare",
                    value = summary.wildlife.toString(),
                    kind = AlertKind.WILDLIFE,
                    payload = "Elg",
                )
                StatRow(label = "Jernbane", value = summary.railways.toString(), kind = AlertKind.RAILWAY)
                StatRow(label = "Ferje", value = summary.ferries.toString(), kind = AlertKind.FERRY)
                StatRow(
                    label = "Kommunegrenser",
                    value = summary.municipalities.toString(),
                    kind = AlertKind.MUNICIPALITY,
                )
                StatRow(
                    label = "Prioritert veg",
                    value = summary.priorityRoads.toString(),
                    kind = AlertKind.PRIORITY_ROAD,
                )
                StatRow(label = "Varsler totalt", value = summary.totalAlerts.toString())
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    kind: AlertKind? = null,
    payload: String = "",
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (kind != null) {
                TrafficSignImage(
                    kind = kind,
                    payload = payload,
                    size = 40.dp,
                    contentDescription = label,
                )
                Spacer(modifier = Modifier.width(12.dp))
            } else {
                Spacer(modifier = Modifier.width(52.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
