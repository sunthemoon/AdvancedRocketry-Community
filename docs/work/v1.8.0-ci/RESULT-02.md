# Hosted repeat DataGen regression

Date: 2026-10-06. Tested source:
`47cdcb85a76ec3e55afeef8da714ac6a30527977`. Result: FAILED; no Gate acceptance.

[Run 37333529725](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37333529725),
attempt 1, completes a fresh clean build with 1,853 JUnit cases /341 suites
/zero failures, errors or skips. Host tooling runs 17 tests. First DataGen
writes 781 files; an actual second run writes zero of 781 outputs. Both tracked
diff checks and tracked/untracked cleanliness checks pass.

The unfiltered GameTest run completes 477 tests with two required failures:
Tau Ceti landing reports DESCENT, DESTINATION_SPAWNED and entity ticking at
(1,122,0); the gravity-field switch reports "A disabled field stayed active".
These are actual failures, not waived negative fixtures. The earlier single
"No rocket" failure is a different cohort. No unique production cause is proved.
The lamp and destination-readiness corrections are absent from this source.

Artifact 11355374064 has 1,707,355 compressed bytes and SHA-256
`a4404c39a31714aa2eb06327e5ab6acacf0b2d60aadaeb13013abfa7495a0e82`.
Root's bounded collector exits zero and checks its API cohort/digest and native
archive bytes before retaining mapped logs/XML; the result remains
RETRIEVED_NOT_VERIFIED. The [retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-47cdcb-attempt1-20261005-01/RETRIEVAL-01.json)
and [independent actual-raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted47-result-review-20261005-90d2a7/REVIEW-01.md)
bind 366 retained files /7,318,651 bytes with zero hash mismatches and independently
derive the XML/DataGen/failed GameTest totals. Audit SHA-256:
`abd034a4d2cf9d1aba2b3ad44f51240b12b8919bcd42de4d634a94493fc07cea`.
It does not independently replay the unretained ZIP or JAR container.

Build-side artifact audit passes; its conditional JAR upload is skipped because
the full job fails. No independent JAR-byte/API comparison or ERROR-free claim
follows. Non-root hosted preflights pass; this is not restart, GPU or reference
performance evidence. Original failed runs and collector/control failures remain
in their separate records. Local C remains below 10 GB; no local heavy run occurs.
New evidence uses the project-parent D directory. Ledger closure, R-021, full
runtime implementation and all G0-G9 remain open.
