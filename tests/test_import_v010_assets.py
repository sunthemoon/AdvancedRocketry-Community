from __future__ import annotations

import importlib.util
import json
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "tools/import/import_v010_assets.py"
SPEC = importlib.util.spec_from_file_location("import_v010_assets", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


MIT_LICENSE = "MIT License\n\nCopyright (c) 2017\n"


class ImportV010AssetsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name)
        self.upstream = self.root / "upstream"
        self.repository = self.root / "project"
        self.upstream.mkdir()
        self.repository.mkdir()
        self.git("init")
        self.git("config", "user.name", "Import Fixture")
        self.git("config", "user.email", "import@example.invalid")
        self.write_upstream("LICENSE", MIT_LICENSE)
        self.write_upstream(
            "src/main/resources/assets/advancedrocketry/textures/items/source.png",
            b"fixture-png-bytes",
        )
        self.write_upstream(
            "src/main/resources/assets/advancedrocketry/lang/en_US.lang",
            "item.fixture.name=Fixture Item\n",
        )
        self.git("add", "--all")
        self.git("commit", "-m", "fixture")
        self.commit = self.git("rev-parse", "HEAD").strip()
        self.plan = self.repository / "plan.json"
        self.record = self.repository / "record.json"
        self.write_plan()

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

    def write_upstream(self, relative: str, content: str | bytes) -> None:
        path = self.upstream / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        if isinstance(content, bytes):
            path.write_bytes(content)
        else:
            path.write_text(content, encoding="utf-8", newline="\n")

    def write_plan(self) -> None:
        plan = {
            "schema_version": 1,
            "batch_id": "fixture",
            "target_version": "v0.1.0",
            "source_repository": "https://github.com/Advanced-Rocketry/AdvancedRocketry",
            "source_branch": "1.12",
            "source_commit": self.commit,
            "license": "MIT",
            "copyright_notice": "Copyright (c) 2017",
            "entries": [
                {
                    "source_path": "src/main/resources/assets/advancedrocketry/textures/items/source.png",
                    "target_path": "src/main/resources/assets/advancedrocketrycommunity/textures/item/target.png",
                    "mode": "copy",
                    "transformation": ["rename namespace"],
                },
                {
                    "source_path": "src/main/resources/assets/advancedrocketry/lang/en_US.lang",
                    "target_path": "src/generated/resources/assets/advancedrocketrycommunity/lang/en_us.json",
                    "mode": "lang",
                    "translations": {"item.fixture.name": "item.advancedrocketrycommunity.fixture"},
                    "additions": {"itemGroup.advancedrocketrycommunity.main": "Fixture Tab"},
                    "transformation": ["extract reviewed key", "serialize JSON"],
                },
            ],
        }
        self.plan.write_bytes(MODULE.pretty_json(plan))

    def test_generate_and_verify_exact_targets(self) -> None:
        first = MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        first_bytes = self.record.read_bytes()
        second = MODULE.generate(self.repository, self.upstream, self.plan, self.record)

        self.assertEqual(first, second)
        self.assertEqual(first_bytes, self.record.read_bytes())
        self.assertEqual([], MODULE.verify(self.repository, self.upstream, self.plan, self.record))
        self.assertEqual("PENDING_HUMAN_REVIEW", first["review"]["status"])
        language = json.loads(
            (self.repository / "src/generated/resources/assets/advancedrocketrycommunity/lang/en_us.json").read_text(encoding="utf-8")
        )
        self.assertEqual("Fixture Item", language["item.advancedrocketrycommunity.fixture"])

    def test_tampered_target_is_rejected(self) -> None:
        MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        target = self.repository / "src/main/resources/assets/advancedrocketrycommunity/textures/item/target.png"
        target.write_bytes(b"tampered")
        errors = MODULE.verify(self.repository, self.upstream, self.plan, self.record)
        self.assertTrue(any("target bytes differ" in error for error in errors))

    def test_approved_review_is_preserved_only_for_bound_digest(self) -> None:
        record = MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        record["review"].update(
            {"status": "APPROVED", "reviewer": "reviewer", "reviewed_at": "2026-08-30"}
        )
        self.record.write_bytes(MODULE.pretty_json(record))
        preserved = MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        self.assertEqual("APPROVED", preserved["review"]["status"])

        plan = json.loads(self.plan.read_text(encoding="utf-8"))
        plan["entries"][0]["transformation"].append("new transformation")
        self.plan.write_bytes(MODULE.pretty_json(plan))
        reset = MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        self.assertEqual("PENDING_HUMAN_REVIEW", reset["review"]["status"])

    def test_pending_review_cannot_prefill_reviewer(self) -> None:
        record = MODULE.generate(self.repository, self.upstream, self.plan, self.record)
        record["review"]["reviewer"] = "not-allowed"
        self.record.write_bytes(MODULE.pretty_json(record))
        with self.assertRaisesRegex(ValueError, "must not prefill"):
            MODULE.verify(self.repository, self.upstream, self.plan, self.record)

    def test_duplicate_casefolded_target_is_rejected(self) -> None:
        plan = json.loads(self.plan.read_text(encoding="utf-8"))
        duplicate = dict(plan["entries"][0])
        duplicate["target_path"] = duplicate["target_path"].upper()
        plan["entries"].append(duplicate)
        with self.assertRaisesRegex(ValueError, "outside the project asset roots|duplicates"):
            MODULE.validate_plan(plan)


if __name__ == "__main__":
    unittest.main()
