package no.skiltvarsler.tiles

import java.sql.Connection
import java.sql.DriverManager

object JdbcTileStore {
    init {
        Class.forName("org.sqlite.JDBC")
    }

    fun open(path: String): Connection {
        return DriverManager.getConnection("jdbc:sqlite:$path")
    }

    fun createSchema(connection: Connection) {
        connection.createStatement().use { statement ->
            statement.executeUpdate("PRAGMA journal_mode=WAL")
            for (raw in TileSchema.SQL.split(';')) {
                val sql = raw.trim()
                if (sql.isNotEmpty()) statement.executeUpdate(sql)
            }
        }
    }

    fun read(connection: Connection): RoadGraph {
        val builder = RoadGraphBuilder()
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT key, value FROM meta").use { rows ->
                while (rows.next()) {
                    when (rows.getString("key")) {
                        "tile_id" -> builder.tileId = rows.getString("value")
                        "version" -> builder.version = rows.getString("value")
                    }
                }
            }
            statement.executeQuery("SELECT id, lon, lat FROM nodes").use { rows ->
                while (rows.next()) {
                    builder.addNode(
                        RoadNode(
                            id = rows.getLong("id"),
                            position = LatLon(rows.getDouble("lat"), rows.getDouble("lon")),
                        ),
                    )
                }
            }
            statement.executeQuery("SELECT id, length_m FROM sequences").use { rows ->
                while (rows.next()) {
                    builder.setSequenceLength(rows.getLong("id"), rows.getDouble("length_m"))
                }
            }
            statement.executeQuery(
                """
                SELECT id, sequence_id, link_number, start_node_id, end_node_id,
                       start_pos, end_pos, length_m, type_veg, matchable, kommune, geometry
                FROM links
                """.trimIndent(),
            ).use { rows ->
                while (rows.next()) {
                    builder.addLink(
                        RoadLink(
                            id = rows.getLong("id"),
                            sequenceId = rows.getLong("sequence_id"),
                            linkNumber = rows.getInt("link_number"),
                            startNodeId = rows.getLong("start_node_id"),
                            endNodeId = rows.getLong("end_node_id"),
                            startPos = rows.getDouble("start_pos"),
                            endPos = rows.getDouble("end_pos"),
                            lengthMeters = rows.getDouble("length_m"),
                            typeVeg = rows.getString("type_veg"),
                            matchable = rows.getInt("matchable") != 0,
                            points = GeometryCodec.decode(rows.getBytes("geometry")),
                            kommune = rows.getInt("kommune"),
                        ),
                    )
                }
            }
            statement.executeQuery(
                "SELECT sequence_id, from_pos, to_pos, kmh, direction FROM link_speed",
            ).use { rows ->
                while (rows.next()) {
                    builder.addSpeed(
                        SpeedInterval(
                            sequenceId = rows.getLong("sequence_id"),
                            fromPos = rows.getDouble("from_pos"),
                            toPos = rows.getDouble("to_pos"),
                            kmh = rows.getInt("kmh"),
                            direction = TravelDirection.valueOf(rows.getString("direction")),
                        ),
                    )
                }
            }
            statement.executeQuery(
                "SELECT nvdb_id, type, sequence_id, from_pos, to_pos, direction, payload FROM objects",
            ).use { rows ->
                while (rows.next()) {
                    builder.addObject(
                        RoadObject(
                            nvdbId = rows.getLong("nvdb_id"),
                            type = RoadObjectType.fromWire(rows.getString("type")),
                            sequenceId = rows.getLong("sequence_id"),
                            fromPos = rows.getDouble("from_pos"),
                            toPos = rows.getDouble("to_pos"),
                            direction = TravelDirection.valueOf(rows.getString("direction")),
                            payload = rows.getString("payload"),
                        ),
                    )
                }
            }
            statement.executeQuery("SELECT kommune, name, ring FROM kommune_polygons").use { rows ->
                while (rows.next()) {
                    builder.addKommune(
                        KommunePolygon(
                            kommune = rows.getInt("kommune"),
                            name = rows.getString("name"),
                            ring = GeometryCodec.decode(rows.getBytes("ring")),
                        ),
                    )
                }
            }
        }
        return builder.build()
    }

    fun write(connection: Connection, graph: RoadGraph, extraMeta: Map<String, String> = emptyMap()) {
        createSchema(connection)
        connection.autoCommit = false
        try {
            connection.prepareStatement("INSERT INTO meta(key, value) VALUES(?, ?)").use { stmt ->
                fun put(key: String, value: String) {
                    stmt.setString(1, key)
                    stmt.setString(2, value)
                    stmt.addBatch()
                }
                put("tile_id", graph.tileId)
                put("version", graph.version)
                put("schema", TileSchema.VERSION.toString())
                extraMeta.forEach { (key, value) -> put(key, value) }
                stmt.executeBatch()
            }
            connection.prepareStatement("INSERT INTO nodes(id, lon, lat) VALUES(?, ?, ?)").use { stmt ->
                for (node in graph.nodes.values) {
                    stmt.setLong(1, node.id)
                    stmt.setDouble(2, node.position.longitude)
                    stmt.setDouble(3, node.position.latitude)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            connection.prepareStatement("INSERT INTO sequences(id, length_m) VALUES(?, ?)").use { stmt ->
                for (sequence in graph.sequences.values) {
                    stmt.setLong(1, sequence.id)
                    stmt.setDouble(2, sequence.lengthMeters)
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            connection.prepareStatement(
                """
                INSERT INTO links(
                  id, sequence_id, link_number, start_node_id, end_node_id,
                  start_pos, end_pos, length_m, type_veg, matchable, kommune, geometry
                ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (link in graph.links.values) {
                    stmt.setLong(1, link.id)
                    stmt.setLong(2, link.sequenceId)
                    stmt.setInt(3, link.linkNumber)
                    stmt.setLong(4, link.startNodeId)
                    stmt.setLong(5, link.endNodeId)
                    stmt.setDouble(6, link.startPos)
                    stmt.setDouble(7, link.endPos)
                    stmt.setDouble(8, link.lengthMeters)
                    stmt.setString(9, link.typeVeg)
                    stmt.setInt(10, if (link.matchable) 1 else 0)
                    stmt.setInt(11, link.kommune)
                    stmt.setBytes(12, GeometryCodec.encode(link.points))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            connection.prepareStatement(
                "INSERT INTO link_speed(sequence_id, from_pos, to_pos, kmh, direction) VALUES(?, ?, ?, ?, ?)",
            ).use { stmt ->
                for (sequence in graph.sequences.values) {
                    for (speed in graph.speedsOn(sequence.id)) {
                        stmt.setLong(1, speed.sequenceId)
                        stmt.setDouble(2, speed.fromPos)
                        stmt.setDouble(3, speed.toPos)
                        stmt.setInt(4, speed.kmh)
                        stmt.setString(5, speed.direction.name)
                        stmt.addBatch()
                    }
                }
                stmt.executeBatch()
            }
            connection.prepareStatement(
                """
                INSERT INTO objects(nvdb_id, type, sequence_id, from_pos, to_pos, direction, payload)
                VALUES(?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { stmt ->
                for (sequence in graph.sequences.values) {
                    for (obj in graph.objectsOn(sequence.id)) {
                        stmt.setLong(1, obj.nvdbId)
                        stmt.setString(2, obj.type.name)
                        stmt.setLong(3, obj.sequenceId)
                        stmt.setDouble(4, obj.fromPos)
                        stmt.setDouble(5, obj.toPos)
                        stmt.setString(6, obj.direction.name)
                        stmt.setString(7, obj.payload)
                        stmt.addBatch()
                    }
                }
                stmt.executeBatch()
            }
            connection.prepareStatement(
                "INSERT INTO kommune_polygons(kommune, name, ring) VALUES(?, ?, ?)",
            ).use { stmt ->
                for (polygon in graph.kommunePolygons) {
                    stmt.setInt(1, polygon.kommune)
                    stmt.setString(2, polygon.name)
                    stmt.setBytes(3, GeometryCodec.encode(polygon.ring))
                    stmt.addBatch()
                }
                stmt.executeBatch()
            }
            connection.commit()
        } catch (error: Exception) {
            connection.rollback()
            throw error
        } finally {
            connection.autoCommit = true
        }
    }
}
