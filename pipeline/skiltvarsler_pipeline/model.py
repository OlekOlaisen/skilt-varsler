from __future__ import annotations

from dataclasses import dataclass, field


DRIVEABLE_TYPE_VEG = {
    "Enkel bilveg",
    "Kanalisert veg",
    "Rampe",
    "Rundkjøring",
    "Ferje",
    "Motorveg",
    "Motortrafikkveg",
}

SKIP_TYPE_VEG = {
    "Gang- og sykkelveg",
    "Sykkelveg",
    "Gangveg",
    "Fortau",
    "Traktorveg",
    "Sti",
}


@dataclass
class Node:
    id: int
    lon: float
    lat: float


@dataclass
class Link:
    id: int
    sequence_id: int
    link_number: int
    start_node_id: int
    end_node_id: int
    start_pos: float
    end_pos: float
    length_m: float
    type_veg: str
    matchable: bool
    points: list[tuple[float, float]]
    kommune: int = 0


@dataclass
class SpeedInterval:
    sequence_id: int
    from_pos: float
    to_pos: float
    kmh: int
    direction: str
    startdato: str = ""


@dataclass
class RoadObject:
    nvdb_id: int
    type: str
    sequence_id: int
    from_pos: float
    to_pos: float
    direction: str
    payload: str


@dataclass
class KommunePolygon:
    kommune: int
    name: str
    ring: list[tuple[float, float]]


@dataclass
class TileGraph:
    tile_id: str
    version: str
    nodes: dict[int, Node] = field(default_factory=dict)
    links: list[Link] = field(default_factory=list)
    sequence_lengths: dict[int, float] = field(default_factory=dict)
    speeds: list[SpeedInterval] = field(default_factory=list)
    objects: list[RoadObject] = field(default_factory=list)
    kommune_polygons: list[KommunePolygon] = field(default_factory=list)
    warnings: int = 0
