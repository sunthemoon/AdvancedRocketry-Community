"""Linux-safe K08B planning/authority tests; no Minecraft launch or save writer."""
import copy
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

from scripts.campaign_prep import k08b_warp_energy as prep


def row(owner=prep.stations.OWNER):
    return dict(id=owner,owner=owner,orbit=prep.NS+":earth",energy=502,
                region=[-1280,768,-769,1279],cores=[[-1020,128,1028]],
                members=[],invitations=[])


class K08BTests(unittest.TestCase):
    def test_integer_tick_plan_and_fold(self):
        plan = prep.energy_plan(0,prep.IN_SYSTEM)
        self.assertEqual(plan["fe_per_station_tick"],4032)
        self.assertEqual(plan["generation_ticks"],497)
        self.assertEqual(plan["generation_and_fold_seconds_at_20tps"],34.85)
        self.assertEqual(prep.energy_plan(502,prep.INTERSTELLAR)["generation_ticks"],1985)
        self.assertLess(prep.energy_plan(0,prep.INTERSTELLAR)["single_array_seconds_at_20tps"],7200)

    def test_invalid_plan_inputs(self):
        for value in (-1,10_000_001,1.5,True):
            with self.assertRaises(ValueError): prep.energy_plan(value,prep.IN_SYSTEM)
        for value in (0,33,False):
            with self.assertRaises(ValueError): prep.energy_plan(0,prep.IN_SYSTEM,value)
        with self.assertRaises(ValueError): prep.energy_plan(0,123)

    def test_already_ready_balance(self):
        self.assertEqual(prep.energy_plan(prep.INTERSTELLAR,prep.IN_SYSTEM)["generation_ticks"],0)

    def test_bank_distinct_contained_and_north_layout(self):
        units = prep.bank(row())
        positions = [p for u in units for p in prep.cells(u)]
        self.assertEqual(len(positions),32*69)
        self.assertEqual(len(set(positions)),len(positions))
        self.assertTrue(all(-1280 <= x <= -769 and 768 <= z <= 1279 and y == 200 for x,y,z in positions))
        self.assertEqual(len(prep.build_commands(units[0])),5)
        self.assertIn("solar_array[facing=north]",prep.build_commands(units[0])[-1])
        self.assertIn("solar_array_panel",prep.build_commands(units[0])[0])
        self.assertEqual(prep.air_command(units[0]).count("if block"),69)

    def test_small_claim_refused(self):
        value = row(); value["region"] = [0,0,4,4]
        with self.assertRaisesRegex(ValueError,"escapes"): prep.bank(value)

    def test_chunk_floor_and_native_signed_tickets(self):
        units = prep.bank(row())
        self.assertIn((-78,50),prep.tickets(units))
        word = (-78 & 0xffffffff) | ((-50 & 0xffffffff) << 32)
        state = dict(forced_chunks={f"dimensions/{prep.NS}/space/data/chunks.dat":dict(data=dict(Forced=[word-2**64])),
                                   "data/chunks.dat":dict(data=dict(Forced=[1]))})
        self.assertEqual(prep.inherited_tickets(state),{(-78,-50)})

    def test_selected_owners_and_interstellar_variant(self):
        values = [row(),row(prep.stations.OTHER),row(prep.stations.MEMBER)]
        self.assertEqual(len(prep.selected_rows(values,"two")),2)
        for variant,target in (("target-removal",prep.IN_SYSTEM),("cost-change",prep.INTERSTELLAR)):
            plan = prep.selected_rows(values,variant)
            self.assertEqual(len(plan),1)
            self.assertEqual(plan[0]["energy"]["target"],target)
        with self.assertRaises(ValueError): prep.selected_rows(values[1:],"two")
        values[0]["orbit"] = prep.NS+":cygnus_x1"
        with self.assertRaises(ValueError): prep.selected_rows(values,"two")

    def test_compare_only_selected_balance_can_change(self):
        before = [row(),row(prep.stations.OTHER)]
        plan = prep.selected_rows(before,"target-removal")
        after = copy.deepcopy(before); after[0]["energy"] = prep.IN_SYSTEM
        prep.compare_rows(before,after,plan)
        for key,value in (("energy",100),("owner",prep.stations.OTHER),("members",["new"])):
            bad = copy.deepcopy(after); bad[0][key] = value
            with self.assertRaises(ValueError): prep.compare_rows(before,bad,plan)
        bad = copy.deepcopy(after); bad[1]["energy"] += 1
        with self.assertRaises(ValueError): prep.compare_rows(before,bad,plan)
        with self.assertRaises(ValueError): prep.compare_rows(before,after,[])

    def test_balance_uses_folded_not_pending(self):
        class Console:
            def all(self,command): return ["station=test orbit=earth balance=123 pending=456 cooldown_ready=true"]
        self.assertEqual(prep.balance(Console(),"test")["balance"],123)
        with self.assertRaises(ValueError): prep.balance(Console(),"missing")

    def test_settled_poll_waits_for_zero_pending(self):
        plan = [dict(station="test",energy=dict(target=prep.IN_SYSTEM))]
        readings = [dict(balance=prep.IN_SYSTEM,pending=20),dict(balance=prep.IN_SYSTEM+20,pending=0)]
        with patch.object(prep,"balance",side_effect=readings), patch.object(prep.time,"sleep") as sleep:
            self.assertEqual(prep.wait_balances(None,plan,settled=True)["test"]["pending"],0)
            sleep.assert_called_once_with(2)

    def test_fixed_poll_deadline(self):
        plan = [dict(station="test",energy=dict(target=prep.IN_SYSTEM))]
        with patch.object(prep,"balance",return_value=dict(balance=0,pending=999)), \
             patch.object(prep.time,"monotonic",side_effect=[0,601]):
            with self.assertRaisesRegex(ValueError,"fixed window"): prep.wait_balances(None,plan)

    def test_copy_seal_detects_parent_changes(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp); world = folder/"source-world"; world.mkdir()
            (world/"example").write_bytes(b"parent")
            prep.chain.seal(folder)
            prep.chain.verify_seal(folder)
            (world/"example").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError,"changed"): prep.chain.verify_seal(folder)

    def test_commands_have_no_resource_injection(self):
        commands = "\n".join(c for u in prep.bank(row()) for c in prep.build_commands(u))
        self.assertNotIn("{",commands)
        self.assertNotIn("release-test",commands)
        self.assertNotIn("data modify",commands)
        self.assertNotIn("creative",commands)


if __name__ == "__main__":
    unittest.main()
