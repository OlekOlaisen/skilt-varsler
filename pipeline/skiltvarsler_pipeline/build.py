from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path

from .changelog import kommuner_of_object
from .coords import parse_wkt_polygon
from .model import KommunePolygon, RoadObject, TileGraph
from .nvdb import NvdbClient
from .objects import (
    collect_tunnels,
    drop_skilt_stop_yield_if_regulering_exists,
    enrich_tunnel_signs,
    ingest_fartsgrense,
    ingest_skiltplate,
    ingest_trafikkreguleringer,
    ingest_typed,
)
from .sanitize import sanitize
from .tile import write_tile
from .vegnett import ingest_sequences

OBJECT_TYPES = (162, 823, 45, 291, 100, 64, 770, 596, 67, 96, 856)
CHANGELOG_TYPES = (105, 162, 823, 45, 291, 100, 64, 67, 96, 856)


def bbox_of(graph: TileGraph) -> dict[str, float]:
    lons = [node.lon for node in graph.nodes.values()]
    lats = [node.lat for node in graph.nodes.values()]
    if not lons:
        return {"min_lon": 0.0, "min_lat": 0.0, "max_lon": 0.0, "max_lat": 0.0}
    return {
        "min_lon": min(lons),
        "min_lat": min(lats),
        "max_lon": max(lons),
        "max_lat": max(lats),
    }


def attach_kommune_polygon(graph: TileGraph, client: NvdbClient, kommune: int) -> None:
    item = client.kommune_with_extent(kommune)
    if not item:
        return
    kart = item.get("kartutsnitt") or {}
    wkt = (kart.get("wkt") if isinstance(kart, dict) else None) or ""
    srid = int((kart.get("srid") if isinstance(kart, dict) else 5973) or 5973)
    ring = parse_wkt_polygon(wkt, srid) if wkt else []
    if len(ring) < 3:
        return
    graph.kommune_polygons.append(
        KommunePolygon(kommune=kommune, name=str(item.get("navn") or kommune), ring=ring),
    )


def has_changelog_hits(client: NvdbClient, days: int) -> bool:
    for type_id in CHANGELOG_TYPES:
        try:
            for _ in client.iter_endringer(type_id, days=days):
                return True
        except Exception:
            return True
    return False


def kommuner_from_changelog(client: NvdbClient, days: int = 10) -> list[int]:
    found: set[int] = set()
    for type_id in CHANGELOG_TYPES:
        try:
            for obj in client.iter_endringer(type_id, days=days):
                found.update(kommuner_of_object(obj))
        except Exception as error:
            print(f"Changelog {type_id} feilet: {error}")
            continue
    return sorted(found)


def build_kommune(
    kommune: int,
    output_dir: Path,
    include_signs: bool = True,
    skip_if_unchanged: bool = False,
    changelog_days: int = 30,
) -> Path | None:
    version = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    graph = TileGraph(tile_id=f"kommune-{kommune}", version=version)
    with NvdbClient() as client:
        if skip_if_unchanged and not has_changelog_hits(client, changelog_days):
            print("Ingen NVDB-endringer i perioden — hopper over bygg")
            return None
        sequences = list(client.iter_veglenkesekvenser(kommune))
        ingest_sequences(graph, sequences)
        ingest_fartsgrense(graph, client.iter_vegobjekter(105, kommune))
        regulering_hits = 0
        tunnels: list[RoadObject] = []
        for type_id in OBJECT_TYPES:
            if type_id == 96 and not include_signs:
                continue
            objects = list(client.iter_vegobjekter(type_id, kommune))
            if type_id == 67:
                tunnels.extend(collect_tunnels(graph, objects))
            elif type_id == 96:
                ingest_skiltplate(graph, objects)
            elif type_id == 856:
                regulering_hits = ingest_trafikkreguleringer(graph, objects)
            else:
                ingest_typed(graph, type_id, objects)
        drop_skilt_stop_yield_if_regulering_exists(graph, regulering_hits > 0)
        enrich_tunnel_signs(graph, tunnels)
        attach_kommune_polygon(graph, client, kommune)
    sanitize(graph)
    output_dir.mkdir(parents=True, exist_ok=True)
    tile_path = output_dir / f"{graph.tile_id}.sqlite"
    bounds = bbox_of(graph)
    write_tile(
        tile_path,
        graph,
        extra_meta={
            "kommune": str(kommune),
            "extracted_at": version,
            **{key: str(value) for key, value in bounds.items()},
        },
    )
    upsert_manifest(
        output_dir,
        version=version,
        entry={
            "id": graph.tile_id,
            "version": version,
            "file": tile_path.name,
            "kommune": kommune,
            "warnings": graph.warnings,
            "links": len(graph.links),
            "objects": len(graph.objects),
            **bounds,
        },
    )
    return tile_path


def upsert_manifest(output_dir: Path, version: str, entry: dict) -> None:
    path = output_dir / "manifest.json"
    if path.exists():
        manifest = json.loads(path.read_text(encoding="utf-8"))
        tiles = [tile for tile in manifest.get("tiles", []) if tile.get("id") != entry["id"]]
    else:
        tiles = []
    tiles.append(entry)
    payload = {"version": version, "tiles": tiles}
    path.write_text(json.dumps(payload, indent=2), encoding="utf-8")


def merge_manifests(*manifests: dict) -> dict:
    by_id: dict[str, dict] = {}
    version = ""
    for manifest in manifests:
        if not manifest:
            continue
        version = str(manifest.get("version") or version)
        for tile in manifest.get("tiles") or []:
            tile_id = str(tile.get("id") or "")
            if tile_id:
                by_id[tile_id] = tile
    tiles = sorted(by_id.values(), key=lambda item: str(item.get("id")))
    return {"version": version or datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"), "tiles": tiles}


def merge_release(existing_dir: Path, incoming_dir: Path, output_dir: Path) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    manifests = [_read_manifest(existing_dir / "manifest.json")]
    for path in incoming_dir.rglob("manifest.json"):
        manifests.append(_read_manifest(path))
    merged = merge_manifests(*manifests)
    for sqlite in incoming_dir.rglob("*.sqlite"):
        target = output_dir / sqlite.name
        target.write_bytes(sqlite.read_bytes())
    manifest_path = output_dir / "manifest.json"
    manifest_path.write_text(json.dumps(merged, indent=2), encoding="utf-8")
    return manifest_path


def _read_manifest(path: Path) -> dict:
    if not path.exists():
        return {"version": "", "tiles": []}
    return json.loads(path.read_text(encoding="utf-8"))


def chunk_kommuner(numbers: list[int], size: int) -> list[list[int]]:
    if size <= 0:
        raise ValueError("chunk size must be positive")
    return [numbers[index : index + size] for index in range(0, len(numbers), size)]


def plan_chunks(numbers: list[int], size: int) -> list[dict[str, str]]:
    return [
        {"id": f"{index:02d}", "kommuner": ",".join(str(item) for item in chunk)}
        for index, chunk in enumerate(chunk_kommuner(numbers, size))
    ]


def norway_grid(cell_degrees: float = 0.18) -> list[dict[str, float]]:
    min_lon, min_lat = 4.0, 57.8
    max_lon, max_lat = 31.5, 71.4
    cells = []
    lat = min_lat
    row = 0
    while lat < max_lat:
        lon = min_lon
        col = 0
        while lon < max_lon:
            cells.append(
                {
                    "id": f"{row:03d}_{col:03d}",
                    "min_lon": lon,
                    "min_lat": lat,
                    "max_lon": lon + cell_degrees,
                    "max_lat": lat + cell_degrees,
                }
            )
            lon += cell_degrees
            col += 1
        lat += cell_degrees
        row += 1
    return cells
