"""Lifecycle and helpers for the packet review facade."""
import json
import os
import re
import shutil
import subprocess
import tempfile
from pathlib import Path
from unittest.mock import patch
import scripts.prepare_v002_g0_review_packet as packet_module
import scripts.validate_bootstrap_provenance as validator_module
from scripts.prepare_v002_g0_review_packet import (
    MANIFEST_NAME,
    PROVENANCE_MANIFEST,
    PROVENANCE_RECORD,
    THIRD_PARTY_NOTICE,
    TOOL_DEFINITIONS,
    _canonical_json,
    generate_packet,
    verify_packet,
    verify_packet_content_only,
)
from scripts.validate_bootstrap_provenance import compute_review_content_sha256


class PacketReviewFixtureMixin:
    @classmethod
    def setUpClass(cls) -> None:
        cls.source_root = Path(__file__).resolve().parents[1]
        cls.class_temporary = tempfile.TemporaryDirectory()
        cls.seed_root = Path(cls.class_temporary.name) / "seed"
        # Materialize only the historical fixture checked out below, not current HEAD.
        cls.run_command(
            [
                "git",
                "clone",
                "--quiet",
                "--shared",
                "--no-checkout",
                "--",
                str(cls.source_root),
                str(cls.seed_root),
            ]
        )
        cls.seed_git("config", "user.name", "G0 Packet Fixture")
        cls.seed_git("config", "user.email", "g0-packet@example.invalid")
        cls.seed_git("config", "core.autocrlf", "false")
        cls.seed_git("config", "core.filemode", "false")
        cls.seed_git(
            "checkout",
            "--quiet",
            "--detach",
            validator_module.HISTORICAL_V002_RECORD_COMMIT,
        )
        cls.ensure_pending_seed_state()

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

    @classmethod
    def ensure_pending_seed_state(cls) -> None:
        """Keep pending-workflow tests independent of the real review lifecycle."""

        manifest_path = cls.seed_root / PROVENANCE_MANIFEST
        document = json.loads(manifest_path.read_text(encoding="utf-8"))
        status = document["review"]["record_status"]
        if status == "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING":
            return
        if status != "THIRD_PARTY_APPROVED":
            raise AssertionError(f"unsupported real provenance review state: {status}")

        document["review"] = {
            "record_status": "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING",
            "reviewer": None,
            "reviewed_at": None,
            "final_status_after_review": None,
            "reviewed_audited_target_commit": None,
            "reviewed_content_sha256": None,
        }
        for target in document["targets"]:
            target["status"] = "PENDING_HUMAN_REVIEW"
            target["proposed_status_after_review"] = "THIRD_PARTY_APPROVED"

        record_path = cls.seed_root / PROVENANCE_RECORD
        notice_path = cls.seed_root / THIRD_PARTY_NOTICE
        record = record_path.read_text(encoding="utf-8")
        notice = notice_path.read_text(encoding="utf-8")

        def replace_exact(
            text: str, pattern: str, replacement: str, expected: int, label: str
        ) -> str:
            updated, count = re.subn(pattern, replacement, text, flags=re.MULTILINE)
            if count != expected:
                raise AssertionError(
                    f"pending fixture {label} replacement count {count} != {expected}"
                )
            return updated

        for field, value in (
            ("record_status", "EVIDENCE_COMPLETE_HUMAN_REVIEW_PENDING"),
            ("final_status_after_review", "null"),
            ("reviewed_audited_target_commit", "null"),
            ("reviewed_content_sha256", "null"),
        ):
            record = replace_exact(
                record,
                rf"^{field}:\s*.*$",
                f"{field}: {value}",
                1,
                field,
            )
        record = replace_exact(
            record, r"^reviewer:\s*.*$", "reviewer: null", 3, "record reviewers"
        )
        record = replace_exact(
            record,
            r"^reviewed_at:\s*.*$",
            "reviewed_at: null",
            3,
            "record review dates",
        )
        record = replace_exact(
            record,
            r"^status:\s*THIRD_PARTY_APPROVED\s*$",
            "status: PENDING_HUMAN_REVIEW",
            2,
            "target statuses",
        )
        record = replace_exact(
            record,
            r"^proposed_status_after_review:\s*null\s*$",
            "proposed_status_after_review: THIRD_PARTY_APPROVED",
            2,
            "target proposed statuses",
        )
        notice = replace_exact(
            notice,
            r"^status:\s*THIRD_PARTY_APPROVED\s*$",
            "status: PENDING_HUMAN_REVIEW",
            1,
            "notice status",
        )
        notice = replace_exact(
            notice, r"^reviewer:\s*.*$", "reviewer: null", 1, "notice reviewer"
        )
        notice = replace_exact(
            notice,
            r"^reviewed_at:\s*.*$",
            "reviewed_at: null",
            1,
            "notice review date",
        )

        manifest_path.write_bytes(_canonical_json(document))
        record_path.write_text(record, encoding="utf-8", newline="\n")
        notice_path.write_text(notice, encoding="utf-8", newline="\n")
        cls.seed_git(
            "add",
            "--",
            PROVENANCE_MANIFEST,
            PROVENANCE_RECORD,
            THIRD_PARTY_NOTICE,
        )

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

    def verify_content_only(self, packet: Path | None = None) -> list[str]:
        return verify_packet_content_only(packet or self.packet)

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
            r"This\s+record\s+does\s+not\s+claim\s+"
            r"binary-distribution\s+notice\s+obligations\s+are\s+complete\.",
            "This fixture records the reviewer's determination about "
            "binary-distribution notice obligations.",
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
        )
        notice = re.sub(
            r"This\s+notice\s+does\s+not\s+claim\s+that\s+"
            r"binary-distribution\s+obligations\s+are\s+complete\.",
            "This fixture records the reviewer's determination about "
            "binary-distribution obligations.",
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
