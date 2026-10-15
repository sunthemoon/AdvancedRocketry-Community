from scripts.collect_v002_manual_evidence import COMMITTED_BUNDLE
from scripts.collect_v002_manual_evidence import CONTENT_MANIFEST
from scripts.collect_v002_manual_evidence import MAX_PEM_PRIVATE_KEY_CHARS
from scripts.collect_v002_manual_evidence import bind_player_identity
from scripts.collect_v002_manual_evidence import collect_evidence
from scripts.collect_v002_manual_evidence import extract_log_excerpt
from scripts.collect_v002_manual_evidence import privacy_findings
from scripts.collect_v002_manual_evidence import redact_text
from scripts.collect_v002_manual_evidence import validate_bundle
import hashlib
import hmac
import json
import subprocess


class SourceIdentityCases:
    """Source commit and checkout identity."""

    def test_dirty_worktree_is_rejected_before_collection(self) -> None:
        session = self.ready_session()
        (self.root / ".gitignore").write_bytes(b"/build/\n/local-only/\n")

        errors, _, output = self.collect(session)

        self.assertTrue(any("clean tracked/untracked worktree" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_source_commit_must_equal_existing_checkout_head(self) -> None:
        session = self.ready_session()
        session["metadata"]["source_commit"] = "a" * 40

        errors, _, output = self.collect(session)

        self.assertTrue(any("rev-parse" in error or "source commit" in error for error in errors), errors)
        self.assertFalse(output.exists())

    def test_committed_bundle_validates_original_source_commit_after_head_moves(self) -> None:
        session = self.ready_session()
        output = self.root / COMMITTED_BUNDLE
        errors, _ = collect_evidence(
            self.write_session(session, "source-revision-session.json"),
            output,
            self.root,
        )
        self.assertEqual([], errors)
        (self.root / "README.md").write_bytes(b"# Later documentation commit\n")
        manifest_path = self.root / CONTENT_MANIFEST
        manifest_path.write_text(
            json.dumps(
                {
                    "schema_version": 1,
                    "artifact": "future-artifact.jar",
                    "artifact_sha256": "1" * 64,
                    "entry_count": 0,
                    "entries": [],
                },
                indent=2,
                sort_keys=True,
            )
            + "\n",
            encoding="utf-8",
            newline="\n",
        )
        subprocess.run(
            ["git", "add", "README.md", CONTENT_MANIFEST.as_posix()],
            cwd=self.root,
            check=True,
        )
        subprocess.run(
            [
                "git",
                "-c",
                "user.name=Evidence Fixture",
                "-c",
                "user.email=evidence@example.invalid",
                "commit",
                "-q",
                "-m",
                "later docs",
            ],
            cwd=self.root,
            check=True,
        )

        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )

        self.assertEqual([], validation_errors)


class PrivacyCases:
    """Private identity and credential redaction."""

    def test_player_identity_binding_uses_private_hmac_protocol(self) -> None:
        secret = b"private-fixture-secret-material!" + b"x" * 8
        expected = hmac.new(
            secret,
            b"v0.0.2-player-identity\0secretplayer",
            hashlib.sha256,
        ).hexdigest()

        actual = bind_player_identity(secret, "SecretPlayer")

        self.assertEqual(expected, actual)
        public_session_digest = hashlib.sha256(
            b"v002-aaaaaaaaaaaaaaaaaaaaaaaa\0secretplayer"
        ).hexdigest()
        self.assertNotEqual(public_session_digest, actual)
        with self.assertRaisesRegex(ValueError, "at least 32 bytes"):
            bind_player_identity(b"short", "SecretPlayer")

    def test_collector_treats_private_player_binding_as_opaque(self) -> None:
        session = self.ready_session()
        summary_path = self.root / session["server_harness"]["summary"]
        summary = json.loads(summary_path.read_text(encoding="utf-8"))
        for cycle in summary["cycles"]:
            cycle["player_identity_binding"] = "9" * 64
        summary_path.write_text(
            json.dumps(summary, indent=2, sort_keys=True) + "\n",
            encoding="utf-8",
        )
        self.refresh_mismatch_receipt(session)

        errors, record, _ = self.collect(session, "opaque-binding")

        self.assertEqual([], errors)
        assert record is not None
        bindings = {
            record["log_excerpts"][role]["player_identity_binding"]
            for role in (
                "server_first_join_leave_save_stop",
                "server_restart_reconnect_save_stop",
            )
        }
        self.assertEqual({"9" * 64}, bindings)

    def test_windows_home_with_spaces_is_fully_redacted(self) -> None:
        source = self.build / "logs" / "home-space.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "loaded C:\\Users\\Private Test User\\isolated-instance\\options.txt\n",
            encoding="utf-8",
        )

        payload, counts = extract_log_excerpt(source, 1, 1, [])
        text = payload.decode("utf-8")

        self.assertEqual(1, counts["home"])
        self.assertIn("[REDACTED_HOME]", text)
        self.assertNotIn("Private Test User", text)
        self.assertNotIn("C:\\Users", text)

    def test_complete_pem_private_key_is_fully_redacted_with_line_count(self) -> None:
        source = self.build / "logs" / "private-key.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "-----BEGIN PRIVATE KEY-----\n"
            "SUPER_SECRET_BASE64_BODY\n"
            "-----END PRIVATE KEY-----\n",
            encoding="utf-8",
            newline="\n",
        )

        payload, counts = extract_log_excerpt(source, 1, 3, [])
        text = payload.decode("utf-8")

        self.assertEqual(3, len(text.splitlines()))
        self.assertEqual(1, counts["credential"])
        self.assertIn("[REDACTED_CREDENTIAL]", text)
        self.assertNotIn("SUPER_SECRET_BASE64_BODY", text)
        self.assertNotIn("BEGIN PRIVATE KEY", text)
        self.assertNotIn("END PRIVATE KEY", text)
        self.assertEqual([], privacy_findings(text))

    def test_partial_pem_body_selection_cannot_escape_redaction(self) -> None:
        source = self.build / "logs" / "partial-private-key.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "-----BEGIN RSA PRIVATE KEY-----\n"
            "PARTIAL_SELECTION_SECRET\n"
            "-----END RSA PRIVATE KEY-----\n",
            encoding="utf-8",
            newline="\n",
        )

        payload, counts = extract_log_excerpt(source, 2, 2, [])
        text = payload.decode("utf-8")

        self.assertEqual("\n", text)
        self.assertEqual(1, counts["credential"])
        self.assertNotIn("PARTIAL_SELECTION_SECRET", text)

    def test_incomplete_pem_private_key_is_rejected(self) -> None:
        source = self.build / "logs" / "incomplete-private-key.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "-----BEGIN OPENSSH PRIVATE KEY-----\n"
            "INCOMPLETE_SECRET_BODY\n",
            encoding="utf-8",
            newline="\n",
        )

        with self.assertRaisesRegex(ValueError, "incomplete or oversized PEM"):
            extract_log_excerpt(source, 1, 2, [])

    def test_oversized_pem_private_key_is_rejected(self) -> None:
        source = self.build / "logs" / "oversized-private-key.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "-----BEGIN PRIVATE KEY-----\n"
            + ("A" * (MAX_PEM_PRIVATE_KEY_CHARS + 1))
            + "\n-----END PRIVATE KEY-----\n",
            encoding="utf-8",
            newline="\n",
        )

        with self.assertRaisesRegex(ValueError, "incomplete or oversized PEM"):
            extract_log_excerpt(source, 1, 3, [])

    def test_excessive_pem_private_key_blocks_are_rejected(self) -> None:
        source = self.build / "logs" / "too-many-private-keys.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        block = (
            "-----BEGIN PRIVATE KEY-----\n"
            "SECRET_BODY\n"
            "-----END PRIVATE KEY-----\n"
        )
        source.write_text(block * 17, encoding="utf-8", newline="\n")

        with self.assertRaisesRegex(ValueError, "too many PEM private-key blocks"):
            extract_log_excerpt(source, 1, 51, [])

    def test_public_key_pem_and_plain_private_key_words_are_not_redacted(self) -> None:
        text = (
            "-----BEGIN PUBLIC KEY-----\n"
            "PUBLIC_MATERIAL\n"
            "-----END PUBLIC KEY-----\n"
            "Documentation discusses PRIVATE KEY handling without PEM markers.\n"
        )

        redacted, counts = redact_text(text, [])

        self.assertEqual(text, redacted)
        self.assertEqual(0, counts["credential"])
        self.assertEqual([], privacy_findings(redacted))

    def test_launcher_argument_credentials_are_redacted_in_strict_bundle(self) -> None:
        session = self.ready_session()
        item = session["log_excerpts"]["client_startup_world"]
        source = self.root / item["source"]
        with source.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write(
                "ModLauncher args [--accessToken, ACCESS_TOKEN_PRIVATE, "
                "--clientId CLIENT_ID_PRIVATE, --xuid, XUID_PRIVATE]\n"
            )
        item["line_start"] = 5
        item["line_end"] = 5

        errors, record, output = self.collect(session, "launcher-secret-strict")
        self.assertEqual([], errors)
        assert record is not None
        archived = (output / "logs" / "client_startup_world.txt").read_text(
            encoding="utf-8"
        )
        for secret in (
            "ACCESS_TOKEN_PRIVATE",
            "CLIENT_ID_PRIVATE",
            "XUID_PRIVATE",
        ):
            self.assertNotIn(secret, archived)
        self.assertGreaterEqual(archived.count("[REDACTED_CREDENTIAL]"), 3)
        self.assertGreaterEqual(
            record["log_excerpts"]["client_startup_world"]["redaction_counts"][
                "credential"
            ],
            3,
        )
        validation_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertEqual([], validation_errors)

    def test_launcher_argument_credentials_are_redacted_in_blocked_bundle(self) -> None:
        session = self.ready_session()
        session["observations"]["MANUAL-V002-001"].update(
            outcome="BLOCKED",
            actual="Client startup observation remained blocked.",
        )
        item = session["log_excerpts"]["client_startup_world"]
        source = self.root / item["source"]
        with source.open("a", encoding="utf-8", newline="\n") as stream:
            stream.write("args --access_token BLOCKED_PRIVATE_TOKEN\n")
        item["line_start"] = 5
        item["line_end"] = 5

        errors, _, output = self.collect(session, "launcher-secret-blocked")

        self.assertEqual([], errors)
        archived = (output / "logs" / "client_startup_world.txt").read_text(
            encoding="utf-8"
        )
        self.assertNotIn("BLOCKED_PRIVATE_TOKEN", archived)
        self.assertIn("[REDACTED_CREDENTIAL]", archived)
        default_errors, _ = validate_bundle(output, self.root)
        strict_errors, _ = validate_bundle(
            output, self.root, require_acceptance_ready=True
        )
        self.assertEqual([], default_errors)
        self.assertTrue(any("not PASS" in error for error in strict_errors), strict_errors)

    def test_launcher_credential_regex_does_not_match_similar_public_labels(self) -> None:
        text = (
            "docs accessToken, public-label --accessTokenization, public-label "
            "--clientIdentity public-label xuid-public-label\n"
        )

        redacted, counts = redact_text(text, [])

        self.assertEqual(text, redacted)
        self.assertEqual(0, counts["credential"])
        self.assertEqual([], privacy_findings(redacted))

    def test_oversized_launcher_credential_is_rejected_not_partially_redacted(self) -> None:
        source = self.build / "logs" / "oversized-launcher-secret.log"
        source.parent.mkdir(parents=True, exist_ok=True)
        source.write_text(
            "--accessToken, " + ("a" * 4097) + "\n",
            encoding="utf-8",
            newline="\n",
        )

        with self.assertRaisesRegex(ValueError, "credential-like value"):
            extract_log_excerpt(source, 1, 1, [])
