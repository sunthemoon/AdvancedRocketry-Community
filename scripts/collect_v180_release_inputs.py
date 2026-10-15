#!/usr/bin/env python3
"""Collect the v1.8 section 15 release inputs into a staging folder (W62 slice S8).

    python -B scripts/collect_v180_release_inputs.py --output <staging folder> [--jar build/libs/<artifact>.jar]
    python -B scripts/collect_v180_release_inputs.py --verify <staging folder>

Run it after ``gradlew test``, which writes the graph and coverage reports under ``build/reports``. The staging folder
follows the ``content/`` and ``assets/`` layout of ``docs/work/v1.8.0-root-queue/records/W62.md`` section 6:

* the recipe-graph reports go to ``content/recipe-graph/`` and ``registered-content-resources.md`` to ``assets/``;
* ``validate_v180_content_ledger.py --closure`` gives ``content/ledger-summary.json`` (its stdout) and
  ``content/ledger-closure-run.json`` (its exit code and stderr). The closure fails until the ledger closes, so a
  failing closure is recorded and does not stop the collector;
* ``validate_v090_resources.py --output`` gives ``assets/resource-audit.json``;
* with ``--jar``, ``validate_v180_provenance.py`` gives ``assets/provenance-index.md`` and
  ``assets/jar-provenance-audit.json``;
* two derived reports: ``content/porting-matrix-counts.md`` (ledger dispositions per batch and per system, with the
  PORTING_MATRIX rows beside each batch) and ``content/progression-report.md`` (a basic-to-endgame route through the
  witness routes, with every stage the graph cannot reach marked);
* ``collector-summary.json`` and the SHA-256 manifest ``SHA256SUMS.txt`` (``sha256sum -c`` format).

The collector never writes into ``docs/releases/``. It exits 1 and names every missing input when a required input
is absent or empty, when a validator other than the ledger closure fails, or when an input changes during the run;
the staging folder then is not created. ``--verify`` rechecks a staging folder against its manifest.
"""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile
from collections import Counter
from pathlib import Path, PurePosixPath

if __package__:
    from . import validate_v180_content_ledger as ledger_rules
else:
    import validate_v180_content_ledger as ledger_rules  # type: ignore[no-redef]

ROOT = Path(__file__).resolve().parents[1]
TOOL = "scripts/collect_v180_release_inputs.py"
SCHEMA = 1
MANIFEST = "SHA256SUMS.txt"
SUMMARY = "collector-summary.json"
MAX_INPUT_BYTES = 16 * 1024 * 1024
MAX_PACKAGE_BYTES = 100 * 1024 * 1024  # AGENTS.md section 9
COMMAND_TIMEOUT_SECONDS = 600
MAX_STDERR_LINES = 500
MAX_LINE_CHARACTERS = 1000
REPORT_DIRECTORY = "build/reports"
PORTING_MATRIX = Path("docs/PORTING_MATRIX.md")
LEDGER_SCRIPT = "scripts/validate_v180_content_ledger.py"
RESOURCE_SCRIPT = "scripts/validate_v090_resources.py"
PROVENANCE_SCRIPT = "scripts/validate_v180_provenance.py"
RELEASES = Path("docs/releases")
NAMESPACE = "advancedrocketrycommunity"

# The reports the unit tests write: source name, staged path and the exact first line.
REPORTS = (
    ("registered-item-acquisition.md", "content/recipe-graph/registered-item-acquisition.md",
     "# Registered item acquisition"),
    ("registered-item-acquisition-without-tau-ceti.md",
     "content/recipe-graph/registered-item-acquisition-without-tau-ceti.md",
     "# Registered item acquisition without Tau Ceti Levels"),
    ("classic-advancement-reachability.md", "content/recipe-graph/classic-advancement-reachability.md",
     "# Classic advancement reachability (ADR-066 section 6.3)"),
    ("classic-advancement-reachability-without-tau-ceti.md",
     "content/recipe-graph/classic-advancement-reachability-without-tau-ceti.md",
     "# Classic advancement reachability without Tau Ceti Levels"),
    ("machine-recipe-material-tags.md", "content/recipe-graph/machine-recipe-material-tags.md",
     "# Machine recipe material tags (W57)"),
    ("registered-content-resources.md", "assets/registered-content-resources.md", "# Registered content resources"),
)
LEDGER_SUMMARY = "content/ledger-summary.json"
LEDGER_RUN = "content/ledger-closure-run.json"
RESOURCE_AUDIT = "assets/resource-audit.json"
PROVENANCE_OUTPUTS = ("assets/provenance-index.md", "assets/jar-provenance-audit.json")
MATRIX_COUNTS = "content/porting-matrix-counts.md"
PROGRESSION = "content/progression-report.md"


def _id(path: str) -> str:
    return f"{NAMESPACE}:{path}"


# Basic to endgame: stage name, its machine (a registered item) and further graph nodes the stage needs. Every node
# is a registered item, which the item report always lists, or a classic advancement goal, which heads its section.
STAGES = (
    ("Basic materials", _id("small_plate_press"), ()),
    ("First Forge Energy", _id("combustion_generator"), ()),
    ("Plate rolling", _id("rolling_machine"), ()),
    ("Electrolysis", _id("electrolyzer"), ()),
    ("Arc furnace", _id("arc_furnace"), ()),
    ("Lathe", _id("lathe"), ()),
    ("Cutting machine", _id("cutting_machine"), ()),
    ("Precision assembly", _id("precision_assembler"), ()),
    ("Crystallizer", _id("crystallizer"), ()),
    ("Chemical reactor", _id("chemical_reactor"), ()),
    ("Precision laser etcher", _id("precision_laser_etcher"), ()),
    ("Centrifuge", _id("centrifuge"), ()),
    ("Space suit", _id("suit_workstation"), ("goal:suited_up",)),
    ("Rocket and Moon landing", _id("rocket_assembler"), ("access:moon", "event:moon_landing")),
    ("Research", _id("observatory"), (_id("astrobody_data_processor"),)),
    ("Space station warp", _id("warp_core"), (_id("station_deployment_kit"), "event:station_warp")),
    ("Endgame: orbital laser drill", _id("orbital_laser_drill"), ()),
    ("Endgame: railgun", _id("railgun"), ()),
    ("Endgame: space elevator", _id("elevator_anchor"), ()),
    ("Endgame: gravity field controller", _id("gravity_field_controller"), ()),
    ("Endgame: black hole generator", _id("black_hole_generator"), ()),
)

WITNESS = re.compile(r"^\s*- (?P<node>\S+) <- (?P<producer>\S+) \(round (?P<round>\d+)\)"
                     r"(?:; prerequisites: \[(?P<prerequisites>.*)\])?$")
MARKED = re.compile(r"^\s*- (?P<node>\S+) \((?P<mark>root|above|unreachable)\)$")
LISTED_GOAL = re.compile(r"^- classic/\S+ -> (?P<node>\S+) \((?P<mark>reachable|unreachable|awaiting content)\)$")
BATCH_TOKEN = re.compile(r"(?<![A-Za-z0-9])(C1[5-8])([a-d]?)(?![A-Za-z0-9])")
VERSION_TOKEN = re.compile(r"v\d+\.\d+\.\d+")
NOT_DELIVERED = ("MERGED", "DEFERRED", "REJECTED")


class CollectError(Exception):
    """A required input is missing or unusable; the message names it."""


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def read_input(path: Path, label: str) -> bytes:
    if path.is_symlink() or not path.is_file():
        raise CollectError(f"missing input: {label}")
    data = path.read_bytes()
    if not data:
        raise CollectError(f"empty input: {label}")
    if len(data) > MAX_INPUT_BYTES:
        raise CollectError(f"oversized input: {label} ({len(data)} bytes, limit {MAX_INPUT_BYTES})")
    return data


def _write(path: Path, data: bytes) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_bytes(data)


def _json_bytes(value: object) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True, ensure_ascii=False) + "\n").encode("utf-8")


def _lines(text: str) -> tuple[list[str], bool]:
    lines = [line[:MAX_LINE_CHARACTERS] for line in text.splitlines()]
    return lines[:MAX_STDERR_LINES], len(lines) > MAX_STDERR_LINES


def run_script(root: Path, script: str, arguments: list[str]) -> tuple[int, str, str]:
    environment = dict(os.environ, PYTHONIOENCODING="utf-8")
    try:
        completed = subprocess.run([sys.executable, "-B", str(root / script), *arguments], cwd=root,
                                   capture_output=True, timeout=COMMAND_TIMEOUT_SECONDS, env=environment)
    except subprocess.TimeoutExpired as error:
        raise CollectError(f"{script} did not finish in {COMMAND_TIMEOUT_SECONDS} seconds") from error
    except OSError as error:
        raise CollectError(f"{script} could not start: {error}") from error
    return (completed.returncode, completed.stdout.decode("utf-8", "replace"),
            completed.stderr.decode("utf-8", "replace"))


def _first_line(text: str) -> str:
    return next((line for line in text.splitlines() if line.strip()), "no output")[:MAX_LINE_CHARACTERS]


def ledger_closure(root: Path) -> tuple[dict, dict]:
    """The one input allowed to fail: returns the stdout summary and the run record."""
    code, stdout, stderr = run_script(root, LEDGER_SCRIPT, ["--root", str(root), "--closure"])
    try:
        summary = json.loads(stdout)
    except json.JSONDecodeError:
        summary = None
    expected = {0: "PASS", 1: "FAIL"}.get(code)
    if not isinstance(summary, dict) or expected is None or summary.get("result") != expected:
        raise CollectError(f"ledger closure run exited {code} without a matching summary: {_first_line(stderr)}")
    lines, truncated = _lines(stderr)
    record = {"command": f"python -B {LEDGER_SCRIPT} --closure", "exit_code": code, "result": summary["result"],
              "schema": SCHEMA, "stderr": lines, "stderr_truncated": truncated}
    return summary, record


def _require_success(script: str, code: int, stderr: str) -> None:
    if code != 0:
        raise CollectError(f"{script} failed with exit code {code}: {_first_line(stderr)}")


# Derived report 1: the ledger dispositions per batch and per system, with the PORTING_MATRIX rows.

def parse_matrix(text: str) -> list[dict]:
    rows: list[dict] = []
    header: list[str] | None = None
    for line in text.splitlines():
        if not line.startswith("|"):
            if header and rows:
                break
            header = None
            continue
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if header is None:
            if all(name in cells for name in ("领域", "目标版本", "最低验收", "状态")):
                header = cells
            continue
        if set("".join(cells)) <= set("-: "):
            continue
        if len(cells) != len(header):
            raise CollectError(f"{PORTING_MATRIX.as_posix()}: a table row has {len(cells)} cells, not {len(header)}")
        rows.append(dict(zip(header, cells)))
    if not rows:
        raise CollectError(f"{PORTING_MATRIX.as_posix()}: no table with 领域, 目标版本, 最低验收 and 状态 columns")
    explicit = {family + letter for row in rows for family, letter in BATCH_TOKEN.findall(row["最低验收"]) if letter}
    for row in rows:
        batches: set[str] = set()
        for family, letter in BATCH_TOKEN.findall(" ".join(row.values())):
            # A family token (C16) names each of its batches that no row names in its acceptance column.
            batches |= {family + letter} if letter else {family + x for x in "abcd"} - explicit
        row["batches"] = batches
        row["versions"] = set(VERSION_TOKEN.findall(row["目标版本"]))
    return rows


def ledger_group(row: dict) -> str:
    if row["disposition"] in NOT_DELIVERED:
        return row["disposition"]
    if row["plan"] in ledger_rules.BATCHES:
        return row["plan"]
    if row["plan"] == "v1.8.0":
        match = ledger_rules.EVIDENCE_PATH.match(row["evidence"])
        return match.group(1).upper()[0] + match.group(1)[1:] if match else "v1.8.0 without batch evidence"
    return row["plan"]


def _group_order(group: str) -> tuple:
    if group in ledger_rules.BATCHES:
        return (0, ledger_rules.BATCHES.index(group))
    version = ledger_rules.VERSION.match(group)
    if version:
        return (1, tuple(int(part) for part in version.groups()))
    if group in NOT_DELIVERED:
        return (3, NOT_DELIVERED.index(group))
    return (2, group)


def _matrix_cell(group: str, matrix: list[dict]) -> str:
    if group in ledger_rules.BATCHES:
        names = [row for row in matrix if group in row["batches"]]
    elif ledger_rules.VERSION.match(group):
        names = [row for row in matrix if group in row["versions"]]
    elif group in NOT_DELIVERED:
        names = [row for row in matrix if row["状态"] == group]
    else:
        names = []
    return "; ".join(f"{row['领域']} ({row['状态']})" for row in names) or "no matrix row"


def _count_table(title: str, counts: dict[str, Counter], order, extra=None) -> list[str]:
    dispositions = list(ledger_rules.DISPOSITIONS)
    header = [title, *dispositions, "Total"] + (["PORTING_MATRIX rows (status)"] if extra else [])
    lines = ["| " + " | ".join(header) + " |", "|" + "---|" * len(header)]
    total: Counter = Counter()
    for group in sorted(counts, key=order):
        total.update(counts[group])
        cells = [group, *(str(counts[group][d]) for d in dispositions), str(sum(counts[group].values()))]
        lines.append("| " + " | ".join(cells + ([extra(group)] if extra else [])) + " |")
    cells = ["Total", *(str(total[d]) for d in dispositions), str(sum(total.values()))]
    lines.append("| " + " | ".join(cells + ([""] if extra else [])) + " |")
    return lines


def porting_matrix_counts(ledger_text: str, matrix_text: str) -> str:
    reader = csv.DictReader(io.StringIO(ledger_text, newline=""))
    if reader.fieldnames != ledger_rules.LEDGER_COLUMNS:
        raise CollectError(f"{ledger_rules.LEDGER.as_posix()}: columns are not {ledger_rules.LEDGER_COLUMNS}")
    rows = list(reader)
    if not rows:
        raise CollectError(f"empty input: {ledger_rules.LEDGER.as_posix()} has no rows")
    matrix = parse_matrix(matrix_text)
    by_group: dict[str, Counter] = {}
    by_system: dict[str, Counter] = {}
    for row in rows:
        by_group.setdefault(ledger_group(row), Counter())[row["disposition"]] += 1
        by_system.setdefault(row["unit_id"].split(":", 1)[0], Counter())[row["disposition"]] += 1
    whole = [f"{row['领域']} ({row['状态']})" for row in matrix if ledger_rules.LEDGER.name in row.get("1.20.1 目标", "")]
    lines = [
        "# v1.8 porting matrix counts", "",
        f"Derived by `{TOOL}` from `{ledger_rules.LEDGER.as_posix()}` ({len(rows)} rows) and "
        f"`{PORTING_MATRIX.as_posix()}` ({len(matrix)} rows). It prints counts only; Root edits the matrix by hand. "
        f"The closure result is in `ledger-summary.json` and `ledger-closure-run.json`.", "",
        f"Matrix row for the whole ledger: {'; '.join(whole) or 'none found'}.", "",
        "## Dispositions per batch", "",
        "A v1.8 delivery's batch is the one its evidence folder names (`docs/work/v1.8.0-<batch>-...`), and a PLANNED "
        "row names its batch in the plan column. Rows an earlier version delivered are grouped by that version, and "
        "MERGED, DEFERRED and REJECTED rows by their disposition.", "",
        "Beside a batch stand the matrix rows that name it in any cell; a family token such as `C16` names each of "
        "its batches that no row names in its 最低验收 column. Beside a version stand the rows whose 目标版本 names "
        "it, and beside MERGED, DEFERRED and REJECTED the rows with that status.", "",
        *_count_table("Group", by_group, _group_order, lambda group: _matrix_cell(group, matrix)), "",
        "## Dispositions per system", "",
        "The system is the inventory kind: the part of the unit ID before the colon.", "",
        *_count_table("System", by_system, lambda system: system), "",
    ]
    return "\n".join(lines)


# Derived report 2: the basic-to-endgame progression through the witness routes.

def parse_witnesses(text: str) -> dict[str, dict]:
    """Node -> its first witness (``producer``, ``round``, ``prerequisites``) or mark (root, unreachable).

    An item-report witness lists its prerequisites inline; an advancement route nests them one level deeper.
    """
    nodes: dict[str, dict] = {}
    stack: list[tuple[int, dict]] = []
    for line in text.splitlines():
        record: dict | None = None
        if match := WITNESS.match(line):
            listed = match.group("prerequisites")
            record = {"mark": "root" if match.group("producer") == "root" else "witness",
                      "producer": match.group("producer"), "round": int(match.group("round")),
                      "prerequisites": [p for p in (listed or "").split(", ") if p]}
        elif match := MARKED.match(line):
            record = None if match.group("mark") == "above" else {"mark": match.group("mark")}
        elif match := LISTED_GOAL.match(line):
            record = {"mark": "witness" if match.group("mark") == "reachable" else "unreachable"}
        else:
            stack.clear()
            continue
        depth = (len(line) - len(line.lstrip(" "))) // 2
        while stack and stack[-1][0] >= depth:
            stack.pop()
        if stack and stack[-1][0] == depth - 1 and "producer" in stack[-1][1]:
            stack[-1][1]["prerequisites"].append(match.group("node"))
        if record is not None:
            nodes.setdefault(match.group("node"), record)
            stack.append((depth, record))
    return nodes


def _reached(witnesses: dict[str, dict], node: str) -> str:
    witness = witnesses.get(node)
    if witness is None:
        return "not in the witness routes"
    return "unreachable" if witness["mark"] == "unreachable" else "reached"


def progression_report(reports: dict[str, str]) -> tuple[str, list[str]]:
    with_tau = parse_witnesses(reports["registered-item-acquisition.md"])
    for node, witness in parse_witnesses(reports["classic-advancement-reachability.md"]).items():
        with_tau.setdefault(node, witness)
    without_tau = parse_witnesses(reports["registered-item-acquisition-without-tau-ceti.md"])
    for node, witness in parse_witnesses(reports["classic-advancement-reachability-without-tau-ceti.md"]).items():
        without_tau.setdefault(node, witness)
    table, details, unreachable = [], [], []
    for number, (name, machine, extra) in enumerate(STAGES, 1):
        nodes = [machine, *extra]
        problems = {node: _reached(with_tau, node) for node in nodes}
        problems = {node: state for node, state in problems.items() if state != "reached"}
        missing = [node for node in nodes if _reached(without_tau, node) != "reached"]
        rounds = [with_tau[node].get("round", 0) for node in nodes if node not in problems]
        result = "reachable" if not problems else "**UNREACHABLE**: " + "; ".join(
            f"`{node}` {state}" for node, state in problems.items())
        if problems:
            unreachable.append(name)
        table.append(f"| {number} | {name} | `{machine}` | {max(rounds) if rounds and not problems else '-'} | "
                     f"{result} | {'reachable' if not missing else 'not reached: ' + ', '.join(missing)} |")
        details += [f"### {number}. {name}", ""]
        for node in nodes:
            witness = with_tau.get(node)
            if node in problems:
                details.append(f"- `{node}`: **{problems[node].upper()}**")
            elif witness["mark"] == "root":
                details.append(f"- `{node}`: survival root")
            elif "producer" in witness:
                prerequisites = ", ".join(f"`{p}`" for p in witness["prerequisites"]) or "none listed"
                details.append(f"- `{node}` <- `{witness['producer']}` (round {witness['round']}); "
                               f"prerequisites: {prerequisites}")
            else:
                details.append(f"- `{node}`: reached (no route printed)")
        details.append("")
    lines = [
        "# v1.8 progression report: basic to endgame", "",
        f"Derived by `{TOOL}` from the witness routes that `RegisteredItemReachabilityTest` and "
        "`ClassicAdvancementReachabilityTest` write (`recipe-graph/`). Each stage names its machine and the "
        "first-acquisition witness of every graph node it needs: the producer and the evaluator round, which orders "
        "availability and is not play time. The graph models acquisition, not placement: a device that works only "
        "on a station is reached when its item is. A stage the graph cannot reach is marked **UNREACHABLE**. The "
        "last column repeats the check without the two Tau Ceti Levels.", "",
        f"Stages: {len(STAGES)}. Unreachable: {', '.join(unreachable) or 'none'}.", "",
        "| # | Stage | Machine | Round | Result | Without Tau Ceti |", "|---|---|---|---|---|---|",
        *table, "", "## Stage routes", "", *details,
    ]
    return "\n".join(lines).rstrip() + "\n", unreachable


# Collection, manifest and verification.

def _git(root: Path, *arguments: str) -> str | None:
    try:
        completed = subprocess.run(["git", "-C", str(root), *arguments], capture_output=True, timeout=60)
    except (OSError, subprocess.TimeoutExpired):
        return None
    return completed.stdout.decode("utf-8", "replace").strip() if completed.returncode == 0 else None


def repository_identity(root: Path) -> tuple[str | None, bool | None]:
    top = _git(root, "rev-parse", "--show-toplevel")
    if top is None or Path(top).resolve() != root.resolve():
        return None, None
    status = _git(root, "status", "--porcelain", "--untracked-files=no")
    return _git(root, "rev-parse", "HEAD"), (None if status is None else status == "")


def manifest_text(folder: Path) -> str:
    files = sorted(path.relative_to(folder).as_posix() for path in folder.rglob("*") if path.is_file())
    return "".join(f"{sha256((folder / name).read_bytes())}  {name}\n" for name in files if name != MANIFEST)


def verify(folder: Path) -> list[str]:
    """Problems of a staging folder against its manifest: mismatched, missing and unlisted files."""
    manifest = folder / MANIFEST
    if not manifest.is_file():
        return [f"missing manifest: {MANIFEST}"]
    listed: dict[str, str] = {}
    problems = []
    for number, line in enumerate(manifest.read_text(encoding="utf-8").splitlines(), 1):
        match = re.fullmatch(r"([0-9a-f]{64})  (\S.*)", line)
        if not match or PurePosixPath(match.group(2)).is_absolute() or ".." in PurePosixPath(match.group(2)).parts:
            problems.append(f"{MANIFEST} line {number} is malformed")
            continue
        listed[match.group(2)] = match.group(1)
    present = {path.relative_to(folder).as_posix() for path in folder.rglob("*") if path.is_file()} - {MANIFEST}
    for name, digest in sorted(listed.items()):
        if name not in present:
            problems.append(f"missing file: {name}")
        elif sha256((folder / name).read_bytes()) != digest:
            problems.append(f"hash mismatch: {name}")
    problems += [f"unlisted file: {name}" for name in sorted(present - set(listed))]
    return problems


def _check_output(root: Path, output: Path) -> None:
    releases = (root / RELEASES).resolve()
    if output == releases or releases in output.parents:
        raise CollectError(f"refusing to write into {RELEASES.as_posix()}: the final campaign writes it")
    if output.exists() and (not output.is_dir() or output.is_symlink() or any(output.iterdir())):
        raise CollectError(f"output exists and is not an empty folder: {output}")


def collect(root: Path, output: Path, jar: Path | None = None) -> dict:
    root, output = root.resolve(), output.resolve()
    jar = None if jar is None else jar.resolve()
    _check_output(root, output)
    reports_dir = root / REPORT_DIRECTORY
    sources = {f"{REPORT_DIRECTORY}/{name}": reports_dir / name for name, _, _ in REPORTS}
    sources[ledger_rules.LEDGER.as_posix()] = root / ledger_rules.LEDGER
    sources[PORTING_MATRIX.as_posix()] = root / PORTING_MATRIX
    scripts = [LEDGER_SCRIPT, RESOURCE_SCRIPT] + ([PROVENANCE_SCRIPT] if jar else [])
    problems = [f"missing input: {script}" for script in scripts if not (root / script).is_file()]
    if jar is not None and not jar.is_file():
        problems.append(f"missing input: --jar {jar}")
    data: dict[str, bytes] = {}
    texts: dict[str, str] = {}
    for label, path in sources.items():
        try:
            data[label] = read_input(path, label)
            texts[label] = data[label].decode("utf-8")
        except CollectError as error:
            problems.append(str(error))
        except UnicodeDecodeError:
            problems.append(f"unreadable input: {label} is not UTF-8")
    for name, _, title in REPORTS:
        first = texts.get(f"{REPORT_DIRECTORY}/{name}", title).splitlines()[0]
        if first != title:
            problems.append(f"unexpected report: {REPORT_DIRECTORY}/{name} starts with {first[:80]!r}, "
                            f"not {title!r}")
    if problems:
        raise CollectError("; ".join(problems))
    jar_digest = None if jar is None else sha256(jar.read_bytes())

    output.parent.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix=output.name + ".partial-", dir=output.parent))
    try:
        files: dict[str, str] = {}
        for name, staged, _ in REPORTS:
            _write(work / staged, data[f"{REPORT_DIRECTORY}/{name}"])
            files[staged] = f"{REPORT_DIRECTORY}/{name}"
        summary, record = ledger_closure(root)
        _write(work / LEDGER_SUMMARY, _json_bytes(summary))
        _write(work / LEDGER_RUN, _json_bytes(record))
        files[LEDGER_SUMMARY] = f"python -B {LEDGER_SCRIPT} --closure (stdout)"
        files[LEDGER_RUN] = f"python -B {LEDGER_SCRIPT} --closure (exit code and stderr)"
        audit = work / RESOURCE_AUDIT
        audit.parent.mkdir(parents=True, exist_ok=True)
        code, _, stderr = run_script(root, RESOURCE_SCRIPT, ["--output", str(audit)])
        _require_success(RESOURCE_SCRIPT, code, stderr)
        read_input(audit, RESOURCE_AUDIT)
        files[RESOURCE_AUDIT] = f"python -B {RESOURCE_SCRIPT} --output"
        if jar is not None:
            code, _, stderr = run_script(root, PROVENANCE_SCRIPT, ["--repository-root", str(root), "--jar",
                                                                   str(jar), "--output-dir", str(work / "assets")])
            _require_success(PROVENANCE_SCRIPT, code, stderr)
            for staged in PROVENANCE_OUTPUTS:
                read_input(work / staged, staged)
                files[staged] = f"python -B {PROVENANCE_SCRIPT} --jar {jar.name}"
        counts = porting_matrix_counts(texts[ledger_rules.LEDGER.as_posix()], texts[PORTING_MATRIX.as_posix()])
        _write(work / MATRIX_COUNTS, counts.encode("utf-8"))
        files[MATRIX_COUNTS] = f"derived from {ledger_rules.LEDGER.as_posix()} and {PORTING_MATRIX.as_posix()}"
        report, unreachable = progression_report({name: texts[f"{REPORT_DIRECTORY}/{name}"] for name, _, _ in REPORTS})
        _write(work / PROGRESSION, report.encode("utf-8"))
        files[PROGRESSION] = "derived from the recipe-graph witness routes"

        changed = [label for label, path in sources.items() if sha256(path.read_bytes()) != sha256(data[label])]
        if jar is not None and sha256(jar.read_bytes()) != jar_digest:
            changed.append(f"--jar {jar.name}")
        if changed:
            raise CollectError("inputs changed during collection: " + ", ".join(changed))
        head, clean = repository_identity(root)
        known = {name for name, _, _ in REPORTS}
        result = {
            "schema": SCHEMA, "tool": TOOL, "repository_head": head, "repository_clean": clean,
            "jar": None if jar is None else {"name": jar.name, "sha256": jar_digest},
            "files": files,
            "ledger_closure": {"exit_code": record["exit_code"], "result": record["result"]},
            "provenance": "collected" if jar is not None else "not collected: no --jar given",
            "progression": {"stages": len(STAGES), "unreachable": unreachable},
            "uncollected_reports": sorted(path.name for path in reports_dir.glob("*.md") if path.name not in known),
        }
        _write(work / SUMMARY, _json_bytes(result))
        size = sum(path.stat().st_size for path in work.rglob("*") if path.is_file())
        if size > MAX_PACKAGE_BYTES:
            raise CollectError(f"staging folder is {size} bytes, over the {MAX_PACKAGE_BYTES}-byte limit")
        (work / MANIFEST).write_text(manifest_text(work), encoding="utf-8", newline="\n")
        problems = verify(work)
        if problems:
            raise CollectError("staging folder fails its own manifest: " + "; ".join(problems))
        if output.exists():
            output.rmdir()
        work.rename(output)
        return result
    except BaseException:
        shutil.rmtree(work, ignore_errors=True)
        raise


def parse_args(argv: list[str] | None = None) -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--repository-root", type=Path, default=ROOT)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--output", type=Path, help="the staging folder to create (absent or empty)")
    mode.add_argument("--verify", type=Path, help="recheck a staging folder against its manifest")
    parser.add_argument("--jar", type=Path, help="the built mod JAR for the provenance audit")
    return parser.parse_args(argv)


def main(argv: list[str] | None = None) -> int:
    args = parse_args(argv)
    if args.verify is not None:
        problems = verify(args.verify)
        for problem in problems:
            print(f"[FAIL] {problem}", file=sys.stderr)
        print(f"[{'FAIL' if problems else 'PASS'}] {args.verify}: {len(problems)} problems")
        return 1 if problems else 0
    try:
        result = collect(args.repository_root, args.output, args.jar)
    except CollectError as error:
        print(f"[FAIL] {error}", file=sys.stderr)
        return 1
    closure = result["ledger_closure"]
    print(f"[PASS] Staged {len(result['files'])} files in {args.output}. Ledger closure: exit {closure['exit_code']} "
          f"({closure['result']}, recorded). Provenance: {result['provenance']}. Unreachable stages: "
          f"{', '.join(result['progression']['unreachable']) or 'none'}.")
    if result["uncollected_reports"]:
        print("[NOTE] Not collected: " + ", ".join(result["uncollected_reports"]))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
