# Reserve calculation source17: limited integration

Root decision on 2026-10-08 under the standing technical delegation and
[limited adoption15](ADOPTION-01.md). Accept only the uncalled calculation/test
source described by CONTRACT-01, not equipment gameplay or persistent balance.
Original author source `707c389b` and Root's separate comment-only precision
correction `78347054` are already committed/pushed; handoff/report remain unchanged.

## Exact independent verification

A different reviewer reads the actual 692aa350..78347054 four-file diff,
contracts and adjacent engine/input/decision/tests. No C/H/M/Low source defect
is identified. Source inspection establishes one direct engine invocation;
decision equality does not establish call cardinality. The bounded scalar
transfer, original PENDING/phase/debit semantics and long conservation remain
as adopted; no engine implementation is changed.

Exactly one actual JDK17 Gradle/JUnit command on clean committed `78347054`:
`test --tests '*SuitReserveTransitionTest' --tests '*PlayerLifeSupportEngineTest' --no-daemon --offline`.
Exit 0; two fresh XML suites, 21 testcase nodes (16 new/five existing), zero F/E/S.
Main/test compilation and :test execute; six API compilation tasks are cached.
The log preserves 27 main/69 test warnings and two instrument failures, neither
a test/JVM failure or hidden retry.

Independent sealed report:
`D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18b-reserve17-source-independent-20261008-01/reviewer-01/REPORT-01.md`,
SHA-256 `ec0a63a0d6e37a60f83cac79dd4432701dc0c859aed86af2e29a035765ae5e83`;
manifest `fcb7080d0134c14ea245dd071558677fca8e477545965ce75685d4c3a3fedd5f`.
Thirteen payloads total 110,804 bytes. Utility: 73 lines/3,114 bytes, SHA-256
`7a7f47848d734362bc9b32d716c1e7cafe4e9aead2ff5bdd47e67b713e810b46`;
test: 445 lines/20,990 bytes, SHA-256
`d5c444ea5d5876b036ec55db9e2c6d7969a37f6a439357bdcf8953c563263fdd`.

## Integration and separate regression result

Root merges the exact reviewed source and normally pushes main at
`eb90c6436689e68bc03affc275094053369bf3ef`. No registry, caller, configuration,
schema, native adapter or save change is included. Existing working API/HUD
stays 2,000. Eligibility is a mathematical input, not installed item/player
authority or proof of joint oxygen publication.

The separate [combined candidate regression17](../v1.8.0-claude-cli-coordination/REGRESSION-17.md)
at e91ddc16 actually passes clean build/2,179 JUnit/twice DataGen/clean diffs,
but fails one required airlock GameTest out of 542 completed. It contains this
same runtime source plus the unintegrated graph test-side candidate. Runtime
sources and named Gradle inputs equal main eb90c643; complete test trees do not.
Neither result is rebound to a new commit or called a whole-version pass.

The reviewer retires 62,445,360 own output bytes after natural exit and releases
all interests. Root normally retires that clean source-only reviewer tree after
containment/reparse/absence checks; no extra output bytes are claimed. Receipt
is RESERVE-REVIEW-COPY-RETIREMENT17.json in the external task-input directory.
This closes only pure A0 source implementation/review, not a content-ledger unit.
Tank/config/schema/install/charge, native publication/restart/V1/V2, airlock
regression, R-021 and all Required Gates remain open. v1.8 stays IN_PROGRESS.
