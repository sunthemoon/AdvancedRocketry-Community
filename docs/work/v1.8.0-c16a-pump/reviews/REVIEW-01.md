# Pump independent review 01 — portable record

Outcome: CHANGES_REQUESTED; 0 Critical, 0 High, 3 Medium.
Findings concern caller-input capability reentrancy, a different resource-ID
bound than the frozen shared codec, and oversized-test fixture interference.
Two actual registered probes failed. Author revision 2 is separate and does
not replace these original sources or logs.

[Immutable evidence](round-01-evidence.zip), SHA-256
`e710ae9a84b65d602f6e0d763ff7cf85494e55be5c7e2e6ebf1ef46add42eb74`:
2,900 ZIP entries, 2,899 manifest-pinned files verified individually; CRC clear.
The raw report is `REVIEW-01.md` inside the archive, SHA-256
`1c75320698c2da387d587b0a206224ea226a2c6747634a2901ac90406df18425`.
Relative links address frozen reviewed source/probes/XML/logs inside the ZIP.

Actual scoped result: 20 JUnit passed; runData exit 0. All 432 GameTests
completed, with the two reviewer probes failing; Gradle exit 1/JVM exit 2.
The raw log records 2,005 repeated save-error pairs caused by the oversized
test fixture. This is not permission to remove the production save veto.
No native, V1/V2 or complete-version Gate result is claimed.
