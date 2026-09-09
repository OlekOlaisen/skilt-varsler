"""Parse DATEX II SituationPublication XML into situations.json for the app."""

from __future__ import annotations

import json
import os
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

DATEX_SITUATION_URL = (
    "https://datex-server-get-v3-1.atlas.vegvesen.no/"
    "datexapi/GetSituation/pullsnapshotdata"
)

ROADWORK_TYPES = {
    "ConstructionWorks",
    "MaintenanceWorks",
}
CLOSURE_HINTS = {
    "RoadOrCarriagewayOrLaneManagement",
    "GeneralNetworkManagement",
    "ReroutingManagement",
}

LOCAL_NAME = re.compile(r"\}?([A-Za-z0-9_]+)$")


@dataclass
class SituationOut:
    id: str
    type: str
    title: str
    description: str
    points: list[list[float]]


def local_name(tag: str) -> str:
    match = LOCAL_NAME.search(tag or "")
    return match.group(1) if match else (tag or "")


def text_of(element: ET.Element | None) -> str:
    if element is None:
        return ""
    return "".join(element.itertext()).strip()


def first_text(record: ET.Element, names: Iterable[str]) -> str:
    wanted = set(names)
    for child in record.iter():
        if local_name(child.tag) in wanted:
            value = text_of(child)
            if value:
                return value
    return ""


def parse_lat_lon_pairs(text: str) -> list[list[float]]:
    numbers = [float(token) for token in re.findall(r"[-+]?\d+(?:\.\d+)?", text)]
    points: list[list[float]] = []
    for index in range(0, len(numbers) - 1, 2):
        latitude = numbers[index]
        longitude = numbers[index + 1]
        # DATEX often uses lat lon; some feeds use lon lat. Norway lat ~57-72, lon ~4-32.
        if 40.0 <= latitude <= 80.0 and -20.0 <= longitude <= 40.0:
            points.append([latitude, longitude])
        elif 40.0 <= longitude <= 80.0 and -20.0 <= latitude <= 40.0:
            points.append([longitude, latitude])
    return points


def extract_points(record: ET.Element) -> list[list[float]]:
    points: list[list[float]] = []
    for child in record.iter():
        name = local_name(child.tag)
        if name in {"latitude", "Latitude"}:
            latitude = text_of(child)
            # look for sibling longitude under same parent
            parent = None
            # ElementTree has no parent pointer; scan attributes/children nearby via full record instead.
            _ = latitude
        if name in {"pos", "gmlPos", "coordinates"}:
            points.extend(parse_lat_lon_pairs(text_of(child)))
        if name == "pointCoordinates":
            latitude = None
            longitude = None
            for nested in child:
                nested_name = local_name(nested.tag)
                if nested_name == "latitude":
                    latitude = text_of(nested)
                elif nested_name == "longitude":
                    longitude = text_of(nested)
            if latitude and longitude:
                try:
                    points.append([float(latitude), float(longitude)])
                except ValueError:
                    pass
        if name == "latitude":
            # handled with longitude pair below via parent walk of pointByCoordinates
            pass
    # Dedicated pass for latitude/longitude pairs under the same parent element.
    for parent in record.iter():
        latitude = None
        longitude = None
        for child in list(parent):
            name = local_name(child.tag)
            if name == "latitude":
                latitude = text_of(child)
            elif name == "longitude":
                longitude = text_of(child)
        if latitude and longitude:
            try:
                points.append([float(latitude), float(longitude)])
            except ValueError:
                pass
    # Deduplicate while preserving order.
    unique: list[list[float]] = []
    seen: set[tuple[float, float]] = set()
    for latitude, longitude in points:
        key = (round(latitude, 6), round(longitude, 6))
        if key in seen:
            continue
        seen.add(key)
        unique.append([latitude, longitude])
    return unique


def classify_record(record: ET.Element) -> str | None:
    type_attr = record.attrib.get("{http://www.w3.org/2001/XMLSchema-instance}type") or record.attrib.get(
        "type",
        "",
    )
    type_name = local_name(type_attr) if type_attr else local_name(record.tag)
    if type_name in ROADWORK_TYPES or type_name.endswith("ConstructionWorks") or type_name.endswith(
        "MaintenanceWorks",
    ):
        return "roadwork"
    if type_name in CLOSURE_HINTS or "LaneManagement" in type_name or "NetworkManagement" in type_name:
        lowered = text_of(record).lower()
        if any(
            token in lowered
            for token in (
                "closed",
                "closure",
                "stengt",
                "roadclosed",
                "carriagewayclosed",
                "carriagewayclosures",
            )
        ):
            return "closure"
    return None


def record_title(record: ET.Element, situation_type: str) -> str:
    values: list[str] = []
    for child in record.iter():
        if local_name(child.tag) != "value":
            continue
        value = text_of(child).replace(".dataProcessingNote", "").strip()
        if not value:
            continue
        if value.upper() in {"NPRA", "SVV", "NO", "NORWAY"}:
            continue
        values.append(value)

    for value in values:
        lowered = value.lower()
        if "vegarbeid" in lowered or "stengt" in lowered or "omkj" in lowered:
            return value.split("|")[0].strip()[:120]

    for value in values:
        if len(value) >= 24 and any(token in value for token in ("Fv.", "Rv.", "E6", "E18", "E39", " i ")):
            return value[:120]

    road_number = first_text(record, {"roadNumber"})
    default = "Veiarbeid" if situation_type == "roadwork" else "Stengt veg"
    if road_number:
        return f"{default} {road_number}"[:120]
    if values:
        return values[0].split("|")[0].strip()[:120]
    return default


def parse_situation_xml(xml_text: str) -> list[SituationOut]:
    root = ET.fromstring(xml_text)
    situations: list[SituationOut] = []
    for record in root.iter():
        if local_name(record.tag) != "situationRecord":
            continue
        situation_type = classify_record(record)
        if situation_type is None:
            continue
        points = extract_points(record)
        if not points:
            continue
        record_id = record.attrib.get("id") or f"anon-{len(situations)+1}"
        title = record_title(record, situation_type)
        situations.append(
            SituationOut(
                id=record_id,
                type=situation_type,
                title=title,
                description="",
                points=points,
            ),
        )
    return situations


def write_situations_json(
    situations: list[SituationOut],
    output: Path,
    source: str,
) -> Path:
    output.parent.mkdir(parents=True, exist_ok=True)
    payload = {
        "version": datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ"),
        "source": source,
        "situations": [asdict(item) for item in situations],
    }
    output.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")
    return output


def fetch_datex_xml(user: str, password: str, url: str = DATEX_SITUATION_URL) -> str:
    import base64

    token = base64.b64encode(f"{user}:{password}".encode("utf-8")).decode("ascii")
    request = Request(
        url,
        headers={
            "Authorization": f"Basic {token}",
            # Vegvesen DATEX returns 406 for application/xml; text/xml is accepted.
            "Accept": "text/xml",
            "User-Agent": "skilt-varsler-pipeline",
        },
    )
    with urlopen(request, timeout=120) as response:
        return response.read().decode("utf-8")


def build_situations(
    output: Path,
    fixture: Path | None = None,
    user: str | None = None,
    password: str | None = None,
) -> Path:
    if fixture is not None:
        xml_text = fixture.read_text(encoding="utf-8")
        source = f"fixture:{fixture.name}"
    else:
        resolved_user = user or os.environ.get("DATEX_USER") or ""
        resolved_password = password or os.environ.get("DATEX_PASSWORD") or ""
        if not resolved_user or not resolved_password:
            raise SystemExit(
                "DATEX credentials mangler. Sett DATEX_USER/DATEX_PASSWORD eller bruk --fixture.",
            )
        xml_text = fetch_datex_xml(resolved_user, resolved_password)
        source = "datex"
    situations = parse_situation_xml(xml_text)
    return write_situations_json(situations, output, source=source)
