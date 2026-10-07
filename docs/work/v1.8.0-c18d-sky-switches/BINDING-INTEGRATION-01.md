# Actual sky-registration tests: reviewed source integration

Date: 2026-10-08. Scope: five test-only observations of the existing client
bootstrap registration. No production/config/ID/network/asset change.

## Committed source and independent review

Claude's fresh author session was a7fbdb19-8d2e-4496-bc0c-bb089cae36cd.
Root checked its exact two-file scope, then committed and non-force pushed
candidate 493df4909458eb519aa1a281a1e4c66ba018288b, parent 2e397a49.
The original [author handoff](BINDING-HANDOFF-01.md) keeps its historical
unrun statements; it is not overwritten with later execution results.

The fresh [independent source report](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-binding-codex-independent-20261008-01/REPORT-01.md)
has SHA-256 caf78e0aafdf1b75fd2b9ba3de2a4b0382b286ee5617a8e7c26cb5fc14ce04b8.
It finds no introduced Critical/High/Medium/Low within the two-file diff,
executes the separately authorized targeted command and releases all interests.
Root read it fully and independently verified its 41-file seal, both exact
source blobs and the raw XML elements.

Root adopted only this same-version test leaf. After reviewing the exact
two-file cached stat and whitespace, it merged candidate 493df490 into main
at d2b4324f54c51c66c2e867ac7fc292c134d09c68 and non-force pushed main.
The other merge parent is the proposed-document checkpoint a8219e75.
Git confirms the merged entire src tree and build.gradle/gradle.properties/
settings.gradle/gradlew/gradlew.bat blobs equal tested 493df490. This is source
equivalence evidence, not a claim that d2b4324f itself was separately executed.

New test SHA-256: 56b62ecdf70c4b2bcb977f0375223997f8b629c72c50df26fc19f7941955c32c.
Unchanged author handoff SHA-256: 7d87fcc31818d3a794cfe141434790e5cb979982d499f246b910bade17611001.

## Actual test command and result

In the clean detached independent review checkout at 493df490, with full
JDK 17.0.7 and all process-local temp paths under its own D evidence leaf:

```text
gradlew.bat test --tests '*SkyEffectsRegistrationTest' --tests '*PlanetaryEffectsTest' --tests '*ClientConfigTest' --no-daemon --no-build-cache --max-workers=2 -Djava.io.tmpdir=D:\GitHub\ARCE-Task-Evidence\v1.8.0\c18d-sky-binding-codex-independent-20261008-01\temp --stacktrace
```

Exit 0, no timeout, 64.838 seconds. Actual copied XML: new registration 5,
existing adapter 7, existing config 8, **20 JUnit cases**, zero failures,
errors or skips. Its separate 21 passing harvest controls are not product
cases. The test task's compilation dependencies ran; no separate clean build,
DataGen, full GameTest or native restart is implied.

The tests call the real ClientBootstrap handler through Forge's event and
observe the map. They cover exact literal IDs/keyset/cardinality, distinct
adapters, preserved vanilla entries, all four live switch combinations and
unload/reload/re-enable on the same registered objects. Existing vanilla
fallback/lightmap/fog/cloud/weather policies are observed without a GPU.
The author's mutation table is reasoning, not executed mutation testing.

The reviewer retained an initial incorrect CRLF wrapper preflight assumption
and failed filename reads; successor metadata checks corrected those without
source/test/timeout changes. It copied reports before checked literal cleanup
of its own new build/.gradle, removing 62,170,578 bytes. Historical cleanup
refusals/debt are unchanged. Root's 93 pre-integration and 104 post-merge
controls pass; none are additional JUnit cases.

## Evidence, remaining work and Gate

Independent evidence is in c18d-sky-binding-codex-independent-20261008-01.
The author packet is sealed separately in c18d-sky-binding-claude-author-20261008-01.
Root's own task, exact seal/XML/source checks and merge/stage/push logs are in
[root integration evidence](D:/GitHub/ARCE-Task-Evidence/v1.8.0/c18d-sky-binding-root-integration-20261008-01/TASK-01.md).
Original evidence/author bytes and user-owned AGENTS stay unchanged.

Still open: real Forge mod-bus dispatch, TOML reload, packed dimension_type
selection, enabled rendered sky/fog/ambience, rapid toggles/travel/resource
reload, V1 and two-real-client V2, complete regression/native/restart and
performance checks. Static config isolation would need attention if JUnit
parallelism is enabled later. The latest complete f9f2d9d2 campaign still has
one required native airlock failure and 63 unwaived ERRORs; those results do
not bind this later source. No ledger delivery or risk/asset acceptance occurs.
v1.8 remains IN_PROGRESS and all Required Gates stay open.
