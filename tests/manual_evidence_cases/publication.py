from pathlib import Path
from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import RECORD_NAME
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import read_bounded_bytes
from scripts.collect_v002_manual_evidence import validate_bundle
from tests.manual_evidence_cases.png import make_png
from unittest.mock import patch
import json


class PublicationCases:
    """Staged publication and final revalidation."""

    def test_committed_bundle_rejects_conflicting_shared_source_audits(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "shared-audit-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        record_path = output / RECORD_NAME
        record = json.loads(record_path.read_text(encoding="utf-8"))
        record["log_excerpts"]["matching_client_connection"]["source_audit"][
            "audit_counts"
        ]["error_count"] = 1
        record_path.write_text(
            json.dumps(record, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )

        validation_errors, _ = validate_bundle(
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertTrue(
            any("raw log audits for shared source" in error for error in validation_errors),
            validation_errors,
        )

    def test_strict_collect_rejects_colon_or_ads_source_without_output(self) -> None:
        session = self.ready_session()
        carrier = self.build / "capture" / "unsafe-source"
        carrier.write_bytes(b"carrier")
        unsafe = Path(str(carrier) + ":capture")
        unsafe.write_bytes(make_png(seed=91))
        session["evidence"]["mods_page"]["source"] = unsafe.relative_to(
            self.root
        ).as_posix()
        output = self.build / "strict-unsafe-source"

        errors, record = collect_evidence(
            self.write_session(session, "strict-unsafe-source-session.json"),
            output,
            self.root,
            require_acceptance_ready=True,
        )

        self.assertIsNone(record)
        self.assertTrue(any("not portable" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_staged_validation_uses_build_mode_and_rejects_changed_raw_input(self) -> None:
        session = self.ready_session()
        output = self.build / "staged-validation-failure"
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        changed = False

        def mutate_then_validate(*args, **kwargs):
            nonlocal changed
            if not changed:
                changed = True
                client_log.write_text(
                    client_log.read_text(encoding="utf-8")
                    + "[Render thread/ERROR] [minecraft/Test]: changed during collect\n",
                    encoding="utf-8",
                )
            return validate_bundle(*args, **kwargs)

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_then_validate,
        ) as validator:
            errors, record = collect_evidence(
                self.write_session(session, "staged-validation-session.json"),
                output,
                self.root,
                require_acceptance_ready=True,
            )

        self.assertIsNone(record)
        self.assertTrue(any("raw log source no longer matches" in error for error in errors), errors)
        self.assertFalse(output.exists())
        staging = self.build / ".v002-evidence-staging"
        self.assertFalse(staging.exists())
        self.assertEqual("build", validator.call_args.kwargs["_validation_mode"])

    def test_validation_rereads_payload_binding_before_success(self) -> None:
        errors, _, output = self.collect(
            self.ready_session(), "payload-binding-reread"
        )
        self.assertEqual([], errors)
        target = output / "logs" / "client_startup_world.txt"
        target_absolute = target.absolute()
        target_reads = 0

        def mutate_after_validated_read(
            path: Path, maximum: int, label: str
        ) -> bytes:
            nonlocal target_reads
            payload = read_bounded_bytes(path, maximum, label)
            if path.absolute() == target_absolute:
                target_reads += 1
                if target_reads == 1:
                    target.write_bytes(b"X" + payload[1:])
            return payload

        with patch(
            "scripts.collect_v002_manual_evidence.read_bounded_bytes",
            side_effect=mutate_after_validated_read,
        ):
            validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "validated bundle payload changed after validation" in error
                for error in validation_errors
            ),
            validation_errors,
        )
        self.assertEqual(2, target_reads)

    def test_validation_rescans_inventory_after_final_payload_reads(self) -> None:
        errors, _, output = self.collect(
            self.ready_session(), "final-inventory-rescan"
        )
        self.assertEqual([], errors)
        added = False

        def add_file_during_final_binding_read(
            path: Path, maximum: int, label: str
        ) -> bytes:
            nonlocal added
            payload = read_bounded_bytes(path, maximum, label)
            if label.startswith("validated bundle payload ") and not added:
                added = True
                (output / "late-extra.txt").write_text("late\n", encoding="utf-8")
            return payload

        with patch(
            "scripts.collect_v002_manual_evidence.read_bounded_bytes",
            side_effect=add_file_during_final_binding_read,
        ):
            validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "file set changed during final binding verification" in error
                for error in validation_errors
            ),
            validation_errors,
        )
        self.assertTrue(added)

    def test_collect_rechecks_generated_record_binding_before_publish(self) -> None:
        session = self.ready_session()
        output = self.build / "staged-record-binding"
        mutated = False

        def mutate_record_after_validation(*args, **kwargs):
            nonlocal mutated
            result = validate_bundle(*args, **kwargs)
            if not mutated:
                mutated = True
                record_path = Path(args[0]) / RECORD_NAME
                payload = record_path.read_bytes()
                record_path.write_bytes(b"[" + payload[1:])
            return result

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_record_after_validation,
        ):
            errors, record = collect_evidence(
                self.write_session(session, "staged-record-binding-session.json"),
                output,
                self.root,
            )

        self.assertIsNone(record)
        self.assertTrue(
            any(
                "staged evidence payload changed after validation" in error
                for error in errors
            ),
            errors,
        )
        self.assertFalse(output.exists())
        self.assertFalse((self.build / ".v002-evidence-staging").exists())

    def test_shared_raw_log_uses_one_immutable_snapshot_per_validation_phase(
        self,
    ) -> None:
        session = self.ready_session()
        target = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        target_absolute = target.absolute()
        target_reads = 0
        reads_at_validation_entry: list[int] = []

        def count_target_reads(path: Path, maximum: int, label: str) -> bytes:
            nonlocal target_reads
            if path.absolute() == target_absolute:
                target_reads += 1
            return read_bounded_bytes(path, maximum, label)

        def validate_after_collection(*args, **kwargs):
            reads_at_validation_entry.append(target_reads)
            return validate_bundle(*args, **kwargs)

        with patch(
            "scripts.collect_v002_manual_evidence.read_bounded_bytes",
            side_effect=count_target_reads,
        ), patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=validate_after_collection,
        ):
            errors, _, output = self.collect(
                session, "single-raw-log-snapshot"
            )

        self.assertEqual([], errors)
        self.assertTrue(output.is_dir())
        self.assertEqual([1], reads_at_validation_entry)
        self.assertEqual(2, target_reads)

    def test_raw_log_change_between_snapshot_phases_is_rejected(self) -> None:
        session = self.ready_session()
        target = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        target_absolute = target.absolute()
        original = target.read_bytes()
        changed = original + (
            b"[Render thread/ERROR] [advancedrocketrycommunity/Test]: "
            b"changed between snapshots\n"
        )
        target_reads = 0

        def change_after_collection(path: Path, maximum: int, label: str) -> bytes:
            nonlocal target_reads
            if path.absolute() == target_absolute:
                target_reads += 1
                return original if target_reads == 1 else changed
            return read_bounded_bytes(path, maximum, label)

        with patch(
            "scripts.collect_v002_manual_evidence.read_bounded_bytes",
            side_effect=change_after_collection,
        ):
            errors, record, output = self.collect(
                session, "changed-raw-log-snapshot"
            )

        self.assertIsNone(record)
        self.assertTrue(
            any("raw log source no longer matches its audit" in error for error in errors),
            errors,
        )
        self.assertEqual(2, target_reads)
        self.assertFalse(output.exists())

    def test_staged_committed_validation_rechecks_raw_inputs_before_publish(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        client_log = self.root / session["log_excerpts"]["client_startup_world"][
            "source"
        ]
        modes: list[str] = []
        changed = False

        def mutate_after_committed_check(*args, **kwargs):
            nonlocal changed
            mode = kwargs["_validation_mode"]
            modes.append(mode)
            result = validate_bundle(*args, **kwargs)
            if mode == "committed" and not changed:
                changed = True
                client_log.write_text(
                    client_log.read_text(encoding="utf-8")
                    + "[Render thread/ERROR] [minecraft/Test]: changed during collect\n",
                    encoding="utf-8",
                )
            return result

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_after_committed_check,
        ):
            errors, record = collect_evidence(
                self.write_session(session, "staged-committed-session.json"),
                output,
                self.root,
                require_acceptance_ready=True,
            )

        self.assertIsNone(record)
        self.assertTrue(any("raw log source no longer matches" in error for error in errors), errors)
        self.assertEqual(["committed", "build"], modes)
        self.assertFalse(output.exists())
        self.assertFalse((self.build / ".v002-evidence-staging").exists())

    def test_staged_committed_validation_rechecks_profile_inventory(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        missing_game = self.root / session["client_profiles"]["missing_mod"][
            "game_directory"
        ]
        modes: list[str] = []
        changed = False

        def mutate_profile_after_committed_check(*args, **kwargs):
            nonlocal changed
            mode = kwargs["_validation_mode"]
            modes.append(mode)
            result = validate_bundle(*args, **kwargs)
            if mode == "committed" and not changed:
                changed = True
                (missing_game / "mods" / "late.jar").write_bytes(b"late mutation")
            return result

        with patch(
            "scripts.collect_v002_manual_evidence.validate_bundle",
            side_effect=mutate_profile_after_committed_check,
        ):
            errors, record = collect_evidence(
                self.write_session(session, "staged-profile-session.json"),
                output,
                self.root,
                require_acceptance_ready=True,
            )

        self.assertIsNone(record)
        self.assertTrue(any("must be empty" in error for error in errors), errors)
        self.assertEqual(["committed", "build"], modes)
        self.assertFalse(output.exists())
        self.assertFalse((self.build / ".v002-evidence-staging").exists())
