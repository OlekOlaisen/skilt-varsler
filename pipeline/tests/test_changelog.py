from skiltvarsler_pipeline.changelog import kommuner_of_object


def test_kommuner_from_lokasjon_list():
    assert kommuner_of_object({"lokasjon": {"kommuner": [3216, 3220]}}) == [3216, 3220]


def test_kommuner_from_nested_nummer():
    obj = {
        "lokasjon": {
            "kommuner": [{"nummer": "0301"}, {"nummer": 4601}],
            "stedfestinger": [{"kommune": 3216}],
        }
    }
    assert kommuner_of_object(obj) == [301, 3216, 4601]


def test_ignores_missing_and_zero():
    assert kommuner_of_object({"lokasjon": {}}) == []
    assert kommuner_of_object({"kommune": 0, "lokasjon": {"kommuner": [None, 0]}}) == []
