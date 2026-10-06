# Exact common save-bridge regression

Date: 2026-10-07. Source `826f5f20fc26a7be6bfc3af5d91f8a81bd7df71c`;
[run 37513453860](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37513453860),
attempt 1, job 112440254798. Result: **FAILED**, at two required GameTests.
This is now historical: the later row source passes in [RESULT-15](RESULT-15.md).
That success does not rewrite these failures or establish their cause.
This supersedes the dated unobserved-run state for that source. The earlier
corrected-detector cohort remains historical in [RESULT-13](RESULT-13.md).

## Actual results

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace` succeeds.
  All 351 XML suites contain **1,918 actual testcase occurrences /0 failures,
  errors or skips**, with actual child counts matching suite attributes.
- First DataGen writes 808 entries; repeat writes 0. Both builds succeed;
  both tracked diff streams are empty, and worktree checks include untracked
  cleanliness. These results belong to this exact committed source.
- **509 GameTests complete; two required failures**:
  `earthmarsvenusearthkeepsonerocketandexactfueldebits` reports
  `Planetary flight did not land at the requested body`; and
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns` reports
  `No rocket at Tau Ceti f`. `runGameTestServer` exits 1.
- The five-test `classic_save_admission:1` batch executes and none of its
  required subjects appears in the complete terminal failure list. This is
  bounded disposable-fixture evidence, not final-save/stop/restart qualification.
- The single primary GameTest stream has **64 ERROR /0 FATAL headers**. Copies
  are not added to this count; no blanket ERROR waiver is granted.
- Hosted artifact audit reports 3,432 entries and JAR SHA
  `92d66634fa1dc8e65d6577acfe0f616bb7fa75b3c461f36e97eea136c09964f3`.
  JAR upload is skipped after failure; no independent JAR bytes are verified.

## Evidence and independent raw audit

[Collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/common-826-ci-collection-20261007-c18-392bc7/REPORT-01.md),
SHA `96c64b913243a92b429d9489a64aac9bfbb1f20106745f974d19f0b718b04d13`,
retains actual GET-only metadata, retrieval commands, complete capture and
separate successor analysis. Observation/retrieval/audit children exit 0; no
workflow restart, retry or product execution occurs locally.

Artifact 11436182951 is 1,746,234 compressed bytes; API/container SHA
`0831e69523f5823d9a88262358f807e4676fcd998310129bd62e61afafedd169`.
[Artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/common-826-ci-artifact-20261007-c18-392bc7/RETRIEVAL-01.json)
SHA `bdcc0711dae81d443c157bc75cd8f6f4b4d852779c48951ae6c1a58e457ea338`;
[attempt-log receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/common-826-ci-attemptlogs-20261007-c18-392bc7/RETRIEVAL-01.json)
SHA `eeb883cf455c06c06425d1a38ba2f535dcf12ac781bbe98282e39ee6bf1dff82`.
Archives were bounded and checked in memory; their CRC execution is not
independently replayed from the retained thin records.

Root's separate [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-common-826-raw-audit-20261007-01/AUDIT-01.json),
SHA `c509c0b8b015bcced51e32123779e51f6e56e00e52173dea71d8ccdf7bffcf05`,
actually exits 0 (tool `2709f8`). It rehashes **394 files /12,547,915 bytes**,
parses every XML actual child count, verifies source/run/attempt identity, reads
build/DataGen/cleanliness streams and independently checks the exact complete
GameTest failure set. It does not rerun Java or reconstruct archive/JAR bytes.

## Observations and remaining work

[Primary GameTest log](D:/GitHub/ARCE-Task-Evidence/v1.8.0/common-826-ci-artifact-20261007-c18-392bc7/raw/0c4e7f51dede697d7724aaaf77ee2f800f2bc7bbbbaa36cc0361b4f3fed2eda5.log),
SHA `ffa2cae6a1da12aac285c2d4e9868120fac3fdf155ecc3cc55f1c73a7f79c148`,
records Planetary leg 1 awaiting Venus: one Mars rocket in TRANSIT, PREPARED
journal, no destination entity, origin `(64,125,0)`, loaded true /
entities-loaded false /ticking false. Tau PRE/POST retains a nonremoved Space
TRANSIT rocket, PREPARED journal, unassigned destination, loaded YES and
entities-loaded NO. Entity-ticking changes NO to YES within the same recorded
test/game tick. These samples do not prove continuous readiness, live-service
membership or a unique cause. Neither failure is changed into a pass.

The common bridge's five new fixtures are no longer unexecuted. Provider,
actual terminal iteration/final-save disposal, full frame/GuardTicket, physical
hatches/lathe, packaged native/restart, clients, content closure and R-021
remain open. No assertion, deadline, cold-world setup or selected subject is
relaxed. v1.8 remains IN_PROGRESS /IMPLEMENTING; G0-G9 are not satisfied.
