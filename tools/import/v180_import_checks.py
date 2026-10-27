"""Repository inputs, the derivation re-run and the upstream history check of the v1.8 importer.

* Inputs (``load_inputs``): the upstream pin in ``UPSTREAM.md``; the derivation results and the two vanilla client
  JARs, each bound to its SHA-256 pinned in ADR-061; the legacy manifest; the asset plan (first matching rule);
  the origin findings; and the content ledger.
* Derivation (ADR-061 section 4.8, ``derive``): ``tools/audit/vanilla_derivation.py`` re-run on the bytes the
  importer reads and writes, against the vanilla JARs and every legacy asset. ``review_reasons`` applies the
  inheritance rule: a file related to a file with a HIT, SUSPECT or UNSUPPORTED verdict, a REVIEW handling or an
  EXCLUDED origin finding is at most REVIEW, unless that file has a CLEARED finding.
* History (ADR-061 section 4.9, ``upstream_history``): every commit that touched a source file, read from a full
  (not shallow) clone at the pinned commit, following renames into each parent of a merge separately, plus the
  first-parent merges that brought each change in. The tool walks the commit graph itself; ``git log --follow``
  gives a different answer on git 2.55 and 2.56 (W169). A message that names the AR 16x texture set's pull
  requests, or brings in material from an issue, an unmerged pull request or another project, is a review trigger.
"""

from __future__ import annotations

import csv
import fnmatch
import hashlib
import json
import re
import struct
import subprocess
import zlib
from dataclasses import dataclass
from pathlib import Path

from tools.audit import vanilla_derivation as derivation

UPSTREAM_DOC = Path("UPSTREAM.md")
DERIVATION_ADR = Path("docs/decisions/ADR-061-CLASSIC-CONTENT-IDENTITY-IMPORT-AND-VALIDATION.md")
DERIVATION_RESULTS = Path("docs/work/v1.8.0-vanilla-derivation.json")
DERIVATION_TOOL = "tools/audit/vanilla_derivation.py"
LEGACY_MANIFEST = Path("legacy-manifest/assets.csv")
ASSET_PLAN = Path("docs/work/v1.8.0-asset-plan.csv")
ORIGIN_FINDINGS = Path("docs/provenance/v1.8.0-origin-findings.json")
CONTENT_LEDGER = Path("docs/work/v1.8.0-content-ledger.csv")
VANILLA_CLIENTS = ("1.12.2", "1.20.1")
LEGACY_ASSET_ROOT = "src/main/resources/assets/advancedrocketry/"
TAINTING_VERDICTS = ("HIT", "SUSPECT", "UNSUPPORTED")
GIT_TIMEOUT_SECONDS = 300
MAX_HISTORY = 1000
MAX_COMMITS = 100_000
MAX_FOLLOWED_PATHS = 64
RENAME_LIMIT = 1000
MAX_INPUT_BYTES = 64 * 1024 * 1024

# ADR-061 section 4.9: the AR 16x texture set entered through these pull requests and commits (C14 history).
SIXTEEN_SET_PULL_REQUESTS = re.compile(r"#(?:1809|1811|1889|1976)\b")
SIXTEEN_SET_COMMITS = (
    "276e4e444f0dde5fe4ea950f30c19d469a934af0",  # #1811
    "9f5a4d3a",  # #1809
    "bfc81e38",  # #1889
    "a40bf119f9859bb50648afa436581ab50988372d",  # #1889
    "15a14dd3b50370cb19f7fc327d4c6dfad64fb2bc",  # #1976
)
# Messages that bring in material from an issue, an unmerged pull request or another project. A plain issue
# reference ("fixes #86") or a merged pull request number is not one; the record review reads the full history.
THIRD_PARTY_MESSAGES = (
    ("material from an issue or pull request",
     re.compile(r"\bfrom\s+(?:an?\s+|the\s+)?(?:issue|pull\s+request|pr)\b|\bfrom\s+#\d+", re.I)),
    ("an unmerged contribution", re.compile(r"\bunmerged\b", re.I)),
    ("material from another source",
     re.compile(r"\b(?:taken|copied|borrowed|ported|adapted|imported)\s+from\b|\bcourtesy\s+of\b", re.I)),
    ("a contributor credit", re.compile(r"\bthanks?\b|\bcredits?\b|\bpr\s+by\b", re.I)),
    ("a link to another project", re.compile(r"https?://", re.I)),
)


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_input(path: Path, limit: int = MAX_INPUT_BYTES) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise ValueError(f"required input is not a regular file: {path}")
    data = path.read_bytes()
    if len(data) > limit:
        raise ValueError(f"input exceeds {limit} bytes: {path}")
    return data


def _csv(path: Path, required: set[str]) -> list[dict[str, str]]:
    rows = list(csv.DictReader(read_input(path).decode("utf-8").splitlines()))
    if rows and not required <= set(rows[0]):
        raise ValueError(f"{path.name} lacks the columns {sorted(required - set(rows[0]))}")
    return rows


@dataclass(frozen=True)
class Inputs:
    upstream: dict[str, str]
    vanilla_pins: dict[str, str]
    results_sha256: str
    verdicts: dict[str, str]
    digests: dict[str, str]
    related: dict[str, set[str]]
    manifest: dict[str, str]
    rules: tuple[tuple[str, str], ...]
    findings: dict[str, str]
    ledger: dict[str, str]

    def handling(self, asset: str) -> str | None:
        for pattern, handling in self.rules:
            if fnmatch.fnmatchcase(asset, pattern):
                return handling
        return None


def load_inputs(root: Path) -> Inputs:
    try:
        return _load_inputs(root)
    except (KeyError, TypeError, AttributeError) as error:
        raise ValueError(f"a pinned repository input is malformed: {error!r}") from None


def _load_inputs(root: Path) -> Inputs:
    upstream_text = read_input(root / UPSTREAM_DOC).decode("utf-8")
    upstream = {}
    for key, field in (("source_repository", "repository"), ("source_branch", "primary_branch"),
                       ("license", "license"), ("source_commit", "upstream_commit")):
        match = re.search(r"^" + field + r":\s*(\S+)\s*$", upstream_text, re.M)
        if not match:
            raise ValueError(f"{UPSTREAM_DOC} does not name the upstream {field}")
        upstream[key] = match.group(1)
    adr = read_input(root / DERIVATION_ADR).decode("utf-8")
    pinned = re.search(r"^derivation_results_sha256:\s*([0-9a-f]{64})\s*$", adr, re.M)
    vanilla_pins = {}
    for label in VANILLA_CLIENTS:
        match = re.search(r'^\s+"' + re.escape(label) + r'":\s*([0-9a-f]{64})\s*$', adr, re.M)
        if not match:
            raise ValueError(f"ADR-061 does not pin the {label} client SHA-256")
        vanilla_pins[label] = match.group(1)
    raw = read_input(root / DERIVATION_RESULTS)
    if not pinned or pinned.group(1) != sha256(raw):
        raise ValueError(f"{DERIVATION_RESULTS} does not match the SHA-256 pinned in ADR-061")
    results = json.loads(raw)
    if (results.get("schema") != derivation.SCHEMA or results.get("generator") != DERIVATION_TOOL
            or results.get("thresholds") != derivation.thresholds()):
        raise ValueError(f"{DERIVATION_RESULTS} was not produced by this {DERIVATION_TOOL}")
    clients = results.get("vanilla") or {}
    if sorted(clients) != sorted(VANILLA_CLIENTS) or any(
            clients[label].get("sha256") != vanilla_pins[label] for label in VANILLA_CLIENTS):
        raise ValueError(f"{DERIVATION_RESULTS} names other vanilla clients than ADR-061 pins")
    verdicts, digests, related = {}, {}, {}
    for entry in results.get("assets", []):
        verdicts[entry["asset"]] = entry["verdict"]
        digests[entry["asset"]] = entry["sha256"]
        for match in entry.get("related", []):
            # A relation counts when either file lists the other (ADR-061 section 4.8).
            related.setdefault(entry["asset"], set()).add(match["asset"])
            related.setdefault(match["asset"], set()).add(entry["asset"])
    manifest = {row["source_path"]: row["sha256"] for row in _csv(root / LEGACY_MANIFEST, {"source_path", "sha256"})}
    rules = tuple((row["pattern"], row["handling"]) for row in _csv(root / ASSET_PLAN, {"pattern", "handling"}))
    findings_data = json.loads(read_input(root / ORIGIN_FINDINGS))
    if findings_data.get("schema_version") != 1 or not isinstance(findings_data.get("findings"), list):
        raise ValueError(f"{ORIGIN_FINDINGS} must be schema 1 with a findings list")
    findings = {item["asset"]: item["decision"] for item in findings_data["findings"]}
    ledger = {row["unit_id"]: row["disposition"] for row in _csv(root / CONTENT_LEDGER, {"unit_id", "disposition"})}
    return Inputs(upstream, vanilla_pins, sha256(raw), verdicts, digests, related, manifest, rules, findings, ledger)


def load_vanilla(specs: list[str], pins: dict[str, str]) -> tuple:
    """The vanilla hashes and images of exactly the two pinned client JARs."""
    labels = []
    for spec in specs:
        label, separator, raw_path = spec.partition("=")
        if not separator or label not in pins or label in labels:
            raise ValueError(f"--vanilla {spec!r}: expected one LABEL=PATH for each of {', '.join(VANILLA_CLIENTS)}")
        if sha256(read_input(Path(raw_path))) != pins[label]:
            raise ValueError(f"the {label} client JAR differs from the SHA-256 pinned in ADR-061")
        labels.append(label)
    if sorted(labels) != sorted(VANILLA_CLIENTS):
        raise ValueError(f"--vanilla must name exactly the clients {', '.join(VANILLA_CLIENTS)}")
    return derivation.load_vanilla(sorted(specs))


def _decode(data: bytes):
    try:
        width, height, rgba = derivation.decode_rgba(data)
    except (derivation.DecodeError, zlib.error, struct.error, IndexError):
        return None
    return derivation.Image(width, height, rgba)


def derive(candidates: dict[str, bytes], legacy: dict[str, bytes], vanilla: tuple) -> dict[str, dict]:
    """Verdict and related legacy files of each candidate, by the rules of ``vanilla_derivation.analyse``.
    A candidate named like a legacy asset is not compared with that asset itself."""
    _, hashes, vanilla_images = vanilla
    legacy_by_hash: dict[str, list[str]] = {}
    references = list(vanilla_images)
    for name, data in sorted(legacy.items()):
        legacy_by_hash.setdefault(sha256(data), []).append(name)
        image = _decode(data) if name.endswith(".png") else None
        if image is not None:
            references.append((derivation.LEGACY, name, image))
    results: dict[str, dict] = {}
    images = []
    for name, data in sorted(candidates.items()):
        digest = sha256(data)
        twins = [other for other in legacy_by_hash.get(digest, []) if other != name]
        results[name] = {"verdict": "HIT" if digest in hashes else "CLEAR", "related": set(twins)}
        if not name.endswith(".png"):
            if name.endswith((".jpg", ".jpeg")) and digest not in hashes:
                results[name]["verdict"] = "UNSUPPORTED"
            continue
        image = _decode(data)
        if image is None:
            results[name]["verdict"] = "HIT" if digest in hashes else "UNSUPPORTED"
            continue
        images.append((name, image))
    if images:
        matcher = derivation.match_images(images, references)
        for name, (_, level, _) in matcher.best.items():
            if results[name]["verdict"] != "HIT":
                results[name]["verdict"] = level
        for name, table in matcher.related.items():
            results[name]["related"].update(table)
    return {name: {"verdict": value["verdict"], "related": sorted(value["related"])} for name, value in results.items()}


def taint(inputs: Inputs, asset: str) -> str | None:
    """Why a related file passes review on (ADR-061 section 4.8), or None."""
    if inputs.findings.get(asset) == "CLEARED":
        return None
    if inputs.verdicts.get(asset) in TAINTING_VERDICTS:
        return f"derivation verdict {inputs.verdicts[asset]}"
    if inputs.findings.get(asset) == "EXCLUDED":
        return "EXCLUDED origin finding"
    if inputs.handling(asset) == "REVIEW":
        return "REVIEW handling"
    return None


def review_reasons(inputs: Inputs, verdicts: dict[str, str], related: set[str]) -> list[str]:
    """Reasons an entry is at most REVIEW: a non-CLEAR verdict of its own or an inherited review."""
    reasons = [f"{kind} derivation verdict {level}" for kind, level in sorted(verdicts.items()) if level != "CLEAR"]
    for other in sorted(related):
        reason = taint(inputs, other)
        if reason:
            reasons.append(f"shares pixels with {other}, whose {reason} it inherits")
    return reasons


def _git(repository: Path, *args: str, stdin: bytes | None = None) -> bytes:
    result = subprocess.run(["git", "-C", str(repository), *args], check=False, input=stdin, stdout=subprocess.PIPE,
                            stderr=subprocess.PIPE, timeout=GIT_TIMEOUT_SECONDS)
    if result.returncode:
        detail = result.stderr.decode("utf-8", errors="replace").strip()
        raise ValueError(f"git {' '.join(args[:3])} failed: {detail}")
    return result.stdout


def require_full_history(repository: Path) -> None:
    if _git(repository, "rev-parse", "--is-shallow-repository").strip() != b"false":
        raise ValueError(f"{repository} is a shallow clone; the history check needs the full history")


def read_blob(repository: Path, commit: str, path: str) -> bytes:
    size = int(_git(repository, "cat-file", "-s", f"{commit}:{path}").strip())
    if size > MAX_INPUT_BYTES:
        raise ValueError(f"{path} at {commit} exceeds {MAX_INPUT_BYTES} bytes")
    return _git(repository, "cat-file", "blob", f"{commit}:{path}")


def first_parent(repository: Path, commit: str) -> str:
    return _git(repository, "rev-parse", "--verify", f"{commit}^1^{{commit}}").decode("ascii").strip()


class _Blobs:
    """The blob of a path at a commit (None where the commit has no file there), read through one
    ``git cat-file --batch-check`` session and remembered."""

    def __init__(self, repository: Path):
        self.process = subprocess.Popen(
            ["git", "-C", str(repository), "cat-file", "--batch-check=%(objectname) %(objecttype)"],
            stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
        self.known: dict[tuple[str, str], str | None] = {}

    def __enter__(self) -> "_Blobs":
        return self

    def __exit__(self, failure, *_) -> None:
        assert self.process.stdin is not None and self.process.stdout is not None
        try:
            self.process.stdin.close()
            code = self.process.wait(timeout=GIT_TIMEOUT_SECONDS)
        except (OSError, subprocess.TimeoutExpired):
            self.process.kill()
            code = self.process.wait()
        finally:
            self.process.stdout.close()
        if code and failure is None:
            raise ValueError(f"git cat-file --batch-check failed with exit code {code}")

    def __call__(self, commit: str, path: str) -> str | None:
        key = (commit, path)
        if key not in self.known:
            if "\n" in path or "\r" in path:
                raise ValueError(f"the history check cannot name the path {path!r}")
            assert self.process.stdin is not None and self.process.stdout is not None
            self.process.stdin.write(f"{commit}:{path}\n".encode("utf-8"))
            self.process.stdin.flush()
            line = self.process.stdout.readline().decode("utf-8", errors="replace").rstrip("\n")
            fields = line.split(" ")
            if line.endswith(" missing"):
                self.known[key] = None
            elif len(fields) == 2 and re.fullmatch(r"[0-9a-f]{40}", fields[0]):
                self.known[key] = fields[0] if fields[1] == "blob" else None
            else:
                raise ValueError(f"git cat-file returned a malformed answer for {commit[:12]}:{path}")
        return self.known[key]


def _graph(repository: Path, commit: str) -> tuple[list[str], dict[str, list[str]]]:
    """Every commit up to ``commit``, children before their parents, and each commit's parents."""
    order: list[str] = []
    parents: dict[str, list[str]] = {}
    for line in _git(repository, "rev-list", "--topo-order", "--parents", commit).decode("ascii").splitlines():
        identifiers = line.split()
        if not identifiers or not all(re.fullmatch(r"[0-9a-f]{40}", item) for item in identifiers):
            raise ValueError("git rev-list returned a malformed commit line")
        order.append(identifiers[0])
        parents[identifiers[0]] = identifiers[1:]
    if not order or len(order) > MAX_COMMITS:
        raise ValueError(f"the upstream history has {len(order)} commits, outside 1..{MAX_COMMITS}")
    return order, parents


def _changes(repository: Path, parent: str, commit: str, *options: str) -> list[tuple[str, str, str, list[str]]]:
    """(status, old blob, new blob, paths) of each file ``commit`` changes against ``parent``."""
    fields = _git(repository, "diff-tree", "-r", "--raw", "-z", "--no-abbrev", *options, parent, commit).split(b"\0")
    rows: list[tuple[str, str, str, list[str]]] = []
    index = 0
    while index < len(fields) and fields[index]:
        meta = fields[index].decode("ascii").split(" ")
        count = 2 if len(meta) == 5 and meta[4][:1] in ("R", "C") else 1
        paths = [field.decode("utf-8") for field in fields[index + 1:index + 1 + count]]
        if len(meta) != 5 or not meta[0].startswith(":") or len(paths) != count or not all(paths):
            raise ValueError(f"git diff-tree returned a malformed change between {parent[:12]} and {commit[:12]}")
        rows.append((meta[4], meta[2], meta[3], paths))
        index += 1 + count
    return rows


def _rename_source(repository: Path, commit: str, parent: str, path: str, blob: str) -> str | None:
    """The path that ``path`` had in ``parent`` when ``commit`` renamed it, or None when the commit adds a new file.

    A rename deletes its source. Deleted and added paths with the same bytes pair by the same path ignoring case, then
    the same file name ignoring case, then a single pair left; a choice between several fails. Only when no unpaired
    deleted path has the same bytes is git's own pairing (``-M``, default similarity) asked for a rename that changed
    the bytes. A file with the same bytes that the commit keeps is a copy and is not followed."""
    rows = _changes(repository, parent, commit, "--no-renames")
    deleted = sorted(paths[0] for status, old, _, paths in rows if status == "D" and old == blob)
    added = sorted(paths[0] for status, _, new, paths in rows if status == "A" and new == blob)
    if path not in added:
        raise ValueError(f"git diff-tree does not show {commit[:12]} adding {path} against {parent[:12]}")
    pairs: dict[str, str] = {}
    for key in (str.casefold, lambda name: name.rsplit("/", 1)[-1].casefold()):
        sources = [source for source in deleted if source not in pairs.values()]
        targets = [target for target in added if target not in pairs]
        for target in targets:
            matches = [source for source in sources if key(source) == key(target)]
            if len(matches) == 1 and sum(key(other) == key(target) for other in targets) == 1:
                pairs[target] = matches[0]
    if path in pairs:
        return pairs[path]
    sources = [source for source in deleted if source not in pairs.values()]
    targets = [target for target in added if target not in pairs]
    if len(sources) == 1 and targets == [path]:
        return sources[0]
    if sources:
        raise ValueError(f"{commit[:12]} adds {path} with the bytes of the deleted {sources[:4]}; the rename "
                         f"source is ambiguous")
    for status, _, _, paths in _changes(repository, parent, commit, "-M", f"-l{RENAME_LIMIT}"):
        if status[:1] == "R" and paths[1] == path and status[1:].isdigit() and int(status[1:]) < 100:
            return paths[0]
    return None


def _touching(repository: Path, commit: str, path: str, graph: tuple[list[str], dict[str, list[str]]],
              blobs: _Blobs, first_parent: bool) -> list[str]:
    """The commits that changed the file now at ``path``, following renames into each parent separately (only the
    first parent with ``first_parent``). Without ``first_parent`` a merge is not listed: the commits on its sides
    are; with it, a merge that changed the file against its first parent is."""
    order, parents = graph
    followed: dict[str, set[str]] = {commit: {path}}
    names = {path}
    touched: list[str] = []
    for identifier in order:
        paths = followed.pop(identifier, None)
        if not paths:
            continue
        own = parents[identifier]
        listed = not own
        for current in sorted(paths):
            blob = blobs(identifier, current)
            if blob is None:
                raise ValueError(f"{identifier[:12]} has no file {current} to follow")
            for parent in own[:1] if first_parent else own:
                before = blobs(parent, current)
                earlier = current if before is not None else _rename_source(repository, identifier, parent,
                                                                             current, blob)
                if earlier is not None:
                    followed.setdefault(parent, set()).add(earlier)
                    names.add(earlier)
                if before != blob and (first_parent or len(own) == 1):
                    listed = True
        if len(names) > MAX_FOLLOWED_PATHS:
            raise ValueError(f"the history of {path} follows more than {MAX_FOLLOWED_PATHS} paths")
        if listed:
            touched.append(identifier)
    return touched


def upstream_history(repository: Path, commit: str, path: str) -> tuple[list[dict[str, str]], list[str]]:
    """The commits that touched ``path`` up to ``commit`` (newest commit date first), then the first-parent merges
    that brought them in, and the review triggers among them.

    The walk is this tool's own, not ``git log --follow``: that one kept one followed path for the whole walk until
    git 2.55, so its result on non-linear history depended on the walk order, and from git 2.56 it follows a copy
    into a merge's side. The same upstream gave two different K6 records on the two versions (W169)."""
    graph = _graph(repository, commit)
    with _Blobs(repository) as blobs:
        passes = [_touching(repository, commit, path, graph, blobs, first_parent) for first_parent in (False, True)]
    found = sorted({identifier for touched in passes for identifier in touched})
    if not found or len(found) > MAX_HISTORY:
        raise ValueError(f"the history of {path} has {len(found)} commits, outside 1..{MAX_HISTORY}")
    output = _git(repository, "-c", "i18n.logOutputEncoding=UTF-8", "log", "--no-walk=unsorted", "--stdin",
                  "--format=%H%x1f%ct%x1f%aI%x1f%an%x1f%s%x1f%b%x1e", stdin="".join(
                      identifier + "\n" for identifier in found).encode("ascii"))
    details: dict[str, list[str]] = {}
    for record in output.decode("utf-8", errors="replace").split("\x1e"):
        record = record.lstrip("\n")
        if not record:
            continue
        fields = record.split("\x1f")
        if len(fields) != 6 or fields[0] not in found or not fields[1].isdigit():
            raise ValueError(f"git log returned a malformed history record for {path}")
        details[fields[0]] = fields[1:]
    if sorted(details) != found:
        raise ValueError(f"git log did not describe every commit in the history of {path}")
    history: list[dict[str, str]] = []
    triggers: list[str] = []
    seen: set[str] = set()
    for touched in passes:
        for identifier in sorted(set(touched) - seen, key=lambda item: (-int(details[item][0]), item)):
            seen.add(identifier)
            _, authored, author, subject, body = details[identifier]
            history.append({"commit": identifier, "authored_at": authored, "author": author, "subject": subject})
            message = subject + "\n" + body
            where = f"{identifier[:12]} ({subject.strip()[:80]!r})"
            if identifier.startswith(SIXTEEN_SET_COMMITS) or SIXTEEN_SET_PULL_REQUESTS.search(message):
                triggers.append(f"history {where} is part of the AR 16x texture set (ADR-061 section 4.9)")
            for reason, pattern in THIRD_PARTY_MESSAGES:
                if pattern.search(message):
                    triggers.append(f"history {where} may bring in {reason} (ADR-061 section 4.9)")
    return history, triggers
