from pathlib import Path

from skiltvarsler_pipeline.datex import build_situations, parse_situation_xml


FIXTURE = Path(__file__).parent / "fixtures" / "datex_situation_sample.xml"


def test_parse_fixture_has_roadwork_and_closure():
    xml_text = FIXTURE.read_text(encoding="utf-8")
    situations = parse_situation_xml(xml_text)
    types = {item.type for item in situations}
    assert "roadwork" in types
    assert "closure" in types
    assert all(item.points for item in situations)


def test_build_situations_from_fixture(tmp_path: Path):
    output = tmp_path / "situations.json"
    path = build_situations(output=output, fixture=FIXTURE)
    text = path.read_text(encoding="utf-8")
    assert "Veiarbeid Kirkeveien" in text
    assert "source" in text
