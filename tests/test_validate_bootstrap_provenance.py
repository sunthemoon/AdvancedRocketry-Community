import copy
import hashlib
import io
import json
import os
import shutil
import subprocess
import sys
import tarfile
import tempfile
import unittest
import zlib
from pathlib import Path
from unittest.mock import patch

import scripts.validate_bootstrap_provenance as validator_module
from scripts.validate_bootstrap_provenance import (
    APPROVED_RECORD_STATUS,
    DEFAULT_MANIFEST,
    EXPECTED_NOTICE_PATH,
    EXPECTED_RECORD_PATH,
    PENDING_RECORD_STATUS,
    REVIEW_DIGEST_DOMAIN,
    compute_review_content_sha256,
    validate_bootstrap_provenance,
    validate_bootstrap_provenance_at_commit,
)
from tests.bootstrap_provenance_fixture import BootstrapProvenanceFixtureMixin
from tests.bootstrap_provenance_cases.fixture_copy import FixtureCopyCases
from tests.bootstrap_provenance_cases.selected_commit import SelectedCommitCases
from tests.bootstrap_provenance_cases.git_objects import GitObjectCases
from tests.bootstrap_provenance_cases.input_bounds import InputBoundsCases
from tests.bootstrap_provenance_cases.selected_snapshots import SelectedSnapshotCases
from tests.bootstrap_provenance_cases.cli import CliCases
from tests.bootstrap_provenance_cases.pending_records import PendingRecordCases
from tests.bootstrap_provenance_cases.history import HistoryCases
from tests.bootstrap_provenance_cases.tree_metadata import TreeMetadataCases
from tests.bootstrap_provenance_cases.manifest_resources import ManifestResourceCases
from tests.bootstrap_provenance_cases.approved_records import ApprovedRecordCases


class BootstrapProvenanceValidationTests(
    BootstrapProvenanceFixtureMixin,
    FixtureCopyCases,
    SelectedCommitCases,
    GitObjectCases,
    InputBoundsCases,
    SelectedSnapshotCases,
    CliCases,
    PendingRecordCases,
    HistoryCases,
    TreeMetadataCases,
    ManifestResourceCases,
    ApprovedRecordCases,
    unittest.TestCase,
):
    pass


class RealBootstrapProvenanceValidationTests(unittest.TestCase):
    def test_real_historical_repository_record_validates_without_presuming_review_state(self) -> None:
        repository_root = Path(__file__).resolve().parents[1]

        errors, details = validate_bootstrap_provenance_at_commit(
            repository_root,
            validator_module.HISTORICAL_V002_RECORD_COMMIT,
        )

        self.assertEqual([], errors)
        self.assertRegex(details["review_content_sha256"], r"^[0-9a-f]{64}$")


if __name__ == "__main__":
    unittest.main()
