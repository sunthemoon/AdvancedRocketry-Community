# Gravity diagnostic source review

Date: 2026-10-06. Reviewer/integrator: Root; implementation: separate worker.
Reviewed isolated source checkpoint: `985362f8a8a3a1b3c3550223b3e70b53a6b2efbc`.
Review parent: `37ef84c26d33d5f369478bd5060dc97b9dc7fbdd`.
Identical three postimages are integrated and non-force pushed at
`cfdcd8f546d7005b11c7bb1c8635eb3d1ee03339`; the isolated SHA is not claimed
as a separately pushed branch. Its clean checkout is normally retired after
exact integration, removing 536,732,926 logical bytes; thin evidence remains.
Status: reviewed-unverified diagnostic source, not behavior delivery.

No introduced source finding in this bounded diagnostic diff. Root reads the
actual 59-addition/two-deletion Java diff, TASK/HANDOFF and native-facing
production getters. Observations read existing config/runtime/field-index and
loaded/entity-loaded/ticking predicates; none acquires a chunk or changes
production state. A single test-only context has a six-line ceiling and clears
in AfterBatch finally. Original restoration mutations retain their order.

Root independently compares complete calls with the fixed parent: all 32
assertions, three GameTest annotations, nine wait oracles and three config
mutations are byte-equivalent; delay arguments remain1,11,11,11,11,2. The five
literal observation stages and final context clear are checked. Source SHA-256
is `f1ad52c11c8c0d3cc01c303c4c3c2ddfc83af7810626f56cb3a7cc9263e6725d`.
[Fresh independent check](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-native-fixture-corrections-20261006-01/gravity-independent-checks-02.json)
exits zero; scoped `git diff --check` passes.

The first Root check incorrectly compares complete delayed callback bodies,
which necessarily change with diagnostics; that check fails. The successor
checks delay arguments separately while retaining complete assertion/oracle
comparisons. The original tool-history failure is not rewritten. An early
handoff read precedes file creation and fails; the exact final file is later
read in full. The first staging guard has a PowerShell operator-precedence error
and stops before commit; an explicit joined-string comparison verifies the
exact three staged files, then the Root-only isolated commit succeeds.

No local Java compilation, GameTest or native execution occurs. The author
retains its own static-check failures and unchanged source pins. Compilation,
actual unfiltered hosted diagnostic observations, gravity root-cause/fix and
repeat regression remain required. This diagnostic adds no production fix,
fixture readiness manipulation, oracle mask, timeout extension, API or save
writer. Existing failures, R-021, full v1.8 and G0-G9 remain open.
