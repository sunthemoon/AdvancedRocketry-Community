#!/usr/bin/env python3
"""Validate the repository governance and documentation baseline."""

from __future__ import annotations

import argparse
import hashlib
import re
import shlex
import subprocess
import sys
import threading
from dataclasses import dataclass, field
from pathlib import Path, PurePosixPath
from urllib.parse import unquote

if __package__:
    from .collect_v002_manual_evidence import COMMITTED_BUNDLE, validate_bundle
    from .validate_bootstrap_provenance import (
        APPROVED_RECORD_STATUS,
        EXCLUDED_RESOURCE_PREFIXES,
        EXPECTED_RESOURCE_PATHS,
        RESOURCE_ROOTS,
        validate_bootstrap_provenance,
    )
    from .validate_release_checksums import validate_release_checksums
else:
    from collect_v002_manual_evidence import COMMITTED_BUNDLE, validate_bundle
    from validate_bootstrap_provenance import (
        APPROVED_RECORD_STATUS,
        EXCLUDED_RESOURCE_PREFIXES,
        EXPECTED_RESOURCE_PATHS,
        RESOURCE_ROOTS,
        validate_bootstrap_provenance,
    )
    from validate_release_checksums import validate_release_checksums


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
    "scripts/check_clean_worktree.py",
    "scripts/collect_v002_manual_evidence.py",
    "scripts/generate_v002_g0_evidence.py",
    "scripts/prepare_v002_g0_review_packet.py",
    "scripts/run_dedicated_server_smoke.py",
    "scripts/validate_bootstrap_provenance.py",
    "scripts/validate_build_artifact.py",
    "scripts/validate_release_checksums.py",
    "tests/test_check_client_imports.py",
    "tests/test_check_clean_worktree.py",
    "tests/test_collect_v002_manual_evidence.py",
    "tests/test_dedicated_server_smoke.py",
    "tests/test_generate_v002_g0_evidence.py",
    "tests/test_prepare_v002_g0_review_packet.py",
    "tests/test_validate_bootstrap_provenance.py",
    "tests/test_validate_build_artifact.py",
    "tests/test_validate_release_checksums.py",
    "tests/test_validate_repository.py",
    "src/main/java/io/github/sunthemoon/advancedrocketrycommunity/AdvancedRocketryCommunity.java",
    "src/main/resources/META-INF/mods.toml",
    "src/main/resources/pack.mcmeta",
    "src/main/resources/advancedrocketrycommunity.png",
    "src/generated/resources/data/advancedrocketrycommunity/structures/empty.nbt",
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
MAX_TRACKED_MARKDOWN_INVENTORY_BYTES = 1024 * 1024
MAX_TRACKED_MARKDOWN_FILES = 4096
MAX_TRACKED_MARKDOWN_PATH_BYTES = 4096
MAX_MARKDOWN_FILE_BYTES = 2 * 1024 * 1024
IDENTITY_STATUS = re.compile(r'identity_status:\s*"([A-Z_]+)"')
UPSTREAM_COMMIT = re.compile(r"upstream_commit:\s*([0-9a-f]{40})\b")
V001_EVIDENCE_PREFIX = "docs/releases/v0.0.1/evidence/"
V001_EVIDENCE_MAX_BYTES = 2 * 1024 * 1024
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
        self.warnings: list[str] = []
        self.failures: list[str] = []

    def passed(self, message: str) -> None:
        self.passes.append(message)

    def warn(self, message: str) -> None:
        self.warnings.append(message)

    def fail(self, message: str) -> None:
        self.failures.append(message)

    def print_report(self) -> None:
        for message in self.passes:
            print(f"[PASS] {message}")
        for message in self.warnings:
            print(f"[WARN] {message}")
        for message in self.failures:
            print(f"[FAIL] {message}")
        print(
            "Summary: "
            f"{len(self.passes)} passed, "
            f"{len(self.warnings)} warnings, "
            f"{len(self.failures)} failed"
        )


def read_text(path: Path, results: Results) -> str:
    try:
        return path.read_text(encoding="utf-8")
    except (OSError, UnicodeError) as exc:
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


def tracked_markdown_files(repository_root: Path = ROOT) -> list[Path]:
    """Return the bounded, Git-indexed Markdown inventory for validation."""

    repository_root = repository_root.resolve()
    command = [
        "git",
        "-c",
        f"safe.directory={repository_root.as_posix()}",
        "-C",
        str(repository_root),
        "ls-files",
        "-z",
        "--cached",
        "--",
        "*.md",
    ]
    try:
        process = subprocess.Popen(
            command,
            stdin=subprocess.DEVNULL,
            stdout=subprocess.PIPE,
            stderr=subprocess.DEVNULL,
        )
    except OSError as exc:
        raise ValueError(f"cannot enumerate tracked Markdown files with Git: {exc}") from exc
    assert process.stdout is not None
    timed_out = threading.Event()

    def terminate_on_timeout() -> None:
        if process.poll() is None:
            timed_out.set()
            process.kill()

    timer = threading.Timer(30, terminate_on_timeout)
    timer.daemon = True
    timer.start()
    try:
        payload = process.stdout.read(MAX_TRACKED_MARKDOWN_INVENTORY_BYTES + 1)
        if len(payload) > MAX_TRACKED_MARKDOWN_INVENTORY_BYTES:
            raise ValueError("tracked Markdown inventory exceeds the byte limit")
        return_code = process.wait()
        if timed_out.is_set():
            raise ValueError("tracked Markdown inventory query timed out")
        if return_code != 0:
            raise ValueError(
                f"tracked Markdown inventory query failed with exit {return_code}"
            )
    finally:
        timer.cancel()
        process.stdout.close()
        if process.poll() is None:
            process.kill()
        process.wait()

    raw_names = [name for name in payload.split(b"\0") if name]
    if len(raw_names) > MAX_TRACKED_MARKDOWN_FILES:
        raise ValueError("tracked Markdown inventory exceeds the file-count limit")

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
            or any(part in {"", ".", ".."} for part in relative.parts)
        ):
            raise ValueError(f"tracked Markdown inventory contains an unsafe path: {name}")
        path = repository_root.joinpath(*relative.parts)
        if path.suffix.lower() != ".md":
            raise ValueError(
                f"Git returned a non-Markdown path for the Markdown inventory: {name}"
            )
        paths.append(path)
    return paths


def markdown_link_errors(
    repository_root: Path, paths: list[Path]
) -> tuple[list[str], int]:
    repository_root = repository_root.resolve()
    broken: list[str] = []
    checked = 0
    for path in sorted(paths):
        try:
            resolved = path.resolve(strict=True)
            relative = resolved.relative_to(repository_root)
            with resolved.open("rb") as stream:
                payload = stream.read(MAX_MARKDOWN_FILE_BYTES + 1)
        except (OSError, ValueError) as exc:
            broken.append(f"{path}: cannot read tracked Markdown file: {exc}")
            continue
        if len(payload) > MAX_MARKDOWN_FILE_BYTES:
            broken.append(
                f"{relative.as_posix()}: tracked Markdown file exceeds "
                f"{MAX_MARKDOWN_FILE_BYTES} bytes"
            )
            continue
        try:
            text = payload.decode("utf-8")
        except UnicodeError as exc:
            broken.append(
                f"{relative.as_posix()}: cannot decode tracked Markdown as UTF-8: {exc}"
            )
            continue

        in_fence = False
        for line_number, source_line in enumerate(text.splitlines(), start=1):
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
                checked += 1
                candidate = (resolved.parent / target).resolve()
                if (
                    not candidate.is_relative_to(repository_root)
                    or not candidate.exists()
                ):
                    broken.append(f"{relative.as_posix()}:{line_number} -> {target}")
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


def repository_files() -> list[Path]:
    command = [
        "git",
        "-c",
        f"safe.directory={ROOT.as_posix()}",
        "-C",
        str(ROOT),
        "ls-files",
        "-z",
        "--cached",
        "--others",
        "--exclude-standard",
    ]
    try:
        completed = subprocess.run(
            command,
            check=True,
            capture_output=True,
            text=False,
        )
        names = [name for name in completed.stdout.split(b"\0") if name]
        return [ROOT / name.decode("utf-8") for name in names]
    except (OSError, subprocess.CalledProcessError, UnicodeError):
        return [path for path in ROOT.rglob("*") if path.is_file() and ".git" not in path.parts]


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
    """Return every distributable source resource absent from provenance."""

    return sorted(
        path
        for path in paths
        if path.startswith(RESOURCE_ROOTS)
        and not path.startswith(EXCLUDED_RESOURCE_PREFIXES)
        and path not in EXPECTED_RESOURCE_PATHS
    )


def check_repository_contents(results: Results) -> None:
    files = repository_files()
    relative = [path.relative_to(ROOT).as_posix() for path in files]
    forbidden = [path for path in relative if path.lower().endswith(".class")]
    approved_wrappers: set[str] = set()
    for path in files:
        binary_relative = path.relative_to(ROOT).as_posix()
        if not binary_relative.lower().endswith(".jar"):
            continue
        try:
            binary_content = path.read_bytes()
        except OSError as exc:
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
        try:
            content = path.read_bytes()
        except OSError as exc:
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
        logo = (ROOT / BOOTSTRAP_LOGO_PATH).read_bytes()
        if hashlib.sha256(logo).hexdigest() != BOOTSTRAP_LOGO_SHA256:
            failures.append(f"{BOOTSTRAP_LOGO_PATH}: unexpected SHA-256")
    except OSError as exc:
        failures.append(f"{BOOTSTRAP_LOGO_PATH}: {exc}")

    for relative in THIRD_PARTY_LICENSE_SHA256:
        try:
            content = (ROOT / relative).read_bytes()
            if not is_approved_third_party_license(relative, content):
                failures.append(f"{relative}: unexpected SHA-256")
        except OSError as exc:
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
) -> bool:
    steps = _job_action_steps(job, action)
    if len(steps) != 1:
        return False
    actual_inputs = {
        key.removeprefix("with."): value
        for key, value in steps[0].fields.items()
        if key.startswith("with.")
    }
    return actual_inputs == expected_inputs


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
    action_contracts = (
        (
            "actions/checkout@v7",
            {"fetch-depth": "0", "persist-credentials": "false"},
        ),
        ("actions/setup-python@v7", {"python-version": "3.12"}),
        (
            "actions/upload-artifact@v7",
            {
                "name": "v0.0.2-g0-review-packet-${{ github.sha }}",
                "if-no-files-found": "error",
                "include-hidden-files": "true",
                "path": "build/v0.0.2-g0-review-packet/",
            },
        ),
    )
    for action, inputs in action_contracts:
        if not _job_has_exact_action_contract(job, action, inputs):
            errors.append(f"exact enabled action contract {action}")

    packet_commands = (
        (
            "python",
            "-I",
            "-S",
            "-c",
            "from pathlib import Path; Path('build').mkdir(exist_ok=True)",
        ),
        (
            "python",
            "-I",
            "-S",
            "scripts/prepare_v002_g0_review_packet.py",
            "generate",
            "--commit",
            "$GITHUB_SHA",
            "--output",
            "build/v0.0.2-g0-review-packet",
        ),
        (
            "python",
            "-I",
            "-S",
            "scripts/prepare_v002_g0_review_packet.py",
            "verify",
            "--commit",
            "$GITHUB_SHA",
            "--packet",
            "build/v0.0.2-g0-review-packet",
        ),
    )
    packet_steps = [
        step
        for step in _required_steps(job)
        if tuple(_run_commands(step.fields.get("run", ""))) == packet_commands
    ]
    if len(packet_steps) != 1:
        errors.append(
            "exact isolated G0 review-packet setup/generate/verify command sequence"
        )

    for command in (
        ("python", "-m", "unittest", "discover", "-s", "tests", "-v"),
        ("python", "scripts/validate_bootstrap_provenance.py"),
        *packet_commands,
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
        for action in (
            "actions/checkout@v7",
            "actions/setup-java@v6",
            "gradle/actions/setup-gradle@v6",
            "actions/upload-artifact@v7",
        ):
            if not _job_has_action(baseline, action):
                errors.append(f"baseline enabled action {action}")
        checkout_steps = _job_action_steps(baseline, "actions/checkout@v7")
        if not any(
            step.fields.get("with.persist-credentials") == "false"
            for step in checkout_steps
        ):
            errors.append("baseline checkout persist-credentials false")
        baseline_commands = (
            ("chmod", "+x", "./gradlew"),
            ("./gradlew", "clean", "build", "--no-daemon", "--stacktrace"),
            ("python", "scripts/validate_bootstrap_provenance.py"),
            (
                "python",
                "scripts/validate_build_artifact.py",
                "build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar",
                "--content-manifest",
                "build/release-evidence/jar-content-manifest.json",
            ),
            (
                "python",
                "scripts/validate_release_checksums.py",
                "--artifact",
                "build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar",
            ),
            (
                "python",
                "scripts/generate_v002_g0_evidence.py",
                "verify",
                "build/libs/advancedrocketry-community-1.20.1-0.0.2-dev.jar",
                "build/libs/advancedrocketry-community-1.20.1-0.0.2-dev-sources.jar",
                "--evidence-dir",
                "docs/releases/v0.0.2/evidence/g0-mechanical",
            ),
            ("python", "scripts/check_client_imports.py"),
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
                "--evidence-dir",
                "build/dedicated-server-smoke/evidence",
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
        if latest.env.get("ORG_GRADLE_PROJECT_forge_version") != "47.4.23":
            errors.append("latest-compatibility Forge 47.4.23 environment")
        for action in (
            "actions/checkout@v7",
            "actions/setup-java@v6",
            "gradle/actions/setup-gradle@v6",
        ):
            if not _job_has_action(
                latest, action, require_blocking_job=False
            ):
                errors.append(f"latest enabled action {action}")
        checkout_steps = _job_action_steps(
            latest,
            "actions/checkout@v7",
            require_blocking_job=False,
        )
        if not any(
            step.fields.get("with.persist-credentials") == "false"
            for step in checkout_steps
        ):
            errors.append("latest checkout persist-credentials false")
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


def check_bootstrap_provenance(results: Results) -> None:
    errors, details = validate_bootstrap_provenance(repository_root=ROOT)
    if errors:
        results.fail("v0.0.2 bootstrap provenance errors: " + "; ".join(errors))
    else:
        results.passed(
            "v0.0.2 bootstrap provenance validates "
            f"{details['targets']} imported targets and "
            f"{details['local_assets']} local resources"
        )


def check_optional_v002_client_evidence(results: Results) -> None:
    bundle = ROOT / COMMITTED_BUNDLE
    if not bundle.exists() and not bundle.is_symlink():
        results.passed(
            "No v0.0.2 client evidence bundle is committed; G4/G8 remain unproven"
        )
        return

    provenance_errors, provenance = validate_bootstrap_provenance(repository_root=ROOT)
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

    errors, record = validate_bundle(bundle, repository_root=ROOT)
    if errors:
        results.fail("v0.0.2 client evidence errors: " + "; ".join(errors))
        return
    assert record is not None
    results.passed(
        "v0.0.2 client evidence bundle is structurally valid; readiness is "
        f"{record['review_readiness']['status']}"
    )


def check_package_checksums(package_root: Path, results: Results) -> None:
    package_root = package_root.resolve()
    sums_path = package_root / "PACKAGE-SHA256SUMS.txt"
    try:
        lines = sums_path.read_text(encoding="utf-8").splitlines()
    except (OSError, UnicodeError) as exc:
        results.fail(f"Cannot read package checksum list: {exc}")
        return

    failures: list[str] = []
    checked = 0
    for line_number, line in enumerate(lines, start=1):
        if not line.strip():
            continue
        parts = line.split(None, 1)
        if len(parts) != 2 or not re.fullmatch(r"[0-9a-f]{64}", parts[0]):
            failures.append(f"line {line_number}: invalid checksum entry")
            continue
        expected, relative = parts
        path = package_root / relative.strip()
        if not path.is_file():
            failures.append(f"missing {relative.strip()}")
            continue
        actual = hashlib.sha256(path.read_bytes()).hexdigest()
        checked += 1
        if actual != expected:
            failures.append(f"hash mismatch {relative.strip()}")
    if failures:
        results.fail("Planning package checksum errors: " + "; ".join(failures))
    else:
        results.passed(f"Planning package checksums match ({checked} files checked)")


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
        help="also verify PACKAGE-SHA256SUMS.txt under the supplied planning package",
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
    check_optional_v002_client_evidence(results)
    check_release_checksums(results)
    if args.package_root:
        check_package_checksums(args.package_root, results)
    results.print_report()
    return 1 if results.failures else 0


if __name__ == "__main__":
    sys.exit(main())
