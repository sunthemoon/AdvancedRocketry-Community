"""W254 native bay-exit cuts: the harness's checks accept the expected exit evidence and refuse every changed field."""
from copy import deepcopy
import json
from pathlib import Path
import sys
import tempfile
import unittest
import zipfile

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_bay_exit_cuts as runner

SmokeError = runner.smoke.SmokeError
LOGICAL = "11111111-1111-4111-8111-111111111111"
TRANSFER = "22222222-2222-4222-8222-222222222222"


def describe(**overrides) -> str:
    values = dict(tick="400", service="true", live="1", refusals="0", histories="1", rider="online", mounted="false",
                  y="51.5", health="20.0", gameMode="survival", slot="NO_SAFE_EXIT", ordinal="0", offer="live",
                  accepted="false", history="issued", first="390", deadline="590", transfer="PREPARED", seat="0",
                  rocket="33333333-3333-4333-8333-333333333333", rocketY="50.0", rocketSeat="0", claim="OUTCOMES",
                  hold="NONE", reason="NONE", observer="true", landing="true")
    values.update(overrides)
    return " ".join(f"{key}={value}" for key, value in values.items())


def left_line(mode="sneak", **overrides) -> dict:
    head = dict(detached="true", counter="7>8", chunks="121>121", tickets="same", authority="same", riderY="51.5>51.5",
                riderHealth="20.0>20.0")
    head.update({key: overrides.pop(key) for key in list(overrides) if key in head})
    line = (f"[Server thread/INFO] ARCE_RELEASE_BAY_EXIT_LEFT logical={LOGICAL} mode={mode} "
            + " ".join(f"{key}={value}" for key, value in head.items()) + " " + describe(**overrides))
    return runner.fields(line, "LEFT")


def landed_line(**overrides) -> dict:
    head = dict(fall="10.25", landingHealth="20.0>20.0", attacks="[fall:true]", cancelled="1", landedY="41.0", onGround="true",
                chunks="121>121", tickets="same", authority="same")
    head.update({key: overrides.pop(key) for key in list(overrides) if key in head})
    line = (f"ARCE_RELEASE_BAY_EXIT_LANDED logical={LOGICAL} " + " ".join(f"{key}={value}" for key, value in head.items())
            + " " + describe(**dict(dict(offer="closed", y="41.0"), **overrides)))
    return runner.fields(line, "LANDED")


class CaseTable(unittest.TestCase):
    def test_every_r279_phase_has_a_case_and_the_tranche_fits(self):
        phases = {spec["phase"] for spec in runner.CASES.values()}
        self.assertTrue({"OFFER", "DESCENT", "CORRECTION", "LANDING", "EXPIRY", "TRUSTED"} <= phases)
        self.assertIn("NONE", phases)
        self.assertLessEqual(len(runner.CASES) * runner.STARTS_PER_CASE, 60)
        self.assertEqual({"GRANTED", "REVOKED"}, {spec["smoke"] for spec in runner.CASES.values() if "smoke" in spec})
        self.assertEqual({"sneak", "input", "menu"}, {spec["mode"] for spec in runner.CASES.values() if "mode" in spec})


class Fields(unittest.TestCase):
    def test_parses_key_values_after_the_marker(self):
        parsed = runner.fields("x ARCE_RELEASE_BAY_EXIT_STATE logical=a live=0 attacks=[fall:true]", "STATE")
        self.assertEqual(dict(logical="a", live="0", attacks="[fall:true]"), parsed)

    def test_refuses_a_missing_marker_or_a_duplicate_key(self):
        with self.assertRaises(SmokeError):
            runner.fields("ARCE_RELEASE_BAY_EXIT_STATE logical=a", "LEFT")
        with self.assertRaises(SmokeError):
            runner.fields("ARCE_RELEASE_BAY_EXIT_STATE live=0 live=1", "STATE")

    def test_pair_needs_exactly_two_sides(self):
        self.assertEqual(("1", "2"), runner.pair("1>2"))
        for value in ("1", "1>2>3"):
            with self.assertRaises(SmokeError):
                runner.pair(value)


class LeftChecks(unittest.TestCase):
    def test_a_granted_fall_exit_passes(self):
        runner.check_left(left_line(), "X2_DESCENT", 2)

    def test_each_changed_fact_refuses(self):
        changes = dict(detached="false", counter="7>9", chunks="121>122", tickets="changed:1:{}", authority="changed",
                       mounted="true", live="0", histories="0", seat="-1", rocketSeat="1", slot="SAFE", offer="none",
                       history="closed", deadline="600", y="50.0")
        for key, value in changes.items():
            with self.subTest(key=key), self.assertRaises(SmokeError):
                runner.check_left(left_line(**{key: value}), "X2_DESCENT", 2)

    def test_the_input_exit_records_but_does_not_assert_its_tick_long_ticket_window(self):
        runner.check_left(left_line(mode="input", tickets="changed:1:{x}"), "X1_OFFER", 2)
        with self.assertRaises(SmokeError):
            runner.check_left(left_line(mode="input", authority="changed"), "X1_OFFER", 2)

    def test_a_safe_menu_exit_stands_on_the_checked_pad_without_an_offer(self):
        safe = dict(slot="SAFE", offer="none", history="closed", y="2.0", riderY="51.5>2.0")
        runner.check_left(left_line(mode="menu", **safe), "X8_SAFE_MENU", 2)
        for key, value in dict(y="3.0", offer="live", history="issued").items():
            with self.subTest(key=key), self.assertRaises(SmokeError):
                runner.check_left(left_line(mode="menu", **dict(safe, **{key: value})), "X8_SAFE_MENU", 2)


class LandingChecks(unittest.TestCase):
    def test_granted_is_exactly_one_cancelled_fall_attempt(self):
        runner.check_landed(landed_line(), "X4_LANDING", True)
        for change in (dict(cancelled="0", attacks="[fall:false]"), dict(landingHealth="20.0>12.0"),
                       dict(attacks="[fall:true,fall:true]", cancelled="2"), dict(tickets="changed:1:{}"),
                       dict(authority="changed"), dict(fall="2.5"), dict(onGround="false"), dict(offer="live")):
            with self.subTest(change=change), self.assertRaises(SmokeError):
                runner.check_landed(landed_line(**change), "X4_LANDING", True)

    def test_revoked_is_an_unprotected_landing(self):
        revoked = dict(attacks="[fall:false]", cancelled="0", landingHealth="20.0>12.0", offer="none", live="0")
        runner.check_landed(landed_line(**revoked), "X7_REVOKED", False)
        with self.assertRaises(SmokeError):
            runner.check_landed(landed_line(**dict(revoked, landingHealth="20.0>20.0")), "X7_REVOKED", False)
        with self.assertRaises(SmokeError):
            runner.check_landed(landed_line(**dict(revoked, attacks="[fall:true]", cancelled="1")), "X7_REVOKED", False)


class ClosedAndRestartChecks(unittest.TestCase):
    def state(self, **overrides) -> dict:
        return runner.fields("ARCE_RELEASE_BAY_EXIT_STATE logical=x " + describe(**overrides), "STATE")

    def test_a_closed_offer_keeps_its_history_and_deadline(self):
        left = left_line()
        runner.check_closed(self.state(live="0", slot="none", offer="none"), "X5_EXPIRY", left)
        for change in (dict(offer="live"), dict(history="closed"), dict(deadline="700"), dict(first="391"), dict(seat="-1"),
                       dict(mounted="true")):
            with self.subTest(change=change), self.assertRaises(SmokeError):
                runner.check_closed(self.state(**dict(dict(live="0", slot="none", offer="none"), **change)), "X5_EXPIRY", left)

    def test_no_exit_state_survives_a_restart_and_the_seat_stays(self):
        clean = dict(live="0", histories="0", slot="none", offer="none", history="none", first="-1", deadline="-1",
                     rider="offline", observer="false")
        runner.check_restart_state(self.state(**clean), "X4_LANDING", 1, False)
        runner.check_restart_state(self.state(**dict(clean, rider="online", observer="true")), "X4_LANDING", 1, True)
        for change in (dict(live="1"), dict(histories="1"), dict(refusals="1"), dict(history="issued"), dict(slot="SAFE"),
                       dict(seat="-1"), dict(rocketSeat="-1"), dict(transfer="NONE"), dict(mounted="true"), dict(landing="false"),
                       dict(rider="online")):
            with self.subTest(change=change), self.assertRaises(SmokeError):
                runner.check_restart_state(self.state(**dict(clean, **change)), "X4_LANDING", 1, False)


class HoldAndControl(unittest.TestCase):
    def setUp(self):
        self.fixture = dict(logical=LOGICAL, transfer=TRANSFER, data="d", solar="s", packages="a+b")
        self.status = dict(claim="OUTCOMES", hold="REPAIR", reason="REGISTRY_BLOCKED", locked="true", transfer="PREPARED",
                           state="TRANSIT", y="50.0", packages="a+b", owned=0,
                           receipts=[dict(op="o", satellite="d", phase="INTENT", reason="NONE", acknowledged=True, registered=False)])

    def test_the_repair_hold_with_the_unchanged_intent_is_held(self):
        self.assertTrue(runner.held(self.status, self.fixture))
        for key, value in dict(claim="NONE", hold="NONE", reason="X", locked="false", transfer="NONE", state="LANDED", y="2.0",
                               packages="c+b", owned=1).items():
            with self.subTest(key=key):
                status = deepcopy(self.status); status[key] = value
                self.assertFalse(runner.held(status, self.fixture))
        for key, value in dict(phase="REGISTERED", acknowledged=False, registered=True, satellite="s").items():
            with self.subTest(receipt=key):
                status = deepcopy(self.status); status["receipts"][0][key] = value
                self.assertFalse(runner.held(status, self.fixture))

    def test_the_cut_status_is_the_paused_d2_registry_proof_hold(self):
        cut = dict(self.status, claim="OUTCOMES", hold="OWNED_WAIT", reason="BAY_REGISTRY_PROOF", owned=1, consumed=[None, None],
                   receipts=[dict(self.status["receipts"][0], registered=True)])
        runner.check_cut_status(cut, self.fixture, "X1_OFFER")
        for key, value in dict(claim="NONE", reason="BAY_RECEIPT_WRITE", transfer="NONE", locked="false", packages="c+b",
                               y="2.0", owned=0, consumed=["op", None]).items():
            with self.subTest(key=key), self.assertRaises(SmokeError):
                runner.check_cut_status(dict(cut, **{key: value}), self.fixture, "X1_OFFER")
        for key, value in dict(registered=False, acknowledged=False, phase="REGISTERED").items():
            with self.subTest(receipt=key), self.assertRaises(SmokeError):
                runner.check_cut_status(dict(cut, receipts=[dict(cut["receipts"][0], **{key: value})]), self.fixture, "X1_OFFER")

    def test_the_exit_must_leave_the_bay_status_unchanged(self):
        runner.check_status_unchanged(deepcopy(self.status), deepcopy(self.status), "X1_OFFER")
        with self.assertRaises(SmokeError):
            runner.check_status_unchanged(self.status, dict(self.status, y="49.0"), "X1_OFFER")

    def test_every_exit_case_must_restart_to_the_controls_outcome(self):
        row = lambda case, seat: dict(case=case, status="PASS", restarts=[dict(summary=dict(seat=seat, live="0"))] * 2)
        self.assertEqual("PASS", runner.compare_with_control([row("X0_CONTROL", "0"), row("X1_OFFER", "0")])["status"])
        failed = runner.compare_with_control([row("X0_CONTROL", "0"), row("X1_OFFER", "-1")])
        self.assertEqual("FAILED", failed["status"]); self.assertEqual(["X1_OFFER"], list(failed["differences"]))
        self.assertEqual("NOT_RUN", runner.compare_with_control([row("X1_OFFER", "0")])["status"])

    def test_one_outcome_line_with_the_expected_key(self):
        line = (f"ARCE_BAY_EXIT logical={LOGICAL} transfer={TRANSFER} player={runner.RIDER} seat=0 outcome=fall_offer "
                "code=NO_SAFE_EXIT candidate=0 attempt=1 capture=1")
        self.assertEqual(dict(outcome="fall_offer", code="NO_SAFE_EXIT", candidate=0),
                         runner.check_outcome_line([line], self.fixture, "X1_OFFER"))
        for lines in ([], [line, line], [line.replace("fall_offer", "safe")], [line.replace("seat=0", "seat=1")]):
            with self.subTest(lines=lines), self.assertRaises(SmokeError):
                runner.check_outcome_line(lines, self.fixture, "X1_OFFER")


class Closings(unittest.TestCase):
    def line(self, tick: int, logical: str = LOGICAL) -> str:
        return (f"[Server thread/INFO] ARCE_RELEASE_BAY_EXIT_CLOSED logical={logical} point=SERVER_END tick={tick} slot=removed "
                "closed=true authority=true lease=true deadline=349 counter=6 window=5:true:false last=150:8.5:51.15:8.5:0.0:true:true "
                "now=151:8.5:51.15:8.5:-0.07:true:true contact=false inspected=true first=null usage=Usage[candidates=0,_reads=3]")

    def test_one_end_at_or_after_its_stimulus_passes(self):
        lines = ["noise", self.line(360), self.line(100, logical="other")]
        self.assertEqual("360", runner.check_closing(lines, LOGICAL, "X5_EXPIRY", 349, "the deadline")["tick"])

    def test_an_earlier_end_or_a_second_end_or_none_refuses(self):
        for lines in ([self.line(151)], [self.line(360), self.line(361)], []):
            with self.subTest(lines=len(lines)), self.assertRaises(SmokeError):
                runner.check_closing(lines, LOGICAL, "X5_EXPIRY", 349, "the deadline")


class JarEvidence(unittest.TestCase):
    PREFIX = "io/github/sunthemoon/advancedrocketrycommunity/rocket/server/"

    def jar(self, directory: Path, service: bytes, lang: bool = True) -> Path:
        path = directory / "mod.jar"
        keys = {"message.advancedrocketrycommunity.bay_exit.fall_offer": "x", "message.advancedrocketrycommunity.bay_exit.safe": "y"}
        with zipfile.ZipFile(path, "w") as archive:
            archive.writestr("META-INF/MANIFEST.MF", "Manifest-Version: 1.0\nImplementation-Version: 1.8.0\n")
            archive.writestr("META-INF/mods.toml", "modId='advancedrocketrycommunity'\n")
            archive.writestr(self.PREFIX + "BayPassengerExitService.class", service)
            archive.writestr(self.PREFIX + "BayPassengerExitMotion.class", b"...f_9767_...")
            archive.writestr(self.PREFIX + "BayPassengerExitLanding.class", b"m_142535_ m_142072_ m_7840_ m_289599_ m_6469_")
            if lang:
                for locale in ("en_us", "zh_cn"):
                    archive.writestr(f"assets/advancedrocketrycommunity_v180_bay_exit/lang/{locale}.json", json.dumps(keys))
        return path

    def test_the_reobfuscated_jar_passes_with_its_hashes_and_language_keys(self):
        with tempfile.TemporaryDirectory() as directory:
            evidence = runner.jar_evidence(self.jar(Path(directory), b"...m_9774_..."))
            runner.check_jar(evidence)
            self.assertEqual(3, len(evidence["exit_classes"])); self.assertEqual(2, len(evidence["lang"]))
            self.assertEqual(64, len(evidence["sha256"])); self.assertIn("META-INF/MANIFEST.MF", evidence["hashes"])

    def test_a_named_dev_jar_or_missing_language_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SmokeError):
                runner.check_jar(runner.jar_evidence(self.jar(Path(directory), b"...teleport...")))
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaises(SmokeError):
                runner.check_jar(runner.jar_evidence(self.jar(Path(directory), b"...m_9774_...", lang=False)))


if __name__ == "__main__":
    unittest.main()
