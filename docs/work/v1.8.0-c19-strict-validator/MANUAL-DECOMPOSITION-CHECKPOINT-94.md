# C19 manual scenario decomposition checkpoint94

Date: 2026-10-10. Version: v1.8.0 under ADR-060. Status: separately committed
and normally pushed development candidate, not Main-integrated.

## Source scope and organization

[Task92](MANUAL-DECOMPOSITION-TASK-92.md) produces
17bb40bcb3b3490469a6433f2d7de2f1baa9b620 on
fix/v1.8.0-manual-scenario-decomposition in
D:/GitHub/arce-v180-manual-decomposition-20261010-92. Parent is
f2587b54b753d77bb985e652612003eee54cc769; tree is
a1c258b6bdc6180f1299ff72753e042e6fbadf99. Exactly 18 test-only files change,
4,231 insertions/3,828 deletions. Root checks explicit cached stat/check before
commit and pushes normally. Full candidate status/index are empty; upstream and
actual commit/tree/blob objects bind to the tested postimages.

All 160 original function nodes are source/AST-exact, including all 137 scenario
methods, CLI, setUp, helpers and nested functions. Frozen original 138 selections
and loader order remain. Original runnable classes and CLI source-location
semantics stay in the facade; PNG functions remain explicit re-exports. Fourteen
non-TestCase scenario mixins plus two fixture helpers have explicit forward
imports, no lifecycle overrides, wildcard/reverse imports or load_tests hooks.
Every changed/new class is below 500 AST lines; facade 60, maximum 439.
Defining-method module/traceback relocation is the explicitly permitted difference.

Original repository-fixture module/tests and production/reader/protocol/bootstrap
inputs remain unchanged. No hardlink, alternate, shared mutable case files,
approval/result cache, shortened selection, assertion weakening or budget change.
Per-case raw physical repository/artifact isolation and cleanup remain intact.
New three-case organization module checks selections/IDs/order, non-runnable
groups and lifecycle composition. The small text ID fixture is not approval data.

This addresses the manual class-size prerequisite in the reviewed candidate,
superseding that unresolved portion of checkpoint87. Historical f258 observations
and helper mutation remain. ADR071 remains PROPOSED, not an accepted size waiver;
adjacent bootstrap ADR070 and Main integration remain separate.

## Actual authored checks and independent review

Root runs each precommit complete module once: organization3 wait0 in 0.424684 s,
unchanged fixture3 wait0 in 1.641024 s and original manual138 wait0 in 86.659149 s.
All raw EOFs and full assigned endpoint bindings match. Actual committed
organization3 separately returns original wait0 in 0.323014 s. No failures,
skips, cap errors or child termination in these four commands. Runtime directories
end empty and are removed only by nonrecursive rmdir. Elapsed observations are
not a controlled performance comparison or whole-suite qualification.

[Independent93](MANUAL-DECOMPOSITION-REVIEW-TASK-93.md) reviews the actual diff and
runs organization3/manual138 once each under the same original 180-second budgets:
wait0 in 0.353521/88.272179 s, full EOFs and no test failure/error/skip/termination.
It finds no source defect in the reviewed decomposition. Organization endpoints
match. Manual tested-source/base/tasks/ID/helper/Python/scoped-index bindings match,
but Main full status gains Root's untracked Task95; broad endpoint equality is
not claimed. All original helper generations stay frozen during those commands.
Default basename discovery identity/order is statically assessed, not separately
executed by the peer; named selections are runtime-checked. Root's literal complete
discovery is a distinct actual committed command, not replaced by that assessment.

Review93 retains a preparation-only read of a 1,602,557-byte physical index,
outside its one-MiB per-file cap, plus early unbound instruction acquisition.
Original helper generations and refusal/corrected outputs remain. Both authored
commands use bounded scoped stage listings instead; no retroactive compliance
claim. Final source/assigned-input cutoff is 2026-10-09T23:58:15.241199+00:00.
Root reads the full report and independently rehashes its exact 40-file packet
before source commit; no source read after cutoff is assigned to the peer.

## Complete qualification and finite evidence

At actual committed17bb, literal complete discovery without -p, PID18484, times
out at180.015010 seconds, original execution exitnull, no final Ran/OK summary.
There are339 complete raw ok rows, not whole-suite qualification. Unfinished:
FinalG0ReviewInputsTests.test_generate_rejects_existing_outside_and_traversal_outputs.
One original-child kill; distinct postdeadline owned wait1/drain0.0055907 seconds
reaches full EOFs, stdout88/stderr57423 bytes, no cap/error. Full Root assigned
endpoint bindings match. The separate ten-second observation does not extend or
qualify the failed original attempt. Own tmpfre6a8mj remains uninspected. No
descendant/unowned process query/termination or inherited cleanup occurs.

Root's initial object-binding observer exits1 decoding a binary tree as UTF-8;
original helper stays unchanged, fresh binary-safe v2 exits0 and recomputes actual
commit/tree/blob IDs. Source commit/push and tests are not retried. Initial PNG
generator self-import is corrected before source application/testing with a fresh
V2 inventory; the original diagnostic remains. An initial peer91 control-file
count mistake is retained, then corrected in fresh finite verification. None is
an authored test repair or historical custody proof.

Sealed leaves under D:/GitHub/ARCE-Task-Evidence/v1.8.0:

| Leaf | Files / bytes | Manifest SHA-256 |
|---|---|---|
| c19-remaining-cost-analysis-20261010-91 |17 / 1034322|c96eff198193b86574706a669918f8ec1e7d55b7cc8d5d5af43958bb1681bf4e|
| c19-manual-decomposition-root-20261010-92 |98 / 1351781|fa8cf57a0a3f8911d26fa711bf70905a088a72617d8b4f67c5d48102e547bc80|
| c19-manual-decomposition-review-20261010-93 |40 / 3110722|2786b1fdc6735f4e719ce8fbacbbdafe211b17b00d6363e5093ad784a0affd64|
| c19-manual-decomposition-evidence-review-20261010-95 |20 / 962476|96cf3663f9731b69c49c22d7a29f5e0c6cd98d7e5aea785aa83873e989a8aceb|

Root92 REPORT SHA256920ea2f46b0a21fd0e3fe35a8e95dab356d722fc0252abbde89f39a258bcaacc;
Review93 REPORTc8a38a1387937ffb07d7013ae1f5aa01f2d8314de7b268baa925ccd1fb60441a.
Root reads the complete independent reports and separately rehashes exact
payload/control inventories. [Finite evidence95](MANUAL-DECOMPOSITION-EVIDENCE-TASK-95.md)
is a separate read-only audit; no source/target execution or Gate decision.
It finds no additional material reconciliation mismatch. Exact inventories and
controls, seven original receipts/raw summaries, all160 original function nodes,
138 IDs and actual commit/tree/18 blobs reconcile independently. It retains
Medium whole failure/cap deviation, Low early instruction custody/Main status
limits, and its own header/range/descriptor parser diagnostics. Final reverse-
patch and byte/SHA addendum are authoritative, not repairs to assigned evidence.
REPORT SHA2569a60bb032f1c3e2245b26f016f3703628bf7237060517305100b382863f9393d;
SEAL6f36c20add02f174cdcd4b4b71efc2b5bb3aa90fa70b31ee54948f216966009c.
Final assigned-input cutoff2026-10-10T00:16:00.605055+00:00. Root reads the full
report and rehashes all four packets in fresh Main records96. Those retained
limits stay open; no live-source, target, remote or Gate approval is supplied.

## Remaining version obligations

[Analysis91](REMAINING-COST-ANALYSIS-TASK-91.md) statically identifies separate
final-input/final-validator fixture opportunities, with source-derived 210/297
fewer initial Git starts respectively. No target execution or measured savings;
the prior timeout does not establish a historical cause. Packet shared-clone
semantics require separate review. Its sealed report and exact inventory are
read/rehash-verified by Root; no implementation is bundled here.

No Main source integration, strict/Markdown, clean build/DataGen/GameTest,
native/server/restart, GPU/two-client or JSON memory qualification occurs here.
These are required and unexecuted, not waived/N/A. Main actual qualification
0cefe86e, native 62 ERROR, Markdown unsafe/missing references, R32-03, historical
operational gaps, sleep behavior matrix and save-writer prerequisites remain.
ADR069 normative memory-window adoption is not resource acceptance. No sleep or
writer activation, assets/upstream import, unknown acquisition or Claude use.
User AGENTS.md stays untouched; v1.8 IN_PROGRESS/IMPLEMENTING, 186 PLANNED /
154 REVIEW, acceptance cursor v1.0 and all G0-G9 remain unchanged. Continue only
current-version qualification and separately scoped integration prerequisites.
