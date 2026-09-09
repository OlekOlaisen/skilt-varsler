"""Parse DATEX II SituationPublication XML into situations.json for the app."""

from __future__ import annotations

import hashlib
import json
import os
import re
from dataclasses import asdict, dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterable
from urllib.request import Request, urlopen
from xml.etree import ElementTree as ET

DATEX_BASE_URL = (
    "https://datex-server-get-v3-1.atlas.vegvesen.no/"
    "datexapi/GetSituation/pullsnapshotdata"
)

# Filtered pulls are much smaller than the national snapshot and are preferred.
DATEX_FILTER_URLS = {
    "Accident": f"{DATEX_BASE_URL}/filter/Accident",
    "ConstructionWorks": f"{DATEX_BASE_URL}/filter/ConstructionWorks",
    "MaintenanceWorks": f"{DATEX_BASE_URL}/filter/MaintenanceWorks",
    "RoadOrCarriagewayOrLaneManagement": (
        f"{DATEX_BASE_URL}/filter/RoadOrCarriagewayOrLaneManagement"
    ),
}

DATEX_PROFILES: dict[str, list[str]] = {
    "accidents": ["Accident"],
    "roadworks": [
        "ConstructionWorks",
        "MaintenanceWorks",
        "RoadOrCarriagewayOrLaneManagement",
    ],
    "all": [
        "Accident",
        "ConstructionWorks",
        "MaintenanceWorks",
        "RoadOrCarriagewayOrLaneManagement",
    ],
}

PROFILE_REPLACE_TYPES: dict[str, set[str]] = {
    "accidents": {"accident"},
    "roadworks": {"roadwork", "closure"},
    "all": {"accident", "roadwork", "closure"},
}

ROADWORK_TYPES = {
    "ConstructionWorks",
    "MaintenanceWorks",
}
ACCIDENT_TYPES = {
    "Accident",
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
    if type_name in ACCIDENT_TYPES or type_name.endswith("Accident"):
        return "accident"
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
        if any(
            token in lowered
            for token in (
                "vegarbeid",
                "stengt",
                "omkj",
                "ulykke",
                "kollisjon",
                "accident",
            )
        ):
            return value.split("|")[0].strip()[:120]

    for value in values:
        if len(value) >= 24 and any(token in value for token in ("Fv.", "Rv.", "E6", "E18", "E39", " i ")):
            return value[:120]

    road_number = first_text(record, {"roadNumber"})
    default = {
        "roadwork": "Veiarbeid",
        "accident": "Trafikkulykke",
        "closure": "Stengt veg",
    }.get(situation_type, "Trafikkmelding")
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


def dedupe_situations(situations: list[SituationOut]) -> list[SituationOut]:
    by_id: dict[str, SituationOut] = {}
    for item in situations:
        by_id[item.id] = item
    return list(by_id.values())


def merge_situations(
    existing: list[SituationOut],
    incoming: list[SituationOut],
    replace_types: set[str],
) -> list[SituationOut]:
    """Replace situations of replace_types; keep other types from existing."""
    kept = [item for item in existing if item.type not in replace_types]
    return dedupe_situations(kept + incoming)


def load_situations_json(path: Path) -> list[SituationOut]:
    if not path.exists() or path.stat().st_size < 8:
        return []
    payload = json.loads(path.read_text(encoding="utf-8"))
    items: list[SituationOut] = []
    for raw in payload.get("situations") or []:
        points = raw.get("points") or []
        if not points:
            continue
        items.append(
            SituationOut(
                id=str(raw.get("id") or f"anon-{len(items)+1}"),
                type=str(raw.get("type") or "roadwork"),
                title=str(raw.get("title") or ""),
                description=str(raw.get("description") or ""),
                points=[[float(pair[0]), float(pair[1])] for pair in points if len(pair) >= 2],
            ),
        )
    return items


def situations_fingerprint(situations: list[SituationOut]) -> str:
    payload = [
        {
            "id": item.id,
            "type": item.type,
            "title": item.title,
            "description": item.description,
            "points": item.points,
        }
        for item in sorted(situations, key=lambda item: item.id)
    ]
    encoded = json.dumps(payload, ensure_ascii=False, separators=(",", ":"), sort_keys=True)
    return hashlib.sha256(encoded.encode("utf-8")).hexdigest()


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


def fetch_datex_xml(user: str, password: str, url: str = DATEX_BASE_URL) -> str:
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


def fetch_profile_situations(
    profile: str,
    user: str,
    password: str,
) -> tuple[list[SituationOut], str]:
    filters = DATEX_PROFILES.get(profile)
    if not filters:
        raise SystemExit(f"Ukjent DATEX-profil: {profile}")
    collected: list[SituationOut] = []
    used: list[str] = []
    for filter_name in filters:
        url = DATEX_FILTER_URLS[filter_name]
        xml_text = fetch_datex_xml(user, password, url=url)
        parsed = parse_situation_xml(xml_text)
        collected.extend(parsed)
        used.append(f"{filter_name}:{len(parsed)}")
    return dedupe_situations(collected), "datex-filters:" + ",".join(used)


def build_situations(
    output: Path,
    fixture: Path | None = None,
    user: str | None = None,
    password: str | None = None,
    profile: str = "all",
    merge_with: Path | None = None,
    skip_if_unchanged: bool = False,
) -> Path | None:
    if profile not in DATEX_PROFILES:
        raise SystemExit(f"Ukjent DATEX-profil: {profile}. Gyldige: {', '.join(DATEX_PROFILES)}")

    if fixture is not None:
        xml_text = fixture.read_text(encoding="utf-8")
        incoming = parse_situation_xml(xml_text)
        source = f"fixture:{fixture.name}"
    else:
        resolved_user = user or os.environ.get("DATEX_USER") or ""
        resolved_password = password or os.environ.get("DATEX_PASSWORD") or ""
        if not resolved_user or not resolved_password:
            raise SystemExit(
                "DATEX credentials mangler. Sett DATEX_USER/DATEX_PASSWORD eller bruk --fixture.",
            )
        incoming, source = fetch_profile_situations(profile, resolved_user, resolved_password)

    replace_types = PROFILE_REPLACE_TYPES[profile]
    if profile != "all":
        incoming = [item for item in incoming if item.type in replace_types]

    existing: list[SituationOut] = []
    if merge_with is not None:
        existing = load_situations_json(merge_with)

    if existing and (profile != "all" or merge_with is not None):
        situations = merge_situations(existing, incoming, replace_types)
        if merge_with is not None:
            source = f"{source}+merge:{merge_with.name}"
    else:
        situations = dedupe_situations(incoming)

    if skip_if_unchanged and merge_with is not None and merge_with.exists():
        if situations_fingerprint(situations) == situations_fingerprint(existing):
            return None

    return write_situations_json(situations, output, source=source)
