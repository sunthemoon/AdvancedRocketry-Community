import tempfile
import unittest
from pathlib import Path

from scripts.collect_v090_compatibility_evidence import (
    EXPECTED_JEI,
    EXPECTED_VERSION,
    validate_client_log,
)
from scripts.run_dedicated_server_smoke import SmokeError


def client_log(*, forge: str, jei: bool, username: str) -> str:
    lines = [
        f"[Render thread/INFO] [minecraft/]: Setting user: {username}",
        f"[worker/INFO] [forge/]: Forge mod loading, version {forge}, for MC 1.20.1",
        f"[worker/INFO] [advancedrocketrycommunity/]: Advanced Rocketry: Community Edition {EXPECTED_VERSION} initialized",
        "[Render thread/INFO] [minecraft/]: Connecting to 127.0.0.1, 25605",
        "[Netty/INFO] [forge/]: Connected to a modded server.",
    ]
    if jei:
        lines.extend((
            f"[worker/INFO] [advancedrocketrycommunity/]: ARCE-BETA-1100 optional_compat=jei status=present version={EXPECTED_JEI}",
            "[Render/INFO] [advancedrocketrycommunity/]: ARCE-BETA-1100 optional_compat=jei status=registered recipes=1",
        ))
    else:
        lines.append(
            "[worker/INFO] [advancedrocketrycommunity/]: ARCE-BETA-1100 optional_compat=jei status=absent version=absent"
        )
    return "\n".join(lines) + "\n"


class V090CompatibilityEvidenceTests(unittest.TestCase):
    def test_present_and_absent_contracts_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for jei, username in ((True, "JeiP10"), (False, "NoJei10")):
                path = root / f"{username}.log"
                path.write_text(
                    client_log(forge="47.4.10", jei=jei, username=username),
                    encoding="utf-8",
                )
                result, _ = validate_client_log(
                    path, forge="47.4.10", jei_present=jei, username=username
                )
                self.assertEqual("PASS", result["result"])
                self.assertEqual(1 if jei else 0, result["jei_recipe_count"])

    def test_unknown_recipe_category_is_blocking(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "client.log"
            path.write_text(
                client_log(forge="47.4.10", jei=True, username="JeiP10")
                + "Unknown recipe category: advancedrocketrycommunity:electrolyzing\n",
                encoding="utf-8",
            )
            with self.assertRaisesRegex(SmokeError, "Unknown recipe category"):
                validate_client_log(
                    path, forge="47.4.10", jei_present=True, username="JeiP10"
                )

    def test_present_cell_requires_final_synchronized_recipe(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "client.log"
            text = client_log(forge="47.4.23", jei=True, username="JeiP23")
            path.write_text(text.replace("recipes=1", "recipes=0"), encoding="utf-8")
            with self.assertRaisesRegex(SmokeError, "recipes=1"):
                validate_client_log(
                    path, forge="47.4.23", jei_present=True, username="JeiP23"
                )


if __name__ == "__main__":
    unittest.main()
