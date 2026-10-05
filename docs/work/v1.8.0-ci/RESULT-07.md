# Solar integration: complete hosted regression with three required failures

Date: 2026-10-06. Status: FAILED, not a release candidate.
Source: `058cd67dacac4ac43ca6373d039fff1a2822e426`.
[Run 37362036838](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37362036838),
attempt 1, job 111938720222.

## Actual execution

The unchanged workflow executes on a non-root Linux host, Java 17 /Forge
47.4.10. Space checks pass before build/data/GameTest; no local full Gradle or
native server is run while C has less than 10 GB.

| Step | Actual result |
| --- | --- |
| `clean build --no-build-cache --no-daemon --stacktrace` | Success, 3m 7s; 1,883 XML testcases /346 suites /0 failures, errors or skipped |
| Artifact/sidedness audit | Reported success; 3,409 JAR entries |
| First `runData` | Success, 20s; 804 files written, tracked diff empty and untracked tree clean |
| Repeat `runData` | Success, 13s; 804 outputs, zero writes; tracked diff empty and untracked tree clean |
| Unfiltered `runGameTestServer` | 493 complete, 3 required failures; JVM exit 3 /Gradle failure |
| Evidence upload | Success; conditional JAR upload skipped |

Required failures retain their original assertions and time bounds:

1. `earthmarsvenusearthkeepsonerocketandexactfueldebits`: planetary flight did
   not land on the requested body.
2. `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`:
   no rocket at Tau Ceti f. The existing diagnostic observes PREPARED,
   live TRANSIT source, destination UUID unassigned, loaded blocks but both
   entities-loaded and entity-ticking false at the existing 270-tick check.
   Its lookup-qualified observation is not a unique causal diagnosis.
3. `nativesurfacedayroofnightandweatherpublishscalarcredit`: ordinary producer
   did not publish within 40 ticks. This new Solar fixture failure's
   production/fixture cause and failed substage remain under investigation.

The cohort has 65 ERROR/FATAL headers in the authoritative GameTest stream;
build/data/repeat-data have zero such headers. No blanket waiver follows.
There is no Laser or destination-readiness failure header in this cohort;
historical failures are not erased or declared uniquely explained.

## Raw identity and limits

Artifact 11366509200: compressed 1,724,381 bytes, SHA-256
`21d646042123eef5fe6b59e06fbf38d721b0c1fa5cbbc2ffbd59ac7313408aba`.
The [retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-058cd6-attempt1-20261006-c18-01/RETRIEVAL-01.json),
SHA-256 `ef80c7bc31bbbb0da16db4092371827e234ba115316880733873a603b56594df`,
binds exact source/run/attempt, digest/CRC controls, 840 members and
371 retained streams /7,411,882 bytes. Collector rehash reports zero mismatch.
Its original repeated-parameterized-display-name parser failure is retained;
the separate successor counts actual XML occurrences, not unique display names.
The [independent raw-result audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c17-hosted-058cd6-audit-20261006-63e4ab/REPORT-01.md),
SHA-256 `77a15e5b8e38aba75189d5e188e51b20081fe85b466944417a991a78ed4654f0`,
freshly verifies all 371 retained streams, XML/batch/failure identities and
unchanged selected inputs. This is outcome evidence, not Solar source approval.

The hosted JAR audit reports SHA-256
`21e4d97138eafdae8eb1568befab5bff9eec3054b77ea892b0371c961297979f`.
No JAR bytes are downloaded or independently compared: conditional upload is
skipped. No new packaged S1/restart/crash or real-client/GPU evidence exists.
The [source checkpoint](../v1.8.0-c17c-solar-generator/SOURCE-INTEGRATION-01.md)
separately records reviewed committed bytes and bounded development checks.
R-021, ledger closure and G0–G9 remain open; this result grants no Gate PASS.
