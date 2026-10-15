import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
import unittest
import zlib
from pathlib import Path, PurePosixPath
from unittest.mock import patch

import scripts.prepare_v002_g0_review_packet as packet_module
import scripts.validate_bootstrap_provenance as validator_module
from scripts.prepare_v002_g0_review_packet import (
    DEFAULT_PACKET_DIRECTORY,
    GENERATOR_PATH,
    MANIFEST_NAME,
    POST_DECISION_PACKET_DIRECTORY,
    PROVENANCE_MANIFEST,
    PROVENANCE_RECORD,
    QUESTION_DEFINITIONS,
    REVIEW_INSTRUCTIONS_NAME,
    THIRD_PARTY_NOTICE,
    TOOL_DEFINITIONS,
    VALIDATOR_PATH,
    PacketError,
    _canonical_json,
    generate_packet,
    main,
    resolve_commit,
    verify_packet,
    verify_packet_content_only,
)
from scripts.validate_bootstrap_provenance import compute_review_content_sha256


from tests.packet_review_fixture import PacketReviewFixtureMixin
from tests.packet_review_cases.construction import ConstructionCases
from tests.packet_review_cases.offline import OfflineCases
from tests.packet_review_cases.identity import IdentityCases
from tests.packet_review_cases.manifest import ManifestCases
from tests.packet_review_cases.git_objects import GitObjectCases
from tests.packet_review_cases.filesystem import FilesystemCases
from tests.packet_review_cases.cli import CliCases


class V002G0ReviewPacketTests(
    ConstructionCases,
    OfflineCases,
    IdentityCases,
    ManifestCases,
    GitObjectCases,
    FilesystemCases,
    CliCases,
    PacketReviewFixtureMixin,
    unittest.TestCase,
):
    pass


if __name__ == "__main__":
    unittest.main()
