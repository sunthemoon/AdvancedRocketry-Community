#!/usr/bin/env python3
"""Check the v1.2 machine DataGen outputs and their local resource references."""

from __future__ import annotations

import argparse
import json
from pathlib import Path


MOD_ID = "advancedrocketrycommunity"
BLOCKS = (
    "rolling_machine",
    "rolling_machine_item_input_port",
    "rolling_machine_fluid_input_port",
    "rolling_machine_energy_input_port",
    "rolling_machine_item_output_port",
    "precision_assembler",
    "precision_assembler_item_input_port",
    "precision_assembler_item_output_port",
    "precision_assembler_energy_input_port",
)
PRECISION_STATUSES = {
    "formation": (
        "unformed", "formed", "waiting_unloaded", "invalid_definition",
        "binding_conflict", "unsupported_data",
    ),
    "process": (
        "idle", "running", "waiting_input", "waiting_output", "waiting_energy",
        "redstone_disabled", "invalid_recipe", "unsupported_data", "recovery_required",
    ),
    "failure": (
        "none", "missing_item_input", "missing_fluid_input", "output_blocked",
        "insufficient_energy", "redstone_disabled", "invalid_recipe",
        "stale_transaction", "journal_conflict", "recovery_diverged",
        "arithmetic_overflow",
    ),
    "diagnostic": (
        "block_mismatch", "chunk_not_loaded", "transform_not_allowed", "position_overflow",
    ),
}


def validate(root: Path) -> list[str]:
    errors: list[str] = []
    generated = root / "src/generated/v1.2/resources"
    resource_roots = [root / "src/main/resources", root / "src/generated/resources"]
    resource_roots.extend(sorted((root / "src/generated").glob("v*/resources")))

    def load(relative: str) -> dict:
        path = generated / relative
        try:
            value = json.loads(path.read_text(encoding="utf-8"))
            if not isinstance(value, dict):
                raise ValueError("root must be an object")
            return value
        except (OSError, ValueError) as exc:
            errors.append(f"{relative}: {exc}")
            return {}

    def local_reference(reference: str, kind: str, owner: str) -> None:
        if reference.startswith("#"):
            return
        namespace, separator, path = reference.partition(":")
        if not separator:
            errors.append(f"{owner}: unqualified {kind} reference {reference}")
            return
        if namespace == "minecraft":
            return
        if namespace != MOD_ID or not path or ".." in Path(path).parts:
            errors.append(f"{owner}: invalid {kind} reference {reference}")
            return
        suffix = "json" if kind == "model" else "png"
        relative = Path("assets") / namespace / f"{kind}s" / f"{path}.{suffix}"
        if not any((base / relative).is_file() for base in resource_roots):
            errors.append(f"{owner}: missing {kind} reference {reference}")

    for name in BLOCKS:
        block_id = f"{MOD_ID}:{name}"
        blockstate = load(f"assets/{MOD_ID}/blockstates/{name}.json")
        variants = blockstate.get("variants", {})
        if not isinstance(variants, dict) or not variants:
            errors.append(f"{name}: missing blockstate variants")
        else:
            for variant, entry in variants.items():
                if not isinstance(entry, dict) or entry.get("model") != f"{MOD_ID}:block/{name}":
                    errors.append(f"{name}: unexpected model in variant {variant}")
                else:
                    local_reference(entry["model"], "model", name)

        block_model = load(f"assets/{MOD_ID}/models/block/{name}.json")
        if isinstance(block_model.get("parent"), str):
            local_reference(block_model["parent"], "model", name)
        textures = block_model.get("textures", {})
        if not isinstance(textures, dict) or not textures:
            errors.append(f"{name}: block model has no textures")
        else:
            for reference in textures.values():
                if not isinstance(reference, str):
                    errors.append(f"{name}: invalid texture reference")
                else:
                    local_reference(reference, "texture", name)

        item_model = load(f"assets/{MOD_ID}/models/item/{name}.json")
        if item_model.get("parent") != f"{MOD_ID}:block/{name}":
            errors.append(f"{name}: item model does not reference its block model")

        loot = load(f"data/{MOD_ID}/loot_tables/blocks/{name}.json")
        entries = [entry for pool in loot.get("pools", []) if isinstance(pool, dict)
                   for entry in pool.get("entries", []) if isinstance(entry, dict)]
        if not any(entry.get("type") == "minecraft:item" and entry.get("name") == block_id
                   for entry in entries):
            errors.append(f"{name}: self-drop loot entry is missing")

        recipe = load(f"data/{MOD_ID}/recipes/{name}.json")
        if recipe.get("type") != "minecraft:crafting_shaped" or recipe.get("result", {}).get("item") != block_id:
            errors.append(f"{name}: shaped crafting recipe result is missing or wrong")
        advancement = load(f"data/{MOD_ID}/advancements/recipes/misc/{name}.json")
        if f"{MOD_ID}:{name}" not in advancement.get("rewards", {}).get("recipes", []):
            errors.append(f"{name}: recipe advancement is missing")

    for tag in ("mineable/pickaxe", "needs_iron_tool"):
        values = load(f"data/minecraft/tags/blocks/{tag}.json").get("values", [])
        missing = {f"{MOD_ID}:{name}" for name in BLOCKS} - set(values)
        if missing:
            errors.append(f"{tag}: missing blocks {sorted(missing)}")

    english = load(f"assets/{MOD_ID}_v120/lang/en_us.json")
    chinese = load(f"assets/{MOD_ID}_v120/lang/zh_cn.json")
    if set(english) != set(chinese):
        errors.append("v1.2 English and Chinese translation keys differ")
    required = {f"block.{MOD_ID}.{name}" for name in BLOCKS}
    required.update(f"menu.{MOD_ID}.{name}" for name in ("rolling_machine", "precision_assembler"))
    required.update(f"screen.{MOD_ID}.precision_assembler.{key}" for key in (
        "inputs", "outputs", "formation", "process", "diagnostic_location"))
    required.add(f"tooltip.{MOD_ID}.precision_assembler.progress")
    required.update(
        f"status.{MOD_ID}.precision_assembler.{group}.{value}"
        for group, values in PRECISION_STATUSES.items() for value in values
    )
    for locale, translations in (("en_us", english), ("zh_cn", chinese)):
        missing = required - set(translations)
        if missing:
            errors.append(f"{locale}: missing translations {sorted(missing)}")

    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()
    errors = validate(args.root.resolve())
    for error in errors:
        print(f"FAIL: {error}")
    if errors:
        return 1
    print(f"PASS: {len(BLOCKS)} machine blocks, recipes, loot, models, tags and bilingual keys")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
