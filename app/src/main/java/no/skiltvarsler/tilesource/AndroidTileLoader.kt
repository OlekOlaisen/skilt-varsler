package no.skiltvarsler.tilesource

import android.database.sqlite.SQLiteDatabase
import no.skiltvarsler.tiles.KommunePolygon
import no.skiltvarsler.tiles.GeometryCodec
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
    fun load(file: File): RoadGraph {
        val db = SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY)
        val builder = RoadGraphBuilder()
        db.rawQuery("SELECT key, value FROM meta", null).use { rows ->
            while (rows.moveToNext()) {
                when (rows.getString(0)) {
                    "tile_id" -> builder.tileId = rows.getString(1)
                    "version" -> builder.version = rows.getString(1)
                }
            }
        }
        db.rawQuery("SELECT id, lon, lat FROM nodes", null).use { rows ->
            while (rows.moveToNext()) {
                builder.addNode(
                    RoadNode(
                        id = rows.getLong(0),
                        position = LatLon(rows.getDouble(2), rows.getDouble(1)),
                    ),
                )
            }
        }
        db.rawQuery("SELECT id, length_m FROM sequences", null).use { rows ->
            while (rows.moveToNext()) {
                builder.setSequenceLength(rows.getLong(0), rows.getDouble(1))
            }
        }
        db.rawQuery(
            """
            SELECT id, sequence_id, link_number, start_node_id, end_node_id,
                   start_pos, end_pos, length_m, type_veg, matchable, kommune, geometry
            FROM links
            """.trimIndent(),
            null,
        ).use { rows ->
            while (rows.moveToNext()) {
                builder.addLink(
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
                        matchable = rows.getInt(9) != 0,
                        points = GeometryCodec.decode(rows.getBlob(11)),
                        kommune = rows.getInt(10),
                    ),
                )
            }
        }
        db.rawQuery("SELECT sequence_id, from_pos, to_pos, kmh, direction FROM link_speed", null).use { rows ->
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
            "SELECT nvdb_id, type, sequence_id, from_pos, to_pos, direction, payload FROM objects",
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
        db.close()
        return builder.build()
    }

    fun loadAll(files: List<File>): RoadGraph {
        val builder = RoadGraphBuilder()
        builder.tileId = files.map { it.nameWithoutExtension }.sorted().joinToString("+")
        builder.version = files.maxOfOrNull { it.lastModified() }?.toString() ?: "0"
        for (file in files) {
            val part = load(file)
            part.nodes.values.forEach(builder::addNode)
            part.links.values.forEach(builder::addLink)
            part.sequences.values.forEach { builder.setSequenceLength(it.id, it.lengthMeters) }
            part.sequences.values.forEach { sequence ->
                part.speedsOn(sequence.id).forEach(builder::addSpeed)
                part.objectsOn(sequence.id).forEach(builder::addObject)
            }
            part.kommunePolygons.forEach(builder::addKommune)
        }
        return builder.build()
    }
}
