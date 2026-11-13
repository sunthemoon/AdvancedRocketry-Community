# TASK W361: per-fluid signed accounting, Slice A (pure account model, codec and size proof)

Root: Claude. Date: 2026-11-13. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w361`, branch `codex/v1.8.0-w361`, at the shared head |
| Decision | Root decision R442 (QUEUE row W361) |
| Design | [W357-DESIGN](records/W357-DESIGN.md), revised after its review (W357F), with Root's answers R440 and R441 in its section 10. Slice A is its section 8, row A. |
| Record | `docs/work/v1.8.0-root-queue/records/W361.md` |
| Write scope | New classes `EscrowFluidAccount` and `EscrowFluidAccountCodec` (and small value types they need) in the `resourceescrow` package, their new unit tests under `src/test`, and the record. Nothing else. |

## Why

The owner asked for per-fluid signed accounting before any switch decision (2026-11-13). The design is approved with
its review's changes. Slice A comes first and alone, because its measurements decide the account row ceiling and the
capacity numbers before the saved form changes.

## What to do

1. **Account model.** Implement the pure, Minecraft-free model of the design's sections 2 and 3:
   - keys (fluid ID plus tag digest, with absent tag distinct from an empty one);
   - baseline entries and planned signed deltas;
   - the balance and outstanding-obligation identities;
   - knowledge states (PLANNED, KNOWN, UNKNOWN_BASELINE, UNKNOWN_ENDPOINT, NOT_APPLICABLE);
   - signed discrepancies, including a half-applied transfer under ACCEPT_CURRENT;
   - chain projection with later-prefix subtraction, for every endpoint that has a prefix chain;
   - component deduplication by the full typed endpoint key;
   - shared adjustment ownership by the lowest-sequence member.
   Use checked `long` arithmetic. An unknown contribution is never zero.
2. **Codec.** Implement the account row codec of section 4.1 (schema 1, the closed short-key form) with every bound of
   section 5: count, bytes, depth, nodes, key cardinality, member list. Refuse duplicate keys, unknown fields,
   malformed digests, negative amounts, excess cardinality and an incorrect balance. Use bounded results, not
   exceptions, for ordinary refusals.
3. **Tests.** Cover the cases of section 7.1 that belong to the model and the codec:
   - all four partial-application combinations;
   - q = 1 and q = 1,000;
   - two fuel banks with aggregation per endpoint and key;
   - both cargo directions;
   - empty units;
   - tag differences, including an absent tag against an empty tag;
   - the same total with different fluids;
   - gains, losses and overflow;
   - missing quantities;
   - cardinalities and duplicate keys;
   - chains on the same bank, on alternating banks and through a cargo device;
   - a deduplicated component;
   - shared adjustment ownership without a double count;
   - a 64-member owner row within its bound.
4. **Measurements (the main deliverable).** With the real codec and the existing real encoders, measure:
   - the worst account row, giving its bytes, depth and nodes;
   - the largest real component;
   - the new receipt size of the design's section 5 for the W114 fixtures;
   - the operation's added bytes.
   From these, propose the smallest row ceiling that fits (at most 4,096 bytes). Compute the new value of every
   pinned count the format moves: `EscrowCargoCapacityTest`'s 61 concurrent operations, the exact 1 MiB root of
   `EscrowFlightBenchmarkCeilingTest`, and the byte refusal point of the saturation families
   `w336-saturation-fluid-load`, `-fluid-unload` and `-fuel`. Give each with the method used. Do not change those tests
   or their pinned values: Root accepts new numbers in a decision row first (R441).

## Rules

- Activate nothing. No ledger, operation, intent, receipt, writer, migration, admission or production path calls
  the new classes yet, and no existing test, pinned value or expectation changes (AGENTS.md section 5). Show this
  with `git diff --stat` against the base.
- Run the new tests and the full unit suite (`gradlew test`, with JAVA_HOME set to
  `C:\Program Files\Java\jdk-17.0.7`, as `run.cmd` does). Report the totals.
- Commit on your branch in reviewable commits, with messages in the style of AGENTS.md section 6. Do not push.
- The record gives what was done, the commits, the commands and results, the measurements with their method, the
  proposed row ceiling and the new pinned values, and any bound that could not hold. Its last line, and the last line
  of your answer, is the path of `records/W361.md`.
