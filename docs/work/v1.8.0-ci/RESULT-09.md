# Completed observer regression and new source metadata

Date: 2026-10-06. These are separate exact-source cohorts, not a rerun-until-pass
result. The workflow, test selection, assertions and timeouts are unchanged.

## Observer source: full executed result remains FAILED

[Run 37369035893, attempt 2](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37369035893/attempts/2)
tests `35a146fbbe1f2de94160f82307d041d2cd26e472`, job `111969601905`.
The allocation failure in [attempt 1](RESULT-08.md) remains separate and failed
without product execution. The ordinary rerun executes the whole workflow:

| Actual command or observation | Result |
|---|---|
| `./gradlew clean build --no-build-cache --no-daemon --stacktrace` | Successful; `:test` executes; 1,883 testcase occurrences /346 XML suites /0 failures, errors or skips |
| `./gradlew runData` and tracked/untracked checks | 804 writes; both checks pass |
| Repeat `./gradlew runData` and tracked/untracked checks | Zero writes; both checks pass |
| Unfiltered `./gradlew runGameTestServer` | 493 complete; one required failure; Gradle exit 1 |
| GameTest diagnostics | 63 ERROR headers and zero FATAL headers in the main console; overlapping streams must not be added together |
| Hosted capacity | Linux uid 1001; all capacity checks pass, with sampled free space above the required minimum |
| Build artifact | Hosted JAR audit/hash observed; success-only JAR upload skipped, so independent JAR-byte verification is unavailable |

The required failure is `nativesurfacedayroofnightandweatherpublishscalarcredit`.
At its original 40-tick assertion the observer records `stage=DAY_SKY`,
`tick=39`, `published=false`, `native_day=true` and `roof_sky=0`.
This does not establish a unique cause or authorize weakening the assertion.
Nonrecurrence of the earlier planetary and cold Tau Ceti failure headers does
not prove their causes fixed; their original cohorts remain failed.

The [different-agent collector](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-35a146-attempt2-c18-20261006-b2d804/REPORT-01.md),
SHA `5237778bc8388f75bfe0b4d234639bb2ad82da5fb046b59836c2a5ef8805ab4b`,
retains exact artifact/log metadata, commands, raw XML/logs and inspection errors.
Root's independent [retained-byte audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-hosted-35a146-a2-audit-20261006-01/AUDIT-01.json),
SHA `2015f2496b6385c29f987223191c577a95518ce1140a8b87bf75eac80e0275a4`,
executes `python -B audit01.py`, exit 0. It rehashes all 373 retained members
/10,038,072 bytes, independently parses all XML suites and actual testcase
occurrences, and reads original build/DataGen/cleanliness/GameTest terminals.
Display labels are not deduplicated into a smaller test count. This is a raw
result audit, not a second product execution, archive CRC replay or Gate pass.

## Raw-fidelity source: complete audited regression remains FAILED

The separately published [four-file correction](../v1.8.0-c16a-hatches/RAW-FIDELITY-SOURCE-01.md)
is included in `a34de0ad5edb0a2b3efe6ad2bb76c17e40fafc43`.
[Run 37475746093, attempt 1](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37475746093),
job `112310559331`, is **completed FAILED**. The [bounded API observation](D:/GitHub/ARCE-Task-Evidence/v1.8.0/observer-ci-root-20261006-01/OBSERVATION-09.json),
SHA `aa11229319184dd431ad7f24d4363c591eaf18360672b186e9e72fb47eec6da7`,
records successful build/unit, artifact audit and DataGen steps, failed GameTest,
successful raw upload and skipped JAR upload. Root then independently audits
the [different-agent retained result](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-a34de0-review-20261006-c17-01/REVIEW-01.md),
SHA `14951a9a5b8b5ceb9dfb39df8bc0cf874fab52be69d3c77582a34c6eec3f43a2`, and
the new retained bytes in [AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/root-hosted-a34de0-audit-20261006-01/AUDIT-01.json),
SHA `ca47aa1c54cb0082f100fd84e2f2b569c68f44f4b036145d4cae58f66f16c836`:
`python -B audit01.py` exits 0; all 389 members /12,477,320 bytes match their
receipts. Actual XML has **1,895 testcase occurrences /346 suites /0FES**,
including all 75 raw-fidelity/unchanged helper subjects (19/13/8/20/15).
The fresh clean-build test task executes. DataGen writes 804 files, then zero;
both tracked/untracked trees are clean. These are this cohort's actual bytes,
not metrics borrowed from the preceding source.

All 493 GameTests complete with **two required failures**:
`adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns` and
`nativesurfacedayroofnightandweatherpublishscalarcredit`. Solar again observes
DAY_SKY at tick 39, native_day=true and roof_sky=0. The main console has 64 ERROR
headers and zero FATAL headers; overlapping stream copies do not multiply that
count. The Tau readiness investigation is separate; neither failure's unique
native cause or repair is established by these result counts. Conditional JAR
upload is skipped; hosted audit/hash observations are not independent JAR bytes.

Remaining content, native/restart/recovery, actual clients, R-021 and G0-G9
remain open. Neither focused raw-data verification nor these hosted steps
admit a world writer or complete v1.8.
