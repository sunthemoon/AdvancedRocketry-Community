#!/usr/bin/env python3
"""Inventory native escrow case producers for the dispatch compatibility regression.

Imports runner declarations only; never calls a runner or starts a JVM. The
inventory is test data, not a production allowlist. Unknown cases still fall back.
"""
from __future__ import annotations

import ast
from collections import defaultdict
import importlib
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / "src/test/resources/escrow-probe-cases.tsv"
PROPERTY = "-Dadvancedrocketrycommunity.escrowCase="
# Reviewed finite domains, including the wrappers which delegate to phases.run.
DECLARED = (
    "cargo_cuts", "escrow_forced_stop", "escrow_destination", "escrow_phases",
    "escrow_reconciliation", "escrow_pre_f2", "escrow_source_clock", "fluid_loader_restart",
    "rocket_escrow_audit", "rocket_settlement_completion", "rocket_settlement_intent",
    "escrow_arrival", "escrow_terminal", "escrow_returns", "escrow_pruning", "cargo_s4_s5_cuts", "loader_s6",
)
SPECIAL = ("cargo_native_benchmark", "cargo_reconciliation", "escrow_benchmark", "f3_n2_cuts", "fill_countdown",
           "governed_benchmark", "item_cuts", "loader_cuts", "retire_before_fill", "transfer_concurrency")
DELEGATED = {"run_v180_" + name for name in ("escrow_arrival", "escrow_terminal", "escrow_returns", "escrow_pruning")}
SUPPLIERS = {"run_v180_" + name for name in DECLARED + SPECIAL}
RELATED = SUPPLIERS | {"loader_cut_oracle", "cargo_storage_oracle", "cargo_contention_oracle",
                       "cargo_recovery_oracle", "item_cuts_oracle", "item_cut_fixture", "loader_s6_named_pad"}
TOKEN = re.compile(r"(?:K\d+|C[123]|E[1-5](?:_[A-Z0-9_]+|L[12]_[A-Z0-9_]+)?|G3(?:L2)?|BENCH|GOVERNED_BENCH|"
                   r"(?:FC|FS|FF|IC|LF|FT|RT|FP|FD|DP|SC|PF|SR|SO|RA|IS|IR)_[A-Z0-9_]+)")


def inventory():
    scripts = sorted((ROOT / "scripts").glob("run_v1*.py"))
    direct = {path.stem for path in scripts if PROPERTY in path.read_text(encoding="utf-8")}
    if direct != SUPPLIERS - DELEGATED:
        raise ValueError(f"Review changed case producers: {direct ^ (SUPPLIERS - DELEGATED)}")
    delegated = set()
    for path in scripts:
        tree = ast.parse(path.read_text(encoding="utf-8"))
        aliases = {alias.asname or alias.name for node in ast.walk(tree) if isinstance(node, ast.Import)
                   for alias in node.names if alias.name in direct}
        if any(isinstance(node, ast.Call) and isinstance(node.func, ast.Attribute) and node.func.attr == "run"
               and isinstance(node.func.value, ast.Name) and node.func.value.id in aliases
               and any(keyword.arg == "cases" for keyword in node.keywords) for node in ast.walk(tree)):
            delegated.add(path.stem)
    if delegated != DELEGATED:
        raise ValueError(f"Review changed delegated case producers: {delegated ^ DELEGATED}")
    rows = defaultdict(set)

    def add(values, origin):
        for value in values:
            if not isinstance(value, str) or not value or any(c.isspace() for c in value):
                raise ValueError(f"Invalid inventory value from {origin}: {value!r}")
            rows[value].add(origin)

    for name in DECLARED:
        module = importlib.import_module("run_v180_" + name)
        add(module.CASES, "scripts/" + module.__name__ + ".py")
    for name in SPECIAL:
        module = importlib.import_module("run_v180_" + name)
        if name == "cargo_reconciliation":
            values = module.CASES + module.ITEM_CASES
        elif name == "item_cuts":
            values = [spec["probe"] for spec in module.CASES.values()]
        elif name == "loader_cuts":
            values = module.oracle.CASES
        elif name in ("fill_countdown", "retire_before_fill"):
            values = [module.PREFIX + cut for cut in module.CUTS]
        elif name == "transfer_concurrency":
            values = module.CONSUMERS.values()
        elif name == "f3_n2_cuts":
            values = [module.GOVERNED]
        else:
            values = ["BENCH" if name == "escrow_benchmark" else "GOVERNED_BENCH"]
        add(values, "scripts/" + module.__name__ + ".py")
    tests = []
    for path in sorted((ROOT / "tests").glob("test_*.py")):
        tree = ast.parse(path.read_text(encoding="utf-8"))
        imports = {alias.name for node in ast.walk(tree) if isinstance(node, ast.Import) for alias in node.names}
        imports.update(node.module for node in ast.walk(tree) if isinstance(node, ast.ImportFrom))
        literals = [node.value for node in ast.walk(tree) if isinstance(node, ast.Constant) and isinstance(node.value, str)]
        commands = [value.split(PROPERTY, 1)[1].split()[0] for value in literals if PROPERTY in value and value.split(PROPERTY, 1)[1].strip()]
        if imports & RELATED or commands:
            tests.append(path.relative_to(ROOT).as_posix())
            add((value for value in literals if TOKEN.fullmatch(value)), tests[-1])
            add(commands, tests[-1])
    return dict(sorted(rows.items())), dict(scanned_scripts=len(scripts), direct=sorted(direct),
                                          delegated=sorted(DELEGATED), tests=tests)


def render(rows):
    return "# escrowCase\torigins (finite runner domains and related test tokens; not a routing allowlist)\n" + "".join(
        case + "\t" + ";".join(sorted(origins)) + "\n" for case, origins in rows.items())


if __name__ == "__main__":
    rows, coverage = inventory()
    if sys.argv[1:] != ["--check"]:
        print(render(rows), end="")
    elif TARGET.read_text(encoding="utf-8") != render(rows):
        raise SystemExit("Case inventory differs; review producers and update the test resource")
    else:
        print(f"PASS: {len(rows)} case/test tokens, {len(coverage['direct'])} direct producers, "
              f"{len(coverage['delegated'])} delegated producers, {len(coverage['tests'])} related tests; "
              f"{coverage['scanned_scripts']} run_v1*.py files scanned")
