import sys
from pathlib import Path
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_w160_function_permission as runner


class FunctionPermissionLogTests(unittest.TestCase):
    def test_only_the_declared_function_load_error_is_counted(self):
        lines = ["[12:00:00] [Worker-Main-1/ERROR] [minecraft/ServerFunctionLibrary]: Failed to load function advancedrocketrycommunity:w160_level_two",
                 "java.util.concurrent.CompletionException: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Incorrect argument for command at position 57: ...000000160 <--[HERE]",
                 "\tat java.util.concurrent.CompletableFuture.encodeThrowable(CompletableFuture.java:315)",
                 "Caused by: java.lang.IllegalArgumentException: Whilst parsing command on line 1: Incorrect argument for command at position 57: ...000000160 <--[HERE]",
                 "\tat net.minecraft.commands.CommandFunction.m_77984_(CommandFunction.java:69)"]
        with patch.object(runner.smoke, "scan_log", return_value=[]):
            self.assertEqual(1, runner.audit_log(lines)["observed"])
            for bad in ([], lines + lines, [lines[0].replace("level_two", "unrelated")] + lines[1:],
                        [lines[0]] + [line.replace("IllegalArgumentException", "NullPointerException") for line in lines[1:]]):
                with self.assertRaises(runner.smoke.SmokeError): runner.audit_log(bad)


if __name__ == "__main__":
    unittest.main()
