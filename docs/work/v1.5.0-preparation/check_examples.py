#!/usr/bin/env python3
"""Check ADR-040 documentation projections, not production NBT or runtime behavior."""

from copy import deepcopy
import json
from pathlib import Path
import unittest


EXAMPLES = json.loads(Path(__file__).with_name("examples.json").read_text(encoding="utf-8"))


def region(cell, width):
    if (len(cell) != 2 or any(type(value) is not int or abs(value) > 1_000_000 for value in cell)
            or type(width) is not int or width not in (512, 768)):
        raise ValueError("unsupported centered region")
    x, z = (coordinate * 1024 for coordinate in cell)
    return [x - width // 2, z - width // 2, x + width // 2 - 1, z + width // 2 - 1]


def contains(bounds, x, z):
    return bounds[0] <= x <= bounds[2] and bounds[1] <= z <= bounds[3]


def indexed_cell(x, z):
    return [(coordinate + 512) // 1024 for coordinate in (x, z)]


def preview_upgrade(source):
    """Only version/geometry projection rules; intentionally not a full codec."""
    result = deepcopy(source)
    version = result["schema_version"]
    if type(version) is not int or version not in (1, 2, 3):
        raise ValueError("unsupported root schema")
    if version == 2 and result.get("format_epoch") != "v0.9.0-beta":
        raise ValueError("invalid legacy epoch")
    if version == 3 and result.get("format_epoch") != "v1.5.0-orbital-station":
        raise ValueError("invalid current epoch")
    for station in result["stations"]:
        expected = 2 if version == 3 else 1
        if type(station["schema_version"]) is not int or station["schema_version"] != expected:
            raise ValueError("mixed station schema")
        bounds = station["region"]
        if len(bounds) != 4 or any(type(value) is not int for value in bounds):
            raise ValueError("invalid bounds")
        width = bounds[2] - bounds[0] + 1
        cell = [station["cell_x"], station["cell_z"]]
        if (version != 3 and width != 512) or bounds != region(cell, width):
            raise ValueError("invalid legacy or off-center geometry")
        if station["landing_pad"] != [cell[0] * 1024, 128, cell[1] * 1024]:
            raise ValueError("moved landing pad")
        station["schema_version"] = 2
    for reservation in result["reservations"]:
        if type(reservation["schema_version"]) is not int or reservation["schema_version"] != 1:
            raise ValueError("unsupported reservation schema")
    result["schema_version"] = 3
    result["format_epoch"] = "v1.5.0-orbital-station"
    return result


class ContractExamples(unittest.TestCase):
    def test_explicit_geometry_examples(self):
        for example in EXAMPLES["geometry"]:
            self.assertEqual(example["region"], region(example["cell"], example["width"]))

    def test_inclusive_edges_and_constant_index(self):
        for x in (-1_000_000, -2, -1, 0, 1, 2, 1_000_000):
            for z in (-1_000_000, -1, 0, 1, 1_000_000):
                for width in (512, 768):
                    bounds = region([x, z], width)
                    for px in (bounds[0], x * 1024, bounds[2]):
                        for pz in (bounds[1], z * 1024, bounds[3]):
                            self.assertTrue(contains(bounds, px, pz))
                            self.assertEqual([x, z], indexed_cell(px, pz))
                    self.assertFalse(contains(bounds, bounds[0] - 1, z * 1024))
                    self.assertFalse(contains(bounds, bounds[2] + 1, z * 1024))
                    self.assertFalse(contains(bounds, x * 1024, bounds[1] - 1))
                    self.assertFalse(contains(bounds, x * 1024, bounds[3] + 1))

    def test_largest_adjacent_regions_leave_256_columns(self):
        for coordinate in range(-10, 11):
            first = region([coordinate, coordinate], 768)
            east = region([coordinate + 1, coordinate], 768)
            south = region([coordinate, coordinate + 1], 768)
            self.assertEqual(256, east[0] - first[2] - 1)
            self.assertEqual(256, south[1] - first[3] - 1)

    def test_bad_width_or_cell_rejected(self):
        for width in (0, 511, 513, 767, 769, 1024, 768.0, True):
            with self.assertRaises(ValueError):
                region([0, 0], width)
        for cell in ([1_000_001, 0], [-1_000_001, 0], [0], [0.0, 0], [True, 0]):
            with self.assertRaises(ValueError):
                region(cell, 512)

    def test_legacy_projection_changes_only_versions(self):
        before = deepcopy(EXAMPLES["baseline"])
        after = preview_upgrade(before)
        expected = deepcopy(before)
        expected["schema_version"] = 3
        expected["format_epoch"] = "v1.5.0-orbital-station"
        expected["stations"][0]["schema_version"] = 2
        self.assertEqual(expected, after)
        self.assertEqual(EXAMPLES["baseline"], before)
        self.assertEqual(before["reservations"], after["reservations"])

    def test_legacy_root_one_and_idempotent_current(self):
        legacy = deepcopy(EXAMPLES["baseline"])
        legacy["schema_version"] = 1
        del legacy["format_epoch"]
        current = preview_upgrade(legacy)
        self.assertEqual(current, preview_upgrade(current))

    def test_legacy_geometry_cannot_gain_new_validity(self):
        source = deepcopy(EXAMPLES["baseline"])
        source["stations"][0]["region"] = region([-1, 1], 768)
        with self.assertRaises(ValueError):
            preview_upgrade(source)

    def test_current_expanded_projection(self):
        current = preview_upgrade(EXAMPLES["baseline"])
        current["stations"][0]["region"] = region([-1, 1], 768)
        self.assertEqual(current, preview_upgrade(current))

    def test_future_mixed_and_reservation_versions_rejected(self):
        for version in (0, -1, 4, 2.0, True):
            source = deepcopy(EXAMPLES["baseline"])
            source["schema_version"] = version
            with self.assertRaises(ValueError):
                preview_upgrade(source)
        for collection in ("stations", "reservations"):
            source = deepcopy(EXAMPLES["baseline"])
            source[collection][0]["schema_version"] = 2
            with self.assertRaises(ValueError):
                preview_upgrade(source)
        current = preview_upgrade(EXAMPLES["baseline"])
        current["stations"][0]["schema_version"] = 1
        with self.assertRaises(ValueError):
            preview_upgrade(current)

    def test_wrong_epochs_rejected(self):
        for source in (deepcopy(EXAMPLES["baseline"]), preview_upgrade(EXAMPLES["baseline"])):
            source["format_epoch"] = "example:wrong"
            with self.assertRaises(ValueError):
                preview_upgrade(source)

    def test_off_center_and_pad_changes_rejected(self):
        for field in ("region", "landing_pad"):
            source = preview_upgrade(EXAMPLES["baseline"])
            source["stations"][0][field][0] += 1
            with self.assertRaises(ValueError):
                preview_upgrade(source)

    def test_other_projection_fields_are_not_reinterpreted(self):
        source = deepcopy(EXAMPLES["baseline"])
        source["stations"][0]["environment"]["vacuum"] = 2
        current = preview_upgrade(source)
        for field in ("station_id", "owner_id", "members", "invitations", "orbit_body",
                      "environment", "created_at_game_time", "example_extra_tag"):
            self.assertEqual(source["stations"][0][field], current["stations"][0][field])


if __name__ == "__main__":
    unittest.main(verbosity=2)
