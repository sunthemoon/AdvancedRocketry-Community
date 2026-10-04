# Analyzer GameTest correction: exact-source disposition

Date: 2026-10-05. Integrator: Root. Status: SOURCE_CORRECTION_ADOPTED.
Scope: one GameTest file plus the author's two FIX records; complete committed
Root regression, normal packaged profiles, native/client and leaf acceptance
remain separate. No production query, scanner, budget, API or save-policy change.

## Independent review and authorization

The owner's existing chat authorization is:
“授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认”.
This disposition uses only its independently reviewed, no-unresolved-C/H/M
condition for the exact test-only correction. It does not accept a new ADR,
change gameplay/save semantics, waive a Gate or open a guarded writer.

A different worker reviews actual source and reports no introduced C/H/M/L:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-corrected-review-0da9d89cec/REVIEW-01.md`,
SHA-256 `7b81c11ac68aee4cfe8d12ae8ceb1e25eb24474b6c31eba6636d96b90cc9e187`.
Its manifest SHA is
`01d920335adeb8d34e75ccd0ea5df4862c13181962c2631738066d7d5afd582a`,
binding 79 payloads /5,856,918 bytes. Earlier registry/controlled-coordinate
technical review is proposal eligibility, not a substitute for this actual review.

## Adopted postimages and verification

Fixed execution base is `9fce551ea9389ce37a94f804aa0e817399586a7e`.
Root applies the source patch using apply_patch and verifies all three imported
postimages are byte-exact to the frozen author/reviewer inputs.

| Postimage | Bytes | SHA-256 |
|---|---:|---|
| `AtmosphereAnalyzerGameTests.java` | 39,660 | `275947498de7b42a18f142616258c75400bedd7ed8d0a8f1d5b608d106b229c3` |
| [FIX-PROGRESS-01.md](FIX-PROGRESS-01.md) | 6,900 | `590c1899b56bddbc5a9272d71c2d64234af0e8e059ba7964fc7f29f90d9816d8` |
| [FIX-HANDOFF-01.md](FIX-HANDOFF-01.md) | 7,619 | `596fc290fc8576a1085655f7c773473bdafee88cbbb5a8bb855e3b0268bf0ba8` |

Author's scoped five-class JUnit executes 19 cases /5 suites /0FES, and actual
unfiltered GameTests pass all 477 required tests in 228.8581002 seconds.
The independent combined replay passes all 477 required GameTests in 236.009
seconds, but its scoped `:test` is FROM-CACHE and is not credited as new execution.
A separately authorized `--no-build-cache cleanTest ... test` actually executes
19 cases /5 suites /0FES in 21.400 seconds. Original cached receipt/XML and its
task-status parser error are retained with a separate raw-line qualification.
Both actual jobs pin the same GT bytes and unchanged 2,835 selected execution
inputs, not Root's larger named-input cohort or a whole repository snapshot.
Raw Git comparison qualifies only the declared wrapper CRLF expansion; Root's
live user AGENTS is the active authority, not the older worktree checkout copy.

Ten case identities, templates/batches and 40-tick timeouts remain unchanged.
All 44 original assertion conditions remain literal or reviewed equivalent/
strengthened forms; new checks add actual setup/query/cleanup observations.
The 541-line class was inspected under the 500-line recommendation: it contains
test-only preparation, assertions, registration and cleanup, not a mixed
production service framework, and is below the 800-line ADR threshold.

## Technical boundaries and retained failures

- Fixture Items are constructed only in positively guarded dedicated,
  nonproduction GameTest ITEM RegisterEvent suppliers. Exact test IDs/counter
  lookup/reset remain local to the fixture. They can enter disposable GameTest
  registry/playerdata; normal-profile exclusion is source-backed, not an executed
  packaged-profile or client compatibility proof.
- The unloaded case is an admitted connected player's controlled detached-
  coordinate query. Native onMove remains; copied forced-mark snapshots are
  overflow-bounded at 4,096, with pre-query actual no-load/identity assertions
  and nested coordinate/attachment restoration. Loaded-count/getChunkNow/native
  forced-mark-set equality is not every internal ticket type or natural movement.
- At most four fixture room chunks are prepared before writes; only owned newly
  added force marks are released. Explicit vent producer observations permit the
  unchanged Root manager's ordinary END publication within 40 ticks. Readiness
  and final queries must not scan or debit FE/oxygen; original local-budget,
  registered-runtime and dirty-wall assertions remain.

[Source26 verification](VERIFICATION-01.md) and all three actual failed author
diagnostics remain immutable, not relabelled as passing. Scoped-04 guessed
selectors discover only seven cases; later actual 19-case cohorts remain distinct.
Corrected runs retain 61 ERROR headers without blanket log waiver. Their automatic
success is not a general clean-log, persistence, native, V1/V2 or Gate result.

The [compact correction packet](fix01/FIX-SOURCE-CHECKS-01.zip), with its
[checksum](fix01/FIX-SOURCE-CHECKS-01.zip.sha256), binds author, technical and
independent actual-source records: 219 unique members /3,001,735 bytes, SHA
`db736d3de4286b179ad38a463773310eadfc8eb9e6003f1c6ad10ebac0765cee`.
Root verifies CRC, all member identities and 213 original payload postchecks.
No whole source tree, JAR, world or mutable build output is packed.

Root must commit/non-force push this exact correction before a new complete
clean build/test/DataGen/GameTest cohort. Until that run finishes, Source26 remains
the latest complete Root observation. Native use/restart, normal profiles,
real clients, ledger delivery, R-021 and G0-G9 remain open; v1.8 stays IN_PROGRESS.
No rejected cleanup target is retried or an unowned process terminated.
