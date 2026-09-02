from skiltvarsler_pipeline.model import RoadObject, TileGraph
from skiltvarsler_pipeline.objects import classify_regulering, classify_sign, drop_skilt_stop_yield_if_regulering_exists


def test_classifies_stop_yield_and_hazard():
    assert classify_sign("202") == "STOP"
    assert classify_sign("210") == "YIELD"
    assert classify_sign("146.1") == "HAZARD"
    assert classify_sign("362.80") is None


def test_regulering_preferred_over_skiltnummer():
    assert classify_regulering("Vikeplikt") == "YIELD"
    assert classify_regulering("Stopplikt") == "STOP"
    assert classify_regulering("Motortrafikk kun tillatt") is None
    graph = TileGraph(tile_id="t", version="1")
    graph.objects = [
        RoadObject(1, "YIELD", 10, 0.2, 0.2, "MED", "Vikeplikt"),
        RoadObject(2, "YIELD", 99, 0.1, 0.1, "MED", "210"),
        RoadObject(3, "HAZARD", 10, 0.4, 0.4, "MED", "146.1"),
    ]
    drop_skilt_stop_yield_if_regulering_exists(graph, True)
    types_payloads = {(obj.type, obj.payload) for obj in graph.objects}
    assert ("YIELD", "Vikeplikt") in types_payloads
    assert ("YIELD", "210") not in types_payloads
    assert ("HAZARD", "146.1") in types_payloads
