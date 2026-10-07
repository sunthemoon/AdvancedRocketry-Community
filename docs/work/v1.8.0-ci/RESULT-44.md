# HUD source checkpoint: failed full CI and Root raw derivation

Date: 2026-10-08. Tested source
`f9f2d9d2c5eb0de2c9f5d28160ab7804fc57eaef`; run **37653204825 /attempt 1**.
This source was committed/non-force pushed before execution. Later document
commits do not become the tested source. Full regression remains **FAILED**.

The closed [MONITOR-12.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-ci-20261008-01/MONITOR-12.json),
SHA `4487aae21af0bbebdfca7d333b9d1f71b5c2af43c6e857df3b38c9b482b1f25e`,
observes completed/failure at 2026-10-07T16:47:12.324458Z. Job 112901600129
is monitor provenance, not an independent raw-receipt field. Earlier running
observations remain dated history in SOURCE-INTEGRATION-03 and the monitor.

Root bounded retrieval exits 0: artifact **11498455692**, compressed 1,832,499 B,
outer SHA `ea44cc4cfb1e77a2abc2d2df0d1f5e1b9897292a5ec88e5f08168ebb482dcf26`.
Exact source/run/attempt/name/digest match the
[retrieval receipt](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-ci-raw-20261008-01/RETRIEVAL-01.json),
SHA `5d0332875aadf97d0aecfb15ea0a1557f5350a81be1278ff830f3f69cfbc8756`.
897 archive members are inventoried; **399 thin raw members /7,758,528 B**
retained. This raw packet stores no archive, JAR, runtime or reproducible source copy.
The raw seal has 401 payloads: those members, receipt and a 501-byte auxiliary
runtime-registration sentinel, not an archive member or product-test input.
Its seal SHA is `e8c6d3dc18e3439e6ba7b776fc676a5823af506e713156a6b7e3d8539938c7a8`.

Root explicit `python.exe -B audit01.py` exits 0, deriving
[RAW-AUDIT-01.json](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-hud-ci-20261008-01/RAW-AUDIT-01.json),
SHA `1357dfc9837b89d2198cb5f3e296e113fdf8255d42b91d0a330d6ccaeb165c03`.
All retained hashes/sizes match before/after and receipt bytes are unchanged.
This is Root derivation; [RESULT-45](RESULT-45.md) records a different agent's
independent parser/source/input review rather than a second native replay.

- **374 XML suites /2,129 actual JUnit children**, zero failures/errors/skips.
  The HUD/config subset is 18 geometry +2 oracle +8 config =28 cases, all pass.
- Clean build/fresh units, artifact/client-boundary audit, both actual DataGen
  runs and both tracked/untracked clean checks pass. Native failure is not waived.
- **137 native batches /525 complete**, with one required failure:
  `bothhalfcallbacksrevokeinstalledcachedinflightandcompletedairbeforetick`.
- Root's canonical Gradle `gametest.log` has **63 ERROR /zero FATAL**, unwaived;
  mirrored native logs are not additional errors. This does not classify every
  intentional refusal/failure-injection log as a separate production defect.
- Four Linux UID 1001 host samples pass, minimum free 89,766,240,256 B above
  the unchanged 10 GB floor. Retained manifest has 3,535 entries, JAR SHA
  `9124841c969a523afbfe610d53ebe64f66e0ecb9788dd4ca71c1c513c63c9dd6`,
  matching artifact audit. No independent JAR download/open/reproduction.

The Gradle failure sample is at line 2844, completion at 5806. The lower/phase-1
supply prerequisite fails before later callback/recovery checks: seed/current
retained OPEN, six SEALED neighbors, closed halves, AIR at y180, sky true,
motion-blocking-no-leaves height182, y-at-or-above-height false. This is a later
sample, not the historical first-OPEN scan, atomic native coherence, a unique
cause, HUD regression or production repair. Prior Tau/loader/destination failures
are absent from this cohort's required-failure list; this alone does not close
their timing/durability/recovery risks or rewrite historical counts.

No new local JVM, assertion/deadline/budget change, save policy, R-021 acceptance,
content/asset/ledger delivery or G0-G9 approval occurs in this evidence update.
Actual file reload/Font/client, dedicated/restart, real-GPU V1, two-client V2,
performance and native/log qualification remain open. Original failed controls,
older RESULT-42/43 and policy-refused cleanup are preserved separately.
