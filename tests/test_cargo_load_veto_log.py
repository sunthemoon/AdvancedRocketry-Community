"""C2's retained load-veto EventBus error, with mutations of its scope and form."""
import hashlib
from pathlib import Path
import sys
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_cargo_s4_s5_cuts as fluid
import run_v180_item_cuts as item
import run_v180_cargo_owner_cap as cap


def veto_error():
    # C2 cargo-C-S4-LOAD-attempt1/native/FS_LOAD_VETO_DESTINATION_SAVED-0.log,
    # from the ERROR header through its throwable, without the following INFO event.
    data = (Path(__file__).parent / "fixtures/cargo/load-settlement-veto-event.log").read_bytes()
    assert hashlib.sha256(data).hexdigest() == "bf639b6eb7f4004181b0ac3a5dc638140479f97f032abdbf274b571206e06add"
    return data.decode().splitlines()


def observation(case, restart):
    return fluid.marker(case, restart) + ("" if restart else "boundary=" + fluid.BOUNDARIES[case])


class CargoLoadVetoLogTest(unittest.TestCase):
    def test_retained_event_passes_only_the_injecting_case_and_start(self):
        error = veto_error()
        case = "FS_LOAD_VETO_DESTINATION_SAVED"
        fluid.observations(error + [observation(case, 0)], case, 0)
        # CapturedProcess supplies newline-terminated strings, unlike read_text().splitlines().
        fluid.observations([line + "\r\n" for line in error + [observation(case, 0)]], case, 0)
        for case, spec in fluid.CASES.items():
            for restart in (0, 1, 2):
                if case == "FS_LOAD_VETO_DESTINATION_SAVED" and restart == 0:
                    continue
                marker = fluid.marker(case, restart)
                if not restart and case in fluid.BOUNDARIES:
                    marker += "boundary=" + fluid.BOUNDARIES[case]
                with self.subTest(case=case, restart=restart), self.assertRaises(fluid.smoke.SmokeError):
                    fluid.observations(error + [marker], case, restart)

    def test_wrong_reason_header_count_and_fatal_fail(self):
        original = veto_error()
        for lines in (
            [], original[1:], original + original, original + [original[0]],
            [line.replace("source veto", "another veto") for line in original],
            [line.replace("/ERROR]", "/FATAL]") for line in original],
            [original[0].replace("Server thread", "Worker thread")] + original[1:],
            [original[0].replace("ne.mi.ev.EventBus/EVENTBUS", "foreign/logger")] + original[1:],
            original + ["[Server thread/ERROR] [foreign/]: another failure"],
            original + ["[Server thread/FATAL] [foreign/]: failure"],
        ):
            with self.subTest(lines=lines[:1]):
                with self.assertRaises(fluid.smoke.SmokeError):
                    fluid.check_load_veto_error(lines)

    def test_listener_throwable_and_complete_stack_are_checked(self):
        original = veto_error()
        for lines in (
            [line for line in original if "GuardedChunkSaves.fail(" not in line],
            [line for line in original if "EscrowChunkStore.terrain(" not in line],
            [line for line in original if "EscrowSettlementRecovery.begin(" not in line],
            [line.replace("\tIndex: 2", "\tIndex: 1") for line in original],
            [line.replace("\t\t2: ASM:", "\t\t3: ASM:") for line in original],
            [line.replace("GuardedChunkSaves beforeSave", "ForeignGuard beforeSave") for line in original],
            [line.replace("IllegalStateException:", "RuntimeException:") for line in original],
            [line.replace("EscrowSaveMonitor.postOwn", "EscrowSaveMonitor.observed") for line in original],
            original + ["Caused by: java.lang.IllegalStateException: unrelated failure"],
            original + ["Suppressed: java.lang.IllegalStateException: unrelated failure"],
            original + ["java.lang.RuntimeException: unrelated failure"],
            original + ["\tat net.minecraft.server.level.ChunkMap.write(ChunkMap.java:831)"],
            original + ["padding"] * 97,
        ):
            with self.subTest(lines=lines[-1:]), self.assertRaises(fluid.smoke.SmokeError):
                fluid.check_load_veto_error(lines)
        changed = [line.replace("Lambda$3991/0x0000000801638d38@69c63f47", "Lambda$42/0xabcdef@123abc")
                   .replace("lambda$tick$12", "lambda$tick$99") for line in original]
        fluid.check_load_veto_error(changed)

    def test_other_w330_cases_have_no_error_allowance(self):
        error = veto_error()
        for case in fluid.CASES:
            if not case.startswith("FS_LOAD_"):
                continue
            for restart in (0, 1, 2):
                if case == "FS_LOAD_VETO_DESTINATION_SAVED" and restart == 0:
                    continue
                fluid.observations([observation(case, restart)], case, restart)
        for case in item.CASES:
            if not case.startswith("IS_LOAD_"):
                continue
            for restart in (0, 1, 2):
                marker = item.marker(case, restart) + ("" if restart else "boundary=" + item.CASES[case]["boundary"])
                item.observations([marker], case, restart)
                with self.subTest(case=case, restart=restart), self.assertRaises(fluid.smoke.SmokeError):
                    item.observations(error + [marker], case, restart)
        for case in cap.CASES:
            for restart in (0, 1, 2):
                marker = ("ARCE_RELEASE_CARGO_CUT_RESULT cut=" + case + " pending=16 refused=OWNER_PENDING_FULL independent_epoch=1"
                          if restart else "ARCE_RELEASE_CARGO_CUT_PAUSED cut=" + case + " boundary=OWNER_PENDING_REFUSED")
                cap.observations([marker], case, restart)
                with self.subTest(case=case, restart=restart), self.assertRaises(fluid.smoke.SmokeError):
                    cap.observations(error + [marker], case, restart)


if __name__ == "__main__":
    unittest.main()
