"""Bounded identity, decision-only diff and unchanged-companion checks.

This static author check is not independent review, runtime or crash proof.
It writes evidence only beside this script in its fresh owned Temp directory.
"""

import csv
import difflib
import hashlib
import io
import json
from pathlib import Path


ROOT = Path("D:/GitHub/AdvancedRocketry-Community")
OUT = Path(__file__).resolve().parent
BASE = ROOT / "docs/work/v1.8.0-c17-contract/proposals/revision-03"
NAME = "ADR-065-CLASSIC-PROPULSION-STATION-CONTROLS-AND-SOLAR.md"
OWNER = ROOT / "docs/work/v1.8.0-c17-contract/OWNER-DECISIONS.md"
C18_OWNER = ROOT / "docs/work/v1.8.0-c18-contract/OWNER-DECISIONS.md"
REVIEW_RAW = ROOT / "docs/work/v1.8.0-c17-contract/reviews/revision-03/REVIEW-03.raw.txt"


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


assert sha(BASE / NAME) == "92e706ae84e6565f605227f7794fb83850a23c4f2ca1915686d28c61fa72c321"
assert sha(OWNER) == "279d7cb58682b0c0e4bc58abf93dce3a32effca4a7a94b12f5d4d246fe39c00c"
assert sha(C18_OWNER) == "ed4352ee786435afa6573f3aede578528e9ae78914cbae2147fccae98f4d028f"
assert sha(REVIEW_RAW) == "f22b9736e99b554d8d45fbdc3ec1b37e38ebd1f1f53b2af2ee87f7a343c00821"
base_bytes = (BASE / NAME).read_bytes()
new_bytes = (OUT / NAME).read_bytes()
assert b"\r" not in base_bytes and b"\r" not in new_bytes
base = base_bytes.decode("utf-8")
new = new_bytes.decode("utf-8")

# These replacements enumerate the entire author edit scope. All other bytes,
# including technical/numeric behavior, must equal the frozen reviewed base.
edits = [
    ("revision: 3\n", "revision: 4\n"),
    ("owner_decisions_confirmed: [D2-A]\nowner_decisions_pending: [D1, D1-disassembly, D3]",
     "owner_decisions_confirmed: [D1-A, D1-disassembly-A, D2-A, D3-A]\nowner_decisions_pending: []"),
    ("Gate result. The recommendation clauses below require independent review. D2-A\n"
     "was expressly chosen by the owner on 2026-10-03: continuous three-axis analytic\n"
     "rotation and logical altitude, no physical block movement, with elevator endpoint\n"
     "conditions protected while linked/busy. This confirms that product choice, not\n"
     "the entire ADR or its implementation. D1, its disassembly subchoice and D3 remain\n"
     "material product choices that must not be silently accepted by an implementer.",
     "Gate result. The clauses below require independent review. The owner receipt\n"
     "dated 2026-10-03 confirms D1-A, D1-disassembly-A, D2-A and D3-A against reviewed\n"
     "revision 3. These are product selections, not acceptance of the entire ADR or\n"
     "its implementation. This revision records their selected status without changing\n"
     "technical or numeric behavior. The unselected alternatives are not fallbacks;\n"
     "changing a selected product boundary requires a new explicit owner decision."),
    ("## 3. D1: propulsion and fuel representation (owner choice)",
     "## 3. D1: propulsion and fuel representation (owner-confirmed D1-A)"),
    ("**Recommendation D1-A:**", "**Owner-confirmed D1-A:**"),
    ("The proposed numeric baseline, subject to D1 acceptance:",
     "The proposed numeric baseline remains unchanged, subject to scoped contract acceptance:"),
    ("The D1-A compatibility recommendation is explicit and order-independent:",
     "The owner-selected D1-A compatibility boundary is explicit and order-independent:"),
    ("included in the pending D1\n", "included in the selected D1-A\n"),
    ("**Alternative D1-B:**", "**Unselected alternative D1-B:**"),
    ("Acceptance must name D1-A or D1-B and any adjusted numeric/efficiency values.\n"
     "Until then, numeric runtime implementations are not authorized by this draft.",
     "D1-A is the selected product boundary; D1-B and numeric/efficiency retuning are\n"
     "not selected. Scoped contract review and acceptance remain required before\n"
     "numeric runtime implementation; this decision-only revision grants neither."),
    ("### 3.1 D1-disassembly: remaining propulsion resources (owner subchoice)",
     "### 3.1 D1-disassembly: remaining propulsion resources (owner-confirmed D1-disassembly-A)"),
    ("**Recommended D1-disassembly-A, pending owner confirmation:**",
     "**Owner-confirmed D1-disassembly-A:**"),
    ("**Alternative D1-disassembly-B:**", "**Unselected alternative D1-disassembly-B:**"),
    ("Do not implement either typed materialization/disposal choice until the owner\n"
     "confirms it and independent resource/recovery review is complete.",
     "D1-disassembly-A is selected; D1-disassembly-B is not an implementation fallback.\n"
     "Independent resource/recovery review and scoped contract acceptance remain required\n"
     "before implementing the selected disposal policy."),
    ("not make a two-store transfer crash-atomic. Therefore implementation must either\n"
     "add an escrow/receipt authority with demonstrable durable barriers and full S2\n"
     "cut evidence, or explicitly obtain owner acceptance of the ADR-027/054 ordinary\n"
     "container torn-save residual and publish it. In-tick conservation plus clean\n"
     "restart is not an exactly-once crash claim. This is D3 below, not a test waiver.",
     "not make a two-store transfer crash-atomic. Owner-selected D3-A requires an\n"
     "escrow/receipt authority with demonstrable durable barriers and full S2 cut\n"
     "evidence before admission. No added ADR-027/054 ordinary-container torn-save\n"
     "residual is selected. In-tick conservation plus clean restart is not an\n"
     "exactly-once crash claim. This is D3 below, not a test waiver."),
    ("refuse migration/start intact. Conditional C18 first-event source outboxes must\n"
     "join this same root-5/journal-3 field/migration freeze only if C18 D3 is selected;\n"
     "this neither accepts that pending policy nor bumps the roots independently.",
     "refuse migration/start intact. Owner-selected C18 D3 first-event source outboxes\n"
     "must join this same root-5/journal-3 field/migration freeze; the product selection\n"
     "neither accepts its shared technical contract nor bumps the roots independently."),
    ("recovery-contract gate. D3-B retains matching in-memory/clean-restart receipts\n"
     "but must disclose its owner-accepted torn-save residual and cannot claim durable\n"
     "exactly-once, with or without a current satellite identity.",
     "recovery-contract gate. Unselected D3-B would retain matching in-memory/clean-restart\n"
     "receipts but require a new explicit owner decision on its added torn-save residual;\n"
     "it could not claim durable exactly-once, with or without a current satellite identity."),
    ("## 10. D3: two-store crash durability decision\n",
     "## 10. D3: two-store crash durability decision (owner-confirmed D3-A)\n"),
    ("**Recommendation D3-A:**", "**Owner-confirmed D3-A:**"),
    ("**Alternative D3-B:**", "**Unselected alternative D3-B:**"),
    ("or malformed-data loss. No implementation may silently choose B to shorten tests.",
     "or malformed-data loss. The owner selected D3-A, not this added residual or a\n"
     "same-tick-only substitute. No implementation may silently choose B to shorten tests."),
    ("fixed verdict is prescribed. C17a production starts only after D1/D3 and its\n"
     "schema/recovery and D1-disassembly contracts are accepted; C17b orbital",
     "fixed verdict is prescribed. The owner-selected D1-A, D1-disassembly-A and D3-A\n"
     "product boundaries still require independent review and acceptance of their\n"
     "schema/recovery contracts, including D3-A forced-stop recovery verification\n"
     "before admitting its interactions. C17b orbital"),
]
expected = base
for old, replacement in edits:
    assert expected.count(old) == 1, old
    expected = expected.replace(old, replacement, 1)
assert expected.encode("utf-8") == new_bytes, "Change outside the enumerated decision-only edit scope"
assert 'status: PROPOSED\n' in new
assert 'accepted_by: ""\naccepted_at: ""' in new
assert "owner_decisions_pending: []" in new

companion_hashes = {}
for name in ("covered-ledger.csv", "upstream-source-checks.json"):
    assert (BASE / name).read_bytes() == (OUT / name).read_bytes(), name
    companion_hashes[name] = sha(OUT / name)
rows = list(csv.DictReader(io.StringIO((OUT / "covered-ledger.csv").read_text(encoding="utf-8-sig"))))
ledger = list(csv.DictReader(io.StringIO((ROOT / "docs/work/v1.8.0-content-ledger.csv").read_text(encoding="utf-8-sig"))))
covered = [row for row in ledger if row["plan"] in {"C17a", "C17b", "C17c"} and row["disposition"] == "PLANNED"]
assert sorted(rows, key=lambda row: row["unit_id"]) == sorted(covered, key=lambda row: row["unit_id"])
assert len(rows) == 41 and len({row["unit_id"] for row in rows}) == 41
facts = json.loads((OUT / "upstream-source-checks.json").read_text(encoding="utf-8"))
assert all(fact["matches"] and fact["sha256"] == fact["manifest_sha256"] for fact in facts)

diff = "".join(difflib.unified_diff(base.splitlines(keepends=True), new.splitlines(keepends=True),
    fromfile=f"revision-03/{NAME}", tofile=f"revision-04/{NAME}"))
(OUT / "revision-03-to-04.diff").write_text(diff, encoding="utf-8", newline="\n")
result = {
    "kind": "author static decision-only check; not independent review/runtime proof",
    "base_sha256": sha(BASE / NAME),
    "proposal_sha256": sha(OUT / NAME),
    "diff_sha256": sha(OUT / "revision-03-to-04.diff"),
    "root_owner_receipt_sha256": sha(OWNER),
    "root_c18_owner_receipt_sha256": sha(C18_OWNER),
    "prior_raw_review_sha256": sha(REVIEW_RAW),
    "decision_only_replacements": len(edits),
    "outside_replacement_bytes": "unchanged",
    "companion_hashes": companion_hashes,
    "current_ledger_exact_rows": len(rows),
    "unchanged_source_check_records": len(facts),
    "status": "PROPOSED; acceptance fields empty",
    "runtime_native_gate_execution": "not performed",
}
(OUT / "PROBE-RESULTS.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8", newline="\n")
print(json.dumps(result, indent=2))
