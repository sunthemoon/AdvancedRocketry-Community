# Missing-rocket diagnostic: committed hosted regression

Date: 2026-10-06. Tested source `80463e28e27c16fda70f4aaf9954f986401247b8`,
[run 37351633781](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37351633781),
attempt 1. **FAILED; no ledger, release or Gate acceptance.** This replaces
RESULT-04 only as the latest tested cohort; that cohort's failures stay recorded.

## Actual results

Fresh hosted `clean build` succeeds in 4m 19s: 1,861 JUnit /342 XML suites,
zero failures, errors or skips. First `runData` writes 788 outputs in 27s;
repeat writes zero in 19s. Both tracked diffs are empty and both tracked/untracked
cleanliness checks pass. Hosted Python reports 17 passing host tests.

Unfiltered `runGameTestServer` completes 483 tests with four required failures,
then JVM exit 4 and Gradle failure in 5m 9s:

- Laser target: Registered before any save.
- Earth-Mars-Venus-Earth: Planetary flight did not land at the requested body.
- Tau Ceti f: No rocket at Tau Ceti f.
- Destination readiness: Ticketed destination did not become ready.

The console has 65 ERROR headers without blanket waiver. No gravity failure
header appears; finite diagnostics again distinguish the disabled phase label
from the actual tick-50 disabled values. The historical gravity issue remains open.

The single Tau diagnostic at test tick 743 observes PREPARED, source TRANSIT
and not removed, unassigned destination entity UUID, block chunk loaded but
entities not loaded and position not entity-ticking. Expected-world and record
creation times differ by 270. This is an unready pre-authority observation,
not a unique cause. The existing fixture lookup has already primed pad chunks;
the observation is not an untouched cold-load measurement. Original flight
delays, deadlines and assertions are unchanged.

## Retained evidence

The [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-80463-observation-20261006-c18-8a60f3/REPORT-01.md)
is 6,409 bytes, SHA-256
`3f92eaeb66d54800bea352160fbf523dc03c5b4ec4b1bbaefdf15c841bff1347`.
The unchanged bounded collector verifies exact source/run/attempt, archive
digest and CRC, then retains 367 mapped streams /7,358,410 bytes. Artifact
11363616391 is 1,713,451 compressed bytes, SHA-256
`7c5cfbd0d22c3ca5e0c0c5cd9c86e412a751c53df62f19e36ac76e934c4a5507`.
[Retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-80463-attempt1-20261006-c18-01/RETRIEVAL-01.json),
SHA-256 `4dc52a1ae38efcf98ef44f73292fdc5873b872d36192b8fdd4bf40105ff5b7a6`,
maps the literal finite streams; the collector independently rehashes them and
parses every retained JUnit XML. The [different-agent retained-stream review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-80463-result-review-20261006-6a193f/REVIEW-01.md),
SHA-256 `82a906a6f5058b2d4b8f303042617a332e3af7ce80b02615f762021b8d16c84e`,
independently reproduces all hashes/counts, three native failure sets/status bars,
DataGen facts and the lookup-qualified Tau observation. Its two failed reviewer
parser controls are retained; separate final checks succeed. No independent
archive/API/container replay is claimed. Conditional JAR upload is skipped;
build-side JAR SHA `c9187a2bd6c6c9a90a28943fe70adf3b52250f4e528b2066aaead8fae501c0d6`
is a recorded audit result, not a retrieved container identity.

## Subsequent source checkpoints, not results of this run

The independently reviewed Laser fixture correction is separately published
at `86a819ca564c43206c6b9272c0b79076fceaa874`. It correlates registration with
the exact serialized Save rather than assuming 25 ticks imply no native save.
The same-call negative control and original downstream assertions/25/300 limits
remain. Its [source review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/laser-target-save-source-review-20261006-9d572a/REVIEW-01.md)
finds no introduced C/H/M/L; the new hosted replay is pending.

Root's separate targeted `javac -proc:none --release 17` compiles the exact Tau
and Laser postimages against 117 existing cached classpath dependencies, exit 0.
[Command/result](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-test-compile-20261006-01/RESULT-01.json)
explicitly records that this is not a clean build or GameTest execution. The four
temporary class outputs are removed; logs/argv/source hashes remain. Root's two
new clean integrated Tau/Laser worktrees are normally retired after exact main
postimage checks. New scratch stays in the D project parent; old C cleanup debt
is untouched. No local heavy execution occurs below the 10 GB threshold.

Solar source authoring is assigned only after its reviewed task and NEW origin
declaration are published at `a2d23d1f2fbd17af13c9e1f7097d8ca2e11b79bd`.
No solar implementation, native restart, real GPU/multiplayer, R-021 closure or
G0-G9 acceptance is established by this cohort or these source checkpoints.
