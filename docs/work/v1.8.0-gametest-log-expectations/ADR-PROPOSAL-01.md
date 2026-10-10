# ADR proposal - Declared expected entries in GameTest server logs

Historical proposal, superseded by [ADR-072](../../decisions/ADR-072-V180-GAMETEST-LOG-EXPECTATIONS.md).
Its original acceptance/completeness claims were narrowed after independent review;
R-021 and all release Gates remain open. This proposal itself was not accepted unchanged.

```yaml
status: PROPOSED
date: 2026-10-10
owner: sunthemoon
deciders: []
target_version: v1.8.0
number: assigned by Root at integration
supersedes: none
```

## Context

[The master test plan](../../05-MASTER-TEST-PLAN.md) section 6 judges server
logs as follows: a project `ERROR` fails, a repeated and unexplained `WARN` fails,
and accepted warnings go into the version's Known Issues. The rule was written for
dedicated-server runs, where no error is expected.

The GameTest server is different. Negative GameTests deliberately drive refusal and
failure paths, and the code under test logs those paths exactly as production must.
Every complete `runGameTestServer` log of the current v1.8 sources therefore holds
62 ERROR, 161 WARN and 0 FATAL entries. Root records them as "unwaived" after every
regression. Nothing in that record would show a 63rd error, or a new error that
replaced an old one.

On 2026-10-10 Claude attributed every entry of seven complete logs (one Claude run
and six Root runs, listed in [HANDOFF-01](HANDOFF-01.md)) to the test batch that was
running and to the stack frames it carried. All seven logs hold the same entries in
the same batches:

| Entries | Level | Produced by |
|---:|---|---|
| 26 | ERROR | Chunk-save refusals for oversized recipe, combustion and non-admissible solar input. The tests post the save events, and Forge's event bus logs each thrown listener exception. |
| 16 | ERROR | Machine-menu channel version rejections (`RecipeMenuChannelGameTests`). |
| 13 | ERROR | Injected Precision Assembler migration save failures and their follow-up reports. |
| 4 | ERROR | Injected satellite registry flush failures. |
| 3 | ERROR | Recipes with an unbound tag, disabled until tags reload. |
| 52 | WARN | Project audit markers (`ARCE_STATION_*`, `ARCE_TRANSFER_*`, `ARCE_SATELLITE_*`) and three warnings from compatibility fixtures, each from a named test. |
| 109 | WARN | Environment: configuration files created by the run, ForgeGradle userdev classpath notices and one "Can't keep up!" warning. |

None of them is a product defect. Production must keep logging these paths at
their current levels: the refusals protect player data, and an operator has to see
them.

## Decision

1. For the GameTest server log only (`build/gametest/logs/latest.log`), an ERROR or
   WARN entry is accepted when it matches exactly one expectation in
   `scripts/gametest_expected_log.json`. A match means the same level and logger,
   a full match of the message pattern, a declared test batch and, when the
   expectation names one, a stack frame of the named test.
2. An ERROR expectation states the exact count for each batch. A WARN expectation
   states exact counts too, except an environment warning, which states an upper
   bound (`max`) and may limit the batches.
3. `scripts/check_gametest_log.py` fails on any FATAL entry, any unmatched entry,
   any entry that matches more than one expectation, any count that differs and a
   log without the GameTest completion banner.
4. Every expectation names its test and the reason. A change that adds, removes or
   moves a deliberate ERROR or WARN updates the manifest in the same commit, and the
   reviewer checks the reason.
5. Dedicated-server, native-harness and client logs keep section 6 unchanged. No
   ERROR is expected there.
6. This classifies test output. It is not a waiver: the entries stay in the log, and
   no assertion, budget, timeout or log level changes.

Root decides how the check joins the hosted workflow and whether section 6 gains a
reference to this decision. [HANDOFF-01](HANDOFF-01.md) proposes the workflow step.

## Alternatives

### A. Keep recording the totals as unwaived

- No work.
- The clean-log obligation can never close, and a new error is hidden among the
  known ones.

### B. Accept the totals (62 ERROR, 161 WARN)

- Simple.
- A new error that replaces a known one passes, and the record names no source.

### C. Lower production log levels or keep the tests from logging

- Cleaner logs.
- It changes production observability. The save refusals are thrown from event
  listeners by design, so Forge logs them; avoiding that would change the refusal
  mechanism.

### D. Markers written by each negative test around its expected errors

- Precise for a single test.
- It touches about 25 test classes, and tests of one batch run concurrently, so
  markers cannot bracket entries reliably.

### E. Declared manifest with batch and stack attribution (proposed)

- Each entry is tied to a batch and, where possible, to a test frame. New, missing
  and moved entries fail with line numbers.
- The manifest needs maintenance when negative tests or batches change.

## Consequences

### Positive

- A GameTest log can be judged clean with evidence instead of "unwaived".
- A new ERROR or WARN fails with its line, batch, logger and test frames.
- Exact counts also catch lost coverage: if a negative test stops reaching its
  refusal path, its count drops and the check fails.

### Negative

- Every new negative test that logs ERROR or WARN needs a manifest entry.
- Renaming a batch, or adding configuration keys (the bound of 99 defaulted keys),
  needs a manifest update.
- The checker reads the `latest.log` format. The Gradle console output abbreviates
  logger names and is rejected.

## Validation

- [x] Automated: 27 unit tests; all 11 deliberate defects in the checker were
  caught; the seven complete logs pass; four negative logs fail for the right
  reasons, including the hosted Linux CI log of 2026-10-07.
- [ ] Hosted CI step: needs Root's workflow change.
- [x] Dedicated server: not applicable; section 6 is unchanged.
- [x] Rollback: delete the manifest, checker, tests and workflow step. No production
  code, saved data or protocol is touched.

## Revisit when

- Forge or Minecraft changes the logger names or messages.
- The GameTest framework changes batch naming.
- A negative test needs a count that varies from run to run.
- The environment warnings change, for example with a new ForgeGradle.
