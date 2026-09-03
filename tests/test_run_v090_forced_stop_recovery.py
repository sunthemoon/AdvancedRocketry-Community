import re
import tempfile
import unittest
from pathlib import Path

from scripts.run_dedicated_server_smoke import SmokeError
from scripts.run_v090_forced_stop_recovery import (
    CHECKPOINT,
    EXPECTED_ACTION,
    EXPECTED_PHASE,
    _write_evidence,
    validate_forced_exit_code,
    validate_recovery_receipt,
)


class V090ForcedStopRecoveryTests(unittest.TestCase):
    def test_fixed_checkpoint_is_post_destination_precommit_authority(self) -> None:
        self.assertEqual("DESTINATION_SPAWNED", CHECKPOINT)
        self.assertEqual("DESTINATION_SPAWNED", EXPECTED_PHASE)
        self.assertEqual("REMOVE_SOURCE_KEEP_DESTINATION", EXPECTED_ACTION)

    def test_clean_exit_cannot_claim_a_forced_stop(self) -> None:
        with self.assertRaisesRegex(SmokeError, "exited cleanly"):
            validate_forced_exit_code(0)
        validate_forced_exit_code(1)
        validate_forced_exit_code(-9)

    def test_recovery_receipt_requires_exact_dual_presence_and_action(self) -> None:
        transfer = "11111111-1111-1111-1111-111111111111"
        pattern = re.compile(
            r"transfer=([0-9a-f-]{36}) phase=([A-Z_]+) source=(\d+) "
            r"destination=(\d+) action=([A-Z_]+) status=([A-Z_]+)"
        )
        valid = pattern.fullmatch(
            f"transfer={transfer} phase={EXPECTED_PHASE} source=1 destination=1 "
            f"action={EXPECTED_ACTION} status=RECOVERED"
        )
        assert valid is not None
        result = validate_recovery_receipt(valid, transfer)
        self.assertEqual("RECOVERED", result["status"])
        self.assertEqual(1, result["source_count_before"])

        invalid = pattern.fullmatch(
            f"transfer={transfer} phase={EXPECTED_PHASE} source=1 destination=2 "
            f"action={EXPECTED_ACTION} status=RECOVERED"
        )
        assert invalid is not None
        with self.assertRaisesRegex(SmokeError, "authority contract"):
            validate_recovery_receipt(invalid, transfer)

    def test_evidence_writer_refuses_to_replace_existing_directory(self) -> None:
        with tempfile.TemporaryDirectory() as temporary:
            output = Path(temporary) / "evidence"
            output.mkdir()
            with self.assertRaisesRegex(SmokeError, "Refusing to overwrite"):
                _write_evidence(output, {}, {}, [])


if __name__ == "__main__":
    unittest.main()
