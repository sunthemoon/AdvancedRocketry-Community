"""Linux-safe safety/command tests; no server or external campaign input needed."""
import json
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from scripts.campaign_prep import k03_chain as k


class ChainTests(unittest.TestCase):
    def test_chain_is_strictly_historical(self):
        self.assertEqual(list(k.PREVIOUS.items()), [("FI05","FI04"),("FI06","FI05"),("FI07","FI06"),("FI08","FI07")])
        self.assertEqual(len({v[2] for v in k.PINS.values()}), 4)
        self.assertNotIn(k.C6_HASH, [v[2] for v in k.PINS.values()])

    def test_actors_are_distinct_uuids(self):
        self.assertEqual(k.actors(k.DEFAULT_OWNER, k.DEFAULT_MEMBER), (k.DEFAULT_OWNER,k.DEFAULT_MEMBER))
        with self.assertRaises(ValueError):
            k.actors(k.DEFAULT_OWNER, k.DEFAULT_OWNER)
        with self.assertRaises(ValueError):
            k.actors("not a uuid", k.DEFAULT_MEMBER)

    def test_seal_detects_mutation_and_additional_world_file(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder / "source-world").mkdir()
            file = folder / "source-world/level.dat"
            file.write_bytes(b"original")
            k.seal(folder)
            k.verify_seal(folder)
            with self.assertRaises(ValueError):
                k.seal(folder)
            file.write_bytes(b"modified")
            with self.assertRaises(ValueError):
                k.verify_seal(folder)
            file.write_bytes(b"original")
            (folder / "source-world/extra.dat").write_bytes(b"extra")
            with self.assertRaises(ValueError):
                k.verify_seal(folder)

    def test_later_qualification_does_not_reseal_source(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            (folder / "manifest.json").write_text("{}")
            k.seal(folder)
            before = k.digest(folder / "SHA256SUMS.txt")
            k.write(folder / "qualification/receipt.json", {"status":"OBSERVATION"})
            k.verify_seal(folder)
            self.assertEqual(before, k.digest(folder / "SHA256SUMS.txt"))

    def test_unsafe_checksum_members(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            for member in ("../x", "/x", "D:/x", "a\\b"):
                (folder / "SHA256SUMS.txt").write_text("0" * 64 + "  " + member + "\n")
                with self.assertRaises(ValueError):
                    k.verify_seal(folder)

    def test_historical_pin_rejects_substitute(self):
        with tempfile.TemporaryDirectory() as tmp:
            folder = Path(tmp)
            jar = folder / "v1.4.0/advancedrocketry-community-1.20.1-1.4.0-dev.jar"
            jar.parent.mkdir()
            jar.write_bytes(b"newer jar")
            with self.assertRaisesRegex(ValueError, "W429"):
                k.host_pin("FI05", folder)

    def test_honest_uncovered_scope(self):
        self.assertTrue(all("historically played" in k.uncovered(n)[0] for n in k.PINS))
        self.assertIn("player actors", " ".join(k.uncovered("FI06")))
        self.assertIn("READY", " ".join(k.uncovered("FI07")))

    def test_fi05_uses_only_host_research_and_station_commands(self):
        class Fake:
            def __init__(self):
                self.commands = []
            def query(self, command, marker, timeout=60):
                self.commands.append(command)
                return "mission=" + k.DEFAULT_MEMBER
        c = Fake()
        with patch.object(k.time, "sleep"):
            k.populate(c, "FI05", k.DEFAULT_OWNER, k.DEFAULT_MEMBER)
        self.assertEqual(len(c.commands), 5)
        self.assertIn("mars", c.commands[0])
        self.assertIn("venus", c.commands[1])
        self.assertEqual(sum("release-test claim" in s for s in c.commands), 2)
        self.assertTrue(c.commands[-1].startswith("arce station admin create"))
        self.assertFalse(any("data merge" in s or "data modify" in s for s in c.commands))

    def test_fi07_records_three_terminal_hook_results_without_force_tickets(self):
        class Fake:
            def __init__(self): self.commands = []
            def query(self, command, marker, timeout=60):
                self.commands.append(command)
                return "status=READY"
        c = Fake()
        made = k.populate(c, "FI07", k.DEFAULT_OWNER, k.DEFAULT_MEMBER)
        self.assertEqual(len(made["terminals"]), 3)
        self.assertEqual(len({row["owner"] for row in made["terminals"]}), 3)
        self.assertFalse(any("forceload" in s for s in c.commands))

    def test_qualification_reports_authority_difference(self):
        empty = dict(roots={}, entities=[])
        changed = dict(roots={k.NS+"_stations.dat": {"data":{"stations":[{"id":"x"}]}}}, entities=[])
        self.assertTrue(all(k.check_qualification(empty, empty).values()))
        result = k.check_qualification(empty, changed)
        self.assertFalse(result["stations"])
        self.assertTrue(result["research_accounts"])

    def test_fi06_uses_distinct_owners_and_native_pad_readback(self):
        class Fake:
            def __init__(self): self.commands = []
            def query(self, command, marker, timeout=60):
                self.commands.append(command)
                if "admin create" in command: return "Created id=" + k.DEFAULT_MEMBER
                if "admin inspect" in command: return "region=0,0,1,1 pad=10,80,20"
                return "ok"
        c = Fake()
        made = k.populate(c, "FI06", k.DEFAULT_OWNER, k.DEFAULT_MEMBER)
        self.assertEqual({s["owner"] for s in made["stations"]},
                         {k.derived_actor(k.DEFAULT_OWNER,256),k.derived_actor(k.DEFAULT_MEMBER,256)})
        self.assertNotIn(k.DEFAULT_OWNER, {s["owner"] for s in made["stations"]})
        self.assertEqual(made["stations"][0]["core"], [14,80,24])
        self.assertEqual(made["stations"][0]["core_energy"], 0)
        self.assertFalse(any("data merge" in s or "warp confirm" in s for s in c.commands))

    def test_fi08_waits_for_registered_endpoints_before_binding(self):
        class Fake:
            def __init__(self): self.commands = []
            def query(self, command, marker, timeout=60):
                self.commands.append(command)
                if "elevator build" in command:
                    return "station=" + k.DEFAULT_OWNER + " terminal_pos=1,2,3"
                return "endpoint status=OK"
        c = Fake()
        k.populate(c, "FI08", k.DEFAULT_OWNER, k.DEFAULT_MEMBER)
        binding = next(i for i,s in enumerate(c.commands) if "elevator bind" in s)
        persisted = next(i for i,s in enumerate(c.commands) if "persist root" in s)
        self.assertLess(persisted, binding)
        self.assertTrue(any("space run arce endgame release-test endpoint 1 2 3" in s for s in c.commands[:persisted]))

    def test_qualification_retains_instance_and_device_differences(self):
        before = dict(roots={}, entities=[])
        after = dict(roots={k.NS+"_satellite_missions.dat":{"data":{"instances":[{"id":"instance"}]}},
                           k.NS+"_endgame.dat":{"data":{"endpoints":[{"id":"device"}]}}}, entities=[])
        result = k.check_qualification(before, after)
        self.assertFalse(result["instances"])
        self.assertFalse(result["device_endpoints"])


if __name__ == "__main__":
    unittest.main()
