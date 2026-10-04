# C18a-01 analyzer GameTest correction handoff

Status: REVIEW_READY author correction; v1.8 remains IN_PROGRESS. This is not
Root integrated delivery, independent actual-source acceptance or a version Gate.

## Exact scope and source

Fixed base: `9fce551ea9389ce37a94f804aa0e817399586a7e`.
Worktree: `D:/GitHub/arce-v180-c18a-analyzer-fix-20261005`.
Branch: `fix/v1.8.0-analyzer-native-tests` (unchanged; no Git mutation).

Owned repository postimages are exactly:

1. `src/main/java/io/github/sunthemoon/advancedrocketrycommunity/gametest/AtmosphereAnalyzerGameTests.java`;
2. this `FIX-HANDOFF-01.md`;
3. [FIX-PROGRESS-01.md](FIX-PROGRESS-01.md).

GT baseline: 29,847 B / SHA-256
`54e6ff5aae95f760260229fcbe2b8c830336726cea1ed75528fd23e87909ce21`.
Final: 39,660 B / 541 lines / SHA-256
`275947498de7b42a18f142616258c75400bedd7ed8d0a8f1d5b608d106b229c3`.
Source-only `SOURCE-FIX-01.patch`: 25,184 B / SHA-256
`87a711965b8d77a3722859584f1678b269cbaf6b0c945d64b4580afdfa6f8d3e`.
Both pre/postimages are LF; no normalization waiver is needed. Read-only forward
`git apply --check` on the individually verified exact Root baseline exits 0.
No actual Root application, index operation, commit or push is performed.

## Corrections and limits

* Disabled/unavailable fixtures are now constructed only in RegisterEvent
  suppliers under dedicated-dist, non-production dedicated-GameTest and ITEMS
  guards. Exact test IDs are `gametest_analyzer_disabled` and
  `gametest_analyzer_unavailable`; callbacks return unavailable and share a
  test-only counter, reset before/finally. Actual registry lookups have no
  fallback. Every old injected count/cooldown/feedback/invalid-identity assertion
  remains. Entries can exist in disposable GameTest registry/playerdata; normal
  profile exclusion is primary/source-backed, not a newly executed normal host.
* The old unloaded setup itself loaded its far chunk through attached native
  Entity.setPosRaw. A controlled detached-coordinate setup keeps native onMove,
  asserts actual connected identity before/after detachment, checks null target /
  loaded count / independent bounded forced-set equality before the query, and
  preserves the original negative-query/no-service/no-load assertions. Coordinates
  and attachment restore in nested finally before PlayerList removal. This does
  not prove ordinary client movement, actual transfer, reconnect or native restart.
* Room shells obtain FULL chunks before writes and retain only newly added
  fixture force marks. Explicit fixture producer observation continues while the
  unchanged installed Root manager publishes on ordinary ServerTick END. Readiness
  queries themselves must not debit FE/oxygen or increase scan inspections. Only
  after actual Root PENDING/supplied publication does the local manager receive
  its single explicit budget/supply tick, followed by all original comparisons.
  Failure/cleanup occurs by tick 39 within the original 40-tick deadline; no
  isolated manager substitutes for Root authority. Cleanup precedes success and
  handles failed setup/assertions. Missing publication remains a failure.

No production scanner/query/lifecycle/config/registry/main/bootstrap/language/
generated/provenance/API/schema or persistent world format changes. No central
integration is needed for this source correction. Normal profile fixture IDs,
new crafting/assets and public gameplay API are not introduced. The 541-line
class is reviewed as ten fixtures plus test-only registration/preparation/
readiness/assertion/cleanup, not a mixed production domain framework; below 800.

## Actual author verification

All Java commands use Java 17.0.7, offline/no-daemon/max-workers=2/2-GiB heap.
Fresh C/D checks meet 10,000,000,000 B before each launch; TEMP/TMP/TMPDIR and
Java temp point to the own D evidence leaf. Raw commands/logs/results are retained.

| Cohort | Actual result | Meaning |
| --- | --- | --- |
| Original Root Source26 | 477 GT / 4 required failures | Immutable inherited failure, not overwritten |
| Diagnostic-01 | exit 1 / 260.5826575 s / 477 GT / same 4 failures | Bounded messages only; setup far becomes FULL, frozen item construction, missing local vent scan |
| Diagnostic-02 | exit 1 / 233.4545634 s / 477 GT / same 4 failures | Room local and same-BE prerequisites pass; Root-runtime equality still fails |
| Diagnostic-03 | exit 1 / 235.0911445 s / 477 GT / same 4 failures | Actual Root ambient matches; no active scan/effective publication in failed observations |
| Scoped-04 | exit 0 / 35.3632503 s / 7 tests / 2 suites / 0 FES | Three guessed selectors do not match; not the 19-test scope |
| Scoped-05 | exit 0 / 20.9954562 s / 19 tests / 5 suites / 0 FES | Five actual Reading, Feedback, Service, Data and Language classes |
| Full-06 | exit 0 / 228.8581002 s / all 477 required GT pass | Unfiltered complete suite, ten analyzer cases included |

Scoped-05 and Full-06 bind the same final GT bytes above. Full-06 records actual
Root PENDING and supplied readiness at tick 1. Final latest.log still contains
61 ERROR headers from the full suite; no blanket log waiver or general clean-log
claim is made. They remain raw evidence and do not become analyzer test failures
or whole-Gate acceptance by an author summary.

Static checks retain all ten names/templates/batches/40-tick annotations and 44
original assertTrue first arguments (captured reading aliases only; one clause
strengthened). Final has 52 assertions; 14 source controls pass. Every command's
2,835 selected build/source inputs have zero in-command drift; 2,834 non-GT
inputs also match initial capture. This is not a full-repository/docs identity
claim. Existing original unit tests are unchanged and none are removed/relaxed.

The input manifest records actual worktree checkout bytes selected by
`git ls-files src build.gradle settings.gradle gradle.properties gradlew
gradlew.bat gradle`, not 3,036 Root inputs or all fixed-commit raw Git blobs.
`gradlew.bat` retains the same observed checkout bytes from Diagnostic-01 through
Full-06; its Windows checkout line endings differ from the raw fixed-base Git
blob. The separately captured exact EOL receipt qualifies only this wrapper,
not a general normalization allowance or an author wrapper change.

## Evidence and transfer

Loose evidence (no whole-source/class/JAR/runtime archive):
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-fix-author-ea8349712b`.
Includes exact final/baseline GT, patch, owned-file/source-input manifests, raw
four full-GT command/latest/debug logs, scoped XML/results, source controls,
selected primary facts and proposals/tool observations. Original Root failure
receipts are pinned individually, not copied as a large archive. Worktree build
and process temp are explicitly excluded from sealed evidence and remain
disposable execution outputs. No deletion/previously rejected cleanup is tried.

Java sessions diagnostic-01/02/03, scoped-04/05 and Full-06 are all terminal.
After Full-06: C 10,234,556,416 B / D 337,760,223,232 B. Author Java slot is
released; after the evidence manifest seals, Root may transfer this worktree's
ordinary disposable build/GameTest outputs to the independent reviewer. Author
will not edit these three source/docs postimages or execute another job meanwhile.

Unrun: independent reviewer replay, Root clean build/DataGen/new integrated GT,
native real-server analyzer item use/persistence/restart, normal-profile launches,
V1/V2 clients and all remaining v1.8 Required Gates. Original product semantics,
performance limits and unrelated R-021/native/other leaf obligations stay open.
