#!/usr/bin/env python3
"""Validate the repository governance and documentation baseline."""

from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import io
import os
import re
import shlex
import shutil
import stat
import subprocess
import sys
import threading
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath, PureWindowsPath
from urllib.parse import unquote

if __package__:
    from .collect_v002_manual_evidence import (
        COMMITTED_BUNDLE,
        _is_link,
        validate_bundle,
    )
    from .validate_bootstrap_provenance import (
        APPROVED_RECORD_STATUS,
        EXCLUDED_RESOURCE_PREFIXES,
        EXPECTED_RESOURCE_PATHS,
        RESOURCE_ROOTS,
        validate_bootstrap_provenance,
        validate_bootstrap_provenance_at_commit,
    )
    from .validate_release_checksums import validate_release_checksums
    from .validate_v002_final_g0_review import (
        validate_v002_final_g0_review,
        validate_v002_final_g0_review_at_commit,
    )
    from .validate_v002_g4_applicability import validate_v002_g4_applicability
    from .validate_v010_asset_baseline import validate_v010_asset_baseline
    from .validate_v010_release_evidence import validate_v010_release_evidence
    from .validate_v020_release_evidence import validate_v020_release_evidence
    from .manage_v020_generated_manifest import verify as verify_v020_generated_manifest
    from .manage_v030_generated_manifest import verify as verify_v030_generated_manifest
else:
    # Isolated script execution omits this directory from sys.path. Add only
    # the already-selected repository scripts directory after stdlib imports.
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    from collect_v002_manual_evidence import (
        COMMITTED_BUNDLE,
        _is_link,
        validate_bundle,
    )
    from validate_bootstrap_provenance import (
        APPROVED_RECORD_STATUS,
        EXCLUDED_RESOURCE_PREFIXES,
        EXPECTED_RESOURCE_PATHS,
        RESOURCE_ROOTS,
        validate_bootstrap_provenance,
        validate_bootstrap_provenance_at_commit,
    )
    from validate_release_checksums import validate_release_checksums
    from validate_v002_final_g0_review import (
        validate_v002_final_g0_review,
        validate_v002_final_g0_review_at_commit,
    )
    from validate_v002_g4_applicability import validate_v002_g4_applicability
    from validate_v010_asset_baseline import validate_v010_asset_baseline
    from validate_v010_release_evidence import validate_v010_release_evidence
    from validate_v020_release_evidence import validate_v020_release_evidence
    from manage_v020_generated_manifest import verify as verify_v020_generated_manifest
    from manage_v030_generated_manifest import verify as verify_v030_generated_manifest


ROOT = Path(__file__).resolve().parents[1]

REQUIRED_PATHS = (
    ".gitattributes",
    ".gitignore",
    "00-READ-ME-FIRST.md",
    "AGENTS.md",
    "BRANDING_AND_AFFILIATION.md",
    "CODE_OF_CONDUCT.md",
    "CHANGELOG.md",
    "CONTRIBUTING.md",
    "LICENSE",
    "NOTICE.md",
    "THIRD-PARTY-NOTICES.md",
    "PRODUCT.md",
    "PROJECT-CONFIG.md",
    "README.md",
    "SECURITY.md",
    "UPSTREAM.md",
    ".github/CODEOWNERS",
    ".github/PULL_REQUEST_TEMPLATE.md",
    ".github/ISSUE_TEMPLATE/bug_report.yml",
    ".github/ISSUE_TEMPLATE/compatibility_report.yml",
    ".github/ISSUE_TEMPLATE/config.yml",
    ".github/ISSUE_TEMPLATE/porting_task.yml",
    ".github/workflows/repository-docs.yml",
    ".github/workflows/forge-bootstrap.yml",
    "build.gradle",
    "gradle.properties",
    "settings.gradle",
    "gradlew",
    "gradlew.bat",
    "gradle/wrapper/gradle-wrapper.jar",
    "gradle/wrapper/gradle-wrapper.properties",
    "scripts/check_client_imports.py",
    "scripts/check_celestial_identity.py",
    "scripts/check_clean_worktree.py",
    "scripts/collect_v002_manual_evidence.py",
    "scripts/generate_v002_g0_evidence.py",
    "scripts/inspect_celestial_saved_data.py",
    "scripts/prepare_v002_final_g0_review_inputs.py",
    "scripts/prepare_v002_g0_review_packet.py",
    "scripts/run_dedicated_server_smoke.py",
    "scripts/run_v002_mismatch_server_cycle.py",
    "scripts/run_v020_machine_server_smoke.py",
    "scripts/run_v030_celestial_server_smoke.py",
    "scripts/validate_bootstrap_provenance.py",
    "scripts/validate_build_artifact.py",
    "scripts/validate_release_checksums.py",
    "scripts/validate_v002_final_g0_review.py",
    "scripts/validate_v002_g4_applicability.py",
    "scripts/manage_v010_generated_manifest.py",
    "scripts/manage_v020_generated_manifest.py",
    "scripts/manage_v030_generated_manifest.py",
    "scripts/validate_v010_asset_baseline.py",
    "scripts/validate_v020_release_evidence.py",
    "tools/audit/audit_upstream.py",
    "tools/import/import_v010_assets.py",
    "tools/import/v010-content-plan.json",
    "tests/test_check_client_imports.py",
    "tests/test_check_celestial_identity.py",
    "tests/test_check_clean_worktree.py",
    "tests/test_collect_v002_manual_evidence.py",
    "tests/test_dedicated_server_smoke.py",
    "tests/test_generate_v002_g0_evidence.py",
    "tests/test_prepare_v002_final_g0_review_inputs.py",
    "tests/test_prepare_v002_g0_review_packet.py",
    "tests/test_run_v002_mismatch_server_cycle.py",
    "tests/test_validate_bootstrap_provenance.py",
    "tests/test_validate_build_artifact.py",
    "tests/test_validate_release_checksums.py",
    "tests/test_validate_v002_final_g0_review.py",
    "tests/test_validate_v002_g4_applicability.py",
    "tests/test_audit_upstream.py",
    "tests/test_import_v010_assets.py",
    "tests/test_inspect_celestial_saved_data.py",
    "tests/test_manage_v010_generated_manifest.py",
    "tests/test_validate_v010_asset_baseline.py",
    "tests/test_validate_v020_release_evidence.py",
    "tests/test_manage_v030_generated_manifest.py",
    "tests/test_run_v030_celestial_server_smoke.py",
    "tests/test_validate_repository.py",
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.java",
    "src/main/resources/META-INF/mods.toml",
    "src/main/resources/pack.mcmeta",
    "src/main/resources/advancedrocketrycommunity.png",
    "src/generated/resources/data/advancedrocketrycommunity/structures/empty.nbt",
    "docs/provenance/v0.2.0-electrolyzer.md",
    "docs/provenance/v0.2.0-generated-resources.json",
    "docs/provenance/v0.3.0-generated-resources.json",
    "docs/provenance/v0.3.0-upstream-xml-fixture.json",
    "docs/status/CURRENT_VERSION.md",
    "docs/status/GATE_STATUS.md",
    "docs/releases/v0.0.1/RELEASE-EVIDENCE.md",
    "docs/releases/v0.0.1/TEST-REPORT.md",
    "docs/releases/v0.0.1/MANUAL-TEST.md",
    "docs/releases/v0.0.1/KNOWN-ISSUES.md",
    "docs/releases/v0.0.1/evidence/README.md",
    "docs/decisions/ADR-004-PRIVATE-REPOSITORY-G8-ACCEPTANCE.md",
    "docs/decisions/ADR-005-V0.0.2-G4-APPLICABILITY.md",
    "docs/work/v0.0.1-implementation-log.md",
    "docs/work/v0.0.2-implementation-log.md",
    "docs/work/v0.0.2-test-machine-handoff.md",
    "docs/work/v0.1.0-implementation-log.md",
    "docs/releases/v0.0.2/GATE-STATUS.md",
    "docs/provenance/schema-v1.json",
    "docs/provenance/v0.1.0-minimal-content.json",
    "docs/provenance/v0.1.0-generated-resources.json",
    "legacy-manifest/UPSTREAM_COMMIT.txt",
    "legacy-manifest/java-files.csv",
    "legacy-manifest/java-packages.csv",
    "legacy-manifest/dependency-imports.csv",
    "legacy-manifest/libvulpes-usage.csv",
    "legacy-manifest/static-world-state.csv",
    "legacy-manifest/network-packets.csv",
    "legacy-manifest/entities.csv",
    "legacy-manifest/block-entities.csv",
    "legacy-manifest/registries.csv",
    "legacy-manifest/recipes.csv",
    "legacy-manifest/assets.csv",
    "legacy-manifest/asset-references.csv",
    "legacy-manifest/missing-asset-references.csv",
    "legacy-manifest/duplicate-case-paths.csv",
    "legacy-manifest/large-files.csv",
    "legacy-manifest/asm-and-coremod.csv",
    "legacy-manifest/audit-summary.md",
    "docs/licenses/GRADLE-8.1.1-LICENSE.txt",
    "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt",
    "docs/provenance/v0.0.2-forge-mdk-and-gradle-wrapper.md",
    "docs/provenance/v0.0.2-bootstrap-inputs.json",
    "docs/releases/v0.0.2/INSTALLATION.md",
    "docs/releases/v0.0.2/RELEASE-EVIDENCE.md",
    "docs/releases/v0.0.2/TEST-REPORT.md",
    "docs/releases/v0.0.2/MANUAL-TEST.md",
    "docs/releases/v0.0.2/KNOWN-ISSUES.md",
    "docs/releases/v0.0.2/checksums.txt",
    "docs/releases/v0.0.2/evidence/artifact/jar-content-manifest.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/README.md",
    "docs/releases/v0.0.2/evidence/g0-mechanical/license-notice-scan.json",
    "docs/releases/v0.0.2/evidence/g0-mechanical/mods.toml",
    "docs/releases/v0.0.2/evidence/g0-mechanical/sources-jar-manifest.json",
    "docs/releases/v0.0.2/evidence/dedicated-server/README.md",
    "docs/releases/v0.0.2/evidence/dedicated-server/summary.json",
    "docs/releases/v0.0.2/evidence/dedicated-server/first-start.txt",
    "docs/releases/v0.0.2/evidence/dedicated-server/restart.txt",
)

VERSION_DOCUMENTS = (
    "V0.0.1-REPOSITORY-BASELINE.md",
    "V0.0.2-FORGE-BOOTSTRAP.md",
    "V0.1.0-ASSET-REGISTRY-BASELINE.md",
    "V0.2.0-MACHINE-VERTICAL-SLICE.md",
    "V0.3.0-CELESTIAL-DATA-AND-DIMENSIONS.md",
    "V0.4.0-VACUUM-LIFE-SUPPORT-ATMOSPHERE.md",
    "V0.5.0-ROCKET-ASSEMBLY.md",
    "V0.6.0-EARTH-MOON-ROUNDTRIP.md",
    "V0.7.0-SPACE-STATION.md",
    "V0.8.0-PROGRESSION-SATELLITES.md",
    "V0.9.0-BETA-HARDENING.md",
    "V1.0.0-COMMUNITY-MVP.md",
)

MARKDOWN_LINK = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")
MAX_TRACKED_MARKDOWN_FILES = 4096
MAX_TRACKED_MARKDOWN_PATH_BYTES = 4096
MAX_MARKDOWN_FILE_BYTES = 2 * 1024 * 1024
MAX_MARKDOWN_TOTAL_BYTES = 32 * 1024 * 1024
MAX_MARKDOWN_LINKS = 10_000
MAX_MARKDOWN_ERRORS = 256
MAX_MARKDOWN_LINK_TARGET_BYTES = 4096
MAX_MARKDOWN_LINES_PER_FILE = 100_000
MAX_REPOSITORY_INVENTORY_BYTES = 4 * 1024 * 1024
MAX_REPOSITORY_FILES = 32768
MAX_REPOSITORY_PATH_BYTES = 4096
MAX_GOVERNANCE_TEXT_BYTES = 4 * 1024 * 1024
MAX_APPROVED_JAR_BYTES = 4 * 1024 * 1024
MAX_BOOTSTRAP_ASSET_BYTES = 4 * 1024 * 1024
MAX_GATE_STATUS_DOCUMENT_LINES = 2048
MAX_GATE_STATUS_BLOCK_LINES = 512
MAX_GATE_STATUS_ERRORS = 32
MAX_PACKAGE_CHECKSUM_LIST_BYTES = 2 * 1024 * 1024
MAX_PACKAGE_CHECKSUM_ENTRIES = 4096
MAX_PACKAGE_FILE_BYTES = 512 * 1024 * 1024
MAX_PACKAGE_TOTAL_BYTES = 512 * 1024 * 1024
MAX_PACKAGE_PATH_BYTES = 4096
GIT_QUERY_TIMEOUT_SECONDS = 30
AUTHORIZED_RELEASE_REVIEWERS = frozenset({"sunthemoon"})
V002_HISTORICAL_RECORD_COMMIT = "9359257b9fe1eccf7e0043dfa7f626cf1ee44be9"
IDENTITY_STATUS = re.compile(r'identity_status:\s*"([A-Z_]+)"')
UPSTREAM_COMMIT = re.compile(r"upstream_commit:\s*([0-9a-f]{40})\b")
V001_EVIDENCE_PREFIX = "docs/releases/v0.0.1/evidence/"
V001_EVIDENCE_MAX_BYTES = 2 * 1024 * 1024
V010_MANAGED_RESOURCE_PREFIXES = (
    "src/main/resources/assets/advancedrocketrycommunity/",
    "src/generated/resources/assets/advancedrocketrycommunity/",
    "src/generated/resources/data/advancedrocketrycommunity/",
    "src/generated/resources/data/minecraft/tags/blocks/",
)
GRADLE_WRAPPER_PATH = "gradle/wrapper/gradle-wrapper.jar"
GRADLE_WRAPPER_SHA256 = "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58"
BOOTSTRAP_LOGO_PATH = "src/main/resources/advancedrocketrycommunity.png"
BOOTSTRAP_LOGO_SHA256 = "c5c6fbc63113a51da1ec28ef1227b358b41030b09cae4103f160f37d3a343690"
THIRD_PARTY_LICENSE_SHA256 = {
    "docs/licenses/GRADLE-8.1.1-LICENSE.txt": (
        "e5bfcf1132c8e12c3fce87d4dfbcb543cfb7202d8fa28ba85c07132e30836437"
    ),
    "docs/licenses/MINECRAFT-FORGE-1.20.1-47.4.10-LICENSE.txt": (
        "481c96d94d182382c4225d5b210f8c658c85350cf548f25c9f56c058804f1e57"
    ),
}
class Results:
    def __init__(self) -> None:
        self.passes: list[str] = []
        self.pending: list[str] = []
        self.warnings: list[str] = []
        self.failures: list[str] = []

    def passed(self, message: str) -> None:
        self.passes.append(message)

    def pending_state(self, message: str) -> None:
        self.pending.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def fail(self, message: str) -> None:
        self.failures.append(message)

    def print_report(self) -> None:
        for message in self.passes:
            print(f"[PASS] {message}")
        for message in self.pending:
            print(f"[PENDING] {message}")
        for message in self.warnings:
            print(f"[WARN] {message}")
        for message in self.failures:
            print(f"[FAIL] {message}")
        print(
            "Summary: "
            f"{len(self.passes)} passed, "
            f"{len(self.pending)} pending, "
            f"{len(self.warnings)} warnings, "
            f"{len(self.failures)} failed"
        )


def _file_identity(metadata: os.stat_result) -> tuple[int, int, int, int, int]:
    return (
        metadata.st_dev,
        metadata.st_ino,
        metadata.st_size,
        metadata.st_mtime_ns,
        metadata.st_mode,
    )


def _inspect_bounded_file(
    path: Path,
    max_bytes: int,
    description: str,
    *,
    trusted_root: Path,
) -> tuple[Path, os.stat_result]:
    """Return a trusted regular file and its bounded initial identity."""

    root = trusted_root.absolute()
    target = path.absolute()
    try:
        relative = target.relative_to(root)
    except ValueError as exc:
        raise ValueError(f"{description} is outside the trusted root") from exc
    current = root
    for component in (None, *relative.parts):
        if component is not None:
            current /= component
        try:
            current.lstat()
        except OSError as exc:
            raise ValueError(f"cannot inspect {description} path component: {exc}") from exc
        if _is_link(current):
            raise ValueError(
                f"{description} path must not contain a symlink, junction, or reparse point"
            )
    try:
        metadata = target.lstat()
    except OSError as exc:
        raise ValueError(f"cannot inspect {description}: {exc}") from exc
    if _is_link(target) or not stat.S_ISREG(metadata.st_mode):
        raise ValueError(f"{description} must be a regular non-link file")
    if metadata.st_size > max_bytes:
        raise ValueError(f"{description} exceeds {max_bytes} bytes")
    return target, metadata


def read_bounded_bytes(
    path: Path,
    max_bytes: int,
    description: str,
    *,
    trusted_root: Path,
) -> bytes:
    """Read a stable regular, non-link file without exceeding the supplied limit."""

    target, metadata = _inspect_bounded_file(
        path,
        max_bytes,
        description,
        trusted_root=trusted_root,
    )
    try:
        with target.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            if _file_identity(opened) != _file_identity(metadata):
                raise ValueError(f"{description} changed before it could be read")
            payload = stream.read(max_bytes + 1)
            final = os.fstat(stream.fileno())
    except OSError as exc:
        raise ValueError(f"cannot read {description}: {exc}") from exc
    if len(payload) > max_bytes:
        raise ValueError(f"{description} exceeds {max_bytes} bytes")
    if len(payload) != metadata.st_size or _file_identity(final) != _file_identity(metadata):
        raise ValueError(f"{description} changed while it was read")
    return payload


def sha256_bounded_file(
    path: Path,
    max_bytes: int,
    description: str,
    *,
    trusted_root: Path,
) -> tuple[str, int]:
    """Hash a stable trusted file with bounded streaming memory and input size."""

    target, metadata = _inspect_bounded_file(
        path,
        max_bytes,
        description,
        trusted_root=trusted_root,
    )
    digest = hashlib.sha256()
    total = 0
    try:
        with target.open("rb") as stream:
            opened = os.fstat(stream.fileno())
            if _file_identity(opened) != _file_identity(metadata):
                raise ValueError(f"{description} changed before it could be hashed")
            while True:
                chunk = stream.read(min(1024 * 1024, max_bytes - total + 1))
                if not chunk:
                    break
                total += len(chunk)
                if total > max_bytes:
                    raise ValueError(f"{description} exceeds {max_bytes} bytes")
                digest.update(chunk)
            final = os.fstat(stream.fileno())
    except OSError as exc:
        raise ValueError(f"cannot hash {description}: {exc}") from exc
    if total != metadata.st_size or _file_identity(final) != _file_identity(metadata):
        raise ValueError(f"{description} changed while it was hashed")
    return digest.hexdigest(), total


def read_text(path: Path, results: Results) -> str:
    try:
        payload = read_bounded_bytes(
            path,
            MAX_GOVERNANCE_TEXT_BYTES,
            f"UTF-8 text file {path}",
            trusted_root=ROOT,
        )
        return payload.decode("utf-8", errors="strict")
    except (UnicodeError, ValueError) as exc:
        results.fail(f"Cannot read UTF-8 text file {path.relative_to(ROOT)}: {exc}")
        return ""


def is_approved_third_party_license(relative: str, content: bytes) -> bool:
    expected_hash = THIRD_PARTY_LICENSE_SHA256.get(relative)
    return (
        expected_hash is not None
        and hashlib.sha256(content).hexdigest() == expected_hash
    )


def check_required_paths(results: Results) -> None:
    missing = [path for path in REQUIRED_PATHS if not (ROOT / path).is_file()]
    missing.extend(
        f"docs/versions/{name}"
        for name in VERSION_DOCUMENTS
        if not (ROOT / "docs" / "versions" / name).is_file()
    )
    if missing:
        results.fail("Missing required files: " + ", ".join(sorted(missing)))
    else:
        results.passed(
            f"Required governance files and {len(VERSION_DOCUMENTS)} version plans exist"
        )


def parse_current_identity(text: str) -> tuple[str | None, str | None, str | None]:
    current_values = text.split("当前值：", 1)[-1]
    match = IDENTITY_STATUS.search(current_values)
    reviewer = re.search(r'reviewed_by:\s*"([^"]+)"', current_values)
    reviewed_at = re.search(r'reviewed_at:\s*"(\d{4}-\d{2}-\d{2})"', current_values)
    return (
        match.group(1) if match else None,
        reviewer.group(1) if reviewer else None,
        reviewed_at.group(1) if reviewed_at else None,
    )


def check_identity(results: Results, require_approved: bool) -> None:
    text = read_text(ROOT / "PROJECT-CONFIG.md", results)
    status, reviewer, reviewed_at = parse_current_identity(text)
    if not status:
        results.fail("PROJECT-CONFIG.md has no parseable identity_status")
        return

    if status == "APPROVED":
        expected_values = (
            "| GitHub owner | `sunthemoon` |",
            "| repository | `AdvancedRocketry-Community` |",
            "| mod id | `advancedrocketrycommunity` |",
        )
        if not reviewer or not reviewed_at or any(value not in text for value in expected_values):
            results.fail("Approved identity is missing reviewer, review date, or required project values")
        else:
            results.passed("Project identity is APPROVED and expected values are present")
    elif require_approved:
        results.fail(f"Project identity is {status}; APPROVED is required")
    else:
        results.warn(f"Project identity is {status}; human approval is still required")


def check_public_statements(results: Results) -> None:
    requirements = {
        "README.md": ("unofficial", "not an official minecraft product"),
        "NOTICE.md": ("unofficial", "not an official minecraft product"),
        "BRANDING_AND_AFFILIATION.md": (
            "unofficial",
            "not an official minecraft product",
        ),
        ".github/ISSUE_TEMPLATE/bug_report.yml": (
            "unofficial community project",
            "original advanced rocketry maintainers",
        ),
    }
    failures: list[str] = []
    for relative, phrases in requirements.items():
        text = read_text(ROOT / relative, results).lower()
        for phrase in phrases:
            if phrase not in text:
                failures.append(f"{relative}: {phrase}")
    if failures:
        results.fail("Missing public non-affiliation statements: " + ", ".join(failures))
    else:
        results.passed("README, NOTICE, branding, and issue intake state unofficial status")


def check_license_and_upstream(results: Results) -> None:
    license_text = read_text(ROOT / "LICENSE", results)
    license_requirements = (
        "MIT License",
        "Copyright (c) 2017",
        "Copyright (c) 2026 Advanced Rocketry: Community Edition contributors",
        "Permission is hereby granted, free of charge",
    )
    missing = [item for item in license_requirements if item not in license_text]
    if missing:
        results.fail("LICENSE is missing: " + ", ".join(missing))
    else:
        results.passed("LICENSE preserves the original notice and community attribution")

    upstream_text = read_text(ROOT / "UPSTREAM.md", results)
    match = UPSTREAM_COMMIT.search(upstream_text)
    if match:
        results.passed(f"Exact upstream commit is recorded: {match.group(1)}")
    else:
        results.fail("UPSTREAM.md does not contain an exact 40-character commit")


def iter_markdown_prose(path: Path, results: Results):
    in_fence = False
    for number, line in enumerate(read_text(path, results).splitlines(), start=1):
        stripped = line.lstrip()
        if stripped.startswith("```") or stripped.startswith("~~~"):
            in_fence = not in_fence
            continue
        if not in_fence:
            yield number, re.sub(r"`[^`]*`", "", line)


def normalize_link_target(raw_target: str) -> str:
    target = raw_target.strip()
    if target.startswith("<") and ">" in target:
        target = target[1 : target.index(">")]
    else:
        target = target.split(maxsplit=1)[0]
    return unquote(target)


def _bounded_git_stdout(
    command: list[str], *, max_bytes: int, description: str
) -> bytes:
    """Run a read-only Git query with bounded output and wall-clock time."""

    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
            env=_git_environment(),
        )
    except OSError as exc:
        raise ValueError(f"cannot run {description}: {exc}") from exc
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(GIT_QUERY_TIMEOUT_SECONDS, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    try:
        payload = process.stdout.read(max_bytes + 1)
        if len(payload) > max_bytes:
            raise ValueError(f"{description} exceeds the byte limit")
        return_code = process.wait()
        if timed_out.is_set():
            raise ValueError(f"{description} timed out")
        if return_code != 0:
            raise ValueError(f"{description} failed with exit {return_code}")
        return payload
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()


def _git_environment() -> dict[str, str]:
    environment = dict(os.environ)
    for name in tuple(environment):
        if name.upper().startswith("GIT_TRACE"):
            environment.pop(name, None)
        if name.startswith("GIT_CONFIG_KEY_") or name.startswith("GIT_CONFIG_VALUE_"):
            environment.pop(name, None)
    for name in (
        "GIT_ALTERNATE_OBJECT_DIRECTORIES",
        "GIT_COMMON_DIR",
        "GIT_CONFIG",
        "GIT_CONFIG_COUNT",
        "GIT_CONFIG_PARAMETERS",
        "GIT_DIR",
        "GIT_EXEC_PATH",
        "GIT_EXTERNAL_DIFF",
        "GIT_INDEX_FILE",
        "GIT_LITERAL_PATHSPECS",
        "GIT_GLOB_PATHSPECS",
        "GIT_NOGLOB_PATHSPECS",
        "GIT_ICASE_PATHSPECS",
        "GIT_NAMESPACE",
        "GIT_OBJECT_DIRECTORY",
        "GIT_REPLACE_REF_BASE",
        "GIT_SHALLOW_FILE",
        "GIT_WORK_TREE",
    ):
        environment.pop(name, None)
    environment.update(
        {
            "GIT_CONFIG_GLOBAL": os.devnull,
            "GIT_CONFIG_NOSYSTEM": "1",
            "GIT_CONFIG_SYSTEM": os.devnull,
            "GIT_NO_LAZY_FETCH": "1",
            "GIT_NO_REPLACE_OBJECTS": "1",
            "GIT_OPTIONAL_LOCKS": "0",
            "GIT_TERMINAL_PROMPT": "0",
            "LC_ALL": "C",
        }
    )
    return environment


def _git_executable(repository_root: Path) -> str:
    candidate = shutil.which("git")
    if not candidate:
        raise ValueError("cannot locate Git on the trusted runtime PATH")
    try:
        executable = Path(candidate).resolve(strict=True)
        metadata = executable.lstat()
    except OSError as exc:
        raise ValueError(f"cannot resolve Git executable: {exc}") from exc
    if not stat.S_ISREG(metadata.st_mode) or _is_link(executable):
        raise ValueError("Git executable must resolve to an ordinary regular file")
    try:
        executable.relative_to(repository_root.resolve())
    except ValueError:
        pass
    else:
        raise ValueError("Git executable must not be contained in the repository")
    return str(executable)


def _git_inventory_command(
    repository_root: Path, *, literal_pathspecs: bool = True
) -> list[str]:
    command = [
        _git_executable(repository_root),
        "--no-pager",
        "--no-replace-objects",
    ]
    if literal_pathspecs:
        command.append("--literal-pathspecs")
    command.extend(
        [
            "-c",
            "core.commitGraph=false",
            "-c",
            "core.fsmonitor=false",
            "-c",
            "core.untrackedCache=false",
            "-c",
            f"safe.directory={repository_root.as_posix()}",
            "-C",
            str(repository_root),
        ]
    )
    return command


def tracked_markdown_files(repository_root: Path = ROOT) -> list[Path]:
    """Return the bounded, Git-indexed Markdown inventory for validation."""

    repository_root = repository_root.resolve()
    command = [
        *_git_inventory_command(repository_root),
        "ls-files",
        "-z",
        "--cached",
    ]
    payload = _bounded_git_stdout(
        command,
        max_bytes=MAX_REPOSITORY_INVENTORY_BYTES,
        description="tracked Markdown inventory query",
    )

    raw_names = [name for name in payload.split(b"\0") if name]
    if len(raw_names) > MAX_REPOSITORY_FILES:
        raise ValueError("tracked file inventory exceeds the file-count limit")

    paths: list[Path] = []
    for raw_name in raw_names:
        if len(raw_name) > MAX_TRACKED_MARKDOWN_PATH_BYTES:
            raise ValueError("tracked Markdown inventory contains an oversized path")
        try:
            name = raw_name.decode("utf-8")
        except UnicodeError as exc:
            raise ValueError("tracked Markdown inventory contains a non-UTF-8 path") from exc
        relative = PurePosixPath(name)
        if (
            relative.is_absolute()
            or not relative.parts
            or "\\" in name
            or ":" in name
            or any(part in {"", ".", ".."} for part in relative.parts)
        ):
            raise ValueError(f"tracked Markdown inventory contains an unsafe path: {name}")
        path = repository_root.joinpath(*relative.parts)
        if path.suffix.lower() == ".md":
            paths.append(path)
    if len(paths) > MAX_TRACKED_MARKDOWN_FILES:
        raise ValueError("tracked Markdown inventory exceeds the file-count limit")
    return paths


def markdown_link_errors(
    repository_root: Path, paths: list[Path]
) -> tuple[list[str], int]:
    repository_root = repository_root.resolve()
    broken: list[str] = []
    checked = 0
    total_bytes = 0

    def add_error(message: str) -> bool:
        if len(broken) >= MAX_MARKDOWN_ERRORS:
            broken.append(
                f"Markdown validation stopped after {MAX_MARKDOWN_ERRORS} errors"
            )
            return False
        broken.append(message)
        return True

    for path in sorted(paths):
        try:
            resolved = path.absolute()
            relative = resolved.relative_to(repository_root)
            payload = read_bounded_bytes(
                resolved,
                MAX_MARKDOWN_FILE_BYTES,
                f"tracked Markdown file {relative.as_posix()}",
                trusted_root=repository_root,
            )
        except ValueError as exc:
            if not add_error(f"{path}: cannot read tracked Markdown file: {exc}"):
                return broken, checked
            continue
        total_bytes += len(payload)
        if total_bytes > MAX_MARKDOWN_TOTAL_BYTES:
            add_error(
                "Tracked Markdown files exceed the aggregate byte limit of "
                f"{MAX_MARKDOWN_TOTAL_BYTES}"
            )
            return broken, checked
        try:
            text = payload.decode("utf-8")
        except UnicodeError as exc:
            if not add_error(
                f"{relative.as_posix()}: cannot decode tracked Markdown as UTF-8: {exc}"
            ):
                return broken, checked
            continue

        in_fence = False
        for line_number, source_line in enumerate(io.StringIO(text), start=1):
            if line_number > MAX_MARKDOWN_LINES_PER_FILE:
                add_error(
                    f"{relative.as_posix()}: tracked Markdown exceeds the line-count "
                    f"limit of {MAX_MARKDOWN_LINES_PER_FILE}"
                )
                return broken, checked
            source_line = source_line.rstrip("\r\n")
            stripped = source_line.lstrip()
            if stripped.startswith("```") or stripped.startswith("~~~"):
                in_fence = not in_fence
                continue
            if in_fence:
                continue
            line = re.sub(r"`[^`]*`", "", source_line)
            for match in MARKDOWN_LINK.finditer(line):
                target = normalize_link_target(match.group(1))
                if not target or target.startswith(
                    ("#", "http://", "https://", "mailto:")
                ):
                    continue
                target = target.split("#", 1)[0].split("?", 1)[0]
                if not target:
                    continue
                if checked >= MAX_MARKDOWN_LINKS:
                    add_error(
                        "Markdown validation stopped after the link-count limit of "
                        f"{MAX_MARKDOWN_LINKS}"
                    )
                    return broken, checked
                checked += 1
                try:
                    target_size = len(target.encode("utf-8", errors="strict"))
                except UnicodeError:
                    target_size = MAX_MARKDOWN_LINK_TARGET_BYTES + 1
                windows_target = PureWindowsPath(target)
                posix_target = PurePosixPath(target)
                if (
                    target_size > MAX_MARKDOWN_LINK_TARGET_BYTES
                    or windows_target.is_absolute()
                    or windows_target.drive
                    or posix_target.is_absolute()
                    or "\\" in target
                ):
                    if not add_error(
                        f"{relative.as_posix()}:{line_number} -> unsafe or oversized target"
                    ):
                        return broken, checked
                    continue
                try:
                    candidate = (resolved.parent / target).resolve()
                    valid = candidate.is_relative_to(repository_root) and candidate.exists()
                except (OSError, RuntimeError, ValueError):
                    valid = False
                if not valid and not add_error(
                    f"{relative.as_posix()}:{line_number} -> {target}"
                ):
                    return broken, checked
    return broken, checked


def check_markdown_links(results: Results) -> None:
    try:
        paths = tracked_markdown_files(ROOT)
    except ValueError as exc:
        results.fail(f"Cannot enumerate authoritative Markdown files: {exc}")
        return
    broken, checked = markdown_link_errors(ROOT, paths)
    if broken:
        results.fail("Broken Markdown links: " + "; ".join(broken))
    else:
        results.passed(f"Markdown relative links resolve ({checked} checked)")


def repository_files(repository_root: Path = ROOT) -> list[Path]:
    """Return a bounded inventory of tracked and non-ignored untracked files."""

    repository_root = repository_root.resolve()
    command = [
        *_git_inventory_command(repository_root),
        "ls-files",
        "-z",
        "--cached",
        "--others",
        "--exclude-standard",
    ]
    payload = _bounded_git_stdout(
        command,
        max_bytes=MAX_REPOSITORY_INVENTORY_BYTES,
        description="repository file inventory query",
    )
    raw_names = [name for name in payload.split(b"\0") if name]
    if len(raw_names) > MAX_REPOSITORY_FILES:
        raise ValueError("repository file inventory exceeds the file-count limit")

    paths: list[Path] = []
    for raw_name in raw_names:
        if len(raw_name) > MAX_REPOSITORY_PATH_BYTES:
            raise ValueError("repository file inventory contains an oversized path")
        try:
            name = raw_name.decode("utf-8", errors="strict")
        except UnicodeError as exc:
            raise ValueError(
                "repository file inventory contains a non-UTF-8 path"
            ) from exc
        relative = PurePosixPath(name)
        if (
            relative.is_absolute()
            or not relative.parts
            or "\\" in name
            or ":" in name
            or any(part in {"", ".", ".."} for part in relative.parts)
        ):
            raise ValueError(
                f"repository file inventory contains an unsafe path: {name}"
            )
        paths.append(repository_root.joinpath(*relative.parts))
    return paths


def is_audited_v001_evidence(relative: str, content: bytes, index_text: str) -> bool:
    evidence_path = Path(relative)
    if (
        evidence_path.parent.as_posix() != V001_EVIDENCE_PREFIX.rstrip("/")
        or evidence_path.suffix.lower() not in (".jpg", ".jpeg")
    ):
        return False
    if (
        len(content) > V001_EVIDENCE_MAX_BYTES
        or not content.startswith(b"\xff\xd8\xff")
    ):
        return False

    filename = evidence_path.name
    digest = hashlib.sha256(content).hexdigest()
    return any(
        f"]({filename})" in line and f"`{digest}`" in line
        for line in index_text.splitlines()
    )


def is_approved_gradle_wrapper(relative: str, content: bytes) -> bool:
    return (
        relative == GRADLE_WRAPPER_PATH
        and content.startswith(b"PK")
        and hashlib.sha256(content).hexdigest() == GRADLE_WRAPPER_SHA256
    )


def find_unlisted_v002_resources(paths: list[str]) -> list[str]:
    """Return resources covered by neither v0.0.2 nor v0.1.0 validation."""

    return sorted(
        path
        for path in paths
        if path.startswith(RESOURCE_ROOTS)
        and not path.startswith(EXCLUDED_RESOURCE_PREFIXES)
        and not path.startswith(V010_MANAGED_RESOURCE_PREFIXES)
        and path not in EXPECTED_RESOURCE_PATHS
    )


def check_repository_contents(results: Results) -> None:
    try:
        files = repository_files(ROOT)
    except ValueError as exc:
        results.fail(f"Cannot enumerate repository files safely: {exc}")
        return
    relative = [path.relative_to(ROOT).as_posix() for path in files]
    forbidden = [path for path in relative if path.lower().endswith(".class")]
    approved_wrappers: set[str] = set()
    for path in files:
        binary_relative = path.relative_to(ROOT).as_posix()
        if not binary_relative.lower().endswith(".jar"):
            continue
        if binary_relative != GRADLE_WRAPPER_PATH:
            forbidden.append(binary_relative)
            continue
        try:
            binary_content = read_bounded_bytes(
                path,
                MAX_APPROVED_JAR_BYTES,
                f"JAR {binary_relative}",
                trusted_root=ROOT,
            )
        except ValueError as exc:
            results.fail(f"Cannot read JAR {binary_relative}: {exc}")
            forbidden.append(binary_relative)
            continue
        if is_approved_gradle_wrapper(binary_relative, binary_content):
            approved_wrappers.add(binary_relative)
        else:
            forbidden.append(binary_relative)
    forbidden.extend(path for path in relative if path.startswith("src/main/java/zmaster587/"))
    audited_evidence: set[str] = set()
    unaudited_evidence: set[str] = set()

    evidence_index = read_text(ROOT / V001_EVIDENCE_PREFIX / "README.md", results)
    for path in files:
        evidence_relative = path.relative_to(ROOT).as_posix()
        if evidence_relative == f"{V001_EVIDENCE_PREFIX}README.md":
            continue
        if not evidence_relative.startswith(V001_EVIDENCE_PREFIX):
            continue
        if Path(evidence_relative).suffix.lower() not in (".jpg", ".jpeg"):
            unaudited_evidence.add(evidence_relative)
            continue
        try:
            content = read_bounded_bytes(
                path,
                V001_EVIDENCE_MAX_BYTES,
                f"evidence asset {evidence_relative}",
                trusted_root=ROOT,
            )
        except ValueError as exc:
            results.fail(f"Cannot read evidence asset {evidence_relative}: {exc}")
            unaudited_evidence.add(evidence_relative)
            continue
        if is_audited_v001_evidence(evidence_relative, content, evidence_index):
            audited_evidence.add(evidence_relative)
        else:
            unaudited_evidence.add(evidence_relative)

    current = read_text(ROOT / "docs/status/CURRENT_VERSION.md", results)
    if "current_version: v0.0.1" in current:
        v001_extensions = (
            ".png",
            ".jpg",
            ".jpeg",
            ".gif",
            ".webp",
            ".ogg",
            ".obj",
            ".mtl",
        )
        forbidden.extend(
            path
            for path in relative
            if path.lower().endswith(v001_extensions) and path not in audited_evidence
        )
        forbidden.extend(path for path in relative if path.startswith("src/"))

    if "current_version: v0.0.2" in current:
        forbidden.extend(find_unlisted_v002_resources(relative))

    forbidden.extend(unaudited_evidence)
    if forbidden:
        results.fail("Forbidden legacy, unaudited evidence, or unapproved binary files: " + ", ".join(sorted(set(forbidden))))
    else:
        results.passed(
            "No forbidden legacy source or unapproved binary found; applicable "
            "source-resource allowlists match"
            f" ({len(approved_wrappers)} wrapper JAR and "
            f"{len(audited_evidence)} v0.0.1 evidence screenshots verified)"
        )

    folded: dict[str, list[str]] = {}
    for path in relative:
        folded.setdefault(path.casefold(), []).append(path)
    collisions = [paths for paths in folded.values() if len(paths) > 1]
    if collisions:
        results.fail("Case-insensitive path collisions: " + "; ".join(", ".join(paths) for paths in collisions))
    else:
        results.passed("No case-insensitive path collisions found")


def check_forge_bootstrap(results: Results) -> None:
    current = read_text(ROOT / "docs/status/CURRENT_VERSION.md", results)
    if "current_version: v0.0.2" not in current:
        return

    requirements = {
        "gradle.properties": (
            "minecraft_version=1.20.1",
            "minecraft_version_range=[1.20.1,1.20.2)",
            "forge_version=47.4.10",
            "forge_latest_version=47.4.23",
            "mod_id=advancedrocketrycommunity",
            "mod_group_id=io.github.sunthemoon.advancedrocketrycommunity",
            "mod_artifact_id=advancedrocketry-community",
        ),
        "build.gradle": (
            "JavaLanguageVersion.of(17)",
            "preserveFileTimestamps = false",
            "reproducibleFileOrder = true",
            "from(rootProject.file('LICENSE'))",
            "from(rootProject.file('NOTICE.md'))",
            "from(rootProject.file('THIRD-PARTY-NOTICES.md'))",
            "from(rootProject.file('docs/licenses'))",
        ),
        "gradle/wrapper/gradle-wrapper.properties": (
            "gradle-8.8-bin.zip",
            "distributionSha256Sum=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612",
        ),
        "src/main/resources/META-INF/mods.toml": (
            'modId="${mod_id}"',
            'displayTest="MATCH_VERSION"',
            'features={java_version="[17,)"}',
        ),
    }
    failures: list[str] = []
    for relative, fragments in requirements.items():
        text = read_text(ROOT / relative, results)
        missing = [fragment for fragment in fragments if fragment not in text]
        if missing:
            failures.append(f"{relative}: {', '.join(missing)}")

    try:
        logo = read_bounded_bytes(
            ROOT / BOOTSTRAP_LOGO_PATH,
            MAX_BOOTSTRAP_ASSET_BYTES,
            BOOTSTRAP_LOGO_PATH,
            trusted_root=ROOT,
        )
        if hashlib.sha256(logo).hexdigest() != BOOTSTRAP_LOGO_SHA256:
            failures.append(f"{BOOTSTRAP_LOGO_PATH}: unexpected SHA-256")
    except ValueError as exc:
        failures.append(f"{BOOTSTRAP_LOGO_PATH}: {exc}")

    for relative in THIRD_PARTY_LICENSE_SHA256:
        try:
            content = read_bounded_bytes(
                ROOT / relative,
                MAX_GOVERNANCE_TEXT_BYTES,
                relative,
                trusted_root=ROOT,
            )
            if not is_approved_third_party_license(relative, content):
                failures.append(f"{relative}: unexpected SHA-256")
        except ValueError as exc:
            failures.append(f"{relative}: {exc}")

    if failures:
        results.fail("Forge bootstrap baseline errors: " + "; ".join(failures))
    else:
        results.passed("Forge 1.20.1 / Java 17 bootstrap identity and pinned binaries match")


def check_issue_templates(results: Results) -> None:
    directory = ROOT / ".github" / "ISSUE_TEMPLATE"
    failures: list[str] = []
    for path in sorted(directory.glob("*.yml")):
        text = read_text(path, results)
        if "\t" in text:
            failures.append(f"{path.name}: tab indentation")
        if path.name == "config.yml":
            required = ("blank_issues_enabled:", "contact_links:")
        else:
            required = ("name:", "description:", "body:")
        missing = [key for key in required if key not in text]
        if missing:
            failures.append(f"{path.name}: missing {', '.join(missing)}")
    if failures:
        results.fail("Issue template structure errors: " + "; ".join(failures))
    else:
        results.passed("Issue template files have the required dependency-free structure")


@dataclass
class WorkflowStep:
    fields: dict[str, str] = field(default_factory=dict)
    structurally_safe: bool = True

    @property
    def enabled(self) -> bool:
        return not _is_statically_false(self.fields.get("if"))

    @property
    def blocking(self) -> bool:
        return (
            self.structurally_safe
            and self.enabled
            and _continue_on_error_is_blocking(
                self.fields.get("continue-on-error")
            )
        )


@dataclass
class WorkflowJob:
    fields: dict[str, str] = field(default_factory=dict)
    env: dict[str, str] = field(default_factory=dict)
    steps: list[WorkflowStep] = field(default_factory=list)
    structurally_safe: bool = True

    @property
    def enabled(self) -> bool:
        return not _is_statically_false(self.fields.get("if"))

    @property
    def blocking(self) -> bool:
        return (
            self.structurally_safe
            and self.enabled
            and _continue_on_error_is_blocking(
                self.fields.get("continue-on-error")
            )
        )


def _indent(line: str) -> int:
    return len(line) - len(line.lstrip(" "))


def _strip_yaml_comment(value: str) -> str:
    quote: str | None = None
    escaped = False
    for index, character in enumerate(value):
        if escaped:
            escaped = False
            continue
        if character == "\\" and quote == '"':
            escaped = True
            continue
        if character in ("'", '"'):
            if quote is None:
                quote = character
            elif quote == character:
                quote = None
            continue
        if character == "#" and quote is None and (
            index == 0 or value[index - 1].isspace()
        ):
            return value[:index].rstrip()
    return value.rstrip()


def _yaml_scalar(value: str) -> str:
    value = _strip_yaml_comment(value).strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in ("'", '"'):
        return value[1:-1]
    return value


def _is_statically_false(value: str | None) -> bool:
    if value is None:
        return False
    normalized = _yaml_scalar(value).strip().lower()
    if normalized.startswith("${{") and normalized.endswith("}}"):
        normalized = normalized[3:-2].strip()
    return normalized in {"false", "no", "off", "0", "null", "~"}


def _normalized_simple_expression(value: str | None) -> str | None:
    if value is None:
        return None
    normalized = _yaml_scalar(value).strip().lower()
    if normalized.startswith("${{") and normalized.endswith("}}"):
        normalized = normalized[3:-2].strip()
    return normalized


def _continue_on_error_is_blocking(value: str | None) -> bool:
    """Accept only absent or provably false continue-on-error values.

    Any non-literal expression is rejected for required jobs and steps because
    repository validation cannot prove that failures will block the workflow.
    """

    normalized = _normalized_simple_expression(value)
    return normalized in {None, "", "false", "no", "off", "0", "null", "~"}


def _continue_on_error_is_statically_true(value: str | None) -> bool:
    normalized = _normalized_simple_expression(value)
    return normalized in {"true", "yes", "on", "1"}


def _mapping_entry(content: str) -> tuple[str, str] | None:
    match = re.fullmatch(
        r"(?:"
        r"(?P<plain>[A-Za-z0-9_-]+)|"
        r"'(?P<single>[A-Za-z0-9_-]+)'|"
        r'"(?P<double>[A-Za-z0-9_-]+)"'
        r")\s*:(?:\s*(?P<value>.*))?",
        content,
    )
    if match is None:
        return None
    key = match.group("plain") or match.group("single") or match.group("double")
    return key, _yaml_scalar(match.group("value") or "")


def _multiline_scalar(
    lines: list[str], start: int, end: int, parent_indent: int, style: str
) -> tuple[str, int]:
    collected: list[str] = []
    index = start
    while index < end:
        line = lines[index]
        if line.strip() and _indent(line) <= parent_indent:
            break
        collected.append(line)
        index += 1
    nonempty_indents = [_indent(line) for line in collected if line.strip()]
    content_indent = min(nonempty_indents, default=parent_indent + 2)
    content = [line[content_indent:] if line.strip() else "" for line in collected]
    if style.startswith(">"):
        value = " ".join(part.strip() for part in content if part.strip())
    else:
        value = "\n".join(content)
    return value, index


def _parse_step(lines: list[str], start: int, end: int, step_indent: int) -> WorkflowStep:
    step = WorkflowStep()
    first = lines[start].lstrip()[2:].strip()
    first_entry = _mapping_entry(first)
    if first_entry is not None:
        step.fields[first_entry[0]] = first_entry[1]
    elif first:
        step.structurally_safe = False

    index = start + 1
    field_indent = step_indent + 2
    while index < end:
        line = lines[index]
        if not line.strip() or line.lstrip().startswith("#"):
            index += 1
            continue
        if _indent(line) != field_indent:
            index += 1
            continue
        entry = _mapping_entry(line.strip())
        if entry is None:
            step.structurally_safe = False
            index += 1
            continue
        key, value = entry
        if value in {"|", "|-", "|+", ">", ">-", ">+"}:
            value, index = _multiline_scalar(
                lines, index + 1, end, field_indent, value
            )
        elif key in {"env", "with"} and not value:
            child = index + 1
            while child < end and (
                not lines[child].strip() or _indent(lines[child]) > field_indent
            ):
                if lines[child].strip() and _indent(lines[child]) == field_indent + 2:
                    child_entry = _mapping_entry(lines[child].strip())
                    if child_entry is not None:
                        step.fields[f"{key}.{child_entry[0]}"] = child_entry[1]
                    else:
                        step.structurally_safe = False
                child += 1
            index = child
        else:
            index += 1
        step.fields[key] = value
    return step


def parse_workflow_jobs(text: str) -> dict[str, WorkflowJob]:
    """Parse the controlled GitHub Actions job/step subset used by this repo."""

    if "\t" in text:
        return {}
    lines = text.splitlines()
    jobs_index = next(
        (
            index
            for index, line in enumerate(lines)
            if _indent(line) == 0 and line.strip() == "jobs:"
        ),
        None,
    )
    if jobs_index is None:
        return {}

    job_starts: list[tuple[int, str]] = []
    jobs_end = len(lines)
    for index in range(jobs_index + 1, len(lines)):
        line = lines[index]
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if _indent(line) == 0:
            jobs_end = index
            break
        if _indent(line) == 2:
            entry = _mapping_entry(line.strip())
            if entry is not None and not entry[1]:
                job_starts.append((index, entry[0]))

    jobs: dict[str, WorkflowJob] = {}
    for position, (start, job_id) in enumerate(job_starts):
        end = (
            job_starts[position + 1][0]
            if position + 1 < len(job_starts)
            else jobs_end
        )
        job = WorkflowJob()
        steps_index: int | None = None
        index = start + 1
        while index < end:
            line = lines[index]
            if not line.strip() or line.lstrip().startswith("#"):
                index += 1
                continue
            if _indent(line) != 4:
                index += 1
                continue
            entry = _mapping_entry(line.strip())
            if entry is None:
                job.structurally_safe = False
                index += 1
                continue
            key, value = entry
            if key == "steps" and not value:
                steps_index = index
                index += 1
                continue
            if key == "env" and not value:
                child = index + 1
                while child < end and (
                    not lines[child].strip() or _indent(lines[child]) > 4
                ):
                    if lines[child].strip() and _indent(lines[child]) == 6:
                        env_entry = _mapping_entry(lines[child].strip())
                        if env_entry is not None:
                            job.env[env_entry[0]] = env_entry[1]
                        else:
                            job.structurally_safe = False
                    child += 1
                index = child
                continue
            job.fields[key] = value
            index += 1

        if steps_index is not None:
            step_starts = [
                index
                for index in range(steps_index + 1, end)
                if _indent(lines[index]) == 6
                and lines[index].lstrip().startswith("- ")
                and not lines[index].lstrip().startswith("- #")
            ]
            for step_position, step_start in enumerate(step_starts):
                step_end = (
                    step_starts[step_position + 1]
                    if step_position + 1 < len(step_starts)
                    else end
                )
                job.steps.append(_parse_step(lines, step_start, step_end, 6))
        jobs[job_id] = job
    return jobs


def _top_level_scalar(text: str, key: str) -> str | None:
    for line in text.splitlines():
        if not line.strip() or line.lstrip().startswith("#") or _indent(line) != 0:
            continue
        entry = _mapping_entry(line.strip())
        if entry is not None and entry[0] == key:
            return entry[1]
    return None


def _nested_top_level_scalar(text: str, parent: str, child: str) -> str | None:
    lines = text.splitlines()
    for index, line in enumerate(lines):
        if _indent(line) != 0 or line.strip() != f"{parent}:":
            continue
        for nested in lines[index + 1 :]:
            if nested.strip() and _indent(nested) == 0:
                break
            if not nested.strip() or nested.lstrip().startswith("#"):
                continue
            if _indent(nested) == 2:
                entry = _mapping_entry(nested.strip())
                if entry is not None and entry[0] == child:
                    return entry[1]
        break
    return None


def _nested_top_level_mapping(text: str, parent: str) -> dict[str, str] | None:
    """Parse one simple top-level mapping and reject ambiguous YAML shapes."""

    lines = text.splitlines()
    starts: list[int] = []
    for index, line in enumerate(lines):
        if not line.strip() or line.lstrip().startswith("#") or _indent(line) != 0:
            continue
        entry = _mapping_entry(line.strip())
        if entry is not None and entry[0] == parent:
            if entry[1]:
                return None
            starts.append(index)
    if len(starts) != 1:
        return None

    values: dict[str, str] = {}
    for nested in lines[starts[0] + 1 :]:
        if not nested.strip() or nested.lstrip().startswith("#"):
            continue
        indent = _indent(nested)
        if indent == 0:
            break
        if indent != 2:
            return None
        entry = _mapping_entry(nested.strip())
        if entry is None or not entry[1] or entry[0] in values:
            return None
        values[entry[0]] = entry[1]
    return values


def _required_steps(
    job: WorkflowJob, *, require_blocking_job: bool = True
) -> list[WorkflowStep]:
    if (
        not job.structurally_safe
        or not job.enabled
        or (require_blocking_job and not job.blocking)
    ):
        return []
    return [step for step in job.steps if step.blocking]


def _job_has_action(
    job: WorkflowJob, action: str, *, require_blocking_job: bool = True
) -> bool:
    return any(
        step.fields.get("uses") == action
        for step in _required_steps(
            job, require_blocking_job=require_blocking_job
        )
    )


def _job_action_steps(
    job: WorkflowJob, action: str, *, require_blocking_job: bool = True
) -> list[WorkflowStep]:
    return [
        step
        for step in _required_steps(
            job, require_blocking_job=require_blocking_job
        )
        if step.fields.get("uses") == action
    ]


def _job_has_exact_action_contract(
    job: WorkflowJob,
    action: str,
    expected_inputs: dict[str, str],
    *,
    require_blocking_job: bool = True,
) -> bool:
    steps = _job_action_steps(
        job, action, require_blocking_job=require_blocking_job
    )
    if len(steps) != 1:
        return False
    actual_inputs = {
        key.removeprefix("with."): value
        for key, value in steps[0].fields.items()
        if key.startswith("with.")
    }
    return actual_inputs == expected_inputs


def _job_has_exact_action_contracts(
    job: WorkflowJob,
    action: str,
    expected_inputs: tuple[dict[str, str], ...],
) -> bool:
    steps = _job_action_steps(job, action)
    actual = sorted(
        tuple(
            sorted(
                (
                    key.removeprefix("with."),
                    value,
                )
                for key, value in step.fields.items()
                if key.startswith("with.")
            )
        )
        for step in steps
    )
    expected = sorted(tuple(sorted(inputs.items())) for inputs in expected_inputs)
    return actual == expected


def _run_commands(run: str) -> list[tuple[str, ...]]:
    commands: list[tuple[str, ...]] = []
    for line in run.splitlines() or [run]:
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        try:
            tokens = tuple(shlex.split(line, comments=True, posix=True))
        except ValueError:
            continue
        if tokens:
            commands.append(tokens)
    return commands


def _job_has_command(
    job: WorkflowJob,
    expected: tuple[str, ...],
    *,
    require_blocking_job: bool = True,
) -> bool:
    return any(
        command == expected
        for step in _required_steps(
            job, require_blocking_job=require_blocking_job
        )
        for command in _run_commands(step.fields.get("run", ""))
    )


def validate_repository_workflow_text(text: str) -> list[str]:
    errors: list[str] = []
    if "\t" in text:
        errors.append("tab-free indentation")
    if _top_level_scalar(text, "name") != "Repository governance":
        errors.append("top-level name Repository governance")
    if _top_level_scalar(text, "on") is None:
        errors.append("top-level on trigger")
    if _nested_top_level_mapping(text, "permissions") != {"contents": "read"}:
        errors.append("top-level permissions limited to contents read")
    jobs = parse_workflow_jobs(text)
    job = jobs.get("validate-repository-docs")
    if job is None or not job.blocking:
        errors.append("enabled blocking validate-repository-docs job")
        return errors
    if "permissions" in job.fields:
        errors.append("no job-level permissions override")
    if job.env != {
        "REVIEW_COMMIT": "${{ github.event.pull_request.head.sha || github.sha }}"
    }:
        errors.append("exact immutable review-commit job environment")
    action_contracts = (
        (
            "actions/checkout@v7",
            {
                "fetch-depth": "0",
                "persist-credentials": "false",
                "ref": "${{ env.REVIEW_COMMIT }}",
            },
        ),
        ("actions/setup-python@v7", {"python-version": "3.12"}),
    )
    for action, inputs in action_contracts:
        if not _job_has_exact_action_contract(job, action, inputs):
            errors.append(f"exact enabled action contract {action}")
    upload_steps = [
        step
        for step in _required_steps(job)
        if step.fields.get("uses", "").startswith("actions/upload-artifact@")
    ]
    if upload_steps:
        errors.append("no governance artifact uploads after v0.0.2 archival")

    retired_v002_tools = {
        "scripts/prepare_v002_g0_review_packet.py",
        "scripts/prepare_v002_final_g0_review_inputs.py",
    }
    if any(
        token in retired_v002_tools
        for step in _required_steps(job)
        for command in _run_commands(step.fields.get("run", ""))
        for token in command
    ):
        errors.append("no retired v0.0.2 review-input generation at the current head")

    for command in (
        ("python", "-m", "unittest", "discover", "-s", "tests", "-v"),
        ("python", "scripts/validate_bootstrap_provenance.py"),
        (
            "git",
            "clone",
            "--filter=blob:none",
            "--no-checkout",
            "https://github.com/Advanced-Rocketry/AdvancedRocketry.git",
            "build/upstream/AdvancedRocketry",
        ),
        (
            "git",
            "-C",
            "build/upstream/AdvancedRocketry",
            "checkout",
            "--detach",
            "c5cd5af62fc07cd4e0d24f06a16033f181c47c04",
        ),
        (
            "python",
            "tools/audit/audit_upstream.py",
            "verify",
            "--upstream",
            "build/upstream/AdvancedRocketry",
            "--commit",
            "c5cd5af62fc07cd4e0d24f06a16033f181c47c04",
        ),
        (
            "python",
            "tools/import/import_v010_assets.py",
            "verify",
            "--upstream",
            "build/upstream/AdvancedRocketry",
        ),
        ("python", "scripts/manage_v010_generated_manifest.py", "verify"),
        ("python", "scripts/validate_v010_asset_baseline.py"),
        ("python", "scripts/validate_v010_release_evidence.py"),
        ("python", "scripts/manage_v020_generated_manifest.py", "verify"),
        ("python", "scripts/validate_v020_release_evidence.py"),
        ("python", "scripts/manage_v030_generated_manifest.py", "verify"),
        ("python", "scripts/check_celestial_identity.py"),
        (
            "python",
            "scripts/validate_repository.py",
            "--require-approved-identity",
        ),
    ):
        if not _job_has_command(job, command):
            errors.append("enabled run command " + " ".join(command))
    return errors


def validate_forge_workflow_text(text: str) -> list[str]:
    errors: list[str] = []
    if "\t" in text:
        errors.append("tab-free indentation")
    if _top_level_scalar(text, "name") != "Forge bootstrap":
        errors.append("top-level name Forge bootstrap")
    if _top_level_scalar(text, "on") is None:
        errors.append("top-level on trigger")
    if _nested_top_level_scalar(text, "permissions", "contents") != "read":
        errors.append("permissions.contents read")

    jobs = parse_workflow_jobs(text)
    baseline = jobs.get("baseline")
    latest = jobs.get("latest-compatibility")
    if baseline is None or not baseline.blocking:
        errors.append("enabled blocking baseline job")
    else:
        if baseline.env != {
            "REVIEW_COMMIT": "${{ github.event.pull_request.head.sha || github.sha }}"
        }:
            errors.append("baseline exact immutable review-commit job environment")
        if not _job_has_exact_action_contract(
            baseline,
            "actions/checkout@v7",
            {
                "fetch-depth": "0",
                "persist-credentials": "false",
                "ref": "${{ env.REVIEW_COMMIT }}",
            },
        ):
            errors.append("baseline exact head-bound checkout action contract")
        for action in (
            "actions/setup-java@v6",
            "gradle/actions/setup-gradle@v6",
        ):
            if not _job_has_action(baseline, action):
                errors.append(f"baseline enabled action {action}")
        upload_steps = [
            step
            for step in _required_steps(baseline)
            if step.fields.get("uses", "").startswith("actions/upload-artifact@")
        ]
        if (
            len(upload_steps) != 1
            or upload_steps[0].fields.get("uses") != "actions/upload-artifact@v7"
            or upload_steps[0].fields.get("with.name")
            != "forge-47.4.10-${{ env.REVIEW_COMMIT }}"
            or upload_steps[0].fields.get("with.if-no-files-found") != "error"
        ):
            errors.append("baseline exact head-bound artifact upload identity")
        baseline_commands = (
            ("chmod", "+x", "./gradlew"),
            ("./gradlew", "clean", "build", "--no-daemon", "--stacktrace"),
            ("python", "scripts/validate_bootstrap_provenance.py"),
            (
                "python",
                "scripts/validate_build_artifact.py",
                "build/libs/advancedrocketry-community-1.20.1-0.3.0-dev.jar",
                "--expected-version",
                "1.20.1-0.3.0-dev",
                "--content-manifest",
                "build/release-evidence/v030-jar-content-manifest.json",
            ),
            ("python", "scripts/validate_v010_asset_baseline.py"),
            ("python", "scripts/manage_v020_generated_manifest.py", "verify"),
            ("python", "scripts/manage_v030_generated_manifest.py", "verify"),
            ("python", "scripts/check_client_imports.py"),
            ("python", "scripts/check_celestial_identity.py"),
            ("./gradlew", "runData", "--no-daemon", "--stacktrace"),
            ("git", "diff", "--exit-code"),
            ("python", "scripts/check_clean_worktree.py"),
            (
                "./gradlew",
                "runGameTestServer",
                "--no-daemon",
                "--stacktrace",
            ),
            (
                "python",
                "scripts/run_dedicated_server_smoke.py",
                "build/libs/advancedrocketry-community-1.20.1-0.3.0-dev.jar",
                "--expected-mod-version",
                "1.20.1-0.3.0-dev",
                "--session-dir",
                "build/dedicated-server-smoke/session",
                "--evidence-dir",
                "build/dedicated-server-smoke/evidence",
                "--port",
                "25585",
            ),
            (
                "python",
                "scripts/run_v020_machine_server_smoke.py",
                "build/dedicated-server-smoke/session",
                "--baseline-summary",
                "build/dedicated-server-smoke/evidence/summary.json",
                "--evidence-dir",
                "build/v020-machine-server-smoke/evidence",
                "--expected-version",
                "1.20.1-0.3.0-dev",
            ),
            (
                "python",
                "scripts/run_v030_celestial_server_smoke.py",
                "build/dedicated-server-smoke/session",
                "--baseline-summary",
                "build/dedicated-server-smoke/evidence/summary.json",
                "--evidence-dir",
                "build/v030-celestial-server-smoke/evidence",
                "--expected-version",
                "1.20.1-0.3.0-dev",
            ),
        )
        for command in baseline_commands:
            if not _job_has_command(baseline, command):
                errors.append("baseline enabled run command " + " ".join(command))

    if latest is None or not latest.enabled:
        errors.append("enabled latest-compatibility job")
    else:
        if not _continue_on_error_is_statically_true(
            latest.fields.get("continue-on-error")
        ):
            errors.append("latest-compatibility continue-on-error true")
        if latest.env != {
            "REVIEW_COMMIT": "${{ github.event.pull_request.head.sha || github.sha }}",
            "ORG_GRADLE_PROJECT_forge_version": "47.4.23",
        }:
            errors.append(
                "latest exact immutable review-commit and Forge 47.4.23 job environment"
            )
        if not _job_has_exact_action_contract(
            latest,
            "actions/checkout@v7",
            {
                "fetch-depth": "0",
                "persist-credentials": "false",
                "ref": "${{ env.REVIEW_COMMIT }}",
            },
            require_blocking_job=False,
        ):
            errors.append("latest exact head-bound checkout action contract")
        for action in (
            "actions/setup-java@v6",
            "gradle/actions/setup-gradle@v6",
        ):
            if not _job_has_action(
                latest, action, require_blocking_job=False
            ):
                errors.append(f"latest enabled action {action}")
        for command in (
            ("chmod", "+x", "./gradlew"),
            ("./gradlew", "clean", "build", "--no-daemon", "--stacktrace"),
        ):
            if not _job_has_command(
                latest, command, require_blocking_job=False
            ):
                errors.append("latest enabled run command " + " ".join(command))
    return errors


def check_workflow(results: Results) -> None:
    path = ROOT / ".github" / "workflows" / "repository-docs.yml"
    text = read_text(path, results)
    errors = validate_repository_workflow_text(text)
    if errors:
        results.fail("Repository workflow is missing: " + ", ".join(errors))
    else:
        results.passed(
            "Repository governance workflow invokes validators in enabled blocking "
            "run steps"
        )

    forge_path = ROOT / ".github" / "workflows" / "forge-bootstrap.yml"
    forge_text = read_text(forge_path, results)
    forge_errors = validate_forge_workflow_text(forge_text)
    if forge_errors:
        results.fail("Forge bootstrap workflow is missing: " + ", ".join(forge_errors))
    else:
        results.passed(
            "Forge baseline and advisory latest-lane commands are in enabled "
            "blocking run steps"
        )


def check_release_checksums(results: Results) -> None:
    errors, details = validate_release_checksums(repository_root=ROOT)
    if errors:
        results.fail("v0.0.2 release checksum errors: " + "; ".join(errors))
    else:
        results.passed(
            "v0.0.2 release checksums cover "
            f"{details['evidence_files']} evidence files and match the JAR manifest"
        )


def _validate_v002_bootstrap_history() -> tuple[list[str], dict[str, object]]:
    if (ROOT / ".git").exists():
        return validate_bootstrap_provenance_at_commit(
            ROOT,
            V002_HISTORICAL_RECORD_COMMIT,
        )
    # Unit-test fixtures without Git exercise the mutable validator directly.
    return validate_bootstrap_provenance(repository_root=ROOT)


def _validate_v002_final_g0_history() -> tuple[list[str], dict[str, object]]:
    if (ROOT / ".git").exists():
        return validate_v002_final_g0_review_at_commit(
            ROOT,
            V002_HISTORICAL_RECORD_COMMIT,
        )
    return validate_v002_final_g0_review(repository_root=ROOT)


def check_bootstrap_provenance(results: Results) -> None:
    errors, details = _validate_v002_bootstrap_history()
    if errors:
        results.fail("v0.0.2 bootstrap provenance errors: " + "; ".join(errors))
    else:
        results.passed(
            "v0.0.2 bootstrap provenance validates "
            f"{details['targets']} imported targets and "
            f"{details['local_assets']} local resources"
        )


def check_v002_g4_applicability(results: Results) -> None:
    errors, details = validate_v002_g4_applicability(repository_root=ROOT)
    if errors:
        results.fail("v0.0.2 G4 applicability errors: " + "; ".join(errors))
        return
    status = details.get("status")
    bundle = details.get("canonical_bundle")
    if status == "ACCEPTED" and bundle:
        results.passed(
            "v0.0.2 ADR-005 is ACCEPTED and matches canonical client evidence"
        )
    else:
        results.pending_state(
            "v0.0.2 ADR-005 is structurally valid and remains PROPOSED; "
            "G4 is unproven"
        )


def check_v002_final_g0_review(results: Results) -> None:
    errors, details = _validate_v002_final_g0_history()
    if errors:
        results.fail("v0.0.2 final-G0 review record errors: " + "; ".join(errors))
        return
    source_outcome = details.get("source_review_outcome")
    readme_outcome = details.get("readme_review_outcome")
    message = (
        "v0.0.2 final-G0 records are mechanically valid; "
        f"source={source_outcome}, README={readme_outcome}; Gate not computed"
    )
    if source_outcome == "APPROVED" and readme_outcome == "APPROVED":
        results.passed(message)
    else:
        results.pending_state(message)


def check_optional_v002_client_evidence(results: Results) -> None:
    bundle = ROOT / COMMITTED_BUNDLE
    try:
        bundle.lstat()
    except FileNotFoundError:
        results.pending_state(
            "No v0.0.2 client evidence bundle is committed; G4/G8 remain unproven"
        )
        return
    except OSError as exc:
        results.fail(
            "Cannot safely inspect the canonical v0.0.2 client evidence path: "
            f"{exc}"
        )
        return
    if _is_link(bundle):
        results.fail(
            "Canonical v0.0.2 client evidence path must not be a symlink, "
            "junction, or reparse point"
        )
        return

    provenance_errors, provenance = _validate_v002_bootstrap_history()
    if provenance_errors:
        results.fail(
            "v0.0.2 client evidence requires valid bootstrap provenance: "
            + "; ".join(provenance_errors)
        )
        return
    if provenance.get("review_status") != APPROVED_RECORD_STATUS:
        results.fail(
            "v0.0.2 client evidence requires digest-bound bootstrap provenance "
            f"status {APPROVED_RECORD_STATUS} before readiness validation"
        )
        return

    errors, record = validate_bundle(
        bundle,
        repository_root=ROOT,
        require_acceptance_ready=True,
    )
    if errors:
        results.fail("v0.0.2 client evidence errors: " + "; ".join(errors))
        return
    assert record is not None
    readiness_record = record.get("review_readiness")
    readiness = (
        readiness_record.get("status")
        if isinstance(readiness_record, dict)
        else None
    )
    if readiness != "READY_FOR_HUMAN_GATE_REVIEW":
        results.fail(
            "v0.0.2 canonical client evidence is not acceptance-ready: "
            f"{readiness}"
        )
        return
    results.passed(
        "v0.0.2 client evidence bundle is structurally valid and mechanically "
        f"{readiness}"
    )


def _gate_scalar(raw: str) -> str:
    value = raw.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in {"'", '"'}:
        return value[1:-1]
    return value


def _valid_gate_approval_timestamp(value: str) -> bool:
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
        try:
            dt.date.fromisoformat(value)
        except ValueError:
            return False
        return True
    if not re.fullmatch(
        r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:Z|[+-]\d{2}:\d{2})",
        value,
    ):
        return False
    try:
        parsed = dt.datetime.fromisoformat(value.replace("Z", "+00:00"))
        return parsed.tzinfo is not None and parsed.utcoffset() is not None
    except ValueError:
        return False


def _parse_gate_status_document(
    text: str,
    *,
    expected_version: str = "v0.0.2",
) -> tuple[list[str], dict[str, str], dict[str, str]]:
    errors: list[str] = []
    block: list[str] | None = None
    if text.count("\n") + 1 > MAX_GATE_STATUS_DOCUMENT_LINES:
        return ["GATE_STATUS.md exceeds the document line limit"], {}, {}
    lines = text.splitlines()
    yaml_starts = [
        index for index, line in enumerate(lines) if line.strip().lower() == "```yaml"
    ]
    if len(yaml_starts) != 1:
        return ["GATE_STATUS.md must contain exactly one YAML status block"], {}, {}
    for start in yaml_starts:
        candidate: list[str] = []
        for item in lines[start + 1 :]:
            if item.strip() == "```":
                block = candidate
                break
            candidate.append(item)
        break
    if block is None:
        return ["GATE_STATUS.md has no closed YAML status block"], {}, {}
    if len(block) > MAX_GATE_STATUS_BLOCK_LINES:
        return ["GATE_STATUS YAML exceeds the status-block line limit"], {}, {}

    top: dict[str, str] = {}
    gates: dict[str, str] = {}
    in_gates = False
    for line_number, line in enumerate(block, start=1):
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        if not line.startswith((" ", "\t")):
            key, separator, raw_value = line.partition(":")
            if not separator or not re.fullmatch(r"[a-z][a-z0-9_]*", key):
                errors.append(
                    f"GATE_STATUS YAML line {line_number} has an unsupported key"
                )
                in_gates = False
                continue
            if key in top:
                errors.append(f"GATE_STATUS YAML repeats top-level key {key}")
            top[key] = _gate_scalar(raw_value)
            in_gates = key == "gates" and not raw_value.strip()
            continue
        if in_gates:
            match = re.fullmatch(r"  (G[0-9]+):\s*([A-Z_]+)\s*", line)
            if not match:
                errors.append(
                    f"GATE_STATUS YAML line {line_number} has an invalid gate entry"
                )
                continue
            gate, value = match.groups()
            if gate in gates:
                errors.append(f"GATE_STATUS YAML repeats gate {gate}")
            gates[gate] = value

    for key in ("version", "status", "overall", "human_approved_by", "human_approved_at"):
        if key not in top:
            errors.append(f"GATE_STATUS YAML is missing {key}")
    expected_gates = {f"G{index}" for index in range(10)}
    missing_gates = sorted(expected_gates.difference(gates))
    extra_gates = sorted(set(gates).difference(expected_gates))
    if missing_gates:
        errors.append("GATE_STATUS YAML is missing gates: " + ", ".join(missing_gates))
    if extra_gates:
        errors.append("GATE_STATUS YAML has unsupported gates: " + ", ".join(extra_gates))
    if top.get("version") not in {None, expected_version}:
        errors.append(f"GATE_STATUS YAML version must be {expected_version}")
    if top.get("status") not in {
        None,
        "IN_PROGRESS",
        "BLOCKED",
        "READY_FOR_AUDIT",
        "PASSED",
    }:
        errors.append("GATE_STATUS YAML has an unsupported version status")
    if top.get("overall") not in {
        None,
        "IN_PROGRESS",
        "BLOCKED",
        "READY_FOR_AUDIT",
        "PASS",
        "PASSED",
    }:
        errors.append("GATE_STATUS YAML has an unsupported overall status")
    allowed_gate_states = {
        "NOT_STARTED",
        "IN_PROGRESS",
        "BLOCKED",
        "READY_FOR_HUMAN_REVIEW",
        "NOT_APPLICABLE",
        "PASS",
    }
    invalid_states = sorted(
        f"{gate}={value}"
        for gate, value in gates.items()
        if value not in allowed_gate_states
    )
    if invalid_states:
        errors.append(
            "GATE_STATUS YAML has unsupported Gate states: "
            + ", ".join(invalid_states)
        )
    if len(errors) > MAX_GATE_STATUS_ERRORS:
        omitted = len(errors) - MAX_GATE_STATUS_ERRORS
        errors = errors[:MAX_GATE_STATUS_ERRORS] + [
            f"GATE_STATUS validation omitted {omitted} additional bounded errors"
        ]
    return errors, top, gates


def validate_v002_gate_status_text(
    text: str,
    *,
    final_g0_details: dict[str, object] | None = None,
    g4_details: dict[str, object] | None = None,
) -> list[str]:
    """Reject v0.0.2 Gate claims that overstate the bound evidence."""

    errors, top, gates = _parse_gate_status_document(text)
    if errors:
        return errors
    final_g0_details = final_g0_details or {}
    g4_details = g4_details or {}
    source_approved = final_g0_details.get("source_review_outcome") == "APPROVED"
    readme_approved = final_g0_details.get("readme_review_outcome") == "APPROVED"
    canonical_bundle_ready = bool(g4_details.get("canonical_bundle"))
    g4_accepted = (
        g4_details.get("status") == "ACCEPTED" and canonical_bundle_ready
    )
    reviewer = top.get("human_approved_by", "").strip()
    reviewed_at = top.get("human_approved_at", "").strip()
    reviewer_well_formed = bool(
        re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]{0,63}", reviewer)
    )
    human_approved = bool(
        reviewer_well_formed
        and reviewer in AUTHORIZED_RELEASE_REVIEWERS
        and _valid_gate_approval_timestamp(reviewed_at)
    )
    if (reviewer or reviewed_at) and not human_approved:
        errors.append(
            "human approval fields must contain an authorized reviewer identifier "
            "and real ISO date/time"
        )

    required = ("G0", "G1", "G2", "G3", "G4", "G8", "G9")
    waived = [gate for gate in required if gates.get(gate) == "NOT_APPLICABLE"]
    if waived:
        errors.append(
            "v0.0.2 Required Gates cannot be NOT_APPLICABLE: " + ", ".join(waived)
        )
    out_of_scope = [
        gate for gate in ("G5", "G6", "G7") if gates.get(gate) != "NOT_APPLICABLE"
    ]
    if out_of_scope:
        errors.append(
            "v0.0.2 out-of-scope Gates must remain NOT_APPLICABLE: "
            + ", ".join(out_of_scope)
        )

    if gates.get("G0") == "PASS" and not (source_approved and readme_approved):
        errors.append("G0 cannot be PASS until both final-G0 reviews are APPROVED")
    if gates.get("G4") == "PASS" and not g4_accepted:
        errors.append(
            "G4 cannot be PASS until ADR-005 is ACCEPTED with canonical valid evidence"
        )
    if gates.get("G8") in {"READY_FOR_HUMAN_REVIEW", "PASS"} and not (
        canonical_bundle_ready
    ):
        errors.append(
            "G8 cannot be READY_FOR_HUMAN_REVIEW or PASS without canonical valid "
            "client evidence"
        )
    if gates.get("G8") == "PASS" and not human_approved:
        errors.append(
            "G8 cannot be PASS from mechanical readiness alone; explicit human "
            "approval fields are required"
        )
    if gates.get("G9") == "PASS" and not human_approved:
        errors.append(
            "G9 cannot be PASS without human_approved_by and human_approved_at"
        )

    status_passed = top.get("status") == "PASSED"
    overall_passed = top.get("overall") in {"PASS", "PASSED"}
    if status_passed != overall_passed:
        errors.append("version status PASSED and overall PASS/PASSED must agree")
    if status_passed or overall_passed:
        unresolved = [gate for gate in required if gates.get(gate) != "PASS"]
        if unresolved:
            errors.append(
                "version cannot be PASSED while Required Gates are unresolved: "
                + ", ".join(unresolved)
            )
        if not (
            source_approved
            and readme_approved
            and g4_accepted
            and canonical_bundle_ready
            and human_approved
        ):
            errors.append("version cannot be PASSED while bound evidence is unresolved")
    return errors


def check_v002_gate_status(results: Results) -> None:
    final_errors, final_details = _validate_v002_final_g0_history()
    g4_errors, g4_details = validate_v002_g4_applicability(repository_root=ROOT)
    historical = ROOT / "docs/releases/v0.0.2/GATE-STATUS.md"
    text = read_text(
        historical if historical.exists() else ROOT / "docs/status/GATE_STATUS.md",
        results,
    )
    errors = validate_v002_gate_status_text(
        text,
        final_g0_details={} if final_errors else final_details,
        g4_details={} if g4_errors else g4_details,
    )
    if errors:
        results.fail("v0.0.2 Gate status contradictions: " + "; ".join(errors))
    else:
        results.passed(
            "v0.0.2 Gate status does not overstate final-G0, G4, G8, or G9 evidence"
        )


def validate_v010_gate_status_text(
    text: str,
    *,
    asset_details: dict[str, object] | None = None,
    release_details: dict[str, object] | None = None,
) -> list[str]:
    """Reject v0.1.0 Gate claims that exceed provenance/resource evidence."""

    errors, top, gates = _parse_gate_status_document(
        text,
        expected_version="v0.1.0",
    )
    if errors:
        return errors
    asset_details = asset_details or {}
    release_details = release_details or {}
    provenance_approved = asset_details.get("review_status") == "APPROVED"
    resources_ready = bool(asset_details.get("resource_count"))
    artifact_ready = release_details.get("artifact_ready") is True
    server_ready = release_details.get("server_ready") is True
    client_ready = release_details.get("client_ready") is True
    checksums_ready = release_details.get("checksums_ready") is True
    reviewer = top.get("human_approved_by", "").strip()
    reviewed_at = top.get("human_approved_at", "").strip()
    human_approved = bool(
        reviewer in AUTHORIZED_RELEASE_REVIEWERS
        and _valid_gate_approval_timestamp(reviewed_at)
    )
    if (reviewer or reviewed_at) and not human_approved:
        errors.append(
            "v0.1.0 human approval fields must contain an authorized reviewer and ISO date/time"
        )

    required = ("G0", "G1", "G2", "G3", "G4", "G8", "G9")
    waived = [gate for gate in required if gates.get(gate) == "NOT_APPLICABLE"]
    if waived:
        errors.append(
            "v0.1.0 Required Gates cannot be NOT_APPLICABLE: " + ", ".join(waived)
        )
    out_of_scope = [
        gate for gate in ("G5", "G6", "G7") if gates.get(gate) != "NOT_APPLICABLE"
    ]
    if out_of_scope:
        errors.append(
            "v0.1.0 out-of-scope Gates must remain NOT_APPLICABLE: "
            + ", ".join(out_of_scope)
        )
    if gates.get("G0") == "PASS" and not provenance_approved:
        errors.append("G0 cannot be PASS before the bound provenance sample review is APPROVED")
    if gates.get("G2") in {"READY_FOR_HUMAN_REVIEW", "PASS"} and not resources_ready:
        errors.append("G2 cannot be ready/pass without a valid complete managed resource set")
    if gates.get("G1") == "PASS" and not artifact_ready:
        errors.append("G1 cannot be PASS without artifact/rebuild evidence")
    if gates.get("G4") == "PASS" and not server_ready:
        errors.append("G4 cannot be PASS without matching-client dedicated-server evidence")
    if gates.get("G8") == "PASS" and not (human_approved and client_ready):
        errors.append("G8 cannot be PASS without explicit owner approval and client evidence")
    if gates.get("G9") == "PASS" and not (human_approved and checksums_ready):
        errors.append("G9 cannot be PASS without explicit owner approval and release checksums")

    status_passed = top.get("status") == "PASSED"
    overall_passed = top.get("overall") in {"PASS", "PASSED"}
    if status_passed != overall_passed:
        errors.append("v0.1.0 status PASSED and overall PASS/PASSED must agree")
    if status_passed or overall_passed:
        unresolved = [gate for gate in required if gates.get(gate) != "PASS"]
        if unresolved:
            errors.append(
                "v0.1.0 cannot be PASSED while Required Gates are unresolved: "
                + ", ".join(unresolved)
            )
        if not (
            provenance_approved
            and resources_ready
            and artifact_ready
            and server_ready
            and client_ready
            and checksums_ready
            and human_approved
        ):
            errors.append("v0.1.0 cannot be PASSED without bound evidence and human approval")
    return errors


def check_v010_asset_baseline(results: Results) -> None:
    errors, details = validate_v010_asset_baseline(repository_root=ROOT)
    if errors:
        results.fail("v0.1.0 asset/provenance baseline errors: " + "; ".join(errors))
        details = {}
    else:
        results.passed(
            "v0.1.0 imported and generated resources have exact provenance, hashes, and references"
        )
        if details.get("review_status") == "PENDING_HUMAN_REVIEW":
            results.pending_state(
                "v0.1.0 provenance mechanics are ready; human ten-entry source sample review remains"
            )
        elif details.get("review_status") != "APPROVED":
            results.fail("v0.1.0 provenance review requires changes")

    release_errors, release_details = validate_v010_release_evidence(repository_root=ROOT)
    if release_errors:
        results.fail("v0.1.0 release evidence errors: " + "; ".join(release_errors))
        release_details = {}
    else:
        results.passed(
            "v0.1.0 artifact, provenance, client, dedicated-server, and checksum evidence is valid"
        )

    historical = ROOT / "docs/releases/v0.1.0/GATE-STATUS.md"
    text = read_text(
        historical if historical.exists() else ROOT / "docs/status/GATE_STATUS.md",
        results,
    )
    gate_errors = validate_v010_gate_status_text(
        text,
        asset_details=details,
        release_details=release_details,
    )
    if gate_errors:
        results.fail("v0.1.0 Gate status contradictions: " + "; ".join(gate_errors))
    else:
        results.passed("v0.1.0 Gate status does not overstate mechanical or human evidence")


def check_v020_generated_resources(results: Results) -> None:
    errors = verify_v020_generated_manifest(
        ROOT,
        ROOT / "docs/provenance/v0.2.0-generated-resources.json",
    )
    if errors:
        results.fail("v0.2.0 generated-resource errors: " + "; ".join(errors))
    else:
        results.passed("v0.2.0 DataGen resources match the bounded authored-resource inventory")


def check_v030_generated_resources(results: Results) -> None:
    errors = verify_v030_generated_manifest(
        ROOT,
        ROOT / "docs/provenance/v0.3.0-generated-resources.json",
    )
    if errors:
        results.fail("v0.3.0 generated-resource errors: " + "; ".join(errors))
    else:
        results.passed(
            "v0.3.0 DataGen resources match the exact fixed-world inventory"
        )


def validate_v020_gate_status_text(
    text: str,
    *,
    evidence_details: dict[str, object] | None = None,
) -> list[str]:
    """Reject v0.2.0 Gate claims that exceed machine-slice evidence."""

    errors, top, gates = _parse_gate_status_document(
        text,
        expected_version="v0.2.0",
    )
    if errors:
        return errors
    evidence = evidence_details or {}
    reviewer = top.get("human_approved_by", "").strip()
    reviewed_at = top.get("human_approved_at", "").strip()
    human_approved = bool(
        reviewer in AUTHORIZED_RELEASE_REVIEWERS
        and _valid_gate_approval_timestamp(reviewed_at)
    )
    if (reviewer or reviewed_at) and not human_approved:
        errors.append(
            "v0.2.0 human approval fields must contain an authorized reviewer and ISO date/time"
        )

    required = tuple(f"G{index}" for index in range(10))
    waived = [gate for gate in required if gates.get(gate) == "NOT_APPLICABLE"]
    if waived:
        errors.append(
            "v0.2.0 Required Gates cannot be NOT_APPLICABLE: " + ", ".join(waived)
        )

    evidence_keys = {
        "G0": "provenance_ready",
        "G1": "artifact_ready",
        "G2": "data_ready",
        "G3": "automated_ready",
        "G4": "server_ready",
        "G5": "persistence_ready",
        "G6": "authority_ready",
        "G7": "performance_ready",
        "G8": "client_ready",
        "G9": "docs_ready",
    }
    for gate, key in evidence_keys.items():
        if gates.get(gate) == "PASS" and evidence.get(key) is not True:
            errors.append(f"{gate} cannot be PASS without bound v0.2.0 {key} evidence")
    if gates.get("G8") == "READY_FOR_HUMAN_REVIEW" and evidence.get("client_ready") is not True:
        errors.append("G8 cannot be ready for human review without bound client evidence")
    if gates.get("G8") == "PASS" and not human_approved:
        errors.append("G8 cannot be PASS without explicit owner approval")
    if gates.get("G9") == "PASS" and not human_approved:
        errors.append("G9 cannot be PASS without explicit owner approval")

    status_passed = top.get("status") == "PASSED"
    overall_passed = top.get("overall") in {"PASS", "PASSED"}
    if status_passed != overall_passed:
        errors.append("v0.2.0 status PASSED and overall PASS/PASSED must agree")
    if status_passed or overall_passed:
        unresolved = [gate for gate in required if gates.get(gate) != "PASS"]
        if unresolved:
            errors.append(
                "v0.2.0 cannot be PASSED while Required Gates are unresolved: "
                + ", ".join(unresolved)
            )
        if not human_approved or any(evidence.get(key) is not True for key in evidence_keys.values()):
            errors.append("v0.2.0 cannot be PASSED without all bound evidence and human approval")
    return errors


def check_v020_gate_status(results: Results) -> None:
    current = read_text(ROOT / "docs/status/CURRENT_VERSION.md", results)
    historical = ROOT / "docs/releases/v0.2.0/GATE-STATUS.md"
    is_current = "current_version: v0.2.0" in current
    if not is_current and not historical.exists():
        return

    evidence_details: dict[str, object] = {}
    if historical.exists():
        release_errors, evidence_details = validate_v020_release_evidence(
            repository_root=ROOT
        )
        if release_errors:
            results.fail(
                "v0.2.0 release evidence errors: " + "; ".join(release_errors)
            )
            evidence_details = {}
        else:
            results.passed(
                "v0.2.0 artifact, automated, persistence, client, server, and checksum evidence is valid"
            )

    text = read_text(
        historical if historical.exists() else ROOT / "docs/status/GATE_STATUS.md",
        results,
    )
    errors = validate_v020_gate_status_text(
        text,
        evidence_details=evidence_details,
    )
    if errors:
        results.fail("v0.2.0 Gate status contradictions: " + "; ".join(errors))
    else:
        results.passed("v0.2.0 Gate status does not overstate machine-slice evidence")


def check_package_checksums(package_root: Path, results: Results) -> None:
    package_root = package_root.absolute()
    sums_path = package_root / "PACKAGE-SHA256SUMS.txt"
    try:
        sums_payload = read_bounded_bytes(
            sums_path,
            MAX_PACKAGE_CHECKSUM_LIST_BYTES,
            "package checksum list",
            trusted_root=package_root,
        )
        lines = sums_payload.decode("utf-8", errors="strict").splitlines()
    except (UnicodeError, ValueError) as exc:
        results.fail(f"Cannot read package checksum list: {exc}")
        return

    failures: list[str] = []
    checked = 0
    seen: set[str] = set()
    seen_casefolded: set[str] = set()
    verified_bytes = 0
    entry_count = sum(bool(line.strip()) for line in lines)
    if entry_count == 0:
        results.fail("Planning package checksum list contains no entries")
        return
    if entry_count > MAX_PACKAGE_CHECKSUM_ENTRIES:
        results.fail(
            "Planning package checksum list exceeds "
            f"{MAX_PACKAGE_CHECKSUM_ENTRIES} entries"
        )
        return
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            failures.append(f"line {line_number}: invalid checksum entry")
            continue
        expected, raw_relative = parts
        relative = raw_relative.strip()
        try:
            encoded = relative.encode("utf-8", errors="strict")
        except UnicodeError:
            failures.append(f"line {line_number}: non-UTF-8 package path")
            continue
        posix = PurePosixPath(relative)
        if (
            not relative
            or len(encoded) > MAX_PACKAGE_PATH_BYTES
            or posix.is_absolute()
            or posix.as_posix() != relative
            or "\\" in relative
            or ":" in relative
            or any(part in {"", ".", ".."} for part in posix.parts)
            or any(ord(character) < 32 or ord(character) == 127 for character in relative)
        ):
            failures.append(f"line {line_number}: unsafe package path")
            continue
        if relative in seen or relative.casefold() in seen_casefolded:
            failures.append(f"line {line_number}: duplicate package path {relative}")
            continue
        seen.add(relative)
        seen_casefolded.add(relative.casefold())
        path = package_root.joinpath(*posix.parts)
        remaining_total = MAX_PACKAGE_TOTAL_BYTES - verified_bytes
        if remaining_total < 0:
            failures.append("package checksum targets exceed the aggregate byte limit")
            break
        effective_limit = min(MAX_PACKAGE_FILE_BYTES, remaining_total)
        try:
            actual, bytes_hashed = sha256_bounded_file(
                path,
                effective_limit,
                f"package file {relative}",
                trusted_root=package_root,
            )
        except ValueError as exc:
            if effective_limit == remaining_total < MAX_PACKAGE_FILE_BYTES:
                failures.append(
                    "package checksum targets exceed the aggregate byte limit: "
                    f"{exc}"
                )
            else:
                failures.append(f"cannot verify {relative}: {exc}")
            continue
        verified_bytes += bytes_hashed
        checked += 1
        if actual != expected:
            failures.append(f"hash mismatch {relative}")
    if failures:
        results.fail("Planning package checksum errors: " + "; ".join(failures))
    else:
        results.passed(
            f"Planning package listed checksums match ({checked} files checked)"
        )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--require-approved-identity",
        action="store_true",
        help="fail unless PROJECT-CONFIG.md has identity_status APPROVED",
    )
    parser.add_argument(
        "--package-root",
        type=Path,
        help="also verify files listed by PACKAGE-SHA256SUMS.txt in a planning package",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    results = Results()
    check_required_paths(results)
    check_identity(results, args.require_approved_identity)
    check_public_statements(results)
    check_license_and_upstream(results)
    check_markdown_links(results)
    check_repository_contents(results)
    check_forge_bootstrap(results)
    check_issue_templates(results)
    check_workflow(results)
    check_bootstrap_provenance(results)
    check_v002_final_g0_review(results)
    check_optional_v002_client_evidence(results)
    check_v002_g4_applicability(results)
    check_v002_gate_status(results)
    check_release_checksums(results)
    check_v010_asset_baseline(results)
    check_v020_generated_resources(results)
    check_v030_generated_resources(results)
    check_v020_gate_status(results)
    if args.package_root:
        check_package_checksums(args.package_root, results)
    results.print_report()
    return 1 if results.failures else 0


if __name__ == "__main__":
    sys.exit(main())
