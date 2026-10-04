# C18a-01 committed regression: four GameTest failures

Date: 2026-10-05. Status: CHANGES_REQUIRED. Integrator: Root.
This complete command cohort, called Source26, tests committed and pushed
`9fce551ea9389ce37a94f804aa0e817399586a7e`. The earlier scoped intermediate
does not override these failures. Full leaf, native/client and ledger acceptance
remain unverified; v1.8 is IN_PROGRESS /IMPLEMENTING and G0-G9 are open.

## Actual commands and results

Java 17, offline/no-daemon Gradle, two workers, 2 GiB heap; process temp settings
use `D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-full-20261005-01/process-temp`.
Fresh C/D space is recorded immediately before each Java command.

| Command | Exit | Actual result |
|---|---:|---|
| `gradlew.bat --offline --no-daemon --max-workers=2 ... clean build test runData` | 0 | 172.1085817 s; 1,807 JUnit /339 suites /0 failures, errors or skips |
| `gradlew.bat --offline --no-daemon --max-workers=2 ... runGameTestServer` | 1 | 235.9449562 s; 477 tests complete, four required failures |
| Separate `runData` with the same Java/temp limits | 0 | 22.3269158 s; 781 files, zero changes |
| Common/client imports, accepted ledger, planning, provenance, machine resources, artifact identity, generated diff, whitespace | 0 each | Eight scoped validators/checks, not all version Gates |
| Content-ledger closure | 1 | Actual open ledger failure preserved |
| Global `git diff --exit-code` | 1 | User-owned AGENTS remains dirty; not waived as clean |

All four required failures are analyzer tests:

1. `unloadedEyeRefusesWithoutCreatingTicketsOrAtmosphereService`: unknown-eye
   observation does not satisfy the no-service assertion.
2. `disabledUnavailableAndInvalidUsesDoNotQueryOrMutate`: native registry is
   already frozen.
3. `incompleteBudgetedRoomReportsPendingWithoutPositiveSupplyOrQueryTick`:
   pending/ambient assertion fails.
4. `suppliedEyeCellKeepsAmbientAndQueriesDoNotSpendVentResources`: supplied
   eye/effective/ambient assertion fails.

These are actual observed assertions, not established production diagnoses.
Repair requires an exact isolated diff, independent review and a fresh committed
cohort. No assertion, scan/time budget or failure is relaxed/reclassified.
The GameTest console has 65 ERROR headers; original latest/debug streams are
also retained. There is no blanket log waiver.

## Source and package boundary

The manifest binds 3,036 named inputs, not the entire repository. It includes
the prior named source paths, the reviewed analyzer files and selected contract/
provenance records. Literal Git comparison qualifies only live user AGENTS and
one LF/CRLF wrapper expansion. Prior guard Python changes are bound to this
commit but no guard native run is newly credited. Postchecks find no named-input
drift or HEAD movement during these commands.

| External artifact | Bytes | SHA-256 |
|---|---:|---|
| Main JAR | 5,509,422 | `ba33be072bc9da427975d52ff351dce0ded9f36d88af7904980885f0280176a8` |
| Sources JAR | 2,652,897 | `6e6b289fc6185cc0a7e90eef1e4f5e90c91a89df7fa77bef3f8592eef05587eb` |
| API JAR | 51,045 | `7484de1d50ffca610328200a838ff47cc8480ea72b37e964e772178325c749da` |

JARs stay externally in the evidence directory's `artifacts-01/`, not in Git.
Actual member/CRC checks confirm no removed or unrelated changed members;
new binary members are scoped to the nine owned main outer classes and their
compiler-generated children. Four generated resources and two language outputs
match actual source outputs. API is byte-exact to the previous artifact.
Automatic packaging does not prove real-client or packaged restart behavior.

## Evidence and cleanup

Raw commands/results/XML/logs/source manifest/package observations are under
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-analyzer-full-20261005-01/`.
The [independent result disposition](RESULT-REVIEW-01.md) confirms these failed
results and exact source/artifact boundaries without Java replay or functional
acceptance. The [failed-cohort packet](failed26-01/FAILED-CHECKS-01.zip) is
1,189,674 bytes, SHA-256
`3d386c5ee99581cee59f36cbfd136ac01595a84f9556c28e10053fe024719606`.
Root verifies its 549 unique members, CRC and all 548 payload hashes. It includes
all 339 XML files, raw four-required-failure logs, publication receipt, independent
audit and actual cleanup failures; large JARs/runtime trees are excluded.
The collector's first CRLF marker observation fails before ZIP output and is
retained; corrected collection and independent packet verification pass.
The [source packet](source01/SOURCE-CHECKS-01.zip) preserves exact author/source
review and the earlier scoped intermediate, not a passing full regression.
It is 846,698 bytes, SHA-256
`d02a46ab162d599219de83531cd2e506d0bd28fc0239fe0ab3de6e87d9589b10`.
Its 167 unique ZIP members/CRC and original 166 payload identities were checked.

Root deletes one newly created D helper bytecode file successfully; no freed-byte
amount is claimed. The author's checked 54,471,846-byte build-directory deletion
is tool-rejected before OS execution. Prior C scripts and native04/native05
cleanup remain unfinished; no alternative-tool bypass is attempted. Sealed
records/source worlds and other owners' files stay unchanged.
Root's separate Source26 runtime-directory cleanup is also tool-rejected before
OS preflight, with no removal or freed-byte claim. Its distinct refusal note is
in the failed-cohort packet. Future helpers and process temp remain D-only.
