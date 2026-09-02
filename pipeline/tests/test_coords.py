from skiltvarsler_pipeline.coords import parse_wkt_line, parse_wkt_polygon


def test_parse_linestring_keeps_order():
    wkt = "LINESTRING Z (10 20 1, 11 21 2, 12 22 3)"
    points = parse_wkt_line(wkt, srid=4326)
    assert len(points) == 3
    assert points[0][0] == 10.0
    assert points[0][1] == 20.0
    assert points[-1] == (12.0, 22.0)


def test_parse_polygon_outer_ring():
    wkt = "POLYGON ((10 20, 11 20, 11 21, 10 21, 10 20))"
    ring = parse_wkt_polygon(wkt, srid=4326)
    assert len(ring) >= 4
    assert ring[0] == (10.0, 20.0)
    assert ring[2] == (11.0, 21.0)
