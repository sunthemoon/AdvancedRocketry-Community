"""Linux-safe unit checks for K17; no Minecraft, Java, lock or world is needed."""
from argparse import Namespace
import copy
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch

from scripts.campaign_prep import k17_reference as k


def schedule_args(**changes):
    values = dict(sample_seconds=14400, save_interval=1800, restart_at=7200, warmup=120,
                  qualification=False, hooks=False, component_interval=300)
    values.update(changes)
    return Namespace(**values)


def entities(layout, *, full=64, vent_full=4, empties=12, units=3000, coal=62, cells=64):
    """A closed world as the region reader returns it: every shell is somewhere, none is lost."""
    canister, empty = k.NS + ":oxygen_canister", k.NS + ":empty_canister"
    result = {}
    for room in layout["rooms"]["items"]:
        def put(key, value):
            result[(k.MOON, *room[key])] = value
        put("canister_hopper", dict(Items=[dict(Slot=0, id=canister, Count=16), dict(Slot=1, id=canister, Count=full - 16)]))
        put("empties_hopper", dict(Items=[dict(Slot=0, id=empty, Count=empties)]))
        put("fuel_hopper", dict(Items=[]))
        put("vent", dict(arce_oxygen_vent=dict(oxygen_canisters=vent_full, empty_canisters=0, oxygen_units=units, energy=40000)))
        put("generator", dict(arce_combustion_generator=dict(energy=20000, duration=16000, remaining=9000,
                                                             fuel=dict(id="minecraft:coal_block", Count=coal))))
    for pad in layout["pads"]["items"].values():
        if pad["chest"]:
            result[(k.OW, *pad["chest"])] = dict(Items=[dict(Slot=0, id=k.NS + ":rocket_fuel_cell", Count=cells)])
            result[(k.OW, *pad["loader"])] = dict(arce_fuel_loader=dict(slot_role=0))
    return result


class LayoutTests(unittest.TestCase):
    def test_sixteen_rooms_share_one_footprint_inside_the_shipped_scan_limit(self):
        rooms = k.rooms()
        self.assertEqual(len(rooms), 16)
        self.assertEqual(len({tuple(r["chunk"]) for r in rooms}), 16)
        for room in rooms:
            (x0, y0, z0), (x1, y1, z1) = room["box"]
            self.assertEqual((x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1), 4096)
            self.assertEqual(room["footprint_blocks"], 4096)
            self.assertLessEqual(room["interior_cells"], k.ATMOSPHERE["maxVolumeCells"])
            # One chunk per room: a scan never reads a neighbouring chunk.
            self.assertEqual((x0 >> 4, z0 >> 4, x1 >> 4, z1 >> 4), (*room["chunk"], *room["chunk"]))
            self.assertTrue(0 <= y0 and y1 < 256)

    def test_devices_sit_inside_the_shell_and_the_boundary_is_in_the_roof(self):
        for room in k.rooms():
            (x0, y0, z0), (x1, y1, z1) = room["box"]
            for key in ("vent", "scan_seed", "empties_hopper", "canister_hopper", "generator", "fuel_hopper"):
                x, y, z = room[key]
                self.assertTrue(x0 < x < x1 and y0 < y < y1 and z0 < z < z1, key)
            vx, vy, vz = room["vent"]
            self.assertEqual(room["scan_seed"], [vx, vy + 1, vz])  # AtmosphereLevelService.seed
            self.assertEqual(room["empties_hopper"], [vx, vy - 1, vz])  # the vent's bottom face is the empty slot
            self.assertEqual(room["canister_hopper"], [vx - 1, vy, vz])  # facing east into a side face
            self.assertEqual(room["generator"], [vx + 1, vy, vz])  # energy enters by a side face only
            self.assertEqual(room["boundary"][1], y1)
            self.assertTrue(x0 < room["boundary"][0] < x1 and z0 < room["boundary"][2] < z1)

    def test_pads_are_the_k04_layouts_at_separate_sites(self):
        pads = k.layout()["pads"]["items"]
        self.assertEqual({name: pad["expected_blocks"] for name, pad in pads.items()}, {"p2048": 2048, "pad-a": 64, "pad-b": 64})
        self.assertEqual(pads["p2048"]["dimensions"], [8, 16, 16])
        self.assertIsNone(pads["p2048"]["loader"])
        boxes = [pad["forceload"] for pad in pads.values()]
        for box in boxes:
            self.assertTrue(all(4080 <= value < 4608 for value in box), box)
        self.assertEqual(len({tuple(box) for box in boxes}), 3)
        # Every placed block entity is in region r.8.8 of the Overworld or r.2.2 of the Moon, both absent from the parent.
        self.assertTrue(all(room["box"][0][0] >> 9 == 2 and room["box"][0][2] >> 9 == 2 for room in k.rooms()))
        self.assertTrue(all(pad["origin"][0] >> 9 == 8 and pad["origin"][2] >> 9 == 8 for pad in pads.values()))


class CommandTests(unittest.TestCase):
    def test_setup_uses_ordinary_commands_and_never_writes_nbt(self):
        commands = k.build_commands()
        allowed = ("fill ", "setblock ", "item replace block ", "forceload add ")
        for command in commands:
            body = command.split(" run ", 1)[1] if command.startswith("execute in ") else command
            self.assertTrue(body.startswith(allowed), command)
            self.assertFalse(any(word in command for word in ("data ", "gamerule", "time set", "weather", "release-test", "{")), command)

    def test_room_supply_is_the_recorded_grant(self):
        commands = k.room_commands(k.rooms()[0])
        self.assertEqual(sum(c.count("oxygen_canister 16") for c in commands) * 16, k.CANISTERS)
        self.assertEqual(sum("coal_block 64" in c for c in commands), 1)
        self.assertEqual(sum("oak_trapdoor[half=top,open=false]" in c for c in commands), 1)
        self.assertEqual(sum(" hollow" in c for c in commands), 1)

    def test_loaders_stay_unclaimed_and_small_pads_get_cells(self):
        for name in ("pad-a", "pad-b"):
            commands = k.pad_commands(name)
            self.assertEqual(sum(c.endswith(":fuel_loader") for c in commands), 1)
            self.assertEqual(sum(c.count("rocket_fuel_cell 16") for c in commands) * 16, k.FUEL_CELLS)
        self.assertFalse(any("fuel_loader" in c or "chest" in c for c in k.pad_commands("p2048")))

    def test_console_refuses_an_nbt_write_before_sending_it(self):
        class Process:
            lines = []
            def command(self, value):
                raise AssertionError("sent " + value)
        console = k.Console(Process(), [])
        for command in ("data merge block 1 2 3 {a:1}", "execute in x run data modify block 1 2 3 a set value 1"):
            with self.assertRaises(ValueError):
                console.all(command)

    def test_station_plan_reaches_ten_with_distinct_owners(self):
        made = k.station_plan(["a", "b", "c", "d"])
        self.assertEqual(len(made), 6)
        self.assertEqual(len({m["owner"] for m in made}), 6)
        self.assertEqual(k.station_plan(list("0123456789")), [])
        with self.assertRaises(ValueError):
            k.station_plan(list("0123456789a"))
        with self.assertRaises(ValueError):
            k.station_plan([made[0]["owner"]])


class ScheduleTests(unittest.TestCase):
    def test_reference_schedule_is_c4(self):
        plan = k.plan(schedule_args())
        self.assertEqual((plan["sampled_seconds"], plan["flushed_save_interval_seconds"], plan["restart_at_sampled_seconds"],
                          plan["warmup_seconds"]), (14400, 1800, 7200, 120))
        self.assertEqual([s["sampled_seconds"] for s in plan["segments"]], [7200, 7200])
        self.assertEqual(plan["test_properties"], [])
        self.assertIn("save-off", plan["vanilla_autosave"])
        self.assertEqual(plan["classification"], "CAMPAIGN_PROCEDURE_NO_GATE_VERDICT")

    def test_a_changed_schedule_is_only_a_qualification(self):
        for change in (dict(sample_seconds=240, restart_at=120), dict(save_interval=60), dict(warmup=20)):
            with self.assertRaises(ValueError):
                k.plan(schedule_args(**change))
        plan = k.plan(schedule_args(sample_seconds=240, restart_at=120, save_interval=60, warmup=20, qualification=True))
        self.assertEqual(plan["classification"], "QUALIFICATION_NOT_MEASUREMENT")
        self.assertEqual(sum(s["sampled_seconds"] for s in plan["segments"]), 240)

    def test_invalid_schedules(self):
        for change in (dict(restart_at=14400), dict(restart_at=0), dict(sample_seconds=float("nan")),
                       dict(save_interval=-1), dict(sample_seconds=90000, restart_at=7200)):
            with self.assertRaises(ValueError):
                k.plan(schedule_args(qualification=True, **change))

    def test_without_the_property_player_actions_are_client_steps(self):
        plan = k.plan(schedule_args())
        modes = {a["id"]: a["mode"] for a in plan["actions"]}
        self.assertEqual(modes, {"assemble-2048": "console", "a11-open-restore": "console", "flights-a-b": "client",
                                 "disassemble-2048": "client", "refill-missions": "client"})
        self.assertTrue(all(a["at"] < plan["restart_at_sampled_seconds"] for a in plan["actions"]))
        self.assertNotIn("release-test", str([a for a in plan["actions"] if a["mode"] == "console"]))
        hooked = {a["id"]: a["mode"] for a in k.plan(schedule_args(hooks=True))["actions"]}
        self.assertEqual((hooked["flights-a-b"], hooked["disassemble-2048"], hooked["refill-missions"]), ("hook", "hook", "client"))
        self.assertIn("raw", k.plan(schedule_args(hooks=True))["tick_source"])

    def test_client_steps_name_both_accounts_and_no_hook(self):
        steps = k.client_steps()
        self.assertEqual(steps["status"], "SCRIPTED_ONLY_NOT_EXECUTED")
        self.assertEqual(len(steps["flights"]["A"]), len(steps["flights"]["B"]))
        text = str(steps)
        self.assertNotIn("release-test", text)
        self.assertNotIn("data merge", text)
        self.assertIn("/arce atmosphere status", text)
        self.assertIn("/arce rocket-disassembly confirm", text)
        self.assertIn(str(k.rooms()[0]["boundary"]), text)


class ParserTests(unittest.TestCase):
    def test_forced_chunk_longs(self):
        def pack(x, z):
            value = (x & 0xffffffff) | ((z & 0xffffffff) << 32)
            return value - (1 << 64) if value >= 1 << 63 else value
        self.assertEqual(k.forced_longs([pack(64, 67), pack(-1, 0), pack(23, -24)]), [[-1, 0], [23, -24], [64, 67]])
        self.assertEqual(k.forced_reply(["2 force loaded chunks were found in x at: [64, 64], [-3, 7]"]), [[-3, 7], [64, 64]])
        self.assertEqual(k.forced_reply(["No force loaded chunks were found in x"]), [])

    def test_forge_tps_needs_exactly_one_overall_line(self):
        lines = ["Dim minecraft:overworld (x): Mean tick time: 0.500 ms. Mean TPS: 20.000",
                 "[minecraft/MinecraftServer]: Overall: Mean tick time: 1.250 ms. Mean TPS: 20.000"]
        self.assertEqual(k.parse_tps(lines), dict(mean_tick_ms=1.25, mean_tps=20.0))
        self.assertEqual(k.parse_tps(["Overall: Mean tick time: 61,5 ms. Mean TPS: 16,260"])["mean_tick_ms"], 61.5)
        for bad in ([], lines[:1], lines[1:] * 2):
            with self.assertRaises(ValueError):
                k.parse_tps(bad)

    def test_field_readbacks(self):
        line = "x: ARCE_SATELLITE_EVIDENCE satellites=213 missions=213 active=101 ready=106 scheduler=deadline_queue"
        fields = k.parse_fields([line], "ARCE_SATELLITE_EVIDENCE ")
        self.assertEqual((fields["missions"], fields["active"], fields["scheduler"]), (213, 101, "deadline_queue"))
        with self.assertRaises(ValueError):
            k.parse_fields([line], "ARCE-BETA-1101 ")

    def test_gc_and_series_summaries(self):
        text = ("[1.0s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 51M->8M(512M) 4.250ms\n"
                "[2.0s][info][gc] GC(1) Pause Remark 60M->60M(512M) 1.500ms\n[2.1s][info][gc] GC(1) Concurrent Mark 7.000ms\n")
        self.assertEqual(k.gc_summary(text)["pause_count"], 2)
        self.assertEqual(k.gc_summary(text)["pause_max_ms"], 4.25)
        self.assertEqual(k.gc_summary("")["pause_max_ms"], None)
        summary = k.series_summary([(0, 100.0), (1800, 150.0), (3600, 200.0)])
        self.assertEqual((summary["first"], summary["last"], summary["maximum"], summary["median"]), (100.0, 200.0, 200.0, 150.0))
        self.assertAlmostEqual(summary["slope_per_hour"], 100.0)
        self.assertIsNone(k.series_summary([(0, 5)])["slope_per_hour"])
        with self.assertRaises(ValueError):
            k.series_summary([])


class AccountingTests(unittest.TestCase):
    def setUp(self):
        self.layout = k.layout()

    def test_totals_come_from_every_store(self):
        result = k.accounting_from(entities(self.layout), self.layout)
        room = result["rooms"][0]
        self.assertEqual((room["full_canisters"], room["empty_canisters"], room["canister_shells"]), (68, 12, 80))
        self.assertEqual(room["oxygen_potential"], 68 * 1000 + 3000)
        self.assertEqual(result["totals"]["canister_shells"], 16 * k.CANISTERS)
        self.assertEqual(result["totals"]["coal_blocks"], 16 * 62)
        self.assertEqual(result["totals"]["fuel_cells"], 2 * k.FUEL_CELLS)
        self.assertEqual(sorted(result["pads"]), ["pad-a", "pad-b"])

    def test_a_missing_device_is_an_error_not_a_zero(self):
        closed = entities(self.layout)
        del closed[(k.MOON, *self.layout["rooms"]["items"][3]["vent"])]
        with self.assertRaises(ValueError):
            k.accounting_from(closed, self.layout)

    def test_consumption_reconciles(self):
        before = k.accounting_from(entities(self.layout), self.layout)
        after = k.accounting_from(entities(self.layout, full=60, empties=16, units=2500, coal=61), self.layout)
        result = k.reconcile(before, after)
        self.assertEqual(result["status"], "RECONCILED")
        self.assertEqual(result["debit"], dict(oxygen_potential=16 * 4500, coal_blocks=16, fuel_cells=0))

    def test_gain_or_loss_is_reported(self):
        before = k.accounting_from(entities(self.layout), self.layout)
        for change, text in ((dict(empties=11), "canister shells 80 -> 79"), (dict(units=3001), "oxygen_potential rose"),
                             (dict(coal=63), "coal_blocks rose"), (dict(cells=63), "fuel cells 64 -> 63"),
                             (dict(cells=65), "fuel cells 64 -> 65")):
            result = k.reconcile(before, k.accounting_from(entities(self.layout, **change), self.layout))
            self.assertEqual(result["status"], "UNEXPLAINED", change)
            self.assertTrue(any(text in finding for finding in result["findings"]), result["findings"])
        used = k.accounting_from(entities(self.layout, cells=40), self.layout)
        self.assertEqual(k.reconcile(before, used, players=True)["status"], "RECONCILED")
        self.assertEqual(k.reconcile(before, k.accounting_from(entities(self.layout, cells=65), self.layout), players=True)["status"], "UNEXPLAINED")


class ClosedWorldTests(unittest.TestCase):
    def test_dimension_comes_from_the_region_path(self):
        world = Path("world")
        self.assertEqual(k.dimension_of(world, world / "region" / "r.8.8.mca"), k.OW)
        self.assertEqual(k.dimension_of(world, world / "dimensions" / k.NS / "moon" / "region" / "r.2.2.mca"), k.MOON)
        self.assertEqual(k.dimension_of(world, world / "dimensions" / k.NS / "space" / "entities" / "r.0.0.mca"), k.SPACE)

    def test_an_unreadable_entity_chunk_is_listed_not_dropped(self):
        with TemporaryDirectory() as temporary:
            world = Path(temporary)
            (world / "entities").mkdir()
            (world / "entities" / "r.0.0.mca").write_bytes(b"")
            with patch.object(k, "region_chunks", lambda path: iter([(3, 4, b"not zlib")])),                     patch("zlib.decompressobj", side_effect=ValueError("bad record")):
                rockets, unreadable = k.rocket_rows(world)
        self.assertEqual(rockets, [])
        self.assertEqual(unreadable, [dict(chunk="entities/r.0.0.mca#3,4", reason="bad record")])


class ReportTests(unittest.TestCase):
    def sample(self, **changes):
        base = dict(kind="components", rooms_active=list(range(16)), stations=10,
                    missions=dict(missions=213, active=0), unexpected_forced={k.OW: [], k.MOON: []})
        base.update(changes)
        return base

    def test_run_without_clients_reports_player_parts_as_client_steps(self):
        events = [dict(event="assembled-2048"), dict(event="flushed-save"), dict(event="flushed-save"), dict(event="restart"),
                  dict(event="boundary-opened"), dict(event="boundary-restored")]
        report = k.component_report(events, [self.sample(), self.sample(missions=dict(missions=213, active=3))], False)
        self.assertEqual(report["16 supplied sealed rooms"], "PRESENT")
        self.assertEqual(report["ten station records"], "PRESENT")
        self.assertEqual(report["missions ACTIVE at each sample"], [0, 3])
        self.assertEqual(report["2,048-block assembly"], "PRESENT")
        for name in ("two rockets in flight", "2,048-block disassembly", "dimension transfer"):
            self.assertEqual(report[name], "CLIENT_STEP_NOT_EXECUTED")
        self.assertTrue(report["two player connections"].startswith("MISSING"))
        self.assertTrue(report["raw ticks"].startswith("MISSING"))
        self.assertEqual((report["flushed saves"], report["orderly restart"], report["unexpected forced chunks"]), (2, "PRESENT", []))

    def test_shortfalls_are_missing_and_never_assumed(self):
        report = k.component_report([], [self.sample(rooms_active=list(range(15)), stations=9,
                                                     unexpected_forced={k.OW: [[1, 2]], k.MOON: []})], True)
        self.assertEqual(report["16 supplied sealed rooms"], "MISSING")
        self.assertEqual(report["ten station records"], "MISSING")
        self.assertEqual(report["2,048-block assembly"], "MISSING")
        self.assertEqual(report["orderly restart"], "MISSING")
        self.assertEqual(len(report["unexpected forced chunks"]), 1)
        self.assertEqual(k.component_report([], [], True)["16 supplied sealed rooms"], "MISSING")

    def test_hook_results_are_labelled_as_such(self):
        events = [dict(event="two-rockets-in-flight"), dict(event="landed"), dict(event="disassembled-2048")]
        report = k.component_report(events, [self.sample()], True)
        self.assertEqual({report[n] for n in ("two rockets in flight", "2,048-block disassembly", "dimension transfer")},
                         {"PRESENT_BY_HOOK"})
        self.assertEqual(report["raw ticks"], "PRESENT")


class SettleTests(unittest.TestCase):
    def test_a_stale_lit_state_after_a_load_does_not_count_as_settled(self):
        full = list(range(16))
        polls = iter([full, [5, 6, 9, 10], full, full, full])
        with patch.object(k, "lit_rooms", lambda console: next(polls)), patch.object(k.time, "sleep", lambda seconds: None):
            result = k.settled_rooms(None)
        self.assertTrue(result["settled"])
        self.assertEqual(list(polls), [])  # all five polls were needed: the first full poll alone settles nothing

    def test_rooms_that_never_all_light_are_reported_unsettled(self):
        clock = iter(range(0, 10000, 100))
        with patch.object(k, "lit_rooms", lambda console: [5, 6, 9, 10]), patch.object(k.time, "sleep", lambda seconds: None),                 patch.object(k.time, "monotonic", lambda: next(clock)):
            result = k.settled_rooms(None, timeout=300)
        self.assertEqual((result["settled"], result["rooms_active"]), (False, [5, 6, 9, 10]))


class LockTests(unittest.TestCase):
    def test_lock_owner_must_be_this_task_and_hold_the_lock(self):
        with TemporaryDirectory() as temporary:
            lock = Path(temporary) / "heavy-jvm"
            lock.mkdir()
            (lock / "owner.txt").write_text("W452 pid=7 token 2026-11-23\n", encoding="utf-8")
            with patch.object(k.chain, "LOCK", lock), patch.object(k.shutil, "disk_usage") as usage, \
                    patch.object(k.sys, "platform", "win32"):
                usage.return_value.free = 11 * 1024**3
                k.check_lock("W452 pid=7 token")
                for token in ("W440 pid=7 token", "W452 pid=8 other", "", None):
                    with self.assertRaises(ValueError):
                        k.check_lock(token)
                usage.return_value.free = 9 * 1024**3
                with self.assertRaises(ValueError):
                    k.check_lock("W452 pid=7 token")

    def test_parent_site_must_be_free(self):
        self.assertEqual(k.FREE_REGIONS, ("region/r.8.8.mca", f"dimensions/{k.NS}/moon/region/r.2.2.mca"))
        self.assertEqual(copy.deepcopy(k.layout()), k.layout())


if __name__ == "__main__":
    unittest.main()
