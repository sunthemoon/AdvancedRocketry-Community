# V180-FUEL-DROP-02 source qualification and integration

Date: 2026-10-10. Result: source-qualified-and-integrated, not full-version acceptance.

## Exact source and decision

Root authored candidate `30ca28efd3b1ededb28226a5576a8d21fbc5d8e2` on
`fix/v1.8.0-fuel-loader-readiness`, based on `6daca57def98225bbbb0d4be8bd713bf232053c0`.
Only EntitySections.java and FuelLoaderPlacementGameTests.java changed: 35
insertions and 20 deletions. Main entry for integration was
`823e182a12f8c8741de7e35ad9fa5adf7bccd34c`; its intervening changes are records.
Normal merge `ba68099617027560db132596883a0c1186add4e9` and the candidate branch
were pushed without force. Full src, Gradle and Wrapper inputs equal the actual
tested candidate. A merge-SHA local rerun and documentation-sensitive Python
qualification are not claimed.

Five native drop/place tests now use the existing entity-section readiness
sequence, with an opt-in GameTestTickPacer. World loader creation and input load
occur after readiness; detached item/NBT fixtures may be constructed before it.
Existing unpaced callers retain their behavior. All 15 placement
assertions, all six 20-tick deadlines, native destruction/query/placement, carried
count, owner/item/root/buffer assertions and the oversized refusal case remain.
No production, chunk ticket, schema, registry, network or asset change occurred.

Native queries skip inaccessible entity sections, but chunk-loaded status is not
a universal equivalence to accessible/ticking sections. The historical failure
did not record actual count or readiness. This change supplies a fixture
precondition; it does not prove that omission was the unique historical cause.
Absolute unfiltered nearby-item count still lacks a post-readiness pre-operation
baseline. No extra entity was filtered or removed to make an assertion pass.

## Independent source review

New Codex worker `/root/fuel_loader_source_review` used a separate, non-resumed,
read-only task. No Claude was dispatched. Its [report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-source-review-20261010/REPORT.md)
is 13995 bytes, SHA-256
`f7d03d33b77f72ccf70b13fcf876a3fdc87c9b3bfb54e3355e6c4076fc5a7fc9`.
It requested no source revision, conditional on Root's actual native regression.
Ten finite private scenarios and 22 assertions passed with JDK17, the actual
helper/pacer code and native GameTestSequence bytecode plus isolated stubs.
Readiness, old unpaced behavior, unavailable/deadline and assertion failures,
multi-chunk/negative positions, off-thread refusal and same-tick pacing were
covered. These checks are not an independent native loader/world execution.
Source/command cutoff was `2026-10-10T14:01:07.974787+00:00`, before Main moved.

The separate [investigation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-independent-20261010/REPORT.md)
retains the count-attribution limitation and establishes no production loss or
duplication. Its read cutoff was `2026-10-10T13:47:27.9034782Z`.

## Actual original commands and results

Checkout: `D:/GitHub/arce-v180-fuel-loader-readiness-20261010`, clean committed
candidate above. JDK `C:/Program Files/Java/jdk-17.0.7`; both disks exceeded 10 GiB
before each JVM target. The fresh launcher assigned a private Windows Job before
resuming each process, retained both complete streams and kept original deadlines.

| Command | Original exit | Original seconds | Actual result |
| --- | --- | --- | --- |
| `gradlew.bat clean build --no-build-cache --no-daemon --stacktrace` | 0 | 181.278 | 381 XML, 2192 tests, zero failures/errors/skips |
| `gradlew.bat test --rerun-tasks --no-build-cache --no-daemon --stacktrace` | 0 | 183.080 | 381 XML, 2192 tests, zero failures/errors/skips |
| `gradlew.bat runData --no-daemon --stacktrace` | 0 | 27.221 | 846 generated resource files, 323017 bytes |
| Same DataGen command again | 0 | 21.666 | Full bounded inventory and bytes equal |
| `gradlew.bat runGameTestServer --no-daemon --stacktrace` | 0 | 228.888 | All 597 required GameTests passed |
| Before/after every target: `git rev-parse HEAD HEAD:src HEAD^{tree}`, `git status --porcelain=v1 -uall`, `git diff --exit-code` | all 0 | individually recorded | Empty status/diff, unchanged input identities |

Thirty-five successful original target/metadata receipts are retained. Native
terminal was at 22:04:42.567 local time; completion observation was
`2026-10-10T14:04:46.617866+00:00`. The complete latest log is 1855003 bytes,
SHA-256 `c21bac84b20012656794440542ce798ba06e4690fc419336295274b1c6427d12`.
Its 62 ERROR, 161 WARN and zero FATAL headers remain unwaived. Passing test
summary is not error-free execution or release approval.

The initial CMD spelling caused exit 1 before JVM startup; the original helper,
receipt and streams remain. The separate successor only normalized the image
argument and used new attempt02 filenames under the same aggregate cap. The
first inert audit exited 1 on an incorrect literal for the terminal spelling;
its successor recognized actual `597 GAME TESTS COMPLETE` without changing the
count or assertions. The first integration helper exited 1 at Python parse time
before any Git command; its original file and separate correction remain. None
of these administrative failures was hidden or counted as a successful target.

## Artifact and custody evidence

Runtime JAR: 6086621 bytes, SHA-256
`e937aef4f05ec6ce283a0ea03730548d7cf4eccb59350649848e0139b3170a6d`.
API JAR: 51052 bytes, SHA-256
`aa9317c6d2a5dfc71f736f7a4de4dca15f6742d6649dd05fa5b3f457fe9ade4b`.
Sources JAR: 2896821 bytes, SHA-256
`97d65a69d3862b39bc542e98b7a8ba3226b11eb0f16a5775f108d96e2c739b79`.
Bounded bytewise runtime-entry comparison against the pinned prior pacing JAR
finds only EntitySections.class and FuelLoaderPlacementGameTests.class changed.
ZIP metadata is not used to infer payload equality. No generated source/assets
changed; no JAR or reproducible source archive is copied into this evidence.

Main's inherited 185 full status rows and protected AGENTS hash were preserved;
only two intended source files were staged for the normal merge. Deferred
Task172/source and the mixed modified implementation log were not staged.
New owned outputs `build`, `.gradle`, `run-data` and the generated `.cache` were
retired only after logs/results/hash capture. The cumulative 50000-entry bound
was frozen before JVM targets; 6383 ordinary entries totaling 271363569 file
bytes were removed after two equal complete inventories and identity checks.
This does not enlarge or retry the older pacing cleanup rejection. Its outputs,
Claude's retained client world, older worlds and sealed evidence remain untouched.
New external runtime leaves remain retained within their assigned scopes.

## Independent integration-account audit

New read-only worker `/root/fuel_loader_integration_review` independently checked
35 original receipts and complete streams, the native terminal/log hash and
headers, all 846 actual generated payload hashes, candidate/merge parents and
input equality, status conservation, original failures and admitted cleanup
scope. Its [report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-integration-review-20261010/REPORT.md)
has SHA-256 `2f207031b4a7ec7160b001513b9961e3ac5108d94b835c7d6d100a2dd043b89a`.
It found no actionable inconsistency in the four frozen proposed records and
recommended committing them with limitations preserved. Its final read/command
cutoff was `2026-10-10T14:22:45.600064+00:00`, before record commits.

The auditor could recompute unit counts only from retained per-file manifest
rows, not retired XML; it did not reproduce the JAR-byte audit or historical
deleted-item inventories. No new native execution, protected AGENTS content
read, future packet-seal approval or Gate approval is claimed. The terminal
hosted observation below, detached-fixture wording clarification and this audit
reference were added by Root after that cutoff; they are not represented as
independently audited final record bytes.

## Hosted observations and remaining scope

Earlier pacing run [38056013604](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38056013604)
at exact `30e89b0a1eb9725debc2ee43eaff761d0f4bc6ce` completed successfully at
13:38:52Z. New loader run [38058625712](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/38058625712)
at exact merge `ba68099617027560db132596883a0c1186add4e9` completed/success at
`2026-10-10T14:23:12Z`; job 114232132145 ended successfully at 14:23:11Z. All named
build, artifact audit, DataGen and complete Forge GameTest steps succeeded.
Fresh bounded official REST run/jobs observations at 14:23:29–30Z supersede the
retained 14:12 and 14:20 in-progress observations. They do not supply raw Linux
per-test counts or the raw native log. Terminal run response is 14006 bytes,
SHA-256 `5681373f6aac47eb4fd8eec54d3d463c91a698ca8c70ebf5ab0ac2f30b27e9c6`;
jobs response is 4084 bytes, SHA-256
`16f885d44c6cf88eeb6ba696e0596a3a677040b97c3af8e7277ddfec1a1d0460`.
The web reader still showed cached in-progress HTML and could not open the API;
the fresh task-owned unauthenticated REST observations returned 200 and retain
their exact bytes, times, identities and hashes. No credential was requested.

Root [evidence leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/fuel-loader-root-20261010)
contains TASK, original/successor helpers, corrections, attempt02 raw streams
and receipts, complete generated/XML inventories, RESULT-AUDIT.json, native log,
OUTPUT-RETIREMENT.json, exact CI metadata and integration command receipts.
The leaf is not declared sealed until its final checksum manifest exists.

Not completed: historical causal attribution, post-readiness entity baseline,
repeated hosted cohorts, packaged S1/S2, save/restart, player V1/V2 and whole
Python/strict qualification. Existing error, strict/Markdown, resource and
operational debts stay open. No G0–G9, content ledger, ADR acceptance, release
tag or full-version approval is granted. v1.8 remains IN_PROGRESS/IMPLEMENTING.
Continue this v1.8 hosted regression and remaining C16–C19 requirements.
