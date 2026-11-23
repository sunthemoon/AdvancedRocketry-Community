"""Portable tests of the K12 planner and log readers; native JVM runs are outside governance."""
import json
from pathlib import Path
import tempfile
import unittest

from scripts.campaign_prep import k12_queues as k12

T = "[10:00:00] [Server thread/INFO] [minecraft/MinecraftServer]: The time is {}"
A, B = "11111111-1111-4111-8111-111111111111", "22222222-2222-4222-8222-222222222222"


def claim(logical, reason):
    return f"[10:00:00] [Server thread/INFO] [x/]: ARCE_BAY_CLAIM transfer=t-{logical[:4]} logical={logical} state=OWNED_WAIT reason={reason}"


def report(cell, kind, rocket, device, epoch=0, watching="false"):
    return (f"ARCE_RELEASE_R042_REPORT cell={cell} kind={kind} found=true rocket={rocket} device={device} "
            f"rocketEpoch={epoch} deviceEpoch={epoch} rocketMarkers=0 deviceMarkers=0 locked=false protected=true "
            f"refusal=NONE watching={watching} logical={A}")


def raw(tick, first, values):
    data = ",".join(f"{first + i}:{value}" for i, value in enumerate(values))
    return f"ARCE_RELEASE_TEST ticks_raw tick={tick} entries={len(values)} data={data}"


class K12PlanTests(unittest.TestCase):
    def test_bounds_are_read_from_the_frozen_source(self):
        bounds = k12.recheck_bounds()
        self.assertEqual((64, 16, 8), (bounds["pending_server"], bounds["pending_owner"], bounds["pending_rocket"]))
        self.assertEqual(400, bounds["ticket_timeout_ticks"])

    def test_a_changed_bound_is_refused(self):
        with tempfile.TemporaryDirectory() as temp:
            for file, _, _ in list(k12.BOUNDS.values()) + [k12.CELLS_SOURCE + (0,)]:
                target = Path(temp) / k12.common.JAVA_ROOT / file
                target.parent.mkdir(parents=True, exist_ok=True)
                source = (k12.common.ROOT / k12.common.JAVA_ROOT / file).read_text(encoding="utf-8")
                target.write_text(source.replace("PER_OWNER = 16", "PER_OWNER = 32"), encoding="utf-8")
            with self.assertRaisesRegex(ValueError, "pending_owner"):
                k12.recheck_bounds(temp)

    def test_queue_sizes_follow_the_bounds(self):
        plan = k12.plan()
        self.assertEqual(["bay-queue", "cargo-cells", "first-event", "mixed", "wait23"], sorted(plan))
        self.assertEqual(8, len(plan["bay-queue"]["claims"]))  # 16 per owner, two bays per fixture rocket
        self.assertGreater(len(plan["bay-queue"]["claims"]), 2)  # J05: more than two queued claims
        self.assertEqual(1, len(plan["wait23"]["claims"]))
        self.assertNotIn("cells", plan["wait23"])
        mixed = plan["mixed"]
        self.assertLessEqual(len(mixed["claims"]) * k12.FIXTURE_BAYS + mixed["cells"]["operations"], 64)
        self.assertEqual(3, len(set(mixed["cells"]["owners"].values())))
        self.assertNotIn(k12.BAY_OWNER, mixed["cells"]["owners"].values())
        self.assertEqual(("2048", "64"), (plan["cargo-cells"]["parent"], plan["first-event"]["parent"]))
        for spec in plan.values():
            self.assertTrue(set(spec["rows"]) <= {"J05", "J08", "J09", "J11"})

    def test_corner_origins_give_more_than_three_source_columns(self):
        origins = k12.claim_origins(8)
        self.assertEqual(8, len({tuple(origin) for origin in origins}))
        for origin in origins:
            self.assertEqual((15, 15), (origin[0] & 15, origin[2] & 15))
            self.assertEqual(4, len(k12.source_columns(origin)))
        self.assertEqual(1, len(k12.source_columns(k12.claim_origins(1, corner=False)[0])))

    def test_census_slots_match_the_bay_rectangle(self):
        end = lambda dim, origin: dict(source_dimension=dim, source_origin=origin, bounding_box=[-1, 0, 0, 1, 2, 0])
        record = dict(source_snapshot=end(k12.OW, [742015, 80, 742015]), destination_snapshot=end(k12.MOON, [8, 70, 8]))
        slots = k12.census_slots(record)
        self.assertEqual([k12.OW, 46375, 46375], slots[0])
        self.assertEqual([k12.MOON, 0, 0], slots[1])
        self.assertEqual(5, len(slots))  # four source columns and one destination column
        wide = dict(record, source_snapshot=dict(end(k12.OW, [0, 80, 0]), bounding_box=[0, 0, 0, 90, 2, 90]))
        with self.assertRaisesRegex(ValueError, "production bound"):
            k12.census_slots(wide)

    def test_every_uncovered_item_has_a_reason(self):
        self.assertTrue(all(len(reason) > 40 for reason in k12.NOT_COVERED.values()))
        self.assertTrue(any(key.startswith("J08") for key in k12.NOT_COVERED))
        self.assertTrue(any(key.startswith("J10") for key in k12.NOT_COVERED))


class K12ReaderTests(unittest.TestCase):
    def test_brackets_and_elapsed_keep_the_uncertainty(self):
        lines = [T.format(100), "event", T.format(103), "later", T.format(140)]
        self.assertEqual([100, 103], k12.bracket(lines, 1))
        self.assertEqual([0, 40], k12.elapsed([100, 103], [103, 140]))
        with self.assertRaises(ValueError):
            k12.bracket(lines, 4)
        with self.assertRaises(ValueError):
            k12.elapsed([100, 103], [90, 95])

    def test_status_durable_part_ignores_session_fields(self):
        line = ("ARCE_RELEASE_BAY_STATUS logical={0} claim=PROVE hold=NONE reason=BAY_SOURCE_PROOF "
                "receipts=[op2:s2:REJECTED:LOCKED:true:false,op1:s1:REGISTERED:NONE:true:true] transfer=LANDED "
                "rocket={1} state=LANDED y=70.0 packages=a+b locked=true consumed=-+- owned=1").format(A, B)
        status = k12.parse_status(line)
        self.assertEqual("PROVE", status["claim"])
        later = k12.parse_status(line.replace("claim=PROVE", "claim=RESUME").replace(f"rocket={B}", "rocket=NONE")
                                 .replace(":true:false", ":false:false").replace(":NONE:true:true", ":NONE:false:true"))
        self.assertEqual(k12.durable_status(status), k12.durable_status(later))
        changed = k12.parse_status(line.replace("REGISTERED", "INTENT"))
        self.assertNotEqual(k12.durable_status(status), k12.durable_status(changed))
        with self.assertRaises(ValueError):
            k12.parse_status("nothing")

    def test_claim_waits_report_queue_order_and_unfinished_claims(self):
        lines = [T.format(10), claim(A, "BAY_SOURCE_PROOF"), claim(B, "BAY_PROOF_TURN"), T.format(12),
                 claim(A, "READY_SOURCE"), T.format(50), claim(B, "BAY_SOURCE_PROOF"), T.format(52),
                 claim(B, "READY_SOURCE"), T.format(90)]
        waits = k12.claim_waits(lines, [A, B])
        first, second = waits["claims"]
        self.assertFalse(first["queued"])
        self.assertTrue(second["queued"])
        self.assertEqual([0, 40], first["wait_through_ready_ticks"])
        self.assertEqual([40, 80], second["wait_through_ready_ticks"])
        self.assertEqual([0, 40], second["turn_ticks"])
        self.assertEqual([40, 80], waits["whole_queue_ticks"])  # first seen [10, 12], last ready [52, 90]
        self.assertEqual([A, B], waits["order"])
        partial = k12.claim_waits(lines[:6], [A, B])
        self.assertIsNone(partial["whole_queue_ticks"])
        self.assertIn("unfinished_reason", partial["claims"][1])
        self.assertNotIn("wait_through_ready_ticks", partial["claims"][1])

    def test_cells_and_cycles(self):
        cell = f"ARCE_RELEASE_R042_CELL cell=A kind=ITEM_LOAD owner={A} logical={B} rocket={A} device={B}"
        self.assertEqual(A, k12.parse_cells([cell])["A"]["ITEM_LOAD"]["owner"])
        attempt = ("ARCE_RELEASE_R042_ATTEMPT cell=A kind={} found=true status={} admitted={} unchanged=false "
                   "pending=0>1 retained=0>0 ledger=absent>present logical=" + A)
        lines = [T.format(100), attempt.format("ITEM_LOAD", "PREPARING", "true"),
                 attempt.format("FLUID_LOAD", "ESCROW_FULL", "false"), T.format(101),
                 report("A", "ITEM_LOAD", 60, 10), T.format(110),
                 "ARCE_RELEASE_R042_GATE_CLOSED cell=A kind=ITEM_LOAD reason=CAPTURED tick=55", T.format(112),
                 report("A", "ITEM_LOAD", 64, 6, 1, "true"), T.format(118),
                 report("A", "ITEM_LOAD", 64, 6, 1), T.format(130)]
        cycles = {row["kind"]: row for row in k12.cargo_cycles(lines)["devices"]}
        self.assertEqual([17, 30], cycles["ITEM_LOAD"]["request_to_settled_ticks"])
        self.assertEqual("CAPTURED", cycles["ITEM_LOAD"]["capture_reason"])
        self.assertEqual("refused", cycles["FLUID_LOAD"]["unfinished_reason"])
        rows = k12.parse_reports(lines)["A"]["ITEM_LOAD"]
        self.assertTrue(k12.completed(rows, "ITEM_LOAD"))
        self.assertTrue(k12.untouched(k12.parse_reports(lines[:5])["A"]["ITEM_LOAD"], "ITEM_LOAD"))
        self.assertFalse(k12.completed(k12.parse_reports(lines[:9])["A"]["ITEM_LOAD"], "ITEM_LOAD"))

    def test_first_event_tick_candidates_come_from_raw_samples_only(self):
        elected = f"ARCE_FIRST_EVENT_ELECTED kind=first_moon_landing event={A} transfer={B} participants=0"
        lines = [T.format(5000), T.format(5040), elected, T.format(5041), T.format(5060)]
        replies = [raw(60, 31, [1_000_000] * 9 + [140_000_000] + [2_000_000] * 20)]
        pairs = [[5059, 60, 5060]]
        seen = k12.first_event_observation(lines, replies, pairs)
        self.assertEqual([5040, 5041], seen["election_game_ticks"])
        self.assertEqual([4999, 5000], seen["tick_offset"])
        self.assertEqual(list(range(39, 44)), [row["tick"] for row in seen["candidate_ticks"]])
        self.assertEqual(140.0, seen["largest_candidate_ms"])
        self.assertEqual(30, seen["window"]["tick_count"])
        self.assertEqual(0, seen["participants"])
        with self.assertRaisesRegex(ValueError, "exactly one"):
            k12.first_event_observation(lines + [elected], replies, pairs)
        with self.assertRaises(ValueError):
            k12.tick_offset([])
        with self.assertRaisesRegex(ValueError, "disagree"):
            k12.tick_offset([[100, 10, 101], [300, 20, 301]])

    def test_ticket_series_keeps_raw_rows_and_unfinished_windows(self):
        rows = [dict(ticks=[100, 102], loaded=[True, True]), dict(ticks=[300, 302], loaded=[True, False]),
                dict(ticks=[520, 522], loaded=[False, False])]
        done = k12.ticket_series(rows, [90, 95])
        self.assertEqual([425, 432], done["end_to_unloaded_ticks"])
        self.assertEqual([300, 302], done["last_loaded"])
        self.assertEqual(rows, done["raw_rows"])
        self.assertEqual([[425, 432], [205, 212]], [column["end_to_unloaded_ticks"] for column in done["columns"]])
        self.assertEqual([100, 102], done["columns"][1]["last_loaded"])
        open_window = k12.ticket_series(rows[:2], [90, 95])
        self.assertIn("unfinished_reason", open_window)
        self.assertNotIn("end_to_unloaded_ticks", open_window)

    def test_cold_columns_read_the_region_header_only(self):
        with tempfile.TemporaryDirectory() as temp:
            region = Path(temp) / "dimensions/advancedrocketrycommunity/moon/region"
            region.mkdir(parents=True)
            header = bytearray(8192)
            header[4 * (3 + 2 * 32):4 * (3 + 2 * 32) + 4] = b"\0\0\2\1"
            (region / "r.0.0.mca").write_bytes(bytes(header))
            before = (region / "r.0.0.mca").read_bytes()
            rows = k12.cold_columns(temp, k12.MOON, [[3, 2], [4, 2], [40, 2]])
            self.assertEqual(["generated", "new_terrain", "new_terrain"], [row["cohort"] for row in rows])
            self.assertEqual(before, (region / "r.0.0.mca").read_bytes())

    def test_saved_advancement_oracle(self):
        name = k12.ADVANCEMENTS["moon"]
        done = json.dumps({name: {"criteria": {"event": "2026-11-23 10:00:00 +0800"}, "done": True}, "DataVersion": 3465})
        self.assertTrue(k12.advancement_done(done, name))
        self.assertFalse(k12.advancement_done(done, k12.ADVANCEMENTS["station"]))
        self.assertFalse(k12.advancement_done(json.dumps({name: {"criteria": {}, "done": False}}), name))


class K12RecipeTests(unittest.TestCase):
    def test_fourteen_variants_use_external_stops_only(self):
        rows = k12.recipes()
        self.assertEqual(["FM10a", "FM10b", "FM10c", "FM10d", "FX-Ma", "FX-Mb", "FX-Wa", "FX-Wb", "FX-Rm", "FX-Rw",
                          "FX-Sm", "FX-Sw", "FX-Dm", "FX-Dw"], [row["variant"] for row in rows])
        for row in rows:
            self.assertIn(row["stop"], k12.STOPS)
            self.assertIn(row["advancement"], k12.ADVANCEMENTS.values())
            self.assertTrue(row["restart_1"] and row["restart_2"])
            self.assertNotIn("firstEventCut", json.dumps(row))
            self.assertNotIn("advancement grant", json.dumps(row))
        saves = {row["variant"]: row["player_save_before_stop"] for row in rows}
        self.assertEqual((True, False, True, False), tuple(saves[v] for v in ("FX-Ma", "FX-Mb", "FX-Wa", "FX-Wb")))
        self.assertEqual(5, sum(row["kind"] == "station" for row in rows))  # nine Moon and five station variants

    def test_stop_lines_exist_in_the_frozen_source(self):
        source = (k12.common.ROOT / k12.common.JAVA_ROOT
                  / "progression/classic/firstevent/ClassicFirstEventDelivery.java").read_text(encoding="utf-8").splitlines()
        self.assertIn("ClassicAdvancementEvents.TRIGGER.trigger(player, event(kind));", source[484])
        self.assertIn("return done(player, kind);", source[485])
        self.assertIn("defer(player, kind, now);", source[436])
        self.assertIn("queue.removeConnection(", source[443])
        self.assertIn("private void requestDelivered(", source[315])

    def test_award_probes_check_both_players_and_forbid_shortcuts(self):
        probe = k12.award_probes("Alice", "Bob", "station")
        self.assertEqual(2, len(probe["live"]))
        self.assertIn("advancements={" + k12.ADVANCEMENTS["station"] + "=true}", probe["live"][0])
        self.assertIn("name=Bob", probe["live"][1])
        self.assertIn("advancement grant", probe["forbidden"])
        self.assertIn("arce.releaseTest.firstEventCut", probe["forbidden"])

    def test_procedure_names_every_fixture_and_the_label(self):
        procedure = k12.procedure()
        self.assertEqual(sorted(k12.plan()), sorted(procedure["steps"]))
        self.assertIn("NOT_REF", procedure["label"])
        self.assertEqual(2, len(procedure["journal_hold"]))


class FakeProcess:
    """Answers each command at once, as the server thread does for console commands."""
    def __init__(self, loaded_after=0):
        self.lines, self.sent, self.loaded_after, self.stuck = [], [], loaded_after, 0

    def command(self, value):
        self.sent.append(value)
        if value.startswith("say "):
            self.lines.append("[Server] " + value[4:])
        elif "run say K04_LOADED" in value:
            self.loaded_after -= 1
            if self.loaded_after < 0:
                self.lines.append("[Server] K04_LOADED")
        elif value == "time query gametime":
            self.lines.append(T.format(100 + len(self.sent)))
        elif value.endswith("forceload query"):
            self.stuck -= 1
            self.lines.append("No force loaded chunks were found in x" if self.stuck < 0 else "A force loaded chunk was found in x at: [16, 16]")
        elif value == "bad":
            self.lines.append("Unknown or incomplete command, see below for error")

    def wait_for(self, pattern, timeout, start_at=0):
        return next(i for i in range(start_at, len(self.lines)) if pattern.search(self.lines[i]))


class K12ConsoleTests(unittest.TestCase):
    def test_console_serves_k04s_chunk_loader(self):
        from scripts.campaign_prep import k04_rockets as k04
        process = FakeProcess(loaded_after=2)
        console = k12.Console(process)
        k04.load_chunks(console, dict(rockets=[dict(origin=[256, 100, 256])]), (k12.OW,))
        self.assertEqual(1, sum(command.startswith("execute in minecraft:overworld run forceload add 240 240 287 287")
                                for command in process.sent))
        self.assertEqual(3, sum("run say K04_LOADED" in command for command in process.sent))

    def test_console_refuses_rejected_commands_and_reads_game_time(self):
        console = k12.Console(FakeProcess())
        with self.assertRaisesRegex(ValueError, "Rejected console command"):
            console.send("bad")
        self.assertGreater(console.tick(), 100)


    def test_forced_chunks_are_removed_until_the_query_is_empty(self):
        process = FakeProcess()
        process.stuck = 2  # the overworld needs three passes
        k12.unforce(k12.Console(process))
        removals = [command for command in process.sent if command.endswith("forceload remove all")]
        self.assertEqual(3, sum(k12.OW in command for command in removals))
        self.assertEqual(1, sum(k12.MOON in command for command in removals))
        process.stuck = 99
        with self.assertRaisesRegex(ValueError, "Forced chunks remain"):
            k12.unforce(k12.Console(process))


class K12SealTests(unittest.TestCase):
    def test_parent_checksum_list_must_match_exactly(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp).resolve() / "scene"
            (folder / "world").mkdir(parents=True)
            (folder / "world/level.dat").write_bytes(b"level")
            (folder / "build.json").write_bytes(b"{}")
            rows = k12.common.tree_hashes(folder)
            (folder / "SHA256SUMS.txt").write_text("".join(f"{h}  {p}\n" for p, h in rows.items()), encoding="ascii",
                                                   newline="\n")
            self.assertEqual(64, len(k12.verify_sealed(folder)))
            (folder / "extra.txt").write_bytes(b"x")
            with self.assertRaisesRegex(ValueError, "checksum list"):
                k12.verify_sealed(folder)
            (folder / "extra.txt").unlink()
            (folder / "world/level.dat").write_bytes(b"changed")
            with self.assertRaisesRegex(ValueError, "checksum list"):
                k12.verify_sealed(folder)


if __name__ == "__main__":
    unittest.main()
