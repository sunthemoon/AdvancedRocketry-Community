import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import run_v180_escrow_pruning as runner


class PruningOracleTest(unittest.TestCase):
    def fixture(self, restarted):
        def section(op, seq, role, released=False):
            return dict(schema_version=1, ledger_id="ledger", resource_epoch=1,
                        markers=[] if released and restarted else [dict(op_id=op, seq=seq, role=role, epoch_after=1)])
        def source(x, identity, op, seq, released=False):
            return dict(x=x, y=100, z=64, ForgeData=dict(arce_escrow_fixture=dict(identity=identity, amount=4000),
                        resource_escrow=section(op, seq, "SOURCE", released)))
        a = dict(kind="BLOCK_ENTITY", identity="source-a", x=64, y=100, z=64)
        b = dict(kind="BLOCK_ENTITY", identity="source-b", x=80, y=100, z=64, before_epoch=0, after_epoch=1)
        bank = dict(kind="ENTITY", identity="bank", logical_rocket="held-logical", before_epoch=0, after_epoch=1)
        held = dict(op_id="B", seq=2, phase="REPAIR", reason="OTHER_STATE", source=b, destination=bank)
        receipt = dict(op_id="A", released=1, release_generation=4, actor=runner.ACTOR, actor_name="Server", accounting_status="UNKNOWN",
                       decision_digest="a"*64, retired=[dict(op_id="A")], schema_version=3, choice="ACCEPT_CURRENT", missing=[],
                       survivors=[dict(endpoint=a), dict(endpoint=dict(kind="ENTITY", identity="rocket"))])
        head = dict(outcome="READY", issued=0, completed=0, e=1, a="A", g=4, p="rocket", l="logical", o="owner")
        root = dict(ledger_id="ledger", records=[held], tombstones=[receipt], rocket_authorities=[head], floors=[], armed_flights=[],
                    issued_through=2, issued_launch_serial=0)
        rocket = dict(id="advancedrocketrycommunity:rocket", UUID="rocket", Pos=[64.5,100.0,64.5], RocketEntityData=dict(schema_version=3,
                      assembly_transaction_id="logical", owner_id="owner", flight_data=dict(fuel=dict(amount=1000, committed_debits=[])),
                      resource_escrow=section("A", 1, "DESTINATION", True)))
        bank = dict(id="minecraft:armor_stand", UUID="bank", Pos=[82.5,100.0,64.5], ForgeData=dict(
                    arce_escrow_fixture=dict(identity="held-logical", amount=1000), resource_escrow=section("B", 2, "DESTINATION")))
        return root, dict(transfers=[]), rocket, bank, [source(64,"source-a","A",1,True), source(80,"source-b","B",2)]

    def test_both_cuts_and_two_restarts_keep_only_a_pruned(self):
        self.assertEqual(("SR_HELD_B__O4_AFTER", "SR_HELD_B__O4_REPLACED"), runner.CASES)
        for cut in runner.CASES:
            before = runner.require_state(*self.fixture(False), False)
            for _ in range(2): self.assertEqual(before, runner.require_state(*self.fixture(True), True), cut)

    def test_independent_b_mutations_or_release_refuse(self):
        for field in ("hold", "lost", "epoch", "source_marker", "bank_marker", "source_amount", "bank_amount", "bank_identity", "pose"):
            root, journal, rocket, bank, sources = self.fixture(True)
            if field == "hold": root["records"][0]["phase"] = "PREPARED"
            if field == "lost": root["records"] = []
            if field == "epoch": bank["ForgeData"]["resource_escrow"]["resource_epoch"] = 0
            if field == "source_marker": sources[1]["ForgeData"]["resource_escrow"]["markers"] = []
            if field == "bank_marker": bank["ForgeData"]["resource_escrow"]["markers"] = []
            if field == "source_amount": sources[1]["ForgeData"]["arce_escrow_fixture"]["amount"] = 5000
            if field == "bank_amount": bank["ForgeData"]["arce_escrow_fixture"]["amount"] = 0
            if field == "bank_identity": bank["UUID"] = "foreign"
            if field == "pose": bank["Pos"][0] += 16
            with self.subTest(field=field), self.assertRaises(runner.phases.smoke.SmokeError):
                runner.require_state(root, journal, rocket, bank, sources, True)

    def test_a_requires_exact_receipt_ready_head_and_unchanged_credit(self):
        for field in ("actor", "release", "generation", "retired", "ready", "fuel", "marker", "source_marker", "epoch", "journal"):
            root, journal, rocket, bank, sources = self.fixture(True)
            if field == "actor": root["tombstones"][0]["actor"] = "foreign"
            if field == "release": root["tombstones"][0]["released"] = 0
            if field == "generation": root["tombstones"][0]["release_generation"] += 1
            if field == "retired": root["tombstones"][0]["retired"].append(dict(op_id="B"))
            if field == "ready": root["rocket_authorities"][0]["outcome"] = "CANCELLED"
            if field == "fuel": rocket["RocketEntityData"]["flight_data"]["fuel"]["amount"] -= 1
            if field == "marker": rocket["RocketEntityData"]["resource_escrow"]["markers"] = [dict(op_id="A")]
            if field == "source_marker": sources[0]["ForgeData"]["resource_escrow"]["markers"] = [dict(op_id="A")]
            if field == "epoch": rocket["RocketEntityData"]["resource_escrow"]["resource_epoch"] = 0
            if field == "journal": journal["transfers"] = [{}]
            with self.subTest(field=field), self.assertRaises(runner.phases.smoke.SmokeError):
                runner.require_state(root, journal, rocket, bank, sources, True)

    def test_cut_must_retain_a_markers_before_pruning_can_run(self):
        with self.assertRaises(runner.phases.smoke.SmokeError): runner.require_state(*self.fixture(True), False)


if __name__ == "__main__": unittest.main()
