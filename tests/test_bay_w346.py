"""W346: the save-boundary runner's tables, parsers, raw readers and oracles, and its first start against a scripted
server (no Java, no world, no lock)."""
from pathlib import Path
import struct
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import uuid
import zlib

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "scripts"))
import bay_integrated_evidence as evidence  # noqa: E402
import loader_cut_nbt as nbt  # noqa: E402
import run_v180_bay_forced_stop as bay  # noqa: E402
import run_v180_bay_save_boundary as w346  # noqa: E402

L = "11111111-1111-4111-8111-111111111111"
DATA = "22222222-2222-4222-8222-222222222222"
SOLAR = "44444444-4444-4444-8444-444444444444"
T = "55555555-5555-4555-8555-555555555555"
OP = "66666666-6666-4666-8666-666666666666"
DEST = "77777777-7777-4777-8777-777777777777"
SOURCE = "88888888-8888-4888-8888-888888888888"
OWNER = "00000000-0000-4000-8000-000000128d01"
CASE = "W346_B6_SAVE_BOUNDARY"
ORIGIN = [8, 26, 8]


def string(value: str) -> nbt.Tag:
    return nbt.Tag(nbt.STRING, value.encode("ascii"))


def ident(value: str) -> nbt.Tag:
    return nbt.uuid_tag(uuid.UUID(value))


def bay_root(consumed: str | None) -> nbt.Tag:
    fields = dict(schema_version=nbt.Tag(nbt.INT, 1), owner=ident(OWNER))
    if consumed:
        fields["consumed_op"] = ident(consumed)
    else:
        fields["package"] = nbt.Tag(nbt.COMPOUND, dict(id=string("advancedrocketrycommunity:solar_package"), Count=nbt.Tag(nbt.BYTE, 1)))
    return nbt.Tag(nbt.COMPOUND, fields)


def snapshot(dimension: str, bays=(OP, None), extra_fields: dict | None = None) -> nbt.Tag:
    blocks = [nbt.Tag(nbt.COMPOUND, dict(position=nbt.Tag(nbt.INTS, [0, 0, 0]), palette=nbt.Tag(nbt.INT, 0)))]
    for index, consumed in enumerate(bays):
        root = bay_root(consumed)
        if extra_fields and index == 0:
            root.value.update(extra_fields)
        blocks.append(nbt.Tag(nbt.COMPOUND, dict(position=nbt.Tag(nbt.INTS, [0, index + 1, 0]), palette=nbt.Tag(nbt.INT, 1),
                                                 block_entity=nbt.Tag(nbt.COMPOUND, dict(adapter=string(w346.BAY_ADAPTER), data=root)))))
    blocks.append(nbt.Tag(nbt.COMPOUND, dict(position=nbt.Tag(nbt.INTS, [0, 9, 0]), palette=nbt.Tag(nbt.INT, 2),
                                             block_entity=nbt.Tag(nbt.COMPOUND, dict(adapter=string("advancedrocketrycommunity:tank"),
                                                                                     data=nbt.Tag(nbt.COMPOUND, dict(fuel=nbt.Tag(nbt.INT, 4))))))))
    return nbt.Tag(nbt.COMPOUND, dict(source_dimension=string(dimension), source_origin=nbt.Tag(nbt.INTS, list(ORIGIN)),
                                      relative_blocks=nbt.Tag(nbt.LIST, (nbt.COMPOUND, blocks))))


def record(logical: str = L, phase: str = "COMMITTED", destination: str | None = DEST, bays=(OP, None),
           dimension: str = w346.MOON) -> nbt.Tag:
    fields = dict(transfer_id=ident(T), phase=string(phase), logical_rocket_id=ident(logical), source_entity_id=ident(SOURCE),
                  checksum=string("ab" * 32), source_snapshot=snapshot("minecraft:overworld", bays),
                  destination_snapshot=snapshot(dimension, bays))
    if destination:
        fields["destination_entity_id"] = ident(destination)
    return nbt.Tag(nbt.COMPOUND, fields)


def gzip_root(path: Path, data: dict) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    nbt.write_gzip(path, b"", nbt.Tag(nbt.COMPOUND, dict(data=nbt.Tag(nbt.COMPOUND, data), DataVersion=nbt.Tag(nbt.INT, 3465))))


def receipt(rocket: str, satellite: str, phase: str) -> nbt.Tag:
    return nbt.Tag(nbt.COMPOUND, dict(op=ident(OP), rocket=ident(rocket), satellite=ident(satellite), phase=string(phase),
                                      reason=string("NONE"), src_before=nbt.Tag(nbt.BYTES, b"\x01" * 32),
                                      dst_before=nbt.Tag(nbt.BYTES, b"\x02" * 32), package_sig=nbt.Tag(nbt.BYTES, b"\x03" * 32),
                                      released=nbt.Tag(nbt.BYTE, 0)))


def rocket(entity: str, logical: str = L, bays=(OP, None)) -> nbt.Tag:
    return nbt.Tag(nbt.COMPOUND, dict(id=string("advancedrocketrycommunity:rocket"), UUID=ident(entity),
                                      RocketEntityData=nbt.Tag(nbt.COMPOUND, dict(assembly_transaction_id=ident(logical),
                                                                                  snapshot=snapshot(w346.MOON, bays)))))


def item() -> nbt.Tag:
    return nbt.Tag(nbt.COMPOUND, dict(id=string("minecraft:item"), UUID=ident(OP)))


def region(folder: Path, chunks: dict) -> None:
    """One region file r.0.0.mca holding the given chunks (x, z in 0..31), each with its list of entities."""
    folder.mkdir(parents=True, exist_ok=True)
    records = []
    for (chunk_x, chunk_z), entities in sorted(chunks.items()):
        root = nbt.Tag(nbt.COMPOUND, dict(Position=nbt.Tag(nbt.INTS, [chunk_x, chunk_z]),
                                          Entities=nbt.Tag(nbt.LIST, (nbt.COMPOUND, list(entities)))))
        encoded = zlib.compress(nbt.write(b"", root))
        records.append(((chunk_x, chunk_z), struct.pack(">IB", len(encoded) + 1, 2) + encoded))
    data = bytearray(2 * nbt.SECTOR)
    for (chunk_x, chunk_z), payload in records:
        sectors = -(-len(payload) // nbt.SECTOR)
        offset = len(data) // nbt.SECTOR
        struct.pack_into(">I", data, ((chunk_x & 31) + (chunk_z & 31) * 32) * 4, offset << 8 | sectors)
        data.extend(payload + bytes(sectors * nbt.SECTOR - len(payload)))
    (folder / "r.0.0.mca").write_bytes(bytes(data))


def world(directory: Path, *, receipts=(), registry=(DATA,), records=None, overworld=None, moon=None) -> Path:
    data = directory / "data"
    gzip_root(data / bay.RECEIPTS, dict(receipts=nbt.Tag(nbt.LIST, (nbt.COMPOUND, list(receipts)))))
    gzip_root(data / bay.REGISTRY_FILE, dict(satellites=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [
        nbt.Tag(nbt.COMPOUND, dict(satellite_id=ident(value))) for value in registry]))))
    gzip_root(data / w346.TRANSFERS_FILE, dict(transfers=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [record()] if records is None else list(records)))))
    region(directory / w346.OVERWORLD_ENTITIES, overworld if overworld is not None else {(0, 0): []})
    region(directory / bay.MOON_ENTITIES, moon if moon is not None else {(0, 0): []})
    return directory


def fixture() -> dict:
    return dict(logical=L, transfer=T, data=DATA, solar=SOLAR, packages="p0+p1", roles=bay.ROLES["standard"],
                satellites=[DATA, SOLAR], rocket=SOURCE)


def autosave_line(**changes) -> str:
    fields = dict(logical=L, transfer=T, phase="COMMITTED", acknowledged="true", destination_level=w346.MOON, destination_entity=DEST,
                  destination_present="true", destination_chunk_loaded="true", source_count="0", result="true",
                  skipped=w346.MOON, saved="minecraft:overworld,minecraft:the_nether,minecraft:the_end")
    fields.update(changes)
    return ("[x] ARCE_RELEASE_SAVE_BOUNDARY_AUTOSAVE logical={logical} transfer={transfer} phase={phase} acknowledged={acknowledged} "
            "destination_level={destination_level} destination_entity={destination_entity} destination_present={destination_present} "
            "destination_chunk_loaded={destination_chunk_loaded} source_count={source_count} result={result} skipped=[{skipped}] "
            "saved=[{saved}]").format(**fields)


def status(**changes) -> dict:
    value = dict(logical=L, claim="NONE", hold="NONE", reason="NONE", receipts=[], transfer="COMMITTED", rocket=DEST, state="LANDED",
                 y="26.0", packages="c0+p1", locked="false", consumed=[OP, None], owned=1)
    value.update(changes)
    return value


def first(**changes) -> dict:
    value = dict(autosave=w346.parse_autosave(autosave_line()), at_kill=status(state="DESCENT"))
    value.update(changes)
    return value


class TableAndParserTests(unittest.TestCase):
    def test_the_case_stays_outside_the_forced_stop_tables_and_names_b65(self):
        self.assertFalse(set(w346.W346_CASES) & (set(bay.CASES) | set(bay.W331_CASES)))
        self.assertFalse(set(w346.W346_TRANCHES) & (set(bay.TRANCHES) | set(bay.W331_TRANCHES)))
        self.assertEqual([CASE], [case for cases in w346.W346_TRANCHES.values() for case in cases])
        spec = w346.W346_CASES[CASE]
        self.assertEqual(("B6.5",), spec["rows"])
        self.assertEqual(("B16_GUARD", "standard", w346.MOON, "LANDED"), (spec["cut"], spec["fixture"], spec["hold"], spec["settled"]))
        self.assertEqual((("COMMITTED", 0, 0, "REBUILD_DESTINATION", "RECOVERED"), ("COMMITTED", 0, 1, "KEEP_DESTINATION", "RECOVERED")),
                         spec["recovery"])
        self.assertIn(spec["settled"], bay.OUTCOMES)
        java = (evidence.REPOSITORY / "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/rocket/server/BayDeploymentCuts.java")
        self.assertIn("B16_GUARD(null, null, null)", java.read_text(encoding="utf-8"))

    def test_control_replies_parse_each_kind_and_refuse_anything_else(self):
        self.assertEqual(("HELD", w346.MOON, "true"),
                         w346.control_reply(f"[x] ARCE_RELEASE_SAVE_BOUNDARY_HELD level={w346.MOON} noSave=true"))
        self.assertEqual(("RESTORED", w346.MOON, "false"),
                         w346.control_reply(f"[x] ARCE_RELEASE_SAVE_BOUNDARY_RESTORED level={w346.MOON} noSave=false"))
        self.assertEqual(("REFUSED_HOLD", w346.MOON, "ALREADY_SAVE_OFF"), w346.control_reply(
            f"[x] ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=hold level={w346.MOON} reason=ALREADY_SAVE_OFF"))
        with self.assertRaises(bay.smoke.SmokeError):
            w346.control_reply("[x] Saved the game")

    def test_the_autosave_report_must_show_the_acknowledged_commit_and_skip_only_the_held_destination(self):
        good = w346.parse_autosave(autosave_line())
        self.assertEqual(([w346.MOON], 0, True), (good["skipped"], good["source_count"], good["destination_chunk_loaded"]))
        w346.check_autosave(good, fixture())
        for bad in (dict(phase="SOURCE_REMOVED"), dict(acknowledged="false"), dict(destination_level="minecraft:overworld"),
                    dict(destination_entity="NONE"), dict(destination_present="false"), dict(destination_chunk_loaded="false"),
                    dict(source_count="1"), dict(result="false"), dict(skipped=""), dict(skipped=f"{w346.MOON},minecraft:the_end"),
                    dict(saved="minecraft:the_end"), dict(saved=f"minecraft:overworld,{w346.MOON}"), dict(transfer=OP), dict(logical=DATA)):
            with self.subTest(bad=bad), self.assertRaises(bay.smoke.SmokeError):
                w346.check_autosave(w346.parse_autosave(autosave_line(**bad)), fixture())
        with self.assertRaises(bay.smoke.SmokeError):
            w346.parse_autosave("[x] ARCE_RELEASE_SAVE_BOUNDARY_WAIT logical=x transfer=NONE phase=NONE acknowledged=false destination_held=true")

    def test_recovery_rows_keep_only_the_transfers_lines(self):
        line = "[x] ARCE_TRANSFER_RECOVERY transfer={} phase=COMMITTED source_count=0 destination_count={} action={} status=RECOVERED"
        lines = [line.format(T, 0, "REBUILD_DESTINATION"), line.format(OP, 1, "KEEP_DESTINATION"), "[x] other"]
        self.assertEqual([("COMMITTED", 0, 0, "REBUILD_DESTINATION", "RECOVERED")], w346.recovery_rows(lines, T))

    def test_the_kill_needs_the_consumed_committed_destination_in_memory(self):
        report = w346.parse_autosave(autosave_line())
        w346.check_at_kill(status(state="DESCENT"), fixture(), report)
        for bad in (status(transfer="SOURCE_REMOVED"), status(rocket=SOURCE), status(claim="RETIRE"), status(owned=0),
                    status(receipts=[dict(op=OP, satellite=DATA, phase="REGISTERED", reason="NONE", acknowledged=True)]),
                    status(consumed=[None, None]), status(consumed=[OP, OP]), status(packages="p0+p1"), status(packages="c0+c1"),
                    status(state="TRANSIT")):
            with self.subTest(bad=bad), self.assertRaises(bay.smoke.SmokeError):
                w346.check_at_kill(bad, fixture(), report)


class RawReaderTests(unittest.TestCase):
    def test_bay_roots_read_only_bay_payloads_in_block_order_and_refuse_unknown_fields(self):
        roots = w346.bay_roots(nbt.plain(snapshot(w346.MOON)))
        self.assertEqual([dict(consumed=OP, package=False), dict(consumed=None, package=True)], roots)
        with self.assertRaisesRegex(bay.smoke.SmokeError, "Unexpected bay root fields"):
            w346.bay_roots(nbt.plain(snapshot(w346.MOON, extra_fields=dict(stray=nbt.Tag(nbt.INT, 1)))))

    def test_the_journal_record_reads_one_record_for_l_and_refuses_two(self):
        with tempfile.TemporaryDirectory() as directory:
            root = world(Path(directory))
            found = w346.journal_record(root, L)
            self.assertEqual((T, "COMMITTED", DEST, SOURCE, w346.MOON, ORIGIN),
                             (found["transfer"], found["phase"], found["destination_entity"], found["source_entity"],
                              found["destination_dimension"], found["destination_origin"]))
            self.assertEqual(w346.expected_bays([OP, None]), found["destination_bays"])
            self.assertIsNone(w346.journal_record(root, DATA))
            world(Path(directory), records=[record(), record()])
            with self.assertRaisesRegex(bay.smoke.SmokeError, "2 records"):
                w346.journal_record(root, L)
            (root / "data" / w346.TRANSFERS_FILE).unlink()
            self.assertIsNone(w346.journal_record(root, L))

    def test_entity_reads_find_rockets_with_bays_items_files_and_the_stored_destination_chunk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = world(Path(directory), moon={(0, 0): [rocket(DEST), item()], (5, 7): [rocket(OP, logical=DATA)]})
            self.assertEqual([dict(uuid=DEST, bays=w346.expected_bays([OP, None]))], w346.rockets_of(root, L, bay.MOON_ENTITIES))
            self.assertEqual([], w346.rockets_of(root, L, w346.OVERWORLD_ENTITIES))
            self.assertEqual((1, 0), (w346.item_entities(root, bay.MOON_ENTITIES), w346.item_entities(root, w346.OVERWORLD_ENTITIES)))
            self.assertTrue(w346.destination_chunk_stored(root, ORIGIN))
            self.assertFalse(w346.destination_chunk_stored(root, [40, 26, 8]))
            self.assertEqual(["dimensions/advancedrocketrycommunity/moon/entities/r.0.0.mca", "entities/r.0.0.mca"],
                             w346.entity_files(root))


class OracleTests(unittest.TestCase):
    def test_the_cut_oracle_accepts_the_save_boundary_state_and_refuses_each_violation(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            observed = w346.cut_disk(world(base / "good"), fixture(), first())
            self.assertEqual((True, 0, []), (observed["destination_chunk_stored"], observed["item_entities"], observed["destination_rockets"]))
            violations = {
                "receipt names L": dict(receipts=[receipt(L, DATA, "REGISTERED")]),
                "exactly the group's registered": dict(registry=()),
                "exactly the group's registered ": dict(registry=(DATA, SOLAR)),
                "no record for L": dict(records=[]),
                "committed T'": dict(records=[record(phase="SOURCE_REMOVED")]),
                "committed T' ": dict(records=[record(destination=OP)]),
                "committed T'  ": dict(records=[record(dimension="minecraft:overworld")]),
                "consumed snapshots": dict(records=[record(bays=(None, None))]),
                "rocket of L is on disk": dict(overworld={(0, 0): [rocket(SOURCE)]}),
                "rocket of L is on disk ": dict(moon={(0, 0): [rocket(DEST)]}),
                "no stored entity record": dict(moon={(4, 4): []}),
                "item entities": dict(overworld={(0, 0): [item()]}),
            }
            for index, (message, changes) in enumerate(violations.items()):
                with self.subTest(message=message), self.assertRaisesRegex(bay.smoke.SmokeError, message.strip()):
                    w346.cut_disk(world(base / f"bad{index}", **changes), fixture(), first())
            with self.assertRaisesRegex(bay.smoke.SmokeError, "reported destination"):
                w346.cut_disk(world(base / "other"), fixture(), first(autosave=w346.parse_autosave(autosave_line(destination_entity=OP))))
            with self.assertRaisesRegex(bay.smoke.SmokeError, "consumed snapshots"):
                w346.cut_disk(world(base / "other2"), fixture(), first(at_kill=status(consumed=[DATA, None])))

    def test_the_restart_oracle_needs_exactly_the_rebuilt_destination_and_keeps_every_entity_file(self):
        with tempfile.TemporaryDirectory() as directory:
            base = Path(directory)
            cut = w346.cut_disk(world(base / "cut"), fixture(), first())
            good = world(base / "good", moon={(0, 0): [rocket(DEST)]})
            observed = w346.restart_disk(good, fixture(), cut, cut["entity_files"], 1)
            self.assertEqual([dict(uuid=DEST, bays=w346.expected_bays([OP, None]))], observed["destination_rockets"])
            violations = {
                "a receipt names L": dict(receipts=[receipt(L, DATA, "REGISTERED")], moon={(0, 0): [rocket(DEST)]}),
                "registry changed": dict(registry=(DATA, SOLAR), moon={(0, 0): [rocket(DEST)]}),
                "T' changed": dict(records=[record(phase="PREPARED")], moon={(0, 0): [rocket(DEST)]}),
                "T' changed ": dict(records=[], moon={(0, 0): [rocket(DEST)]}),
                "exactly the rebuilt destination": dict(moon={(0, 0): []}),
                "exactly the rebuilt destination ": dict(moon={(0, 0): [rocket(DEST), rocket(OP)]}),
                "exactly the rebuilt destination  ": dict(moon={(0, 0): [rocket(OP)]}),
                "exactly the rebuilt destination   ": dict(moon={(0, 0): [rocket(DEST, bays=(None, None))]}),
                "source of L was forced back": dict(moon={(0, 0): [rocket(DEST)]}, overworld={(0, 0): [rocket(SOURCE)]}),
                "item entities": dict(moon={(0, 0): [rocket(DEST), item()]}),
            }
            for index, (message, changes) in enumerate(violations.items()):
                with self.subTest(message=message), self.assertRaisesRegex(bay.smoke.SmokeError, message.strip()):
                    w346.restart_disk(world(base / f"bad{index}", **changes), fixture(), cut, cut["entity_files"], 1)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "entity region files were removed"):
                w346.restart_disk(good, fixture(), cut, cut["entity_files"] + ["entities/r.1.0.mca"], 2)


class Scripted:
    """A forced-stop Session stand-in that answers each command with scripted log lines."""

    def __init__(self, guard_refused: bool = False, waits: int = 2, held_lost: bool = False):
        self.args = SimpleNamespace(flight_timeout=1, settle_timeout=5, exit_timeout=1)
        self.process = SimpleNamespace(lines=[])
        self.guard_refused, self.waits, self.held_lost = guard_refused, waits, held_lost
        self.holds, self.ended = [], None

    def start(self, case, restart, properties):
        self.properties = properties
        return self.process

    def emit(self, line: str) -> str:
        self.process.lines.append(line + "\n")
        return line

    def send(self, command, marker, timeout=60.0):
        start = len(self.process.lines)
        self.reply(command)
        return next(line for line in self.process.lines[start:] if marker.search(line))

    def reply(self, command):
        prefix = "[Server thread/INFO] [x/]: "
        if command.startswith(w346.CONTROL + " hold"):
            level = command.rsplit(" ", 1)[1]
            if level in self.holds:
                self.emit(prefix + f"ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=hold level={level} reason=ALREADY_SAVE_OFF")
            else:
                self.holds.append(level); self.emit(prefix + f"ARCE_RELEASE_SAVE_BOUNDARY_HELD level={level} noSave=true")
        elif command.startswith(w346.CONTROL + " restore"):
            level = command.rsplit(" ", 1)[1]
            if level in self.holds:
                self.holds.remove(level); self.emit(prefix + f"ARCE_RELEASE_SAVE_BOUNDARY_RESTORED level={level} noSave=false")
            else:
                self.emit(prefix + f"ARCE_RELEASE_SAVE_BOUNDARY_REFUSED action=restore level={level} reason=NOT_HELD")
        elif command.startswith(w346.CONTROL + " autosave"):
            if self.waits:
                self.waits -= 1
                held = "false" if self.held_lost else "true"
                self.emit(prefix + f"ARCE_RELEASE_SAVE_BOUNDARY_WAIT logical={L} transfer={T} phase=SOURCE_REMOVED "
                                   f"acknowledged=false destination_held={held}")
            else:
                self.emit(prefix + autosave_line().split("[x] ", 1)[1])
        elif command == "forceload add 8 8":
            self.emit(prefix + "Marked chunk [0, 0] in minecraft:overworld to be force loaded")
        elif command.startswith("arce bay release-test fixture"):
            self.emit(prefix + f"ARCE_RELEASE_BAY_FIXTURE logical={L} transfer={T} rocket={SOURCE} data={DATA} solar={SOLAR} "
                               "launch=JOURNAL_PENDING packages=p0+p1")
            self.emit(prefix + f"ARCE_RELEASE_BAY_PAUSED cut=B16_GUARD op={OP}")
        elif command == "arce bay release-test release":
            self.emit(prefix + "ARCE_RELEASE_BAY_RELEASE_REQUESTED")
            if self.guard_refused:
                self.emit(prefix + f"ARCE_BAY_RETIRE_REFUSED transfer={T} logical={L} outcome=GUARD_REFUSED")
            self.emit(prefix + f"ARCE_BAY_CLAIM transfer={T} logical={L} state=READY_SOURCE reason=READY_SOURCE")
        else:
            raise AssertionError(command)

    def wait(self, marker, timeout):
        return next(line for line in self.process.lines if marker.search(line))

    def wait_after(self, marker, start, timeout):
        return next(line for line in self.process.lines[start:] if marker.search(line))

    def status(self, logical):
        paused = not any("READY_SOURCE" in line for line in self.process.lines)
        return status(claim="RETIRE", transfer="PREPARED", state="TRANSIT", rocket=SOURCE, packages="c0+p1") if paused \
            else status(state="DESCENT")

    def end(self, how, timeout):
        self.ended = how
        return None


class FirstStartTests(unittest.TestCase):
    def setUp(self):
        sleep = patch.object(w346.time, "sleep", lambda seconds: None)
        sleep.start()
        self.addCleanup(sleep.stop)

    def test_the_first_start_holds_after_the_guard_pause_and_kills_after_the_acknowledged_autosave(self):
        run = Scripted()
        found, observed = w346.first_start(run, CASE)
        self.assertEqual({"bayCheckpoint": "B16_GUARD"}, run.properties)
        self.assertEqual("kill", run.ended)
        self.assertEqual((L, T), (found["logical"], found["transfer"]))
        self.assertEqual(4, len(observed["control_check"]))
        self.assertEqual(2, observed["autosave_waits"])
        self.assertEqual(DEST, observed["autosave"]["destination_entity"])
        self.assertEqual([w346.MOON], run.holds, "the Moon must stay held through the kill")
        def index(text: str, last: bool = False) -> int:
            found = [number for number, line in enumerate(run.process.lines) if text in line]
            return found[-1] if last else found[0]
        # The flight's hold comes after D7's pause (D6 waits on a save-off Level) and before the guard's release.
        self.assertLess(index("ARCE_RELEASE_BAY_PAUSED"), index("ARCE_RELEASE_SAVE_BOUNDARY_HELD", last=True))
        self.assertLess(index("ARCE_RELEASE_SAVE_BOUNDARY_HELD", last=True), index("ARCE_RELEASE_BAY_RELEASE_REQUESTED"))
        self.assertLess(index("ARCE_RELEASE_BAY_RELEASE_REQUESTED"), index("ARCE_RELEASE_SAVE_BOUNDARY_AUTOSAVE"))

    def test_a_guard_refusal_after_the_hold_fails_the_case(self):
        with self.assertRaisesRegex(bay.smoke.SmokeError, "guard refused after the hold"):
            w346.first_start(Scripted(guard_refused=True), CASE)

    def test_a_lost_hold_fails_the_case_instead_of_saving(self):
        with self.assertRaisesRegex(bay.smoke.SmokeError, "no longer held"):
            w346.first_start(Scripted(held_lost=True), CASE)


class CommandLineTests(unittest.TestCase):
    def test_the_runner_requires_the_candidate_arguments_and_only_its_own_cases(self):
        common = ["server", "--jar", "j", "--session-dir", "s", "--evidence-dir", "e", "--source-commit", "c", "--java", "x",
                  "--lock-owner", "o"]
        for arguments in (["--jar-sha256", "0" * 64], ["--candidate-commit", "b" * 40],
                          ["--jar-sha256", "0" * 64, "--candidate-commit", "b" * 40, "--case", "W331_B6_DESTINATION_ABSENT"]):
            with self.subTest(arguments=arguments), patch.object(sys, "argv", ["runner", *common, *arguments]), patch("sys.stderr"):
                with self.assertRaises(SystemExit) as stopped:
                    w346.main()
                self.assertEqual(2, stopped.exception.code)

    def test_run_passes_its_case_to_the_forced_stop_session_and_defaults_to_its_tranche(self):
        args = SimpleNamespace(case=None, tranche=None)
        with patch.object(bay, "run", return_value={"status": "PASS"}) as forced_run:
            self.assertEqual({"status": "PASS"}, w346.run(args))
        self.assertEqual("W346_R", args.tranche)
        self.assertEqual({CASE: w346.run_w346_case}, forced_run.call_args.kwargs["extra_cases"])
        self.assertEqual(w346.W346_TRANCHES, forced_run.call_args.kwargs["extra_tranches"])

    def test_the_candidate_is_checked_before_any_session_or_java(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory) / "session"
            args = SimpleNamespace(source_commit="c", jar=Path(directory) / "j.jar", jar_sha256="0" * 64, candidate_commit="b" * 40,
                                   session_dir=session, evidence_dir=Path(directory) / "evidence", source_server=Path(directory),
                                   case=[CASE], tranche=None, lock_owner="o")
            with patch.object(bay, "assert_pin", lambda source: None), \
                    patch.object(evidence, "candidate_pin", side_effect=evidence.CandidateError("refused")):
                with self.assertRaisesRegex(bay.smoke.SmokeError, "refused"):
                    w346.run(args)
            self.assertFalse(session.exists() or args.evidence_dir.exists())

    def test_the_forced_stop_runner_dispatches_an_extra_case_only_by_name(self):
        calls = []
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "server" / "libraries").mkdir(parents=True)
            jar = root / "candidate.jar"
            jar.write_bytes(b"candidate")
            sha = evidence.file_sha256(jar)
            session_root = root / "Temp"
            session_root.mkdir()
            args = SimpleNamespace(source_commit="c", jar=jar, jar_sha256=sha, candidate_commit="b" * 40,
                                   source_server=root / "server", session_dir=session_root / "run", evidence_dir=root / "evidence",
                                   lock_owner="o", tranche="W346_R", case=None, java="java")

            def redirected(value):
                return session_root if value == "D:/GitHub/ARCE-Task-Evidence/Temp" else Path(value)

            with patch.object(bay, "assert_pin", lambda source: None), \
                    patch.object(evidence, "candidate_pin", return_value=dict(jar=str(jar), jar_sha256=sha)), \
                    patch.object(bay, "Path", side_effect=redirected), \
                    patch.object(bay.smoke, "allocate_port", return_value=25565), \
                    patch.object(bay.smoke, "write_server_configuration", lambda *a, **k: None), \
                    patch.object(bay, "run_case", side_effect=AssertionError("a W325 case ran")), \
                    patch.object(bay, "run_w331_case", side_effect=AssertionError("a W331 case ran")):
                report = bay.run(args, extra_cases={CASE: lambda session, case: calls.append(case) or dict(case=case, status="PASS")},
                                 extra_tranches=w346.W346_TRANCHES)
        self.assertEqual([CASE], calls)
        self.assertEqual("PASS", report["status"])


if __name__ == "__main__":
    unittest.main()
