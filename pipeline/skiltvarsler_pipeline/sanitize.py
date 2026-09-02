from __future__ import annotations

from .model import RoadObject, SpeedInterval, TileGraph


def sanitize(graph: TileGraph) -> None:
    graph.speeds = _sanitize_speeds(graph.speeds, graph)
    graph.objects = _sanitize_objects(graph.objects, graph)


def _clip(pos: float) -> float | None:
    if pos < -1e-6 or pos > 1.0 + 1e-6:
        return None
    return min(1.0, max(0.0, pos))


def _sanitize_speeds(intervals: list[SpeedInterval], graph: TileGraph) -> list[SpeedInterval]:
    cleaned: list[SpeedInterval] = []
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    for interval in intervals:
        if interval.sequence_id not in known:
            graph.warnings += 1
            continue
        start = _clip(interval.from_pos)
        end = _clip(interval.to_pos)
        if start is None or end is None:
            graph.warnings += 1
            continue
        lo, hi = (start, end) if start <= end else (end, start)
        if hi - lo <= 1e-9:
            graph.warnings += 1
            continue
        cleaned.append(
            SpeedInterval(
                sequence_id=interval.sequence_id,
                from_pos=lo,
                to_pos=hi,
                kmh=interval.kmh,
                direction=interval.direction,
                startdato=interval.startdato,
            )
        )
    result: list[SpeedInterval] = []
    grouped: dict[tuple[int, str], list[SpeedInterval]] = {}
    for interval in cleaned:
        grouped.setdefault((interval.sequence_id, interval.direction), []).append(interval)
    for (sequence_id, direction), group in grouped.items():
        result.extend(_resolve_speed_overlaps(sequence_id, direction, group, graph))
    return result


def _resolve_speed_overlaps(
    sequence_id: int,
    direction: str,
    group: list[SpeedInterval],
    graph: TileGraph,
) -> list[SpeedInterval]:
    cuts = sorted({item.from_pos for item in group} | {item.to_pos for item in group})
    resolved: list[SpeedInterval] = []
    for left, right in zip(cuts, cuts[1:]):
        covering = [item for item in group if item.from_pos <= left + 1e-12 and item.to_pos >= right - 1e-12]
        if not covering:
            continue
        if len(covering) > 1:
            graph.warnings += 1
        winner = max(covering, key=lambda item: (item.startdato, item.kmh))
        if resolved and resolved[-1].kmh == winner.kmh and abs(resolved[-1].to_pos - left) < 1e-9:
            resolved[-1] = SpeedInterval(
                sequence_id=sequence_id,
                from_pos=resolved[-1].from_pos,
                to_pos=right,
                kmh=winner.kmh,
                direction=direction,
                startdato=winner.startdato,
            )
        else:
            resolved.append(
                SpeedInterval(
                    sequence_id=sequence_id,
                    from_pos=left,
                    to_pos=right,
                    kmh=winner.kmh,
                    direction=direction,
                    startdato=winner.startdato,
                )
            )
    return resolved


def _sanitize_objects(objects: list[RoadObject], graph: TileGraph) -> list[RoadObject]:
    known = set(graph.sequence_lengths) | {link.sequence_id for link in graph.links}
    cleaned: list[RoadObject] = []
    for obj in objects:
        if obj.sequence_id not in known:
            graph.warnings += 1
            continue
        start = _clip(obj.from_pos)
        end = _clip(obj.to_pos)
        if start is None or end is None:
            graph.warnings += 1
            continue
        lo, hi = (start, end) if start <= end else (end, start)
        cleaned.append(
            RoadObject(
                nvdb_id=obj.nvdb_id,
                type=obj.type,
                sequence_id=obj.sequence_id,
                from_pos=lo,
                to_pos=hi,
                direction=obj.direction,
                payload=obj.payload,
            )
        )
    merged: list[RoadObject] = []
    grouped: dict[tuple[str, int, str, str], list[RoadObject]] = {}
    for obj in cleaned:
        if obj.from_pos == obj.to_pos:
            merged.append(obj)
            continue
        key = (obj.type, obj.sequence_id, obj.direction, obj.payload)
        grouped.setdefault(key, []).append(obj)
    for group in grouped.values():
        merged.extend(_merge_identical_intervals(group, graph))
    seen_starts: set[tuple[str, int, str, float]] = set()
    result: list[RoadObject] = []
    for obj in merged:
        start_key = (obj.type, obj.sequence_id, obj.direction, round(min(obj.from_pos, obj.to_pos), 5))
        if start_key in seen_starts and obj.from_pos != obj.to_pos:
            graph.warnings += 1
            continue
        seen_starts.add(start_key)
        result.append(obj)
    return result


def _merge_identical_intervals(group: list[RoadObject], graph: TileGraph) -> list[RoadObject]:
    ordered = sorted(group, key=lambda item: (item.from_pos, item.to_pos))
    result: list[RoadObject] = []
    for obj in ordered:
        if result and obj.from_pos <= result[-1].to_pos + 1e-6:
            if obj.to_pos > result[-1].to_pos:
                graph.warnings += 1
                previous = result[-1]
                result[-1] = RoadObject(
                    nvdb_id=previous.nvdb_id,
                    type=previous.type,
                    sequence_id=previous.sequence_id,
                    from_pos=previous.from_pos,
                    to_pos=obj.to_pos,
                    direction=previous.direction,
                    payload=previous.payload,
                )
            else:
                graph.warnings += 1
        else:
            result.append(obj)
    return result
