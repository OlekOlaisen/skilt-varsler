from datetime import date

from skiltvarsler_pipeline.model import RoadObject, TileGraph
from skiltvarsler_pipeline.objects import (
    classify_regulering,
    classify_sign,
    collect_tunnels,
    dedupe_paired_speed_plates,
    direction_from_ansiktsside,
    encode_payload,
    enrich_tunnel_signs,
    ingest_skiltplate,
    ingest_typed,
    payload_for_type,
    prefer_skiltplate_over_regulering_for_stop_yield,
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
    assert classify_sign("362.80") == "SPEED_LIMIT"
    assert classify_sign("362.40 - Fartsgrense 40 km/t") == "SPEED_LIMIT"
    assert classify_sign("362.50") == "SPEED_LIMIT"
    assert classify_sign("362") is None
    assert classify_sign("108 - Ujevn veg") == "HAZARD"
    assert classify_sign("110 - Vegarbeid") == "HAZARD"
    assert classify_sign("122 - Tunnel.") == "HAZARD"
    assert classify_sign("204 - Stopp") == "STOP"
    assert classify_sign("146.1 - Elg") == "HAZARD"
    assert classify_sign("808.105 - Fartsdempere/Fartsdemparar") == "HAZARD"
    assert classify_sign("808.111 - Is-svuller") is None
    assert classify_sign("406") == "HAZARD"
    assert classify_sign("406 - Påbudt kjøring i rundkjøring") == "HAZARD"
    assert classify_sign("126") == "HAZARD"


def test_full_nvdb_verdi_ingests_as_canonical_payload():
    graph = graph_with_sequence()
    ingest_skiltplate(
        graph,
        [
            nvdb_object(1, {"Skiltnummer": "108 - Ujevn veg"}),
            nvdb_object(2, {"Skiltnummer": "808.105 - Fartsdempere/Fartsdemparar"}),
            nvdb_object(3, {"Skiltnummer": "122 - Tunnel."}, from_pos=0.5),
            nvdb_object(4, {"Skiltnummer": "406 - Påbudt kjøring i rundkjøring"}, from_pos=0.6),
            nvdb_object(5, {"Skiltnummer": "126"}, from_pos=0.7),
            nvdb_object(6, {"Skiltnummer": "362.40 - Fartsgrense 40 km/t"}, from_pos=0.8),
            nvdb_object(7, {"Skiltnummer": "362.50"}, from_pos=0.9),
        ],
    )
    by_id = {obj.nvdb_id: obj for obj in graph.objects}
    assert by_id[1].type == "HAZARD" and by_id[1].payload == "108"
    assert by_id[2].type == "HAZARD" and by_id[2].payload == "109"
    assert by_id[3].type == "HAZARD" and by_id[3].payload == "122"
    assert by_id[6].type == "SPEED_LIMIT" and by_id[6].payload == "40"
    assert by_id[7].type == "SPEED_LIMIT" and by_id[7].payload == "50"
    assert by_id[4].type == "HAZARD" and by_id[4].payload == "406"
    assert by_id[5].type == "HAZARD" and by_id[5].payload == "406"


def test_skiltplate_wins_over_regulering_on_same_sequence():
    assert classify_regulering("Vikeplikt") == "YIELD"
    assert classify_regulering("Stopplikt") == "STOP"
    assert classify_regulering("tidligere stopplikt, nå vikeplikt") == "YIELD"
    assert classify_regulering("Motortrafikk kun tillatt") is None
    graph = TileGraph(tile_id="t", version="1")
    graph.objects = [
        RoadObject(1, "STOP", 10, 0.2, 0.2, "MED", "Stopplikt"),
        RoadObject(2, "YIELD", 10, 0.2, 0.2, "MED", "202"),
        RoadObject(3, "STOP", 99, 0.1, 0.1, "MED", "Stopplikt"),
        RoadObject(4, "HAZARD", 10, 0.4, 0.4, "MED", "146.1"),
    ]
    prefer_skiltplate_over_regulering_for_stop_yield(graph)
    on_ten = [(obj.type, obj.payload) for obj in graph.objects if obj.sequence_id == 10]
    assert ("YIELD", "202") in on_ten
    assert ("STOP", "Stopplikt") not in on_ten
    assert ("HAZARD", "146.1") in on_ten
    # Sequence without a plate keeps its regulering.
    on_ninetynine = [(obj.type, obj.payload) for obj in graph.objects if obj.sequence_id == 99]
    assert on_ninetynine == [("STOP", "Stopplikt")]


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
    ingest_skiltplate(graph, [nvdb_object(10, {"Skiltnummer": "122 - Tunnel."}, from_pos=0.5)])
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


def test_direction_from_ansiktsside():
    assert direction_from_ansiktsside("Trafikk mot metreringsretning") == "MOT"
    assert direction_from_ansiktsside("Trafikk i metreringsretning") == "MED"
    assert direction_from_ansiktsside("") is None
    assert direction_from_ansiktsside(None) is None


def test_speed_limit_uses_ansiktsside_over_stedfesting_retning():
    graph = graph_with_sequence()
    ingest_skiltplate(
        graph,
        [
            nvdb_object(
                1014926783,
                {
                    "Skiltnummer": "362.40 - Fartsgrense 40 km/t",
                    "Ansiktsside, rettet mot": "Trafikk i metreringsretning",
                },
                direction="MOT",
                from_pos=0.30,
            ),
            nvdb_object(
                1014925851,
                {
                    "Skiltnummer": "362.40 - Fartsgrense 40 km/t",
                    "Ansiktsside, rettet mot": "Trafikk mot metreringsretning",
                },
                direction="MOT",
                from_pos=0.35,
            ),
        ],
    )
    by_id = {obj.nvdb_id: obj for obj in graph.objects}
    assert by_id[1014926783].direction == "MED"
    assert by_id[1014925851].direction == "MOT"


def test_dedupe_paired_speed_plates_keeps_one_per_zone():
    graph = graph_with_sequence(400.0)
    ingest_skiltplate(
        graph,
        [
            nvdb_object(
                1014927192,
                {
                    "Skiltnummer": "362.40",
                    "Ansiktsside, rettet mot": "Trafikk i metreringsretning",
                },
                direction="MOT",
                from_pos=0.020,
            ),
            nvdb_object(
                1014925525,
                {
                    "Skiltnummer": "362.40",
                    "Ansiktsside, rettet mot": "Trafikk i metreringsretning",
                },
                direction="MOT",
                from_pos=0.022,
            ),
            nvdb_object(
                1014925851,
                {
                    "Skiltnummer": "362.40",
                    "Ansiktsside, rettet mot": "Trafikk mot metreringsretning",
                },
                direction="MOT",
                from_pos=0.35,
            ),
        ],
    )
    dedupe_paired_speed_plates(graph)
    med_plates = [obj for obj in graph.objects if obj.direction == "MED"]
    mot_plates = [obj for obj in graph.objects if obj.direction == "MOT"]
    assert len(med_plates) == 1
    assert med_plates[0].nvdb_id == 1014927192
    assert [obj.nvdb_id for obj in mot_plates] == [1014925851]
