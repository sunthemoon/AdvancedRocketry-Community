# Airlock first regression: units/DataGen pass, four native failures

Date: 2026-10-07. Exact source/resource commit:
`61ae5001532c2a95c3393030be8f2d36521a446a`.
[Run 37590839225](https://github.com/sunthemoon/AdvancedRocketry-Community/actions/runs/37590839225),
attempt 1 /job 112691678476. Automatic regression: FAILED.
The dated [running observation](RESULT-25.md) remains unchanged as history.

## Actual execution and retained outcomes

- `./gradlew clean build --no-build-cache --no-daemon --stacktrace`: succeeds.
  Actual retained XML has **372 suites /2,097 testcase children /zero failures,
  errors or skips**, matching suite attributes with no direct suite failures or
  errors. Four pair/placement, five provider and five runtime-lifecycle cases
  match every fixed-source method and pass: fourteen new unit tests, not just
  declaration counts.
- Artifact and common/client-boundary audits succeed. `./gradlew runData
  --no-daemon --stacktrace` executes twice. Both tracked diff logs are empty;
  both tracked/untracked clean-worktree checks pass. This now checks the
  provisional resource combination through actual native generation.
- `./gradlew runGameTestServer --no-daemon --stacktrace`: fails. Canonical line
  5777 reports **525 GameTests complete**, followed by four required failures:
  `earthmarsvenusearthkeepsonerocketandexactfueldebits`,
  `adiscoveredtaucetifisreachedbywarpandadockedrocketlandsandreturns`,
  `sixironrecipeandseedednativelowerhalfexplosionloot`, and
  `bothhalfcallbacksrevokeinstalledcachedinflightandcompletedairbeforetick`.
  Their messages are respectively logical-rocket conservation, absent Tau
  rocket, finite loot samples missing an outcome and initial installed supply
  not established. Seven-test `airlock:1` batch executes at line 2794; two are
  named terminal failures. No individual native pass XML is retained.
- Canonical console has **67 ERROR /zero FATAL**, unwaived. Mirror logs are not
  summed. Raw-evidence upload succeeds; external built-JAR upload skips.

## Evidence and audit association

[Terminal capture](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-source-ci-regression-20261007-01/TERMINAL-CHECK-01.json)
at **2026-10-07T08:11:53.624496Z**, SHA-256
`2f909e3db5b1a7bb6faa2eee2da080f64b20c48705b8d674ce00f481e05bb77d`,
binds exact source/run/attempt/job and all completed step outcomes. The bounded
monitor also terminates normally after a separate completed-failure capture.
No failed run is retried or rewritten into a pass.

Bounded retrieval `37d54e`, exit 0, retains the
[receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-source-ci-raw-20261007-01/RETRIEVAL-01.json),
SHA-256 `24c1e3c7e147a21803bedaf2aac57675b5729ace13c6281148a399b16dcda581`.
Artifact 11469012456 has 1,823,327 archive bytes, API/actual digest
`5af0d015f43fef75994e1314618cf21a2ed428e68c09b80815934c635fe4487a`.
Only compact raw XML/log/JSON/text is retained: 397 files /7,677,516 bytes.
No archive/source/class tree, server copy or JAR is exported; credentials/signed
redirects remain in memory and are not persisted.

Root [raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-source-ci-regression-20261007-01/RAW-AUDIT-01.json),
SHA-256 `bf2e4be1c69f4f0d9b72ff95b4c3a4d8753366c6afc10464e9ec806d1c6fc75c`,
executes at `fc7ddf`, exit 0. All retained files are rehashed before/after;
actual XML children/attributes, selected committed declarations, canonical
batch/terminal, generation/clean logs and hosted manifest agree with zero drift.
Earlier preliminary recount `6edc7f` also exits 0. The separate
[different-agent raw audit](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-ci-raw-independent-audit-20261007-01/REPORT-01.md),
SHA-256 `37ea4bd41857d61a14230abf83c359c321331cf8353ac53b9f9747c8900607ff`,
independently derives agreeing XML/terminal/error/manifest facts with zero raw
input drift; it does not read Root's audit as an expected result. Its
[thin results](D:/GitHub/ARCE-Task-Evidence/v1.8.0/airlock-ci-raw-independent-audit-20261007-01/RESULTS-01.json),
SHA-256 `1d9dd44de4adb30ae39a5d6318474f0ea178cb7e35768dd133969b9da7ab5bf2`,
retain the exact records. Root fully reads/hash-checks both at `43d9c5`, exit 0.
The review's report-size formatting control failure and later unchanged pin
checks are disclosed; no product failure is overwritten.

Hosted JAR records SHA-256
`524ff2140430f974f344d4c1c91b8c807b38e40306ea8568f437caf482a2f34c`
and 3,525 entries. Actual hosted manifest includes all 21 airlock resource
postimages with exact bytes/hashes and the three new primary classes. This is
hosted identity/membership evidence, not a downloaded binary or independent
inner-JAR reparse. Resource V1/V2 and original-art comparison are not proven.

The separate [committed integration review](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18-airlock-committed-integration-independent-review-20261007-01/REVIEW-01.md),
SHA-256 `92e407bf4de9cefd149f257f0f83f8846bbc27f91f0f9a03a540e5d7510e781f`,
checks 37 exact source/resource pins and the 43-path combined commit scope with
no actionable static finding. Root fully reads/hash-checks it (`d3da97`, exit 0).
That static assessment is not execution of the later native tests.

## Remaining work and failure preservation

Root assigns only the two new airlock failures to isolated worktree
`D:/GitHub/arce-v180-airlock-fixture-fix-20261007`, fixed at committed f410d70d;
setup `903cd5` exits 0 with clean initial status. Worker may change only the
airlock GameTest and its new correction note. No production semantic change,
assertion deletion, larger timeout, wider inspection loop or Gate relaxation is
authorized. Cause and repair require actual evidence; source correction alone
does not prove either runtime success or whole-version completion.

The two rocket failures retain their exact messages/limits; the Earth-Mars-Venus
failure predates this run's airlock batch, not a demonstrated unique cause.
Native restart, real clients/GPU V1/V2, performance, license/full-content and
all G0-G9 remain open. R-021 and ledger states are unchanged. Root's two later
guessed source locators (`c32790`, `266c3f`, exits 1) are disclosed lookup errors,
not product-test outcomes; later exact-path reads do not turn them into passes.
