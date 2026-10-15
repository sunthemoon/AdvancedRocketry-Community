import copy
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_pre_f2 as runner


class PreF2OracleTest(unittest.TestCase):
    def read_journal(self, data, payload=b"bounded NBT fixture"):
        with patch.object(runner.gzip, "open") as compressed, patch.object(runner, "NbtReader") as reader:
            compressed.return_value.__enter__.return_value.read.return_value = payload
            reader.return_value.read_root.return_value = {"data": data}
            result = runner.journal(Path("world"))
            compressed.return_value.__enter__.return_value.read.assert_called_once_with(16 * 1024 * 1024 + 1)
            return result

    def test_pre_f2_journal_requires_current_root_and_empty_first_landing_outbox(self):
        data = {"schema_version": 3, "transfers": [],
                "classic_first_moon_landing": {"schema_version": 1, "state": "EMPTY"}}
        self.assertEqual(data, self.read_journal(data))
        for key, value in [("schema_version", 2), ("schema_version", 4), ("transfers", {}),
                           ("classic_first_moon_landing", None),
                           ("classic_first_moon_landing", {"schema_version": 2, "state": "EMPTY"}),
                           ("classic_first_moon_landing", {"schema_version": 1, "state": "ELECTED"}),
                           ("classic_first_moon_landing", {"schema_version": 1, "state": "EMPTY", "event": {}})]:
            changed = copy.deepcopy(data)
            changed[key] = value
            with self.subTest(key=key, value=value), self.assertRaises(runner.smoke.SmokeError):
                self.read_journal(changed)
        with self.assertRaises(runner.smoke.SmokeError):
            self.read_journal(data, b"x" * (16 * 1024 * 1024 + 1))

    def test_only_the_two_written_record_cases_expect_armed_after_restart(self):
        self.assertEqual(len(runner.CASES), 5)
        self.assertEqual([cut for cut in runner.CASES if runner.has_transfer(cut)],
                         ["PF_T_ACK", "PF_GOVERNED_T_ACK"])

    def test_governed_oracle_requires_independent_versions_exact_arm_and_no_publication(self):
        arm = {"serial": 1, "outcome": "ARMED", "d": "fixed-destination"}
        record = {"schema_version": 3, "phase": "PREPARED",
                  "source_flight": {"schema_version": 2}, "destination_flight": {"schema_version": 2},
                  "native_transfer": {"schema_version": 1, "armed": arm.copy(), "signature_kind": "FUEL_VECTOR_V1"}}
        runner.require_governed_record(record, arm)
        for key, value in [("schema_version", 2), ("phase", "COMMITTED"),
                           ("destination_entity_id", "fixed-destination"), ("native_transfer", {})]:
            changed = copy.deepcopy(record)
            changed[key] = value
            with self.assertRaises(runner.smoke.SmokeError):
                runner.require_governed_record(changed, arm)
        changed = copy.deepcopy(record)
        changed["native_transfer"]["armed"]["serial"] = 2
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_governed_record(changed, arm)
        changed = copy.deepcopy(record)
        changed["source_flight"]["schema_version"] = 3
        with self.assertRaises(runner.smoke.SmokeError):
            runner.require_governed_record(changed, arm)


if __name__ == "__main__":
    unittest.main()
