from __future__ import annotations

from datetime import date
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

STOP_NUMBERS = {"204", "204.0"}
YIELD_NUMBERS = {"202", "202.0"}
HAZARD_PREFIXES = tuple(str(n) for n in range(100, 157))
INACTIVE_STATUS = ("nedlagt", "utgått", "utgatt", "fjernet", "sanert")


def _norm_name(navn: str) -> str:
    text = navn.lower().strip()
    if "(" in text:
        text = text.split("(", 1)[0].strip()
    return text


def egenskap(obj: dict[str, Any], *names: str) -> Any:
    wanted = {_norm_name(name) for name in names}
    for item in obj.get("egenskaper") or []:
        navn = _norm_name(str(item.get("navn") or ""))
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


def encode_payload(code: str = "", title: str = "", extra: str = "") -> str:
    parts = [_clean_payload_part(code), _clean_payload_part(title), _clean_payload_part(extra)]
    while parts and not parts[-1]:
        parts.pop()
    return "|".join(parts)


def _clean_payload_part(value: Any) -> str:
    return str(value or "").replace("|", " ").strip()


def format_price(value: Any) -> str:
    raw = str(value or "").strip().replace(",", ".")
    if not raw:
        return ""
    try:
        price = float(raw)
    except ValueError:
        return raw
    if price.is_integer():
        return str(int(price))
    return f"{price:g}"


def is_inactive(obj: dict[str, Any]) -> bool:
    status = str(egenskap(obj, "Status", "Driftsstatus") or "").lower()
    return any(token in status for token in INACTIVE_STATUS)


def is_expired(obj: dict[str, Any], today: date | None = None) -> bool:
    today = today or date.today()
    sluttdato = ((obj.get("metadata") or {}).get("sluttdato")) or ""
    if not sluttdato:
        return False
    try:
        return date.fromisoformat(str(sluttdato)[:10]) < today
    except ValueError:
        return False


def payload_for_type(type_id: int, mapped: str, obj: dict[str, Any]) -> str:
    if type_id == 45:
        name = str(egenskap(obj, "Navn bomstasjon", "Navn") or "").strip()
        price = format_price(egenskap(obj, "Takst liten bil"))
        return encode_payload("792", name, price)
    if type_id in {64, 770}:
        name = str(egenskap(obj, "Navn") or "").strip()
        return encode_payload("775", name)
    if type_id == 100:
        crossing = str(egenskap(obj, "Type", "Kryssingstype") or "").lower()
        without_barriers = (
            "uten bommer" in crossing or "uten lysregulering og bommer" in crossing
        )
        code = "135" if without_barriers else "134"
        return encode_payload(code)
    if type_id == 823:
        return encode_payload("556.2", str(egenskap(obj, "Navn") or "").strip())
    if type_id == 291:
        return str(egenskap(obj, "Art") or mapped)
    if type_id == 162:
        return str(egenskap(obj, "Navn") or "")
    if type_id == 596:
        return "206"
    return str(egenskap(obj, "Navn", "Art", "Skiltnummer") or mapped)


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
        if is_inactive(obj):
            continue
        if type_id == 100:
            crossing = str(egenskap(obj, "Type", "Kryssingstype") or "")
            if crossing and "plan" not in crossing.lower() and "over" in crossing.lower():
                continue
        payload = payload_for_type(type_id, mapped, obj)
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


def ingest_skiltplate(graph: TileGraph, objects: Iterable[dict[str, Any]], today: date | None = None) -> None:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    for obj in objects:
        number = str(egenskap(obj, "Skiltnummer") or "")
        mapped = classify_sign(number)
        if mapped is None:
            continue
        if number.strip().startswith("110") and is_expired(obj, today):
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


def collect_tunnels(graph: TileGraph, objects: Iterable[dict[str, Any]]) -> list[RoadObject]:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    tunnels: list[RoadObject] = []
    for obj in objects:
        if is_inactive(obj):
            continue
        name = str(egenskap(obj, "Navn") or "").strip()
        nvdb_length = egenskap(obj, "Lengde")
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
            sequence_length = graph.sequence_lengths.get(int(sequence_id), 0.0)
            length_m = abs(to_pos - from_pos) * sequence_length
            if length_m <= 1.0 and nvdb_length is not None:
                try:
                    length_m = float(nvdb_length)
                except (TypeError, ValueError):
                    pass
            tunnels.append(
                RoadObject(
                    nvdb_id=nvdb_id,
                    type="TUNNEL",
                    sequence_id=int(sequence_id),
                    from_pos=from_pos,
                    to_pos=to_pos,
                    direction=direction_of(sted),
                    payload=encode_payload("122", name or "Tunnel", str(int(round(length_m)))),
                )
            )
    return tunnels


def enrich_tunnel_signs(graph: TileGraph, tunnels: list[RoadObject]) -> None:
    if not tunnels:
        return
    by_sequence: dict[int, list[RoadObject]] = {}
    for tunnel in tunnels:
        by_sequence.setdefault(tunnel.sequence_id, []).append(tunnel)
    for obj in graph.objects:
        if obj.type != "HAZARD" or not _is_tunnel_sign(obj.payload):
            continue
        match = _nearest_tunnel_ahead(graph, obj, by_sequence.get(obj.sequence_id) or [])
        if match is None:
            continue
        obj.payload = match.payload


def _is_tunnel_sign(payload: str) -> bool:
    token = payload.strip().split("|", 1)[0]
    return token == "122" or token.startswith("122.")


def _directions_match(left: str, right: str) -> bool:
    return left == "BOTH" or right == "BOTH" or left == right


def _nearest_tunnel_ahead(
    graph: TileGraph,
    sign: RoadObject,
    tunnels: list[RoadObject],
) -> RoadObject | None:
    sequence_length = graph.sequence_lengths.get(sign.sequence_id, 0.0)
    best: RoadObject | None = None
    best_meters: float | None = None
    travel = sign.direction
    sign_pos = sign.from_pos
    for tunnel in tunnels:
        if not _directions_match(travel, tunnel.direction):
            continue
        low = min(tunnel.from_pos, tunnel.to_pos)
        high = max(tunnel.from_pos, tunnel.to_pos)
        if travel == "MOT":
            entry = high
            delta = sign_pos - entry
            if delta < -1e-6:
                if sign_pos >= low - 1e-6:
                    delta = 0.0
                else:
                    continue
        else:
            entry = low
            delta = entry - sign_pos
            if delta < -1e-6:
                if sign_pos <= high + 1e-6:
                    delta = 0.0
                else:
                    continue
        meters = abs(delta) * sequence_length
        if best_meters is None or meters < best_meters:
            best_meters = meters
            best = tunnel
    return best


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
    if token in STOP_NUMBERS or token.startswith("204"):
        return "STOP"
    if token in YIELD_NUMBERS or token.startswith("202"):
        return "YIELD"
    if token.startswith("206") or token.startswith("208"):
        return "PRIORITY_ROAD"
    head = token.split(".")[0]
    if head.isdigit() and 100 <= int(head) <= 156:
        return "HAZARD"
    return None
