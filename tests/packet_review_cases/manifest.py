"""ManifestCases for the packet review facade."""
import hashlib
import json
import shutil
from pathlib import PurePosixPath
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
from scripts.prepare_v002_g0_review_packet import MANIFEST_NAME


class ManifestCases:
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
                "differs from authoritative selected-commit expectation" in error
                or "size is" in error
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

        self.assertTrue(
            any(
                "packet files plus reviewer instructions exceeds 1 entries" in error
                for error in errors
            ),
            errors,
        )

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
