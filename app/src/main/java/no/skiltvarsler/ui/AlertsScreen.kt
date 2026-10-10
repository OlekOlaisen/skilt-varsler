package no.skiltvarsler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.AlertSettings
import no.skiltvarsler.matcher.SignCatalog
import no.skiltvarsler.matcher.SignGroup
import no.skiltvarsler.matcher.SignOption

private sealed class AlertsListItem {
    data class Header(val group: SignGroup) : AlertsListItem()
    data class Sign(val option: SignOption) : AlertsListItem()
}

@Composable
fun AlertsScreen(
    settings: AlertSettings,
    onToggleSign: (id: String, enabled: Boolean) -> Unit,
    onToggleGroup: (SignGroup, enabled: Boolean) -> Unit,
    onPriorityChange: (id: String, tier: Int) -> Unit,
) {
    val rows = remember {
        SignCatalog.groups.flatMap { group ->
            listOf(AlertsListItem.Header(group)) +
                group.signs.map { sign -> AlertsListItem.Sign(sign) }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "title") {
            Text(
                "Varsler",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
        item(key = "subtitle") {
            Text(
                "Velg hvilke skilt som varsler, og hvor viktige de er når flere kommer samtidig.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        items(
            items = rows,
            key = { row ->
                when (row) {
                    is AlertsListItem.Header -> "header:${row.group.title}"
                    is AlertsListItem.Sign -> row.option.id
                }
            },
        ) { row ->
            when (row) {
                is AlertsListItem.Header -> {
                    GroupHeader(
                        title = row.group.title,
                        allOn = settings.groupAllOn(row.group),
                        onToggleAll = { enabled -> onToggleGroup(row.group, enabled) },
                    )
                }
                is AlertsListItem.Sign -> {
                    val sign = row.option
                    ToggleRow(
                        label = sign.label,
                        checked = settings.isOn(sign.id),
                        kind = sign.kind,
                        payload = sign.payload,
                        nvdbId = sign.nvdbId,
                        priorityTier = settings.priorityTierForId(sign.id),
                        onChange = { enabled -> onToggleSign(sign.id, enabled) },
                        onPriorityChange = { tier -> onPriorityChange(sign.id, tier) },
                    )
                }
            }
        }
    }
}
