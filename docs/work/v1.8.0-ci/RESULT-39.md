# Installed-airlock supply snapshot: failed terminal and Root raw derivation

Date: 2026-10-07. Exact source
`2a59cfac2e5c6713a5e6039149d0b4f2830ff8d7`, run 37618804027 /attempt 1
/job 112783570613. Earlier [RESULT-38](RESULT-38.md) is a dated running
observation, not the terminal. This full regression is **FAILED**.

## Cohort and actual execution

The same monitor ends normally (`d31062`, session 55164 closed), observing
completed failure at 2026-10-07T12:19:44.271439+00:00.
[MONITOR-08](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-supply-diagnostic-ci-20261007-01/MONITOR-08.json)
SHA-256 `4c5c59b57379fc53d1412c395d6bd2b276eced998f166bc22185fee730ac2e85`.
No restart, replacement run or changed source is used. After re-reading and
hashing the existing bounded retrieval helper, Root runs it against artifact
11482000627 (`c71ec3`, exit 0). Archive is 1,824,223 B /SHA-256
`2edd1f28c55e36c36206f2dd1720fe3a74708dbb34ccf2c129233319d19e8770`,
matching API digest; source/run/attempt/name/CRC/member bounds are checked.
The [retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-supply-diagnostic-ci-raw-20261007-01/RETRIEVAL-01.json)
SHA-256 `3ebe8ee9e9d8424299bc4e02ef541816c4acaa704251a4d3bdffe06d4f7b5c2f`
retains 397 compact raw members, not binary/source/HTML trees.

Root's new `python -B audit01.py` (`d3961e`, exit 0) hashes every retained
member twice, parses actual XML child counts, reads raw step logs/manifests,
and compares the committed airlock source. Its
[RAW-AUDIT-01](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-supply-diagnostic-ci-20261007-01/RAW-AUDIT-01.json)
SHA-256 `29e03abba8cb02f80d796a47036660453d419b10cab53c8fa8493109cb1b9d8b`
records zero input drift. This is raw derivation, not native replay or independent
binary parsing. An earlier PowerShell preview (`29566b`) wrongly used reserved
$Host and could not read that host file; its tool errors are retained as Root
tool-history transcription. Separate corrected reads (`af5751`, exit 0) and
the successful Python derivation establish host facts, not the failed preview.

## Results and honest limits

**372 XML /2,104 JUnit cases**, zero failures/errors/skips, actual children match
declared counters. Clean build, artifact/client audits and two actual DataGen
logs succeed; both tracked diff logs are empty and tracked/untracked clean
status logs pass. Four host samples are Linux UID 1001, all three filesystem
roles above the 10 GB floor (minimum observed 89,685,024,768 B).

**137 native batches /525 complete**, with three required failures:
- Tau travel: no rocket at Tau Ceti f; retained PREPARED/source readiness
  observations do not establish a unique native loading cause.
- Installed airlock: lower half/phase 1 fails initial supply. New snapshot
  executes: retainedOutcome=OPEN, needsScan=false, single-seed last bounds,
  exposedSkyIsOpen=true, climateControlRequired=false; seed OPEN and all six
  adjacent cells SEALED; both halves lower/upper north/left, unpowered/closed.
  Resources remain oxygen=1000 and energy=40000. This describes failure-time
  state, not what created that outcome, sky internals or successful recovery.
- Native loader drop/place: diagnostic executes with **count=0**. The original
  expected conservation assertion remains failed; no filter/retry/repair is added.

Canonical gametest.log is 1,592,402 B /SHA-256
`d0d095d9d2ee50e05032350f0d93cbbdf7eff705749ab1d3365df5fec22439e4`.
**65 ERROR /zero FATAL** remain unwaived; latest/debug mirrors are not summed.
No individual native PASS XML, abnormal cleanup, full six-case airlock recovery,
packaged use/restart or client V1/V2 qualification is manufactured.

Retained JAR manifest reports 3,526 entries and SHA-256
`3c6e9c57591fe4c541257e15e947a8294f591cf0eda1e8fe66e9e2b83d2e8df4`,
consistent with artifact audit and identity 1.20.1-1.8.0-dev. It is not a
downloaded/reproduced JAR or independent binary inspection. The new airlock
class exists in that manifest and its exact committed source is read separately.
Different-agent retained-stream audit is pending. Local C stays below the floor;
no local JVM/Gradle/native/client starts. All G0-G9, content/asset delivery,
shared hatch activation, persistence recovery and real clients remain open.
