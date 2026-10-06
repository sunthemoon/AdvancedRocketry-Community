# Tau failure observation bounded adoption

Date: 2026-10-06. Integrator: Root. The owner's conversation authorization is:
"授权无未解决 Critical/High/Medium 的审核定稿继续实现；重大语义调整仍另行确认".
This adopts a GameTest diagnostic only, not production transfer semantics.

Root reads the full original [independent review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-observation-contract-review-20261006-902ec7/REVIEW-01.md),
SHA-256 `3954c7e522b618eb8d62d29cd5471430477c27e1fb3fc52ccc02d87cd32959dd`,
and the [successor review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-observation-task-review02-20261006-4fb835/REVIEW-02.md),
SHA-256 `6784c030a877e4dc79db111a6cc015e34c72c99fde24adb5081cf51294b2f870`.
The original Low singular-other-test wording is resolved only in the successor;
the sealed original brief/reports remain unchanged. The successor review has
no new C/H/M/L, six actual finite controls exit 0, and nine inputs do not drift.

Root adopts exactly [TASK-01.md](TASK-01.md), 13,853 bytes / SHA-256
`b066b94d0fba3592e629d4240e308e30dbec86ccc36c861e34d2cdaefba0669d`.
Its unadopted wording is superseded by this disposition only. The source basis
is fixed e0; the original eight named source/workflow dependencies and target
must still match before assignment. A fresh isolated worktree at the published
disposition commit will be assigned separately. Worker source scope remains
one existing `gametest/TauCetiPathGameTests.java`; Root retains all other files
and Git. This record grants no Java/native/network or cleanup execution.

The diagnostic captures bounded PRE/POST scalar samples around the original
fixture lookup and emits at most two bounded lines on its existing null
failure. Both other GameTests remain byte-identical; all original assertions,
270/1400 delays, route/fuel/target oracles, cold setup and cleanup stay unchanged.
No readiness bypass, timer/radius increase, prewarm, recovery policy, production
trace, SavedData acquisition inside capture or new persistent writer is adopted.
Unknown data is unavailable, never positive certification. The new exact e0
[full regression](../v1.8.0-ci/RESULT-10.md) remains failed with the Tau case;
these diagnostics neither establish its cause nor fix it.

Independent actual-source review and exact committed unfiltered hosted
build/JUnit/DataGen/GameTest execution remain mandatory and pending. Native,
restart/crash recovery, real clients and all Required Gates remain open.
