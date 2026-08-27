import hashlib
import io
import json
import stat
import tempfile
import unittest
import zipfile
from contextlib import redirect_stdout
from pathlib import Path
from unittest.mock import patch

from scripts.generate_v002_g0_evidence import (
    EVIDENCE_FILENAMES,
    EvidenceError,
    MAX_ARCHIVE_COMPRESSION_RATIO,
    _expected_directory_entries,
    _validate_archive_structure,
    build_evidence,
    build_expected_sources_files,
    main,
    verify_evidence,
    write_evidence,
)
from scripts.validate_build_artifact import (
    DEFAULT_VERSION,
    PACKAGED_SOURCE_FILES,
)


class G0EvidenceTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary_directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary_directory.cleanup)
        self.root = Path(self.temporary_directory.name)
        self.mods_toml = (
            'modLoader="javafml"\n'
            'license="MIT"\n'
            'modId="advancedrocketrycommunity"\n'
            f'version="{DEFAULT_VERSION}"\n'
            'displayName="Advanced Rocketry: Community Edition"\n'
            'displayTest="MATCH_VERSION"\n'
            'features={java_version="[17,)"}\n'
            'versionRange="[47.4.10,48)"\n'
            'versionRange="[1.20.1,1.20.2)"\n'
        ).encode("utf-8")
        self.binary = self.root / f"advancedrocketry-community-{DEFAULT_VERSION}.jar"
        self.sources = (
            self.root
            / f"advancedrocketry-community-{DEFAULT_VERSION}-sources.jar"
        )
        self._write_binary()
        self._write_sources()

    @staticmethod
    def _packaged_files() -> dict[str, bytes]:
        return {
            packaged_path: source_path.read_bytes()
            for packaged_path, source_path in PACKAGED_SOURCE_FILES.items()
        }

    def _write_archive(self, path: Path, entries: dict[str, bytes]) -> None:
        with zipfile.ZipFile(path, "w") as archive:
            for name, content in entries.items():
                archive.writestr(name, content)

    def _write_binary(self, extra_entries: dict[str, bytes] | None = None) -> None:
        entries = {
            "META-INF/": b"",
            "META-INF/MANIFEST.MF": (
                "Manifest-Version: 1.0\n"
                "Specification-Title: advancedrocketrycommunity\n"
                "Implementation-Title: Advanced Rocketry: Community Edition\n"
                f"Implementation-Version: {DEFAULT_VERSION}\n"
            ).encode("utf-8"),
            **self._packaged_files(),
            "META-INF/mods.toml": self.mods_toml,
            "pack.mcmeta": json.dumps(
                {
                    "pack": {
                        "description": "Advanced Rocketry: Community Edition resources",
                        "pack_format": 15,
                    }
                }
            ).encode("utf-8"),
            "advancedrocketrycommunity.png": b"\x89PNG\r\n\x1a\n",
            "io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.class": b"class",
        }
        entries.update(extra_entries or {})
        self._write_archive(self.binary, entries)

    def _write_sources(self, extra_entries: dict[str, bytes] | None = None) -> None:
        entries = build_expected_sources_files()
        entries.update(extra_entries or {})
        directory_entries = {
            directory: b"" for directory in _expected_directory_entries(set(entries))
        }
        self._write_archive(self.sources, {**directory_entries, **entries})

    @staticmethod
    def _append_special_entry(path: Path, name: str, unix_mode: int) -> None:
        info = zipfile.ZipInfo(name)
        info.create_system = 3
        info.external_attr = unix_mode << 16
        with zipfile.ZipFile(path, "a") as archive:
            archive.writestr(info, b"target")

    def _generate(self, evidence_dir: Path) -> dict[str, bytes]:
        evidence = build_evidence(self.binary, self.sources)
        write_evidence(evidence_dir, evidence, self.binary, self.sources)
        return evidence

    def test_generated_evidence_is_complete_deterministic_and_portable(self) -> None:
        first = self.root / "first"
        second = self.root / "second"

        first_evidence = self._generate(first)
        second_evidence = self._generate(second)

        self.assertEqual(set(EVIDENCE_FILENAMES), set(first_evidence))
        for filename in EVIDENCE_FILENAMES:
            with self.subTest(filename=filename):
                self.assertEqual(
                    (first / filename).read_bytes(),
                    (second / filename).read_bytes(),
                )
                self.assertNotIn(
                    str(self.root).encode("utf-8"),
                    (first / filename).read_bytes(),
                )

        scan = json.loads((first / "license-notice-scan.json").read_text())
        self.assertEqual(1, scan["schema_version"])
        self.assertEqual(["binary", "sources"], [a["role"] for a in scan["artifacts"]])
        self.assertIn("does not constitute legal approval", scan["scope"])
        for artifact in scan["artifacts"]:
            scanned_paths = {
                entry["path"] for entry in artifact["license_notice_entries"]
            }
            self.assertEqual(set(PACKAGED_SOURCE_FILES), scanned_paths)
            self.assertTrue(
                all(
                    entry["matches_repository_source"]
                    for entry in artifact["license_notice_entries"]
                )
            )

        sources_manifest = json.loads(
            (first / "sources-jar-manifest.json").read_text()
        )
        paths = [entry["path"] for entry in sources_manifest["entries"]]
        self.assertEqual(sorted(paths), paths)
        self.assertEqual(len(paths), sources_manifest["entry_count"])
        self.assertEqual(self.sources.name, sources_manifest["artifact"])
        self.assertEqual(
            hashlib.sha256(self.sources.read_bytes()).hexdigest(),
            sources_manifest["artifact_sha256"],
        )
        self.assertEqual(self.binary.name, sources_manifest["paired_binary_artifact"])
        expected_sources = build_expected_sources_files()
        repository_inputs = {
            entry["archive_path"]: entry
            for entry in sources_manifest["repository_inputs"]
        }
        expected_java = {
            path for path in expected_sources if path.endswith(".java")
        }
        self.assertGreater(len(expected_java), 1)
        self.assertEqual(expected_java, expected_java & repository_inputs.keys())
        self.assertEqual(
            len(repository_inputs), sources_manifest["repository_input_count"]
        )
        for archive_path, binding in repository_inputs.items():
            self.assertEqual(
                hashlib.sha256(expected_sources[archive_path]).hexdigest(),
                binding["sha256"],
            )
            self.assertEqual(len(expected_sources[archive_path]), binding["size"])
        self.assertEqual(self.mods_toml, (first / "mods.toml").read_bytes())
        self.assertEqual([], verify_evidence(first, first_evidence))

    def test_verify_rejects_tampered_evidence(self) -> None:
        evidence_dir = self.root / "evidence"
        expected = self._generate(evidence_dir)
        (evidence_dir / "mods.toml").write_bytes(self.mods_toml + b"# changed\n")

        errors = verify_evidence(evidence_dir, expected)

        self.assertTrue(any("mods.toml" in error for error in errors))

    def test_verify_rejects_missing_and_unexpected_files(self) -> None:
        evidence_dir = self.root / "evidence"
        expected = self._generate(evidence_dir)
        (evidence_dir / "README.md").unlink()
        (evidence_dir / "unexpected.txt").write_text("unexpected")

        errors = verify_evidence(evidence_dir, expected)

        self.assertTrue(any("missing evidence" in error for error in errors))
        self.assertTrue(any("unexpected evidence" in error for error in errors))

    def test_verify_rejects_evidence_generated_for_different_sources_jar(self) -> None:
        evidence_dir = self.root / "evidence"
        original = self._generate(evidence_dir)
        with zipfile.ZipFile(self.sources, "a") as archive:
            archive.comment = b"different deterministic build container"

        changed = build_evidence(self.binary, self.sources)
        errors = verify_evidence(evidence_dir, changed)

        self.assertNotEqual(
            original["sources-jar-manifest.json"],
            changed["sources-jar-manifest.json"],
        )
        self.assertTrue(any("sources-jar-manifest.json" in error for error in errors))

    def test_sources_jar_unsafe_path_is_rejected(self) -> None:
        self._write_sources({"../outside.java": b"class Outside {}\n"})

        with self.assertRaisesRegex(EvidenceError, "unsafe archive paths"):
            build_evidence(self.binary, self.sources)

    def test_sources_jar_mismatched_license_is_rejected(self) -> None:
        self._write_sources({"META-INF/LICENSE": b"changed"})

        with self.assertRaisesRegex(EvidenceError, "do not match repository/generated"):
            build_evidence(self.binary, self.sources)

    def test_sources_jar_cannot_substitute_one_dummy_java_file(self) -> None:
        expected = build_expected_sources_files()
        java_paths = sorted(path for path in expected if path.endswith(".java"))
        entries = {
            path: content
            for path, content in expected.items()
            if not path.endswith(".java") or path == java_paths[0]
        }
        directories = {
            directory: b"" for directory in _expected_directory_entries(set(entries))
        }
        self._write_archive(self.sources, {**directories, **entries})

        with self.assertRaisesRegex(EvidenceError, "missing files"):
            build_evidence(self.binary, self.sources)

    def test_sources_jar_rejects_changed_repository_java_content(self) -> None:
        java_path = next(
            path for path in build_expected_sources_files() if path.endswith(".java")
        )
        self._write_sources({java_path: b"package substituted;\n"})

        with self.assertRaisesRegex(EvidenceError, "do not match repository/generated"):
            build_evidence(self.binary, self.sources)

    def test_binary_and_sources_jars_reject_symbolic_link_entries(self) -> None:
        self._append_special_entry(
            self.binary,
            "linked.class",
            stat.S_IFLNK | 0o777,
        )
        with self.assertRaisesRegex(EvidenceError, "symbolic-link"):
            build_evidence(self.binary, self.sources)

        self._write_binary()
        self._append_special_entry(
            self.sources,
            "linked.java",
            stat.S_IFLNK | 0o777,
        )
        with self.assertRaisesRegex(EvidenceError, "symbolic-link"):
            build_evidence(self.binary, self.sources)

    def test_non_regular_zip_entry_is_rejected(self) -> None:
        self._append_special_entry(
            self.sources,
            "named-pipe.java",
            stat.S_IFIFO | 0o600,
        )

        with self.assertRaisesRegex(EvidenceError, "non-regular"):
            build_evidence(self.binary, self.sources)

    def test_archive_resource_limits_are_enforced_before_decompression(self) -> None:
        archive_path = self.root / "limits.jar"
        self._write_archive(archive_path, {"one": b"1234", "two": b"5678"})

        cases = (
            ("MAX_ARCHIVE_ENTRIES", 1, "too many entries"),
            ("MAX_ARCHIVE_ENTRY_SIZE", 3, "per-entry uncompressed-size"),
            ("MAX_ARCHIVE_TOTAL_SIZE", 7, "total uncompressed size"),
        )
        for constant, limit, message in cases:
            with self.subTest(constant=constant), patch(
                f"scripts.generate_v002_g0_evidence.{constant}", limit
            ), zipfile.ZipFile(archive_path) as archive:
                with self.assertRaisesRegex(EvidenceError, message):
                    _validate_archive_structure(archive, "test")

    def test_high_compression_ratio_is_rejected(self) -> None:
        archive_path = self.root / "ratio.jar"
        with zipfile.ZipFile(
            archive_path, "w", compression=zipfile.ZIP_DEFLATED
        ) as archive:
            archive.writestr("repeated.bin", b"0" * (1024 * 1024))

        with zipfile.ZipFile(archive_path) as archive, self.assertRaisesRegex(
            EvidenceError, "compression-ratio"
        ):
            info = archive.getinfo("repeated.bin")
            self.assertGreater(
                info.file_size,
                info.compress_size * MAX_ARCHIVE_COMPRESSION_RATIO,
            )
            _validate_archive_structure(archive, "test")

    def test_sources_jar_must_pair_with_binary_filename(self) -> None:
        renamed = self.root / "unrelated-sources.jar"
        self.sources.rename(renamed)

        with self.assertRaisesRegex(EvidenceError, "filename must pair"):
            build_evidence(self.binary, renamed)

    def test_cli_generates_and_verifies_evidence(self) -> None:
        evidence_dir = self.root / "evidence"
        generate_stdout = io.StringIO()
        with redirect_stdout(generate_stdout):
            generate_result = main(
                [
                    "generate",
                    str(self.binary),
                    str(self.sources),
                    "--evidence-dir",
                    str(evidence_dir),
                ]
            )
        verify_stdout = io.StringIO()
        with redirect_stdout(verify_stdout):
            verify_result = main(
                [
                    "verify",
                    str(self.binary),
                    str(self.sources),
                    "--evidence-dir",
                    str(evidence_dir),
                ]
            )

        self.assertEqual(0, generate_result)
        self.assertEqual(0, verify_result)
        self.assertIn("[PASS] Generated 4", generate_stdout.getvalue())
        self.assertIn("[PASS] Verified 4", verify_stdout.getvalue())
        self.assertIn("Mechanical packaging evidence only", verify_stdout.getvalue())

    def test_cli_verify_fails_after_evidence_tampering(self) -> None:
        evidence_dir = self.root / "evidence"
        self._generate(evidence_dir)
        (evidence_dir / "license-notice-scan.json").write_text("{}\n")

        stdout = io.StringIO()
        with redirect_stdout(stdout):
            result = main(
                [
                    "verify",
                    str(self.binary),
                    str(self.sources),
                    "--evidence-dir",
                    str(evidence_dir),
                ]
            )

        self.assertEqual(1, result)
        self.assertIn("[FAIL]", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
