#!/usr/bin/env python3
"""The v1.8 provenance audit of ADR-061 section 4.7 (W62 slice S2).

    python -B scripts/validate_v180_provenance.py --jar build/libs/<artifact>.jar --output-dir <directory>

Rules (docs/work/v1.8.0-root-queue/records/W62.md, section 7):

1. Every ``assets/`` and ``data/`` entry of the JAR comes from exactly one source root: the v0.1.0 managed root
   (``src/main/resources`` with its DataGen root ``src/generated/resources``), a ``src/generated/v*`` DataGen root,
   or ``src/imported/v1.8/resources``. An entry comes from each root that holds its path with the same bytes.
2. Every file in the tree or the JAR whose SHA-256 is in ``legacy-manifest/assets.csv`` is the target of a record,
   and its latest record gives it the upstream status (or a blocked one, which rule 3 keeps out of the JAR).
3. No target whose latest record is QUARANTINED or REJECTED is packaged, by path or by bytes.
4. A target is compared against the latest record that names it. A naming that another naming of the same target
   lists in ``supersedes_target_sha256`` or ``supersedes.target_sha256`` is superseded. Otherwise, when records
   disagree, the record file added last in HEAD's history wins; records added in one commit must agree.
5. The shared v1.8 language files are checked by key, not by whole-file hash. A record vouches for its
   ``new_keys`` or, without that list, for the keys that the ordinary commit producing its recorded version added
   or changed (a version that only a merge produced needs ``new_keys``). Each vouched key must still exist with the
   value of the latest record that vouches for it. Keys that no record vouches for are DataGen output, which
   rule 1 already places.

Records are the JSON files in ``docs/provenance``: entries, related entries, fixture files, local assets and the
generated-resource inventories. Markdown records are listed but not parsed. Rule 5 and the record order read
HEAD's git history, so a shallow checkout fails where they need it. Without ``--jar``, rules 1 and 3 are not run
and rule 2 covers the tree only. The audit writes ``provenance-index.md`` and ``jar-provenance-audit.json`` and
exits 1 when an input is unreadable or a rule fails.
"""

from __future__ import annotations

import argparse
import csv
import dataclasses
import hashlib
import io
import json
import os
import re
import subprocess
import sys
import zipfile
from collections import defaultdict
from pathlib import Path, PurePosixPath
from typing import Any

ROOT = Path(__file__).resolve().parents[1]
TOOL = "scripts/validate_v180_provenance.py"
RESULT_SCHEMA = 1
INDEX_NAME = "provenance-index.md"
RESULT_NAME = "jar-provenance-audit.json"
LEGACY_MANIFEST = "legacy-manifest/assets.csv"
PROVENANCE_DIRECTORY = "docs/provenance"
MAIN_ROOT = "src/main/resources"
V010_DATAGEN_ROOT = "src/generated/resources"
DATAGEN_ROOT = re.compile(r"src/generated/v(\d+)\.(\d+)/resources")
IMPORT_ROOT = "src/imported/v1.8/resources"
SHARED_LANGUAGE = re.compile(r"src/generated/v1\.8/resources/assets/[a-z0-9_.-]+/lang/[a-z]{2,3}_[a-z]{2,4}\.json")
PACKAGED_PREFIXES = ("assets/", "data/")
UPSTREAM = "UPSTREAM_AR_MIT"
BLOCKED = ("QUARANTINED", "REJECTED")
SHA256 = re.compile(r"[0-9a-f]{64}")
ZERO_OBJECT = "0" * 40
# The record lists that name targets, with their path and SHA-256 fields. A ``targets`` list is read only in a
# generated-resource inventory; v0.0.2's bootstrap targets are checked by validate_bootstrap_provenance.py.
NAMING_LISTS = {"entries": ("target_path", "target_sha256"), "related_entries": ("target_path", "target_sha256"),
                "files": ("target_path", "target_sha256"), "local_assets": ("path", "target_sha256"),
                "targets": ("path", "sha256")}
WALK_SKIPPED = {".git", ".gradle", "build", "run", "out"}
RULES = {1: "Every packaged assets/ and data/ entry comes from exactly one source root",
         2: "Every file with a legacy-manifest SHA-256 is the target of a record",
         3: "No QUARANTINED or REJECTED target is packaged",
         4: "A target matches the latest record that names it",
         5: "The shared v1.8 language files match their records key by key"}
MAX_RECORD_BYTES = 4 * 1024 * 1024
MAX_RECORDS = 1024
MAX_MANIFEST_BYTES = 16 * 1024 * 1024
MAX_TREE_FILES = 200_000
MAX_FILE_BYTES = 256 * 1024 * 1024
MAX_JAR_BYTES = 256 * 1024 * 1024
MAX_JAR_ENTRIES = 100_000
MAX_ENTRY_BYTES = 64 * 1024 * 1024
MAX_LANGUAGE_BYTES = 8 * 1024 * 1024
MAX_LANGUAGE_VERSIONS = 2000
MAX_REPORTED_FAILURES = 5000
GIT_TIMEOUT_SECONDS = 300
CHUNK_BYTES = 1024 * 1024


@dataclasses.dataclass(frozen=True)
class Naming:
    """One record's statement that a target has a SHA-256."""

    record: str
    where: str
    target: str
    sha256: str
    status: str | None
    supersedes: frozenset[str] = frozenset()
    new_keys: tuple[str, ...] | None = None
    cites: str | None = None

    @property
    def label(self) -> str:
        return f"{PurePosixPath(self.record).name} {self.where}"


@dataclasses.dataclass
class Audit:
    """The audit's inputs, findings and file-hash cache."""

    root: Path
    failures: dict[int, list[str]] = dataclasses.field(default_factory=lambda: {rule: [] for rule in [0, *RULES]})
    checked: dict[int, int] = dataclasses.field(default_factory=lambda: dict.fromkeys(RULES, 0))
    hashes: dict[str, str | None] = dataclasses.field(default_factory=dict)
    legacy: dict[str, list[str]] = dataclasses.field(default_factory=dict)
    by_target: dict[str, list[Naming]] = dataclasses.field(default_factory=dict)
    latest: dict[str, Naming] = dataclasses.field(default_factory=dict)
    legacy_rows: dict[str, dict[str, Any]] = dataclasses.field(default_factory=dict)

    def fail(self, rule: int, message: str) -> None:
        if len(self.failures[rule]) < MAX_REPORTED_FAILURES:
            self.failures[rule].append(message)

    def sha256(self, relative: str) -> str | None:
        """The SHA-256 of a repository file, or None when it is not a regular file."""
        if relative not in self.hashes:
            path = self.root.joinpath(*PurePosixPath(relative).parts)
            if path.is_symlink() or not path.is_file():
                self.hashes[relative] = None
            elif path.stat().st_size > MAX_FILE_BYTES:
                self.fail(0, f"{relative} exceeds {MAX_FILE_BYTES} bytes and was not hashed.")
                self.hashes[relative] = None
            else:
                self.hashes[relative] = file_sha256(path)
        return self.hashes[relative]

    def legacy_hit(self, relative: str, digest: str, packaged_as: str | None = None) -> None:
        """Rule 2 for a file with a legacy-manifest SHA-256, once per file however often it is packaged."""
        row = self.legacy_rows.get(relative)
        if row is None:
            self.checked[2] += 1
            named, source = self.latest.get(relative), self.legacy[digest][0]
            row = self.legacy_rows[relative] = {"file": relative, "legacy_sources": sorted(self.legacy[digest]),
                                                "record": named.label if named else None,
                                                "status": named.status if named else None, "packaged_as": []}
            if relative not in self.by_target:
                self.fail(2, f"{relative} has the SHA-256 of legacy asset {source}, and no record names it.")
            elif named is not None and named.status != UPSTREAM and named.status not in BLOCKED:
                self.fail(2, f"{relative} has the bytes of legacy asset {source}, but its latest record "
                             f"{named.label} gives it the status {named.status}.")
        if packaged_as:
            row["packaged_as"].append(packaged_as)


class Git:
    """Read-only queries of the repository at ``root``; ``available`` is False without git or a work tree there."""

    def __init__(self, root: Path):
        self.root = root
        top = self.run("rev-parse", "--show-toplevel")
        self.available = top is not None and Path(top.decode("utf-8").strip()).resolve() == root.resolve()
        head = self.run("rev-parse", "HEAD") if self.available else None
        self.head = head.decode("ascii").strip() if head else None
        self.shallow = self.available and self.run("rev-parse", "--is-shallow-repository") == b"true\n"

    def run(self, *args: str, data: bytes | None = None) -> bytes | None:
        try:
            completed = subprocess.run(["git", "-C", str(self.root), *args], input=data, stdout=subprocess.PIPE,
                                       stderr=subprocess.DEVNULL, timeout=GIT_TIMEOUT_SECONDS, check=False)
        except (OSError, subprocess.TimeoutExpired):
            return None
        return completed.stdout if completed.returncode == 0 else None

    def files(self) -> list[str] | None:
        listed = self.run("ls-files", "-z", "--cached", "--others", "--exclude-standard") if self.available else None
        return None if listed is None else sorted({name for name in listed.decode("utf-8").split("\0") if name})

    def clean(self) -> bool | None:
        status = self.run("status", "--porcelain") if self.available else None
        return None if status is None else not status.strip()

    def record_ranks(self) -> dict[str, int]:
        """Record path -> rank, higher when the file was added later in HEAD's topological history."""
        output = self.run("log", "HEAD", "--topo-order", "--diff-filter=A", "--no-renames", "--name-only",
                          "--format=%x00%H", "--", PROVENANCE_DIRECTORY) if self.available else None
        ranks: dict[str, int] = {}
        for position, chunk in enumerate((output or b"").decode("utf-8").split("\0")[1:]):
            for line in chunk.splitlines()[1:]:
                if line.strip():
                    ranks.setdefault(line.strip(), -position)
        return ranks

    def gap(self) -> str:
        """Why a version may be missing from the history this checkout can read."""
        return ("the checkout is shallow" if self.shallow else "git is unavailable" if not self.available
                else "no commit in HEAD's history has those bytes")

    def blobs(self, oids: list[str]) -> dict[str, bytes]:
        """Blob contents by object ID, refusing a blob over the language-file limit."""
        request = ("\n".join(oids) + "\n").encode("ascii")
        sizes = self.run("cat-file", "--batch-check", data=request)
        if sizes is None:
            raise ValueError("git cat-file --batch-check failed")
        for line in sizes.decode("ascii").splitlines():
            parts = line.split()
            if len(parts) != 3 or parts[1] != "blob" or int(parts[2]) > MAX_LANGUAGE_BYTES:
                raise ValueError(f"history object {parts[0]} is not a blob within {MAX_LANGUAGE_BYTES} bytes")
        output, contents, position = self.run("cat-file", "--batch", data=request), {}, 0
        while output and position < len(output):
            end = output.index(b"\n", position)
            oid, _, size = output[position:end].decode("ascii").split()
            contents[oid] = output[end + 1:end + 1 + int(size)]
            position = end + 1 + int(size) + 1
        if set(contents) != set(oids):
            raise ValueError("git cat-file --batch did not return every history blob")
        return contents


class LanguageHistory:
    """Every version of one file in HEAD's history, and the commits that produced each version."""

    def __init__(self, git: Git, target: str):
        output = git.run("log", "HEAD", "--full-history", "-m", "--no-renames", "--raw", "--no-abbrev",
                         "--format=%x00%H %P", "--", target) if git.available else None
        # (commit, is a merge, old blob, new blob); with -m a merge has one diff per parent it differs from.
        self.transitions: list[tuple[str, bool, str, str]] = []
        for chunk in (output or b"").decode("utf-8").split("\0")[1:]:
            lines = chunk.splitlines()
            commit, *parents = lines[0].split()
            for line in lines[1:]:
                if line.startswith(":"):
                    fields = line.split("\t", 1)[0].split()
                    self.transitions.append((commit, len(parents) > 1, fields[2], fields[3]))
        oids = sorted({oid for *_, old, new in self.transitions for oid in (old, new)} - {ZERO_OBJECT})
        if len(oids) > MAX_LANGUAGE_VERSIONS:
            raise ValueError(f"{target} has more than {MAX_LANGUAGE_VERSIONS} versions in HEAD's history")
        self.contents = git.blobs(oids) if oids else {}
        self.by_sha256 = {hashlib.sha256(data).hexdigest(): oid for oid, data in self.contents.items()}

    def version(self, digest: str) -> bytes | None:
        oid = self.by_sha256.get(digest)
        return None if oid is None else self.contents[oid]

    def producers(self, digest: str) -> list[tuple[str, dict[str, str], dict[str, str]]]:
        """(commit, parent version, produced version) for each ordinary commit that produced this version. A merge
        is left out: its diff against each parent also holds the other side's changes."""
        oid = self.by_sha256.get(digest)
        return [(commit, parse_language(self.contents[old]) if old != ZERO_OBJECT else {},
                 parse_language(self.contents[new]))
                for commit, merge, old, new in self.transitions if new == oid and not merge]


def safe_relative(raw: object) -> str | None:
    if not isinstance(raw, str) or not raw or len(raw) > 1024 or "\\" in raw or ":" in raw:
        return None
    path = PurePosixPath(raw)
    if path.is_absolute() or path.as_posix() != raw or any(part in ("", ".", "..") for part in path.parts):
        return None
    return None if any(ord(character) < 32 or ord(character) == 127 for character in raw) else raw


def parse_language(data: bytes) -> dict[str, str]:
    value = json.loads(data.decode("utf-8"))
    if not isinstance(value, dict) or not all(isinstance(key, str) and isinstance(item, str)
                                              for key, item in value.items()):
        raise ValueError("a language file must be a JSON object of strings")
    return value


def read_limited(path: Path, limit: int) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"{path.name} is missing or not a regular file")
    if path.stat().st_size > limit:
        raise ValueError(f"{path.name} exceeds {limit} bytes")
    return path.read_bytes()


def load_manifest(root: Path) -> dict[str, list[str]]:
    text = read_limited(root / LEGACY_MANIFEST, MAX_MANIFEST_BYTES).decode("utf-8")
    reader = csv.DictReader(io.StringIO(text, newline=""))
    if not reader.fieldnames or not {"source_path", "sha256"} <= set(reader.fieldnames):
        raise ValueError(f"{LEGACY_MANIFEST} lacks the source_path and sha256 columns")
    legacy: dict[str, list[str]] = defaultdict(list)
    for number, row in enumerate(reader, start=2):
        if not SHA256.fullmatch(row.get("sha256") or ""):
            raise ValueError(f"{LEGACY_MANIFEST} line {number} has no lowercase SHA-256")
        legacy[row["sha256"]].append(row["source_path"])
    return dict(legacy)


def load_records(audit: Audit) -> tuple[list[dict[str, Any]], list[Naming], list[str]]:
    """Every JSON record with its namings, and the Markdown records, which are listed but not parsed."""
    directory = audit.root / PROVENANCE_DIRECTORY
    paths = sorted(directory.glob("*.json")) if directory.is_dir() else []
    if len(paths) > MAX_RECORDS:
        raise ValueError(f"{PROVENANCE_DIRECTORY} holds more than {MAX_RECORDS} JSON records")
    records, namings = [], []
    for path in paths:
        relative = f"{PROVENANCE_DIRECTORY}/{path.name}"
        try:
            record = json.loads(read_limited(path, MAX_RECORD_BYTES).decode("utf-8"))
        except (OSError, UnicodeError, ValueError) as error:
            audit.fail(0, f"{relative} cannot be read as JSON: {error}")
            continue
        if not isinstance(record, dict):
            audit.fail(0, f"{relative} is not a JSON object.")
            continue
        found = list(record_namings(audit, relative, record))
        review = record.get("review")
        records.append({"path": relative, "batch": record.get("batch_id") or record.get("batch"),
                        "schema_version": record.get("schema_version"),
                        "kind": "inventory" if "generated_root" in record else
                        "provenance" if "entries" in record else "other",
                        "review": review.get("status") if isinstance(review, dict) else None,
                        "namings": len(found)})
        namings += found
    markdown = sorted(f"{PROVENANCE_DIRECTORY}/{path.name}" for path in directory.glob("*.md")
                      if path.name != "README.md") if directory.is_dir() else []
    return records, resolve_citations(audit, namings), markdown


def record_namings(audit: Audit, relative: str, record: dict[str, Any]):
    for key, (path_field, hash_field) in NAMING_LISTS.items():
        items = record.get(key)
        if items is None or (key == "targets" and "generated_root" not in record):
            continue
        if not isinstance(items, list):
            audit.fail(0, f"{relative} {key} is not a list.")
            continue
        for index, item in enumerate(items):
            where = f"{key}[{index}]"
            item = item if isinstance(item, dict) else {}
            target, digest = safe_relative(item.get(path_field)), item.get(hash_field)
            if target is None or not isinstance(digest, str) or not SHA256.fullmatch(digest):
                audit.fail(0, f"{relative} {where} does not name a safe target path and a lowercase SHA-256.")
                continue
            supersedes = [item.get("supersedes_target_sha256")]
            if isinstance(item.get("supersedes"), dict):
                supersedes.append(item["supersedes"].get("target_sha256"))
            new_keys = item.get("new_keys")
            if new_keys is not None and (not isinstance(new_keys, list) or len(set(new_keys)) != len(new_keys)
                                         or not all(isinstance(name, str) and name for name in new_keys)):
                audit.fail(0, f"{relative} {where} new_keys is not a list of unique key names.")
                new_keys = None
            cites = safe_relative(item.get("record")) if key == "related_entries" else None
            if key == "related_entries" and cites is None:
                audit.fail(0, f"{relative} {where} does not cite a record.")
                continue
            status = None if key == "related_entries" else item.get("status", record.get("status"))
            yield Naming(relative, where, target, digest, status if isinstance(status, str) else None,
                         frozenset(value for value in supersedes if isinstance(value, str)),
                         tuple(new_keys) if new_keys is not None else None, cites)


def resolve_citations(audit: Audit, namings: list[Naming]) -> list[Naming]:
    """A related entry re-pins a target that its cited record names; it takes that naming's status."""
    by_record: dict[tuple[str, str], list[Naming]] = defaultdict(list)
    for naming in namings:
        if naming.cites is None:
            by_record[(naming.record, naming.target)].append(naming)
    resolved = []
    for naming in namings:
        if naming.cites is not None:
            cited = by_record.get((naming.cites, naming.target), [])
            if not cited:
                audit.fail(4, f"{naming.label} cites {naming.cites}, which does not name {naming.target}.")
                continue
            same = [item for item in cited if item.sha256 == naming.sha256]
            naming = dataclasses.replace(naming, status=(same or cited)[-1].status)
        resolved.append(naming)
    return resolved


def resolve(namings: list[Naming], ranks: dict[str, int]) -> tuple[Naming | None, str | None]:
    """The naming a target is compared against, or the reason there is none."""
    superseded = {digest for naming in namings for digest in naming.supersedes}
    live = [naming for naming in namings if naming.sha256 not in superseded]
    if not live:
        return None, "every naming of it is superseded by another"
    if len({naming.sha256 for naming in live}) > 1:
        if any(naming.record not in ranks for naming in live):
            return None, "its records name different SHA-256 values and HEAD's history does not order them"
        newest = max(ranks[naming.record] for naming in live)
        live = [naming for naming in live if ranks[naming.record] == newest]
        if len({naming.sha256 for naming in live}) > 1:
            return None, ("records added in the same commit name different SHA-256 values: "
                          + ", ".join(sorted(naming.label for naming in live)))
    # One SHA-256 is left. A blocked status wins over an approved one, then the newest record.
    return min(live, key=lambda naming: (naming.status not in BLOCKED, -ranks.get(naming.record, 0),
                                         naming.record, naming.where)), None


def check_targets(audit: Audit, by_target: dict[str, list[Naming]], ranks: dict[str, int]) -> tuple[list, dict]:
    """Rule 4 for every target but the shared language files."""
    rows, latest_by_target = [], {}
    for target in sorted(by_target):
        if SHARED_LANGUAGE.fullmatch(target):
            continue
        audit.checked[4] += 1
        latest, problem = resolve(by_target[target], ranks)
        actual = audit.sha256(target)
        if latest is None:
            audit.fail(4, f"{target}: {problem}.")
        elif actual is None:
            audit.fail(4, f"{target} is named by {latest.label} but is missing or not a regular file.")
        elif actual != latest.sha256:
            audit.fail(4, f"{target} has SHA-256 {actual}, not the {latest.sha256} of its latest record "
                          f"{latest.label}.")
        if latest is not None:
            latest_by_target[target] = latest
        older = sorted(naming.label for naming in by_target[target] if latest and naming.sha256 != latest.sha256)
        rows.append({"target": target, "latest": latest.label if latest else None,
                     "status": latest.status if latest else None, "sha256": actual,
                     "result": "PASS" if latest and actual == latest.sha256 else "FAIL", "superseded": older})
    return rows, latest_by_target


def check_language(audit: Audit, git: Git, target: str, namings: list[Naming], ranks: dict[str, int]) -> list:
    """Rule 5 for one shared language file."""
    rows, history = [], None
    try:
        current_bytes = audit.root.joinpath(*PurePosixPath(target).parts).read_bytes()
        current = parse_language(current_bytes)
    except (OSError, UnicodeError, ValueError) as error:
        audit.fail(5, f"{target} cannot be read as a language file: {error}")
        return [{"target": target, "record": naming.label, "keys_from": None, "keys": 0, "result": "FAIL"}
                for naming in namings]
    current_sha256 = hashlib.sha256(current_bytes).hexdigest()
    superseded = {digest for naming in namings for digest in naming.supersedes}
    vouchers: dict[str, list[tuple[Naming, str]]] = defaultdict(list)
    for naming in sorted(namings, key=lambda item: item.label):
        audit.checked[5] += 1
        row = {"target": target, "record": naming.label, "sha256": naming.sha256, "keys_from": None,
               "keys": 0, "result": "FAIL"}
        rows.append(row)
        if naming.sha256 in superseded:
            row.update(keys_from="superseded", result="SUPERSEDED")
            continue
        try:
            if naming.sha256 != current_sha256 and history is None:
                history = LanguageHistory(git, target)
            recorded_bytes = current_bytes if naming.sha256 == current_sha256 else history.version(naming.sha256)
            if recorded_bytes is None:
                audit.fail(5, f"{naming.label}: its recorded version {naming.sha256} of {target} cannot be found "
                              f"({git.gap()}).")
                continue
            recorded = parse_language(recorded_bytes)
            if naming.new_keys is not None:
                keys, row["keys_from"] = list(naming.new_keys), "new_keys"
            else:
                if history is None:
                    history = LanguageHistory(git, target)
                producers = history.producers(naming.sha256)
                if not producers:
                    reason = "only a merge produced it" if history.version(naming.sha256) else git.gap()
                    audit.fail(5, f"{naming.label}: no ordinary commit produced its recorded version of {target} "
                                  f"({reason}), so its keys are unknown; the record must list them in new_keys.")
                    continue
                changed = [{key for key, value in new.items() if old.get(key) != value} for _, old, new in producers]
                keys = sorted(set.intersection(*changed))
                row["keys_from"] = "commit " + ", ".join(sorted({commit[:8] for commit, _, _ in producers}))
        except (OSError, UnicodeError, ValueError) as error:
            audit.fail(5, f"{naming.label}: the history of {target} cannot be read: {error}")
            continue
        absent = [key for key in keys if key not in recorded]
        if absent:
            audit.fail(5, f"{naming.label} lists keys that its recorded version of {target} lacks: "
                          + ", ".join(absent[:5]))
            continue
        if not keys:
            audit.fail(5, f"{naming.label}: its recorded version of {target} adds or changes no key.")
            continue
        row.update(keys=len(keys), result="PASS")
        for key in keys:
            vouchers[key].append((naming, recorded[key]))
    for key in sorted(vouchers):
        candidates = vouchers[key]
        newest = max(ranks.get(naming.record, 0) for naming, _ in candidates)
        top = [(naming, value) for naming, value in candidates if ranks.get(naming.record, 0) == newest]
        naming, value = top[0]
        if len({value for _, value in top}) > 1:
            problem = (f"{target}: key {key} is vouched with different values by records added in one commit: "
                       + ", ".join(sorted(item.label for item, _ in top)))
        elif key not in current:
            problem = f"{target}: key {key}, vouched for by {naming.label}, is missing."
        elif current[key] != value:
            problem = f"{target}: key {key} no longer has the value recorded by {naming.label}."
        else:
            continue
        audit.fail(5, problem)
        for row in rows:
            if row["record"] in {item.label for item, _ in top}:
                row["result"] = "FAIL"
    return rows


def read_jar(audit: Audit, jar: Path) -> list[tuple[str, str]]:
    """The JAR's file entries with their SHA-256."""
    if jar.is_symlink() or not jar.is_file() or jar.stat().st_size > MAX_JAR_BYTES:
        audit.fail(0, f"The JAR {jar.name} is missing, not a regular file or over {MAX_JAR_BYTES} bytes.")
        return []
    entries = []
    try:
        with zipfile.ZipFile(jar) as archive:
            infos = [info for info in archive.infolist() if not info.is_dir()]
            if len(infos) > MAX_JAR_ENTRIES:
                audit.fail(0, f"The JAR has more than {MAX_JAR_ENTRIES} entries.")
                return []
            if len({info.filename for info in infos}) != len(infos):
                audit.fail(1, "The JAR holds an entry name twice, so its source is ambiguous.")
            for info in infos:
                if info.file_size > MAX_ENTRY_BYTES:
                    audit.fail(0, f"JAR entry {info.filename} exceeds {MAX_ENTRY_BYTES} bytes.")
                    continue
                digest, total = hashlib.sha256(), 0
                with archive.open(info) as handle:
                    for chunk in iter(lambda: handle.read(CHUNK_BYTES), b""):
                        total += len(chunk)
                        if total > MAX_ENTRY_BYTES:
                            raise ValueError(f"JAR entry {info.filename} inflates beyond {MAX_ENTRY_BYTES} bytes")
                        digest.update(chunk)
                entries.append((info.filename, digest.hexdigest()))
    except (OSError, ValueError, zipfile.BadZipFile) as error:
        audit.fail(0, f"The JAR {jar.name} cannot be read: {error}")
        return []
    return sorted(entries)


def check_jar(audit: Audit, jar: Path, roots: list[str]) -> tuple[dict[str, Any], dict[str, int]]:
    """Rules 1 and 3, and rule 2 for packaged files. An entry comes from each root holding its path and bytes."""
    entries, origins = read_jar(audit, jar), defaultdict(int)
    blocked = {naming.sha256: naming for naming in audit.latest.values() if naming.status in BLOCKED}
    for name, digest in entries:
        relative = safe_relative(name)
        candidates = [root for root in roots if relative and audit.sha256(f"{root}/{relative}")]
        matches = [root for root in candidates if audit.sha256(f"{root}/{relative}") == digest]
        if not name.startswith(PACKAGED_PREFIXES):
            origins["outside rule 1 (not assets/ or data/)"] += 1
        else:
            audit.checked[1] += 1
            if len(matches) == 1:
                origins[matches[0]] += 1
            elif matches:
                audit.fail(1, f"JAR entry {name} has identical bytes in {len(matches)} source roots: "
                              + ", ".join(matches) + ".")
            else:
                where = "; the path exists in " + ", ".join(candidates) + " with other bytes" if candidates else ""
                audit.fail(1, f"JAR entry {name} comes from no source root{where}.")
        source = f"{matches[0]}/{relative}" if len(matches) == 1 else None
        if digest in audit.legacy and source is not None:
            audit.legacy_hit(source, digest, name)
        elif digest in audit.legacy:
            audit.checked[2] += 1
            audit.fail(2, f"JAR entry {name} has the SHA-256 of legacy asset {audit.legacy[digest][0]} and no "
                          f"single source file, so no record can name it.")
        if source is not None:
            audit.checked[3] += 1
            named = audit.latest.get(source)
            if named is not None and named.status in BLOCKED:
                audit.fail(3, f"JAR entry {name} packages {source}, whose latest record {named.label} is "
                              f"{named.status}.")
        if digest in blocked and blocked[digest].target != source:
            audit.fail(3, f"JAR entry {name} has the bytes of {blocked[digest].target}, which "
                          f"{blocked[digest].label} marks {blocked[digest].status}.")
    jar_info = {"name": jar.name, "sha256": file_sha256(jar) if jar.is_file() else None, "entries": len(entries)}
    return jar_info, dict(sorted(origins.items()))


def source_roots(root: Path) -> list[str]:
    """The allowed roots that exist, DataGen roots in version order."""
    generated = []
    for path in (root / "src/generated").glob("v*/resources"):
        match = DATAGEN_ROOT.fullmatch(path.relative_to(root).as_posix())
        if match and path.is_dir():
            generated.append((int(match[1]), int(match[2]), match[0]))
    roots = [MAIN_ROOT, V010_DATAGEN_ROOT, *[name for *_, name in sorted(generated)], IMPORT_ROOT]
    return [name for name in roots if (root / name).is_dir()]


def tree_files(root: Path, git: Git) -> tuple[list[str], str]:
    listed = git.files()
    if listed is not None:
        return listed, "git ls-files (tracked and untracked, not ignored)"
    found = []
    for directory, subdirectories, names in os.walk(root):
        subdirectories[:] = sorted(name for name in subdirectories if name not in WALK_SKIPPED)
        found += [Path(directory, name).relative_to(root).as_posix() for name in names]
    return sorted(found), "directory walk (no git work tree)"


def audit_repository(root: Path, jar: Path | None) -> dict[str, Any]:
    root = root.resolve()
    audit, git = Audit(root), Git(root)
    try:
        audit.legacy = load_manifest(root)
    except (OSError, UnicodeError, ValueError, csv.Error) as error:
        audit.fail(0, f"The legacy manifest cannot be read: {error}")
    try:
        records, namings, markdown = load_records(audit)
    except (OSError, ValueError) as error:
        audit.fail(0, str(error))
        records, namings, markdown = [], [], []
    ranks = git.record_ranks()
    for record in records:
        if git.available and record["path"] not in ranks:
            ranks[record["path"]] = 1  # Not yet committed: newer than every committed record.
    for naming in namings:
        audit.by_target.setdefault(naming.target, []).append(naming)
    target_rows, audit.latest = check_targets(audit, audit.by_target, ranks)
    language_rows = []
    for target in sorted(target for target in audit.by_target if SHARED_LANGUAGE.fullmatch(target)):
        language_rows += check_language(audit, git, target, audit.by_target[target], ranks)

    files, tree_source = tree_files(root, git)
    if len(files) > MAX_TREE_FILES:
        audit.fail(0, f"The tree holds more than {MAX_TREE_FILES} files.")
        files = []
    for relative in files:
        digest = audit.sha256(relative)
        if digest in audit.legacy:
            audit.legacy_hit(relative, digest)
    roots = source_roots(root)
    jar_info, origins = check_jar(audit, jar, roots) if jar is not None else (None, {})

    rules = []
    for rule, title in RULES.items():
        not_run = jar is None and rule in (1, 3)
        rules.append({"rule": rule, "title": title, "checked": audit.checked[rule],
                      "result": "NOT_RUN" if not_run else "FAIL" if audit.failures[rule] else "PASS",
                      "failures": audit.failures[rule]})
    failed = bool(audit.failures[0]) or any(rule["result"] == "FAIL" for rule in rules)
    return {
        "schema_version": RESULT_SCHEMA,
        "tool": TOOL,
        "tool_sha256": file_sha256(Path(__file__)),
        "result": "FAIL" if failed else "PASS",
        "repository": {"commit": git.head, "worktree_clean": git.clean(), "shallow": git.shallow,
                       "tree_source": tree_source, "tree_files": len(files), "source_roots": roots},
        "jar": jar_info,
        "input_failures": audit.failures[0],
        "rules": rules,
        "records": records,
        "markdown_records": markdown,
        "targets": target_rows,
        "legacy_hits": [audit.legacy_rows[key] for key in sorted(audit.legacy_rows)],
        "language_keys": language_rows,
        "jar_origins": origins,
    }


def file_sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(CHUNK_BYTES), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _cell(value: object) -> str:
    if value is None or value == []:
        return "-"
    text = ", ".join(map(str, value)) if isinstance(value, list) else str(value)
    return text.replace("|", "\\|").replace("\n", " ")


def _table(headers: list[str], rows: list[list[object]]) -> list[str]:
    lines = ["| " + " | ".join(headers) + " |", "|" + "---|" * len(headers)]
    return lines + ["| " + " | ".join(_cell(value) for value in row) + " |" for row in rows] + [""]


def render_index(result: dict[str, Any]) -> str:
    repository, jar = result["repository"], result["jar"]
    lines = ["# v1.8 provenance index", "",
             f"Written by `{TOOL}` (ADR-061 section 4.7; W62 slice S2). Result: **{result['result']}**.", ""]
    lines += _table(["Item", "Value"], [
        ["Repository commit", repository["commit"]], ["Work tree clean", repository["worktree_clean"]],
        ["Tree files scanned", f"{repository['tree_files']} ({repository['tree_source']})"],
        ["JAR", f"`{jar['name']}`, SHA-256 `{jar['sha256']}`, {jar['entries']} entries" if jar else "not given"],
        ["Tool SHA-256", f"`{result['tool_sha256']}`"]])
    lines += ["## Rules", ""] + _table(["Rule", "Check", "Checked", "Result", "Failures"], [
        [rule["rule"], rule["title"], rule["checked"], rule["result"], len(rule["failures"])]
        for rule in result["rules"]])
    failures = [f"- Input: {item}" for item in result["input_failures"]] + [
        f"- Rule {rule['rule']}: {item}" for rule in result["rules"] for item in rule["failures"]]
    lines += ["## Failures", ""] + (failures or ["None."]) + [""]
    lines += ["## JAR origins", ""] + (_table(["Source root", "Entries"], [
        [f"`{name}`", count] for name, count in result["jar_origins"].items()]) if jar else ["No JAR was given.", ""])
    lines += ["## Files with a legacy-manifest SHA-256", ""] + _table(
        ["File", "Legacy source", "Latest record", "Status", "Packaged as"],
        [[f"`{row['file']}`", row["legacy_sources"], row["record"], row["status"], row["packaged_as"]]
         for row in result["legacy_hits"]])
    lines += ["## Shared v1.8 language files (rule 5)", ""] + _table(
        ["File", "Record", "Keys from", "Keys", "Result"],
        [[f"`{row['target']}`", row["record"], row["keys_from"], row["keys"], row["result"]]
         for row in result["language_keys"]])
    lines += ["## Records", ""] + _table(["Record", "Kind", "Schema", "Review", "Targets named"], [
        [f"`{row['path']}`", row["kind"], row["schema_version"], row["review"], row["namings"]]
        for row in result["records"]])
    lines += ["Markdown records, listed but not parsed: " + (", ".join(
        f"`{PurePosixPath(path).name}`" for path in result["markdown_records"]) or "none") + ".", ""]
    lines += ["## Targets (rule 4)", ""] + _table(
        ["Target", "Latest record", "Status", "SHA-256", "Result", "Older namings with other bytes"],
        [[f"`{row['target']}`", row["latest"], row["status"], (row["sha256"] or "missing")[:16], row["result"],
          row["superseded"]] for row in result["targets"]])
    return "\n".join(lines).rstrip() + "\n"


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    parser.add_argument("--jar", type=Path, help="the built mod JAR; without it rules 1 and 3 are not run")
    parser.add_argument("--output-dir", type=Path, help=f"where to write {INDEX_NAME} and {RESULT_NAME}")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    result = audit_repository(args.repository_root, args.jar)
    if args.output_dir is not None:
        args.output_dir.mkdir(parents=True, exist_ok=True)
        (args.output_dir / INDEX_NAME).write_text(render_index(result), encoding="utf-8", newline="\n")
        (args.output_dir / RESULT_NAME).write_text(json.dumps(result, indent=2, sort_keys=True) + "\n",
                                                   encoding="utf-8", newline="\n")
    for failure in result["input_failures"]:
        print(f"[FAIL] Input: {failure}", file=sys.stderr)
    for rule in result["rules"]:
        print(f"[{rule['result']}] Rule {rule['rule']}: {rule['title']} ({rule['checked']} checked)")
        for failure in rule["failures"]:
            print(f"[FAIL]   {failure}", file=sys.stderr)
    print(f"[{result['result']}] v1.8 provenance audit: {len(result['targets'])} targets, "
          f"{len(result['legacy_hits'])} legacy-hash files, {len(result['language_keys'])} language namings")
    return 0 if result["result"] == "PASS" else 1


if __name__ == "__main__":
    raise SystemExit(main())
