from __future__ import annotations

from datetime import date
from typing import Any

from .coords import parse_wkt_line
from .model import DRIVEABLE_TYPE_VEG, SKIP_TYPE_VEG, Link, Node, RoadObject, TileGraph


def ingest_sequences(graph: TileGraph, sequences: list[dict[str, Any]], today: date | None = None) -> None:
    today = today or date.today()
    for sequence in sequences:
        sequence_id = int(sequence["veglenkesekvensid"])
        ports = {int(port["id"]): port for port in sequence.get("porter") or []}
        sequence_length = 0.0
        for veglenke in sequence.get("veglenker") or []:
            end_date = veglenke.get("sluttdato")
            if end_date:
                try:
                    if date.fromisoformat(end_date) < today:
                        continue
                except ValueError:
                    pass
            type_veg = veglenke.get("typeVeg") or ""
            if type_veg in SKIP_TYPE_VEG:
                continue
            link_type = (veglenke.get("type") or "HOVED").upper()
            is_connection = link_type == "KONNEKTERING"
            driveable = type_veg in DRIVEABLE_TYPE_VEG
            if not driveable and not is_connection:
                continue
            geometry = veglenke.get("geometri") or {}
            wkt = geometry.get("wkt") or ""
            srid = int(geometry.get("srid") or 5973)
            points = parse_wkt_line(wkt, srid)
            if len(points) < 2:
                graph.warnings += 1
                continue
            start_port = ports.get(int(veglenke["startport"]))
            end_port = ports.get(int(veglenke["sluttport"]))
            if not start_port or not end_port:
                graph.warnings += 1
                continue
            start_node = int(start_port["tilkobling"]["nodeid"])
            end_node = int(end_port["tilkobling"]["nodeid"])
            start_lon, start_lat = points[0]
            end_lon, end_lat = points[-1]
            graph.nodes[start_node] = Node(start_node, start_lon, start_lat)
            graph.nodes[end_node] = Node(end_node, end_lon, end_lat)
            length = float(veglenke.get("lengde") or 0.0)
            link_number = int(veglenke.get("veglenkenummer") or 1)
            graph.links.append(
                Link(
                    id=sequence_id * 10_000 + link_number,
                    sequence_id=sequence_id,
                    link_number=link_number,
                    start_node_id=start_node,
                    end_node_id=end_node,
                    start_pos=float(veglenke.get("startposisjon") or 0.0),
                    end_pos=float(veglenke.get("sluttposisjon") or 1.0),
                    length_m=length,
                    type_veg=type_veg,
                    matchable=driveable and not is_connection,
                    kommune=int(geometry.get("kommune") or 0),
                    points=points,
                )
            )
            sequence_length += length
            if type_veg == "Ferje":
                graph.objects.append(
                    RoadObject(
                        nvdb_id=sequence_id,
                        type="FERRY",
                        sequence_id=sequence_id,
                        from_pos=float(veglenke.get("startposisjon") or 0.0),
                        to_pos=float(veglenke.get("sluttposisjon") or 1.0),
                        direction="BOTH",
                        payload="Ferje",
                    )
                )
        if sequence_length > 0:
            graph.sequence_lengths[sequence_id] = float(sequence.get("lengde") or sequence_length)
