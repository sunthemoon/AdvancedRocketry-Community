# ADR-072 - Declared expectations for v1.8 GameTest logs

```yaml
status: ACCEPTED
date: 2026-10-10
owner: sunthemoon
deciders: [Codex Root under owner delegated review authority]
target_version: v1.8.0
scope: Forge GameTest userdev latest.log only
supersedes: ADR-PROPOSAL-01 in v1.8.0-gametest-log-expectations
```

## Authority and context

The owner requested independent review and, if acceptable, branch integration in
the interactive Codex channel on 2026-10-10. The supplied scope explicitly asks:
"审核这两个提交（e2ed43eb 代码、30419f76 记录），决定是否接受 ADR。"
Root accepts the narrowly defined classification below, not the original
proposal's blanket conclusion that no observed entry can represent a defect.
See the [integration record](../work/v1.8.0-gametest-log-expectations/ROOT-INTEGRATION-01.md).

Negative tests exercise logged refusal, injected save failure, version rejection
and recovery paths. Aggregate totals alone cannot distinguish a new error from
an old one. The [original proposal](../work/v1.8.0-gametest-log-expectations/ADR-PROPOSAL-01.md)
and handoff remain historical implementation records, not current normative rules.
The original source reviews found missing log-finalization validation and weak
attribution. Root's successor narrows these boundaries before integration.

## Decision

1. Audit only the complete `build/gametest/logs/latest.log` produced by
   `runGameTestServer` in the pinned checkout. Keep native task success and test
   assertions independent and mandatory. Console output is not this format.
2. Require one GameTest userdev launch, ordered batches, one completion banner,
   one successful required-test summary, and normal GameTest shutdown as the last
   nonempty record. Reject duplicate or misplaced markers, failed or missing
   summaries, malformed header-shaped lines and incomplete final lines.
   The required-test count must be positive and no greater than the completed
   count; optional tests may account for the difference. This is structural log
   completeness, not proof of storage durability or exhaustive test selection.
3. Every ERROR or WARN must match exactly one reviewed manifest rule: exact level
   and logger, full message pattern, declared batch, and any specified structural
   Java stack frame and throwable. FATAL always fails. Exact per-batch counts
   apply to all ERROR and test-driven WARN rules. A lost or extra entry fails.
4. Match available failure-handler frames rather than arbitrary exception text.
   Precision ChunkMap failures additionally require the exact injected throwable.
   Distinct recipe IDs and transfer recovery cases have separate exact counts;
   one case cannot replace another while preserving an aggregate count.
5. Only the six documented environment WARN categories use diagnostic count
   ceilings. Startup categories stay in startup. Host-lag recognition does not
   establish a bound on delay duration, tick time or reliability and does not
   waive any existing performance budget. A novel shape or an exceeded ceiling
   fails rather than automatically expanding a rule.
6. A commit that adds, removes or relocates an intentionally logged test event
   updates its rule, named test and explanation in that same commit. Review the
   source and run outcome; never add a rule merely because a regression emitted
   an error. The checker is not an authorization to ignore unexpected failures.

Batch membership and reviewed dynamic fields are diagnostic attribution, not
cryptographic or causal authentication. A same-shaped event from the same test
cannot always be distinguished by logs alone; native assertions and source
review remain necessary. Do not describe the raw log as ERROR-free.

## Unchanged obligations and expiry

The [master test plan](../05-MASTER-TEST-PLAN.md) section 6 remains unchanged for
dedicated-server, native-harness and client logs. This decision does not classify
their errors, lower production logging, remove assertions, expand a timeout,
reduce a resource/performance budget or grant a Required Gate exemption.

In particular, [R-021](../11-RISK-REGISTER.md) remains unaccepted. Recognizing
negative GameTest save refusals does not accept whole-chunk refusal, unload or
cross-store data loss, unbounded production logging/retries, or incomplete native
shutdown/restart recovery. Those tests and operational decisions remain open.

This v1.8 development-only policy must be reviewed before applying it to a
different version, Forge baseline or test cohort. It expires at the v1.8 release
acceptance review unless the owner reaffirms the precise source/rule inventory.
Rollback removes the checker, manifest, its tests and workflow command; product
code, persistence and protocol are unaffected. No Gate is approved by this ADR.

## Verification and operation

Root independently reran the original 27 tests and audited seven positive and
four negative retained logs. Independent original-source audits recorded parser
and attribution findings. Root's successor unit and real-log checks, exact
committed SHA, review disposition and hosted CI result are recorded separately
in the integration record; their results must not be inferred from this decision.

The hosted workflow first runs checker unit tests, then invokes the checker after
the native GameTest command under Bash's existing `-e -o pipefail` behavior.
Either native failure or checker failure fails the step. If the native command
fails, the subsequent checker command is skipped; raw evidence is still uploaded
by the existing `always()` step. A current Linux qualification requires an actual
instrumented hosted run, not a replay of an older Linux log.

Use `python -B scripts/check_gametest_log.py <latest.log> --inventory` to inspect
unclassified entries. Inventory mode is diagnostic and is not an acceptance check.
