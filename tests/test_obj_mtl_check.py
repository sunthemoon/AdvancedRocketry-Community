from __future__ import annotations

import unittest

from tools.audit import obj_mtl_check as models

CUBE = (
    "# Fixture quad pair written by the test\n"
    "mtllib widget.mtl\n"
    "o Body\n"
    "v 0 0 0\nv 1 0 0\nv 1 1 0\nv 0 1 0\nv 0 0 1\n"
    "vt 0 0\nvt 1 0\nvt 1 1\n"
    "vn 0 0 -1\n"
    "usemtl Body\n"
    "s off\n"
    "f 1/1/1 2/2/1 3/3/1 4/1/1\n"
    "f 1//1 2//1 5//1\n"
    "l 1 5\n"
)
MATERIAL = (
    "newmtl Body\n"
    "Ka 0 0 0\nKd 1 1 1\nKs 0.5 0.5 0.5\nNs 10\nd 1\nillum 2\n"
    "map_Kd advancedrocketrycommunity:block/widget\n"
)


def obj(text: str) -> bytes:
    return text.encode("utf-8")


class ObjFormatTests(unittest.TestCase):
    def test_counts_a_valid_model(self) -> None:
        summary = models.parse_obj(obj(CUBE), "cube.obj")
        self.assertEqual(5, summary["positions"])
        self.assertEqual(2, summary["faces"])
        self.assertEqual(8, summary["baked_vertices"])
        self.assertEqual(1, summary["ignored_lines"])
        self.assertEqual("widget.mtl", summary["material_library"])
        self.assertEqual(["Body"], summary["materials"])

    def test_refuses_what_forge_would_load_wrongly(self) -> None:
        cases = {
            "face outside any material": CUBE.replace("usemtl Body\n", ""),
            "3 or 4 corners": CUBE.replace("f 1//1 2//1 5//1", "f 1 2 3 4 5"),
            "outside the 5 defined": CUBE.replace("f 1//1 2//1 5//1", "f 1 2 6"),
            "texture coordinate index": CUBE.replace("4/1/1", "4/9/1"),
            "unsupported statement": CUBE + "cstype bspline\n",
            "second material library": CUBE.replace("o Body\n", "o Body\nmtllib other.mtl\n"),
            "usemtl before any material library": CUBE.replace("mtllib widget.mtl\n", ""),
            "line continuation": CUBE.replace("s off", "s \\"),
            "control character": CUBE.replace("o Body", "o Bo\x01dy"),
            "exactly one name": CUBE.replace("o Body", "o Body Extra"),
            "not a finite number": CUBE.replace("v 1 0 0", "v 1 nan 0"),
            "vertex weight is zero": CUBE.replace("v 1 0 0", "v 1 0 0 0"),
        }
        for message, text in cases.items():
            with self.subTest(message), self.assertRaisesRegex(models.FormatError, message):
                models.parse_obj(obj(text), "cube.obj")

    def test_refuses_a_byte_order_mark_and_invalid_utf8(self) -> None:
        with self.assertRaisesRegex(models.FormatError, "byte-order mark"):
            models.parse_obj(b"\xef\xbb\xbf" + obj(CUBE), "cube.obj")
        with self.assertRaisesRegex(models.FormatError, "not UTF-8"):
            models.parse_obj(obj(CUBE) + b"o \xff\n", "cube.obj")

    def test_baked_object_budget_counts_positions_and_quad_corners(self) -> None:
        positions = "mtllib a.mtl\n" + "v 0 0 0\n" * (models.MAX_BAKED_OBJECT_VERTICES + 1)
        with self.assertRaisesRegex(models.FormatError, "more than 65536 vertices"):
            models.parse_obj(obj(positions), "big.obj")
        faces = "mtllib a.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl A\n" + "f 1 2 3\n" * (
            models.MAX_BAKED_OBJECT_VERTICES // models.QUAD_CORNERS + 1)
        with self.assertRaisesRegex(models.FormatError, "more than 65536 baked vertices"):
            models.parse_obj(obj(faces), "big.obj")
        at_budget = "mtllib a.mtl\nv 0 0 0\nv 1 0 0\nv 0 1 0\nusemtl A\n" + "f 1 2 3\n" * (
            models.MAX_BAKED_OBJECT_VERTICES // models.QUAD_CORNERS)
        self.assertEqual(models.MAX_BAKED_OBJECT_VERTICES, models.parse_obj(obj(at_budget), "edge.obj")["baked_vertices"])

    def test_oversized_file_is_refused_before_parsing(self) -> None:
        with self.assertRaisesRegex(models.FormatError, "model set budget"):
            models.parse_obj(b"#" * (models.MAX_FILE_BYTES + 1), "huge.obj")


class MtlFormatTests(unittest.TestCase):
    def test_reads_materials_and_textures(self) -> None:
        summary = models.parse_mtl(obj(MATERIAL), "widget.mtl")
        self.assertEqual({"Body": {"map_Kd": "advancedrocketrycommunity:block/widget"}}, summary["materials"])
        self.assertEqual(["advancedrocketrycommunity:block/widget"], summary["textures"])

    def test_refuses_malformed_libraries(self) -> None:
        cases = {
            "before any newmtl": "Kd 1 1 1\n" + MATERIAL,
            "defined twice": MATERIAL + "newmtl Body\n",
            "no options": MATERIAL.replace("map_Kd ", "map_Kd -s 1 1 1 "),
            "given twice": MATERIAL + "map_Kd advancedrocketrycommunity:block/other\n",
            "unsupported statement": MATERIAL + "map_Bump advancedrocketrycommunity:block/bump\n",
            "one integer": MATERIAL.replace("illum 2", "illum high"),
        }
        for message, text in cases.items():
            with self.subTest(message), self.assertRaisesRegex(models.FormatError, message):
                models.parse_mtl(obj(text), "widget.mtl")

    def test_legacy_references_pass_only_the_format_only_parse(self) -> None:
        legacy = MATERIAL.replace("advancedrocketrycommunity:block/widget", "advancedrocketry:models/widget")
        self.assertEqual(["advancedrocketry:models/widget"], models.parse_mtl(obj(legacy), "a", references=False)["textures"])
        with self.assertRaisesRegex(models.FormatError, "external namespace"):
            models.parse_mtl(obj(legacy), "a")


class ReferenceTests(unittest.TestCase):
    def test_absolute_external_and_variable_references_are_refused(self) -> None:
        cases = {
            "/models/widget.mtl": "absolute",
            "C:/models/widget.mtl": "absolute",
            "C:\\models\\widget.mtl": "absolute",
            "\\\\server\\share\\widget.mtl": "absolute",
            "https://example.invalid/widget.mtl": "absolute",
            "file:///tmp/widget.mtl": "absolute",
            "#texture": "variable",
            "minecraft:block/stone": "external namespace",
            "../widget.mtl": "parent directory",
            "advancedrocketrycommunity:models/../x": "parent directory",
            "Widget.MTL": "not a valid resource location",
        }
        for value, message in cases.items():
            with self.subTest(value), self.assertRaisesRegex(models.FormatError, message):
                models.check_reference(value, relative=True, where="test")

    def test_a_texture_needs_the_mod_namespace(self) -> None:
        with self.assertRaisesRegex(models.FormatError, "no namespace"):
            models.check_reference("block/widget", relative=False, where="test")
        self.assertEqual("widget.mtl", models.check_reference("widget.mtl", relative=True, where="test"))

    def test_resolution_follows_the_forge_loader(self) -> None:
        model = "advancedrocketrycommunity:models/block/widget.obj"
        self.assertEqual("advancedrocketrycommunity:models/block/widget.mtl", models.resolve_library(model, "widget.mtl"))
        self.assertEqual("advancedrocketrycommunity:models/x.mtl",
                         models.resolve_library(model, "advancedrocketrycommunity:models/x.mtl"))
        self.assertEqual("advancedrocketrycommunity:textures/block/widget.png",
                         models.resolve_texture("advancedrocketrycommunity:block/widget"))


class RewriteTests(unittest.TestCase):
    def test_rewrites_only_mapped_references_and_keeps_every_other_byte(self) -> None:
        source = obj(CUBE.replace("\n", "\r\n").replace("usemtl Body", "usemtl  None"))
        output = models.rewrite(source, "obj", {"mtllib": {"widget.mtl": "machine.mtl"},
                                                "usemtl": {"None": "Body"}}, "cube.obj")
        expected = source.replace(b"mtllib widget.mtl", b"mtllib machine.mtl").replace(b"usemtl  None", b"usemtl Body")
        self.assertEqual(expected, output)
        texture = models.rewrite(obj(MATERIAL), "mtl", {"textures": {
            "advancedrocketrycommunity:block/widget": "advancedrocketrycommunity:block/machine"}}, "widget.mtl")
        self.assertEqual(obj(MATERIAL.replace("block/widget", "block/machine")), texture)

    def test_every_reference_must_be_mapped_and_every_mapping_used(self) -> None:
        with self.assertRaisesRegex(models.FormatError, "not mapped by the plan"):
            models.rewrite(obj(CUBE), "obj", {"mtllib": {"widget.mtl": "widget.mtl"}, "usemtl": {}}, "cube.obj")
        with self.assertRaisesRegex(models.FormatError, "does not use"):
            models.rewrite(obj(CUBE), "obj", {"mtllib": {"widget.mtl": "widget.mtl"},
                                              "usemtl": {"Body": "Body", "Hull": "Hull"}}, "cube.obj")
        with self.assertRaisesRegex(models.FormatError, "not a plain name"):
            models.rewrite(obj(CUBE), "obj", {"mtllib": {"widget.mtl": "widget.mtl"},
                                              "usemtl": {"Body": "Two Words"}}, "cube.obj")


if __name__ == "__main__":
    unittest.main()
