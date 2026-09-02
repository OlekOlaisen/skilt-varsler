from skiltvarsler_pipeline.model import RoadObject, SpeedInterval, TileGraph
from skiltvarsler_pipeline.sanitize import sanitize


def test_speed_overlap_keeps_newest():
    graph = TileGraph(tile_id="t", version="1")
    graph.sequence_lengths[1] = 100.0
    graph.speeds = [
        SpeedInterval(1, 0.0, 1.0, 80, "MED", startdato="2010-01-01"),
        SpeedInterval(1, 0.4, 0.7, 60, "MED", startdato="2020-01-01"),
    ]
    sanitize(graph)
    values = [(round(item.from_pos, 2), round(item.to_pos, 2), item.kmh) for item in graph.speeds]
    assert (0.0, 0.4, 80) in values
    assert (0.4, 0.7, 60) in values
    assert (0.7, 1.0, 80) in values
    assert graph.warnings >= 1


def test_drops_inverted_and_unknown_sequence():
    graph = TileGraph(tile_id="t", version="1")
    graph.sequence_lengths[1] = 100.0
    graph.speeds = [
        SpeedInterval(1, 0.9, 0.1, 50, "MED"),
        SpeedInterval(99, 0.0, 1.0, 80, "MED"),
        SpeedInterval(1, -2.0, 0.5, 40, "MED"),
    ]
    sanitize(graph)
    assert all(item.sequence_id == 1 for item in graph.speeds)
    assert all(item.from_pos <= item.to_pos for item in graph.speeds)


def test_merges_overlapping_wildlife():
    graph = TileGraph(tile_id="t", version="1")
    graph.sequence_lengths[1] = 100.0
    graph.objects = [
        RoadObject(1, "WILDLIFE", 1, 0.1, 0.4, "MED", "Elg"),
        RoadObject(2, "WILDLIFE", 1, 0.35, 0.6, "MED", "Elg"),
    ]
    sanitize(graph)
    wildlife = [obj for obj in graph.objects if obj.type == "WILDLIFE"]
    assert len(wildlife) == 1
    assert wildlife[0].from_pos == 0.1
    assert wildlife[0].to_pos == 0.6
