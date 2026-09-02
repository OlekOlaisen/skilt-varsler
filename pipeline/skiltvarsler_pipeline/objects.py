from __future__ import annotations

from typing import Any, Iterable

from .model import RoadObject, SpeedInterval, TileGraph

TYPE_MAP = {
    162: "SPEED_CAMERA",
    823: "SECTION_ATK",
    45: "TOLL",
    291: "WILDLIFE",
    100: "RAILWAY",
    64: "FERRY",
    770: "FERRY",
    596: "PRIORITY_ROAD",
}

STOP_NUMBERS = {"202", "202.0"}
YIELD_NUMBERS = {"210", "210.0"}
HAZARD_PREFIXES = tuple(str(n) for n in range(100, 150))


def egenskap(obj: dict[str, Any], *names: str) -> Any:
    wanted = {name.lower() for name in names}
    for item in obj.get("egenskaper") or []:
        navn = str(item.get("navn") or "").lower()
        if navn in wanted:
            if "verdi" in item:
                return item["verdi"]
            return item.get("enum_id")
    return None


def stedfestinger(obj: dict[str, Any]) -> list[dict[str, Any]]:
    lokasjon = obj.get("lokasjon") or {}
    return list(lokasjon.get("stedfestinger") or [])


def direction_of(sted: dict[str, Any]) -> str:
    value = (sted.get("retning") or "MED").upper()
    if value in {"MED", "MOT"}:
        return value
    return "BOTH"


def positions(sted: dict[str, Any]) -> tuple[float, float] | None:
    sted_type = (sted.get("type") or "").lower()
    if "relativPosisjon" in sted or sted_type == "punkt":
        pos = sted.get("relativPosisjon")
        if pos is None:
            pos = sted.get("posisjon")
        if pos is None:
            return None
        value = float(pos)
        return value, value
    start = sted.get("startposisjon", sted.get("fra_posisjon", sted.get("fraPosisjon")))
    end = sted.get("sluttposisjon", sted.get("til_posisjon", sted.get("tilPosisjon")))
    if start is None or end is None:
        return None
    return float(start), float(end)


def ingest_fartsgrense(graph: TileGraph, objects: Iterable[dict[str, Any]]) -> None:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    for obj in objects:
        kmh = egenskap(obj, "Fartsgrense")
        if kmh is None:
            graph.warnings += 1
            continue
        startdato = ((obj.get("metadata") or {}).get("startdato")) or ""
        for sted in stedfestinger(obj):
            sequence_id = sted.get("veglenkesekvensid") or sted.get("veglenkeid")
            span = positions(sted)
            if sequence_id is None or span is None:
                graph.warnings += 1
                continue
            if int(sequence_id) not in known:
                graph.warnings += 1
                continue
            from_pos, to_pos = span
            graph.speeds.append(
                SpeedInterval(
                    sequence_id=int(sequence_id),
                    from_pos=min(from_pos, to_pos),
                    to_pos=max(from_pos, to_pos),
                    kmh=int(kmh),
                    direction=direction_of(sted),
                    startdato=startdato,
                )
            )


def ingest_typed(graph: TileGraph, type_id: int, objects: Iterable[dict[str, Any]]) -> None:
    mapped = TYPE_MAP.get(type_id)
    if mapped is None:
        return
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    for obj in objects:
        if type_id == 100:
            crossing = str(egenskap(obj, "Type", "Kryssingstype") or "")
            if crossing and "plan" not in crossing.lower() and "over" in crossing.lower():
                continue
        payload = str(egenskap(obj, "Navn", "Art", "Skiltnummer") or mapped)
        nvdb_id = int(obj["id"])
        for sted in stedfestinger(obj):
            sequence_id = sted.get("veglenkesekvensid") or sted.get("veglenkeid")
            span = positions(sted)
            if sequence_id is None or span is None:
                graph.warnings += 1
                continue
            if int(sequence_id) not in known:
                graph.warnings += 1
                continue
            from_pos, to_pos = span
            graph.objects.append(
                RoadObject(
                    nvdb_id=nvdb_id,
                    type=mapped,
                    sequence_id=int(sequence_id),
                    from_pos=from_pos,
                    to_pos=to_pos,
                    direction=direction_of(sted),
                    payload=payload,
                )
            )


def ingest_skiltplate(graph: TileGraph, objects: Iterable[dict[str, Any]]) -> None:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    for obj in objects:
        number = str(egenskap(obj, "Skiltnummer") or "")
        mapped = classify_sign(number)
        if mapped is None:
            continue
        nvdb_id = int(obj["id"])
        for sted in stedfestinger(obj):
            sequence_id = sted.get("veglenkesekvensid") or sted.get("veglenkeid")
            span = positions(sted)
            if sequence_id is None or span is None:
                graph.warnings += 1
                continue
            if int(sequence_id) not in known:
                graph.warnings += 1
                continue
            from_pos, to_pos = span
            graph.objects.append(
                RoadObject(
                    nvdb_id=nvdb_id,
                    type=mapped,
                    sequence_id=int(sequence_id),
                    from_pos=from_pos,
                    to_pos=to_pos,
                    direction=direction_of(sted),
                    payload=number,
                )
            )


def classify_regulering(verdi: str) -> str | None:
    token = verdi.lower()
    if "stopp" in token or "stopplikt" in token:
        return "STOP"
    if "vikeplikt" in token:
        return "YIELD"
    return None


def ingest_trafikkreguleringer(graph: TileGraph, objects: Iterable[dict[str, Any]]) -> int:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    added = 0
    for obj in objects:
        mapped = classify_regulering(str(egenskap(obj, "Trafikkreguleringer") or ""))
        if mapped is None:
            continue
        nvdb_id = int(obj["id"])
        for sted in stedfestinger(obj):
            sequence_id = sted.get("veglenkesekvensid") or sted.get("veglenkeid")
            span = positions(sted)
            if sequence_id is None or span is None:
                graph.warnings += 1
                continue
            if int(sequence_id) not in known:
                graph.warnings += 1
                continue
            from_pos, to_pos = span
            graph.objects.append(
                RoadObject(
                    nvdb_id=nvdb_id,
                    type=mapped,
                    sequence_id=int(sequence_id),
                    from_pos=from_pos,
                    to_pos=to_pos,
                    direction=direction_of(sted),
                    payload=str(egenskap(obj, "Trafikkreguleringer") or mapped),
                )
            )
            added += 1
    return added


def drop_skilt_stop_yield_if_regulering_exists(graph: TileGraph, had_regulering: bool) -> None:
    if not had_regulering:
        return
    kept: list[RoadObject] = []
    for obj in graph.objects:
        if obj.type in {"STOP", "YIELD"} and obj.payload.replace(".", "").isdigit():
            graph.warnings += 1
            continue
        kept.append(obj)
    graph.objects = kept


def classify_sign(number: str) -> str | None:
    token = number.strip()
    if token in STOP_NUMBERS or token.startswith("202"):
        return "STOP"
    if token in YIELD_NUMBERS or token.startswith("210"):
        return "YIELD"
    head = token.split(".")[0]
    if head.isdigit() and 100 <= int(head) <= 149:
        return "HAZARD"
    return None
