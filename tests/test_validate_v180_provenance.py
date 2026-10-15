from __future__ import annotations

import hashlib
import json
import shutil
import subprocess
import tempfile
import unittest
import zipfile
from contextlib import redirect_stderr, redirect_stdout
from io import StringIO
from pathlib import Path

from scripts import validate_v180_provenance as provenance

MOD = "advancedrocketrycommunity"
LEGACY_CIRCUIT = b"\x89PNG legacy circuit texture"
LEGACY_LENS = b"\x89PNG legacy lens texture"
# The v0.1.0 managed root, its DataGen root, a superseded v1.2 copy, the v1.8 DataGen root and the v1.8 import root.
CIRCUIT = f"src/main/resources/assets/{MOD}/textures/item/circuit.png"
PACK = "src/main/resources/pack.mcmeta"
MODEL = f"src/generated/resources/assets/{MOD}/models/item/circuit.json"
OLD_RECIPE = f"src/generated/v1.2/resources/data/{MOD}/recipes/plate.json"
RECIPE = f"src/generated/v1.8/resources/data/{MOD}/recipes/plate.json"
BRICK = f"src/generated/v1.8/resources/assets/{MOD}/textures/item/brick.png"
LANG = f"src/generated/v1.8/resources/assets/{MOD}_v180/lang/en_us.json"
LENS = f"src/imported/v1.8/resources/assets/{MOD}/textures/item/lens.png"
ROOTS = ("src/main/resources", "src/generated/resources", "src/generated/v1.2/resources",
         "src/generated/v1.8/resources", "src/imported/v1.8/resources")
FIRST, REDRAW = "docs/provenance/v1.8.0-first.json", "docs/provenance/v1.8.0-redraw.json"
BRICK_FIRST, BRICK_REDRAWN = b"\x89PNG first brick drawing", b"\x89PNG redrawn brick"
LANG_V1 = {f"item.{MOD}.brick": "Brick"}
LANG_V2 = {**LANG_V1, f"item.{MOD}.lens": "Lens"}
LANG_V3 = {**LANG_V2, f"item.{MOD}.plate": "Plate"}


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def language(values: dict[str, str]) -> bytes:
    return json.dumps(values, ensure_ascii=False, sort_keys=True, indent=2).encode("utf-8")


def entry(target: str, data: bytes, status: str = "NEW", **extra: object) -> dict[str, object]:
    return {"target_path": target, "target_sha256": sha256(data), "status": status, **extra}


class Repository:
    """A git work tree with three commits: the first records, a redraw that supersedes the brick, and a grown
    language file that no record names."""

    def __init__(self, base: Path):
        self.base = base
        self.root = base / "repository"
        self.root.mkdir()
        for command in (("init", "-q"), ("config", "user.name", "Fixture"), ("config", "user.email", "f@example.invalid"),
                        ("config", "commit.gpgsign", "false"), ("config", "core.autocrlf", "false")):
            self.git(*command)
        self.write("legacy-manifest/assets.csv", "source_path,kind,sha256\n"
                   f"src/main/resources/assets/advancedrocketry/textures/items/circuit.png,texture,{sha256(LEGACY_CIRCUIT)}\n"
                   f"src/main/resources/assets/advancedrocketry/textures/items/lens.png,texture,{sha256(LEGACY_LENS)}\n")
        model, recipe = b'{"parent": "item/generated"}', b'{"type": "minecraft:crafting_shaped"}'
        for path, data in ((CIRCUIT, LEGACY_CIRCUIT), (PACK, b'{"pack": {}}'), (MODEL, model),
                           (OLD_RECIPE, b'{"type": "old"}'), (RECIPE, recipe), (BRICK, BRICK_FIRST),
                           (LANG, language(LANG_V1)), (LENS, LEGACY_LENS)):
            self.write(path, data)
        self.record("docs/provenance/v0.1.0-minimal-content.json",
                    [entry(CIRCUIT, LEGACY_CIRCUIT, "UPSTREAM_AR_MIT")], schema_version=1)
        self.write("docs/provenance/v0.1.0-generated-resources.json", json.dumps({
            "generated_root": "src/generated/resources", "schema_version": 1, "status": "GENERATED",
            "targets": [{"path": MODEL, "sha256": sha256(model), "bytes": len(model)}]}))
        self.write("docs/provenance/v1.8.0-notes.md", "# A Markdown record\n")
        self.record(FIRST, [entry(BRICK, BRICK_FIRST), entry(LANG, language(LANG_V1)),
                            entry(LENS, LEGACY_LENS, "UPSTREAM_AR_MIT")])
        self.commit("first records")
        self.write(BRICK, BRICK_REDRAWN)
        self.write(LANG, language(LANG_V2))
        self.record(REDRAW, [entry(BRICK, BRICK_REDRAWN),
                             entry(LANG, language(LANG_V2), new_keys=[f"item.{MOD}.lens"])])
        self.commit("redraw the brick and add the lens name")
        self.write(LANG, language(LANG_V3))
        self.commit("grow the language file")

    def git(self, *args: str) -> str:
        return subprocess.run(["git", "-C", str(self.root), *args], check=True, stdout=subprocess.PIPE,
                              stderr=subprocess.PIPE, text=True).stdout

    def write(self, relative: str, data: bytes | str) -> None:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_bytes(data.encode("utf-8") if isinstance(data, str) else data)

    def record(self, relative: str, entries: list[dict[str, object]], **top: object) -> None:
        self.write(relative, json.dumps({"schema_version": 2, "batch_id": Path(relative).stem, **top,
                                         "entries": entries, "review": {"status": "PENDING_HUMAN_REVIEW"}}))

    def commit(self, message: str) -> None:
        self.git("add", "--all")
        self.git("commit", "-q", "-m", message)

    def jar(self, extra: dict[str, bytes] | None = None, skip: tuple[str, ...] = ()) -> Path:
        """The packaged roots without the superseded v1.2 copy, as the build excludes it, plus non-resource entries."""
        entries = {"META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\n", "io/example/Mod.class": b"\xca\xfe\xba\xbe"}
        for root in ROOTS:
            for path in sorted((self.root / root).rglob("*")):
                relative = f"{root}/{path.relative_to(self.root / root).as_posix()}"
                if path.is_file() and relative != OLD_RECIPE and relative not in skip:
                    entries[path.relative_to(self.root / root).as_posix()] = path.read_bytes()
        entries.update(extra or {})
        jar = self.base / "mod.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return jar

    def audit(self, jar: Path | None | bool = True, **jar_options: object) -> dict:
        path = self.jar(**jar_options) if jar is True else jar or None
        return provenance.audit_repository(self.root, path)


def failures(result: dict, rule: int) -> list[str]:
    return result["input_failures"] if rule == 0 else result["rules"][rule - 1]["failures"]


def results(result: dict) -> dict[int, str]:
    return {rule["rule"]: rule["result"] for rule in result["rules"]}


class ProvenanceAuditTestCase(unittest.TestCase):
    def setUp(self) -> None:
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repository = Repository(Path(directory.name))

    def assertOnlyFailure(self, result: dict, rule: int, pattern: str) -> None:
        self.assertEqual("FAIL", result["result"])
        self.assertTrue(failures(result, rule), f"rule {rule} must fail")
        for message in failures(result, rule):
            self.assertRegex(message, pattern)
        others = {other: failures(result, other) for other in range(6) if other != rule and failures(result, other)}
        self.assertEqual({}, others)


class CleanRepositoryTests(ProvenanceAuditTestCase):
    def test_clean_tree_and_jar_pass_every_rule(self) -> None:
        result = self.repository.audit()

        self.assertEqual("PASS", result["result"], json.dumps(result["rules"], indent=1))
        self.assertEqual({1: "PASS", 2: "PASS", 3: "PASS", 4: "PASS", 5: "PASS"}, results(result))
        self.assertEqual({"src/generated/resources": 1, "src/generated/v1.8/resources": 3,
                          "src/imported/v1.8/resources": 1, "src/main/resources": 1,
                          "outside rule 1 (not assets/ or data/)": 3}, result["jar_origins"])
        self.assertEqual(["docs/provenance/v1.8.0-notes.md"], result["markdown_records"])

    def test_superseded_target_is_compared_with_the_later_record(self) -> None:
        result = self.repository.audit()

        brick = next(row for row in result["targets"] if row["target"] == BRICK)
        self.assertEqual("v1.8.0-redraw.json entries[0]", brick["latest"])
        self.assertEqual(["v1.8.0-first.json entries[0]"], brick["superseded"])
        self.assertEqual("PASS", brick["result"])

    def test_grown_language_file_is_checked_by_key(self) -> None:
        result = self.repository.audit()

        rows = {row["record"]: row for row in result["language_keys"]}
        self.assertEqual("PASS", rows["v1.8.0-first.json entries[1]"]["result"])
        self.assertTrue(rows["v1.8.0-first.json entries[1]"]["keys_from"].startswith("commit "))
        self.assertEqual(("new_keys", 1, "PASS"), tuple(rows["v1.8.0-redraw.json entries[1]"][key]
                                                         for key in ("keys_from", "keys", "result")))
        self.assertNotIn(LANG, [row["target"] for row in result["targets"]])

    def test_legacy_hashes_are_found_in_the_tree_and_the_jar(self) -> None:
        result = self.repository.audit()

        hits = {row["file"]: row for row in result["legacy_hits"]}
        self.assertEqual({CIRCUIT, LENS}, set(hits))
        self.assertEqual(["assets/advancedrocketrycommunity/textures/item/lens.png"], hits[LENS]["packaged_as"])
        self.assertEqual("UPSTREAM_AR_MIT", hits[LENS]["status"])

    def test_without_a_jar_rules_one_and_three_are_not_run(self) -> None:
        result = self.repository.audit(jar=None)

        self.assertEqual("PASS", result["result"])
        self.assertEqual({1: "NOT_RUN", 2: "PASS", 3: "NOT_RUN", 4: "PASS", 5: "PASS"}, results(result))
        self.assertIsNone(result["jar"])

    def test_main_writes_the_index_and_the_json_result(self) -> None:
        output = self.repository.base / "evidence"
        jar = self.repository.jar()
        with redirect_stdout(StringIO()) as printed, redirect_stderr(StringIO()):
            code = provenance.main(["--repository-root", str(self.repository.root), "--jar", str(jar),
                                    "--output-dir", str(output)])

        self.assertEqual(0, code)
        self.assertIn("[PASS] v1.8 provenance audit", printed.getvalue())
        result = json.loads((output / provenance.RESULT_NAME).read_text(encoding="utf-8"))
        self.assertEqual(("PASS", "mod.jar", sha256(jar.read_bytes())),
                         (result["result"], result["jar"]["name"], result["jar"]["sha256"]))
        self.assertEqual(self.repository.git("rev-parse", "HEAD").strip(), result["repository"]["commit"])
        index = (output / provenance.INDEX_NAME).read_text(encoding="utf-8")
        self.assertIn("Result: **PASS**", index)
        self.assertIn(f"`{BRICK}`", index)

    def test_main_exits_one_and_reports_a_failure(self) -> None:
        self.repository.write("docs/copy-of-circuit.png", LEGACY_CIRCUIT)
        with redirect_stdout(StringIO()), redirect_stderr(StringIO()) as errors:
            code = provenance.main(["--repository-root", str(self.repository.root)])

        self.assertEqual(1, code)
        self.assertIn("docs/copy-of-circuit.png", errors.getvalue())


class SourceRootTests(ProvenanceAuditTestCase):
    def test_entry_with_bytes_from_no_root_fails(self) -> None:
        jar = self.repository.jar(extra={f"assets/{MOD}/models/item/circuit.json": b'{"parent": "edited"}'})

        self.assertOnlyFailure(self.repository.audit(jar), 1, "comes from no source root; the path exists in "
                                                              "src/generated/resources with other bytes")

    def test_entry_from_an_unlisted_root_fails(self) -> None:
        self.repository.write(f"src/extra/resources/data/{MOD}/recipes/extra.json", b"{}")

        result = self.repository.audit(extra={f"data/{MOD}/recipes/extra.json": b"{}"})

        self.assertOnlyFailure(result, 1, "extra.json comes from no source root\\.$")

    def test_identical_bytes_in_two_roots_are_ambiguous(self) -> None:
        self.repository.write(OLD_RECIPE, (self.repository.root / RECIPE).read_bytes())

        self.assertOnlyFailure(self.repository.audit(), 1, "identical bytes in 2 source roots")

    def test_entries_outside_assets_and_data_are_not_placed(self) -> None:
        result = self.repository.audit(extra={"META-INF/extra.txt": b"anything"})

        self.assertEqual("PASS", result["result"])
        self.assertEqual(4, result["jar_origins"]["outside rule 1 (not assets/ or data/)"])


class LegacyHashTests(ProvenanceAuditTestCase):
    def test_unrecorded_tree_file_with_a_legacy_hash_fails(self) -> None:
        self.repository.write("src/test/resources/fixture.png", LEGACY_LENS)

        self.assertOnlyFailure(self.repository.audit(), 2, "^src/test/resources/fixture.png has the SHA-256 of "
                                                           "legacy asset .*items/lens.png, and no record names it")

    def test_packaged_legacy_bytes_without_a_source_file_fail(self) -> None:
        result = self.repository.audit(extra={f"assets/{MOD}/textures/item/stray.png": LEGACY_CIRCUIT})

        self.assertEqual("FAIL", result["result"])
        self.assertRegex(failures(result, 1)[0], "stray.png comes from no source root")
        self.assertRegex(failures(result, 2)[0], "stray.png has the SHA-256 of legacy asset .* no single source")

    def test_legacy_bytes_recorded_as_new_fail(self) -> None:
        self.repository.write(BRICK, LEGACY_CIRCUIT)
        self.repository.record(REDRAW, [entry(BRICK, LEGACY_CIRCUIT),
                                        entry(LANG, language(LANG_V2), new_keys=[f"item.{MOD}.lens"])])

        self.assertOnlyFailure(self.repository.audit(), 2, "brick.png has the bytes of legacy asset .* status NEW")


class BlockedStatusTests(ProvenanceAuditTestCase):
    def quarantine_lens(self) -> None:
        self.repository.record(FIRST, [entry(BRICK, BRICK_FIRST), entry(LANG, language(LANG_V1)),
                                       entry(LENS, LEGACY_LENS, "QUARANTINED")])

    def test_packaged_quarantined_target_fails(self) -> None:
        self.quarantine_lens()

        self.assertOnlyFailure(self.repository.audit(), 3, "lens.png packages .*lens.png, whose latest record "
                                                           "v1.8.0-first.json entries\\[2\\] is QUARANTINED")

    def test_quarantined_target_that_is_not_packaged_passes(self) -> None:
        self.quarantine_lens()

        result = self.repository.audit(skip=(LENS,))

        self.assertEqual("PASS", result["result"])
        self.assertEqual("QUARANTINED", {row["file"]: row for row in result["legacy_hits"]}[LENS]["status"])

    def test_rejected_bytes_packaged_under_another_name_fail(self) -> None:
        rejected = f"src/test/resources/{MOD}/rejected.png"
        self.repository.write(rejected, b"rejected art")
        self.repository.record("docs/provenance/v1.8.0-rejected.json", [entry(rejected, b"rejected art", "REJECTED")])

        result = self.repository.audit(extra={f"assets/{MOD}/textures/item/renamed.png": b"rejected art"})

        self.assertEqual("FAIL", result["result"])
        self.assertRegex(failures(result, 3)[0], "renamed.png has the bytes of .*rejected.png, which "
                                                 "v1.8.0-rejected.json entries\\[0\\] marks REJECTED")


class LatestRecordTests(ProvenanceAuditTestCase):
    def test_file_that_differs_from_its_latest_record_fails(self) -> None:
        self.repository.write(BRICK, BRICK_FIRST)

        self.assertOnlyFailure(self.repository.audit(), 4, f"^{BRICK} has SHA-256 {sha256(BRICK_FIRST)}, not the "
                                                           f"{sha256(BRICK_REDRAWN)} of its latest record "
                                                           "v1.8.0-redraw.json entries\\[0\\]")

    def test_missing_target_fails(self) -> None:
        (self.repository.root / MODEL).unlink()

        result = self.repository.audit(jar=None)

        self.assertOnlyFailure(result, 4, "circuit.json is named by v0.1.0-generated-resources.json targets\\[0\\] "
                                          "but is missing")

    def test_explicit_supersession_inside_one_record_passes(self) -> None:
        self.repository.record(REDRAW, [
            entry(BRICK, BRICK_FIRST), entry(BRICK, BRICK_REDRAWN, supersedes_target_sha256=sha256(BRICK_FIRST)),
            entry(LANG, language(LANG_V2), new_keys=[f"item.{MOD}.lens"])])
        self.repository.commit("one record names both drawings")

        result = self.repository.audit()

        self.assertEqual("PASS", result["result"])
        brick = next(row for row in result["targets"] if row["target"] == BRICK)
        self.assertEqual("v1.8.0-redraw.json entries[1]", brick["latest"])

    def test_disagreeing_records_added_in_one_commit_fail(self) -> None:
        self.repository.record(REDRAW, [entry(BRICK, BRICK_FIRST), entry(BRICK, BRICK_REDRAWN),
                                        entry(LANG, language(LANG_V2), new_keys=[f"item.{MOD}.lens"])])

        self.assertOnlyFailure(self.repository.audit(), 4, "records added in the same commit name different SHA-256")

    def test_related_entry_re_pins_a_target_with_the_cited_status(self) -> None:
        self.repository.write("docs/provenance/v1.8.0-relink.json", json.dumps({
            "schema_version": 2, "entries": [],
            "related_entries": [{"record": REDRAW, "target_path": BRICK, "target_sha256": sha256(BRICK_REDRAWN)}]}))

        result = self.repository.audit()

        self.assertEqual("PASS", result["result"])
        brick = next(row for row in result["targets"] if row["target"] == BRICK)
        self.assertEqual(("v1.8.0-relink.json related_entries[0]", "NEW"), (brick["latest"], brick["status"]))

    def test_related_entry_citing_a_record_without_the_target_fails(self) -> None:
        self.repository.write("docs/provenance/v1.8.0-relink.json", json.dumps({
            "schema_version": 2, "entries": [],
            "related_entries": [{"record": FIRST, "target_path": RECIPE, "target_sha256": "0" * 64}]}))

        self.assertOnlyFailure(self.repository.audit(), 4, "cites docs/provenance/v1.8.0-first.json, which does "
                                                           "not name .*plate.json")

    def test_malformed_record_entry_is_an_input_failure(self) -> None:
        self.repository.record("docs/provenance/v1.8.0-broken.json", [{"target_path": "../outside.png",
                                                                        "target_sha256": "0" * 64}])

        self.assertOnlyFailure(self.repository.audit(), 0, "does not name a safe target path")

    def test_order_comes_from_supersession_or_history_only(self) -> None:
        old = provenance.Naming("docs/provenance/a.json", "entries[0]", BRICK, "1" * 64, "NEW")
        new = provenance.Naming("docs/provenance/b.json", "entries[0]", BRICK, "2" * 64, "NEW")

        self.assertEqual((None, "its records name different SHA-256 values and HEAD's history does not order "
                                "them"), provenance.resolve([old, new], {}))
        self.assertEqual(new, provenance.resolve([old, new], {old.record: -1, new.record: 0})[0])
        explicit = provenance.Naming(old.record, "entries[1]", BRICK, "3" * 64, "NEW", frozenset({"1" * 64, "2" * 64}))
        self.assertEqual(explicit, provenance.resolve([old, new, explicit], {})[0])


class LanguageKeyTests(ProvenanceAuditTestCase):
    def test_removed_vouched_key_fails(self) -> None:
        self.repository.write(LANG, language({key: value for key, value in LANG_V3.items() if "lens" not in key}))

        self.assertOnlyFailure(self.repository.audit(), 5, f"key item.{MOD}.lens, vouched for by "
                                                           "v1.8.0-redraw.json entries\\[1\\], is missing")

    def test_changed_vouched_value_fails_and_an_unvouched_change_passes(self) -> None:
        self.repository.write(LANG, language({**LANG_V3, f"item.{MOD}.plate": "Iron Plate"}))
        self.assertEqual("PASS", self.repository.audit()["result"])

        self.repository.write(LANG, language({**LANG_V3, f"item.{MOD}.brick": "Clay Brick"}))
        self.assertOnlyFailure(self.repository.audit(), 5, f"key item.{MOD}.brick no longer has the value "
                                                           "recorded by v1.8.0-first.json entries\\[1\\]")

    def test_later_record_re_vouches_a_changed_value(self) -> None:
        changed = {**LANG_V3, f"item.{MOD}.brick": "Clay Brick"}
        self.repository.write(LANG, language(changed))
        self.repository.commit("rename the brick")
        self.repository.record("docs/provenance/v1.8.0-rename.json", [entry(LANG, language(changed))])
        self.repository.commit("record the rename")

        result = self.repository.audit()

        self.assertEqual("PASS", result["result"])
        rename = next(row for row in result["language_keys"] if row["record"] == "v1.8.0-rename.json entries[0]")
        self.assertEqual(1, rename["keys"])

    def test_new_keys_absent_from_the_recorded_version_fail(self) -> None:
        self.repository.record(REDRAW, [entry(BRICK, BRICK_REDRAWN),
                                        entry(LANG, language(LANG_V2), new_keys=[f"item.{MOD}.plate"])])

        self.assertOnlyFailure(self.repository.audit(), 5, "lists keys that its recorded version .* lacks: "
                                                           f"item.{MOD}.plate")

    def test_recorded_version_missing_from_history_fails(self) -> None:
        self.repository.record(REDRAW, [entry(BRICK, BRICK_REDRAWN),
                                        entry(LANG, language({"never": "committed"}), new_keys=["never"])])

        self.assertOnlyFailure(self.repository.audit(), 5, "cannot be found \\(no commit in HEAD's history has "
                                                           "those bytes\\)")

    def test_version_made_only_by_a_merge_needs_new_keys(self) -> None:
        repository, trunk = self.repository, self.repository.git("rev-parse", "--abbrev-ref", "HEAD").strip()
        repository.git("checkout", "-q", "-b", "side")
        repository.write(LANG, language({**LANG_V3, "side": "Side"}))
        repository.commit("side key")
        repository.git("checkout", "-q", trunk)
        repository.write(LANG, language({**LANG_V3, "trunk": "Trunk"}))
        repository.commit("trunk key")
        repository.git("merge", "-q", "--no-ff", "--no-commit", "-s", "ours", "side")
        merged = language({**LANG_V3, "side": "Side", "trunk": "Trunk"})
        repository.write(LANG, merged)
        repository.commit("merge both keys")
        repository.record("docs/provenance/v1.8.0-merged.json", [entry(LANG, merged)])

        self.assertOnlyFailure(repository.audit(), 5, "merged.json entries\\[0\\]: no ordinary commit produced .* "
                                                      "\\(only a merge produced it\\).* list them in new_keys")
        repository.record("docs/provenance/v1.8.0-merged.json", [entry(LANG, merged, new_keys=["side"])])
        self.assertEqual("PASS", repository.audit()["result"])

    def test_without_git_history_a_grown_file_cannot_be_checked(self) -> None:
        copy = self.repository.base / "export"
        shutil.copytree(self.repository.root, copy, ignore=shutil.ignore_patterns(".git"))

        result = provenance.audit_repository(copy, None)

        self.assertEqual("directory walk (no git work tree)", result["repository"]["tree_source"])
        self.assertEqual("FAIL", result["result"])
        self.assertTrue(all("git is unavailable" in message for message in failures(result, 5)), failures(result, 5))
        self.assertTrue(failures(result, 4), "without history the two brick records cannot be ordered")


if __name__ == "__main__":
    unittest.main()
