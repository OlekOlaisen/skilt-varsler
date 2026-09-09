package no.skiltvarsler.situations

import no.skiltvarsler.matcher.SituationIndex
import no.skiltvarsler.matcher.SituationType
import no.skiltvarsler.matcher.TrafficSituation
import no.skiltvarsler.tiles.LatLon
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

object SituationsHolder {
    const val FILE_NAME = "situations.json"
    const val NEAR_RADIUS_METERS = 12_000.0

    private val all = AtomicReference<List<TrafficSituation>>(emptyList())
    @Volatile private var version: String = ""
    @Volatile private var source: String = ""

    fun version(): String = version

    fun source(): String = source

    fun all(): List<TrafficSituation> = all.get()

    fun near(latitude: Double, longitude: Double, radiusMeters: Double = NEAR_RADIUS_METERS): List<TrafficSituation> {
        return SituationIndex.near(latitude, longitude, all.get(), radiusMeters)
    }

    fun replace(situations: List<TrafficSituation>, version: String = "", source: String = "") {
        this.version = version
        this.source = source
        all.set(situations)
    }

    fun clear() {
        version = ""
        source = ""
        all.set(emptyList())
    }

    fun loadFile(file: File): Boolean {
        if (!file.exists() || file.length() < 8L) {
            return false
        }
        return try {
            loadJson(file.readText(Charsets.UTF_8))
            true
        } catch (_: Exception) {
            false
        }
    }

    fun loadJson(text: String) {
        val root = JSONObject(text)
        val version = root.optString("version", "")
        val source = root.optString("source", "")
        val array = root.optJSONArray("situations") ?: org.json.JSONArray()
        val parsed = ArrayList<TrafficSituation>(array.length())
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val type = SituationType.fromWire(item.optString("type", "roadwork")) ?: continue
            val pointsArray = item.optJSONArray("points") ?: continue
            val points = ArrayList<LatLon>(pointsArray.length())
            for (pointIndex in 0 until pointsArray.length()) {
                val pair = pointsArray.optJSONArray(pointIndex) ?: continue
                if (pair.length() < 2) {
                    continue
                }
                points.add(LatLon(pair.getDouble(0), pair.getDouble(1)))
            }
            if (points.isEmpty()) {
                continue
            }
            parsed.add(
                TrafficSituation(
                    id = item.optString("id", "situation-$index"),
                    type = type,
                    title = item.optString("title", type.defaultTitle),
                    description = item.optString("description", ""),
                    points = points,
                ),
            )
        }
        replace(parsed, version = version, source = source)
    }
}
