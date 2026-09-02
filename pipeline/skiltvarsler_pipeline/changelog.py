from __future__ import annotations

from typing import Any, Iterable


def kommuner_of_object(obj: dict[str, Any]) -> list[int]:
    numbers: set[int] = set()
    _collect_kommune_values(obj.get("kommune"), numbers)
    _collect_kommune_values(obj.get("kommuner"), numbers)
    lokasjon = obj.get("lokasjon") or {}
    if isinstance(lokasjon, dict):
        _collect_kommune_values(lokasjon.get("kommune"), numbers)
        _collect_kommune_values(lokasjon.get("kommuner"), numbers)
        for sted in lokasjon.get("stedfestinger") or []:
            if isinstance(sted, dict):
                _collect_kommune_values(sted.get("kommune"), numbers)
                _collect_kommune_values(sted.get("kommuner"), numbers)
    return sorted(numbers)


def _collect_kommune_values(value: Any, numbers: set[int]) -> None:
    if value is None:
        return
    if isinstance(value, bool):
        return
    if isinstance(value, int):
        if value > 0:
            numbers.add(value)
        return
    if isinstance(value, float) and value.is_integer() and value > 0:
        numbers.add(int(value))
        return
    if isinstance(value, str):
        text = value.strip()
        if text.isdigit():
            number = int(text)
            if number > 0:
                numbers.add(number)
        return
    if isinstance(value, dict):
        _collect_kommune_values(value.get("nummer"), numbers)
        _collect_kommune_values(value.get("kommune"), numbers)
        return
    if isinstance(value, Iterable) and not isinstance(value, (bytes, bytearray)):
        for item in value:
            _collect_kommune_values(item, numbers)
