import json
import unittest

from scripts.campaign_client.evidence import MASK, copy_log, dumps, redact, write_json
from scripts.campaign_client.server import server_command, write_settings
from test_campaign_client_support import Fixture


class EvidenceTests(Fixture):
    def test_redaction_forms(self):
        values = ["--accessToken secret", '--accessToken "secret value"', "--accessToken='secret value'",
                  '"accessToken": "secret"', "accessToken=secret", "clientId: secret", '"xuid":"secret"',
                  "'clientId' = 'secret'", "--accessToken\tsecret", "ACCESStoken : secret",
                  "--clientId secret", "--xuid=secret"]
        for value in values:
            with self.subTest(value=value):
                self.assertNotIn("secret", redact(value))
                self.assertIn(MASK, redact(value))
        self.assertEqual("normal player log", redact("normal player log"))

    def test_json_output_never_contains_secret_values(self):
        value = {"clientId": "secret", "nested": [{"accessToken": "secret"}],
                 "line": "xuid=secret", "path": self.root}
        text = dumps(value)
        self.assertNotIn("secret", text)
        self.assertEqual(str(self.root), json.loads(text)["path"])
        write_json(self.root / "summary.json", value)
        self.assertNotIn("secret", (self.root / "summary.json").read_text(encoding="utf-8"))

    def test_copied_logs_are_redacted(self):
        source, target = self.root / "source.log", self.root / "target.log"
        source.write_text('debug --accessToken secret\n{"clientId":"secret", "xuid":"secret"}\n', encoding="utf-8")
        copy_log(source, target)
        self.assertNotIn("secret", target.read_text(encoding="utf-8"))
        self.assertIn("secret", source.read_text(encoding="utf-8"))

    def test_oversized_log_line_refused(self):
        source = self.root / "source.log"
        source.write_text("a" * 65530 + " --accessToken secret\n", encoding="utf-8")
        target = self.root / "target.log"
        with self.assertRaises(ValueError):
            copy_log(source, target)
        self.assertEqual("", target.read_text(encoding="utf-8"))

    def test_server_settings_whitelist_and_ops(self):
        properties = self.server / "server.properties"
        properties.write_text("motd=retained\nonline-mode=false\nenable-rcon=true\nrcon.password=old\n"
                              " server-port : 1\nwhite-list false\n", encoding="utf-8")
        self.settings["ops"] = list(self.settings["whitelist"])
        write_settings(self.server, self.settings)
        value = properties.read_text(encoding="utf-8")
        self.assertIn("motd=retained", value)
        self.assertIn("online-mode=true", value)
        self.assertIn("white-list=true", value)
        self.assertIn("enforce-whitelist=true", value)
        self.assertIn("server-ip=127.0.0.1", value)
        self.assertIn("server-port=25565", value)
        self.assertIn("enable-rcon=false", value)
        self.assertNotIn("old", value)
        self.assertEqual(1, value.count("server-port"))
        self.assertEqual(self.settings["whitelist"], json.loads((self.server / "whitelist.json").read_text()))
        ops = json.loads((self.server / "ops.json").read_text())
        self.assertEqual(4, ops[0]["level"])
        self.assertFalse(ops[0]["bypassesPlayerLimit"])

    def test_escaped_properties_refused(self):
        (self.server / "server.properties").write_text("online\\-mode=false", encoding="utf-8")
        with self.assertRaises(ValueError):
            write_settings(self.server, self.settings)

    def test_vanilla_escaped_values_preserved_but_continuations_refused(self):
        properties = self.server / "server.properties"
        properties.write_text("level-type=minecraft\\:normal\nresource-pack=https\\://example.invalid/a\n", encoding="utf-8")
        write_settings(self.server, self.settings)
        self.assertIn("level-type=minecraft\\:normal", properties.read_text())
        properties.write_text("motd=continued\\\nonline-mode=false\n", encoding="utf-8")
        with self.assertRaises(ValueError):
            write_settings(self.server, self.settings)

    def test_system_properties_are_single_arguments_without_shell(self):
        args = self.server / "libraries/net/minecraftforge/forge/1.20.1-47.4.10/win_args.txt"
        args.parent.mkdir(parents=True, exist_ok=True)
        args.write_text("fixture", encoding="utf-8")
        self.settings["jvm_properties"]["arce.extra"] = "value with spaces & chars"
        command = server_command("java.exe", self.server, "47.4.10", self.settings)
        self.assertIn("-Darce.extra=value with spaces & chars", command)
        self.assertEqual("nogui", command[-1])
        self.assertEqual("@" + str(args), command[-2])


if __name__ == "__main__":
    unittest.main()
