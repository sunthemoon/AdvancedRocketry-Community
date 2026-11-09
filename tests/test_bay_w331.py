"""W331: the candidate pin of the bay matrix runners, and the raw readers and oracles of the W331 native cases."""
from pathlib import Path
import struct
import subprocess
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
import run_v180_bay_exit_cuts as exit_runner  # noqa: E402
import run_v180_bay_forced_stop as bay  # noqa: E402
import run_v180_bay_released_flight as released  # noqa: E402

L = "11111111-1111-4111-8111-111111111111"
DATA = "22222222-2222-4222-8222-222222222222"
SURVEY = "33333333-3333-4333-8333-333333333333"
SOLAR = "44444444-4444-4444-8444-444444444444"
OP = "55555555-5555-4555-8555-555555555555"
HEAD = subprocess.run(["git", "rev-parse", "HEAD"], cwd=evidence.REPOSITORY, capture_output=True, text=True).stdout.strip()


def jar(directory: Path, content: bytes = b"candidate") -> tuple[Path, str]:
    path = directory / "candidate.jar"
    path.write_bytes(content)
    return path, evidence.file_sha256(path)


def done(code: int, stdout: str = ""):
    return SimpleNamespace(returncode=code, stdout=stdout, stderr="")


class CandidatePinTests(unittest.TestCase):
    def test_a_matching_jar_and_production_source_pass_and_record_the_exact_diff(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            calls = []
            def fake(command, **kwargs):
                calls.append(command)
                return done(0, "a" * 40 + "\n") if command[1] == "rev-parse" else done(0)
            pin = evidence.candidate_pin(path, sha, "b" * 40, run=fake)
        diff = ["git", "diff", "--quiet", "b" * 40, "HEAD", "--", "src/main", "src/generated", "build.gradle",
                "settings.gradle", "gradle.properties"]
        self.assertEqual(diff, calls[0])
        self.assertEqual(dict(jar=str(path), jar_sha256=sha, candidate_commit="b" * 40, harness_commit="a" * 40,
                              production_diff=diff, production_diff_exit=0), pin)

    def test_a_different_jar_hash_refuses_before_git(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            calls = []
            with self.assertRaisesRegex(evidence.CandidateError, "differs from --jar-sha256"):
                evidence.candidate_pin(path, "0" * 64, "b" * 40, run=lambda command, **_: calls.append(command) or done(0))
            self.assertEqual([], calls)

    def test_a_production_difference_from_the_candidate_refuses(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            with self.assertRaisesRegex(evidence.CandidateError, "Production source differs .* exit 1"):
                evidence.candidate_pin(path, sha, "b" * 40, run=lambda command, **_: done(1))

    def test_malformed_arguments_and_a_missing_jar_refuse(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            for bad_sha, bad_commit in ((sha.upper(), "b" * 40), (sha[:-1], "b" * 40), (sha, "b" * 39), (sha, "HEAD")):
                with self.assertRaises(evidence.CandidateError):
                    evidence.candidate_pin(path, bad_sha, bad_commit, run=lambda command, **_: done(0, "a" * 40))
            with self.assertRaisesRegex(evidence.CandidateError, "missing"):
                evidence.candidate_pin(Path(directory) / "absent.jar", sha, "b" * 40, run=lambda command, **_: done(0, "a" * 40))

    def test_the_end_of_an_invocation_refuses_a_changed_jar(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            pin = dict(jar=str(path), jar_sha256=sha)
            evidence.recheck_candidate(pin)
            path.write_bytes(b"replaced")
            with self.assertRaises(evidence.CandidateError):
                evidence.recheck_candidate(pin)

    def test_the_command_line_records_one_invocation_against_the_real_repository(self):
        with tempfile.TemporaryDirectory() as directory:
            path, sha = jar(Path(directory))
            output = Path(directory) / "pin" / "invocation.json"
            arguments = ["pin", "--jar", str(path), "--jar-sha256", sha, "--candidate-commit", HEAD, "--output", str(output)]
            self.assertEqual(0, evidence.main(arguments))
            recorded = evidence.json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual((sha, HEAD, 0), (recorded["jar_sha256"], recorded["candidate_commit"], recorded["production_diff_exit"]))
            self.assertEqual(arguments, recorded["argv"])
            self.assertEqual(1, evidence.main(arguments), "an existing record is never overwritten")
            other = Path(directory) / "other.json"
            self.assertEqual(1, evidence.main(arguments[:4] + ["0" * 64] + arguments[5:7] + ["--output", str(other)]))
            self.assertFalse(other.exists(), "a refused check writes no record")

    def test_every_matrix_runner_requires_the_candidate_arguments(self):
        common = ["server", "--jar", "j", "--session-dir", "s", "--evidence-dir", "e", "--source-commit", "c", "--java", "x",
                  "--lock-owner", "o"]
        for module in (bay, released, exit_runner):
            for missing in ("--jar-sha256", "--candidate-commit"):
                extra = [flag for pair in (("--jar-sha256", "0" * 64), ("--candidate-commit", "b" * 40))
                         for flag in pair if pair[0] != missing]
                with patch.object(sys, "argv", ["runner", *common, *extra]), patch("sys.stderr"):
                    with self.assertRaises(SystemExit, msg=f"{module.__name__} without {missing}") as stopped:
                        module.main()
                self.assertEqual(2, stopped.exception.code)

    def test_every_matrix_runner_checks_the_candidate_before_any_session_or_java(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory) / "session"
            args = SimpleNamespace(source_commit="c", jar=Path(directory) / "j.jar", jar_sha256="0" * 64, candidate_commit="b" * 40,
                                   session_dir=session, evidence_dir=Path(directory) / "evidence", source_server=Path(directory),
                                   session_root=Path(directory), case=None, tranche=None, lock_owner="o")
            refused = evidence.CandidateError("refused")
            for module in (bay, released, exit_runner):
                with patch.object(bay, "assert_pin", lambda source: None), \
                        patch.object(evidence, "candidate_pin", side_effect=refused):
                    with self.assertRaisesRegex(bay.smoke.SmokeError, "refused", msg=module.__name__):
                        module.run(args)
                self.assertFalse(session.exists() or args.evidence_dir.exists(), module.__name__)


def gzip_root(path: Path, data: dict) -> Path:
    path.parent.mkdir(parents=True, exist_ok=True)
    nbt.write_gzip(path, b"", nbt.Tag(nbt.COMPOUND, dict(data=nbt.Tag(nbt.COMPOUND, data), DataVersion=nbt.Tag(nbt.INT, 3465))))
    return path


def receipt(op: str, rocket: str, satellite: str, phase: str, reason: str = "NONE", src: bytes = b"\x01" * 32) -> nbt.Tag:
    return nbt.Tag(nbt.COMPOUND, dict(op=nbt.uuid_tag(uuid.UUID(op)), rocket=nbt.uuid_tag(uuid.UUID(rocket)),
                                      satellite=nbt.uuid_tag(uuid.UUID(satellite)), phase=nbt.Tag(nbt.STRING, phase.encode()),
                                      reason=nbt.Tag(nbt.STRING, reason.encode()), src_before=nbt.Tag(nbt.BYTES, src),
                                      dst_before=nbt.Tag(nbt.BYTES, b"\x02" * 32), package_sig=nbt.Tag(nbt.BYTES, b"\x03" * 32),
                                      released=nbt.Tag(nbt.BYTE, 0)))


def world(directory: Path, receipts=(), satellites=(), journal_names=()) -> Path:
    data = directory / "data"
    gzip_root(data / bay.RECEIPTS, dict(receipts=nbt.Tag(nbt.LIST, (nbt.COMPOUND, list(receipts)))))
    gzip_root(data / bay.REGISTRY_FILE, dict(satellites=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [
        nbt.Tag(nbt.COMPOUND, dict(satellite_id=nbt.uuid_tag(uuid.UUID(value)))) for value in satellites]))))
    gzip_root(data / bay.JOURNAL_FILE, dict(transactions=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [
        nbt.Tag(nbt.COMPOUND, dict(transaction_id=nbt.uuid_tag(uuid.UUID(value)))) for value in journal_names]))))
    return directory


class RawReaderTests(unittest.TestCase):
    def test_receipts_registry_and_uuid_reads(self):
        with tempfile.TemporaryDirectory() as directory:
            root = world(Path(directory), [receipt(OP, L, DATA, "INTENT")], [DATA, SURVEY], [L])
            rows = evidence.bay_receipts(root / "data" / bay.RECEIPTS)
            self.assertEqual([dict(op=OP, rocket=L, satellite=DATA, phase="INTENT", reason="NONE", src_before="01" * 32,
                                   dst_before="02" * 32, package_sig="03" * 32, released=False)], rows)
            self.assertEqual([DATA, SURVEY], evidence.registry_satellites(root / "data" / bay.REGISTRY_FILE))
            self.assertTrue(evidence.raw_names(root / "data" / bay.JOURNAL_FILE, L))
            self.assertFalse(evidence.raw_names(root / "data" / bay.JOURNAL_FILE, DATA))
            self.assertEqual([], evidence.bay_receipts(root / "data" / "absent.dat"))
            self.assertFalse(evidence.raw_names(root / "data" / "absent.dat", L))

    def test_the_journal_injection_allowance_is_off_by_default_and_exact(self):
        line = "[Server thread/ERROR] [x/]: ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=4 acknowledged=2"
        other = "[Server thread/ERROR] [x/]: ARCE_TRANSACTION_JOURNAL_UNACKNOWLEDGED outcome=UNKNOWN sequence=4 acknowledged=2 extra"
        self.assertEqual([line], evidence.unexpected_errors([line]))
        self.assertEqual([], evidence.unexpected_errors([line], journal_injection=True))
        self.assertEqual([other], evidence.unexpected_errors([other], journal_injection=True))

    def test_entities_naming_reads_every_stored_moon_chunk(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            folder = root / bay.MOON_ENTITIES
            folder.mkdir(parents=True)
            entity = nbt.Tag(nbt.COMPOUND, dict(UUID=nbt.uuid_tag(uuid.UUID(OP)), RocketEntityData=nbt.Tag(nbt.COMPOUND, dict(
                assembly_transaction_id=nbt.uuid_tag(uuid.UUID(L))))))
            chunk = nbt.Tag(nbt.COMPOUND, dict(Position=nbt.Tag(nbt.INTS, [3, -2]), Entities=nbt.Tag(nbt.LIST, (nbt.COMPOUND, [entity]))))
            self.assertEqual([], bay.entities_naming(root, L))
            encoded = zlib.compress(nbt.write(b"", chunk))
            record = struct.pack(">IB", len(encoded) + 1, 2) + encoded
            region = bytearray(2 * nbt.SECTOR + -(-len(record) // nbt.SECTOR) * nbt.SECTOR)
            struct.pack_into(">I", region, ((3 & 31) + (-2 & 31) * 32) * 4, 2 << 8 | -(-len(record) // nbt.SECTOR))
            region[2 * nbt.SECTOR:2 * nbt.SECTOR + len(record)] = record
            (folder / "r.0.-1.mca").write_bytes(bytes(region))
            self.assertEqual([OP], bay.entities_naming(root, L))
            self.assertEqual([], bay.entities_naming(root, DATA))


def fixture(kind: str = "trio") -> dict:
    roles = bay.ROLES[kind]
    satellites = [DATA, SURVEY, SOLAR][:len(roles)] if kind == "trio" else [DATA, SURVEY if "survey" in kind else SOLAR]
    return dict(logical=L, transfer=OP, data=DATA, solar=satellites[1], packages="+".join(f"p{i}" for i in range(len(roles))),
                roles=roles, satellites=satellites)


def status(**changes) -> dict:
    value = dict(claim="NONE", hold="NONE", reason="NONE", receipts=[], transfer="COMMITTED", rocket=OP, state="LANDED", y="50",
                 packages="c0+c1+p2", locked="false", consumed=[OP, SURVEY, None], owned=2)
    value.update(changes)
    return value


class OracleTests(unittest.TestCase):
    def test_the_w331_cases_stay_outside_the_pinned_tables_and_cover_the_design_rows(self):
        self.assertFalse(set(bay.W331_CASES) & set(bay.CASES))
        listed = [case for cases in bay.W331_TRANCHES.values() for case in cases]
        self.assertEqual(sorted(bay.W331_CASES), sorted(listed))
        self.assertFalse(set(bay.W331_TRANCHES) & set(bay.TRANCHES))
        rows = {row for spec in bay.W331_CASES.values() for row in spec["rows"]}
        self.assertEqual({"B4.3", "B4.6", "B4.7", "B4.8", "B4.9", "B4.10", "B6.5", "B6.8", "B7.3", "B8.7", "B8.8", "B11.7", "B14.3",
                          "B14.4", "B15.3", "B15.4", "B15.5", "B17.4", "B18.6", "B18.7"}, rows)
        cuts = {"B1_MOVED", "B3_STAGED", "B3_MOVED", "B6_D5", "B8_STAGED", "B8_MOVED", "B16_GUARD", "B18_INTENT", "B15_D1"}
        for case, spec in bay.W331_CASES.items():
            self.assertIn(spec["fixture"], bay.ROLES, case)
            self.assertTrue(spec.get("cut") is None or spec["cut"] in cuts, case)
            self.assertIn(spec["end"], ("kill", "stop", "land", "land-kill"), case)
            self.assertEqual(spec["end"] in ("land", "land-kill"), "first" in spec, case)
            for name in spec["restarts"] + ((spec["first"],) if "first" in spec else ()):
                self.assertIn(name, bay.OUTCOMES, case)
            if spec.get("predecommission") or spec.get("copies"):
                self.assertIn(spec["fixture"], ("survey", "online_survey"), case)
            if spec.get("reject"):
                self.assertTrue(spec["end"] == "land" and spec["first"] == "LANDED_UNCONSUMED", case)

    def test_a_trio_landing_needs_both_registering_bays_consumed_and_the_solar_bay_untouched(self):
        spec, trio = bay.W331_CASES["W331_B8_TRIO_STAGED"], fixture()
        good = status()
        self.assertTrue(bay.w331_settled(spec, "LANDED", good, trio))
        bay.w331_check("W331_B8_TRIO_STAGED", spec, "LANDED", good, trio)
        for bad in (status(packages="c0+c1+c2"), status(consumed=[OP, SURVEY, OP]), status(packages="c0+p1+p2"),
                    status(consumed=[OP, None, None]), status(owned=1),
                    status(receipts=[dict(op=OP, satellite=SOLAR, phase="REGISTERED", reason="NONE", acknowledged=True)]),
                    status(receipts=[dict(op=OP, satellite=DATA.replace("2", "9"), phase="INTENT", reason="NONE", acknowledged=True)])):
            with self.assertRaises(bay.smoke.SmokeError, msg=str(bad)):
                bay.w331_check("W331_B8_TRIO_STAGED", spec, "LANDED", bad, trio)

    def test_a_partial_d1_restart_keeps_only_the_first_bays_interrupted_receipt(self):
        spec, trio = bay.W331_CASES["W331_B17_PARTIAL_D1"], fixture()
        interrupted = dict(op=OP, satellite=DATA, phase="REJECTED", reason="INTERRUPTED", acknowledged=True, registered=False)
        good = status(receipts=[interrupted], state="FUELED", transfer="NONE", y="2", packages="p0+p1+p2", consumed=[None] * 3,
                      owned=0)
        self.assertTrue(bay.w331_settled(spec, "INTERRUPTED", good, trio))
        bay.w331_check("W331_B17_PARTIAL_D1", spec, "INTERRUPTED", good, trio)
        survey = dict(interrupted, op=SOLAR, satellite=SURVEY)
        with self.assertRaises(bay.smoke.SmokeError):
            bay.w331_check("W331_B17_PARTIAL_D1", spec, "INTERRUPTED", dict(good, receipts=[interrupted, survey]), trio)
        self.assertFalse(bay.w331_settled(spec, "INTERRUPTED", dict(good, claim="RESUME"), trio))

    def test_the_raw_oracles_refuse_each_violated_disk_fact(self):
        trio, standard = fixture(), fixture("standard")
        with tempfile.TemporaryDirectory() as directory:
            root = world(Path(directory) / "a", [receipt(OP, L, DATA, "INTENT")], [DATA])
            cut = bay.w331_disk("W331_B17_PARTIAL_D1", root, trio, 0)
            world(Path(directory) / "a", [receipt(OP, L, DATA, "REJECTED", "INTERRUPTED")], [])
            bay.w331_disk("W331_B17_PARTIAL_D1", root, trio, 1, cut)
            world(Path(directory) / "a", [receipt(OP, L, DATA, "REJECTED", "INTERRUPTED", src=b"\x09" * 32)], [])
            with self.assertRaisesRegex(bay.smoke.SmokeError, "src_before changed"):
                bay.w331_disk("W331_B17_PARTIAL_D1", root, trio, 1, cut)
            survey = fixture("survey")
            with self.assertRaisesRegex(bay.smoke.SmokeError, "decommissioned X"):
                bay.w331_disk("W331_B4_GONE_X_S_ABSENT", world(Path(directory) / "b", [], [DATA, SURVEY]), survey, 1)
            bay.w331_disk("W331_B4_GONE_X_S_ABSENT", world(Path(directory) / "c", [], [DATA]), survey, 1)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "stale assembly"):
                bay.w331_disk("W331_B14_STALE", world(Path(directory) / "d", [], [], [OP]), standard, 1)
            bay.w331_disk("W331_B14_STALE", world(Path(directory) / "e", [], [], [L]), standard, 1)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "drain both"):
                bay.w331_disk("W331_B18_SUCCESSOR", world(Path(directory) / "f", [receipt(OP, L, DATA, "INTENT")], []), standard, 0)
            bay.w331_disk("W331_B18_SUCCESSOR", world(Path(directory) / "g", [receipt(OP, L, DATA, "INTENT"),
                                                                             receipt(SOLAR, SURVEY, SOLAR, "INTENT")], []), standard, 0)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "exactly once"):
                bay.w331_disk("W331_B15_REGISTRY", world(Path(directory) / "h", [], []), standard, 0)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "D0 rejection"):
                bay.w331_disk("W331_B14_FAILED", world(Path(directory) / "i", [receipt(OP, L, DATA, "INTENT")], []), standard, 0)
            with self.assertRaisesRegex(bay.smoke.SmokeError, "2 REGISTERED"):
                bay.w331_disk("W331_B8_TRIO_STAGED", world(Path(directory) / "j", [receipt(OP, L, DATA, "REGISTERED")], []), trio, 0)

    def test_the_union_and_successor_counts_are_exact(self):
        self.assertEqual((2, 2), bay.W331_CASES["W331_B11_UNION"]["union"])
        self.assertEqual((0, 0), bay.W331_CASES["W331_B11_UNION"]["pending"])
        self.assertEqual((1, 2), bay.W331_CASES["W331_B18_SUCCESSOR"]["pending"])

    def test_exit_fields_refuse_a_repeated_field(self):
        line = f"[x] ARCE_RELEASE_BAY_EXIT_LOGIN logical={L} tick=4 rider=online mounted=false seat=0"
        self.assertEqual(dict(tick="4", rider="online", mounted="false", seat="0"), bay.exit_fields(line))
        with self.assertRaises(bay.smoke.SmokeError):
            bay.exit_fields(line + " seat=1")


if __name__ == "__main__":
    unittest.main()
