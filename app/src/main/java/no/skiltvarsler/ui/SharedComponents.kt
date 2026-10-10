package no.skiltvarsler.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.AlertPriority
import no.skiltvarsler.tilesource.TileCacheTag
import no.skiltvarsler.tilesource.TileInventoryItem

private val PriorityHighestRed = Color(0xFFD32F2F)
private val PriorityMediumOrange = Color(0xFFE65100)
private val PriorityLowGreen = Color(0xFF2E7D32)

private val TileActiveGreen = Color(0xFF3DDC84)
private val TileUpdatedOrange = Color(0xFFFF9F1A)
private val TileUpdatedOrangeInk = Color(0xFF1C1204)
private val TileNewCoral = Color(0xFFF85B60)
private val TileCachedGrey = Color(0xFF6B7380)
private val TileCachedGreyBg = Color(0xFF2A3038)

@Composable
fun LoadBar(
    fraction: Float?,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (fraction == null) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        } else {
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = StrokeCap.Round,
            )
        }
        if (label.isNotBlank()) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
fun TileInventoryCard(
    tiles: List<TileInventoryItem>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Kart",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            if (tiles.isEmpty()) {
                Text(
                    "Ingen fliser på telefonen ennå",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                )
            } else {
                tiles.forEach { tile ->
                    TileInventoryRow(tile)
                }
            }
        }
    }
}

@Composable
private fun TileInventoryRow(tile: TileInventoryItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                tile.name,
                style = MaterialTheme.typography.bodyLarge,
            )
            if (tile.active) {
                Text(
                    "Aktiv nå",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = TileActiveGreen,
                )
            }
        }
        TileTagChip(tag = tile.tag)
    }
}

@Composable
private fun TileTagChip(tag: TileCacheTag) {
    val background = when (tag) {
        TileCacheTag.NEW -> TileNewCoral
        TileCacheTag.UPDATED -> TileUpdatedOrange
        TileCacheTag.CACHED -> TileCachedGreyBg
    }
    val foreground = when (tag) {
        TileCacheTag.NEW -> Color.White
        TileCacheTag.UPDATED -> TileUpdatedOrangeInk
        TileCacheTag.CACHED -> TileCachedGrey
    }
    Text(
        text = tag.label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = foreground,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
fun StatusCard(
    title: String,
    value: String,
    subtitle: String = "",
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
                if (subtitle.isNotBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary,
                    )
                }
            }
        }
    }
}

@Composable
fun GroupHeader(
    title: String,
    allOn: Boolean,
    onToggleAll: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (allOn) "Alle på" else "Alle",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
            Spacer(Modifier.width(8.dp))
            Switch(checked = allOn, onCheckedChange = onToggleAll)
        }
    }
}

@Composable
fun ToggleRow(
    label: String,
    checked: Boolean,
    kind: AlertKind,
    payload: String = "",
    nvdbId: Long = 0,
    priorityTier: Int = AlertPriority.defaultTierFor(kind),
    onChange: (Boolean) -> Unit,
    onPriorityChange: ((Int) -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TrafficSignImage(
            kind = kind,
            payload = payload,
            nvdbId = nvdbId,
            size = 48.dp,
            contentDescription = label,
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            if (onPriorityChange != null) {
                PriorityTierDropdown(
                    tier = priorityTier,
                    onTierChange = onPriorityChange,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun PriorityTierDropdown(
    tier: Int,
    onTierChange: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = AlertPriority.coerce(tier)
    val label = AlertPriority.label(selected)
    val accent = priorityAccentColor(selected)
    Box {
        OutlinedButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, accent),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = accent,
            ),
        ) {
            Text(
                "Prioritet: $label",
                style = MaterialTheme.typography.labelLarge,
                color = accent,
            )
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Endre prioritet",
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            AlertPriority.ALL_TIERS.forEach { option ->
                val optionColor = priorityAccentColor(option)
                DropdownMenuItem(
                    text = {
                        Text(
                            AlertPriority.label(option),
                            color = optionColor,
                        )
                    },
                    onClick = {
                        onTierChange(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

private fun priorityAccentColor(tier: Int): Color {
    return when (AlertPriority.coerce(tier)) {
        AlertPriority.HIGHEST, AlertPriority.HIGH -> PriorityHighestRed
        AlertPriority.MEDIUM -> PriorityMediumOrange
        else -> PriorityLowGreen
    }
}
