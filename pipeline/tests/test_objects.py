from datetime import date

from skiltvarsler_pipeline.model import RoadObject, TileGraph
from skiltvarsler_pipeline.objects import (
    classify_regulering,
    classify_sign,
    collect_tunnels,
    drop_skilt_stop_yield_if_regulering_exists,
    encode_payload,
    enrich_tunnel_signs,
    ingest_skiltplate,
    ingest_typed,
    payload_for_type,
)


def nvdb_object(
    object_id: int,
    egenskaper: dict[str, object],
    *,
    sequence_id: int = 1,
    from_pos: float = 0.2,
    to_pos: float | None = None,
    direction: str = "MED",
    metadata: dict | None = None,
) -> dict:
    if to_pos is None:
        sted = {
            "veglenkesekvensid": sequence_id,
            "relativPosisjon": from_pos,
            "retning": direction,
            "type": "punkt",
        }
    else:
        sted = {
            "veglenkesekvensid": sequence_id,
            "startposisjon": from_pos,
            "sluttposisjon": to_pos,
            "retning": direction,
        }
    return {
        "id": object_id,
        "egenskaper": [{"navn": name, "verdi": value} for name, value in egenskaper.items()],
        "lokasjon": {"stedfestinger": [sted]},
        "metadata": metadata or {},
    }


def graph_with_sequence(length_m: float = 400.0) -> TileGraph:
    graph = TileGraph(tile_id="t", version="1")
    graph.sequence_lengths[1] = length_m
    return graph


def test_classifies_stop_yield_and_hazard():
    assert classify_sign("204") == "STOP"
    assert classify_sign("202") == "YIELD"
    assert classify_sign("146.1") == "HAZARD"
    assert classify_sign("206") == "PRIORITY_ROAD"
    assert classify_sign("208") == "PRIORITY_ROAD"
    assert classify_sign("362.80") is None


def test_regulering_preferred_over_skiltnummer():
    assert classify_regulering("Vikeplikt") == "YIELD"
    assert classify_regulering("Stopplikt") == "STOP"
    assert classify_regulering("Motortrafikk kun tillatt") is None
    graph = TileGraph(tile_id="t", version="1")
    graph.objects = [
        RoadObject(1, "YIELD", 10, 0.2, 0.2, "MED", "Vikeplikt"),
        RoadObject(2, "YIELD", 99, 0.1, 0.1, "MED", "202"),
        RoadObject(3, "HAZARD", 10, 0.4, 0.4, "MED", "146.1"),
    ]
    drop_skilt_stop_yield_if_regulering_exists(graph, True)
    types_payloads = {(obj.type, obj.payload) for obj in graph.objects}
    assert ("YIELD", "Vikeplikt") in types_payloads
    assert ("YIELD", "202") not in types_payloads
    assert ("HAZARD", "146.1") in types_payloads


def test_toll_payload_has_name_and_price():
    payload = payload_for_type(
        45,
        "TOLL",
        nvdb_object(
            1,
            {
                "Navn bomstasjon": "Sørkedalsveien",
                "Takst liten bil (kr)": "42",
            },
        ),
    )
    assert payload == "792|Sørkedalsveien|42"


def test_skips_inactive_toll_and_expired_roadwork():
    graph = graph_with_sequence()
    ingest_typed(
        graph,
        45,
        [
            nvdb_object(1, {"Navn bomstasjon": "Aktiv", "Takst liten bil": 28}),
            nvdb_object(2, {"Navn bomstasjon": "Gammel", "Takst liten bil": 10, "Status": "Nedlagt"}),
        ],
    )
    assert [obj.nvdb_id for obj in graph.objects] == [1]
    ingest_skiltplate(
        graph,
        [
            nvdb_object(3, {"Skiltnummer": "110"}, metadata={"sluttdato": "2020-01-01"}),
            nvdb_object(4, {"Skiltnummer": "110"}, metadata={"sluttdato": "2099-01-01"}),
        ],
        today=date(2026, 9, 3),
    )
    hazards = [obj for obj in graph.objects if obj.type == "HAZARD"]
    assert [obj.nvdb_id for obj in hazards] == [4]


def test_ferry_and_section_atk_use_names():
    assert payload_for_type(64, "FERRY", nvdb_object(1, {"Navn": "Moss–Horten"})) == "775|Moss–Horten"
    assert payload_for_type(823, "SECTION_ATK", nvdb_object(1, {"Navn": "Lærdalstunnelen"})) == (
        "556.2|Lærdalstunnelen"
    )


def test_railway_without_barriers_uses_135():
    payload = payload_for_type(
        100,
        "RAILWAY",
        nvdb_object(1, {"Type": "I plan, uten lysregulering og bommer"}),
    )
    assert payload == "135"


def test_tunnel_sign_gets_name_and_length():
    graph = graph_with_sequence(400.0)
    ingest_skiltplate(graph, [nvdb_object(10, {"Skiltnummer": "122"}, from_pos=0.5)])
    tunnels = collect_tunnels(
        graph,
        [
            nvdb_object(
                99,
                {"Navn": "Lærdalstunnelen", "Lengde": 999},
                from_pos=0.75,
                to_pos=0.95,
            )
        ],
    )
    enrich_tunnel_signs(graph, tunnels)
    sign = graph.objects[0]
    assert sign.payload == "122|Lærdalstunnelen|80"


def test_encode_payload_drops_trailing_empties():
    assert encode_payload("775", "Moss") == "775|Moss"
    assert encode_payload("134") == "134"
