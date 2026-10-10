"""IdentityCases for the packet review facade."""
import json
import re
import sys
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
import scripts.validate_bootstrap_provenance as validator_module
from scripts.prepare_v002_g0_review_packet import (
    GENERATOR_PATH,
    PROVENANCE_MANIFEST,
    PROVENANCE_RECORD,
    VALIDATOR_PATH,
    PacketError,
    generate_packet,
)


class IdentityCases:
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
