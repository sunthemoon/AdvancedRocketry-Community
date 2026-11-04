"""Generate inert ledger-fixture edits for the proposal's adoption step 5.

The patch changes fixture setup only. Applying it and simulating acceptance
are test operations in disposable copies, not adoption or an owner decision.
"""
from __future__ import annotations

import difflib
import json

BEACON = "textures/blocks/beacon.png"
TEST = "tests/test_validate_v180_content_ledger.py"


def new_releases(derivation: dict, excluded: set[str]) -> list[str]:
    # ADR-061 relations count in either direction; HIT wins over SUSPECT.
    related = set()
    for entry in derivation["assets"]:
        for match in entry.get("related", []):
            if match["level"] == "HIT":
                if entry["asset"] == BEACON:
                    related.add(match["asset"])
                if match["asset"] == BEACON:
                    related.add(entry["asset"])
    return sorted(related & excluded)


def fixture_patch(original: str, before: dict, allow_lines: list[str], releases: list[str]) -> str:
    helper = '''    def _restore_beacon_review_fixture(self, allowlist: bool = False) -> None:
        # W268 adoption: restore the original row; all test assertions stay unchanged.
        before = __BEFORE__
        self._set_rule(before["pattern"], **{k: v for k, v in before.items() if k != "pattern"})
        if allowlist:
            path = self.root / ALLOWLIST
            kept = set(path.read_text(encoding="utf-8").splitlines())
            kept.add("textures/blocks/beacon.png\\tREVIEW")
            raw = "".join(line + "\\n" for line in sorted(kept)).encode("utf-8")
            path.write_bytes(raw)
            adr = self.root / "docs/decisions/ADR-062-CLASSIC-CONTENT-DISPOSITIONS-AND-BATCHES.md"
            adr.write_text(re.sub(r"^import_allowlist_sha256:.*$",
                                 "import_allowlist_sha256: " + hashlib.sha256(raw).hexdigest(),
                                 adr.read_text(encoding="utf-8"), count=1, flags=re.M), encoding="utf-8")
            findings = self.root / ORIGIN_FINDINGS
            data = json.loads(findings.read_text(encoding="utf-8"))
            data["findings"] = [f for f in data["findings"] if f["asset"] != "textures/blocks/beacon.png"]
            findings.write_text(json.dumps(data), encoding="utf-8")

'''.replace("__BEFORE__", repr(before))
    updated = original
    for name, call in (("test_excluded_finding_forces_exclusion", "()"),
                       ("test_import_for_a_deferred_unit_fails", "()"),
                       ("test_review_needs_a_cleared_finding_to_import", "(allowlist=True)")):
        anchor = f"    def {name}(self) -> None:\n"
        if updated.count(anchor) != 1:
            raise ValueError("Ledger fixture method changed: " + name)
        insertion = helper if name == "test_excluded_finding_forces_exclusion" else ""
        updated = updated.replace(anchor, insertion + anchor + "        self._restore_beacon_review_fixture" + call + "\n", 1)
    # Insert after the releases assignment in this one method, without relying
    # on its existing list of filenames or changing any assertion.
    start = updated.index("    def test_review_needs_a_cleared_finding_to_import(")
    end = updated.index("\n    def ", start + 1)
    method = updated[start:end]
    anchor = '        self._write_findings([{"asset": "textures/blocks/beacon.png", "decision": "CLEARED",'
    if method.count(anchor) != 1:
        raise ValueError("Ledger clearance fixture changed")
    method = method.replace(anchor, "        released += " + json.dumps(releases) + "\n" + anchor, 1)
    updated = updated[:start] + method + updated[end:]
    return "".join(difflib.unified_diff(original.splitlines(keepends=True), updated.splitlines(keepends=True),
                                       fromfile="a/" + TEST, tofile="b/" + TEST, n=0))
