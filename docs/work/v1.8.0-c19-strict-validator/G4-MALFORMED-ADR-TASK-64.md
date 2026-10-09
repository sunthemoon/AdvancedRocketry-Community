# C19 G4 malformed ADR revision64

Date: 2026-10-10. Version: v1.8.0, ADR-060. Status: source candidate d605be33 committed/pushed; focused qualification only.

## Scope and checkout

Root implements the previously registered R32-02 in an independent worktree:
D:/GitHub/arce-v180-g4-malformed-adr-20261010-64, branch
fix/v1.8.0-g4-malformed-adr. Base is Main documentation checkpoint
0686abab94b65ebf458b2b5fcbdbca185de507e7. Main source and other candidates
remain unchanged. This is a C19 diagnostic correctness slice, not an attempted
explanation or budget change for full Python timeout.

Write scope in that checkout: scripts/validate_v002_g4_applicability.py and
tests/test_validate_v002_g4_applicability.py only. Root also owns this task,
its review task/checkpoint and the canonical C19 progress records. No ADR-005,
approval policy, source provenance, runtime Java, assets, saves, build settings,
protocol, timeout, enum vocabulary or acceptance-budget change is authorized.

## Verification

Add direct public-text-entry regression coverage for nonstring JSON enum values
and parser recursion in either JSON record. Run the added cases on the unchanged
base script once, retaining actual outcomes; apply the minimal diagnostic fix
then run the complete G4 module and relevant repository integration cases.
Preserve all original14 G4 methods and assertions. A fresh independent worker
reviews actual diff and reruns the complete G4 module, without inherited Root
context or predetermined findings. R32-03's broader wrapper coverage is not
closed by this scope. No whole C19 or Required Gate approval follows.

Root writes its fresh evidence leaf
D:/GitHub/ARCE-Task-Evidence/v1.8.0/c19-g4-malformed-root-20261010-64 and fresh
TEMP/TMP sibling c19-g4-malformed-runtime-20261010-64. Python3.13.15 pinned
executable, -X utf8 -B; original180-second command ceiling, read/file1 MiB,
combined streams256 KiB, leaf4 MiB. Bind actual source/script/test/contract/helper/
executable identities and scoped index before/after each invocation. Preserve
negative receipts and complete raw streams, original owned child wait exits,
argv/PID/UTC. No retry of the same postimage, inherited evidence/helper execution,
unowned process control or old TEMP cleanup. Authored cleanup only.

Commit and normally push only the two owned source files after independent
review. Before HEAD movement, confirm the reviewer is finished and source
binding ended. Check staged scope/stat and whitespace. Actual committed-source
module execution must remain distinct from precommit results. Main integration,
full broad/strict/standard/native qualification and delivery are not authorized
by source review or focused checks alone. Seal evidence exactly; update existing
plan/log rather than creating another development plan.

Checkpoint68 records actual negative/positive/committed commands and independent
review. Original64's observer fails after the target runs; no child exit receipt
is recovered. Explicit65 is a separate successor. Scope audit69 retains Medium
R69-01 for independent66's C: rather than assigned D: runtime. Source candidate
is not Main-integrated or whole-qualified; no Required Gate approval.
