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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
    onReplay: () -> Unit,
    onTestSign: (SignOption) -> Unit,
) {
    val context = LocalContext.current
    val items = remember { SignCatalog.all }
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
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("Test", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        }
        item {
            Text(
                "Sender ekte varsler på telefonen (og Auto hvis tilkoblet). Replay spiller av en E6-fotoboks.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            Button(onClick = onReplay, modifier = Modifier.fillMaxWidth()) {
                Text("Test-replay (E6 fotoboks)")
            }
        }
        item {
            Text(
                "Debug-logg",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        item {
            Text(
                if (logging) {
                    "Logger GPS, match og varsler. Stopp og del teksten med AI."
                } else {
                    "Start loggføring før en kjøretur. Del eller kopier loggen og send den til AI."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
        item {
            Button(
                onClick = { if (logging) DebugLog.stop() else DebugLog.start() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (logging) "Stopp loggføring" else "Start loggføring")
            }
        }
        item {
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
                            clipboard.setPrimaryClip(ClipData.newPlainText("Skilt-varsler debug-logg", text))
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
        }
        item {
            // Fixed-height preview so a growing log never stretches the Test tab.
            // The full log still accumulates in DebugLog / the shareable file.
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)),
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
        items(items, key = { sign -> sign.id }) { sign ->
            TestAlertRow(
                label = TestAlerts.labelFor(sign),
                sign = sign,
                onClick = { onTestSign(sign) },
            )
        }
    }
}

@Composable
private fun TestAlertRow(
    label: String,
    sign: SignOption,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
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
            modifier = Modifier.weight(1f),
        )
    }
}
