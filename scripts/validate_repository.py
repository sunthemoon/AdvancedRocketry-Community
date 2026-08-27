#!/usr/bin/env python3
"""Validate the repository governance and documentation baseline."""

from __future__ import annotations

import argparse
import hashlib
import re
import subprocess
import sys
from pathlib import Path
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parents[1]

REQUIRED_PATHS = (
    ".gitattributes",
    ".gitignore",
    "00-READ-ME-FIRST.md",
    "AGENTS.md",
    "BRANDING_AND_AFFILIATION.md",
    "CODE_OF_CONDUCT.md",
    "CONTRIBUTING.md",
    "LICENSE",
    "NOTICE.md",
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
    "scripts/run_dedicated_server_smoke.py",
    "scripts/validate_build_artifact.py",
    "tests/test_check_client_imports.py",
    "tests/test_dedicated_server_smoke.py",
    "tests/test_validate_build_artifact.py",
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
    "docs/work/v0.0.1-implementation-log.md",
    "docs/work/v0.0.2-implementation-log.md",
    "docs/work/v0.0.2-test-machine-handoff.md",
    "docs/releases/v0.0.2/RELEASE-EVIDENCE.md",
    "docs/releases/v0.0.2/TEST-REPORT.md",
    "docs/releases/v0.0.2/MANUAL-TEST.md",
    "docs/releases/v0.0.2/KNOWN-ISSUES.md",
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
IDENTITY_STATUS = re.compile(r'identity_status:\s*"([A-Z_]+)"')
UPSTREAM_COMMIT = re.compile(r"upstream_commit:\s*([0-9a-f]{40})\b")
V001_EVIDENCE_PREFIX = "docs/releases/v0.0.1/evidence/"
V001_EVIDENCE_MAX_BYTES = 2 * 1024 * 1024
GRADLE_WRAPPER_PATH = "gradle/wrapper/gradle-wrapper.jar"
GRADLE_WRAPPER_SHA256 = "ed2c26eba7cfb93cc2b7785d05e534f07b5b48b5e7fc941921cd098628abca58"
BOOTSTRAP_LOGO_PATH = "src/main/resources/advancedrocketrycommunity.png"
BOOTSTRAP_LOGO_SHA256 = "c5c6fbc63113a51da1ec28ef1227b358b41030b09cae4103f160f37d3a343690"


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


def check_markdown_links(results: Results) -> None:
    broken: list[str] = []
    checked = 0
    for path in sorted(ROOT.rglob("*.md")):
        if ".git" in path.parts:
            continue
        for line_number, line in iter_markdown_prose(path, results):
            for match in MARKDOWN_LINK.finditer(line):
                target = normalize_link_target(match.group(1))
                if not target or target.startswith(("#", "http://", "https://", "mailto:")):
                    continue
                target = target.split("#", 1)[0].split("?", 1)[0]
                if not target:
                    continue
                checked += 1
                candidate = (path.parent / target).resolve()
                if not candidate.is_relative_to(ROOT) or not candidate.exists():
                    broken.append(f"{path.relative_to(ROOT)}:{line_number} -> {target}")
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

    forbidden.extend(unaudited_evidence)
    if forbidden:
        results.fail("Forbidden legacy, unaudited evidence, or unapproved binary files: " + ", ".join(sorted(set(forbidden))))
    else:
        results.passed(
            "No forbidden legacy source, unapproved binary, or unaudited evidence found"
            f" ({len(approved_wrappers)} wrapper JAR and "
            f"{len(audited_evidence)} evidence screenshots verified)"
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


def check_workflow(results: Results) -> None:
    path = ROOT / ".github" / "workflows" / "repository-docs.yml"
    text = read_text(path, results)
    required = (
        "name: Repository governance",
        "on:",
        "jobs:",
        "uses: actions/checkout@v7",
        "uses: actions/setup-python@v7",
        "python -m unittest discover -s tests -v",
        "python scripts/validate_repository.py --require-approved-identity",
    )
    missing = [item for item in required if item not in text]
    if "\t" in text:
        missing.append("tab-free indentation")
    if missing:
        results.fail("Repository workflow is missing: " + ", ".join(missing))
    else:
        results.passed("Repository governance workflow invokes the strict validator")

    forge_path = ROOT / ".github" / "workflows" / "forge-bootstrap.yml"
    forge_text = read_text(forge_path, results)
    forge_required = (
        "name: Forge bootstrap",
        "permissions:",
        "contents: read",
        "uses: actions/checkout@v7",
        "persist-credentials: false",
        "uses: actions/setup-java@v6",
        "uses: gradle/actions/setup-gradle@v6",
        "./gradlew clean build --no-daemon --stacktrace",
        "python scripts/validate_build_artifact.py",
        "python scripts/check_client_imports.py",
        "./gradlew runData --no-daemon --stacktrace",
        "git diff --exit-code",
        "./gradlew runGameTestServer --no-daemon --stacktrace",
        "python scripts/run_dedicated_server_smoke.py",
        'ORG_GRADLE_PROJECT_forge_version: "47.4.23"',
        "continue-on-error: true",
        "uses: actions/upload-artifact@v7",
    )
    forge_missing = [item for item in forge_required if item not in forge_text]
    if "\t" in forge_text:
        forge_missing.append("tab-free indentation")
    if forge_missing:
        results.fail("Forge bootstrap workflow is missing: " + ", ".join(forge_missing))
    else:
        results.passed("Forge baseline and advisory latest-lane workflow is present")


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
    if args.package_root:
        check_package_checksums(args.package_root, results)
    results.print_report()
    return 1 if results.failures else 0


if __name__ == "__main__":
    sys.exit(main())
