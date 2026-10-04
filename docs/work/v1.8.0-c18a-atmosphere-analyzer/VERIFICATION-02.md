# C18a-01 corrected committed regression

Date: 2026-10-05. Status: AUTOMATED_SCOPE_PASSED; native/client/leaf acceptance
pending. Source27 tests committed and pushed
`a0873a30a2e1ad9fe0b42d11a0b89903fc478d5c`. The [Source26 failures](VERIFICATION-01.md)
remain immutable historical results, not waived or overwritten. v1.8 remains
IN_PROGRESS /IMPLEMENTING with G0-G9 open.

## Actual commands and results

Java 17; Gradle offline/no-daemon/**no-build-cache**, two workers, 2 GiB heap.
TEMP/TMP/TMPDIR and java.io.tmpdir use the project-parent D evidence directory's
`process-temp`. Fresh C/D free bytes are recorded immediately before each Java
command and meet the 10,000,000,000-byte floor.

| Command | Exit | Actual result |
|---|---:|---|
| `gradlew.bat --offline --no-daemon --no-build-cache --max-workers=2 ... clean build test runData` | 0 | 194.043484 s; 1,807 JUnit /339 suites /0 failures, errors or skips |
| Same flags, `runGameTestServer` | 0 | 235.4779811 s; 477 complete /477 required passed; no required-failure report |
| Separate `runData`, same flags and temp bounds | 0 | 22.0996142 s; 781 files, zero changes |
| Common/client imports, accepted ledger, planning, provenance, machine resources, artifact identity, generated diff, whitespace | 0 each | Eight scoped checks, not all version Gates |
| Content-ledger closure | 1 | Actual open ledger failure, not waived |
| Global `git diff --exit-code` | 1 | User-owned AGENTS remains dirty, not reclassified clean |

The build's raw `> Task :test` is actual execution, not FROM-CACHE; all 339 XML
files are copied before the separate GameTest run. GameTest console, latest and
debug logs are retained. Its 61 ERROR headers are disclosed without blanket
waiver. The ten analyzer tests, original conditions, 40-tick deadlines and
scanner/query resource limits remain. The [exact fixture-only correction](FIX-REVIEW-DISPOSITION-01.md)
does not change production gameplay/save semantics or admit a new guarded writer.

## Source and package boundary

Source27 binds **3,039 named inputs**, not the whole repository. Against
Source26's 3,036 inputs, only the reviewed analyzer GameTest changes; three
exact FIX records are newly named. Literal Git comparison separately qualifies
live owner-maintained AGENTS and one exact wrapper LF/CRLF expansion. Both full
and postcheck receipts record zero named-input drift and unchanged tested HEAD.
Earlier guard-native results are not rerun on these new artifacts.

| External artifact | Bytes | SHA-256 |
|---|---:|---|
| Main JAR | 5,515,026 | `cb7d3b48148bea78cf5ffb0da18a6bb578db1f6f10040c894e7d9721df976206` |
| Sources JAR | 2,654,938 | `42fbb7cfe207d5b329fcb26366005da0d0b4f5bdb815de541ef16aba119c2634` |
| API JAR | 51,045 | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

Actual member/CRC comparison against Source26 finds no removed or unrelated
changed members/resources. Main changes only the GameTest outer class and
`$NoMenu`, adding `$FixtureItems`; sources changes only that exact Java file.
API is byte-exact. JARs remain externally in `artifacts-01`, not in Git.
Automatic packaging does not prove production-profile exclusion, packaged
analyzer use/restart, real-client display, ledger delivery or whole-version Gates.

## Evidence and remaining work

Raw commands/results/XML/logs/manifests/artifact observations remain under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-fixed-full-20261005-01/`.
[Independent result disposition](RESULT-REVIEW-02.md) records the separate
raw-result/source/artifact audit; no Java/native replay or Gate acceptance is
claimed. The [portable full-check packet](full27-01/FULL-CHECKS-01.zip) is
1,094,430 bytes /493 unique members, SHA-256
`36c58d414bffa80da49a99d0e26e9d0686ee447eb2cd06cb49b1d33cf1f2e6b0`.
Root verifies CRC, all 492 payload hashes and 487 original-input byte identities.
It includes all XML/raw execution streams, audit and source-correction publication
records. The old fix ZIP and its identical binary Git stdout are separately
pinned/excluded, not claimed as complete publication raw-output closure.
The existing [fix-source packet](fix01/FIX-SOURCE-CHECKS-01.zip)
preserves author/source-review checks, not this new full cohort.

All new helpers/process temp stay under the project-parent D evidence directory.
No rejected C scripts, previous native copies or runtime-cleanup target is retried;
their actual cleanup failures and zero-removal claims remain. The mandatory
clean-build result is verification, not a successful direct-cleanup receipt.
After both commands terminate, read-only preflight finds two owned process-temp
files /235,008 bytes. Their distinct LiteralPath removal is tool-rejected before
OS execution: nothing is deleted or freed, and this target is not retried.
Sealed evidence, source worlds and other owners' files are unchanged.
Ledger remains 186 PLANNED /154 REVIEW; R-021 remains open/unaccepted.
