package no.skiltvarsler.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import no.skiltvarsler.log.DebugLog
import no.skiltvarsler.matcher.SignCatalog
import no.skiltvarsler.matcher.SignOption
import no.skiltvarsler.tracking.TestAlerts

private const val LOG_PREVIEW_LINE_COUNT = 40
private val LogPreviewHeight = 140.dp

@Composable
fun TestScreen(
    onClose: () -> Unit,
    onReplayE6: () -> Unit,
    onReplayOsloRing2: () -> Unit,
    onReplayE6Jessheim: () -> Unit,
    onTestSign: (SignOption) -> Unit,
) {
    val context = LocalContext.current
    val signOptions = remember { SignCatalog.all }
    val logging by DebugLog.enabled.collectAsState()
    val logLines by DebugLog.lines.collectAsState()
    val previewLines = remember(logLines) { logLines.takeLast(LOG_PREVIEW_LINE_COUNT) }
    val logScroll = rememberScrollState()

    LaunchedEffect(previewLines.size, previewLines.lastOrNull()) {
        if (previewLines.isNotEmpty()) {
            logScroll.scrollTo(logScroll.maxValue)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            TextButton(
                onClick = onClose,
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("Tilbake")
            }
            Text(
                "Test",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Sender ekte varsler.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        item {
            TestSectionCard(title = "Replay") {
                Button(onClick = onReplayE6, modifier = Modifier.fillMaxWidth()) {
                    Text("Replay E6 fotoboks")
                }
                OutlinedButton(onClick = onReplayOsloRing2, modifier = Modifier.fillMaxWidth()) {
                    Text("GPS-replay Oslo Ring 2")
                }
                OutlinedButton(onClick = onReplayE6Jessheim, modifier = Modifier.fillMaxWidth()) {
                    Text("GPS-replay E6 Jessheim")
                }
            }
        }

        item {
            TestSectionCard(title = "Tur-logg") {
                Text(
                    if (logging) {
                        "Loggen går. Stopp og del den etter turen."
                    } else {
                        "Start før en kjøretur, og del loggen etterpå."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Button(
                    onClick = { if (logging) DebugLog.stop() else DebugLog.start() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (logging) "Stopp loggføring" else "Start loggføring")
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            val share = DebugLog.shareIntent(context)
                            if (share == null) {
                                Toast.makeText(context, "Loggen er tom", Toast.LENGTH_SHORT).show()
                            } else {
                                context.startActivity(share)
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Del")
                    }
                    OutlinedButton(
                        onClick = {
                            val text = DebugLog.copyText()
                            if (text.isBlank()) {
                                Toast.makeText(context, "Loggen er tom", Toast.LENGTH_SHORT).show()
                            } else {
                                val clipboard = context.getSystemService(ClipboardManager::class.java)
                                clipboard.setPrimaryClip(ClipData.newPlainText("Skilt-varsler tur-logg", text))
                                Toast.makeText(context, "Logg kopiert", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Kopier")
                    }
                    OutlinedButton(
                        onClick = {
                            DebugLog.clear()
                            Toast.makeText(context, "Logg tømt", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Tøm")
                    }
                }
                Text(
                    if (logging) {
                        "Forhåndsvisning (full logg via Del/Kopier)"
                    } else {
                        "Forhåndsvisning"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(LogPreviewHeight)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (previewLines.isEmpty()) {
                        Text(
                            "Ingen logglinjer ennå",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(12.dp),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(logScroll)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            previewLines.forEach { line ->
                                Text(
                                    text = line,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        lineHeight = 14.sp,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Text(
                "Testvarsler",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                "Trykk for å sende et varsel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp),
            )
        }

        items(signOptions, key = { sign -> sign.id }) { sign ->
            TestAlertRow(
                label = TestAlerts.labelFor(sign),
                sign = sign,
                onClick = { onTestSign(sign) },
            )
        }
    }
}

@Composable
private fun TestSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            content()
        }
    }
}

@Composable
private fun TestAlertRow(
    label: String,
    sign: SignOption,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrafficSignImage(
                kind = sign.kind,
                payload = TestAlerts.payloadFor(sign),
                nvdbId = sign.nvdbId,
                size = 48.dp,
                contentDescription = label,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
        }
    }
}
