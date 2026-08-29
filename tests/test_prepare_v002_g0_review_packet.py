import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path, PurePosixPath
from unittest.mock import patch

import scripts.prepare_v002_g0_review_packet as packet_module
import scripts.validate_bootstrap_provenance as validator_module
from scripts.prepare_v002_g0_review_packet import (
    GENERATOR_PATH,
    MANIFEST_NAME,
    PROVENANCE_MANIFEST,
    PROVENANCE_RECORD,
    QUESTION_DEFINITIONS,
    THIRD_PARTY_NOTICE,
    TOOL_DEFINITIONS,
    VALIDATOR_PATH,
    PacketError,
    _canonical_json,
    generate_packet,
    main,
    resolve_commit,
    verify_packet,
)
from scripts.validate_bootstrap_provenance import compute_review_content_sha256


class V002G0ReviewPacketTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source_root = Path(__file__).resolve().parents[1]
        cls.class_temporary = tempfile.TemporaryDirectory()
        cls.seed_root = Path(cls.class_temporary.name) / "seed"
        cls.run_command(
            [
                "git",
                "clone",
                "--quiet",
                "--shared",
                "--",
                str(cls.source_root),
                str(cls.seed_root),
            ]
        )
        cls.seed_git("config", "user.name", "G0 Packet Fixture")
        cls.seed_git("config", "user.email", "g0-packet@example.invalid")
        cls.seed_git("config", "core.autocrlf", "false")
        cls.seed_git("config", "core.filemode", "false")

        for _, relative in TOOL_DEFINITIONS:
            source = cls.source_root / relative
            destination = cls.seed_root / relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes(source.read_bytes())
        cls.seed_git("add", "--", *(path for _, path in TOOL_DEFINITIONS))
        # In CI the checked-out HEAD already contains these exact tool bytes;
        # keep a dedicated fixture tip even when copying them creates no diff.
        cls.seed_git(
            "commit",
            "--quiet",
            "--allow-empty",
            "-m",
            "add bound G0 review tools",
        )
        cls.seed_commit = cls.seed_git("rev-parse", "HEAD")

        build = cls.seed_root / "build"
        build.mkdir(exist_ok=True)
        cls.base_packet = build / "base-packet"
        cls.base_manifest = generate_packet(
            cls.seed_root, cls.seed_commit, cls.base_packet
        )
        mechanical = cls.base_manifest["mechanical_validation"]
        binding = cls.base_manifest["review_content_binding"]
        cls.base_validation = {
            "components": mechanical["components"],
            "targets": mechanical["targets"],
            "local_assets": mechanical["local_assets"],
            "review_status": mechanical["observed_review_status"],
            "review_content_sha256": binding["value"],
        }

    @classmethod
    def tearDownClass(cls) -> None:
        cls.class_temporary.cleanup()

    @staticmethod
    def run_command(
        arguments: list[str],
        *,
        cwd: Path | None = None,
        check: bool = True,
    ) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            arguments,
            cwd=cwd,
            check=check,
            capture_output=True,
            text=True,
            timeout=180,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )

    @classmethod
    def seed_git(cls, *arguments: str) -> str:
        return cls.run_command(
            ["git", "-C", str(cls.seed_root), *arguments]
        ).stdout.strip()

    def setUp(self) -> None:
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name) / "repository"
        self.run_command(
            [
                "git",
                "clone",
                "--quiet",
                "--shared",
                "--",
                str(self.seed_root),
                str(self.root),
            ]
        )
        self.git("config", "user.name", "G0 Packet Test")
        self.git("config", "user.email", "g0-packet-test@example.invalid")
        self.git("config", "core.filemode", "false")
        self.build = self.root / "build"
        self.build.mkdir()
        self.commit = self.git("rev-parse", "HEAD")
        self.packet = self.build / "packet"
        shutil.copytree(self.base_packet, self.packet)

    def git(self, *arguments: str) -> str:
        return self.run_command(
            ["git", "-C", str(self.root), *arguments]
        ).stdout.strip()

    def git_with_input(self, content: bytes, *arguments: str) -> str:
        result = subprocess.run(
            ["git", "-C", str(self.root), *arguments],
            check=True,
            input=content,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            timeout=180,
            env={**os.environ, "PYTHONDONTWRITEBYTECODE": "1"},
        )
        return result.stdout.decode("ascii", errors="strict").strip()

    def commit_all(self, message: str) -> str:
        self.git("add", "--all")
        self.git("commit", "--quiet", "-m", message)
        return self.git("rev-parse", "HEAD")

    def generate(self, name: str = "generated") -> tuple[Path, dict[str, object]]:
        destination = self.build / name
        manifest = generate_packet(self.root, self.commit, destination)
        return destination, manifest

    def verify_fast(
        self, packet: Path | None = None, commit: str | None = None
    ) -> list[str]:
        with patch.object(
            packet_module,
            "_run_selected_commit_validation",
            return_value=dict(self.base_validation),
        ):
            return verify_packet(
                self.root,
                commit or self.commit,
                packet or self.packet,
            )

    @staticmethod
    def snapshot(directory: Path) -> dict[str, bytes]:
        return {
            path.relative_to(directory).as_posix(): path.read_bytes()
            for path in sorted(directory.rglob("*"))
            if path.is_file()
        }

    @staticmethod
    def load_manifest(packet: Path) -> dict[str, object]:
        return json.loads((packet / MANIFEST_NAME).read_text(encoding="utf-8"))

    @staticmethod
    def write_manifest(packet: Path, document: dict[str, object]) -> None:
        (packet / MANIFEST_NAME).write_bytes(_canonical_json(document))

    def write_source_manifest(self, document: dict[str, object]) -> None:
        (self.root / PROVENANCE_MANIFEST).write_bytes(_canonical_json(document))

    def approve_fixture(self) -> str:
        manifest_path = self.root / PROVENANCE_MANIFEST
        document = json.loads(manifest_path.read_text(encoding="utf-8"))
        reviewer = "fixture-license-reviewer"
        reviewed_at = "2026-08-30"
        document["review"] = {
            "record_status": "THIRD_PARTY_APPROVED",
            "reviewer": reviewer,
            "reviewed_at": reviewed_at,
            "final_status_after_review": "THIRD_PARTY_APPROVED",
            "reviewed_audited_target_commit": document["audited_target_commit"],
            "reviewed_content_sha256": None,
        }
        for target in document["targets"]:
            target["status"] = "THIRD_PARTY_APPROVED"
            target["proposed_status_after_review"] = None

        record_path = self.root / PROVENANCE_RECORD
        notice_path = self.root / THIRD_PARTY_NOTICE
        record = record_path.read_text(encoding="utf-8")
        notice = notice_path.read_text(encoding="utf-8")
        for text_name, value in (
            ("record_status", "THIRD_PARTY_APPROVED"),
            ("reviewer", reviewer),
            ("reviewed_at", reviewed_at),
            ("final_status_after_review", "THIRD_PARTY_APPROVED"),
            ("reviewed_audited_target_commit", document["audited_target_commit"]),
        ):
            record = re.sub(
                rf"^{re.escape(text_name)}:\s*.*$",
                f"{text_name}: {value}",
                record,
                flags=re.MULTILINE,
            )
        record = re.sub(
            r"^reviewed_content_sha256:\s*.*$",
            "reviewed_content_sha256: null",
            record,
            flags=re.MULTILINE,
        )
        record = record.replace(
            "PENDING_HUMAN_REVIEW", "THIRD_PARTY_APPROVED"
        ).replace(
            "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING", "THIRD_PARTY_APPROVED"
        ).replace(
            "proposed_status_after_review: THIRD_PARTY_APPROVED",
            "proposed_status_after_review: null",
        )
        record = re.sub(r"\bpending\b", "completed", record, flags=re.IGNORECASE)
        record = re.sub(
            r"does\s+not\s+assign\s+`?THIRD_PARTY_APPROVED`?",
            "records THIRD_PARTY_APPROVED",
            record,
            flags=re.IGNORECASE,
        )
        record = re.sub(
            r"does\s+not\s+claim\s+human\s+license\s+approval",
            "records completed human license review",
            record,
            flags=re.IGNORECASE,
        )
        record = re.sub(
            r"human\s+review\s+must\s+resolve",
            "human review resolved",
            record,
            flags=re.IGNORECASE,
        )
        record = re.sub(
            r"\bunresolved\b", "resolved", record, flags=re.IGNORECASE
        ).replace("- [ ]", "- [x]")

        notice = notice.replace(
            "PENDING_HUMAN_REVIEW", "THIRD_PARTY_APPROVED"
        ).replace(
            "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING", "THIRD_PARTY_APPROVED"
        )
        notice = re.sub(
            r"^reviewer:\s*.*$", f"reviewer: {reviewer}", notice, flags=re.MULTILINE
        )
        notice = re.sub(
            r"^reviewed_at:\s*.*$",
            f"reviewed_at: {reviewed_at}",
            notice,
            flags=re.MULTILINE,
        )
        notice = re.sub(r"\bpending\b", "completed", notice, flags=re.IGNORECASE)
        notice = re.sub(
            r"does\s+not\s+claim\s+human\s+license\s+approval",
            "records completed human license review",
            notice,
            flags=re.IGNORECASE,
        ).replace("- [ ]", "- [x]")

        record_bytes = record.encode("utf-8")
        notice_bytes = notice.encode("utf-8")
        digest = compute_review_content_sha256(
            document, record_bytes, notice_bytes
        )
        document["review"]["reviewed_content_sha256"] = digest
        record = record.replace(
            "reviewed_content_sha256: null",
            f"reviewed_content_sha256: {digest}",
            1,
        )
        manifest_path.write_bytes(_canonical_json(document))
        record_path.write_text(record, encoding="utf-8", newline="\n")
        notice_path.write_text(notice, encoding="utf-8", newline="\n")
        return self.commit_all("valid approved provenance fixture")

    def test_pending_packet_is_deterministic_and_fully_commit_bound(self) -> None:
        generated, manifest = self.generate()

        self.assertEqual(self.snapshot(self.base_packet), self.snapshot(generated))
        self.assertEqual(2, manifest["schema_version"])
        self.assertEqual(self.commit, manifest["source_commit"])
        self.assertEqual(
            "COMPLETE_SCHEMA3_PROVENANCE_SELECTED_COMMIT",
            manifest["mechanical_validation"]["scope"],
        )
        self.assertEqual("PASS", manifest["mechanical_validation"]["status"])
        self.assertEqual("NONE", manifest["mechanical_validation"]["human_approval_effect"])
        self.assertEqual(
            "PENDING_CONTENT_DIAGNOSTIC_ONLY",
            manifest["review_content_binding"]["classification"],
        )
        self.assertIn("must never be copied", manifest["review_content_binding"]["statement"])
        self.assertIsNone(
            manifest["review_content_binding"]["recorded_in_authoritative_review"]
        )

        tools = manifest["tool_identity"]["tools"]
        self.assertEqual(
            [(role, path) for role, path in TOOL_DEFINITIONS],
            [(item["role"], item["repository_path"]) for item in tools],
        )
        for item in tools:
            self.assertEqual(self.commit, item["tool_commit"])
            self.assertRegex(item["git_blob_oid"], r"^[0-9a-f]{40}$")
            self.assertRegex(item["raw_sha256"], r"^[0-9a-f]{64}$")
            self.assertGreater(item["size"], 0)

        section = manifest["question_section"]
        self.assertEqual(
            "V002_G0_PROVENANCE_HUMAN_DECISIONS_V1", section["id"]
        )
        self.assertEqual("PACKET_SCHEMA", section["id_owner"])
        self.assertEqual(4, section["authoritative_source_question_count"])
        self.assertEqual("PENDING_HUMAN_DECISION", section["workflow_state"])
        self.assertEqual(
            [item["id"] for item in QUESTION_DEFINITIONS],
            [item["id"] for item in section["questions"]],
        )
        for question in section["questions"]:
            self.assertNotIn("answer", question)
            self.assertNotIn("reviewer", question)
            self.assertNotIn("reviewed_at", question)
            self.assertEqual(section["id"], question["packet_question_section_id"])
            self.assertRegex(question["source_question_sha256"], r"^[0-9a-f]{64}$")

        for entry in manifest["files"]:
            content = (generated / entry["packet_path"]).read_bytes()
            self.assertEqual(len(content), entry["size"])
            self.assertEqual(hashlib.sha256(content).hexdigest(), entry["raw_sha256"])
            self.assertEqual(
                self.git("rev-parse", f"{self.commit}:{entry['repository_path']}"),
                entry["git_blob_oid"],
            )
        self.assertEqual([], verify_packet(self.root, self.commit, generated))

    def test_explicit_commit_uses_git_objects_despite_dirty_checkout(self) -> None:
        dirty = b"mutable worktree bytes must not enter the packet\n"
        (self.root / "README.md").write_bytes(dirty)
        generated, _ = self.generate()
        committed = packet_module._git_blob(
            self.root, self.commit, "README.md"
        ).content

        self.assertEqual(committed, (generated / "files/README.md").read_bytes())
        self.assertNotEqual(dirty, (generated / "files/README.md").read_bytes())
        self.assertEqual([], verify_packet(self.root, self.commit, generated))
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            generate_packet(self.root, "HEAD", self.build / "dirty-head")

    def test_full_validator_rejects_nonexistent_history_and_wrong_source_hash(self) -> None:
        document = json.loads((self.root / PROVENANCE_MANIFEST).read_text())
        missing = "0" * 40
        document["import_commit"] = missing
        self.write_source_manifest(document)
        record = self.root / PROVENANCE_RECORD
        record.write_text(
            re.sub(
                r"^import_commit:\s*.*$",
                f"import_commit: {missing}",
                record.read_text(encoding="utf-8"),
                flags=re.MULTILINE,
            ),
            encoding="utf-8",
            newline="\n",
        )
        invalid_history = self.commit_all("invalid missing history")
        with self.assertRaisesRegex(PacketError, "does not exist as a local Git commit"):
            generate_packet(self.root, invalid_history, self.build / "missing-history")

        self.git("reset", "--hard", self.commit)
        document = json.loads((self.root / PROVENANCE_MANIFEST).read_text())
        document["components"][0]["source_sha256"] = "0" * 64
        self.write_source_manifest(document)
        wrong_hash = self.commit_all("invalid source hash")
        with self.assertRaisesRegex(PacketError, "source_sha256 must be"):
            generate_packet(self.root, wrong_hash, self.build / "wrong-source-hash")

    def test_approved_packet_only_observes_valid_recorded_binding(self) -> None:
        approved_commit = self.approve_fixture()
        self.commit = approved_commit
        approved_packet = self.build / "approved"
        manifest = generate_packet(self.root, approved_commit, approved_packet)

        binding = manifest["review_content_binding"]
        self.assertEqual(
            "VALID_RECORDED_APPROVAL_BINDING_OBSERVED_ONLY",
            binding["classification"],
        )
        self.assertEqual(binding["value"], binding["recorded_in_authoritative_review"])
        self.assertIn("already recorded", binding["statement"])
        self.assertEqual(
            "VALID_APPROVED_RECORD_OBSERVATION_ONLY",
            manifest["question_section"]["workflow_state"],
        )
        self.assertTrue(
            all(
                question["workflow_state"]
                == "VALID_APPROVED_RECORD_OBSERVATION_ONLY"
                for question in manifest["question_section"]["questions"]
            )
        )
        self.assertEqual([], verify_packet(self.root, approved_commit, approved_packet))

    def test_selected_commit_must_contain_exact_runtime_tools(self) -> None:
        self.git("rm", "--quiet", "--", GENERATOR_PATH)
        missing_tool = self.git("commit", "--quiet", "-m", "remove packet tool")
        missing_commit = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(PacketError, "exactly one Git entry"):
            generate_packet(self.root, missing_commit, self.build / "missing-tool")

        self.git("reset", "--hard", self.commit)
        validator = self.root / VALIDATOR_PATH
        validator.write_bytes(validator.read_bytes() + b"\n# unbound mutation\n")
        mismatched = self.commit_all("mutate selected validator")
        with self.assertRaisesRegex(
            PacketError, "runtime schema3_provenance_validator (?:bytes|size)"
        ):
            generate_packet(self.root, mismatched, self.build / "mismatched-tool")

    def test_bound_validator_bytes_ignore_preloaded_canonical_module(self) -> None:
        with patch.object(
            validator_module,
            "validate_bootstrap_provenance_at_commit",
            side_effect=AssertionError("preloaded validator must not execute"),
        ):
            generated = generate_packet(
                self.root, self.commit, self.build / "bound-validator"
            )

        self.assertEqual(self.commit, generated["source_commit"])

    def test_bound_validator_selectors_must_match_packet_inputs(self) -> None:
        binding = packet_module._git_blob(
            self.root, self.commit, VALIDATOR_PATH
        )
        cases = (
            (
                b'DEFAULT_MANIFEST = Path("docs/provenance/v0.0.2-bootstrap-inputs.json")',
                b'DEFAULT_MANIFEST = Path("docs/provenance/alternate.json")',
                "DEFAULT_MANIFEST",
            ),
            (
                b'EXPECTED_RECORD_PATH = "docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md"',
                b'EXPECTED_RECORD_PATH = "docs/provenance/alternate.md"',
                "EXPECTED_RECORD_PATH",
            ),
            (
                b'EXPECTED_NOTICE_PATH = "THIRD-PARTY-NOTICES.md"',
                b'EXPECTED_NOTICE_PATH = "ALTERNATE-NOTICE.md"',
                "EXPECTED_NOTICE_PATH",
            ),
        )
        for needle, replacement, expected_error in cases:
            with self.subTest(selector=expected_error):
                self.assertIn(needle, binding.content)
                changed_content = binding.content.replace(needle, replacement, 1)
                changed_binding = packet_module.GitBlob(
                    binding.mode,
                    binding.object_type,
                    binding.oid,
                    changed_content,
                )
                with self.assertRaisesRegex(PacketError, expected_error):
                    packet_module._run_selected_commit_validation(
                        self.root,
                        self.commit,
                        {VALIDATOR_PATH: changed_binding},
                    )

    def test_generator_identity_rejects_stale_preloaded_bytecode(self) -> None:
        stale_code = compile(
            "stale_loaded_generator = True\n",
            packet_module._LOADED_GENERATOR_MODULE_CODE.co_filename,
            "exec",
            dont_inherit=True,
            optimize=sys.flags.optimize,
        )
        with (
            patch.object(
                packet_module, "_LOADED_GENERATOR_MODULE_CODE", stale_code
            ),
            self.assertRaisesRegex(
                PacketError, "executing packet generator bytecode does not match"
            ),
        ):
            generate_packet(
                self.root, self.commit, self.build / "stale-loaded-generator"
            )

    def test_runtime_tool_reads_are_size_bounded(self) -> None:
        runtime = self.build / "oversized-runtime-tool.py"
        runtime.write_bytes(b"ab")

        with self.assertRaisesRegex(PacketError, "exceeds 1 bytes"):
            packet_module._read_bounded_regular_file(
                runtime,
                "runtime packet generator",
                maximum_size=1,
                expected_size=2,
            )

    def test_runtime_dependency_identity_is_fail_closed(self) -> None:
        with (
            patch.dict(sys.modules, {"json": object()}),
            self.assertRaisesRegex(PacketError, "runtime dependency identity changed"),
        ):
            packet_module._validate_runtime_dependency_origins(self.root)

        original_code = packet_module.json.loads.__code__
        try:
            packet_module.json.loads.__code__ = (lambda: None).__code__
            with self.assertRaisesRegex(
                PacketError, "runtime dependency callable changed"
            ):
                packet_module._validate_runtime_dependency_origins(self.root)
        finally:
            packet_module.json.loads.__code__ = original_code

    def test_packet_rejects_stale_question_number_bindings(self) -> None:
        record_path = self.root / PROVENANCE_RECORD
        record = record_path.read_text(encoding="utf-8")
        record, replacements = re.subn(
            r"^4\.", "5.", record, count=1, flags=re.MULTILINE
        )
        self.assertEqual(1, replacements)
        record_path.write_text(record, encoding="utf-8", newline="\n")
        changed = self.commit_all("change provenance decision numbering")

        with self.assertRaisesRegex(PacketError, "source numbers 1 through 4"):
            generate_packet(self.root, changed, self.build / "stale-questions")

    def test_question_parser_rejects_unbounded_or_duplicate_structure(self) -> None:
        heading = "## Existing notices and fixture human decisions\n\n"
        long_number = "9" * 10_000
        with self.assertRaisesRegex(PacketError, "source numbers 1 through 4"):
            packet_module._source_decision_bindings(
                heading + f"{long_number}. invalid\n"
            )

        with self.assertRaisesRegex(PacketError, "exactly one"):
            packet_module._source_decision_bindings(
                heading + "1. one\n" + heading + "1. duplicate section\n"
            )

        yaml_blocks = "```yaml\nstatus: value\n```\n" * 2
        with (
            patch.object(packet_module, "MAX_MARKDOWN_YAML_FENCES", 1),
            self.assertRaisesRegex(PacketError, "exceeds 1 YAML metadata blocks"),
        ):
            packet_module._markdown_scalar_occurrences(yaml_blocks, "status")

    def test_verify_rejects_payload_manifest_inventory_and_commit_attacks(self) -> None:
        payload = self.packet / "files/README.md"
        replacement = b"attacker replacement\n"
        payload.write_bytes(replacement)
        document = self.load_manifest(self.packet)
        entry = next(
            item for item in document["files"] if item["packet_path"] == "files/README.md"
        )
        entry["size"] = len(replacement)
        entry["raw_sha256"] = hashlib.sha256(replacement).hexdigest()
        self.write_manifest(self.packet, document)
        errors = self.verify_fast()
        self.assertTrue(any("manifest differs" in error for error in errors), errors)
        self.assertTrue(
            any(
                "differs from selected Git blob" in error or "size is" in error
                for error in errors
            ),
            errors,
        )

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        (self.packet / "files/LICENSE").unlink()
        (self.packet / "unexpected.txt").write_text("extra\n", encoding="utf-8")
        (self.packet / "unexpected-directory").mkdir()
        errors = self.verify_fast()
        self.assertTrue(any("missing files" in error for error in errors), errors)
        self.assertTrue(any("unexpected files" in error for error in errors), errors)
        self.assertTrue(any("unexpected directories" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        changed_readme = self.root / "README.md"
        changed_readme.write_text("# Other selected commit\n", encoding="utf-8")
        other_commit = self.commit_all("other selected input")
        errors = self.verify_fast(packet=self.packet, commit=other_commit)
        self.assertTrue(any("packet commit binding" in error for error in errors), errors)

    def test_verify_rejects_unsafe_paths_links_and_bounded_traversal(self) -> None:
        document = self.load_manifest(self.packet)
        document["files"][0]["packet_path"] = "../escape"
        self.write_manifest(self.packet, document)
        errors = self.verify_fast()
        self.assertTrue(any("packet_path is unsafe" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        outside = self.build / "outside.txt"
        outside.write_text("outside\n", encoding="utf-8")
        link = self.packet / "unsafe-link"
        try:
            link.symlink_to(outside)
        except OSError:
            pass
        else:
            errors = self.verify_fast()
            self.assertTrue(
                any("symlink, junction" in error for error in errors),
                errors,
            )
            link.unlink()

        expected_directories = {
            path.parent.relative_to(self.packet).as_posix()
            for path in self.packet.rglob("*")
            if path.is_file() and path.parent != self.packet
        }
        (self.packet / "bounded-extra-directory").mkdir()
        with patch.object(
            packet_module, "MAX_PACKET_DIRECTORIES", len(expected_directories)
        ):
            errors = self.verify_fast()
        self.assertTrue(any("exceeds" in error and "directories" in error for error in errors), errors)

        aggregate = sum(
            path.stat().st_size for path in self.packet.rglob("*") if path.is_file()
        )
        (self.packet / "aggregate-extra.bin").write_bytes(b"x")
        with patch.object(packet_module, "MAX_OBSERVED_PACKET_BYTES", aggregate):
            errors = self.verify_fast()
        self.assertTrue(any("aggregate size exceeds" in error for error in errors), errors)

    def test_verify_rejects_deep_json_without_traceback(self) -> None:
        nested: object = "leaf"
        for _ in range(80):
            nested = [nested]
        (self.packet / MANIFEST_NAME).write_text(
            json.dumps({"deep": nested}), encoding="utf-8", newline="\n"
        )

        errors = self.verify_fast()

        self.assertTrue(any("exceeds JSON depth" in error for error in errors), errors)

    def test_verify_rejects_nonfinite_json(self) -> None:
        (self.packet / MANIFEST_NAME).write_bytes(b'{"schema_version": NaN}\n')

        errors = self.verify_fast()

        self.assertTrue(
            any("non-finite JSON number is forbidden" in error for error in errors),
            errors,
        )

        (self.packet / MANIFEST_NAME).write_bytes(
            b'{"schema_version": 1e9999}\n'
        )
        errors = self.verify_fast()
        self.assertTrue(
            any("contains a non-finite JSON number" in error for error in errors),
            errors,
        )

    def test_verify_rejects_portable_manifest_path_collisions(self) -> None:
        document = self.load_manifest(self.packet)
        original = document["files"][0]["packet_path"]
        document["files"][1]["packet_path"] = original.swapcase()
        self.write_manifest(self.packet, document)

        errors = self.verify_fast()

        self.assertTrue(
            any("Unicode-normalized packet_path collision" in error for error in errors),
            errors,
        )

    def test_untrusted_manifest_cardinality_stops_before_entry_iteration(self) -> None:
        errors: list[str] = []
        document = {
            "schema_version": packet_module.SCHEMA_VERSION,
            "scope_version": packet_module.SCOPE_VERSION,
            "packet_purpose": "HUMAN_REVIEW_INPUTS_ONLY",
            "source_commit": self.commit,
            "source_tree_oid": "0" * 40,
            "files": [{} for _ in range(10_000)],
        }
        with patch.object(packet_module, "MAX_PACKET_FILES", 1):
            packet_module._validate_untrusted_manifest(document, errors)

        self.assertEqual(["packet files exceeds 1 entries"], errors)

    def test_verify_rejects_non_utf8_encodable_json_path_without_traceback(self) -> None:
        document = self.load_manifest(self.packet)
        document["files"][0]["packet_path"] = "\ud800"
        (self.packet / MANIFEST_NAME).write_text(
            json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True) + "\n",
            encoding="ascii",
            newline="\n",
        )

        errors = self.verify_fast()

        self.assertTrue(
            any(
                "packet_path is unsafe" in error or "not valid UTF-8" in error
                for error in errors
            ),
            errors,
        )

    def test_verify_enforces_file_count_size_and_path_bounds(self) -> None:
        expected_paths = [
            path.relative_to(self.packet).as_posix()
            for path in self.packet.rglob("*")
            if path.is_file()
        ]
        maximum_size = max((self.packet / path).stat().st_size for path in expected_paths)
        (self.packet / "oversized-extra.bin").write_bytes(b"x" * (maximum_size + 1))
        with patch.object(packet_module, "MAX_FILE_BYTES", maximum_size):
            errors = self.verify_fast()
        self.assertTrue(any("file exceeds" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        expected_count = sum(1 for path in self.packet.rglob("*") if path.is_file())
        (self.packet / "count-extra.txt").write_text("extra\n", encoding="utf-8")
        with patch.object(packet_module, "MAX_PACKET_FILES", expected_count - 1):
            errors = self.verify_fast()
        self.assertTrue(any("exceeds" in error and "files" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        manifest = self.load_manifest(self.packet)
        bound_paths = [MANIFEST_NAME]
        bound_paths.extend(entry["packet_path"] for entry in manifest["files"])
        bound_paths.extend(entry["repository_path"] for entry in manifest["files"])
        maximum_path_bytes = max(len(path.encode("utf-8")) for path in bound_paths)
        long_name = "x" * (maximum_path_bytes + 1)
        (self.packet / long_name).write_text("extra\n", encoding="utf-8")
        with patch.object(packet_module, "MAX_PACKET_PATH_BYTES", maximum_path_bytes):
            errors = self.verify_fast()
        self.assertTrue(any("path is unsafe" in error and "bytes" in error for error in errors), errors)

        shutil.rmtree(self.packet)
        shutil.copytree(self.base_packet, self.packet)
        maximum_depth = max(len(PurePosixPath(path).parts) for path in bound_paths)
        deep = self.packet
        for index in range(maximum_depth + 1):
            deep /= f"d{index}"
        deep.mkdir(parents=True)
        (deep / "extra.txt").write_text("extra\n", encoding="utf-8")
        with patch.object(packet_module, "MAX_PACKET_PATH_DEPTH", maximum_depth):
            errors = self.verify_fast()
        self.assertTrue(
            any("path is unsafe" in error and "components" in error for error in errors),
            errors,
        )

    def test_selected_tree_bounds_and_symlink_are_rejected(self) -> None:
        for field, value, message in (
            ("MAX_SELECTED_TREE_FILES", 1, "exceeds 1 files"),
            ("MAX_SELECTED_TREE_DIRECTORIES", 0, "exceeds 0 directories"),
            ("MAX_SELECTED_TREE_FILE_BYTES", 1, "selected commit blob exceeds 1"),
            ("MAX_SELECTED_TREE_BYTES", 1, "exceeds 1 total bytes"),
        ):
            with (
                self.subTest(field=field),
                patch.object(packet_module, field, value),
                self.assertRaisesRegex(PacketError, message),
            ):
                packet_module._validate_selected_tree_bounds(self.root, self.commit)

        link_blob = self.git("hash-object", "-w", "README.md")
        self.git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"120000,{link_blob},tracked-selected-link",
        )
        self.git("commit", "--quiet", "-m", "add tracked selected symlink")
        selected = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(PacketError, "symlink, submodule, or non-regular"):
            packet_module._validate_selected_tree_bounds(self.root, selected)

        self.git("reset", "--hard", self.commit)
        self.git("config", "core.ignorecase", "false")
        readme_blob = self.git("hash-object", "-w", "README.md")
        self.git(
            "update-index",
            "--add",
            "--cacheinfo",
            f"100644,{readme_blob},readme.md",
        )
        self.git("commit", "--quiet", "-m", "add portable path collision")
        collision_commit = self.git("rev-parse", "HEAD")
        with self.assertRaisesRegex(PacketError, "path collision"):
            packet_module._validate_selected_tree_bounds(
                self.root, collision_commit
            )

        duplicate_blob = self.git("hash-object", "-w", "README.md")
        duplicate_tree_content = (
            b"100644 README.md\0"
            + bytes.fromhex(duplicate_blob)
            + b"100644 README.md\0"
            + bytes.fromhex(duplicate_blob)
        )
        duplicate_tree = self.git_with_input(
            duplicate_tree_content,
            "hash-object",
            "--literally",
            "-t",
            "tree",
            "-w",
            "--stdin",
        )
        duplicate_commit = self.git_with_input(
            b"duplicate exact tree path\n",
            "commit-tree",
            duplicate_tree,
            "-p",
            self.commit,
        )
        with self.assertRaisesRegex(PacketError, "duplicate exact path"):
            packet_module._validate_selected_tree_bounds(
                self.root, duplicate_commit
            )
        with self.assertRaisesRegex(PacketError, "exactly one Git entry"):
            packet_module._git_blob(self.root, duplicate_commit, "README.md")

        empty_tree = self.git_with_input(b"", "mktree")
        empty_directories = (
            b"040000 empty-one\0"
            + bytes.fromhex(empty_tree)
            + b"040000 empty-two\0"
            + bytes.fromhex(empty_tree)
        )
        empty_root = self.git_with_input(
            empty_directories,
            "hash-object",
            "--literally",
            "-t",
            "tree",
            "-w",
            "--stdin",
        )
        empty_commit = self.git_with_input(
            b"bounded empty trees\n",
            "commit-tree",
            empty_root,
            "-p",
            self.commit,
        )
        with (
            patch.object(packet_module, "MAX_SELECTED_TREE_DIRECTORIES", 1),
            self.assertRaisesRegex(PacketError, "exceeds 1 directories"),
        ):
            packet_module._validate_selected_tree_bounds(self.root, empty_commit)

    def test_git_object_reads_recompute_oid_and_bound_undeclared_bytes(self) -> None:
        oid = self.git("rev-parse", f"{self.commit}:README.md")
        objects = Path(self.git("rev-parse", "--git-path", "objects"))
        if not objects.is_absolute():
            objects = self.root / objects
        loose = objects / oid[:2] / oid[2:]
        loose.parent.mkdir(parents=True, exist_ok=True)
        if loose.exists():
            loose.chmod(0o600)

        loose.write_bytes(zlib.compress(b"blob 4\0BBBB"))
        with self.assertRaisesRegex(PacketError, "Git object identity mismatch"):
            packet_module._read_verified_git_object(
                self.root, oid, "blob", 1024, "corrupt blob"
            )

        loose.write_bytes(zlib.compress(b"blob 1\0" + b"A" * (1024 * 1024)))
        with self.assertRaisesRegex(PacketError, "undeclared bytes"):
            packet_module._read_verified_git_object(
                self.root, oid, "blob", 1024, "size-forged blob"
            )

    def test_paths_reject_all_windows_device_names_and_component_bounds(self) -> None:
        for value in (
            "CONIN$/value",
            "CONOUT$.txt",
            "COM¹/value",
            "LPT³.txt",
            ".Git/config",
            "a" * 256,
        ):
            with self.subTest(value=value):
                self.assertIsNotNone(packet_module._relative_path_error(value))

        repository_path = "/".join("a" for _ in range(32))
        self.assertIsNone(packet_module._relative_path_error(repository_path))
        with self.assertRaisesRegex(PacketError, "components"):
            packet_module._safe_relative_path(
                f"files/{repository_path}", "derived packet path"
            )

    @unittest.skipUnless(os.name == "nt", "Windows junction coverage")
    def test_verify_rejects_windows_junction(self) -> None:
        target = self.build / "junction-target"
        target.mkdir()
        junction = self.packet / "unsafe-junction"
        result = self.run_command(
            ["cmd", "/c", "mklink", "/J", str(junction), str(target)],
            check=False,
        )
        if result.returncode != 0:
            self.skipTest("junction creation is unavailable")
        try:
            errors = self.verify_fast()
            self.assertTrue(
                any("junction" in error or "reparse point" in error for error in errors),
                errors,
            )
        finally:
            os.rmdir(junction)

    def test_head_commit_and_output_safety_rules(self) -> None:
        with self.assertRaisesRegex(PacketError, "lowercase full 40-character"):
            resolve_commit(self.root, self.commit[:12])
        with self.assertRaisesRegex(PacketError, "Git-ignored path"):
            generate_packet(self.root, self.commit, self.root / "not-ignored")
        with self.assertRaisesRegex(PacketError, "must not already exist"):
            generate_packet(self.root, self.commit, self.packet)

        clean_output = self.build / "clean-head"
        generate_packet(self.root, "HEAD", clean_output)
        self.assertEqual([], verify_packet(self.root, "HEAD", clean_output))
        (self.root / "untracked.txt").write_text("dirty\n", encoding="utf-8")
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            generate_packet(self.root, "HEAD", self.build / "dirty-head")

        (self.root / "untracked.txt").unlink()
        many_untracked = self.root / "many-untracked"
        many_untracked.mkdir()
        for index in range(200):
            (many_untracked / f"entry-{index:03d}.txt").write_bytes(b"")
        with self.assertRaisesRegex(PacketError, "HEAD may be used only"):
            resolve_commit(self.root, "HEAD")

    def test_generation_rejects_missing_tracked_output_without_writing(self) -> None:
        tracked_output = self.build / "tracked-packet"
        tracked_output.write_text("tracked file\n", encoding="utf-8")
        self.git("add", "-f", "--", "build/tracked-packet")
        self.git("commit", "--quiet", "-m", "track ignored packet output")
        tracked_output.unlink()
        status_before = self.git("status", "--short")

        with self.assertRaisesRegex(PacketError, "tracked index path"):
            generate_packet(
                self.root,
                self.commit,
                tracked_output,
            )

        self.assertFalse(tracked_output.exists())
        self.assertEqual(status_before, self.git("status", "--short"))

    def test_tracked_output_query_is_literal_and_large_result_bounded(self) -> None:
        wildcard_match = self.build / "metaa" / "tracked.txt"
        wildcard_match.parent.mkdir()
        wildcard_match.write_text("tracked wildcard neighbor\n", encoding="utf-8")

        literal_output = self.build / "meta[ab]"
        tracked_descendants = [
            literal_output / f"directory-{index:03d}" / "tracked.txt"
            for index in range(256)
        ]
        for path in tracked_descendants:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("tracked descendant\n", encoding="utf-8")
        self.git(
            "add",
            "-f",
            "--",
            "build/metaa",
            ":(top,literal)build/meta[ab]",
        )
        self.git("commit", "--quiet", "-m", "track bounded literal output fixtures")
        shutil.rmtree(literal_output)

        self.assertFalse(
            packet_module._index_contains_path_or_descendant(
                self.root, "build/meta[ac]"
            )
        )
        self.assertTrue(
            packet_module._index_contains_path_or_descendant(
                self.root, "build/meta[ab]"
            )
        )
        with self.assertRaisesRegex(PacketError, "tracked index path"):
            packet_module._safe_ignored_directory(
                self.root,
                literal_output,
                "output directory",
                require_exists=False,
            )
        self.assertFalse(literal_output.exists())

    def test_cli_generate_verify_and_failure_exit_bind_one_printed_sha(self) -> None:
        script = self.root / GENERATOR_PATH
        output = self.build / "cli-packet"
        nonisolated = self.run_command(
            [sys.executable, str(script), "--help"], check=False
        )
        self.assertEqual(2, nonisolated.returncode)
        self.assertIn("requires Python isolated mode", nonisolated.stderr)

        generate_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "generate",
                "--commit",
                self.commit,
                "--output",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(0, generate_result.returncode, generate_result.stderr)
        self.assertEqual(1, generate_result.stdout.count(self.commit))

        verify_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "verify",
                "--commit",
                self.commit,
                "--packet",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(0, verify_result.returncode, verify_result.stderr)
        self.assertEqual(1, verify_result.stdout.count(self.commit))

        invalid_result = self.run_command(
            [
                sys.executable,
                "-I",
                "-S",
                str(script),
                "--repository-root",
                str(self.root),
                "verify",
                "--commit",
                self.commit[:12],
                "--packet",
                str(output),
            ],
            check=False,
        )
        self.assertEqual(1, invalid_result.returncode)
        self.assertIn("lowercase full 40-character", invalid_result.stderr)

    def test_nonisolated_cli_rejects_local_stdlib_shadow_before_import(self) -> None:
        script = self.root / GENERATOR_PATH
        marker = self.build / "shadow-imported.txt"
        shadow = self.root / "scripts/json.py"
        shadow.write_text(
            f"open({str(marker)!r}, 'w', encoding='utf-8').write('executed')\n",
            encoding="utf-8",
            newline="\n",
        )

        result = self.run_command(
            [sys.executable, str(script), "--help"], check=False
        )

        self.assertEqual(2, result.returncode)
        self.assertIn("requires Python isolated mode", result.stderr)
        self.assertFalse(marker.exists())

    def test_main_resolves_commit_once(self) -> None:
        stdout = io.StringIO()
        stderr = io.StringIO()
        with (
            patch.object(
                packet_module,
                "_run_selected_commit_validation",
                return_value=dict(self.base_validation),
            ),
            patch.object(
                packet_module,
                "resolve_commit",
                wraps=packet_module.resolve_commit,
            ) as resolver,
            patch("sys.stdout", stdout),
            patch("sys.stderr", stderr),
        ):
            result = main(
                [
                    "--repository-root",
                    str(self.root),
                    "verify",
                    "--commit",
                    self.commit,
                    "--packet",
                    str(self.packet),
                ]
            )
        self.assertEqual(0, result, stderr.getvalue())
        self.assertEqual(1, resolver.call_count)
        self.assertEqual(1, stdout.getvalue().count(self.commit))


if __name__ == "__main__":
    unittest.main()
