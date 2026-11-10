"""W341: complete-group retirement in live observations and stopped worlds."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path[:0] = [str(Path(__file__).resolve().parents[1] / "scripts"), str(Path(__file__).resolve().parent)]
import test_bay_w331 as fixtures
import run_v180_bay_forced_stop as bay


def leftover(phase="REGISTERED", released=False):
    return dict(op=fixtures.SURVEY, satellite=fixtures.SURVEY, phase=phase, reason="NONE",
                acknowledged=True, released=released)


class CompleteReceiptTests(unittest.TestCase):
    def test_every_w331_retired_outcome_refuses_any_member_receipt_and_a_lock(self):
        for case, spec in bay.W331_CASES.items():
            fixture = fixtures.fixture(spec["fixture"])
            for restart, name in enumerate(spec["restarts"], 1):
                if name not in ("LANDED", "CONSUMED_REST"):
                    continue
                consumed = [fixtures.OP if role != "solar" else None for role in fixture["roles"]]
                packages = "+".join(f"c{i}" if role != "solar" else f"p{i}" for i, role in enumerate(fixture["roles"]))
                good = fixtures.status(consumed=consumed, packages=packages, owned=spec.get("owned", 1))
                if name == "CONSUMED_REST":
                    good.update(state="FUELED", transfer="NONE", y="2")
                self.assertTrue(bay.w331_settled(spec, name, good, fixture), case)
                bay.w331_check(case, spec, name, good, fixture)
                for change in ([dict(locked="true")] + [dict(receipts=[dict(leftover(phase, released), satellite=satellite)])
                        for satellite in fixture["satellites"] for phase, released in
                        (("REGISTERED", False), ("REPAIR", False), ("REGISTERED", True))]):
                    with self.subTest(case=case, restart=restart, change=change):
                        bad = dict(good, **change)
                        self.assertFalse(bay.w331_settled(spec, name, bad, fixture))
                        with self.assertRaises(bay.smoke.SmokeError):
                            bay.w331_check(case, spec, name, bad, fixture)

    def test_trio_receipts_are_refused_on_both_restart_disks(self):
        fixture = fixtures.fixture()
        with tempfile.TemporaryDirectory() as directory:
            for case in ("W331_B8_TRIO_STAGED", "W331_B8_TRIO_MOVED"):
                for restart in (1, 2):
                    root = fixtures.world(Path(directory) / f"{case}-{restart}", [], [fixtures.DATA, fixtures.SURVEY])
                    bay.w331_disk(case, root, fixture, restart)
                    for phase, released in (("REGISTERED", False), ("REPAIR", False), ("REGISTERED", True)):
                        row = fixtures.receipt(fixtures.SURVEY, fixtures.L, fixtures.SURVEY, phase)
                        row.value["released"].value = int(released)
                        fixtures.world(root, [row], [fixtures.DATA, fixtures.SURVEY])
                        with self.subTest(case=case, restart=restart, phase=phase, released=released):
                            with self.assertRaisesRegex(bay.smoke.SmokeError, "complete receipt set"):
                                bay.w331_disk(case, root, fixture, restart)

    def test_interrupted_allows_only_one_acknowledged_unreleased_data_receipt(self):
        case = "W331_B17_PARTIAL_D1"
        spec, fixture = bay.W331_CASES[case], fixtures.fixture()
        row = dict(op=fixtures.OP, satellite=fixtures.DATA, phase="REJECTED", reason="INTERRUPTED", acknowledged=True)
        good = fixtures.status(state="FUELED", transfer="NONE", y="2", packages=fixture["packages"],
                               consumed=[None] * 3, owned=0, receipts=[row])
        bay.w331_check(case, spec, "INTERRUPTED", good, fixture)
        with tempfile.TemporaryDirectory() as directory:
            root = fixtures.world(Path(directory), [fixtures.receipt(fixtures.OP, fixtures.L, fixtures.DATA, "INTENT")])
            cut = bay.w331_disk(case, root, fixture, 0)
            for restart in (1, 2):
                for rows in ([], [row, row], [row, leftover()], [dict(row, acknowledged=False)],
                             [dict(row, released=True)], [dict(row, reason="SERVER_ERROR")], [dict(row, satellite=fixtures.SURVEY)]):
                    bad = dict(good, receipts=rows)
                    self.assertFalse(bay.w331_settled(spec, "INTERRUPTED", bad, fixture))
                    with self.assertRaises(bay.smoke.SmokeError):
                        bay.w331_check(case, spec, "INTERRUPTED", bad, fixture)
                    if any(not entry["acknowledged"] for entry in rows):
                        continue  # Acknowledgement is a runtime observation, not a persisted receipt field.
                    tags = [fixtures.receipt(entry["op"], fixtures.L, entry["satellite"], entry["phase"], entry["reason"])
                            for entry in rows]
                    for tag, entry in zip(tags, rows):
                        tag.value["released"].value = int(entry.get("released", False))
                    fixtures.world(root, tags)
                    with self.assertRaises(bay.smoke.SmokeError):
                        bay.w331_disk(case, root, fixture, restart, cut)

    def test_legacy_survey_landings_cannot_keep_a_survey_receipt(self):
        fixture = fixtures.fixture("survey")
        for case, spec in bay.CASES.items():
            if not spec.get("survey"):
                continue
            good = fixtures.status(packages="c0+c1", consumed=[fixtures.OP, fixtures.SURVEY], owned=spec.get("owned", 1))
            for restart in (1, 2):
                bay.check_settled(case, restart, good, fixture)
                for phase, released in (("REGISTERED", False), ("REPAIR", False), ("REGISTERED", True)):
                    bad = dict(good, receipts=[leftover(phase, released)])
                    self.assertFalse(bay.settled(case, restart, bad, fixture))
                    with self.assertRaises(bay.smoke.SmokeError):
                        bay.check_settled(case, restart, bad, fixture)
                    with tempfile.TemporaryDirectory() as directory:
                        tag = fixtures.receipt(fixtures.SURVEY, fixtures.L, fixtures.SURVEY, phase)
                        tag.value["released"].value = int(released)
                        root = fixtures.world(Path(directory), [tag])
                        with self.assertRaises(bay.smoke.SmokeError):
                            bay.settled_disk(case, root, fixture, None)

    def test_registered_cut_requires_each_registering_member_exactly_once(self):
        fixture = fixtures.fixture()
        rows = [fixtures.receipt(fixtures.OP, fixtures.L, fixtures.DATA, "REGISTERED"),
                fixtures.receipt(fixtures.SURVEY, fixtures.L, fixtures.SURVEY, "REGISTERED")]
        with tempfile.TemporaryDirectory() as directory:
            root = fixtures.world(Path(directory), rows)
            bay.w331_disk("W331_B8_TRIO_STAGED", root, fixture, 0)
            for wrong in ([rows[0], rows[0]], [rows[0], fixtures.receipt(fixtures.SOLAR, fixtures.L, fixtures.SOLAR, "REGISTERED")]):
                fixtures.world(root, wrong)
                with self.assertRaisesRegex(bay.smoke.SmokeError, "complete receipt set"):
                    bay.w331_disk("W331_B8_TRIO_STAGED", root, fixture, 0)

    def test_retirement_releases_reservations_without_erasing_unrelated_capacity(self):
        bay.check_retired_pending("retired", dict(pending=0, retained=0, work=False))
        bay.check_retired_pending("seeded", dict(pending=16, retained=16, work=False), (16, 16))
        for bad in (dict(pending=1, retained=0, work=False), dict(pending=0, retained=1, work=False),
                    dict(pending=0, retained=0, work=True)):
            with self.assertRaisesRegex(bay.smoke.SmokeError, "reservations"):
                bay.check_retired_pending("retired", bad)


if __name__ == "__main__":
    unittest.main()
