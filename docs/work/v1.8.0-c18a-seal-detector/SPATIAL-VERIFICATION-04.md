# Selected-cell native qualification04 checkpoint

Date: 2026-10-09. **CANDIDATE IN PROGRESS; FULL REGRESSION FAILED.**
Fixed successor 770b3134cae99ab603ccde610c3731a98d63f524 is committed/pushed in
its isolated task branch, not integrated into Main. Its parent is fd585a98;
the complete src tree is 99675f8801c669f67380d830bce9e8c9ad2b75e9.
Only SealDetectorAdmissionGameTests.java changes: three native cases and the
loaded-overreach discriminator, class 480 lines. Production and budgets are unchanged.

## Source, review and actual Root checks

[SPATIAL-TASK-04](SPATIAL-TASK-04.md) and its corrective scope were committed
before their respective source edits. Original fd's forced build passes 2192
JUnit/381 XML. Independent Codex review04 identifies Low R04-01 in the original
unloaded-target test: chunk refusal could mask an omitted target-AABB check.
The successor retains that observation and adds a loaded overreach target with
explicit eye/hit/distance/loaded/permission preconditions. Independent static
review regards that discriminator as source-addressing R04-01; its own full
commands and stable final report remain pending at this checkpoint.

Root evidence is the [external qualification leaf](D:/GitHub/ARCE-Task-Evidence/v1.8.0/seal-spatial-boundary-root-20261009-04).
Receipts bind fixed source/tree, ten build inputs, Java17, exact argv/cwd,
UTC/exit/raw hashes, clean tracked/index observations and disk space. Commands
use no-daemon/offline/no build-cache, with forced build/test/DataGen execution.

| Actual receipt | Result |
| --- | --- |
| build-02 | exit 1 in 0.109 s, cmd syntax error; no Gradle output, not a build result |
| build-03 | exit 0 in 212.558 s; 20 tasks executed; 2192 actual JUnit/381 XML/0 failures/errors/skips |
| test-01 | exit 0 in 212.113 s; 17 tasks executed; fresh 2192 actual JUnit/381 XML/0 failures/errors/skips |
| data-01 /data-diff-01 | exit 0/0; 58.263 s forced generation; empty generated diff |
| data-02 /data-diff-02 | exit 0/0; 57.967 s forced generation; empty repeated generated diff |
| native-01 | exit 1 in 288.917 s; 597 tests complete; one named required failure |
| ledger-01 | exit 0; accepted-contract ledger validator PASS, 653 units; no delivery count change |
| provenance-01 | exit 0 in 27.112 s |
| whitespace-01 | exit 0 |
| strict-01 | 180-second TIMEOUT, controlled owned child termination; not PASS |

The original build-02 files/helper remain unchanged. A distinct build-03 uses
the native Windows executable path from the successful original build. The
successful launch does not retrospectively make build-02 a Gradle run.

## Native failure and observation limits

The sole final required failure is
`LaserTargetGameTests.anOwnerChangeBeforeRegistrationRegistersTheNewOwner`:
`The record registered for the old owner`. The command starts at 13:17:50.264584 UTC
and ends at 13:22:39.182045 UTC, with no timeout/output overflow. stdout is 1707590
bytes/SHA256 201c6a145a5575947d4dd6ed9f88962de4cd646533ff9b4c4825d5120686c399.
Retained latest.log and debug.log each have 63 ERROR headers/0 FATAL, not 126
independent events. No diagnostic is waived or downgraded.

The detector admission batch is discovered with 15 cases and the full 597-test
completion/failure list contains no detector failure. This supports the new
cases only by source/discovery/aggregate completion evidence; there is no native
per-test XML in this repository run. The Root progress statement proposing XML
inspection was not an observation that such XML existed. JUnit XML is separate.
Neither this run nor empty local-manager metrics proves zero every native
world query, installed lifecycle, packaged restart or real-client behavior.

Separate read-only diagnosis01 finds a scheduling vulnerability in the existing
owner fixture and confirms mapped-sequence error-replacement behavior. The exact
first failed assertion/native-save chronology remains inference. No production
ownership defect or clean production verdict follows. A separately published
[test-only correction task05](LASER-OWNER-REGRESSION-TASK-05.md) preserves this
failed cohort and requires direct prerequisites/command/owner evidence before
another distinctly identified fixed-source run. Source remains unmodified here.

Original command transports for early source authoring/staging are tool-visible,
not all separately retained raw receipts. Prospective source/corrective patch
captures are retained; no earlier transport is reconstructed. Root's disposable
output retirement and the independent final packet are still pending here.

No detector source integration/full delivery, resource-window case, native sleep,
dedicated/restart/V1/V2/survival or release acceptance. All v1.8 Required Gates
remain open, with the inherited acceptance cursor at v1.0.0.
**All current-version Required Gates satisfied: NO.**
