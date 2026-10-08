# Airlock next19: test design for W19

Written design only. Nothing here was executed. The checks below apply to a
future committed implementation of NEXT-SPEC-19 section 4. They do not
prescribe whether the subject passes or fails.

## 1. Source-diff review (reviewer, read-only)

- D1: `git diff --name-status <base> <impl>` lists only
  `src/main/java/.../gametest/AirlockDoorGameTests.java`.
- D2: unchanged tokens: `timeoutTicks = 40` on all seven subjects; the
  `tick < 32` loops at the initial supply and recovery stages; `tick < 8`;
  `runAfterDelay(index + 1L, ...)`; `index < 6`; `index >= 3`, `index % 3`; every
  `helper.assertTrue` predicate and message prefix; `pair`, `prepare`,
  `Fixture`, `InstalledCases.close` and the listener callbacks.
- D3: the only added import is `net.minecraft.world.level.LightLayer`. There is
  no new `runAfterDelay`, `succeed`, `setChunkForced`, `setBlock`, `schedule(`,
  coordinator `tick(`, `cancel`, flush or reset call.
- D4: the witness helper contains no `helper.assert*`. Its catch covers
  `ReflectiveOperationException | RuntimeException | AssertionError |
  LinkageError` and returns `UNAVAILABLE` text. Sample length is at most 640,
  and the total witness text is at most 2,048.
- D5: the witness is appended only on the `!supplied` path of the existing
  message, after `initialSupplyDiagnostic`.
- D6: the line count is recorded. Over 500 lines, an AGENTS 3.4 responsibility
  note is written; 800 lines must not be reached.

## 2. Build and data (on the committed implementation SHA)

```bash
./gradlew clean build
./gradlew test
./gradlew runData
git diff --exit-code
```

Record exit codes, logs and SHA. A compile failure means W19 is not callable as
specified. In that case report the failing API; do not substitute another
method.

## 3. Native cohort

```bash
./gradlew runGameTestServer
```

- N1: the total GameTest count, the required-test set, and `airlock:1 (7 tests)`
  match packet 17 (542 tests), apart from any separately explained change.
- N2: if the subject fails at the supply prerequisite, the message contains the
  unchanged prefix and diagnostic, then `witness19` with ENTRY, PRE[i] and
  POST[i] (or `NO_INSPECTING_PASS`). Each class has its label.
- N3: if the subject passes, no witness text appears anywhere in the logs.
- N4: any other failure is preserved verbatim and is not counted as W19 data.
- N5: the other native ERROR headers are listed separately and not waived.

One cohort cannot establish causation. Root decides the cohort count and host.
Using the packet 17 host improves comparability but is not a control.

## 4. Reading a failing witness (no repair follows)

| PRE observation | Suggests | Does not prove |
|---|---|---|
| seed OPEN, canSeeSky true, sky 15, y < height | R3 via canSeeSky in the scan bracket | stale light as the cause, or scan-time value |
| seed TRAVERSABLE, a neighbour OPEN | R1/R2 or a neighbour path | which return the scan saw |
| all non-OPEN at PRE and POST | change between samples, or another source | absence of light change |
| ENTRY differs from PRE | change between case entry and scan | timing of the scan-time read |
| `NO_INSPECTING_PASS` | no growth in service inspections this case | that no scan ran elsewhere |

Every row keeps non-atomicity and native cache effects (NEXT-SPEC-19 section 6).

## 5. Behaviour preservation in passing cases

- P1: the six cases complete in order, and `succeed` is reached only at case 5.
- P2: no new fixture cell, drop or forced chunk remains after the test. Checked
  through the existing Fixture bound and InstalledCases close paths, unchanged.
- P3: the witness adds no ticks. Each case still starts at its original delay.

## 6. Not covered

Scan-time operands, vanilla `canSeeSky` semantics, light-engine pending state,
production recovery (ANALYSIS-18 C4), dedicated server, restart, S1/S2, V1/V2
and fault injection. These remain open.
