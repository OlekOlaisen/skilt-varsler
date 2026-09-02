from __future__ import annotations

import re
from functools import lru_cache

from pyproj import Transformer

_LINESTRING = re.compile(
    r"LINESTRING\s*Z?\s*\((.+)\)",
    re.IGNORECASE,
)
_POLYGON = re.compile(
    r"POLYGON\s*Z?\s*\(\((.+)\)\)",
    re.IGNORECASE,
)


@lru_cache(maxsize=8)
def _transformer(srid: int) -> Transformer:
    horizontal = 25833 if srid in (25833, 5973, 32633) else srid
    return Transformer.from_crs(horizontal, 4326, always_xy=True)


def parse_wkt_line(wkt: str, srid: int) -> list[tuple[float, float]]:
    match = _LINESTRING.search(wkt.replace("\n", " "))
    if not match:
        return []
    points: list[tuple[float, float]] = []
    transformer = _transformer(srid)
    for token in match.group(1).split(","):
        parts = token.strip().split()
        if len(parts) < 2:
            continue
        easting = float(parts[0])
        northing = float(parts[1])
        lon, lat = transformer.transform(easting, northing)
        points.append((lon, lat))
    return points


def parse_wkt_polygon(wkt: str, srid: int) -> list[tuple[float, float]]:
    match = _POLYGON.search(wkt.replace("\n", " "))
    if not match:
        return parse_wkt_line(wkt, srid)
    transformer = _transformer(srid)
    points: list[tuple[float, float]] = []
    for token in match.group(1).split(","):
        parts = token.strip().split()
        if len(parts) < 2:
            continue
        lon, lat = transformer.transform(float(parts[0]), float(parts[1]))
        points.append((lon, lat))
    return points
