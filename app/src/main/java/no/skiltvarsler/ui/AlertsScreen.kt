package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.matcher.SignCatalog
import no.skiltvarsler.matcher.SignGroup

@Composable
fun AlertsScreen(
    settings: AlertSettings,
    onToggleSign: (id: String, enabled: Boolean) -> Unit,
    onToggleGroup: (SignGroup, enabled: Boolean) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Varsler", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Velg hvilke skilt som skal varsles under sporing. Valgene huskes.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        SignCatalog.groups.forEach { group ->
            GroupHeader(
                title = group.title,
                allOn = settings.groupAllOn(group),
                onToggleAll = { enabled -> onToggleGroup(group, enabled) },
            )
            group.signs.forEach { sign ->
                key(sign.id) {
                    ToggleRow(
                        label = sign.label,
                        checked = settings.isOn(sign.id),
                        kind = sign.kind,
                        payload = sign.payload,
                        nvdbId = sign.nvdbId,
                        onChange = { enabled -> onToggleSign(sign.id, enabled) },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
