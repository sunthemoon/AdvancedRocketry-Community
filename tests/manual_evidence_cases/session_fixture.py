from pathlib import Path
from scripts.collect_v002_manual_evidence import bind_player_identity
from scripts.collect_v002_manual_evidence import build_template
from scripts.collect_v002_manual_evidence import create_profile_snapshot
from scripts.run_dedicated_server_smoke import SERVER_PROPERTIES_IDENTITY_FILE
from scripts.run_dedicated_server_smoke import server_configuration_payload as smoke_server_configuration_payload
from tests.manual_evidence_cases.png import make_png
import hashlib
import json


class SessionFixture:
    """Construct the canonical physical manual session."""

    def ready_session(self) -> dict[str, object]:
        session = build_template(self.artifact_name)
        session["metadata"] = {
            "source_commit": self.source_commit,
            "test_date": "2026-08-27",
            "tester_id": "external-tester-01",
            "environment": {
                "os": "Windows 11 test VM",
                "java": "17.0.16",
                "minecraft": "1.20.1",
                "forge": "47.4.10",
            },
        }
        session["artifacts"] = {
            role: path.relative_to(self.root).as_posix()
            for role, path in self.jar_paths.items()
        }
        session["privacy"] = {
            "player_names": ["SecretPlayer"],
            "visual_review": {
                "completed": True,
                "reviewed_by": "privacy-reviewer-01",
                "reviewed_at": "2026-08-27",
                "notes": "Reviewed every full-window capture for account and desktop data.",
            },
        }
        for observation in session["observations"].values():
            observation["outcome"] = "PASS"
            observation["actual"] = "Observed the expected packaged behavior."

        for seed, (role, item) in enumerate(session["evidence"].items()):
            screenshot = self.build / "capture" / f"{role}.png"
            screenshot.parent.mkdir(parents=True, exist_ok=True)
            screenshot.write_bytes(make_png(seed=seed))
            item.update(
                {
                    "status": "PRESENT",
                    "source": screenshot.relative_to(self.root).as_posix(),
                    "note": "Full Minecraft window; pixel content manually reviewed.",
                }
            )
        matching_game = self.jar_paths["client"].parent.parent
        missing_game = self.build / "missing-client"
        (missing_game / "mods").mkdir(parents=True)
        snapshots = self.build / "profile-snapshots"
        snapshots.mkdir()
        profile_snapshot_paths: dict[tuple[str, str], Path] = {}
        for role, game_directory in (
            ("matching", matching_game),
            ("missing_mod", missing_game),
        ):
            path = snapshots / f"{role}-before.json"
            create_profile_snapshot(
                game_directory,
                path,
                profile_role=role,
                phase="before",
                repository_root=self.root,
            )
            document = json.loads(path.read_text(encoding="utf-8"))
            document["captured_at"] = (
                "2026-08-27T11:00:00+00:00"
                if role == "matching"
                else "2026-08-27T12:12:00+00:00"
            )
            path.write_text(
                json.dumps(document, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
                newline="\n",
            )
            profile_snapshot_paths[(role, "before")] = path

        matching_client_log = matching_game / "logs" / "latest.log"
        matching_client_log.parent.mkdir(parents=True)
        matching_client_log.write_text(
            "SecretPlayer initialized from C:\\Users\\private user\\instance "
            "with UUID 123e4567-e89b-42d3-a456-426614174000\n"
            "remote 203.0.113.8 Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz\n"
            "matching connection to 127.0.0.1\n"
            "matching client entered the world\n",
            encoding="utf-8",
        )
        mismatch_client_log = missing_game / "logs" / "latest.log"
        mismatch_client_log.parent.mkdir(parents=True)
        mismatch_client_log.write_text(
            "missing-mod indicator observed\n"
            "[Render thread/INFO] [minecraft/ConnectScreen]: "
            "Connecting to 127.0.0.1, 25565\n"
            "missing-mod connection result recorded\n",
            encoding="utf-8",
        )
        for role, game_directory in (
            ("matching", matching_game),
            ("missing_mod", missing_game),
        ):
            path = snapshots / f"{role}-after.json"
            create_profile_snapshot(
                game_directory,
                path,
                profile_role=role,
                phase="after",
                repository_root=self.root,
            )
            document = json.loads(path.read_text(encoding="utf-8"))
            document["captured_at"] = (
                "2026-08-27T12:11:00+00:00"
                if role == "matching"
                else "2026-08-27T12:13:00+00:00"
            )
            path.write_text(
                json.dumps(document, indent=2, sort_keys=True) + "\n",
                encoding="utf-8",
                newline="\n",
            )
            profile_snapshot_paths[(role, "after")] = path
            session["client_profiles"][role] = {
                "status": "PRESENT",
                "game_directory": game_directory.relative_to(self.root).as_posix(),
                "before_snapshot": profile_snapshot_paths[
                    (role, "before")
                ].relative_to(self.root).as_posix(),
                "after_snapshot": path.relative_to(self.root).as_posix(),
                "note": "Canonical before/after profile inventories captured.",
            }
        server_root = self.jar_paths["server"].parent.parent
        first_log = server_root / "first-start-full.txt"
        first_log.write_text(
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n",
            encoding="utf-8",
        )
        restart_log = server_root / "restart-full.txt"
        restart_log.write_text(
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Done (1.00s)! For help, type help\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer joined the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: "
            "SecretPlayer left the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n",
            encoding="utf-8",
        )
        mismatch_payload = (
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Starting Minecraft server on 127.0.0.1:25565\n"
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Preparing level \"world\"\n"
            "[Server thread/INFO] [minecraft/DedicatedServer]: "
            "Done (1.00s)! For help, type help\n"
            "[Netty Server IO #1/INFO] "
            "[net.minecraftforge.server.ServerLifecycleHooks/SERVERHOOKS]: "
            "Disconnecting VANILLA connection attempt from isolated client\n"
            "Missing-mod connection attempt was rejected\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Saved the game\n"
            "[Server thread/INFO] [minecraft/MinecraftServer]: Stopping server\n"
        )
        runtime_log = server_root / "logs" / "latest.log"
        runtime_log.parent.mkdir(parents=True)
        runtime_log.write_text(mismatch_payload, encoding="utf-8")
        mismatch_log = self.build / "mismatch-server-full.txt"
        mismatch_log.write_text(mismatch_payload, encoding="utf-8")
        mismatch_receipt = self.build / "mismatch-server-receipt.json"
        mismatch_receipt.write_text("{}\n", encoding="utf-8", newline="\n")
        server_properties_payload = smoke_server_configuration_payload(25565, True)
        (server_root / "server.properties").write_bytes(server_properties_payload)
        (server_root / SERVER_PROPERTIES_IDENTITY_FILE).write_bytes(
            server_properties_payload
        )
        server_properties_sha256 = hashlib.sha256(
            server_properties_payload
        ).hexdigest()
        log_inputs = {
            "client_startup_world": (matching_client_log, 1, 2),
            "matching_client_connection": (matching_client_log, 3, 4),
            "server_first_join_leave_save_stop": (first_log, 1, 4),
            "server_restart_reconnect_save_stop": (restart_log, 1, 5),
            "mismatch_attempt": (mismatch_client_log, 1, 3),
            "mismatch_server_attempt_save_stop": (mismatch_log, 1, 7),
        }
        for role, item in session["log_excerpts"].items():
            source, line_start, line_end = log_inputs[role]
            item.update(
                {
                    "status": "PRESENT",
                    "source": source.relative_to(self.root).as_posix(),
                    "line_start": line_start,
                    "line_end": line_end,
                    "note": "Selected lifecycle lines only.",
                    "warning_disposition": {
                        "status": "NONE",
                        "warning_count": 0,
                        "origins": [],
                        "explanation": "",
                    },
                }
            )
            if role == "mismatch_server_attempt_save_stop":
                item["server_exit_code"] = 0
                item["receipt"] = mismatch_receipt.relative_to(
                    self.root
                ).as_posix()

        session_id = "v002-" + "a" * 24
        player_binding = bind_player_identity(b"\x17" * 32, "SecretPlayer")
        cycle_base = {
            "error_count": 0,
            "warning_count": 0,
            "fatal_count": 0,
            "project_error_count": 0,
            "project_warning_count": 0,
            "project_fatal_count": 0,
            "client_linkage_failure_count": 0,
            "exit_code": 0,
            "mod_marker": "1.20.1-0.0.2-dev",
            "player_join_observed": True,
            "player_identity_binding": player_binding,
            "player_leave_observed": True,
            "status_protocol": 763,
            "status_version": "1.20.1",
        }
        world_before_sha256 = "0" * 64
        world_identity = hashlib.sha256(
            (
                f"{session_id}\0{self.artifact_hash}\0{world_before_sha256}\0"
                f"{server_properties_sha256}"
            ).encode("utf-8")
        ).hexdigest()
        summary = {
            "schema_version": 4,
            "session_id": session_id,
            "artifact": self.artifact_name,
            "artifact_sha256": self.artifact_hash,
            "completed_at": "2026-08-27T12:10:00+00:00",
            "cycles": [
                {
                    **cycle_base,
                    "completed_at": "2026-08-27T12:04:00+00:00",
                    "cycle_id": f"{session_id}-first-start",
                    "full_log_file": "first-start-full.txt",
                    "full_log_sha256": hashlib.sha256(first_log.read_bytes()).hexdigest(),
                    "name": "first-start",
                    "started_at": "2026-08-27T12:01:00+00:00",
                },
                {
                    **cycle_base,
                    "completed_at": "2026-08-27T12:09:00+00:00",
                    "cycle_id": f"{session_id}-restart",
                    "full_log_file": "restart-full.txt",
                    "full_log_sha256": hashlib.sha256(restart_log.read_bytes()).hexdigest(),
                    "name": "restart",
                    "started_at": "2026-08-27T12:05:00+00:00",
                },
            ],
            "forge": "47.4.10",
            "installer_sha1": "b" * 40,
            "installer_sha256": "c" * 64,
            "installer_attempts": 1,
            "java": "17.0.16",
            "manual_player_cycles": True,
            "minecraft": "1.20.1",
            "offline_mode": True,
            "platform": "Windows 11 test VM",
            "server_artifact_sha256": self.artifact_hash,
            "server_bind": "127.0.0.1",
            "server_port": 25565,
            "same_player_verified": True,
            "started_at": "2026-08-27T12:00:00+00:00",
            "world": {
                "identity": world_identity,
                "identity_marker": "world/.v002-smoke-world-identity.json",
                "identity_marker_sha256": "",
                "level_dat_after_restart_sha256": "f" * 64,
                "level_dat_after_restart_size": 2048,
                "level_dat_before_restart_sha256": world_before_sha256,
                "level_dat_before_restart_size": 1024,
                "level_name": "world",
                "same_world_verified": True,
                "server_properties_sha256": server_properties_sha256,
            },
            "world_level_dat": True,
        }
        world_marker = server_root / "world" / ".v002-smoke-world-identity.json"
        world_marker.parent.mkdir()
        world_marker.write_text(
            json.dumps(
                {
                    "artifact_sha256": self.artifact_hash,
                    "server_properties_sha256": server_properties_sha256,
                    "session_id": session_id,
                    "world_identity": summary["world"]["identity"],
                },
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
        )
        summary["world"]["identity_marker_sha256"] = hashlib.sha256(
            world_marker.read_bytes()
        ).hexdigest()
        summary_path = self.build / "server-evidence" / "summary.json"
        summary_path.parent.mkdir()
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n", encoding="utf-8"
        )
        session["server_harness"] = {
            "status": "PRESENT",
            "summary": summary_path.relative_to(self.root).as_posix(),
            "note": "Harness-generated matching-client cycles.",
        }
        self.refresh_mismatch_receipt(session)
        session["findings"] = {
            "client_project_error_count": 0,
            "client_project_warning_count": 0,
            "server_project_error_count": 0,
            "server_project_warning_count": 0,
            "client_class_linkage_failure_count": 0,
            "notes": "",
        }
        for review in session["applicability_reviews"].values():
            review.update(
                {
                    "decision": "ACCEPT_NOT_APPLICABLE",
                    "reviewed_by": "scope-reviewer-01",
                    "reviewed_at": "2026-08-27",
                    "notes": "Accepted only for the v0.0.2 empty bootstrap scope.",
                }
            )
        return session
