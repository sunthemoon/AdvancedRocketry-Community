"""Linux-safe K08 planning/readback tests; never launch Minecraft or edit NBT."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
from uuid import UUID

from scripts.campaign_prep import k08_stations as prep
from scripts.campaign_prep import k08_station_load as load


def words(text):
    raw = UUID(text).int
    values = [(raw >> shift) & 0xffffffff for shift in (96,64,32,0)]
    return [v if v < 2**31 else v - 2**32 for v in values]


def state():
    station = dict(station_id=words(prep.OWNER), owner_id=words(prep.OTHER), name="test",
        orbit_body=prep.NS+":earth", landing_pad=[0,128,0], region=[-256,-256,255,255],
        members=[dict(id=words(prep.MEMBER))], invitations=[dict(id=words(prep.OUTSIDER))])
    root = dict(stations=[station], warp_energy=[dict(station_id=words(prep.OWNER),energy=456)])
    return dict(roots={prep.NS+"_stations.dat":dict(data=root)}, block_entities=[
        dict(value=dict(id=prep.NS+":warp_core",x=4,y=128,z=4)),
        dict(value=dict(id=prep.NS+":warp_core",x=1024,y=128,z=4))])


class K08Tests(unittest.TestCase):
    def test_uuid_signed_native_words(self):
        for text in (prep.OWNER, prep.OUTSIDER, "ffffffff-ffff-ffff-ffff-ffffffffffff"):
            self.assertEqual(text, prep.uuid_text(words(text)))

    def test_native_owners_members_region_energy_and_cores(self):
        row = prep.station_rows(state())[0]
        self.assertEqual(row["members"], [prep.MEMBER])
        self.assertEqual(row["invitations"], [prep.OUTSIDER])
        self.assertEqual(row["owner"], prep.OTHER)
        self.assertEqual(row["energy"], 456)
        self.assertEqual(row["cores"], [[4,128,4]])
        self.assertEqual(row["region"], [-256,-256,255,255])

    def test_missing_balance_is_zero_not_core_probe_value(self):
        saved = state()
        saved["roots"][prep.NS+"_stations.dat"]["data"]["warp_energy"] = []
        self.assertEqual(prep.station_rows(saved)[0]["energy"], 0)

    def plan(self, variant, inherited=None):
        return prep.creation_plan(variant, inherited or [], prep.OWNER, prep.OTHER, prep.MEMBER, prep.OUTSIDER)

    def test_two_eligible_owners_different_orbits_and_48_character_name(self):
        plan = self.plan("two")
        self.assertEqual([v["owner"] for v in plan], [prep.OWNER,prep.OTHER])
        self.assertEqual(len(plan[0]["name"]), 48)
        self.assertNotEqual(plan[0]["orbit"], plan[1]["orbit"])

    def test_load_counts_include_four_inherited_records(self):
        inherited = [dict(owner=str(UUID(int=i))) for i in range(4)]
        for variant,count in (("ten",10),("hundred",100)):
            plan = self.plan(variant,inherited)
            self.assertEqual(len(plan)+len(inherited), count)
            self.assertEqual(len({v["owner"] for v in plan}), len(plan))

    def test_ineligible_or_identical_actors_refused(self):
        with self.assertRaises(ValueError):
            self.plan("two",[dict(owner=prep.OWNER)])
        with self.assertRaises(ValueError):
            prep.creation_plan("two",[],prep.OWNER,prep.OWNER,prep.MEMBER,prep.OUTSIDER)

    def test_client_commands_bound_to_station_and_actors(self):
        rows = [dict(id=str(UUID(int=i+1)),owner=a) for i,a in enumerate((prep.OWNER,prep.OTHER))]
        steps = prep.client_steps(rows,[prep.OWNER,prep.OTHER,prep.MEMBER,prep.OUTSIDER],"two")
        actions = steps["actions"]
        self.assertEqual(actions[2]["actor"], prep.MEMBER)
        self.assertIn(rows[0]["id"], actions[2]["command"])
        self.assertIn(" uuid "+prep.MEMBER, actions[3]["command"])
        self.assertEqual(actions[6]["actor"], prep.OUTSIDER)
        self.assertTrue(actions[6]["command"].endswith(" confirm"))
        self.assertIn("NOT_COVERED", steps["pending"])
        self.assertIn("NOT_COVERED", steps["docked_rocket"])

    def test_overlay_expectations_remain_conditional_not_observations(self):
        rows = [dict(id=str(UUID(int=i+1)),owner=a) for i,a in enumerate((prep.OWNER,prep.OTHER))]
        actors = [prep.OWNER,prep.OTHER,prep.MEMBER,prep.OUTSIDER]
        for variant in ("target-removal","cost-change"):
            overlay = prep.client_steps(rows,actors,variant)["overlay"]
            self.assertIn("NOT_COVERED", overlay["actual"])
            self.assertIn('after "file/station_baseline.zip"', overlay["command"])
            self.assertIn("before due commit", overlay["preconditions"])
        self.assertIn("only if", overlay["expected"])

    def test_no_mutating_save_api_or_player_emulation_in_driver(self):
        source = Path(prep.__file__).read_text(encoding="utf-8")
        for forbidden in ("write_nbt", "write_region", "data merge", "data modify", "summon ", "execute as "):
            self.assertNotIn(forbidden, source)
        self.assertIn('solar_generator', source)

    def test_c10_defaults_and_short_window_label(self):
        parsed = load.parser().parse_args(["--variant","ten","--mode","load","--run-label","loaded",
            "--template","template","--java","java","--lock-owner","W440 pid=1 token"])
        self.assertEqual((parsed.warmup,parsed.duration), (120,600))
        self.assertIn("QUALIFICATION", load.windows(1,2,True)["classification"])
        with self.assertRaises(ValueError):
            load.windows(1,2,False)

    def test_windows_bounded_finite_positive(self):
        for value in (0,-1,float("nan"),float("inf"),86401):
            with self.assertRaises(ValueError):
                load.windows(value,600,True)

    def test_all_four_platform_chunks_and_negative_coordinates(self):
        self.assertEqual(load.chunks([dict(pad=[0,128,0])]), [(-1,-1),(-1,0),(0,-1),(0,0)])
        self.assertEqual(len(load.chunks([dict(pad=[0,128,0]),dict(pad=[1024,128,0])])), 8)

    def test_force_reply_coordinates(self):
        self.assertEqual(load.forced_reply(["Force loaded chunks in space: [-1, 0], [64, 64]"]), [(-1,0),(64,64)])
        self.assertEqual(load.forced_reply(["No force loaded chunks were found"]), [])

    def test_load_witness_uses_non_loading_predicate(self):
        class Console:
            commands=[]
            def all(self, command):
                self.commands.append(command)
                return ["[Server] K08_CHUNK_0_0"]
        c = Console()
        result = load.wait_chunks(c,[(0,0)],False)
        self.assertFalse(result["loaded"])
        self.assertIn("unless loaded", c.commands[0])

    def test_unload_deadline_is_finite(self):
        class Console:
            def all(self, command): return ["Test failed"]
        clock = iter((0,61))
        with self.assertRaisesRegex(ValueError,"did not unload"):
            load.wait_chunks(Console(),[(0,0)],False,clock=lambda:next(clock),sleep=lambda _:None)

    def test_k15_raw_sample_discards_pre_window_ticks(self):
        class Console:
            def __init__(self): self.tick=10
            def query(self,*args):
                self.tick+=1
                return f"ARCE_RELEASE_TEST ticks_raw tick={self.tick} entries=2 data={self.tick-1}:1000000,{self.tick}:2000000"
        # Different durations for duplicate ticks must be refused by K15, not hidden.
        with tempfile.TemporaryDirectory() as directory:
            with patch.object(load,"schedule",return_value=iter([0,1])):
                with self.assertRaisesRegex(ValueError,"Conflicting"):
                    load.sample(Console(),Path(directory),2)

    def test_parent_seal_change_refused(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root/"source-world").mkdir()
            (root/"source-world/file").write_bytes(b"original")
            prep.chain.seal(root)
            (root/"source-world/file").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError,"changed"):
                prep.chain.verify_seal(root)

    def test_successful_sampler_has_only_post_warmup_ticks(self):
        class Console:
            def __init__(self): self.tick=10
            def query(self,*args):
                self.tick+=1
                return f"ARCE_RELEASE_TEST ticks_raw tick={self.tick} entries=2 data={self.tick-1}:1000000,{self.tick}:1000000"
        with tempfile.TemporaryDirectory() as directory:
            folder = Path(directory)
            with patch.object(load,"schedule",return_value=iter([0,1])):
                result = load.sample(Console(),folder,2)
            self.assertEqual(result["start_after_tick"],11)
            self.assertEqual(result["summary"]["first_tick"],12)
            self.assertEqual(result["summary"]["tick_count"],3)
            self.assertEqual(result["summary"]["missing_tick_count"],0)
            self.assertIn("NOT_PERFORMED",result["reference_comparison"])
            self.assertEqual(len((folder/"samples.csv").read_text().splitlines()),4)

    def test_created_authority_does_not_accept_duplicate_owner(self):
        rows = prep.station_rows(state())
        with self.assertRaisesRegex(ValueError,"Per-owner"):
            prep.check_created("two",[],rows+rows,[])

    def test_source_qualification_checks_all_stable_fields(self):
        before = dict(stations=[dict(owner=prep.OWNER)],warp_energy=[456])
        self.assertTrue(all(prep.planets.comparison(before,before,"station").values()))
        for key in before:
            after = dict(before,**{key:[]})
            with self.assertRaisesRegex(ValueError,"Restart authority"):
                prep.planets.comparison(before,after,"station")


if __name__ == "__main__":
    unittest.main()
