# Recipe signature candidate independent review

Result: 0 Critical/High, two unresolved Medium findings.

1. Rolling controller/port and electrolyzer lack the distinct Forge-aware
   entity-destruction hooks needed to retain unsupported or unproved roots.
2. Built-in ID plus resolved historical hash does not prove authored JSON
   origin: an overriding custom-tag recipe can resolve identically. Automatic
   conversion needs an explicit compatibility-policy decision or refusal when
   origin is unproved.

Actual independent checks: 99 JUnit tests in 30 suites and two observation
probes passed. This does not negate either finding. No tag-reload third finding
is inferred without a supported update sequence. GameTest/native were not run.

[Immutable evidence](round-01-evidence.zip), 352,510 bytes, SHA-256
`5ac9e458ff855262b7e579f415a0e7f8b4fcc1b6111f3fbcaf497450e3fae8f5`.
Raw report SHA-256
`2638e4a6352f501652a8ab3dc5044a1198adf5588c1e23330b04bf0948bef588`.
Root verified ZIP CRC, unique safe names and all 218 manifest-pinned files.
Candidate source remains outside central integration pending revision/review.
