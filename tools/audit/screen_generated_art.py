#!/usr/bin/env python3
"""Screen the textures a DataGen root writes against vanilla clients (ADR-061 section 4.8, ADR-063 section 9).

The art of a v1.8 batch is drawn new (``NEW``), so nothing in it is an imported legacy file; this screen runs the
same whole-image and region measures as ``vanilla_derivation.py`` over the generated PNG files and reports the best
vanilla match of each. Any verdict other than CLEAR fails the check.

    python tools/audit/screen_generated_art.py --root src/generated/v1.8/resources \\
        --vanilla 1.20.1=client.jar [--vanilla 1.12.2=client-1.12.2.jar] [--output report.json]
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

try:
    from tools.audit import vanilla_derivation as derivation
except ImportError:  # run as a script: tools/audit is the script directory
    sys.path.insert(0, str(Path(__file__).resolve().parent))
    import vanilla_derivation as derivation  # noqa: E402


def screen(root: Path, specs: list[str]) -> dict:
    sources, _, vanilla = derivation.load_vanilla(specs)
    candidates = []
    for path in sorted(root.rglob("*.png")):
        width, height, rgba = derivation.decode_rgba(path.read_bytes())
        candidates.append((path.relative_to(root).as_posix(), derivation.Image(width, height, rgba)))
    best = derivation.analyse_images(candidates, vanilla)
    files = {}
    for name, _ in candidates:
        _, verdict, record = best.get(name, (None, "CLEAR", {}))
        files[name] = {"verdict": verdict, "best": record.get("reference"), "record": record}
    counts: dict[str, int] = {}
    for entry in files.values():
        counts[entry["verdict"]] = counts.get(entry["verdict"], 0) + 1
    return {"schema_version": 1, "vanilla": sources, "counts": counts, "files": files}


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--root", required=True, type=Path)
    parser.add_argument("--vanilla", required=True, action="append", help="LABEL=PATH of a vanilla client JAR")
    parser.add_argument("--output", type=Path)
    args = parser.parse_args(argv)
    result = screen(args.root, args.vanilla)
    text = json.dumps(result, indent=2, sort_keys=True, default=str) + "\n"
    if args.output:
        args.output.write_text(text, encoding="utf-8", newline="\n")
    print(json.dumps(result["counts"], sort_keys=True))
    flagged = sorted(name for name, entry in result["files"].items() if entry["verdict"] != "CLEAR")
    for name in flagged:
        print(f"{result['files'][name]['verdict']}: {name} ~ {result['files'][name]['best']}")
    return 1 if flagged else 0


if __name__ == "__main__":
    raise SystemExit(main())
