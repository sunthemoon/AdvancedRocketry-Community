from pathlib import Path
from scripts.validate_bootstrap_provenance import APPROVED_RECORD_STATUS
from scripts.validate_v002_final_g0_review import APPROVED as FINAL_G0_APPROVED
from scripts.validate_v002_final_g0_review import PENDING as FINAL_G0_PENDING
from unittest.mock import patch
import hashlib
import subprocess
import sys
import tempfile
import unittest
from tests.manual_evidence_fixture import ManualRepositoryFixtureMixin
from tests.manual_evidence_cases.png import make_png, png_chunk
from tests.manual_evidence_cases.session_fixture import SessionFixture
from tests.manual_evidence_cases.fixture_operations import FixtureOperations
from tests.manual_evidence_cases.session import SessionCases
from tests.manual_evidence_cases.profiles import ProfileCases
from tests.manual_evidence_cases.source_logs import SourceLogCases
from tests.manual_evidence_cases.payload_bounds import PayloadBoundsCases
from tests.manual_evidence_cases.server_audits import ServerAuditCases
from tests.manual_evidence_cases.mismatch_receipts import MismatchReceiptCases
from tests.manual_evidence_cases.readiness import ReadinessCases
from tests.manual_evidence_cases.identity_privacy import SourceIdentityCases
from tests.manual_evidence_cases.identity_privacy import PrivacyCases
from tests.manual_evidence_cases.mismatch_runtime import MismatchPropertiesCases
from tests.manual_evidence_cases.mismatch_runtime import MismatchConnectionCases
from tests.manual_evidence_cases.publication import PublicationCases
from tests.manual_evidence_cases.archive_identity import ArchivedPropertiesCases
from tests.manual_evidence_cases.archive_identity import UnsafeParentCases


class CollectorCliTests(unittest.TestCase):
    def test_help_runs_with_isolated_python(self) -> None:
        script = (
            Path(__file__).resolve().parents[1]
            / "scripts"
            / "collect_v002_manual_evidence.py"
        )

        completed = subprocess.run(
            [sys.executable, "-I", "-S", str(script), "--help"],
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="strict",
        )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("profile-snapshot", completed.stdout)


class ManualEvidenceTests(
    SessionCases,
    ProfileCases,
    SourceLogCases,
    PayloadBoundsCases,
    ServerAuditCases,
    MismatchReceiptCases,
    ReadinessCases,
    SourceIdentityCases,
    PrivacyCases,
    MismatchPropertiesCases,
    MismatchConnectionCases,
    PublicationCases,
    ArchivedPropertiesCases,
    UnsafeParentCases,
    SessionFixture,
    FixtureOperations,
    ManualRepositoryFixtureMixin,
    unittest.TestCase,
):
    artifact_name = "advancedrocketry-community-1.20.1-0.0.2-dev.jar"

    def setUp(self) -> None:
        provenance_patcher = patch(
            "scripts.collect_v002_manual_evidence."
            "validate_bootstrap_provenance_at_commit",
            return_value=([], {"review_status": APPROVED_RECORD_STATUS}),
        )
        self.provenance_validator = provenance_patcher.start()
        self.addCleanup(provenance_patcher.stop)
        final_g0_patcher = patch(
            "scripts.collect_v002_manual_evidence."
            "validate_v002_final_g0_review_at_commit",
            return_value=(
                [],
                {
                    "source_review_outcome": FINAL_G0_APPROVED,
                    "readme_review_outcome": FINAL_G0_PENDING,
                },
            ),
        )
        self.final_g0_validator = final_g0_patcher.start()
        self.addCleanup(final_g0_patcher.stop)
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.build = self.root / "build"
        self.build.mkdir()
        self.artifact_content = b"final-distributable-v002"
        self.artifact_hash = hashlib.sha256(self.artifact_content).hexdigest()
        self.source_commit = self.copy_repository_fixture(self.root)
        self.jar_paths: dict[str, Path] = {}
        for role in ("source", "server", "client"):
            role_root = self.build / role
            if role == "client":
                role_root = self.build / "v0.0.2-manual" / "client-matching"
            path = role_root / "mods" / self.artifact_name
            path.parent.mkdir(parents=True)
            path.write_bytes(self.artifact_content)
            self.jar_paths[role] = path


if __name__ == "__main__":
    unittest.main()
