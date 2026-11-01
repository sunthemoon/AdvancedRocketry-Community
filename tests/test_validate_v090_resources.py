import json
import tempfile
import unittest
from unittest import mock
from pathlib import Path

from scripts.validate_v090_resources import (
    ROOT,
    _placeholder_arguments,
    _placeholder_contract,
    audit_languages,
    audit_resources,
)


class V090ResourceAuditTests(unittest.TestCase):
    def _obj_fixture(self, model: dict, mesh: str | None = "motor.obj") -> list[str]:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            assets = root / "src/generated/v1.8/resources/assets/advancedrocketrycommunity"
            (assets / "models/block").mkdir(parents=True)
            (assets / "models/block/motor.json").write_text(json.dumps(model), encoding="utf-8")
            if mesh is not None:
                (assets / f"models/block/{mesh}").write_text("fixture existence only", encoding="utf-8")
            # UI source contracts are a separate concern from these minimal resource fixtures.
            with mock.patch("scripts.validate_v090_resources.ACCESSIBILITY_CONTRACTS", {}):
                return audit_resources(root)[1]

    def test_obj_loader_metadata_and_mesh_are_not_texture_ids(self) -> None:
        self.assertEqual([], self._obj_fixture({"loader": "forge:obj",
                         "model": "advancedrocketrycommunity:models/block/motor.obj"}))

    def test_obj_loader_requires_the_actual_mesh_file(self) -> None:
        errors = self._obj_fixture({"loader": "forge:obj",
                                   "model": "advancedrocketrycommunity:models/block/motor.obj"}, None)
        self.assertEqual(["missing asset reference assets/advancedrocketrycommunity/models/block/motor.obj "
                          "from assets/advancedrocketrycommunity/models/block/motor.json"], errors)

    def test_obj_loader_keeps_texture_and_case_checks(self) -> None:
        errors = self._obj_fixture({"loader": "forge:obj",
                                   "model": "advancedrocketrycommunity:models/block/motor.obj",
                                   "textures": {"particle": "advancedrocketrycommunity:block/missing"}}, "Motor.obj")
        self.assertTrue(any("case-mismatched asset reference" in error and "motor.obj" in error for error in errors))
        self.assertTrue(any("missing asset reference assets/advancedrocketrycommunity/textures/block/missing.png"
                            in error for error in errors))

    def test_unknown_model_loaders_are_not_silently_skipped(self) -> None:
        errors = self._obj_fixture({"loader": "fixture:unknown"})
        self.assertTrue(any("unsupported model loader" in error for error in errors))

    def test_obj_loader_requires_an_obj_model_location(self) -> None:
        for model in ({"loader": "forge:obj"}, {"loader": "forge:obj", "model": "minecraft:block/cube_all"}):
            self.assertTrue(any("requires an OBJ model resource location" in error for error in self._obj_fixture(model)))

    def test_obj_texture_slots_named_loader_and_model_still_require_pngs(self) -> None:
        errors = self._obj_fixture({"loader": "forge:obj",
                                   "model": "advancedrocketrycommunity:models/block/motor.obj",
                                   "textures": {"loader": "advancedrocketrycommunity:block/loader_sprite",
                                                "model": "advancedrocketrycommunity:block/model_sprite"}})
        self.assertEqual(2, len(errors))
        for name in ("loader_sprite", "model_sprite"):
            self.assertTrue(any(f"textures/block/{name}.png" in error for error in errors), errors)

    def test_obj_loader_keys_are_metadata_and_mtl_override_is_refused_clearly(self) -> None:
        # W249-R9: Forge's ObjLoader reads these top-level keys; none of them names a texture.
        errors = self._obj_fixture({"loader": "forge:obj",
                                   "model": "advancedrocketrycommunity:models/block/motor.obj",
                                   "mtl_override": "advancedrocketrycommunity:models/block/other.mtl",
                                   "flip_v": "advancedrocketrycommunity:block/flip_v",
                                   "automatic_culling": False, "shade_quads": True, "emissive_ambient": True})
        self.assertEqual(["forge:obj mtl_override is not supported; the atlas test does not follow it either, from "
                          "assets/advancedrocketrycommunity/models/block/motor.json"], errors)

    def test_obj_texture_slots_named_like_loader_keys_still_require_pngs(self) -> None:
        errors = self._obj_fixture({"loader": "forge:obj",
                                   "model": "advancedrocketrycommunity:models/block/motor.obj",
                                   "textures": {"mtl_override": "advancedrocketrycommunity:block/override_sprite",
                                                "flip_v": "advancedrocketrycommunity:block/flip_sprite"}})
        self.assertEqual(2, len(errors))
        for name in ("override_sprite", "flip_sprite"):
            self.assertTrue(any(f"textures/block/{name}.png" in error for error in errors), errors)

    def test_repository_resource_contract_passes(self) -> None:
        summary, errors = audit_resources(ROOT)

        self.assertEqual([], errors)
        self.assertEqual("PASS", summary["result"])
        self.assertGreater(summary["en_us_keys"], 100)
        self.assertEqual(summary["en_us_keys"], summary["zh_cn_keys"])
        self.assertEqual(4, summary["textual_status_surfaces"])

    def test_placeholder_contract_ignores_literal_percent(self) -> None:
        self.assertEqual(("%s", "%2$d"), _placeholder_contract("Fuel %s / %2$d %%"))

    def test_placeholder_arguments_resolve_as_minecraft_does(self) -> None:
        self.assertEqual(((1, "s"), (2, "d")), _placeholder_arguments("Fuel %s / %2$d %%"))
        self.assertEqual(_placeholder_arguments("%s to %s"), _placeholder_arguments("%2$s 从 %1$s"))
        self.assertEqual(((1, "s"), (2, "s")), _placeholder_arguments("%2$s then %s"))

    def test_language_audit_accepts_reordered_arguments_only(self) -> None:
        en_text = {
            "station.advancedrocketrycommunity.reordered": "[%1$s] Warp to %2$s in %3$s s",
            "station.advancedrocketrycommunity.renumbered": "[%1$s] Warp to %2$s in %3$s s",
            "station.advancedrocketrycommunity.repeated": "%1$s to %2$s",
        }
        zh_text = {
            "station.advancedrocketrycommunity.reordered": "[%1$s] %3$s 秒后跃迁至 %2$s",
            "station.advancedrocketrycommunity.renumbered": "[%1$s] %3$s 秒后跃迁至 %3$s",
            "station.advancedrocketrycommunity.repeated": "%1$s 到 %2$s（%2$s）",
        }
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            en = root / "en_us.json"
            zh = root / "zh_cn.json"
            en.write_text(json.dumps(en_text), encoding="utf-8")
            zh.write_text(json.dumps(zh_text, ensure_ascii=False), encoding="utf-8")

            _, errors = audit_languages((en, zh))

        self.assertEqual(
            [
                "translation placeholder contract differs for "
                "station.advancedrocketrycommunity.renumbered",
                "translation placeholder contract differs for "
                "station.advancedrocketrycommunity.repeated",
            ],
            errors,
        )

    def test_language_audit_rejects_missing_key_and_placeholder_drift(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            en = root / "en_us.json"
            zh = root / "zh_cn.json"
            en.write_text(
                json.dumps({"screen.advancedrocketrycommunity.test": "Value %s", "only.en": "x"}),
                encoding="utf-8",
            )
            zh.write_text(
                json.dumps({"screen.advancedrocketrycommunity.test": "值 %s %s"}),
                encoding="utf-8",
            )

            _, errors = audit_languages((en, zh))

        self.assertTrue(any("missing translation key only.en" in error for error in errors))
        self.assertTrue(any("placeholder contract differs" in error for error in errors))


if __name__ == "__main__":
    unittest.main()
