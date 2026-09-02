from pathlib import Path
import json

from skiltvarsler_pipeline.build import chunk_kommuner, merge_manifests, merge_release, plan_chunks


def test_chunk_kommuner_splits_evenly():
    chunks = chunk_kommuner([1, 2, 3, 4, 5], 2)
    assert chunks == [[1, 2], [3, 4], [5]]


def test_plan_chunks_uses_stable_ids():
    planned = plan_chunks([3216, 3220, 4601], 2)
    assert planned[0]["id"] == "00"
    assert planned[0]["kommuner"] == "3216,3220"
    assert planned[1]["kommuner"] == "4601"


def test_merge_manifests_replaces_same_id():
    merged = merge_manifests(
        {
            "version": "old",
            "tiles": [{"id": "kommune-3216", "version": "1", "file": "kommune-3216.sqlite"}],
        },
        {
            "version": "new",
            "tiles": [
                {"id": "kommune-3216", "version": "2", "file": "kommune-3216.sqlite"},
                {"id": "kommune-4601", "version": "2", "file": "kommune-4601.sqlite"},
            ],
        },
    )
    assert merged["version"] == "new"
    by_id = {tile["id"]: tile for tile in merged["tiles"]}
    assert by_id["kommune-3216"]["version"] == "2"
    assert "kommune-4601" in by_id


def test_merge_release_walks_nested_chunk_dirs(tmp_path: Path):
    existing = tmp_path / "existing"
    incoming = tmp_path / "incoming"
    output = tmp_path / "merged"
    existing.mkdir()
    chunk_a = incoming / "nvdb-tiles-00"
    chunk_b = incoming / "nvdb-tiles-01"
    chunk_a.mkdir(parents=True)
    chunk_b.mkdir(parents=True)
    (existing / "manifest.json").write_text(
        '{"version": "old", "tiles": [{"id": "kommune-3216", "file": "kommune-3216.sqlite"}]}',
        encoding="utf-8",
    )
    (chunk_a / "manifest.json").write_text(
        '{"version": "a", "tiles": [{"id": "kommune-3220", "file": "kommune-3220.sqlite"}]}',
        encoding="utf-8",
    )
    (chunk_b / "manifest.json").write_text(
        '{"version": "b", "tiles": [{"id": "kommune-4601", "file": "kommune-4601.sqlite"}]}',
        encoding="utf-8",
    )
    (chunk_a / "kommune-3220.sqlite").write_bytes(b"a")
    (chunk_b / "kommune-4601.sqlite").write_bytes(b"b")
    merge_release(existing, incoming, output)
    files = {path.name for path in output.iterdir()}
    assert files == {"manifest.json", "kommune-3220.sqlite", "kommune-4601.sqlite"}
    tiles = {tile["id"] for tile in json.loads((output / "manifest.json").read_text(encoding="utf-8"))["tiles"]}
    assert tiles == {"kommune-3216", "kommune-3220", "kommune-4601"}
