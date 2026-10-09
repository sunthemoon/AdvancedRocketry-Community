# Diagnostics08 source integration and qualification checkpoint

Date: 2026-10-09. Status: IN_PROGRESS. The bounded diagnostic source is committed
and normally pushed; complete Python/strict qualification and all G0-G9 remain
open. This is current v1.8 tooling, not product delivery or release approval.

## Completed scope and design

[Task08](DIAGNOSTICS-TASK-08.md) implements only two Python files in Root's
exclusive fix/v1.8.0-strict-phase-diagnostics worktree. Actual source commit is
945e07c6bc8b69e51f4819235e70577f217226f0, direct parent
b58d4b8bb25eff5a41d4d82362ee4667c06ae6f6. Main integrates the exact postimages at
af363704b712c2bd0791f05d71a541bd8d904be5, direct parent
fe82f243f596a469596fbf58b525a15243059e4f. Staged scope/stat/whitespace and user
AGENTS hash are checked before commit; source and Main are normally pushed.

The Results phase context manager accepts bounded static ASCII check names,
emits flushed stderr entry before execution, and on normal return emits monotonic
duration and cumulative counts. It exposes no paths or arbitrary result messages,
does not invent PASS, catch an exception or print a fabricated final verdict.
All original 33 unconditional main checks and the optional final package check
keep their order, arguments, conditions and predicates. Final grouped stdout
text/list ordering/exit behavior is retained, with immediate flushing added.
No Java/API, resource, sleep behavior, dependency, CLI flag, optimization,
allowlist or deadline change is made. Nested Git containment is outside this scope.

Root and fresh independent08 AST reviews preserve 93 non-main functions,
original main after only matched wrapper unwrapping, result-recording methods
and report text after only flush additions. All 128 original test methods remain,
with one strengthened CLI empty-stderr assertion; twelve methods are added.
The new methods cover exact report order/flush/list retention, bounded phase
shape/counts/timing, normal/no-op behavior, unchanged exception identity,
literal 33-check orchestration, optional package arguments, recorded-failure
continuation and existing exit rules. CLI help remains isolated and silent.

## Actual Python results and remaining failures

Root uses local Python 3.13.15 with -X utf8 -B. CI Python 3.12 is not executed.
Exact argv/cwd/UTC/source/input/stream observations are retained in Root08 receipts.

| Actual cohort | Result |
| --- | --- |
| Root parent baseline | 128 methods; exit1; one live resource-inventory assertion failure |
| Root diagnostic/report/orchestration/CLI | 14 methods PASS; exit0 |
| Root full repository file at945 | 140 methods; exit1; same one resource assertion failure |
| Root broader Python suite | 180.321 seconds TIMEOUT; no final summary |
| Root strict --require-approved-identity | 180.304 seconds TIMEOUT; no final summary |
| Fresh independent08 targeted | 14 methods PASS; own source audit also exits0 |
| Fresh independent08 full repository | 140 methods; exit1; same one resource assertion failure |
| Fresh independent08 strict / adjacent suite | 180.324 /180.270 seconds TIMEOUT; no final summaries |

Independent08 also runs a distinct six-method orchestration cohort, not a retry.
Root fully reads its stable report, acknowledges the exact SHA before sealing,
and independently verifies six raw receipts and all 26 payload hashes. No change-
specific defect is established within its finite review, not whole-suite acceptance.

Additive correction to sealed Root08 REPORT.md lines39-40: its phrase "plus two
retained CLI methods" is inaccurate. The actual fourteen methods comprise twelve
new methods, one retained CLI help method and one retained ResultsReport pending
method, as shown by both raw targeted streams. Independent integration10 reports
this Low documentation finding. Test counts/exits remain unchanged; the sealed
report, receipts and manifest are not edited. This new checkpoint supersedes
only that method-classification wording, not qualification failures or evidence.

Root's early baseline/candidate wrappers return 0 without propagating their child
code. Their receipts retain child 1/failures/timeout; wrapper 0 is not PASS. The
distinct committed strict wrapper pins the unchanged helper and propagates 124
classification. Original wrappers, raw observations and failures are preserved.
Copied helper schema/scratch exclusion are not offline JSON resource certification.

Root strict actually emits 13 entries/12 normal returns. Its terminal line is
`[PHASE] check_v002_g4_applicability begin`, without return. Bootstrap/final G0/
optional-client normal returns take 26.545/30.580/86.354 seconds. Markdown-link
failure count is already 1, but no final grouped detail is printed. Independent
strict also ends at that main-phase boundary. This does not attribute an internal
subphase/descendant or reconstruct the cause of historical03/04/05 timeouts.

Root suite taskkill returns 0 for named primary/child. Root strict taskkill 128
reports primary 16808/child 6368 successful but child 18388/grandchild 24868
unsupported. Independent strict taskkill 0 and adjacent taskkill 128 remain
distinct; the adjacent descendant failures are not repaired by another cohort.
No retry or historical whole-tree termination proof is claimed.

The read-only inventory09 worker verifies the failing twelve paths are tracked
later resources: two v110 language JSON, six machine patterns and four travel
routes. The helper's v0.0.2/v0.1.0 prefixes and production v0.0.2-only dispatch
are compared against the test's unconditional whole-live-tree scan. The unchanged
parent/candidate src tree and identical failure support an applicability mismatch,
not invalid content or missing provenance. A separately assigned corrective
test slice must preserve undeclared-resource negative coverage and not broaden
the historical allowlist. No such corrective code is implemented here.

## Actual standard subset09

[Task09](STANDARD-QUALIFICATION-TASK-09.md) separately qualifies exact945 without
repeating strict. Forced clean build and forced separate test each execute 2192
actual JUnit/381 XML/0 failures/errors/skips. Two forced runData commands and
empty git diff --exit-code checks pass. Unfiltered runGameTestServer exits 0:
all 597 required tests pass in 151 batches. Accepted ledger, approved historical
bootstrap provenance and git diff --check pass. All 13 command classifications
are zero; this subset does not repair the failing Python or strict qualification.
Each native debug/latest log retains 62 ERROR/0 FATAL, with no waiver.

Root audit recomputes all raw stream/copy hashes, both XML cohorts, unchanged
source/index/twelve-input states and actual artifact hashes before cleanup.
Its alternate completion-text selector is empty; raw required-pass lines supply
the stated native result. No native XML, consumer rerun, JAR/source/world copy,
resource/memory probe, durable-writer/restart or V1/V2 proof is produced.
Actual Java execution remains at 945, not at Main af363704.

Complete src tree is a7b7d83a67fb02403f5aaee9c2d21987a06b1f6a. The main/sources/API
JAR hashes are respectively e965c5fba3d33cdffd6944aec31014470211eabcbd52e14d45aeea7927ff002d,
b4f5f4db27377ab3ef8fb5d85536064dd0c703c046440fb4503b064341300ef1 and
aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b.
The two source postimages are bound by validator SHA
2fd4dca13a7ef377b2ac6cd17487ce894527224169f2628c00b6950633ec873e and test SHA
20a0fdc71625b38d23f7bb93b6c76aa72689554b745c45941a4514d4e5800c56.
Whole repositories contain differing documentation; strict-validation execution
at 945 is not Main-SHA validation equivalence. Fresh fixed-object independent
[integration review10](INTEGRATION-REVIEW-TASK-10.md) completes the actual diff,
AST and attribution audit, finding only the Low caption error corrected above.
It independently verifies 24 receipts/48 raw streams, 764 copied results and
all three complete manifests. Twelve named source/integration Git inputs agree;
gradlew.bat's CRLF checkout hash is explicitly distinguished from its LF Git blob.
src/scripts/tests/compat-test-mod/gradle trees and top-level build objects match;
only three documentation files differ at the two compared source commits.
Its report is returned through the task mailbox on 2026-10-09, not a new sealed
file. It performs read-only parsing, not tests, retries, source writes or process
control. A corrected stdin-parser typo is a separate read-only analysis error.
Artifact files are already removed, so the reviewer reads retained identities
rather than rehashing JARs; only Root's earlier actual rehash is claimed.

## Evidence custody and cleanup

Evidence leaves are external retained logs/results/helpers, not duplicate source
archives. Root verifies each final manifest against every covered file. Counts
exclude the manifest in payload and include it in whole-leaf totals:

| Leaf beneath D:/GitHub/ARCE-Task-Evidence/v1.8.0 | Payload files/bytes | Whole files/bytes | SHA256SUMS.txt SHA-256 |
| --- | --- | --- | --- |
| strict-phase-diagnostics-root-20261009-08 | 22 /137415 | 23 /139436 | 03a0ce2b969b653bbf82e1962b8570933c2ce57a2fdcbaebad06e808581bcf0f |
| strict-phase-diagnostics-independent-20261009-08 | 26 /185379 | 27 /187805 | 46cbf5f58f0080996e00bb7de8dd58d91b218180be6659b53c25da0ef5ab16b9 |
| strict-phase-standard-root-20261009-09 | 823 /9438761 | 824 /9600698 | d4c3a1bcf37e7b2eba7a0deb62927be54520a511ef7474fafc6a9989358a3923 |

Root08/independent08 whole leaves are below 4 MiB; Root09 below 100 MiB, each file
below 50 MiB. These final retained sizes do not prove continuous temporary-storage
quota or offline resource admission. No sealed historical packet is changed.
Report SHA-256 values are Root08
3b41e239e41381c5200e012c92540c6b31d0b69fbfa0390a8b24b58c834b33bc,
independent08 bf5e9a153b1ab7b7f1afd16155cdcae10fe662d3c63360b64e2b28e77b1b086e,
Root09 2d7faec5c473a771b0eb338e11de870e13a12cf1ef3a15a5921a7e5c02ebaf6d.

Both volumes exceed 10 GiB before launches. After all 18 Root08/09 receipt commands
end, one guarded native PowerShell cleanup exits 0. Four checkout logs, 904453 bytes,
are copied/hash-verified; actual v1.8 cache is catalogued. Eight exact owned
targets, 5864 files/273731376 logical bytes, are removed in 2.3777532 seconds;
absent tests cache is not removed. Source/index stay clean. Root08 script caches
are attributed to its exclusive CLI run, with explicit09 initial inventory, not
a fabricated earlier per-directory receipt. Named PIDs are absent at the later
15:48:24.1655291Z observation only. No retry or historical process-tree proof.
Independent08 separately retires its own scratch/four caches; its partial
adjacent termination remains disclosed. Peer/inherited outputs, global caches,
source checkouts, user work and every earlier refused target are untouched.

## Unfinished scope and next current-version work

C19 overall stays open because full Python/strict do not pass. The finite
inventory applicability correction and historical snapshot/Git containment
review are separate current-v1.8 candidates; checks and the 180-second budget
must remain intact. No true sleep, resource-helper/case execution, hatch/shared
save, content, restart/client/soak or Gate prerequisite is completed here.
v1.8 remains IN_PROGRESS /IMPLEMENTING, 186 PLANNED /154 REVIEW; no Required Gate,
ADR acceptance, ledger delivery, tag or release verdict is generated.
