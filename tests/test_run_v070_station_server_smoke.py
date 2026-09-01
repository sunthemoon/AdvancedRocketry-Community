import unittest

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v070_station_server_smoke import (
    EXPECTED_VERSION,
    STATION_COUNT,
    STATION_LAUNCH_LOG,
    STATION_TRANSACTION_LOG,
    _regions_overlap,
    _station_from_match,
    _validate_station_set,
)


class V070StationServerSmokeTests(unittest.TestCase):
    def test_station_creation_receipt_is_bounded_and_parseable(self) -> None:
        line = (
            "ARCE_STATION_TRANSACTION action=creation_committed "
            "station=00000000-0000-0000-0000-000000000700 "
            "owner=00000000-0000-0000-0000-000000000701 "
            "cell=-2,3 region=-2304,2816,-1793,3327 "
            "inspected=289 changed=289 chunks=4 detail=success"
        )
        match = STATION_TRANSACTION_LOG.search(line)

        self.assertIsNotNone(match)
        station = _station_from_match(match)  # type: ignore[arg-type]
        self.assertEqual([-2, 3], station["cell"])
        self.assertEqual([-2304, 2816, -1793, 3327], station["region"])
        self.assertEqual(("289", "289", "4"), match.groups()[8:11])  # type: ignore[union-attr]

    def test_fixed_map_rejects_overlap_and_accepts_ten_unique_cells(self) -> None:
        stations = []
        for index in range(STATION_COUNT):
            minimum = index * 1024 - 256
            stations.append({
                "station_id": f"00000000-0000-0000-0000-{index + 0x700:012x}",
                "cell": [index, 0],
                "region": [minimum, -256, minimum + 511, 255],
            })

        _validate_station_set(stations)
        self.assertFalse(_regions_overlap(
            list(stations[0]["region"]),
            list(stations[1]["region"]),
        ))
        stations[1]["region"] = list(stations[0]["region"])
        with self.assertRaisesRegex(SmokeError, "overlap"):
            _validate_station_set(stations)

    def test_station_launch_receipt_binds_station_uuid(self) -> None:
        line = (
            "ARCE_RELEASE_TEST_STATION_LAUNCH "
            "request=00000000-0000-0000-0000-000000000710 "
            "entity=00000000-0000-0000-0000-000000000711 "
            "logical=00000000-0000-0000-0000-000000000712 "
            "source=minecraft:overworld "
            "station=00000000-0000-0000-0000-000000000713 "
            "code=SUCCESS required_fuel=234 fuel_before=1000"
        )
        match = STATION_LAUNCH_LOG.search(line)

        self.assertIsNotNone(match)
        self.assertEqual("00000000-0000-0000-0000-000000000713", match.group(5))  # type: ignore[union-attr]
        self.assertEqual("SUCCESS", match.group(6))  # type: ignore[union-attr]
        self.assertEqual("1.20.1-0.7.0-dev", EXPECTED_VERSION)


if __name__ == "__main__":
    unittest.main()
