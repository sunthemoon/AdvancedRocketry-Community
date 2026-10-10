from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import MAX_JSON_BYTES
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import _profile_inventory_sha256
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import inspect_profile_inventory
from scripts.collect_v002_manual_evidence import validate_bundle
from unittest.mock import patch
import hashlib
import json
import shutil


class ProfileCases:
    """Profile inventory and snapshot binding."""

    def test_profile_snapshot_happy_path_is_canonical_and_role_bound(self) -> None:
        session = self.ready_session()

        for role in ("matching", "missing_mod"):
            profile = session["client_profiles"][role]
            for phase in ("before", "after"):
                path = self.root / profile[f"{phase}_snapshot"]
                document = json.loads(path.read_text(encoding="utf-8"))
                self.assertEqual(role, document["profile_role"])
                self.assertEqual(phase, document["phase"])
                self.assertEqual(
                    path.read_text(encoding="utf-8"),
                    json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True)
                    + "\n",
                )
        matching = json.loads(
            (
                self.root
                / session["client_profiles"]["matching"]["after_snapshot"]
            ).read_text(encoding="utf-8")
        )
        missing = json.loads(
            (
                self.root
                / session["client_profiles"]["missing_mod"]["after_snapshot"]
            ).read_text(encoding="utf-8")
        )
        self.assertEqual([self.artifact_name], [item["path"] for item in matching["mods_files"]])
        self.assertEqual([], missing["mods_files"])

    def test_matching_profile_rejects_any_extra_mod_file_after_snapshot(self) -> None:
        session = self.ready_session()
        matching_game = self.root / session["client_profiles"]["matching"][
            "game_directory"
        ]
        (matching_game / "mods" / "unexpected.jar").write_bytes(b"unexpected")

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("exactly the expected project JAR" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_matching_profile_rejects_surplus_before_hashing_mods(self) -> None:
        matching_game = self.jar_paths["client"].parent.parent
        (matching_game / "mods" / "surplus.jar").write_bytes(b"surplus")

        with patch(
            "scripts.collect_v002_manual_evidence.file_sha256",
            side_effect=AssertionError("surplus inventory must fail before hashing"),
        ):
            with self.assertRaisesRegex(ValueError, "exactly the expected project JAR"):
                inspect_profile_inventory(
                    matching_game,
                    profile_role="matching",
                    artifact_metadata={
                        "filename": self.artifact_name,
                        "sha256": self.artifact_hash,
                    },
                    repository_root=self.root,
                )

    def test_missing_profile_rejects_first_entry_before_hashing_mods(self) -> None:
        missing_game = self.build / "bounded-missing-client"
        mods = missing_game / "mods"
        mods.mkdir(parents=True)
        (mods / "unexpected.jar").write_bytes(b"unexpected")

        with patch(
            "scripts.collect_v002_manual_evidence.file_sha256",
            side_effect=AssertionError("missing profile must fail before hashing"),
        ):
            with self.assertRaisesRegex(ValueError, "must be empty"):
                inspect_profile_inventory(
                    missing_game,
                    profile_role="missing_mod",
                    artifact_metadata={
                        "filename": self.artifact_name,
                        "sha256": self.artifact_hash,
                    },
                    repository_root=self.root,
                )

    def test_matching_profile_rejects_wrong_singleton_before_hashing(self) -> None:
        matching_game = self.build / "wrong-singleton-client"
        mods = matching_game / "mods"
        mods.mkdir(parents=True)
        (mods / "wrong.jar").write_bytes(b"wrong")

        with patch(
            "scripts.collect_v002_manual_evidence.file_sha256",
            side_effect=AssertionError("wrong filename must fail before hashing"),
        ):
            with self.assertRaisesRegex(ValueError, "exact committed project JAR"):
                inspect_profile_inventory(
                    matching_game,
                    profile_role="matching",
                    artifact_metadata={
                        "filename": self.artifact_name,
                        "sha256": self.artifact_hash,
                    },
                    repository_root=self.root,
                )

    def test_oversized_profile_snapshot_source_is_rejected_before_parsing(self) -> None:
        session = self.ready_session()
        snapshot = self.root / session["client_profiles"]["matching"][
            "before_snapshot"
        ]
        with snapshot.open("wb") as stream:
            stream.truncate(MAX_JSON_BYTES + 1)

        errors, _, output = self.collect(session, "oversized-profile-source")

        self.assertTrue(
            any(
                f"matching before profile snapshot exceeds {MAX_JSON_BYTES} bytes"
                in error
                for error in errors
            ),
            errors,
        )
        self.assertFalse(output.exists())

    def test_oversized_server_summary_source_is_rejected_before_parsing(self) -> None:
        session = self.ready_session()
        summary = self.root / session["server_harness"]["summary"]
        with summary.open("wb") as stream:
            stream.truncate(MAX_JSON_BYTES + 1)

        errors, _, output = self.collect(session, "oversized-summary-source")

        self.assertTrue(
            any(
                f"server harness summary source exceeds {MAX_JSON_BYTES} bytes"
                in error
                for error in errors
            ),
            errors,
        )
        self.assertFalse(output.exists())

    def test_deeply_nested_session_json_is_rejected_without_crashing(self) -> None:
        session_path = self.build / "deep-session.json"
        session_path.write_bytes(b"[" * 2000 + b"0" + b"]" * 2000)
        output = self.build / "deep-session-bundle"

        errors, record = collect_evidence(session_path, output, self.root)

        self.assertIsNone(record)
        self.assertTrue(
            any("exceeds the JSON nesting limit" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_missing_mod_profile_rejects_project_jar_after_snapshot(self) -> None:
        session = self.ready_session()
        missing_game = self.root / session["client_profiles"]["missing_mod"][
            "game_directory"
        ]
        (missing_game / "mods" / self.artifact_name).write_bytes(self.artifact_content)

        errors, _, output = self.collect(session)

        self.assertTrue(any("must be empty" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_client_profile_directories_must_not_be_nested(self) -> None:
        session = self.ready_session()
        missing_profile = session["client_profiles"]["missing_mod"]
        old_missing_game = self.root / missing_profile["game_directory"]
        matching_game = self.root / session["client_profiles"]["matching"][
            "game_directory"
        ]
        nested_missing_game = matching_game / "logs" / "nested-missing-client"
        nested_missing_game.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(old_missing_game, nested_missing_game)
        nested_relative = nested_missing_game.relative_to(self.root).as_posix()
        missing_profile["game_directory"] = nested_relative

        for phase in ("before", "after"):
            snapshot_path = self.root / missing_profile[f"{phase}_snapshot"]
            document = json.loads(snapshot_path.read_text(encoding="utf-8"))
            document["game_directory"] = nested_relative
            document["mods_directory"] = f"{nested_relative}/mods"
            document["inventory_sha256"] = _profile_inventory_sha256(document)
            snapshot_path.write_text(
                json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True)
                + "\n",
                encoding="utf-8",
                newline="\n",
            )
        session["log_excerpts"]["mismatch_attempt"]["source"] = (
            nested_missing_game / "logs" / "latest.log"
        ).relative_to(self.root).as_posix()

        errors, _, output = self.collect(session, "nested-client-profiles")

        self.assertTrue(
            any("ancestor/descendant pair" in error for error in errors), errors
        )
        self.assertFalse(output.exists())

    def test_committed_bundle_rejects_nested_profile_path_rebinding(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "nested-profile-record-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        profile = record["client_profiles"]["missing_mod"]
        nested_relative = (
            record["client_profiles"]["matching"]["game_directory"]
            + "/logs/nested-missing-client"
        )
        profile["game_directory"] = nested_relative
        profile["mods_directory"] = f"{nested_relative}/mods"

        rebound_inventory = ""
        for phase in ("before_snapshot", "after_snapshot"):
            snapshot_record = profile[phase]
            snapshot_path = output / snapshot_record["file"]
            document = json.loads(snapshot_path.read_text(encoding="utf-8"))
            document["game_directory"] = nested_relative
            document["mods_directory"] = f"{nested_relative}/mods"
            document["inventory_sha256"] = _profile_inventory_sha256(document)
            payload = (
                json.dumps(document, ensure_ascii=True, indent=2, sort_keys=True)
                + "\n"
            ).encode("utf-8")
            digest = hashlib.sha256(payload).hexdigest()
            snapshot_path.write_bytes(payload)
            snapshot_record.update(
                source_sha256=digest,
                sha256=digest,
                size=len(payload),
            )
            rebound_inventory = document["inventory_sha256"]
        profile["inventory_sha256"] = rebound_inventory
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "ancestor/descendant pair" in error
                for error in validation_errors
            ),
            validation_errors,
        )

    def test_profile_before_and_after_sources_must_be_distinct(self) -> None:
        session = self.ready_session()
        profile = session["client_profiles"]["matching"]
        profile["after_snapshot"] = profile["before_snapshot"]

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("snapshots must be distinct physical files" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_profile_snapshot_timeline_must_span_player_sessions(self) -> None:
        session = self.ready_session()
        after_path = self.root / session["client_profiles"]["matching"][
            "after_snapshot"
        ]
        document = json.loads(after_path.read_text(encoding="utf-8"))
        document["captured_at"] = "2026-08-27T11:30:00+00:00"
        after_path.write_text(
            json.dumps(document, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        errors, _, output = self.collect(session)

        self.assertTrue(
            any("after snapshot must follow the player harness" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())

    def test_missing_mod_snapshots_must_bracket_third_cycle(self) -> None:
        session = self.ready_session()
        profile = session["client_profiles"]["missing_mod"]
        for phase, captured_at in (
            ("before", "2026-08-27T12:14:00+00:00"),
            ("after", "2026-08-27T12:15:00+00:00"),
        ):
            path = self.root / profile[f"{phase}_snapshot"]
            document = json.loads(path.read_text(encoding="utf-8"))
            document["captured_at"] = captured_at
            path.write_text(
                json.dumps(document, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
                newline="\n",
            )

        errors, _, output = self.collect(session, "late-missing-mod-snapshots")

        self.assertTrue(
            any("before snapshot must not follow the third-cycle start" in error for error in errors),
            errors,
        )
        self.assertFalse(output.exists())
