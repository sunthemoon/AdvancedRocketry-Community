"""W373B's exact account oracle: positive real-codec states and one negative control per checked property."""
import copy
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import accounting_cut_oracle as oracle
import escrow_accounting as accounting
import run_v180_escrow_forced_stop as native
from run_dedicated_server_smoke import SmokeError

FIXTURES = Path(__file__).parent / "fixtures/escrow_accounting"


def ledger(name):
    """A ledger written by the real codec (fluid cargo load: the device is the source)."""
    return native.ledger(FIXTURES / (name + ".dat"))


def ends(state):
    return copy.deepcopy(state["source"]), copy.deepcopy(state["destination"])


def observed(state, markers=None):
    """Raw stores as a runner extracts them; `markers` replaces both marker counts."""
    result = []
    for end in ends(state):
        if end is not None and markers is not None:
            end["markers"] = markers
        result.append(end)
    return result


class AccountingCutOracleTest(unittest.TestCase):
    def check(self, name, cut, start=0, root=None, stores=None):
        state = oracle.settlement(cut, True)[start]
        source, destination = stores or observed(state)
        return oracle.check_state(root or ledger(name), source, destination, state)

    def test_real_codec_states_match_their_cuts(self):
        for name, cut, start in (("cargo-planned", "O1_BEFORE", 0), ("cargo-known-o1", "O1_AFTER", 0),
                                 ("cargo-known-o1", "AC_O1_ACK", 0), ("cargo-known-o3", "O3_AFTER", 0),
                                 ("cargo-known-o3", "AC_O3_ACK", 0), ("cargo-known-o4", "O4_AFTER", 0),
                                 ("cargo-known-o4", "AC_O4_ACK", 1), ("cargo-known-o4", "PRUNE_AFTER", 2)):
            with self.subTest(name=name, cut=cut):
                summary = self.check(name, cut, start)
                self.assertEqual(1, summary["rows"])

    def test_a_planned_row_is_exact_and_the_key_digest_is_recomputed(self):
        root = ledger("cargo-planned")
        oracle.check_rows(root, [oracle.PLANNED])
        for mutate in (lambda r: r["fluid_accounts"][0]["b"]["e"][0].update(s=4999),
                       lambda r: r["fluid_accounts"][0]["b"]["e"][0].update(d=[0] * 32),
                       lambda r: r["fluid_accounts"].clear(),
                       lambda r: r["fluid_accounts"].append(dict(copy.deepcopy(r["fluid_accounts"][0]), op=[1, 2, 3, 4])),
                       lambda r: r.update(schema_version=2),
                       lambda r: r.update(format_epoch=accounting.EPOCHS[2])):
            bad = copy.deepcopy(root); mutate(bad)
            with self.assertRaises(SmokeError): oracle.check_rows(bad, [oracle.PLANNED])
        # The fixture value is compared, not only the row's own consistency.
        for wrong in (oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 1000), oracle.row(oracle.FUEL_SCOPE, oracle.WATER, 5000, 0),
                      oracle.row(oracle.CARGO_SCOPE, oracle.LAVA, 5000, 0), oracle.SETTLED):
            with self.assertRaises(SmokeError): oracle.check_rows(root, [wrong])
        with self.assertRaisesRegex(SmokeError, "owners"): oracle.check_rows(root, [])

    def test_a_fresh_fluid_operation_or_receipt_cannot_take_the_legacy_branch(self):
        root = ledger("cargo-planned"); root["fluid_accounts"] = []
        op = root["records"][0]; op["schema_version"] = 2; op.pop("accounting_baseline")
        accounting.validate_root(root)  # A legacy operation is structurally valid, so only fresh_fluid refuses it.
        state = oracle.settlement("O1_BEFORE", True)[0]
        with self.assertRaisesRegex(SmokeError, "legacy branch"): oracle.check_state(root, *observed(state), state)
        with self.assertRaisesRegex(SmokeError, "legacy branch"): oracle.check_rows(root, [])

    def test_a_frozen_row_needs_its_exact_entries_knowledge_and_signed_balance(self):
        root = ledger("cargo-known-o1")
        with self.assertRaisesRegex(SmokeError, "frozen part"): oracle.check_rows(root, [oracle.PLANNED])
        for entries, knowledge in (([(oracle.WATER, 4000, 1000, 100)], oracle.KNOWN), ([(oracle.WATER, 4000, 900, 0)], oracle.KNOWN),
                                   ([(oracle.WATER, 4000, 1000, None)], oracle.KNOWN), ([(oracle.WATER, 4000, 1000, 0)], oracle.UNKNOWN_ENDPOINT),
                                   ([(oracle.LAVA, 0, 0, 0), (oracle.WATER, 4000, 1000, 0)], oracle.KNOWN)):
            with self.assertRaises(SmokeError):
                oracle.check_rows(root, [oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 0, entries, knowledge)])
        state = oracle.settlement("O1_AFTER", True)[0]
        # A raw store that lost 100 mB while the row approves nothing: the residual is recomputed from the stores.
        source, destination = observed(state); source["fluids"][oracle.WATER] = 3900
        with self.assertRaisesRegex(SmokeError, "Raw source amounts"): oracle.check_state(root, source, destination, state)
        with self.assertRaisesRegex(SmokeError, "frozen observation"): oracle.check_frozen(state["rows"][0], source, destination)
        # A wrong sign: the stores lost 100 mB and the row calls it a gain.
        gain = oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 0, [(oracle.WATER, 3900, 1000, -100)])
        with self.assertRaisesRegex(SmokeError, "Signed residual"): oracle.check_frozen(gain, source, destination)
        loss = oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 0, [(oracle.WATER, 3900, 1000, 100)])
        oracle.check_frozen(loss, source, destination)
        # Another fluid in a store has to be a frozen key.
        source, destination = observed(state); destination["fluids"][oracle.LAVA] = 1
        with self.assertRaisesRegex(SmokeError, "no frozen account key"): oracle.check_frozen(state["rows"][0], source, destination)

    def test_epochs_markers_sequence_phase_and_totals_are_exact(self):
        root = ledger("cargo-known-o1"); state = oracle.settlement("O1_AFTER", True)[0]
        for change, message in ((lambda s, d: s.update(epoch=0), "epoch"), (lambda s, d: d.update(markers=0), "marker count"),
                                (lambda s, d: d.update(epoch=2), "epoch")):
            source, destination = observed(state); change(source, destination)
            with self.assertRaisesRegex(SmokeError, message): oracle.check_state(root, source, destination, state)
        with self.assertRaisesRegex(SmokeError, "store is missing"): oracle.check_state(root, None, observed(state)[1], state)
        bad = copy.deepcopy(root); bad["issued_through"] = 2
        with self.assertRaisesRegex(SmokeError, "Issued sequence"): oracle.check_state(bad, *observed(state), state)
        bad = copy.deepcopy(root); bad["records"][0]["phase"] = "REPAIR"
        with self.assertRaisesRegex(SmokeError, "records differ"): oracle.check_state(bad, *observed(state), state)
        wrong = dict(state, totals={oracle.WATER: 5001})
        with self.assertRaisesRegex(SmokeError, "total"): oracle.check_state(root, *observed(state), wrong)
        with self.assertRaisesRegex(SmokeError, "Unknown balance"): oracle.check_state(root, *observed(state), dict(state, balance="ZERO"))
        with self.assertRaisesRegex(SmokeError, "no escrow ledger"): oracle.check_state(None, *observed(state), state)

    def test_receipts_have_their_release_knowledge_and_missing_count(self):
        root = ledger("cargo-known-o3")
        oracle.check_receipts(root, [(False, "KNOWN", 0)])
        for wrong in ([(True, "KNOWN", 0)], [(False, "UNKNOWN", 0)], [(False, "KNOWN", 1)], []):
            with self.assertRaisesRegex(SmokeError, "Receipts differ"): oracle.check_receipts(root, wrong)
        missing = ledger("cargo-missing-o4")
        oracle.check_receipts(missing, [(True, "UNKNOWN", 1)])
        oracle.check_rows(missing, [oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 0, [(oracle.WATER, 4000, None, None)],
                                               oracle.UNKNOWN_ENDPOINT)])
        # An unknown contribution is never a zero amount.
        with self.assertRaises(SmokeError):
            oracle.check_rows(missing, [oracle.row(oracle.CARGO_SCOPE, oracle.WATER, 5000, 0, [(oracle.WATER, 4000, 0, None)],
                                                   oracle.UNKNOWN_ENDPOINT)])

    def test_restarts_keep_baselines_frozen_rows_and_receipts(self):
        planned, o1, o3, o4 = (ledger("cargo-" + name) for name in ("planned", "known-o1", "known-o3", "known-o4"))
        for root in (o1, o3, o4):  # The fixtures are separate codec runs; give them one ledger identity and one decision.
            root["ledger_id"] = planned["ledger_id"]
        oracle.check_progress(o3, o4); oracle.check_progress(o4, copy.deepcopy(o4))
        retired = copy.deepcopy(planned); retired.update(records=[], fluid_accounts=[])
        oracle.check_progress(planned, retired)
        changed = copy.deepcopy(o4); changed["fluid_accounts"][0]["f"]["e"][0]["a"] = 1
        with self.assertRaises(SmokeError): oracle.check_progress(o3, changed)
        rebased = copy.deepcopy(planned); rebased["fluid_accounts"][0]["b"]["e"][0]["s"] = 4000
        with self.assertRaises(SmokeError): oracle.check_progress(planned, rebased)
        lost = copy.deepcopy(o4); lost["tombstones"] = []; lost["fluid_accounts"] = []
        with self.assertRaisesRegex(SmokeError, "lost a receipt|frozen account"): oracle.check_progress(o4, lost)
        withdrawn = copy.deepcopy(o4); withdrawn["tombstones"][0]["released"] = 0
        with self.assertRaisesRegex(SmokeError, "withdrew a release"): oracle.check_progress(o4, withdrawn)
        restated = copy.deepcopy(o4); restated["tombstones"][0]["accounting_status"] = "UNKNOWN"
        with self.assertRaises(SmokeError): oracle.check_progress(o4, restated)
        for key, value in (("ledger_id", [0, 0, 0, 1]), ("writer_generation", -1), ("issued_through", 0)):
            regressed = copy.deepcopy(o4); regressed[key] = value
            with self.assertRaisesRegex(SmokeError, "regressed"): oracle.check_progress(o4, regressed)

    def test_every_table_state_is_self_consistent_in_both_directions(self):
        cuts = ("O1_BEFORE", "O1_AFTER", "AC_O1_ACK", "O3_BEFORE", "O3_AFTER", "AC_O3_ACK", "O4_BEFORE", "O4_AFTER",
                "AC_O4_ACK", "PRUNE_BEFORE", "PRUNE_AFTER", "AC_LOSS", "AC_GAIN", "AC_SUBSTITUTE", "AC_READBACK",
                "ABANDON_ROCKET", "ABANDON_DEVICE")
        for load in (False, True):
            for cut in cuts:
                states = oracle.settlement(cut, load)
                self.assertEqual(3, len(states))
                for state in states:
                    with self.subTest(cut=cut, load=load):
                        source, destination = observed(state)
                        if state["balance"] == "PLAN": oracle.check_plan(state["rows"][0], source, destination)
                        if state["balance"] == "FROZEN": oracle.check_frozen(state["rows"][0], source, destination)
                        for fluid, total in state["totals"].items():
                            self.assertEqual(total, oracle.raw_total((source, destination), fluid))
        for cut in ("L1_BEFORE", "L1_REPLACED", "L1_ACK", "C6_JOINT_READBACK", "L2_REPLACED", "C7_ACKNOWLEDGED",
                    "E4L1_UNKNOWN", "E4L2_UNKNOWN"):
            for state in oracle.ordinary(cut):
                source, destination = observed(state)
                if state["balance"] == "PLAN": oracle.check_plan(state["rows"][0], source, destination)
                self.assertEqual(5000, oracle.raw_total((source, destination), oracle.WATER))
        with self.assertRaises(SmokeError): oracle.ordinary("C8_RELEASED")
        with self.assertRaises(SmokeError): oracle.settlement("O2_ROCKET_AFTER", False)

    def test_the_signed_outcomes_are_the_designed_values(self):
        entries = lambda cut, load: oracle.settlement(cut, load)[0]["rows"][0]["frozen"]["entries"]
        # Unload: the rocket is the source. Load: the device is the source.
        self.assertEqual([(oracle.WATER, 4000, 900, 100)], entries("AC_LOSS", False))
        self.assertEqual([(oracle.WATER, 3900, 1000, 100)], entries("AC_LOSS", True))
        self.assertEqual([(oracle.WATER, 4000, 1100, -100)], entries("AC_GAIN", False))
        self.assertEqual([(oracle.WATER, 4100, 1000, -100)], entries("AC_GAIN", True))
        # A substitution is a loss of one key and a gain of another, never a zero scalar balance.
        self.assertEqual([(oracle.LAVA, 0, 1000, -1000), (oracle.WATER, 4000, 0, 1000)], entries("AC_SUBSTITUTE", False))
        self.assertEqual([(oracle.LAVA, 4000, 0, -4000), (oracle.WATER, 0, 1000, 4000)], entries("AC_SUBSTITUTE", True))
        self.assertEqual({oracle.WATER: 4900}, oracle.settlement("AC_LOSS", False)[2]["totals"])
        self.assertEqual({oracle.WATER: 5100}, oracle.settlement("AC_GAIN", True)[2]["totals"])
        # ABANDON: the missing side is absent, not zero; the survivor keeps its BEFORE amount.
        self.assertEqual([(oracle.WATER, None, 0, None)], entries("ABANDON_ROCKET", False))
        self.assertEqual([(oracle.WATER, 5000, None, None)], entries("ABANDON_DEVICE", False))
        self.assertEqual([(oracle.WATER, 5000, None, None)], entries("ABANDON_ROCKET", True))
        self.assertEqual([(oracle.WATER, None, 0, None)], entries("ABANDON_DEVICE", True))

    def test_a_held_readback_must_differ_from_the_frozen_choice_and_release_nothing(self):
        root = ledger("cargo-known-o1"); state = oracle.settlement("AC_READBACK", True)[0]
        oracle.check_state(root, *observed(state), state)
        # Stores that equal the frozen choice are not this case's hold.
        same = oracle.settlement("O1_AFTER", True)[0]
        with self.assertRaisesRegex(SmokeError, "did not hold"):
            oracle.check_state(root, *observed(same), dict(state, source=same["source"], destination=same["destination"], totals={}))
        released = ledger("cargo-known-o3")
        with self.assertRaises(SmokeError): oracle.check_state(released, *observed(state), state)

    def test_planned_identity_covers_every_partial_application(self):
        spec = oracle.PLANNED
        for source, destination in (((5000, 0), (0, 0)), ((4000, 1), (0, 0)), ((5000, 0), (1000, 1)), ((4000, 1), (1000, 1))):
            oracle.check_plan(spec, oracle.store(oracle.WATER, *source), oracle.store(oracle.WATER, *destination))
        for source, destination in (((4000, 0), (0, 0)), ((5000, 1), (0, 0)), ((5000, 0), (1000, 0)), ((4000, 1), (900, 1))):
            with self.assertRaisesRegex(SmokeError, "planned transfer identity"):
                oracle.check_plan(spec, oracle.store(oracle.WATER, *source), oracle.store(oracle.WATER, *destination))
        with self.assertRaisesRegex(SmokeError, "Another fluid"):
            oracle.check_plan(spec, oracle.store(oracle.WATER, 5000, 0), oracle.store(oracle.LAVA, 1, 0))
        with self.assertRaisesRegex(SmokeError, "repeated"):
            oracle.check_plan(spec, oracle.store(oracle.WATER, 5000, 2), oracle.store(oracle.WATER, 0, 0))

    def loader_disk(self, root, tanks, bank):
        devices = [dict(arce_fluid_fuel_loader=dict(device=[0, 0, 0, index], tank=dict(FluidName=oracle.WATER, Amount=amount),
                        resource_escrow=dict(resource_epoch=0, markers=[]))) for index, amount in enumerate(tanks)]
        primary = dict(amount=bank, fluid=oracle.WATER) if bank else dict(amount=0)
        rocket = dict(RocketEntityData=dict(resource_escrow=dict(resource_epoch=0, markers=[]),
                      flight_data=dict(fuel=dict(propulsion=dict(primary=primary, oxidizer=dict(amount=0))))))
        return dict(ledger=root, loaders=devices, rocket=rocket)

    def test_loader_rows_are_fuel_scope_and_a_chained_baseline_is_the_projection(self):
        with patch.object(oracle, "FUEL", oracle.WATER):  # The codec fixture's fill moves water.
            root = ledger("fuel-planned"); root["records"][0]["phase"] = "PREPARED"
            oracle.check_loader(self.loader_disk(root, [5000], 0), "LF_M_D2_C1_REPLACED", 0)
            oracle.check_loader(self.loader_disk(root, [4000], 1000), "LF_M_D2_C7_BEFORE", 0)
            with self.assertRaisesRegex(SmokeError, "bank"): oracle.check_loader(self.loader_disk(root, [5000], 1000), "LF_M_D2_C1_ACK", 0)
            with self.assertRaisesRegex(SmokeError, "tank"): oracle.check_loader(self.loader_disk(root, [5000], 1000), "LF_M_D2_C7_BEFORE", 0)
            with self.assertRaisesRegex(SmokeError, "records differ"): oracle.check_loader(self.loader_disk(root, [5000], 0), "LF_M_D2_C1_BEFORE", 0)
            empty = copy.deepcopy(root); empty.update(records=[], fluid_accounts=[])
            oracle.check_loader(self.loader_disk(empty, [5000], 0), "LF_M_D2_C1_BEFORE", 1)
            oracle.check_loader(self.loader_disk(empty, [4000], 1000), "LF_M_D2_C7_ACK", 2)
            oracle.check_loader(self.loader_disk(None, [5000], 0), "LF_M_E4L1_UNKNOWN", 0)
            with self.assertRaisesRegex(SmokeError, "without a ledger"): oracle.check_loader(self.loader_disk(None, [4000], 1000), "LF_M_D2_C7_ACK", 0)
            # The partial chain cut keeps only the second fill, whose destination baseline is the first fill's AFTER.
            chained = copy.deepcopy(root); chained["records"][0]["seq"] = 2
            with self.assertRaisesRegex(SmokeError, "baseline differs"):
                oracle.check_loader(self.loader_disk(chained, [4000, 4000], 2000), "LF_M_C2_PARTIAL_REPLACED", 0)
            with self.assertRaisesRegex(SmokeError, "loader count"): oracle.check_loader(self.loader_disk(root, [5000], 0), "LF_M_C2", 0)
            with self.assertRaisesRegex(SmokeError, "No loader accounting"): oracle.check_loader(self.loader_disk(root, [5000], 0), "LF_M_K5", 0)
            tagged = self.loader_disk(root, [5000], 0); tagged["loaders"][0]["arce_fluid_fuel_loader"]["tank"]["Tag"] = {}
            with self.assertRaisesRegex(SmokeError, "tagged"): oracle.check_loader(tagged, "LF_M_D2_C1_REPLACED", 0)
            tagged = self.loader_disk(root, [4000], 1000)
            tagged["rocket"]["RocketEntityData"]["flight_data"]["fuel"]["propulsion"]["primary"]["tag"] = {}
            with self.assertRaisesRegex(SmokeError, "tagged"): oracle.check_loader(tagged, "LF_M_D2_C7_BEFORE", 0)
            # No resource created: a bank that gained without a debit breaks the raw total.
            with self.assertRaises(SmokeError): oracle.check_loader(self.loader_disk(empty, [5000], 1000), "LF_M_D2_C7_ACK", 1)

    def settlement_disk(self, root, tank, bank, markers=1, epoch=1):
        section = lambda: dict(resource_epoch=epoch, markers=[{}] * markers)
        device = dict(arce_fluid_fuel_loader=dict(device=[0, 0, 0, 1], tank=dict(FluidName=oracle.WATER, Amount=tank) if tank else {},
                                                  resource_escrow=section()))
        primary = dict(amount=bank, fluid=oracle.WATER) if bank else dict(amount=0)
        rocket = dict(RocketEntityData=dict(resource_escrow=section(),
                      flight_data=dict(fuel=dict(propulsion=dict(primary=primary, oxidizer=dict(amount=0))))))
        return dict(ledger=root, loaders=[device], rocket=rocket)

    def test_loader_settlement_cuts_match_the_real_codec_states(self):
        """W373C: the loader's O1 to O4 and pruning cuts, with the codec fixtures of a settled fuel-scope fill."""
        planned, o1, o3, o4 = (ledger("fuel-" + name) for name in ("planned", "known-o1", "known-o3", "known-o4"))
        with patch.object(oracle, "FUEL", oracle.WATER):  # The codec fixture's fill moves water.
            check = lambda root, cut, start, markers: oracle.check_loader_settlement(self.settlement_disk(root, 4000, 1000, markers), cut, start)
            for start in range(3):
                self.assertEqual(["REPAIR"], check(planned, "LF_S6_O1_BEFORE", start, 1)["records"])
            for name in oracle.LOADER_O1_AFTER[:-2]:
                self.assertEqual((1, 0), (lambda s: (s["rows"], s["receipts"]))(check(o1, oracle.LOADER_SETTLEMENT_PREFIX + name, 0, 1)))
            for name in oracle.LOADER_O3_AFTER:
                self.assertEqual(1, check(o3, oracle.LOADER_SETTLEMENT_PREFIX + name, 0, 1)["receipts"])
            for name in oracle.LOADER_O4_AFTER:
                self.assertEqual([], check(o4, oracle.LOADER_SETTLEMENT_PREFIX + name, 0, 1)["records"])
            unchanged = [name for name in oracle.LOADER_SETTLEMENT if name not in ("O1_BEFORE", "AC_LOSS", "AC_GAIN")]
            self.assertEqual(14, len(unchanged))
            for name in unchanged:
                for start in (1, 2):
                    check(o4, oracle.LOADER_SETTLEMENT_PREFIX + name, start, 0)
            check(o4, "LF_S6_AC_PRUNE_AFTER", 0, 0)
            # Each ledger state belongs to its own cut only.
            for root, cut, start, markers in ((o1, "LF_S6_O1_BEFORE", 0, 1), (planned, "LF_S6_O1_REPLACED", 0, 1), (o1, "LF_S6_O3_REPLACED", 0, 1),
                                              (o3, "LF_S6_O3_BEFORE", 0, 1), (o3, "LF_S6_O4_REPLACED", 0, 1), (o4, "LF_S6_O4_BEFORE", 0, 1),
                                              (o3, "LF_S6_O2_AFTER_1", 1, 0), (o1, "LF_S6_AC_PRUNE_AFTER", 0, 0)):
                with self.subTest(cut=cut, start=start), self.assertRaises(SmokeError): check(root, cut, start, markers)
            # Markers are exact at the cut and after the restarts: pruning is stored only by PRUNE_AFTER.
            for cut, start, markers in (("LF_S6_O4_ACK", 0, 0), ("LF_S6_AC_PRUNE_BEFORE", 0, 0), ("LF_S6_AC_PRUNE_AFTER", 0, 1),
                                        ("LF_S6_O4_ACK", 1, 1), ("LF_S6_AC_PRUNE_BEFORE", 2, 1)):
                with self.subTest(cut=cut, start=start), self.assertRaisesRegex(SmokeError, "marker count"): check(o4, cut, start, markers)
            one_sided = self.settlement_disk(o4, 4000, 1000, 1); one_sided["rocket"]["RocketEntityData"]["resource_escrow"]["markers"] = []
            with self.assertRaisesRegex(SmokeError, "Raw destination marker count differs: 0, expected 1"):
                oracle.check_loader_settlement(one_sided, "LF_S6_AC_PRUNE_BEFORE", 0)
            # Amounts, epochs and the loader count are exact; a raw change the row does not approve fails the balance.
            for disk, message in ((self.settlement_disk(o1, 3900, 1000), "Raw source amounts"), (self.settlement_disk(o1, 4000, 1100), "Raw destination amounts"),
                                  (self.settlement_disk(o1, 4000, 1000, epoch=0), "epoch"), (dict(self.settlement_disk(o1, 4000, 1000), loaders=[]), "loader count"),
                                  (dict(self.settlement_disk(o1, 4000, 1000), ledger=None), "not a propulsion fill")):
                with self.assertRaisesRegex(SmokeError, message): oracle.check_loader_settlement(disk, "LF_S6_O1_ACK", 0)
            cargo = copy.deepcopy(o1); cargo["records"][0]["kind"] = "FLUID_CARGO_LOAD"
            with self.assertRaisesRegex(SmokeError, "not a propulsion fill"):
                oracle.check_loader_settlement(self.settlement_disk(cargo, 4000, 1000), "LF_S6_O1_ACK", 0)
            legacy = copy.deepcopy(planned); legacy["fluid_accounts"] = []
            legacy["records"][0]["schema_version"] = 2; legacy["records"][0].pop("accounting_baseline")
            with self.assertRaisesRegex(SmokeError, "legacy branch"):
                oracle.check_loader_settlement(self.settlement_disk(legacy, 4000, 1000), "LF_S6_O1_BEFORE", 0)

    def test_a_loader_loss_and_gain_freeze_their_signed_adjustment(self):
        """W373C: the held tank changed by 100 mB; the unchanged fixture row approves nothing, so it cannot pass."""
        entries = lambda cut, start=0: oracle.loader_settlement(cut)[start]["rows"][0]["frozen"]["entries"]
        self.assertEqual([(oracle.FUEL, 3900, 1000, 100)], entries("LF_S6_AC_LOSS"))
        self.assertEqual([(oracle.FUEL, 4100, 1000, -100)], entries("LF_S6_AC_GAIN"))
        self.assertEqual([(oracle.FUEL, 4000, 1000, 0)], entries("LF_S6_O4_ACK"))
        for cut, total in (("LF_S6_AC_LOSS", 4900), ("LF_S6_AC_GAIN", 5100), ("LF_S6_O1_BEFORE", 5000), ("LF_S6_AC_PRUNE_AFTER", 5000)):
            states = oracle.loader_settlement(cut)
            self.assertEqual(3, len(states))
            for state in states:
                self.assertEqual({oracle.FUEL: total}, state["totals"])
                self.assertEqual(total, oracle.raw_total((state["source"], state["destination"]), oracle.FUEL))
                if state["balance"] == "FROZEN": oracle.check_frozen(state["rows"][0], state["source"], state["destination"])
                if state["balance"] == "PLAN": oracle.check_plan(state["rows"][0], state["source"], state["destination"])
        # The released states keep the accepted amounts: nothing is refunded after the restarts.
        self.assertEqual([{oracle.FUEL: 3900}] * 3, [state["source"]["fluids"] for state in oracle.loader_settlement("LF_S6_AC_LOSS")])
        self.assertEqual([1, 0, 0], [state["source"]["markers"] for state in oracle.loader_settlement("LF_S6_AC_GAIN")])
        with patch.object(oracle, "FUEL", oracle.WATER):
            o1 = ledger("fuel-known-o1")
            for cut, tank in (("LF_S6_AC_LOSS", 3900), ("LF_S6_AC_GAIN", 4100)):
                # The fixture row says a = 0 over 4,000 mB: with the changed tank the frozen entry differs.
                with self.assertRaisesRegex(SmokeError, "Frozen entries differ"):
                    oracle.check_loader_settlement(self.settlement_disk(o1, tank, 1000), cut, 0)
                adjusted = copy.deepcopy(o1); entry = adjusted["fluid_accounts"][0]["f"]["e"][0]
                entry["s"] = tank; entry["a"] = 4000 - tank
                # Row consistency (hash, digest) is the shared reader's own check; here only the fixture comparison is shown.
                with patch.object(oracle.accounting, "check", lambda root, fresh_fluid: {oracle.accounting.identity(r["op"]): r for r in root["fluid_accounts"]}):
                    oracle.check_loader_settlement(self.settlement_disk(adjusted, tank, 1000), cut, 0)
                    wrong = copy.deepcopy(adjusted); wrong["fluid_accounts"][0]["f"]["e"][0]["a"] = tank - 4000
                    with self.assertRaisesRegex(SmokeError, "Frozen entries differ"):
                        oracle.check_loader_settlement(self.settlement_disk(wrong, tank, 1000), cut, 0)
                    # The raw tank must be the accepted amount, not the unchanged one.
                    with self.assertRaisesRegex(SmokeError, "Raw source amounts"):
                        oracle.check_loader_settlement(self.settlement_disk(adjusted, 4000, 1000), cut, 0)
        for cut in ("LF_S6_O3J_ACK", "LF_S6_F7_ACK", "LF_M_D2_C1_ACK", "O1_BEFORE"):
            with self.assertRaisesRegex(SmokeError, "No loader settlement accounting"): oracle.loader_settlement(cut)

    def upgraded(self):
        root = ledger("cargo-root2"); root["records"][0]["phase"] = "PREPARED"
        legacy = copy.deepcopy(root); legacy["record_digests"] = ["aa"]
        root.update(schema_version=3, format_epoch=accounting.EPOCHS[3], fluid_accounts=[], record_digests=["aa"])
        return legacy, root

    def test_an_upgraded_ledger_keeps_its_legacy_operation_unapplied_and_byte_equal(self):
        legacy, root = self.upgraded()
        before = (oracle.store(oracle.WATER, 5000), oracle.store())
        for end in before: end["markers"] = 0
        oracle.check_root2(legacy, ["aa"], *before)
        oracle.check_upgraded(root, ["aa"], *before)
        with self.assertRaisesRegex(SmokeError, "not root 3"): oracle.check_upgraded(legacy, ["aa"], *before)
        with self.assertRaisesRegex(SmokeError, "not root 2"): oracle.check_root2(root, ["aa"], *before)
        with self.assertRaisesRegex(SmokeError, "bytes changed"): oracle.check_upgraded(root, ["bb"], *before)
        for mutate, message in ((lambda r: r["records"][0].update(phase="REPAIR"), "retired, settled or changed"),
                                (lambda r: r.update(records=[], record_digests=[]), "promoted|bytes changed")):
            bad = copy.deepcopy(root); mutate(bad)
            with self.assertRaisesRegex(SmokeError, message): oracle.check_upgraded(bad, ["aa"], *before)
        applied = (dict(before[0], fluids={oracle.WATER: 4000}, epoch=1), before[1])
        with self.assertRaisesRegex(SmokeError, "Raw source"): oracle.check_upgraded(root, ["aa"], *applied)
        invented = copy.deepcopy(root); invented["fluid_accounts"] = ledger("cargo-planned")["fluid_accounts"]
        with self.assertRaises(SmokeError): oracle.check_upgraded(invented, ["aa"], *before)

    def test_the_root2_cut_checks_both_endpoints_while_the_ledger_stays_the_same(self):
        """W373C, review finding 3: the BEFORE_COMMIT snapshot is the fixture's exact BEFORE state, not only its ledger."""
        legacy, _ = self.upgraded()
        before = lambda: (oracle.store(oracle.WATER, 5000), oracle.store())
        summary = oracle.check_root2(legacy, ["aa"], *before())
        self.assertEqual(({oracle.WATER: 5000}, {}), (summary["source"]["fluids"], summary["destination"]["fluids"]))
        for side, change, message in ((0, dict(fluids={oracle.WATER: 4000}), "Raw source amounts"),
                                      (0, dict(fluids={oracle.WATER: 5001}), "Raw source amounts"),
                                      (0, dict(fluids={oracle.LAVA: 5000}), "Raw source amounts"),
                                      (0, dict(epoch=1), "Raw source epoch"), (0, dict(markers=1), "Raw source marker"),
                                      (1, dict(fluids={oracle.WATER: 1000}), "Raw destination amounts"),
                                      (1, dict(epoch=1), "Raw destination epoch"), (1, dict(markers=1), "Raw destination marker")):
            stores = before(); stores[side].update(change)
            with self.subTest(side=side, change=change), self.assertRaisesRegex(SmokeError, message):
                oracle.check_root2(copy.deepcopy(legacy), ["aa"], *stores)
        with self.assertRaisesRegex(SmokeError, "store is missing"): oracle.check_root2(legacy, ["aa"], None, before()[1])
        with self.assertRaisesRegex(SmokeError, "not root 2"): oracle.check_root2(None, ["aa"], *before())

    def test_an_l2_cut_needs_exactly_one_marker_on_each_endpoint_and_none_after_the_restarts(self):
        """W373C, review finding 2: pruning before the acknowledged retirement, on one side or both, fails the cut."""
        root = ledger("cargo-planned"); root.update(records=[], fluid_accounts=[])
        for cut in ("L2_REPLACED", "C7_ACKNOWLEDGED"):
            first, *restarts = oracle.ordinary(cut)
            self.assertEqual((1, 1), (first["source"]["markers"], first["destination"]["markers"]))
            oracle.check_state(root, *observed(first), first)
            for source, destination, message in ((0, 0, "Raw source marker"), (0, 1, "Raw source marker"),
                                                 (1, 0, "Raw destination marker"), (2, 1, "Raw source marker"),
                                                 (1, 2, "Raw destination marker")):
                stores = observed(first); stores[0]["markers"] = source; stores[1]["markers"] = destination
                with self.subTest(cut=cut, markers=(source, destination)), self.assertRaisesRegex(SmokeError, message):
                    oracle.check_state(root, *stores, first)
            for state in restarts:
                oracle.check_state(root, *observed(state), state)
                for source, destination in ((1, 1), (1, 0), (0, 1)):
                    stores = observed(state); stores[0]["markers"] = source; stores[1]["markers"] = destination
                    with self.assertRaisesRegex(SmokeError, "marker count"): oracle.check_state(root, *stores, state)
        # A marker count is one exact integer: a set of allowed counts is not an expectation any more.
        with self.assertRaisesRegex(SmokeError, "marker count"):
            oracle.check_store("source", dict(fluids={}, epoch=0, markers=(0, 1)), oracle.store(None, 0, 0, 0))
        with self.assertRaisesRegex(SmokeError, "marker count"):
            oracle.check_store("source", dict(fluids={}, epoch=0, markers=True), oracle.store(None, 0, 0, 1))

    def test_changed_files_lists_changes_additions_and_removals(self):
        before = {"level.dat": "1", "data/a.dat": "2", "region/r.0.0.mca": "3"}
        after = {"level.dat": "9", "data/a.dat": "2", "level.dat_old": "1"}
        self.assertEqual(dict(changed=["level.dat"], added=["level.dat_old"], removed=["region/r.0.0.mca"]),
                         oracle.changed_files(before, after))
        self.assertEqual(dict(changed=[], added=[], removed=[]), oracle.changed_files(before, dict(before)))


if __name__ == "__main__":
    unittest.main()
