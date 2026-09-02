package no.skiltvarsler.tiles

object TileSchema {
    const val VERSION = 2

    const val SQL = """
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
CREATE INDEX idx_links_start_node ON links(start_node_id);
CREATE INDEX idx_links_end_node ON links(end_node_id);

CREATE TABLE link_speed (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  sequence_id INTEGER NOT NULL,
  from_pos REAL NOT NULL,
  to_pos REAL NOT NULL,
  kmh INTEGER NOT NULL,
  direction TEXT NOT NULL
);
CREATE INDEX idx_speed_seq ON link_speed(sequence_id);

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
CREATE INDEX idx_objects_seq ON objects(sequence_id);

CREATE TABLE kommune_polygons (
  kommune INTEGER NOT NULL,
  name TEXT NOT NULL,
  ring BLOB NOT NULL
);
"""
}
