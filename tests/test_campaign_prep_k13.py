"""Unit tests for scripts/campaign_prep/k13_inventory.py on a small fake JAR (TASK W402, package K13)."""

from __future__ import annotations

import csv
import io
import json
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

REPO = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(REPO / "scripts"))

from campaign_prep import k13_inventory as k13  # noqa: E402

NS = "assets/advancedrocketrycommunity/"
PNG = b"\x89PNG\r\n\x1a\n" + b"fake"


def model(parent: str | None = None, **textures: str) -> bytes:
    body: dict = {}
    if parent:
        body["parent"] = parent
    if textures:
        body["textures"] = {key: "advancedrocketrycommunity:" + value for key, value in textures.items()}
    return json.dumps(body).encode()


def fake_entries() -> dict[str, bytes]:
    entries = {
        "pack.mcmeta": b"{}",
        "advancedrocketrycommunity.png": PNG,
        "io/github/sunthemoon/advancedrocketrycommunity/Some.class": b"\xca\xfe",
        "data/advancedrocketrycommunity/machine_patterns/railgun.json": b"{}",
        "data/advancedrocketrycommunity/recipes/x.json": b"{}",
        # Three ordinary cubes of one family, out of order on purpose.
        NS + "models/block/b_cube.json": model("minecraft:block/cube_all", all="block/b_cube"),
        NS + "models/block/a_cube.json": model("minecraft:block/cube_all", all="block/a_cube"),
        NS + "models/block/c_cube.json": model("minecraft:block/cube_all", all="block/c_cube"),
        NS + "models/item/a_cube.json": model("advancedrocketrycommunity:block/a_cube"),
        NS + "models/item/b_cube.json": model("advancedrocketrycommunity:block/b_cube"),
        NS + "models/item/plain.json": model("minecraft:item/generated", layer0="item/plain"),
        NS + "blockstates/a_cube.json": json.dumps(
            {"variants": {"": {"model": "advancedrocketrycommunity:block/a_cube"}}}).encode(),
        NS + "blockstates/c_cube.json": json.dumps(
            {"variants": {"": {"model": "advancedrocketrycommunity:block/c_cube"}}}).encode(),
        NS + "textures/block/a_cube.png": PNG + b"a",
        NS + "textures/block/b_cube.png": PNG + b"b",
        NS + "textures/block/c_cube.png": PNG + b"c",
        NS + "textures/item/plain.png": PNG + b"p",
        NS + "textures/item/orphan.png": PNG + b"o",
        # A multiblock: the controller's cube and its OBJ hull.
        NS + "blockstates/railgun.json": json.dumps(
            {"variants": {"facing=north": {"model": "advancedrocketrycommunity:block/railgun"}}}).encode(),
        NS + "models/block/railgun.json": model("minecraft:block/cube_all", all="block/railgun_front"),
        NS + "models/item/railgun.json": model("advancedrocketrycommunity:block/railgun"),
        NS + "textures/block/railgun_front.png": PNG + b"rf",
        NS + "models/railgun.obj": b"mtllib railgun.mtl\no Barrel\n",
        NS + "models/railgun.mtl": b"newmtl None\nmap_Kd advancedrocketrycommunity:models/railgun\n",
        NS + "textures/models/railgun.png": PNG + b"hull",
        # An OBJ block model and the item that inherits it.
        NS + "models/block/rocket_motor.json": json.dumps({
            "loader": "forge:obj", "model": "advancedrocketrycommunity:models/block/models/rocketmotor.obj",
            "textures": {"particle": "advancedrocketrycommunity:block/combustion"}}).encode(),
        NS + "models/item/rocket_motor.json": model("advancedrocketrycommunity:block/rocket_motor"),
        NS + "models/block/models/rocketmotor.obj": b"mtllib rocketmotor.mtl\n",
        NS + "models/block/models/rocketmotor.mtl": b"map_Kd advancedrocketrycommunity:block/combustion\n",
        NS + "textures/block/combustion.png": PNG + b"m",
        # Armour, and the other files the missing-resource pack replaces.
        NS + "textures/models/armor/space_suit_layer_1.png": PNG + b"s1",
        NS + "models/item/space_suit_helmet.json": model("minecraft:item/generated", layer0="item/space_suit_helmet"),
        NS + "textures/item/space_suit_helmet.png": PNG + b"sh",
        NS + "textures/block/rocketpad_.png": PNG + b"pad",
        NS + "textures/models/blackholegenerator.png": PNG + b"bh",
        NS + "textures/models/rollingmachine.png": PNG + b"rm",
        NS + "textures/models/spaceelevatorcapsule.png": PNG + b"cap",
        # Sounds and languages.
        NS + "sounds.json": json.dumps({
            "lathe": {"sounds": [{"name": "advancedrocketrycommunity:lathe", "attenuation_distance": 32}],
                      "subtitle": "subtitle.advancedrocketrycommunity.lathe"},
            "ui_select": {"sounds": ["advancedrocketrycommunity:ui_select"], "subtitle": "subtitle.only_english"},
        }).encode(),
        NS + "sounds/lathe.ogg": b"OggS1",
        NS + "sounds/ui_select.ogg": b"OggS2",
        NS + "lang/en_us.json": json.dumps({
            "subtitle.advancedrocketrycommunity.lathe": "Lathe turns", "subtitle.only_english": "Select",
            "gui.amount": "%s of %2$s mB", "gui.percent": "%s%%"}).encode(),
        NS + "lang/zh_cn.json": json.dumps({
            "subtitle.advancedrocketrycommunity.lathe": "车床运转", "gui.amount": "%s / %2$s mB",
            "gui.percent": "%s%%", "gui.only_chinese": "仅中文"}, ensure_ascii=False).encode("utf-8"),
        "assets/advancedrocketrycommunity_v180/lang/en_us.json": json.dumps({"k.long": "x" * 300}).encode(),
        "assets/advancedrocketrycommunity_v180/lang/zh_cn.json": json.dumps({"k.long": "长"}, ensure_ascii=False
                                                                              ).encode("utf-8"),
    }
    return entries


def write_fake_jar(path: Path, order_reversed: bool = False, year: int = 2026) -> None:
    entries = fake_entries()
    names = sorted(entries, reverse=order_reversed)
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr(zipfile.ZipInfo("assets/", (year, 1, 2, 3, 4, 6)), b"")
        for name in names:
            archive.writestr(zipfile.ZipInfo(name, (year, 1, 2, 3, 4, 6)), entries[name])


def rows_of(path: Path) -> list[dict]:
    with open(path, encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


class FakeJarCase(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / "fake.jar"
        write_fake_jar(self.jar)
        self.files = k13.read_jar(self.jar)


class InventoryTests(FakeJarCase):
    def test_rows_have_kind_size_and_hash_and_skip_code_data_and_directories(self) -> None:
        rows = {row["path"]: row for row in k13.inventory(self.files)}
        self.assertNotIn("assets/", rows)
        self.assertNotIn("io/github/sunthemoon/advancedrocketrycommunity/Some.class", rows)
        self.assertNotIn("data/advancedrocketrycommunity/recipes/x.json", rows)
        texture = rows[NS + "textures/block/a_cube.png"]
        self.assertEqual("texture", texture["kind"])
        self.assertEqual(len(PNG) + 1, texture["size"])
        self.assertEqual(k13.sha256_bytes(PNG + b"a"), texture["sha256"])
        self.assertEqual("advancedrocketrycommunity", texture["namespace"])
        kinds = {path: row["kind"] for path, row in rows.items()}
        self.assertEqual("block_model", kinds[NS + "models/block/a_cube.json"])
        self.assertEqual("item_model", kinds[NS + "models/item/a_cube.json"])
        self.assertEqual("model_obj", kinds[NS + "models/railgun.obj"])
        self.assertEqual("model_mtl", kinds[NS + "models/railgun.mtl"])
        self.assertEqual("blockstate", kinds[NS + "blockstates/a_cube.json"])
        self.assertEqual("sound_file", kinds[NS + "sounds/lathe.ogg"])
        self.assertEqual("sound_definitions", kinds[NS + "sounds.json"])
        self.assertEqual("language_file", kinds[NS + "lang/zh_cn.json"])
        self.assertEqual("root_image", kinds["advancedrocketrycommunity.png"])
        self.assertEqual(sorted(rows), list(rows))

    def test_language_keys_and_the_keys_missing_from_either_side(self) -> None:
        rows, missing = k13.languages(self.files)
        self.assertEqual(6, len(rows))  # five keys in the base namespace, one in v180
        self.assertEqual(
            [("advancedrocketrycommunity", "gui.only_chinese", "en_us"),
             ("advancedrocketrycommunity", "subtitle.only_english", "zh_cn")],
            [(row["namespace"], row["key"], row["missing_from"]) for row in missing])
        long_row = [row for row in rows if row["key"] == "k.long"][0]
        self.assertEqual((300, 1), (long_row["en_us_chars"], long_row["zh_cn_chars"]))

    def test_sound_events_carry_the_subtitle_and_whether_each_language_has_it(self) -> None:
        rows, _ = k13.languages(self.files)
        events = {row["event"]: row for row in k13.sound_events(self.files, rows)}
        self.assertEqual({"lathe", "ui_select"}, set(events))
        self.assertEqual("32", events["lathe"]["attenuation_distance"])
        self.assertEqual((1, 1), (events["lathe"]["subtitle_in_en_us"], events["lathe"]["subtitle_in_zh_cn"]))
        self.assertEqual((1, 0), (events["ui_select"]["subtitle_in_en_us"], events["ui_select"]["subtitle_in_zh_cn"]))

    def test_a_sound_event_without_its_file_is_an_error(self) -> None:
        del self.files[NS + "sounds/lathe.ogg"]
        rows, _ = k13.languages(self.files)
        with self.assertRaises(k13.K13Error):
            k13.sound_events(self.files, rows)


class ClassificationTests(FakeJarCase):
    def classes(self, **arguments) -> tuple[dict[str, dict], list[dict]]:
        rows, families = k13.classify(self.files, **arguments)
        return {row["path"]: row for row in rows}, families

    def test_family_pick_is_the_first_sorted_path_that_is_not_high_risk(self) -> None:
        rows, families = self.classes(strict=False)
        table = {(row["kind"], row["family"]): row for row in families}
        cubes = table[("block", "minecraft:block/cube_all")]
        # a_cube, b_cube, c_cube and the railgun controller share the parent; the railgun is high-risk.
        self.assertEqual((4, 1), (cubes["members"], cubes["high_risk_members"]))
        self.assertEqual(NS + "models/block/a_cube.json", cubes["pick"])
        self.assertEqual("SAMPLE", rows[NS + "models/block/a_cube.json"]["class"])
        self.assertEqual("FAMILY_MEMBER", rows[NS + "models/block/b_cube.json"]["class"])
        self.assertEqual("FAMILY_MEMBER", rows[NS + "models/block/c_cube.json"]["class"])

    def test_an_item_that_inherits_its_own_block_joins_the_blocks_family_as_an_item(self) -> None:
        rows, families = self.classes(strict=False)
        table = {(row["kind"], row["family"]): row for row in families}
        items = table[("item", "minecraft:block/cube_all")]
        self.assertEqual(3, items["members"])  # a_cube, b_cube and the high-risk railgun item
        self.assertEqual(NS + "models/item/a_cube.json", items["pick"])
        self.assertEqual("item:minecraft:block/cube_all", rows[NS + "models/item/b_cube.json"]["family"])

    def test_a_family_whose_members_are_all_high_risk_has_no_pick(self) -> None:
        _, families = self.classes(strict=False)
        motor = [row for row in families if row["family"].endswith("models/block/rocket_motor.json")]
        self.assertEqual({"block", "item"}, {row["kind"] for row in motor})
        self.assertTrue(all(row["pick"] == "" and row["members"] == row["high_risk_members"] for row in motor))

    def test_obj_models_multiblocks_and_armour_are_high_risk_with_their_textures(self) -> None:
        rows, _ = self.classes(strict=False)
        for path, reason in (
                (NS + "models/railgun.obj", "OBJ model"), (NS + "models/railgun.mtl", "OBJ model"),
                (NS + "textures/models/railgun.png", "OBJ model"),
                (NS + "models/block/rocket_motor.json", "OBJ model"),
                (NS + "models/item/rocket_motor.json", "OBJ model"),
                (NS + "models/block/models/rocketmotor.obj", "OBJ model"),
                (NS + "textures/block/combustion.png", "OBJ model"),
                (NS + "blockstates/railgun.json", "multiblock railgun"),
                (NS + "models/block/railgun.json", "multiblock railgun"),
                (NS + "models/item/railgun.json", "multiblock railgun"),
                (NS + "textures/block/railgun_front.png", "multiblock railgun"),
                (NS + "textures/models/armor/space_suit_layer_1.png", "armour"),
                (NS + "models/item/space_suit_helmet.json", "armour"),
                (NS + "textures/item/space_suit_helmet.png", "armour")):
            self.assertEqual("HIGH_RISK", rows[path]["class"], path)
            self.assertIn(reason, rows[path]["reason"], path)

    def test_textures_and_blockstates_follow_the_models_that_need_them(self) -> None:
        rows, _ = self.classes(strict=False)
        self.assertEqual("SAMPLE", rows[NS + "textures/block/a_cube.png"]["class"])
        self.assertEqual("SAMPLE", rows[NS + "blockstates/a_cube.json"]["class"])
        self.assertEqual("FAMILY_MEMBER", rows[NS + "textures/block/c_cube.png"]["class"])
        self.assertEqual("FAMILY_MEMBER", rows[NS + "blockstates/c_cube.json"]["class"])
        self.assertEqual("UNREFERENCED", rows[NS + "textures/item/orphan.png"]["class"])

    def test_corrected_assets_are_high_risk_and_a_missing_one_is_an_error(self) -> None:
        corrected = {"W999": ("models/block/c_cube.json",)}
        rows, families = self.classes(corrected=corrected)
        self.assertEqual("HIGH_RISK", rows[NS + "models/block/c_cube.json"]["class"])
        self.assertEqual("corrected by W999", rows[NS + "models/block/c_cube.json"]["reason"])
        self.assertEqual("HIGH_RISK", rows[NS + "textures/block/c_cube.png"]["class"])
        self.assertEqual("HIGH_RISK", rows[NS + "blockstates/c_cube.json"]["class"])
        with self.assertRaises(k13.K13Error):
            k13.classify(self.files, corrected={"W999": ("models/block/absent.json",)})

    def test_every_visual_asset_has_exactly_one_class(self) -> None:
        rows, _ = k13.classify(self.files, strict=False)
        paths = [row["path"] for row in rows]
        self.assertEqual(len(paths), len(set(paths)))
        visual = [row["path"] for row in k13.inventory(self.files)
                  if row["kind"] in ("texture", "blockstate", "block_model", "item_model", "model_obj", "model_mtl")]
        self.assertEqual(sorted(visual), paths)
        self.assertTrue(all(row["class"] in ("HIGH_RISK", "SAMPLE", "FAMILY_MEMBER", "UNREFERENCED") for row in rows))


class LongTextTests(unittest.TestCase):
    def test_the_value_is_at_least_doubled_and_keeps_each_placeholder_once(self) -> None:
        longer = k13.lengthen("%s of %2$s mB")
        self.assertTrue(longer.startswith("%s of %2$s mB "))
        self.assertGreaterEqual(len(longer), 2 * len("%s of %2$s mB"))
        self.assertEqual(1, longer.count("%s"))
        self.assertEqual(1, longer.count("%2$s"))
        self.assertEqual(2, longer.count("%"))

    def test_an_escaped_percent_sign_is_not_repeated(self) -> None:
        self.assertEqual(k13.lengthen("%s%% full").count("%"), 3)
        self.assertEqual("%s%%", k13.lengthen("%s%%"))  # nothing but specifiers: unchanged

    def test_the_cap_cuts_only_the_repeated_text(self) -> None:
        value = "a" * 200 + " %s"
        longer = k13.lengthen(value)
        self.assertEqual(256, len(longer))
        self.assertTrue(longer.startswith(value))
        self.assertEqual(1, longer.count("%"))
        self.assertEqual("b" * 256, k13.lengthen("b" * 256))
        self.assertEqual("c" * 300, k13.lengthen("c" * 300))

    def test_chinese_text_stays_chinese(self) -> None:
        self.assertEqual("车床运转 车床运转", k13.lengthen("车床运转"))

    def test_the_rule_is_a_pure_function(self) -> None:
        self.assertEqual(k13.lengthen("Launch"), k13.lengthen("Launch"))
        self.assertEqual("Launch Launch", k13.lengthen("Launch"))


class PackTests(FakeJarCase):
    @staticmethod
    def entries(data: bytes) -> dict[str, bytes]:
        with zipfile.ZipFile(io.BytesIO(data)) as archive:
            return {info.filename: archive.read(info) for info in archive.infolist()}

    def test_missing_resource_pack_replaces_exactly_the_stated_files(self) -> None:
        data, rows = k13.missing_resource_pack(self.files)
        entries = self.entries(data)
        stated = {NS + below for below, _ in k13.MISSING_TEXTURES} | {NS + k13.MISSING_MODEL[0]}
        self.assertEqual(stated | {"pack.mcmeta"}, set(entries))
        self.assertEqual(15, json.loads(entries["pack.mcmeta"])["pack"]["pack_format"])
        for below, _ in k13.MISSING_TEXTURES:
            self.assertEqual(k13.MISSING_TEXTURE_BYTES, entries[NS + below])
            self.assertFalse(entries[NS + below].startswith(b"\x89PNG"))
        broken = json.loads(entries[NS + k13.MISSING_MODEL[0]])
        self.assertEqual({"parent": "advancedrocketrycommunity:block/k13_missing_parent"}, broken)
        self.assertNotIn(NS + "models/block/k13_missing_parent.json", self.files)
        self.assertEqual(stated, {row["path"] for row in rows})
        for row in rows:
            self.assertEqual(k13.sha256_bytes(self.files[row["path"]]), row["original_sha256"])
            self.assertEqual(k13.sha256_bytes(entries[row["path"]]), row["pack_sha256"])
            self.assertNotEqual(row["original_sha256"], row["pack_sha256"])

    def test_missing_resource_pack_refuses_a_path_the_jar_does_not_have(self) -> None:
        with self.assertRaises(k13.K13Error):
            k13.missing_resource_pack(self.files, textures=(("textures/block/absent.png", "x"),))

    def test_long_text_pack_overrides_every_language_file_with_the_same_keys(self) -> None:
        data, rows = k13.long_text_pack(self.files)
        entries = self.entries(data)
        languages = {path for path in self.files if "/lang/" in path}
        self.assertEqual(4, len(languages))
        self.assertEqual(languages | {"pack.mcmeta"}, set(entries))
        for path in languages:
            original = json.loads(self.files[path].decode("utf-8"))
            longer = json.loads(entries[path].decode("utf-8"))
            self.assertEqual(list(original), list(longer))
            for key, value in original.items():
                self.assertEqual(k13.lengthen(value), longer[key])
        english = json.loads(entries[NS + "lang/en_us.json"].decode("utf-8"))
        self.assertEqual("Lathe turns Lathe turns", english["subtitle.advancedrocketrycommunity.lathe"])
        self.assertEqual("%s%%", english["gui.percent"])
        self.assertIn("车床运转 车床运转".encode("utf-8"), entries[NS + "lang/zh_cn.json"])
        actions = {row["path"]: row["action"] for row in rows}
        self.assertEqual("3 values lengthened, 1 unchanged", actions[NS + "lang/en_us.json"])
        self.assertEqual("0 values lengthened, 1 unchanged",
                         actions["assets/advancedrocketrycommunity_v180/lang/en_us.json"])

    def test_the_same_input_gives_byte_identical_packs_and_tables(self) -> None:
        first, second = self.root / "first", self.root / "second"
        other = self.root / "other.jar"
        write_fake_jar(other, order_reversed=True, year=2031)  # same entries, other order and timestamps
        before = k13.sha256_file(self.jar)
        k13.generate(self.jar, first, None, strict=False)
        k13.generate(other, second, None, strict=False)
        self.assertEqual(before, k13.sha256_file(self.jar))
        names = sorted(path.name for path in first.iterdir())
        self.assertEqual(names, sorted(path.name for path in second.iterdir()))
        self.assertIn("k13-missing-resource-pack.zip", names)
        self.assertIn("k13-long-text-pack.zip", names)
        for name in names:
            if name in ("summary.json", "SHA256SUMS.txt"):  # these name the JAR file and its hash
                continue
            self.assertEqual((first / name).read_bytes(), (second / name).read_bytes(), name)
        again = self.root / "again"
        k13.generate(self.jar, again, None, strict=False)
        for name in names:
            self.assertEqual((first / name).read_bytes(), (again / name).read_bytes(), name)

    def test_generate_writes_the_tables_and_a_checksum_list_of_its_own_files(self) -> None:
        out = self.root / "out"
        summary = k13.generate(self.jar, out, None, strict=False)
        self.assertEqual(k13.sha256_file(self.jar), summary["jar_sha256"])
        assets = rows_of(out / "assets.csv")
        self.assertEqual(len(k13.inventory(self.files)), len(assets))
        self.assertEqual(["path", "kind", "namespace", "size", "sha256"], list(assets[0]))
        self.assertEqual(2, len(rows_of(out / "lang-missing.csv")))
        self.assertEqual(11, len(rows_of(out / "pack-contents.csv")))
        sums = dict(reversed(line.split("  ")) for line in (out / "SHA256SUMS.txt").read_text("utf-8").splitlines())
        self.assertEqual(sorted(path.name for path in out.iterdir() if path.name != "SHA256SUMS.txt"), sorted(sums))
        for name, digest in sums.items():
            self.assertEqual(k13.sha256_file(out / name), digest)
        self.assertNotIn(b"\r\n", (out / "assets.csv").read_bytes())

    def test_a_jar_with_another_hash_is_refused(self) -> None:
        with self.assertRaises(k13.K13Error):
            k13.generate(self.jar, self.root / "refused", None, expect_sha256="0" * 64, strict=False)
        self.assertFalse((self.root / "refused").exists())


class CuratedTableTests(unittest.TestCase):
    """The curated tables against this checkout's source: every row still points at real source text."""

    @classmethod
    def setUpClass(cls) -> None:
        cls.source = k13.Source(REPO)

    def test_every_registered_menu_type_has_a_row_and_no_row_is_left_over(self) -> None:
        self.assertEqual(set(k13.MENUS), k13.registered_menus(self.source))

    def test_the_access_matrix_covers_every_menu_and_every_client_to_server_message(self) -> None:
        rows = k13.access_matrix(self.source)
        menus = [row for row in rows if row["subject"] == "menu"]
        messages = [row for row in rows if row["subject"] == "client-to-server message"]
        self.assertEqual(len(k13.MENUS), len(menus))
        pinned = [row for row in k13.pinned_messages(self.source) if row["direction"] == "PLAY_TO_SERVER"]
        self.assertEqual(len(pinned), len(messages))
        for row in rows:
            self.assertEqual(set(k13.ACCESS_FIELDS), set(row))
            self.assertRegex(row["source"], r"^src/main/java/\S+\.java:\d+( src/main/java/\S+\.java:\d+)*$")
            for field in ("who_may_act", "distance_check", "refusal_message_or_log"):
                self.assertTrue(row[field].strip(), (row["id"], field))

    def test_a_vanished_source_line_is_an_error(self) -> None:
        with self.assertRaises(k13.K13Error):
            self.source.locate(k13.A("registry/ModMenuTypes.java", "this text is not in the file"))
        with self.assertRaises(k13.K13Error):
            self.source.locate(k13.A("registry/NoSuchFile.java", "x"))

    def test_screen_and_sound_rows_point_at_source_text(self) -> None:
        for entry in k13.OTHER_SCREENS + k13.SOUND_PRODUCERS + k13.MENULESS:
            self.assertTrue(self.source.locations(entry["anchors"]))

    def test_checklist_rows_are_found_by_whole_words(self) -> None:
        sections = {"J16": "open the drill; the railgun launch", "J01": "the bay menu and the trailgunner"}
        self.assertEqual(["J16"], k13.naming_rows(sections, ("railgun",)))
        self.assertEqual(["J01"], k13.naming_rows(sections, ("Bay Menu",)))


if __name__ == "__main__":
    unittest.main()
