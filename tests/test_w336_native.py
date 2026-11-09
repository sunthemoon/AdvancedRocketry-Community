"""W336 native observation oracles; no JVM, filesystem mutation outside TemporaryDirectory or altered legacy tests."""
import copy
import hashlib
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import loader_cut_nbt as nbt
import run_v180_w336_downgrade as downgrade
import run_v180_w336_saturation as saturation
import run_v180_w336_timing as runner
import w336_bay_timing as timing
import w336_downgrade_oracle as old
import w336_native as native
import w336_saturation_oracle as pool


def query(tick):
    return f"[00:00:01] [Server thread/INFO] [minecraft/MinecraftServer]: The time is {tick}\n"


def claim(logical, state, reason):
    return f"ARCE_BAY_CLAIM transfer=t logical={logical} state={state} reason={reason}\n"


class TimingTest(unittest.TestCase):
    def test_ticks_use_vanilla_queries_not_log_clock(self):
        lines = [query(100), claim("a", "PROVE", "BAY_SOURCE_PROOF"), query(102),
                 claim("b", "PROVE", "BAY_PROOF_TURN"), query(104),
                 claim("a", "ENDED", "READY_SOURCE"), query(120),
                 claim("b", "ENDED", "READY_SOURCE"), query(145)]
        rows = timing.proof_turns(lines, ["a", "b"], 4)
        self.assertEqual(rows[0]["wait_through_ready_ticks"], [2, 20])
        self.assertFalse(rows[0]["queued"])
        self.assertTrue(rows[1]["queued"])
        self.assertEqual(rows[1]["ticks_per_census_slot"], [4, 10.75])
        observed = timing.observations(lines)
        self.assertEqual(observed["window_ticks"], 45)
        self.assertEqual(observed["events"][0]["ticks"], [100, 102])

    def test_missing_or_reset_query_refuses(self):
        for lines in ([query(1)], [query(3), query(2)]):
            with self.assertRaises(ValueError):
                timing.tick_samples(lines)
        with self.assertRaises(ValueError):
            timing.bracket([claim("a", "PROVE", "NONE"), query(1), query(2)], 0)

    def test_missing_ready_or_contention_refuses(self):
        lines = [query(0), claim("a", "PROVE", "BAY_SOURCE_PROOF"), query(2),
                 claim("a", "ENDED", "READY_SOURCE"), query(5)]
        with self.assertRaises(ValueError):
            timing.proof_turns(lines, ["a", "b"], 2)
        with self.assertRaises(ValueError):
            timing.proof_turns(lines, ["a"], 2)

    def test_retry_spacing_is_an_interval(self):
        lines = [query(0), "ARCE_RELEASE_SRW_INJECTED point=STAGED", query(2),
                 query(200), "ARCE_RELEASE_SRW_INJECTED point=STAGED", query(202)]
        self.assertEqual(timing.retry_spacing(lines, "ARCE_RELEASE_SRW_INJECTED"), [[198, 202]])
        with self.assertRaises(ValueError):
            timing.retry_spacing(lines, "ARCE_BAY_PROOF_REFUSED")

    def test_retry_impulse_uses_logged_delay_and_tick_bracket_not_repeated_stage(self):
        lines = [query(100), "ARCE_BAY_PROOF_REFUSED transfer=t logical=a reason=BARRIER_UNSTABLE retry=200", query(103)]
        self.assertEqual(timing.retry_probe_tick(lines, 1), 294)
        for value in (0, 201):
            lines[1] = f"ARCE_BAY_PROOF_REFUSED transfer=t logical=a reason=X retry={value}"
            with self.assertRaises(ValueError):
                timing.retry_probe_tick(lines, 1)

    def test_reload_impulses_stop_at_observed_refusal_and_have_a_hard_bound(self):
        class Session:
            def __init__(self, refusal_at):
                self.process = SimpleNamespace(lines=[])
                self.sent = []
                self.refusal_at = refusal_at
            def send(self, command, marker):
                self.sent.append(command)
                if len(self.sent) == self.refusal_at:
                    self.process.lines.append("ARCE_BAY_PROOF_REFUSED transfer=t logical=a reason=X retry=200")
        observed = Session(2)
        self.assertIn("retry=200", runner.refuse_d6(observed, "a")[1])
        self.assertEqual(observed.sent, ["arce bay release-test reload a"] * 2)
        missing = Session(100)
        with self.assertRaisesRegex(ValueError, "sixteen bounded"):
            runner.refuse_d6(missing, "a")
        self.assertEqual(len(missing.sent), 16)

    def test_reconnect_compares_login_with_expiry(self):
        lines = [query(10), "ARCE_RELEASE_BAY_EXIT_LOGIN logical=a", query(12),
                 query(210), "ARCE_PASSENGER_RECONNECT_EXPIRED player=p", query(212)]
        self.assertEqual(runner.reconnect(lines)["elapsed_ticks"], [198, 202])
        lines[-1] = query(1000)
        lines[-3] = query(900)
        with self.assertRaises(ValueError):
            runner.reconnect(lines)

    def test_two_census_sizes_from_snapshot_bounds(self):
        end = dict(source_dimension="minecraft:overworld", source_origin=[8, 80, 8], bounding_box=[-1, 0, 0, 1, 2, 0])
        other = dict(end, source_dimension="arce:moon")
        first = timing.census_slots(dict(source_snapshot=end, destination_snapshot=other))
        end = dict(end, source_origin=[15, 80, 8])
        second = timing.census_slots(dict(source_snapshot=end, destination_snapshot=other))
        self.assertEqual((len(first), len(second)), (2, 3))
        end["bounding_box"] = [-100, 0, -100, 100, 2, 100]
        with self.assertRaises(ValueError):
            timing.census_slots(dict(source_snapshot=end, destination_snapshot=other))


class SaturationTest(unittest.TestCase):
    def test_exact_distinct_boundary(self):
        self.assertEqual(len({(x >> 4, z >> 4) for x, _, z in pool.BACKGROUND}), 255)
        self.assertNotIn(pool.TARGET, pool.BACKGROUND)
        fixtures = pool.BACKGROUND + [pool.TARGET]
        for x, _, z in fixtures:
            self.assertGreater(max(abs(x - pool.OVERFLOW[0]), abs(z - pool.OVERFLOW[2])), 16 * 8)
        valid = {255: "minecraft:gold_block", 256: "minecraft:diamond_block", 257: "minecraft:diamond_block"}
        self.assertEqual(pool.check_boundary(valid)["saturation_denial"], 257)
        for at in (255, 256, 257):
            wrong = dict(valid); wrong[at] = "minecraft:emerald_block"
            with self.assertRaises(ValueError):
                pool.check_boundary(wrong)

    def test_nan_payload_is_noncanonical_and_typed(self):
        root = pool.unsupported()
        encoded = nbt.write(b"", root)
        decoded = nbt.read(encoded)[1]
        self.assertEqual(nbt.write(b"", decoded), encoded)
        self.assertIn(bytes.fromhex("7fc00001"), encoded)
        canonical = copy.deepcopy(root)
        canonical.value["opaque"] = nbt.Tag(nbt.FLOAT, struct.unpack(">f", bytes.fromhex("7fc00000"))[0])
        self.assertNotEqual(pool.raw_root(root), pool.raw_root(canonical))

    def test_all_device_families_and_control_have_distinct_ids(self):
        self.assertEqual(len(pool.DEVICES), 6)
        self.assertEqual(len({row[0] for row in pool.DEVICES.values()}), 6)

    def test_audit_allows_only_expected_native_refusals(self):
        event = "[Server thread/ERROR] [net.minecraftforge.eventbus.EventBus/EVENTBUS]: Exception caught during firing event: "
        chunk = "[Server thread/ERROR] [minecraft/ChunkMap]: Failed to save chunk "
        self.assertEqual(saturation.audit([event + saturation.SATURATED, chunk + "[64, 64]"]), 2)
        for line in (event + "foreign error", chunk + "[1, 1]", "[Server thread/FATAL] [forge/]: bad"):
            with self.assertRaises(ValueError):
                saturation.audit([line])
        self.assertEqual(saturation.audit([chunk + "575,511"], {(575, 511)}), 1)
        with self.assertRaises(ValueError):
            saturation.audit([chunk + "575,511"], {(576, 512)})

    def test_chunk_allowlist_is_read_from_stopped_seed_header(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder); (world / "region").mkdir()
            header = bytearray(4096); header[0:4] = bytes.fromhex("00000201")
            header[1023 * 4:4096] = bytes.fromhex("00000301")
            (world / "region/r.-1.2.mca").write_bytes(header)
            self.assertEqual(pool.generated_chunks(world), {(-32, 64), (-1, 95)})

    def test_distinct_loaded_positions_cannot_be_replaced_by_repeated_warning(self):
        class Fake:
            process = type("Process", (), {"lines": ["ARCE_X_UNSUPPORTED pos=1, 2, 3 status=FUTURE"] * 255})()
        with patch.object(saturation.time, "monotonic", side_effect=[0, 100]):
            with self.assertRaises(ValueError):
                saturation.await_denials(Fake(), [(1, 2, 3), (4, 5, 6)])

    def test_bay_guarded_and_cargo_unsupported_both_identify_loads(self):
        class Fake:
            process = type("Process", (), {"lines": [
                "ARCE_SATELLITE_BAY_GUARDED pos=1, 2, 3 guard=FUTURE keepable=false",
                "ARCE_ITEM_CARGO_UNSUPPORTED pos=4, 5, 6 status=FUTURE keepable=false"]})()
        saturation.await_denials(Fake(), [(1, 2, 3), (4, 5, 6)])

    def test_collateral_load_invalidates_boundary(self):
        fake = SimpleNamespace(process=SimpleNamespace(lines=[
            "ARCE_ITEM_CARGO_UNSUPPORTED pos=1, 2, 3 status=FUTURE",
            "ARCE_ITEM_CARGO_UNSUPPORTED pos=4, 5, 6 status=FUTURE"]))
        saturation.exact_denials(fake, [(1, 2, 3), (4, 5, 6)])
        with self.assertRaisesRegex(ValueError, "collateral"):
            saturation.exact_denials(fake, [(1, 2, 3)])

    def test_protection_disabled_is_rejected_by_terrain_oracle(self):
        with patch.object(pool, "check_roots"), patch.object(pool, "terrain", return_value="minecraft:emerald_block"):
            with self.assertRaisesRegex(ValueError, "Device-free terrain persisted"):
                pool.check_stage(Path("unused"), [], "minecraft:diamond_block")


class DowngradeTest(unittest.TestCase):
    def test_placed_roots_are_claimed_and_role_specific(self):
        for index, kind in enumerate(old.KINDS):
            command = old.placed_command(old.PLACED[index], kind, index)
            self.assertIn("schema_version:1,owner:[I;0,336,0,1]", command)
            self.assertIn(pool.DEVICES[kind][1] + ":{", command)
            if "unload" in kind:
                self.assertIn('role:"UNLOAD"', command)
            elif "load" in kind:
                self.assertIn('role:"LOAD"', command)

    def test_exact_historical_artifact_is_pinned(self):
        self.assertEqual(len(old.OLD_SHA256), 64)
        self.assertEqual(old.OLD_COMMIT, "ba046688b0df59ce89181475be8c9d237ea33a26")

    def test_original_whole_world_failure_is_preserved(self):
        before = dict({"level.dat": "a", "level.dat_old": "b", "region/r.0.0.mca": "terrain"},
                      **{"data/" + name: name for name in old.STORES})
        after = dict(before, **{"level.dat": "c", "level.dat_old": "a"})
        removed = {native.NS + identity for identity, _ in pool.DEVICES.values()}
        with patch.object(old, "registry_ids", side_effect=[removed, set(), removed, set()]):
            result = old.compare(before, after, Path("before"), Path("after"))
        self.assertEqual(result["w176_oracle"]["status"], "FAILED")
        self.assertEqual(result["terrain_and_device_roots"], "BYTE_IDENTICAL")
        after["region/r.0.0.mca"] = "changed"
        with patch.object(old, "registry_ids", side_effect=[removed, set(), removed, set()]):
            with self.assertRaises(ValueError):
                old.compare(before, after, Path("before"), Path("after"))

    def test_missing_or_changed_ledger_is_rejected(self):
        before = {"level.dat": "a", "level.dat_old": "b"}
        after = {"level.dat": "c", "level.dat_old": "a"}
        removed = {native.NS + identity for identity, _ in pool.DEVICES.values()}
        with patch.object(old, "registry_ids", side_effect=[removed, set(), removed, set()]):
            with self.assertRaisesRegex(ValueError, "Protected store absent"):
                old.compare(before, after, Path("before"), Path("after"))

    def test_seed_ledger_has_current_schema_and_exact_fields(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder); (world / "data").mkdir()
            downgrade.seed_ledger(world)
            root = nbt.read_gzip(world / "data" / old.STORES[1])[1]
            fields = nbt.compound(root, "data")
            self.assertEqual(nbt.compound(fields, "schema_version"), nbt.Tag(nbt.INT, 2))
            self.assertEqual(nbt.compound(fields, "format_epoch").value, b"v1.8.0-resource-escrow")
            for key in ("records", "floors", "tombstones", "rocket_authorities", "armed_flights"):
                self.assertEqual(nbt.compound(fields, key), nbt.Tag(nbt.LIST, (0, [])))
            with self.assertRaises(ValueError):
                downgrade.seed_ledger(world)


class HarnessTest(unittest.TestCase):
    def test_remaining_vanilla_force_ticket_refuses_restart_fixture(self):
        with tempfile.TemporaryDirectory() as folder:
            world = Path(folder); (world / "data").mkdir()
            def write(values):
                nbt.write_gzip(world / "data/chunks.dat", b"", nbt.Tag(nbt.COMPOUND, {
                    "data": nbt.Tag(nbt.COMPOUND, {"Forced": nbt.Tag(nbt.LONGS, values)})}))
            write([])
            native.require_unforced(world)
            write([2207613190666])
            with self.assertRaisesRegex(ValueError, "persistent vanilla"):
                native.require_unforced(world)

    def test_candidate_is_installed_before_an_adapter_can_start(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); parent = root / "Temp"; parent.mkdir()
            source = root / "source"; (source / "libraries").mkdir(parents=True)
            jar = root / "candidate.jar"; jar.write_bytes(b"pinned candidate")
            args = SimpleNamespace(session_dir=parent / "session", evidence_dir=root / "evidence",
                                   jar=jar, source_commit="a", candidate_commit="a", jar_sha256="b",
                                   lock_owner="test", source_server=source)
            with patch.object(native, "TEMP", parent), patch.object(native, "verify_candidate", return_value={}), \
                    patch.object(native, "require_lock"), patch.object(native.smoke, "allocate_port", return_value=32000):
                run = native.Native(args)
                try:
                    self.assertEqual((run.session / "mods/w336.jar").read_bytes(), jar.read_bytes())
                finally:
                    run.close()

    def test_candidate_arguments_are_required(self):
        parser = native.parser("test")
        args = parser.parse_args(["template", "--jar", "test.jar", "--session-dir", "session", "--evidence-dir", "evidence",
                                  "--source-commit", "abc", "--candidate-commit", "def", "--jar-sha256", "a" * 64,
                                  "--java", "java", "--lock-owner", "owner", "--smoke"])
        self.assertTrue(args.smoke)
        self.assertEqual(args.candidate_commit, "def")

    def test_archive_preserves_all_dimensions_and_metadata(self):
        with tempfile.TemporaryDirectory() as folder:
            root = Path(folder); world = root / "world"; world.mkdir()
            evidence = root / "evidence"; evidence.mkdir()
            for name in ("level.dat", "DIM-1/region/r.0.0.mca", "data/ledger.dat", "session.lock"):
                file = world / name; file.parent.mkdir(parents=True, exist_ok=True); file.write_bytes(name.encode())
            fake = type("Fake", (), dict(process=None, evidence=evidence))()
            result = native.Native.capture(fake, world, "raw")
            self.assertEqual(len(result["members"]), 3)
            self.assertEqual(result["members"]["level.dat"], hashlib.sha256(b"level.dat").hexdigest())


if __name__ == "__main__":
    unittest.main()
