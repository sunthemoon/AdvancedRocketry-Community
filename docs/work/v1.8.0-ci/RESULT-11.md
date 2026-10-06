# Exact Tau observation regression

Date: 2026-10-07. Source `c6d60282afdd495acae56e05e56d2ccffdbd9959`;
[run 37496511702](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37496511702),
attempt 1, job 112382323227. Result: **FAILED**. This source contains the
reviewed Tau observation, not the later seal detector.

## Actual complete results

- Uncached `./gradlew clean build --no-build-cache --no-daemon --stacktrace`
  succeeds: 346 XML suites, 1,895 actual testcase occurrences, 0 failures/errors/
  skips. Suite attributes agree with actual cases; occurrences are not deduplicated.
- `runData` and its repeat succeed. Main HashCache reports 804 writes, then zero.
  Both tracked diff logs are empty; both worktree receipts explicitly include
  untracked cleanliness. This is raw evidence, not inferred from step metadata.
- Unfiltered `runGameTestServer` completes **493** GameTests. Exactly one required
  failure: `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`,
  `No rocket at Tau Ceti f`. Main terminal lines 5738–5740 associate that failure
  with the completed cohort; its earlier failure message is at line 2718.
- Main console has 63 ERROR headers, zero FATAL headers, without a blanket waiver.
  Raw evidence upload succeeds; build-JAR upload is skipped. The build-side audit
  reports SHA `0a50f4352d7b692fb3615f0a33d1aed6d3b31a624c759d7a410cd35f3196b972`
  and 3,411 entries, but no independent JAR bytes were downloaded.

## Bounded Tau observations

Main lines 2713–2714 contain PRE and POST around the unchanged single lookup.
Both report PREPARED, the exact source UUID present as a nonremoved TRANSIT
rocket, destination UUID UNASSIGNED, cached destination chunk loaded, but
entities_loaded and entity_ticking both NO. Expected/source/destination world
times are 14996, creation time 14726, test tick 743. Nano values 528727622387
and 529663639651 differ by 936,017,264 ns.

These are two samples, not continuous readiness, on-disk absence, live ownership
or a unique cause. Original assertions, 270-tick delays and deadlines remain.
The missing destination is not diagnosed as a spawned-then-lost entity.

## Raw identity and independent audit

Artifact 11428591552 has 1,730,682 compressed bytes and verified API/container
SHA `55f00238971e4f9817d3324607eb1bc923c11b4e9a8954ee2c16ee9fa2c92785`.
The bounded collector also retrieves the full attempt logs; all-member path,
size and CRC checks precede retention. Archives are not kept or replayed here.

[Artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-c6-artifact-20261007-c18-25fa10/RETRIEVAL-01.json)
SHA `19c8fe42301edbc3473cf4cf6dd10c5f82f2c7279b4b060103ef1ecb036994f1`;
[attempt-log receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/tau-c6-attemptlogs-20261007-c18-25fa10/RETRIEVAL-01.json)
SHA `27e27645fbcf9f32f53470a8478f76f5c9d51fd5c8b3a0d38162b542fa282039`.
Root independently rehashes all 371 artifact plus 18 attempt-log retained members,
12,478,245 bytes, with no mismatch. Duplicate consoles are not summed as tests.

[Root raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-tau-c6-raw-audit-20261007-01/AUDIT-01.json),
SHA `42b4ae4f996dad647ae84aba20603e5a0fd53bc5341a9f0a4c37afede6f5015e`,
is an actual fresh Python `-B` exit 0. It binds actual XML cases, main terminal/
observations/ERROR counts, empty diff/status receipts and raw tested-commit bytes.
Its case-sensitive optional HashCache-line selector returned no lines; Root
separately reads the exact data/repeat members at lines 3681/97 for write counts.
That limitation does not rewrite the audit or imply a failed DataGen command.
Main console is 1,546,236 bytes, SHA
`bba6c42c44dabb74800d60748ecc5fcc0c94f5fb9261de4a4733a2c98f55aa65`.

No workflow rerun, test filter, timeout increase or production fix is performed.
Earlier failed cohorts remain at their own identities. Native/restart/crash
recovery, real clients/GPU, content closure, R-021 and G0–G9 remain open.
