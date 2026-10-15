from datetime import datetime
from pathlib import Path
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import read_bounded_bytes
from scripts.collect_v002_manual_evidence import validate_bundle
from scripts.run_dedicated_server_smoke import expected_server_properties
from unittest.mock import patch
import hashlib
import json
import os


class FixtureOperations:
    """Mutate and collect per-case evidence fixtures."""

    def write_session(self, session: dict[str, object], name: str = "session.json") -> Path:
        path = self.build / name
        path.write_text(
            json.dumps(session, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        return path

    def refresh_summary_log_hash(
        self,
        session: dict[str, object],
        cycle_name: str,
        log_path: Path,
    ) -> None:
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        cycle = next(item for item in summary["cycles"] if item["name"] == cycle_name)
        cycle["full_log_sha256"] = hashlib.sha256(log_path.read_bytes()).hexdigest()
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )

    def set_warning_disposition(
        self,
        session: dict[str, object],
        roles: tuple[str, ...],
        *,
        count: int,
        status: str = "UNRESOLVED",
    ) -> None:
        for role in roles:
            session["log_excerpts"][role]["warning_disposition"] = {
                "status": status,
                "warning_count": count,
                "origins": ["Test logger"],
                "explanation": "Preserved warning disposition for the rejection-path test.",
            }

    def refresh_mismatch_receipt(
        self,
        session: dict[str, object],
        *,
        exit_code: int | None = None,
    ) -> None:
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        if exit_code is not None:
            item["server_exit_code"] = exit_code
        source = self.root / item["source"]
        receipt = self.root / item["receipt"]
        summary_path = self.root / session["server_harness"]["summary"]
        summary_payload = summary_path.read_bytes()
        summary = json.loads(summary_payload.decode("utf-8"))
        active_properties = (
            self.jar_paths["server"].parent.parent / "server.properties"
        ).read_bytes()
        started_at = "2026-08-27T12:12:00+00:00"
        completed_at = "2026-08-27T12:13:00+00:00"
        full_log_sha256 = hashlib.sha256(source.read_bytes()).hexdigest()
        receipt.write_text(
            json.dumps(
                {
                    "schema_version": 2,
                    "run_id": "v002-mismatch-" + "b" * 24,
                    "session_id": summary["session_id"],
                    "harness_summary_sha256": hashlib.sha256(
                        summary_payload
                    ).hexdigest(),
                    "harness_cycle_log_sha256": {
                        cycle["name"]: cycle["full_log_sha256"]
                        for cycle in summary["cycles"]
                    },
                    "started_at": started_at,
                    "completed_at": completed_at,
                    "duration_millis": 60_000,
                    "java_version": "17.0.16",
                    "exit_code": item["server_exit_code"],
                    "previous_runtime_log_sha256": "d" * 64,
                    "full_log_sha256": full_log_sha256,
                    "server_artifact_sha256": summary[
                        "server_artifact_sha256"
                    ],
                    "active_server_properties_sha256": hashlib.sha256(
                        active_properties
                    ).hexdigest(),
                    "critical_server_properties": {
                        key: value
                        for key, value in sorted(
                            expected_server_properties(
                                summary["server_port"], True
                            ).items()
                        )
                    },
                    "server_mods_files": [
                        {
                            "filename": self.artifact_name,
                            "sha256": summary["server_artifact_sha256"],
                        }
                    ],
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        runtime = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        timestamp = datetime.fromisoformat(completed_at).timestamp()
        os.utime(runtime, (timestamp, timestamp))

    def replace_mismatch_server_log(
        self,
        session: dict[str, object],
        payload: str,
    ) -> None:
        role = "mismatch_server_attempt_save_stop"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        source.write_text(payload, encoding="utf-8")
        runtime = self.jar_paths["server"].parent.parent / "logs" / "latest.log"
        runtime.write_text(payload, encoding="utf-8")
        item["line_start"] = 1
        item["line_end"] = len(payload.splitlines())
        self.refresh_mismatch_receipt(session)

    def replace_client_connection_marker(
        self,
        session: dict[str, object],
        replacement: str,
    ) -> None:
        role = "mismatch_attempt"
        item = session["log_excerpts"][role]
        source = self.root / item["source"]
        lines = source.read_text(encoding="utf-8").splitlines()
        marker_index = next(
            index for index, line in enumerate(lines) if "Connecting to" in line
        )
        lines[marker_index] = replacement
        source.write_text("\n".join(lines) + "\n", encoding="utf-8")

    def collect(
        self, session: dict[str, object], output_name: str = "bundle"
    ) -> tuple[list[str], dict[str, object] | None, Path]:
        output = self.build / output_name
        errors, record = collect_evidence(
            self.write_session(session, f"{output_name}-session.json"),
            output,
            self.root,
        )
        return errors, record, output

    def assert_bundle_parent_link_rejected(self, parent_name: str) -> None:
        session = self.ready_session()
        errors, _, output = self.collect(
            session, f"linked-{parent_name}-parent-bundle"
        )
        self.assertEqual([], errors)
        parent = output / parent_name
        outside = self.build / f"outside-{parent_name}-archive-parent"
        parent.rename(outside)
        try:
            parent.symlink_to(outside, target_is_directory=True)
        except OSError as exc:
            outside.rename(parent)
            self.skipTest(f"directory symlinks unavailable: {exc}")

        lexical_parent = str(parent.absolute()).casefold() + os.sep

        def reject_read_through_parent(
            path: Path, maximum: int, label: str
        ) -> bytes:
            if str(path.absolute()).casefold().startswith(lexical_parent):
                raise AssertionError(
                    f"validator read {path} before rejecting linked parent"
                )
            return read_bounded_bytes(path, maximum, label)

        with patch(
            "scripts.collect_v002_manual_evidence.read_bounded_bytes",
            side_effect=reject_read_through_parent,
        ):
            validation_errors, _ = validate_bundle(output, self.root)

        self.assertTrue(
            any(
                "symlink or junction/reparse point" in error
                for error in validation_errors
            ),
            validation_errors,
        )
