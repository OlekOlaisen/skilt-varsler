package no.skiltvarsler.tilesource

import android.database.sqlite.SQLiteDatabase
import no.skiltvarsler.tiles.Geo
import no.skiltvarsler.tiles.GeometryCodec
import no.skiltvarsler.tiles.KommunePolygon
import no.skiltvarsler.tiles.LatLon
import no.skiltvarsler.tiles.RoadGraph
import no.skiltvarsler.tiles.RoadGraphBuilder
import no.skiltvarsler.tiles.RoadLink
import no.skiltvarsler.tiles.RoadNode
import no.skiltvarsler.tiles.RoadObject
import no.skiltvarsler.tiles.RoadObjectType
import no.skiltvarsler.tiles.SpeedInterval
import no.skiltvarsler.tiles.TravelDirection
import java.io.File

object AndroidTileLoader {
    const val DEFAULT_WINDOW_METERS = 3_500.0

    fun loadNear(
        files: List<File>,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double = DEFAULT_WINDOW_METERS,
    ): RoadGraph {
        val builder = RoadGraphBuilder()
        builder.tileId = files.map { it.nameWithoutExtension }.sorted().joinToString("+")
        builder.version = windowVersion(files, latitude, longitude, radiusMeters)
        for (file in files) {
            loadFileNear(file, builder, latitude, longitude, radiusMeters)
        }
        return builder.build()
    }

    fun isReadable(file: File): Boolean {
        if (!file.exists() || file.length() < 100L) {
            return false
        }
        return try {
            val database = SQLiteDatabase.openDatabase(
                file.path,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                database.rawQuery("SELECT 1 FROM meta LIMIT 1", null).use { rows ->
                    rows.moveToFirst()
                }
                true
            } finally {
                database.close()
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun windowVersion(
        files: List<File>,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): String {
        val stamp = files.maxOfOrNull { it.lastModified() } ?: 0L
        if (!radiusMeters.isFinite()) return stamp.toString()
        return "$stamp@${"%.3f".format(latitude)},${"%.3f".format(longitude)}"
    }

    private fun loadFileNear(
        file: File,
        builder: RoadGraphBuilder,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ) {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        try {
            db.rawQuery("SELECT key, value FROM meta", null).use { rows ->
                while (rows.moveToNext()) {
                    when (rows.getString(0)) {
                        "tile_id" -> if (builder.tileId == "unknown") builder.tileId = rows.getString(1)
                    }
                }
            }
            val nearbyNodeIds = nearbyNodeIds(db, latitude, longitude, radiusMeters)
            val kept = ArrayList<RoadLink>()
            if (nearbyNodeIds.isEmpty() && radiusMeters.isFinite()) {
                return
            }
            for (chunk in nearbyNodeIds.chunked(400).ifEmpty { listOf(emptyList()) }) {
                val clause = if (chunk.isEmpty() || !radiusMeters.isFinite()) {
                    "1=1"
                } else {
                    val list = chunk.joinToString(",")
                    "(start_node_id IN ($list) OR end_node_id IN ($list))"
                }
                db.rawQuery(
                    """
                    SELECT id, sequence_id, link_number, start_node_id, end_node_id,
                           start_pos, end_pos, length_m, type_veg, matchable, kommune, geometry
                    FROM links
                    WHERE matchable = 1 AND $clause
                    """.trimIndent(),
                    null,
                ).use { rows ->
                    while (rows.moveToNext()) {
                        val points = GeometryCodec.decode(rows.getBlob(11))
                        kept.add(
                            RoadLink(
                                id = rows.getLong(0),
                                sequenceId = rows.getLong(1),
                                linkNumber = rows.getInt(2),
                                startNodeId = rows.getLong(3),
                                endNodeId = rows.getLong(4),
                                startPos = rows.getDouble(5),
                                endPos = rows.getDouble(6),
                                lengthMeters = rows.getDouble(7),
                                typeVeg = rows.getString(8),
                                matchable = true,
                                points = points,
                                kommune = rows.getInt(10),
                            ),
                        )
                    }
                }
            }
            val sequenceIds = LinkedHashSet<Long>()
            for (link in kept) {
                sequenceIds.add(link.sequenceId)
                if (link.points.isNotEmpty()) {
                    builder.addNode(RoadNode(link.startNodeId, link.points.first()))
                    builder.addNode(RoadNode(link.endNodeId, link.points.last()))
                }
                builder.addLink(link)
            }
            if (sequenceIds.isNotEmpty()) {
                for (chunk in sequenceIds.chunked(400)) {
                    val list = chunk.joinToString(",")
                    db.rawQuery("SELECT id, length_m FROM sequences WHERE id IN ($list)", null).use { rows ->
                        while (rows.moveToNext()) {
                            builder.setSequenceLength(rows.getLong(0), rows.getDouble(1))
                        }
                    }
                    db.rawQuery(
                        "SELECT sequence_id, from_pos, to_pos, kmh, direction FROM link_speed WHERE sequence_id IN ($list)",
                        null,
                    ).use { rows ->
                        while (rows.moveToNext()) {
                            builder.addSpeed(
                                SpeedInterval(
                                    sequenceId = rows.getLong(0),
                                    fromPos = rows.getDouble(1),
                                    toPos = rows.getDouble(2),
                                    kmh = rows.getInt(3),
                                    direction = TravelDirection.valueOf(rows.getString(4)),
                                ),
                            )
                        }
                    }
                    db.rawQuery(
                        "SELECT nvdb_id, type, sequence_id, from_pos, to_pos, direction, payload FROM objects WHERE sequence_id IN ($list)",
                        null,
                    ).use { rows ->
                        while (rows.moveToNext()) {
                            builder.addObject(
                                RoadObject(
                                    nvdbId = rows.getLong(0),
                                    type = RoadObjectType.fromWire(rows.getString(1)),
                                    sequenceId = rows.getLong(2),
                                    fromPos = rows.getDouble(3),
                                    toPos = rows.getDouble(4),
                                    direction = TravelDirection.valueOf(rows.getString(5)),
                                    payload = rows.getString(6),
                                ),
                            )
                        }
                    }
                }
            }
            db.rawQuery("SELECT kommune, name, ring FROM kommune_polygons", null).use { rows ->
                while (rows.moveToNext()) {
                    builder.addKommune(
                        KommunePolygon(
                            kommune = rows.getInt(0),
                            name = rows.getString(1),
                            ring = GeometryCodec.decode(rows.getBlob(2) ?: GeometryCodec.encode(emptyList())),
                        ),
                    )
                }
            }
        } finally {
            db.close()
        }
    }

    private fun nearbyNodeIds(
        db: SQLiteDatabase,
        latitude: Double,
        longitude: Double,
        radiusMeters: Double,
    ): Set<Long> {
        if (!radiusMeters.isFinite()) {
            val all = HashSet<Long>()
            db.rawQuery("SELECT id FROM nodes", null).use { rows ->
                while (rows.moveToNext()) all.add(rows.getLong(0))
            }
            return all
        }
        val origin = LatLon(latitude, longitude)
        val south = Geo.offsetMeters(origin, northMeters = -radiusMeters, eastMeters = 0.0)
        val north = Geo.offsetMeters(origin, northMeters = radiusMeters, eastMeters = 0.0)
        val west = Geo.offsetMeters(origin, northMeters = 0.0, eastMeters = -radiusMeters)
        val east = Geo.offsetMeters(origin, northMeters = 0.0, eastMeters = radiusMeters)
        val ids = HashSet<Long>()
        db.rawQuery(
            "SELECT id FROM nodes WHERE lon BETWEEN ? AND ? AND lat BETWEEN ? AND ?",
            arrayOf(
                west.longitude.toString(),
                east.longitude.toString(),
                south.latitude.toString(),
                north.latitude.toString(),
            ),
        ).use { rows ->
            while (rows.moveToNext()) ids.add(rows.getLong(0))
        }
        return ids
    }
}
