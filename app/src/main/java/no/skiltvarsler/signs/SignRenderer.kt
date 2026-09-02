package no.skiltvarsler.signs

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.LruCache
import com.caverock.androidsvg.PreserveAspectRatio
import com.caverock.androidsvg.SVG
import no.skiltvarsler.matcher.Alert
import no.skiltvarsler.matcher.AlertKind
import no.skiltvarsler.matcher.SignAssetId
import java.io.ByteArrayInputStream
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SignRenderer {
    private const val ASSET_DIR = "trafikkskilt"
    private val cache = LruCache<String, Bitmap>(48)
    private val doctype = Regex("""<!DOCTYPE[^>]*>""", RegexOption.IGNORE_CASE)
    private val pageGroup = Regex(
        """<g id="#(?:ffffffff|d0d0d0ff|fbe7e5ff|fffdfdff|fffdfeff)">[\s\S]*?</g>""",
    )
    private val svgOpen = Regex("""<svg\b([^>]*)>""", RegexOption.IGNORE_CASE)
    private val viewBoxAttr = Regex("""viewBox\s*=\s*"([^"]+)"""", RegexOption.IGNORE_CASE)
    private val widthAttr = Regex("""\bwidth\s*=\s*"([0-9.]+)""", RegexOption.IGNORE_CASE)
    private val heightAttr = Regex("""\bheight\s*=\s*"([0-9.]+)""", RegexOption.IGNORE_CASE)

    fun bitmap(context: Context, alert: Alert, sizePx: Int): Bitmap? =
        bitmap(context, alert.kind, alert.payload, alert.nvdbId, sizePx)

    fun bitmap(
        context: Context,
        kind: AlertKind,
        payload: String,
        nvdbId: Long,
        sizePx: Int,
    ): Bitmap? {
        for (fileName in SignAssetId.candidates(kind, payload, nvdbId)) {
            val key = "$fileName:$sizePx"
            cache.get(key)?.let { return it }
            val rendered = renderAsset(context, fileName, sizePx) ?: continue
            cache.put(key, rendered)
            return rendered
        }
        return null
    }

    private fun renderAsset(context: Context, fileName: String, sizePx: Int): Bitmap? {
        val svgText = try {
            context.assets.open("$ASSET_DIR/$fileName").bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            return null
        }
        val svg = try {
            SVG.getFromInputStream(ByteArrayInputStream(prepareSvg(svgText).toByteArray()))
        } catch (_: Exception) {
            return null
        }
        val viewBox = svg.documentViewBox
        if (viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
            svg.setDocumentViewBox(viewBox.left, viewBox.top, viewBox.width(), viewBox.height())
        }
        svg.setDocumentPreserveAspectRatio(PreserveAspectRatio.LETTERBOX)
        svg.setDocumentWidth("100%")
        svg.setDocumentHeight("100%")
        val picture = svg.renderToPicture(sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawPicture(picture)
        punchPageColor(bitmap)
        return bitmap
    }

    private fun prepareSvg(raw: String): String {
        var text = doctype.replace(raw, "")
        text = pageGroup.replace(text, "")
        return svgOpen.replace(text) { match ->
            val attributes = match.groupValues[1]
            val viewBox = viewBoxAttr.find(attributes)?.groupValues?.get(1)
                ?: viewBoxFromWidthHeight(attributes)
            """<svg xmlns="http://www.w3.org/2000/svg" version="1.1" width="100%" height="100%" viewBox="$viewBox" fill-rule="evenodd">"""
        }
    }

    private fun viewBoxFromWidthHeight(attributes: String): String {
        val width = widthAttr.find(attributes)?.groupValues?.get(1) ?: "100"
        val height = heightAttr.find(attributes)?.groupValues?.get(1) ?: "100"
        return "0 0 $width $height"
    }

    private fun punchPageColor(bitmap: Bitmap) {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val visited = BooleanArray(pixels.size)
        val queue = ArrayDeque<Int>()

        fun enqueue(index: Int, seedRed: Int, seedGreen: Int, seedBlue: Int) {
            if (index < 0 || index >= pixels.size || visited[index]) return
            val color = pixels[index]
            if (Color.alpha(color) < 8) {
                visited[index] = true
                return
            }
            val red = Color.red(color)
            val green = Color.green(color)
            val blue = Color.blue(color)
            if (!isNear(red, green, blue, seedRed, seedGreen, seedBlue)) return
            visited[index] = true
            pixels[index] = Color.TRANSPARENT
            queue.add(index)
        }

        fun flood(x: Int, y: Int) {
            val index = y * width + x
            val color = pixels[index]
            if (Color.alpha(color) < 8 || !isPageColor(color)) return
            val seedRed = Color.red(color)
            val seedGreen = Color.green(color)
            val seedBlue = Color.blue(color)
            enqueue(index, seedRed, seedGreen, seedBlue)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val column = current % width
                val row = current / width
                if (column > 0) enqueue(current - 1, seedRed, seedGreen, seedBlue)
                if (column + 1 < width) enqueue(current + 1, seedRed, seedGreen, seedBlue)
                if (row > 0) enqueue(current - width, seedRed, seedGreen, seedBlue)
                if (row + 1 < height) enqueue(current + width, seedRed, seedGreen, seedBlue)
            }
        }

        flood(0, 0)
        flood(width - 1, 0)
        flood(0, height - 1)
        flood(width - 1, height - 1)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
    }

    private fun isPageColor(color: Int): Boolean {
        val red = Color.red(color)
        val green = Color.green(color)
        val blue = Color.blue(color)
        val luminance = (red + green + blue) / 3
        val saturation = max(red, max(green, blue)) - min(red, min(green, blue))
        return luminance >= 200 && saturation <= 40
    }

    private fun isNear(
        red: Int,
        green: Int,
        blue: Int,
        seedRed: Int,
        seedGreen: Int,
        seedBlue: Int,
    ): Boolean {
        return abs(red - seedRed) <= 28 &&
            abs(green - seedGreen) <= 28 &&
            abs(blue - seedBlue) <= 28
    }
}
