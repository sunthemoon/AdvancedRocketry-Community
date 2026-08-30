from __future__ import annotations

import csv
import subprocess
import tempfile
import unittest
from pathlib import Path

from tools.audit.audit_upstream import EXPECTED_OUTPUTS, build_manifest, load_tracked_files


MIT_LICENSE = """MIT License

Copyright (c) 2017

Permission is hereby granted, free of charge, to any person obtaining a copy.
"""


class AuditUpstreamTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.upstream = self.root / "upstream"
        self.upstream.mkdir()
        self.git("init")
        self.git("config", "user.name", "Audit Fixture")
        self.git("config", "user.email", "audit@example.invalid")
        self.write("LICENSE", MIT_LICENSE)
        self.write(
            "src/main/java/zmaster587/advancedRocketry/network/PacketFixture.java",
            """package zmaster587.advancedRocketry.network;

import java.util.HashMap;
import java.util.Map;
import zmaster587.libVulpes.network.BasePacket;

public final class PacketFixture extends BasePacket {
    private static final Map<String, String> WORLD_CACHE = new HashMap<>();
}
""",
        )
        self.write(
            "src/main/resources/assets/advancedrocketry/models/item/fixture.json",
            '{"parent":"item/generated","textures":{"layer0":"advancedrocketry:items/fixture"}}\n',
        )
        self.write(
            "src/main/resources/assets/advancedrocketry/textures/items/fixture.png",
            b"\x89PNG\r\n\x1a\n" + b"\x00" * 64,
        )
        self.write(
            "src/main/resources/assets/advancedrocketry/recipes/fixture.json",
            '{"type":"minecraft:crafting_shapeless","result":{"item":"advancedrocketry:fixture"}}\n',
        )
        self.git("add", "--all")
        self.git("commit", "-m", "fixture")
        self.commit = self.git("rev-parse", "HEAD").strip()

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def git(self, *args: str) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.upstream), *args],
            check=True,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
        )
        return result.stdout

    def write(self, relative: str, content: str | bytes) -> None:
        path = self.upstream / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(content, encoding="utf-8", newline="\n")

    def test_generation_is_byte_deterministic(self) -> None:
        first = self.root / "first"
        second = self.root / "second"
        build_manifest(self.upstream, self.commit, first)
        build_manifest(self.upstream, self.commit, second)

        self.assertEqual(set(EXPECTED_OUTPUTS), {path.name for path in first.iterdir()})
        for name in EXPECTED_OUTPUTS:
            self.assertEqual((first / name).read_bytes(), (second / name).read_bytes(), name)

    def test_java_risk_indexes_include_fixture(self) -> None:
        output = self.root / "manifest"
        build_manifest(self.upstream, self.commit, output)

        with (output / "java-files.csv").open(encoding="utf-8", newline="") as handle:
            row = next(csv.DictReader(handle))
        self.assertEqual("network", row["primary_domain"])
        self.assertEqual("true", row["imports_libvulpes"])
        self.assertEqual("true", row["has_static_mutable_state"])
        self.assertIn("static_mutable_candidate", row["notes"])

    def test_local_texture_reference_is_present(self) -> None:
        output = self.root / "manifest"
        build_manifest(self.upstream, self.commit, output)

        with (output / "asset-references.csv").open(encoding="utf-8", newline="") as handle:
            rows = list(csv.DictReader(handle))
        texture = next(row for row in rows if row["reference_kind"] == "texture")
        self.assertEqual("PRESENT", texture["status"])
        self.assertTrue(texture["target_path"].endswith("textures/items/fixture.png"))

    def test_wrong_commit_is_rejected(self) -> None:
        with self.assertRaisesRegex(ValueError, "expected"):
            load_tracked_files(self.upstream, "0" * 40)

    def test_dirty_checkout_is_rejected(self) -> None:
        self.write("untracked.txt", "not part of the exact audit input\n")
        with self.assertRaisesRegex(ValueError, "not clean"):
            load_tracked_files(self.upstream, self.commit)

    def test_output_directory_rejects_unexpected_files(self) -> None:
        output = self.root / "manifest"
        output.mkdir()
        (output / "manual-note.txt").write_text("not generated", encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "unexpected files"):
            build_manifest(self.upstream, self.commit, output)


if __name__ == "__main__":
    unittest.main()
