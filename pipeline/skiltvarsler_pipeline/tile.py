from __future__ import annotations

import sqlite3
import struct
from pathlib import Path

from .model import TileGraph

SCHEMA = """
CREATE TABLE meta (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);
CREATE TABLE nodes (
  id INTEGER PRIMARY KEY,
  lon REAL NOT NULL,
  lat REAL NOT NULL
);
CREATE TABLE sequences (
  id INTEGER PRIMARY KEY,
  length_m REAL NOT NULL
);
CREATE TABLE links (
  id INTEGER PRIMARY KEY,
  sequence_id INTEGER NOT NULL,
  link_number INTEGER NOT NULL,
  start_node_id INTEGER NOT NULL,
  end_node_id INTEGER NOT NULL,
  start_pos REAL NOT NULL,
  end_pos REAL NOT NULL,
  length_m REAL NOT NULL,
  type_veg TEXT NOT NULL,
  matchable INTEGER NOT NULL,
  kommune INTEGER NOT NULL DEFAULT 0,
  geometry BLOB NOT NULL
);
CREATE INDEX idx_links_seq ON links(sequence_id);
CREATE TABLE link_speed (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sequence_id INTEGER NOT NULL,
  from_pos REAL NOT NULL,
  to_pos REAL NOT NULL,
  kmh INTEGER NOT NULL,
  direction TEXT NOT NULL
);
CREATE TABLE objects (
  nvdb_id INTEGER NOT NULL,
  type TEXT NOT NULL,
  sequence_id INTEGER NOT NULL,
  from_pos REAL NOT NULL,
  to_pos REAL NOT NULL,
  direction TEXT NOT NULL,
  payload TEXT NOT NULL,
  PRIMARY KEY (nvdb_id, type, sequence_id, from_pos)
);
CREATE TABLE kommune_polygons (
  kommune INTEGER NOT NULL,
  name TEXT NOT NULL,
  ring BLOB NOT NULL
);
"""


def encode_points(points: list[tuple[float, float]]) -> bytes:
    packed = [struct.pack("<I", len(points))]
    for lon, lat in points:
        packed.append(struct.pack("<ff", float(lon), float(lat)))
    return b"".join(packed)


def write_tile(path: Path, graph: TileGraph, extra_meta: dict[str, str] | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists():
        path.unlink()
    connection = sqlite3.connect(path)
    try:
        connection.executescript(SCHEMA)
        meta = {
            "tile_id": graph.tile_id,
            "version": graph.version,
            "schema": "2",
            "pipeline_warnings": str(graph.warnings),
        }
        if extra_meta:
            meta.update(extra_meta)
        connection.executemany("INSERT INTO meta(key, value) VALUES(?, ?)", list(meta.items()))
        connection.executemany(
            "INSERT INTO nodes(id, lon, lat) VALUES(?, ?, ?)",
            [(node.id, node.lon, node.lat) for node in graph.nodes.values()],
        )
        connection.executemany(
            "INSERT INTO sequences(id, length_m) VALUES(?, ?)",
            list(graph.sequence_lengths.items()),
        )
        connection.executemany(
            """
            INSERT INTO links(
              id, sequence_id, link_number, start_node_id, end_node_id,
              start_pos, end_pos, length_m, type_veg, matchable, kommune, geometry
            ) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (
                    link.id,
                    link.sequence_id,
                    link.link_number,
                    link.start_node_id,
                    link.end_node_id,
                    link.start_pos,
                    link.end_pos,
                    link.length_m,
                    link.type_veg,
                    1 if link.matchable else 0,
                    link.kommune,
                    encode_points(link.points),
                )
                for link in graph.links
            ],
        )
        connection.executemany(
            "INSERT INTO link_speed(sequence_id, from_pos, to_pos, kmh, direction) VALUES(?, ?, ?, ?, ?)",
            [
                (item.sequence_id, item.from_pos, item.to_pos, item.kmh, item.direction)
                for item in graph.speeds
            ],
        )
        connection.executemany(
            """
            INSERT INTO objects(nvdb_id, type, sequence_id, from_pos, to_pos, direction, payload)
            VALUES(?, ?, ?, ?, ?, ?, ?)
            """,
            [
                (obj.nvdb_id, obj.type, obj.sequence_id, obj.from_pos, obj.to_pos, obj.direction, obj.payload)
                for obj in graph.objects
            ],
        )
        connection.executemany(
            "INSERT INTO kommune_polygons(kommune, name, ring) VALUES(?, ?, ?)",
            [
                (poly.kommune, poly.name, encode_points(poly.ring))
                for poly in graph.kommune_polygons
            ],
        )
        connection.commit()
    finally:
        connection.close()
