from __future__ import annotations

import contextlib
import copy
import importlib.util
import io
import json
import shutil
import struct
import subprocess
import tempfile
import unittest
import zipfile
import zlib
from pathlib import Path

REPOSITORY = Path(__file__).resolve().parents[1]


def _load(name: str, relative: str):
    spec = importlib.util.spec_from_file_location(name, REPOSITORY / relative)
    assert spec is not None and spec.loader is not None
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


MODULE = _load("import_v180_assets", "tools/import/import_v180_assets.py")
V010 = _load("import_v010_assets_for_v180_tests", "tools/import/import_v010_assets.py")
from tools.audit import vanilla_derivation as derivation  # noqa: E402

LEGACY = "src/main/resources/assets/advancedrocketry/"
TARGET = "src/main/resources/assets/advancedrocketrycommunity/"
ADR = "docs/decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md"
COVERAGE = "docs/work/fixture-asset-coverage.csv"
NOTICE_NEW = "Copyright (c) 2026 Advanced Rocketry: Community Edition contributors"
MIT = "MIT License\n\nCopyright (c) 2017\n"

WIDGET_OBJ = (
    "# Fixture model written by the test\n"
    "mtllib widget.mtl\n"
    "o Body\n"
    "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nv 0 0 1\nv 1 0 1\n"
    "vt 0 0\nvt 1 0\nvt 1 1\nvt 0 1\n"
    "vn 0 0 -1\n"
    "usemtl Body\n"
    "s off\n"
    "f 1/1/1 2/2/1 3/3/1 4/4/1\n"
    "f 1/1/1 2/2/1 6/3/1 5/4/1\n"
)
WIDGET_MTL = "# Fixture material\nnewmtl Body\nKa 0 0 0\nKd 1 1 1\nd 1\nillum 2\nmap_Kd advancedrocketry:models/widget\n"
NEW_MTL = "newmtl Body\nKd 1 1 1\nmap_Kd advancedrocketrycommunity:models/widget\n"
GERMAN = "# German fixture\nitem.widget.name=Widget\nitem.gadget.name=Gerät\ntile.old.name=Alt\ngui.unused=Unbenutzt\n"


def _noise(seed: int, width: int = 16, height: int = 16) -> list[tuple[int, int, int, int]]:
    state, palette = seed, []
    for _ in range(24):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        palette.append((state & 0xFF, (state >> 8) & 0xFF, (state >> 16) & 0xFF, 255))
    pixels = []
    for _ in range(width * height):
        state = (state * 1103515245 + 12345) & 0x7FFFFFFF
        pixels.append(palette[state % 24])
    return pixels


def _png(pixels: list[tuple[int, int, int, int]], width: int = 16, height: int = 16) -> bytes:
    raw = b"".join(b"\x00" + bytes(c for pixel in pixels[y * width:(y + 1) * width] for c in pixel)
                   for y in range(height))

    def chunk(kind: bytes, body: bytes) -> bytes:
        return struct.pack(">I", len(body)) + kind + body + struct.pack(">I", zlib.crc32(kind + body) & 0xFFFFFFFF)

    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b""))


STONE = _png(_noise(101))
SUSPECT = _noise(41)
SIBLING = list(SUSPECT)
SIBLING[0] = (1, 2, 3, 255)
UPSTREAM_FILES = {
    "models/widget.obj": WIDGET_OBJ.encode(),
    "models/widget.mtl": WIDGET_MTL.encode(),
    "models/huge.obj": ("mtllib widget.mtl\n" + "v 0 0 0\n" * 65_537).encode(),
    "textures/models/widget.png": _png(_noise(11)),
    "textures/items/Gadget.png": _png(_noise(23)),
    "textures/items/stonecopy.png": STONE,
    "textures/items/suspect.png": _png(SUSPECT),
    "textures/items/sibling.png": _png(SIBLING),
    "lang/de_DE.lang": GERMAN.encode(),
}
OWNERS = {"models/widget.obj": "block:widget", "models/widget.mtl": "block:widget",
          "textures/models/widget.png": "block:widget", "textures/items/Gadget.png": "item:gadget"}


def _git(directory: Path, *args: str) -> str:
    return subprocess.run(["git", "-C", str(directory), *args], check=True, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, text=True).stdout


def _write(path: Path, data: bytes | str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data.encode("utf-8") if isinstance(data, str) else data)


class Fixture:
    """An upstream clone, two client JARs and a repository root, all made by code, built once per class."""

    def __init__(self, base: Path):
        self.base = base
        self.upstream = base / "upstream"
        self.template = base / "template"
        self.make_upstream()
        self.vanilla = [self.jar("1.12.2", {"assets/minecraft/textures/blocks/stone.png": STONE}),
                        self.jar("1.20.1", {"assets/minecraft/textures/block/stone.png": STONE,
                                            "assets/minecraft/textures/block/dirt.png": _png(_noise(103))})]
        self.make_template()

    def make_upstream(self) -> None:
        self.upstream.mkdir()
        _git(self.upstream, "init", "-q")
        _git(self.upstream, "config", "user.name", "Upstream Fixture")
        _git(self.upstream, "config", "user.email", "upstream@example.invalid")
        _write(self.upstream / "LICENSE", MIT)
        for relative, data in UPSTREAM_FILES.items():
            _write(self.upstream / (LEGACY + relative), data)
        self.commit("Add the widget model, textures and translations")
        _write(self.upstream / (LEGACY + "textures/items/sixteen.png"), _png(_noise(37)))
        self.commit("16x textures and four new planet textures added (#1811)")
        trunk = _git(self.upstream, "rev-parse", "--abbrev-ref", "HEAD").strip()
        _git(self.upstream, "checkout", "-q", "-b", "contributor")
        _write(self.upstream / (LEGACY + "textures/items/gizmo.png"), _png(_noise(43)))
        self.commit("Add gizmo texture")
        _git(self.upstream, "checkout", "-q", trunk)
        _git(self.upstream, "merge", "-q", "--no-ff", "-m", "Merge pull request #1809 from contributor/16x", "contributor")
        self.commit_sha = _git(self.upstream, "rev-parse", "HEAD").strip()

    def commit(self, message: str) -> None:
        _git(self.upstream, "add", "--all")
        _git(self.upstream, "commit", "-q", "-m", message)

    def jar(self, label: str, entries: dict[str, bytes]) -> str:
        path = self.base / f"client-{label}.jar"
        with zipfile.ZipFile(path, "w") as archive:
            for name, data in entries.items():
                archive.writestr(name, data)
        return f"{label}={path}"

    def assets(self) -> list[str]:
        return sorted(path.relative_to(self.upstream / LEGACY).as_posix()
                      for path in (self.upstream / LEGACY).rglob("*") if path.is_file())

    def make_template(self) -> None:
        root = self.template
        _write(root / "LICENSE", "MIT License\n\nCopyright (c) 2017\n" + NOTICE_NEW + "\n")
        _write(root / "UPSTREAM.md", "```text\nrepository: https://github.com/Advanced-Rocketry/AdvancedRocketry\n"
                                     f"primary_branch: 1.12\nlicense: MIT\nupstream_commit: {self.commit_sha}\n```\n")
        digests = {asset: derivation.sha256((self.upstream / (LEGACY + asset)).read_bytes()) for asset in self.assets()}
        _write(root / "legacy-manifest/assets.csv", "source_path,kind,sha256\n" + "".join(
            f"{LEGACY}{asset},asset,{digest}\n" for asset, digest in digests.items()))
        _write(root / COVERAGE, "source_path,source_sha256,handling,owning_units\n" + "".join(
            f"{LEGACY}{asset},{digest},IMPORT,{OWNERS.get(asset, '-')}\n" for asset, digest in digests.items()))
        self.rows = {asset: number for number, asset in enumerate(digests, start=1)}
        self.digests = digests
        _write(root / "docs/work/v1.8.0-asset-plan.csv",
               "order,pattern,handling,plan,units,reason\n"
               "1,textures/items/stonecopy.png,EXCLUDE,-,-,vanilla-derived\n"
               "2,textures/items/suspect.png,REVIEW,C18d,-,origin review\n"
               "3,*,IMPORT,C18d,-,fixture batch\n")
        _write(root / "docs/provenance/v1.8.0-origin-findings.json", '{"schema_version": 1, "findings": []}\n')
        _write(root / "docs/work/v1.8.0-content-ledger.csv",
               "unit_id,disposition\nblock:widget,PLANNED\nitem:gadget,IMPLEMENTED\nblock:old,DEFERRED\n")
        results = derivation.render(derivation.analyse(self.upstream, self.vanilla, root))
        _write(root / "docs/work/v1.8.0-vanilla-derivation.json", results)
        jars = {spec.partition("=")[0]: derivation.sha256(Path(spec.partition("=")[2]).read_bytes())
                for spec in self.vanilla}
        _write(root / ADR, "# ADR-061 fixture\n\n```yaml\n"
                           f"derivation_results_sha256: {derivation.sha256(results)}\n"
                           "vanilla_client_sha256:\n"
                           f"  \"1.12.2\": {jars['1.12.2']}\n  \"1.20.1\": {jars['1.20.1']}\n```\n")
        _git(root, "init", "-q")
        _git(root, "config", "user.name", "Project Fixture")
        _git(root, "config", "user.email", "project@example.invalid")
        _git(root, "add", "--all")
        _git(root, "commit", "-q", "-m", "base")
        self.base_commit = _git(root, "rev-parse", "HEAD").strip()
        _write(root / (TARGET + "models/block/widget_new.mtl"), NEW_MTL)
        _write(root / (TARGET + "textures/item/drawn.png"), _png(_noise(103)))
        _git(root, "add", "--all")
        _git(root, "commit", "-q", "-m", "Draw the NEW widget material")
        self.authoring_commit = _git(root, "rev-parse", "HEAD").strip()


class ImportV180AssetsTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.temporary_class = tempfile.TemporaryDirectory()
        cls.fixture = Fixture(Path(cls.temporary_class.name))

    @classmethod
    def tearDownClass(cls) -> None:
        cls.temporary_class.cleanup()

    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.root = Path(self.temporary.name) / "project"
        shutil.copytree(self.fixture.template, self.root)
        self.plan_path = self.root / "tools/import/v180-fixture-plan.json"
        self.record = self.root / "docs/provenance/v1.8.0-fixture.json"
        self.plan = self.base_plan()
        self.save_plan()

    def tearDown(self) -> None:
        self.temporary.cleanup()

    def entry(self, asset: str, target: str, transformation: dict) -> dict:
        return {"source": "ar", "source_path": LEGACY + asset, "source_sha256": self.fixture.digests[asset],
                "target_path": TARGET + target, "transformation": transformation,
                "units": [OWNERS[asset]] if asset in OWNERS else [], "asset_coverage_row": self.fixture.rows[asset]}

    def base_plan(self) -> dict:
        return {
            "schema_version": 1,
            "batch_id": "v1.8.0-fixture-01",
            "target_version": "v1.8.0",
            "asset_coverage": COVERAGE,
            "sources": {"ar": {"status": "UPSTREAM_AR_MIT",
                               "source_repository": "https://github.com/Advanced-Rocketry/AdvancedRocketry",
                               "source_branch": "1.12", "source_commit": self.fixture.commit_sha, "license": "MIT",
                               "copyright_notice": "Copyright (c) 2017"}},
            "entries": [
                self.entry("models/widget.obj", "models/block/widget.obj",
                           {"kind": "obj", "mtllib": {"widget.mtl": "widget.mtl"}, "usemtl": {"Body": "Body"}}),
                self.entry("models/widget.mtl", "models/block/widget.mtl", {"kind": "mtl", "textures": {
                    "advancedrocketry:models/widget": "advancedrocketrycommunity:models/widget"}}),
                self.entry("textures/models/widget.png", "textures/models/widget.png", {"kind": "copy"}),
                self.entry("textures/items/Gadget.png", "textures/item/gadget.png", {"kind": "rename"}),
                self.entry("lang/de_DE.lang", "lang/de_de.json", {"kind": "lang", "keys": {
                    "item.widget.name": "block.advancedrocketrycommunity.widget",
                    "item.gadget.name": "item.advancedrocketrycommunity.gadget"},
                    "discard": ["tile.old.name", "gui.unused"]}),
            ],
        }

    def save_plan(self) -> None:
        _write(self.plan_path, V010.pretty_json(self.plan))

    def generate(self) -> dict:
        return MODULE.generate(self.root, self.fixture.upstream, self.plan_path, self.record, self.fixture.vanilla)

    def verify(self) -> list[str]:
        return MODULE.verify(self.root, self.fixture.upstream, self.plan_path, self.record, self.fixture.vanilla)

    def tree(self) -> dict[str, bytes]:
        return {path.relative_to(self.root).as_posix(): path.read_bytes() for path in self.root.rglob("*")
                if path.is_file() and ".git" not in path.relative_to(self.root).parts}

    def assert_fails(self, pattern: str, exception: type[Exception] = ValueError) -> None:
        before = self.tree()
        with self.assertRaisesRegex(exception, pattern):
            self.generate()
        self.assertEqual(before, self.tree(), "a failed run must write nothing")
        self.assertFalse(self.record.exists())

    def add(self, entry: dict) -> None:
        self.plan["entries"].append(entry)
        self.save_plan()

    # Round trip and transformations

    def test_generate_then_verify_round_trips_byte_for_byte(self) -> None:
        first = self.generate()
        record_bytes = self.record.read_bytes()
        self.assertEqual([], self.verify())
        self.assertEqual(first, self.generate())
        self.assertEqual(record_bytes, self.record.read_bytes())
        self.assertEqual("PENDING_HUMAN_REVIEW", first["review"]["status"])
        self.assertEqual(2, first["schema_version"])
        self.assertEqual(5, len(first["entries"]))
        for entry in first["entries"]:
            self.assertEqual(V010.sha256((self.root / entry["target_path"]).read_bytes()), entry["target_sha256"])
            self.assertEqual({"source": "CLEAR", "recorded": "CLEAR"},
                             {key: value for key, value in entry["derivation"]["verdicts"].items() if key != "target"})
            self.assertEqual("Add the widget model, textures and translations", entry["upstream_history"][0]["subject"])

    def test_byte_copy_and_rename_keep_the_bytes(self) -> None:
        record = self.generate()
        for asset, target in (("textures/models/widget.png", "textures/models/widget.png"),
                              ("textures/items/Gadget.png", "textures/item/gadget.png")):
            self.assertEqual(UPSTREAM_FILES[asset], (self.root / (TARGET + target)).read_bytes())
        gadget = next(entry for entry in record["entries"] if entry["target_path"].endswith("gadget.png"))
        self.assertIn("renamed from textures/items/Gadget.png", gadget["transformation"][0])

    def test_obj_and_mtl_references_are_rewritten(self) -> None:
        record = self.generate()
        self.assertEqual(WIDGET_OBJ.encode(), (self.root / (TARGET + "models/block/widget.obj")).read_bytes())
        self.assertEqual(WIDGET_MTL.replace("advancedrocketry:", "advancedrocketrycommunity:").encode(),
                         (self.root / (TARGET + "models/block/widget.mtl")).read_bytes())
        model = next(entry for entry in record["entries"] if entry["target_path"].endswith("widget.obj"))
        self.assertEqual({"format": "obj", "positions": 6, "texture_coordinates": 4, "normals": 1, "faces": 2,
                          "baked_vertices": 8, "ignored_lines": 0, "material_library": "widget.mtl",
                          "materials": ["Body"]}, model["format_check"])
        model_files = ("models/block/widget.obj", "models/block/widget.mtl", "textures/models/widget.png")
        self.assertEqual({"vertices": 8, "bytes": sum(len((self.root / (TARGET + path)).read_bytes())
                                                      for path in model_files)}, record["model_set"])

    def test_lang_keys_are_extracted_to_the_named_modern_keys(self) -> None:
        self.generate()
        self.assertEqual('{\n  "block.advancedrocketrycommunity.widget": "Widget",\n'
                         '  "item.advancedrocketrycommunity.gadget": "Gerät"\n}'.encode("utf-8"),
                         (self.root / (TARGET + "lang/de_de.json")).read_bytes())

    def test_several_sources_in_one_record(self) -> None:
        self.plan["sources"]["community"] = {
            "status": "NEW", "source_repository": "https://github.com/sunthemoon/AdvancedRocketry-Community",
            "source_branch": "codex/v1.8.0-fixture", "source_commit": self.fixture.base_commit,
            "authoring_commit": self.fixture.authoring_commit, "license": "MIT", "copyright_notice": NOTICE_NEW}
        self.plan["entries"][0]["transformation"]["mtllib"] = {"widget.mtl": "widget_new.mtl"}
        new_path = TARGET + "models/block/widget_new.mtl"
        self.add({"source": "community", "source_path": new_path,
                  "source_sha256": V010.sha256((self.root / new_path).read_bytes()), "target_path": new_path,
                  "transformation": {"kind": "copy"}, "units": ["block:widget"], "asset_coverage_row": None})
        record = self.generate()
        self.assertEqual([], self.verify())
        self.assertEqual(["ar", "community"], [source["name"] for source in record["sources"]])
        self.assertEqual("Copyright (c) 2017; " + NOTICE_NEW, record["copyright_notice"])
        new = next(entry for entry in record["entries"] if entry["status"] == "NEW")
        self.assertEqual(self.fixture.authoring_commit, new["authoring_commit"])
        self.assertNotIn("upstream_history", new)

    def test_new_source_must_name_its_authoring_parent(self) -> None:
        self.plan["sources"]["community"] = {
            "status": "NEW", "source_repository": "https://github.com/sunthemoon/AdvancedRocketry-Community",
            "source_branch": "codex/v1.8.0-fixture", "source_commit": self.fixture.authoring_commit,
            "authoring_commit": self.fixture.authoring_commit, "license": "MIT", "copyright_notice": NOTICE_NEW}
        new_path = TARGET + "models/block/widget_new.mtl"
        self.add({"source": "community", "source_path": new_path, "source_sha256": "0" * 64,
                  "target_path": new_path, "transformation": {"kind": "copy"}, "units": [], "asset_coverage_row": None})
        with self.assertRaisesRegex(ValueError, "not the parent of its authoring commit"):
            self.generate()

    # Every check failing on purpose

    def test_hash_mismatch_fails(self) -> None:
        self.plan["entries"][2]["source_sha256"] = "0" * 64
        self.save_plan()
        self.assert_fails("differs from the plan's", MODULE.ImportFailure)

    def test_coverage_row_must_name_the_source(self) -> None:
        self.plan["entries"][2]["asset_coverage_row"] = self.fixture.rows["textures/items/Gadget.png"]
        self.save_plan()
        self.assert_fails("asset-coverage row", MODULE.ImportFailure)

    def test_derivation_hit_recorded_and_rerun_fails(self) -> None:
        plan_rules = self.root / "docs/work/v1.8.0-asset-plan.csv"
        plan_rules.write_text(plan_rules.read_text(encoding="utf-8").replace("stonecopy.png,EXCLUDE", "stonecopy.png,IMPORT"),
                              encoding="utf-8")
        self.add(self.entry("textures/items/stonecopy.png", "textures/item/stonecopy.png", {"kind": "rename"}))
        self.assert_fails("derivation verdict HIT", MODULE.ImportFailure)

    def test_derivation_hit_of_a_new_file_is_found_by_the_rerun(self) -> None:
        self.plan["sources"]["community"] = {
            "status": "NEW", "source_repository": "https://github.com/sunthemoon/AdvancedRocketry-Community",
            "source_branch": "codex/v1.8.0-fixture", "source_commit": self.fixture.base_commit,
            "authoring_commit": self.fixture.authoring_commit, "license": "MIT", "copyright_notice": NOTICE_NEW}
        drawn = TARGET + "textures/item/drawn.png"
        self.add({"source": "community", "source_path": drawn, "source_sha256": V010.sha256(_png(_noise(103))),
                  "target_path": drawn, "transformation": {"kind": "copy"}, "units": [], "asset_coverage_row": None})
        self.assert_fails("derivation verdict HIT", MODULE.ImportFailure)

    def test_excluded_or_review_handling_fails(self) -> None:
        self.add(self.entry("textures/items/stonecopy.png", "textures/item/stonecopy.png", {"kind": "rename"}))
        self.assert_fails("handled as EXCLUDE", MODULE.ImportFailure)

    def test_inherited_review_fails(self) -> None:
        self.add(self.entry("textures/items/sibling.png", "textures/item/sibling.png", {"kind": "rename"}))
        self.assert_fails("shares pixels with textures/items/suspect.png, whose REVIEW handling", MODULE.ImportFailure)

    def test_oversized_obj_fails(self) -> None:
        self.add(self.entry("models/huge.obj", "models/huge.obj", {"kind": "copy"}))
        self.assert_fails("more than 65536 vertices", MODULE.ImportFailure)

    def test_model_set_budget_fails(self) -> None:
        quad = "mtllib a.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl A\n" + "f 1 2 3\n" * 16_384
        rendered = {TARGET + f"models/m{index}.obj": quad.encode() for index in range(5)}
        rendered[TARGET + "models/a.mtl"] = b"newmtl A\nmap_Kd advancedrocketrycommunity:block/a\n"
        rendered[TARGET + "textures/block/a.png"] = b"png"
        _, model_set, errors = MODULE.models.check_model_set(rendered, TARGET)
        self.assertEqual(5 * 65_536, model_set["vertices"])
        self.assertTrue(any("over the 262144 budget" in error for error in errors), errors)

    def test_dangling_material_fails(self) -> None:
        self.plan["entries"][0]["transformation"]["usemtl"] = {"Body": "Missing"}
        self.save_plan()
        self.assert_fails("dangling material", MODULE.ImportFailure)

    def test_material_library_outside_the_plan_fails(self) -> None:
        del self.plan["entries"][1]
        self.save_plan()
        self.assert_fails("does not resolve to an MTL in the plan", MODULE.ImportFailure)

    def test_external_texture_reference_fails(self) -> None:
        self.plan["entries"][1]["transformation"]["textures"] = {"advancedrocketry:models/widget": "minecraft:block/stone"}
        self.save_plan()
        self.assert_fails("external namespace", MODULE.ImportFailure)

    def test_absolute_library_reference_fails(self) -> None:
        self.plan["entries"][0]["transformation"]["mtllib"] = {"widget.mtl": "/models/block/widget.mtl"}
        self.save_plan()
        self.assert_fails("absolute", MODULE.ImportFailure)

    def test_more_than_512_entries_fail(self) -> None:
        plan = copy.deepcopy(self.plan)
        template = plan["entries"][3]
        plan["entries"] = [dict(template, source_path=f"{LEGACY}textures/items/x{index}.png",
                                target_path=f"{TARGET}textures/item/x{index}.png") for index in range(513)]
        with self.assertRaisesRegex(ValueError, "1 to 512 entries"):
            MODULE.validate_plan(plan)
        plan["entries"] = plan["entries"][:512]
        self.assertIs(plan, MODULE.validate_plan(plan))

    def test_unmapped_lang_key_fails(self) -> None:
        self.plan["entries"][4]["transformation"]["discard"] = ["tile.old.name"]
        self.save_plan()
        self.assert_fails(r"1 legacy keys are neither mapped nor discarded by the plan, for example \['gui.unused'\]",
                          MODULE.ImportFailure)

    def test_lang_plan_naming_missing_or_invalid_keys_fails(self) -> None:
        self.plan["entries"][4]["transformation"]["discard"].append("gui.gone")
        self.save_plan()
        self.assert_fails("does not have", MODULE.ImportFailure)
        self.plan = self.base_plan()
        self.plan["entries"][4]["transformation"]["keys"]["item.widget.name"] = "block.widget"
        self.save_plan()
        self.assert_fails("not a modern advancedrocketrycommunity language key", MODULE.ImportFailure)

    def test_sixteen_set_history_fails_without_a_cleared_finding(self) -> None:
        self.add(self.entry("textures/items/sixteen.png", "textures/item/sixteen.png", {"kind": "rename"}))
        self.assert_fails("AR 16x texture set", MODULE.ImportFailure)
        findings = self.root / "docs/provenance/v1.8.0-origin-findings.json"
        findings.write_text(json.dumps({"schema_version": 1, "findings": [{
            "asset": "textures/items/sixteen.png", "decision": "CLEARED", "reviewer": "owner",
            "reviewed_at": "2026-10-14", "basis": "fixture"}]}), encoding="utf-8")
        record = self.generate()
        entry = next(item for item in record["entries"] if item["target_path"].endswith("sixteen.png"))
        self.assertEqual("CLEARED", entry["origin_finding"])
        self.assertIn("AR 16x texture set", entry["review_triggers"][0])

    def test_first_parent_merge_history_is_checked(self) -> None:
        self.add(self.entry("textures/items/gizmo.png", "textures/item/gizmo.png", {"kind": "rename"}))
        self.assert_fails(r"Merge pull request #1809.*AR 16x texture set", MODULE.ImportFailure)

    def test_unknown_or_deferred_unit_fails(self) -> None:
        self.plan["entries"][3]["units"] = ["block:old"]
        self.save_plan()
        self.assert_fails("differ from the coverage row", MODULE.ImportFailure)
        self.plan = self.base_plan()
        self.plan["entries"][4]["units"] = ["block:old"]
        self.save_plan()
        self.assert_fails("not an open ledger unit", MODULE.ImportFailure)

    def test_unpinned_vanilla_jar_fails(self) -> None:
        other = self.fixture.base / "other.jar"
        with zipfile.ZipFile(other, "w") as archive:
            archive.writestr("assets/minecraft/textures/block/stone.png", STONE)
        with self.assertRaisesRegex(ValueError, "differs from the SHA-256 pinned in ADR-061"):
            MODULE.generate(self.root, self.fixture.upstream, self.plan_path, self.record,
                            [self.fixture.vanilla[0], f"1.20.1={other}"])
        self.assertFalse(self.record.exists())

    def test_unpinned_derivation_results_fail(self) -> None:
        results = self.root / "docs/work/v1.8.0-vanilla-derivation.json"
        results.write_bytes(results.read_bytes() + b"\n")
        with self.assertRaisesRegex(ValueError, "pinned in ADR-061"):
            self.generate()

    def test_shallow_upstream_fails(self) -> None:
        shallow = Path(self.temporary.name) / "shallow"
        subprocess.run(["git", "clone", "-q", "--depth", "1", self.fixture.upstream.as_uri(), str(shallow)],
                       check=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE)
        with self.assertRaisesRegex(ValueError, "shallow clone"):
            MODULE.generate(self.root, shallow, self.plan_path, self.record, self.fixture.vanilla)

    def test_unsupported_file_type_fails(self) -> None:
        plan = copy.deepcopy(self.plan)
        plan["entries"][2]["source_path"] = LEGACY + "sounds/hiss.ogg"
        with self.assertRaisesRegex(ValueError, "no format check"):
            MODULE.validate_plan(plan)

    def test_animation_file_needs_its_image_and_an_animation_object(self) -> None:
        plan = copy.deepcopy(self.plan)
        plan["entries"][2]["source_path"] = LEGACY + "textures/models/widget.png.mcmeta"
        plan["entries"][2]["target_path"] = TARGET + "textures/models/widget.png.mcmeta"
        with self.assertRaisesRegex(ValueError, "animation without its image"):
            MODULE.validate_plan(plan)
        MODULE.check_animation(b'{"animation": {"frametime": 2}}', "a.png.mcmeta")
        for data in (b'{"frames": []}', b"\xff", b"[]"):
            with self.subTest(data), self.assertRaisesRegex(ValueError, "animation"):
                MODULE.check_animation(data, "a.png.mcmeta")

    def test_existing_foreign_target_is_not_overwritten(self) -> None:
        _write(self.root / (TARGET + "textures/item/gadget.png"), b"someone else's file")
        with self.assertRaisesRegex(MODULE.ImportFailure, "other content"):
            self.generate()
        self.assertEqual(b"someone else's file", (self.root / (TARGET + "textures/item/gadget.png")).read_bytes())
        self.assertFalse(self.record.exists())

    def test_target_shadowed_by_a_datagen_root_fails(self) -> None:
        _write(self.root / "src/generated/v1.8/resources/assets/advancedrocketrycommunity/lang/de_de.json", "{}")
        self.assert_fails("same resource exists in src/generated/v1.8/resources", MODULE.ImportFailure)

    def test_target_recorded_by_another_batch_fails(self) -> None:
        _write(self.root / "docs/provenance/v1.8.0-other.json", json.dumps({"schema_version": 2, "entries": [
            {"target_path": TARGET + "textures/item/gadget.png", "target_sha256": "0" * 64}]}))
        self.assert_fails("already recorded by v1.8.0-other.json", MODULE.ImportFailure)

    # Verify

    def test_verify_finds_tampered_targets_and_records(self) -> None:
        self.generate()
        target = self.root / (TARGET + "textures/item/gadget.png")
        target.write_bytes(b"tampered")
        self.assertIn(f"target bytes differ from the deterministic transform: {TARGET}textures/item/gadget.png",
                      self.verify())
        target.write_bytes(UPSTREAM_FILES["textures/items/Gadget.png"])
        self.assertEqual([], self.verify())
        self.record.write_bytes(self.record.read_bytes().replace(b'"schema_version": 2', b'"schema_version":  2'))
        self.assertIn("provenance record does not reproduce byte for byte from the plan, upstream and targets",
                      self.verify())

    def test_approved_review_survives_only_an_unchanged_record(self) -> None:
        record = self.generate()
        record["review"].update(status="APPROVED", reviewer="reviewer", reviewed_at="2026-10-14")
        self.record.write_bytes(V010.pretty_json(record))
        self.assertEqual([], self.verify())
        self.assertEqual("APPROVED", self.generate()["review"]["status"])
        self.plan["batch_id"] = "v1.8.0-fixture-02"
        self.save_plan()
        self.assertEqual("PENDING_HUMAN_REVIEW", self.generate()["review"]["status"])

    def test_dropped_entries_must_be_removed_deliberately(self) -> None:
        self.generate()
        del self.plan["entries"][3]
        self.save_plan()
        with self.assertRaisesRegex(MODULE.ImportFailure, "no longer has"):
            self.generate()

    def test_command_line_reports_pass_and_fail(self) -> None:
        arguments = ["--upstream", str(self.fixture.upstream), "--plan", str(self.plan_path), "--record",
                     str(self.record), "--repository-root", str(self.root)]
        for spec in self.fixture.vanilla:
            arguments += ["--vanilla", spec]
        output, failures = io.StringIO(), io.StringIO()
        with contextlib.redirect_stdout(output), contextlib.redirect_stderr(failures):
            self.assertEqual(0, MODULE.main(["generate", *arguments]))
            self.assertEqual(0, MODULE.main(["verify", *arguments]))
            (self.root / (TARGET + "lang/de_de.json")).write_text("{}", encoding="utf-8")
            self.assertEqual(1, MODULE.main(["verify", *arguments]))
        self.assertIn("[PASS] 5 v1.8 targets and their record reproduce byte for byte", output.getvalue())
        self.assertIn(f"[FAIL] target bytes differ from the deterministic transform: {TARGET}lang/de_de.json",
                      failures.getvalue())


class V010UnchangedTests(unittest.TestCase):
    """The v0.1.0 importer and its record format are untouched: a v0.1.0 record still verifies with its own tool."""

    def test_v010_record_verifies_with_its_own_tool(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            upstream, project = base / "upstream", base / "project"
            upstream.mkdir()
            project.mkdir()
            _git(upstream, "init", "-q")
            _git(upstream, "config", "user.name", "Import Fixture")
            _git(upstream, "config", "user.email", "import@example.invalid")
            _write(upstream / "LICENSE", MIT)
            _write(upstream / (LEGACY + "textures/items/source.png"), b"fixture-png-bytes")
            _write(upstream / (LEGACY + "lang/en_US.lang"), "item.fixture.name=Fixture Item\n")
            _git(upstream, "add", "--all")
            _git(upstream, "commit", "-q", "-m", "fixture")
            plan = {
                "schema_version": 1, "batch_id": "fixture", "target_version": "v0.1.0",
                "source_repository": "https://github.com/Advanced-Rocketry/AdvancedRocketry", "source_branch": "1.12",
                "source_commit": _git(upstream, "rev-parse", "HEAD").strip(), "license": "MIT",
                "copyright_notice": "Copyright (c) 2017",
                "entries": [{"source_path": LEGACY + "textures/items/source.png",
                             "target_path": TARGET + "textures/item/target.png", "mode": "copy",
                             "transformation": ["rename namespace"]},
                            {"source_path": LEGACY + "lang/en_US.lang",
                             "target_path": "src/generated/resources/assets/advancedrocketrycommunity/lang/en_us.json",
                             "mode": "lang", "translations": {"item.fixture.name": "item.advancedrocketrycommunity.fixture"},
                             "additions": {"itemGroup.advancedrocketrycommunity.main": "Fixture Tab"},
                             "transformation": ["extract reviewed key", "serialize JSON"]}],
            }
            _write(project / "plan.json", V010.pretty_json(plan))
            record = V010.generate(project, upstream, project / "plan.json", project / "record.json")
            self.assertEqual(1, record["schema_version"])
            self.assertEqual([], V010.verify(project, upstream, project / "plan.json", project / "record.json"))
            with self.assertRaisesRegex(ValueError, "v1.8 plan schema"):
                MODULE.validate_plan(plan)


if __name__ == "__main__":
    unittest.main()
