package no.skiltvarsler.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.signs.SignRenderer

@Composable
fun TrafficSignImage(
    alert: Alert,
    size: Dp = 72.dp,
    modifier: Modifier = Modifier,
) {
    TrafficSignImage(
        kind = alert.kind,
        payload = alert.payload,
        nvdbId = alert.nvdbId,
        size = size,
        modifier = modifier,
        contentDescription = alert.title,
    )
}

@Composable
fun TrafficSignImage(
    kind: AlertKind,
    payload: String = "",
    nvdbId: Long = 0,
    size: Dp = 40.dp,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap = remember(kind, payload, nvdbId, sizePx) {
        SignRenderer.bitmap(context, kind, payload, nvdbId, sizePx)
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.size(size),
            contentScale = ContentScale.Fit,
        )
    }
}
