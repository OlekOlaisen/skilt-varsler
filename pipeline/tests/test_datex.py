from pathlib import Path

from skiltvarsler_pipeline.datex import (
    build_situations,
    merge_situations,
    parse_situation_xml,
    situations_fingerprint,
)


FIXTURE = Path(__file__).parent / "fixtures" / "datex_situation_sample.xml"


def test_parse_fixture_has_roadwork_closure_and_accident():
    xml_text = FIXTURE.read_text(encoding="utf-8")
    situations = parse_situation_xml(xml_text)
    types = {item.type for item in situations}
    assert "roadwork" in types
    assert "closure" in types
    assert "accident" in types
    assert all(item.points for item in situations)


def test_build_situations_from_fixture(tmp_path: Path):
    output = tmp_path / "situations.json"
    path = build_situations(output=output, fixture=FIXTURE)
    assert path is not None
    text = path.read_text(encoding="utf-8")
    assert "Veiarbeid Kirkeveien" in text
    assert "Trafikkulykke Ring 3" in text
    assert "source" in text


def test_accidents_profile_merges_without_dropping_roadworks(tmp_path: Path):
    previous = tmp_path / "previous.json"
    build_situations(output=previous, fixture=FIXTURE, profile="all")
    built = tmp_path / "built.json"
    path = build_situations(
        output=built,
        fixture=FIXTURE,
        profile="accidents",
        merge_with=previous,
    )
    assert path is not None
    types = {item["type"] for item in __import__("json").loads(path.read_text(encoding="utf-8"))["situations"]}
    assert "accident" in types
    assert "roadwork" in types
    assert "closure" in types


def test_roadworks_profile_keeps_accidents(tmp_path: Path):
    previous = tmp_path / "previous.json"
    build_situations(output=previous, fixture=FIXTURE, profile="all")
    built = tmp_path / "built.json"
    path = build_situations(
        output=built,
        fixture=FIXTURE,
        profile="roadworks",
        merge_with=previous,
    )
    assert path is not None
    types = {item["type"] for item in __import__("json").loads(path.read_text(encoding="utf-8"))["situations"]}
    assert "accident" in types
    assert "roadwork" in types


def test_skip_if_unchanged(tmp_path: Path):
    previous = tmp_path / "previous.json"
    build_situations(output=previous, fixture=FIXTURE, profile="accidents")
    built = tmp_path / "built.json"
    path = build_situations(
        output=built,
        fixture=FIXTURE,
        profile="accidents",
        merge_with=previous,
        skip_if_unchanged=True,
    )
    assert path is None
    assert not built.exists()


def test_merge_and_fingerprint_helpers():
    xml_text = FIXTURE.read_text(encoding="utf-8")
    all_items = parse_situation_xml(xml_text)
    accidents = [item for item in all_items if item.type == "accident"]
    roadworks = [item for item in all_items if item.type in {"roadwork", "closure"}]
    merged = merge_situations(roadworks, accidents, replace_types={"accident"})
    assert len(merged) == len(all_items)
    assert situations_fingerprint(merged) == situations_fingerprint(all_items)
