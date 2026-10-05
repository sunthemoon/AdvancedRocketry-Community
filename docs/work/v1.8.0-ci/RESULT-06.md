# Laser-fixture cohort stops at checkout cleanliness

Date: 2026-10-06. Tested source `86a819ca564c43206c6b9272c0b79076fceaa874`,
[run 37353443122](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37353443122),
attempt 1. **FAILED; GameTests not executed.** RESULT-05 remains the last
complete GameTest cohort, with its four required failures and unwaived ERRORs.

Hosted fresh `clean build --no-build-cache` succeeds in 3m 47s; all 20 tasks
execute. All 342 unique JUnit XML suites parse to 1,861 testcases, zero failures,
errors or skips. First `runData` succeeds in 22s and writes 788 outputs. The
unchanged whole-tree `git diff --exit-code` fails on one historical diagnostic,
so the untracked check, repeat DataGen, unfiltered GameTest and JAR upload are
skipped. This neither passes nor re-fails the Laser fixture at runtime.

The original checkout-status step already reports
`docs/work/v1.0.0-native-reconnect/recovery-bytecode.txt` dirty before build and
DataGen. Its six-line diff is reproduced by Git text conversion. The earlier
Git blob is 68,268 bytes with six residual CRLF delimiters; the existing Windows
capture is 69,100 bytes and matches its historical SHA-256 checksum
`bf67cefd598b6916acf7b4c5117b162b5741fd5344f71af8e938c3b4cafda45b`.
No generator writer or production failure is established by the diff.
The separate [preservation candidate](CAPTURED-TEXT-PRESERVATION-01.md) retains
captured bytes and proposes only an exact-path Git attribute correction.
It does not waive cleanliness or alter historical results.

The [collector report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-86a819-observation-20261006-c18-52bc71/REPORT-01.md)
is 7,268 bytes, SHA-256
`13c9359f2f10a04d31a0a537a88cf1aefb814bde486a1a0e2636273592368dc0`.
Regression artifact 11363098234 is 1,372,294 compressed bytes, API/archive SHA
`914f4288ff64ea54b780d4b2a4e1dfc7c6925953593b47e9cf2ff78622b7f47e`.
The unchanged bounded collector checks source/run/attempt/digest/CRC and maps
359 retained streams /2,301,621 bytes; all independent stream rehashes match.
Its [artifact receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-86a819-attempt1-20261006-c18-01/RETRIEVAL-01.json)
has SHA `84d408d4d11b4fd76c28171d889d9cb6c22d9a70773c6bdb0dea232350238801`.
The [original-step receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/v180-hosted-86a819-joblogs-20261006-c18-01/RETRIEVAL-01.json)
has SHA `0c2d3c118e840a66053e9a95434cab253e49cef5daa78e274db207539dc7616e`;
17 retained step streams /1,524,699 bytes also rehash without mismatch.
The original failed XML-prefix parser and imprecise initial delimiter description
remain qualified in the collector record. No independent archive replay,
retrieved JAR-byte comparison, native recovery or Required Gate acceptance is
claimed. The recorded build-side JAR SHA is
`9f192c759d39ba457abd10723d2b0b7f92ff2e0107cb0a6545dc04aff21a025e`,
not a rehashed retrieved container.
