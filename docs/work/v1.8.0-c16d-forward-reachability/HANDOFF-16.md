# C16d forward 16: test-side evaluator handoff

2026-10-08. Writer: a fresh delegated Claude session (not Root, reviewer or
approver). Worktree `arce-v180-claude-forward-reachability16-20261008`,
base `12aaf4e9f82a4b6fd10702c331f47cf3ea8ceb42`. Authority: the limited
[CONTRACT-01](CONTRACT-01.md) as adopted by [ADOPTION-01](ADOPTION-01.md).
Nothing in this handoff has been compiled or executed.

## Completed scope (written only)

- `src/test/java/.../recipegraph/RecipeGraphReachability.java` (410 lines
  measured by a Grep line count): a package-private final JDK-only helper with
  `evaluate(nodes, roots, producers, limits)`, `DEFAULT_LIMITS`
  (16,384; 32,768; 64; 1,024; 1,048,576; 1,048,576; 8,388,608) and the
  nested `Producer`, `Limits`, `Code`, `Evaluation`, `Available`, `Success`
  and `Failure` types exactly as named in CONTRACT-01.
- `src/test/java/.../recipegraph/RecipeGraphReachabilityTest.java` (486 lines):
  17 JUnit 5 tests.
- This handoff file.

Uncompleted or excluded: no main-source caller, Forge or Minecraft use,
extractor, adapter, SCC, hard-lock diagnosis, report encoder, registry, asset,
producer rows or real recipe data. No full graph CONTRACT-02 work and no R2 to
R5 closure.

## Implementation notes

- Validation order: null containers and limits, container sizes, node IDs,
  roots, then producers in input order. Each owned array is allocated only
  after the size or aggregate check that guards it.
- Node indices follow `String.compareTo`, so ascending index is ascending ID.
  Producers are sorted by ID after validation.
- The forward pass uses an occurrence index by node (global slot numbers),
  per-producer missing-slot counters and a witness per slot. Work is charged
  before each node pop, raw occurrence visit, producer application and output
  visit, and checked as `++work > limit`.
- Ordered slot semantics are kept: `slotMembers` lists witnesses in the
  original slot order and repeated slots stay separate requirements.
- Results use unmodifiable views over owned `TreeMap`/`TreeSet` objects and
  `List.of` witness lists. Each producer's witness list is built once and
  shared by all outputs credited to it.

## Interpretations Root should review

1. An oversized `roots` set (`roots.size() > limits.nodes()`) returns
   `LIMIT_NODES` with subject `roots`. CONTRACT-01 requires a size check
   before traversal but names no code for this case.
2. Failure subjects are location strings such as `producers[2].slots[1][0]`.
   Positions in a `Set` are its iteration positions. The format is local to
   the implementation and never contains the rejected ID text.
3. `Owned` and `Owner` are private implementation types and are not part of
   the adopted interface.

No change to the adopted interface is proposed.

## Planned tests (written, not run)

- Limits: the default values, zero limits, and -1 or ceiling + 1 for every
  field.
- Empty and roots-only graphs. The two-edge chain costs exactly 9 units;
  `work(8)` overflows.
- AND/OR slots. Repeated slots and repeated members, with exact work 12 and
  overflow at 11.
- Witness choice: earlier rank first, then `String.compareTo`; original slot
  order is kept.
- Credit choice: earliest round first, then the lexically smallest producer
  ID; the result does not depend on input order.
- Multi-output credit. Outputs that are already available are still charged
  (13 units).
- Unseeded and seeded cycles. Equivalent OR encodings.
- 300 seeded random graphs compared with an independent synchronous-round
  reference. The reference derives work from the four counted events. Each
  graph is also checked under permuted input and exact or exact - 1 work
  limits.
- Ownership: results are immutable and detached, and the `Producer`
  constructor does not copy its lists.
- Every illegal-input code, with bounded subjects that do not echo the input.
- Exact and over-bound structural counts with small limits and at the default
  ceilings for nodes, producers, slots, members, member occurrences and output
  occurrences.

## Checks actually performed

Read, Glob, Grep, Write and Edit only. Grep line counts are 410 and 486. A Grep
scan found no characters outside tab, CR, LF and 0x20-0x7E after a fix. One
`é` escape had been written as a literal character; it is now
`(char) 0xe9`. Three test lines are 121-128 characters long, and no
checkstyle or `-Werror` configuration was found. File byte sizes were not
measured; the estimate is under 48 KiB each.

Not run: javac, JUnit, `./gradlew clean build`, `./gradlew test`,
`./gradlew runData`, `git diff --exit-code`, `./gradlew runGameTestServer`,
dedicated server, restart, client or any Git command. Compile errors,
assertion mistakes and fixture runtime or memory use (the ceiling fixtures
build about one million occurrences) are unverified.

## Gates and status

G0-G9 are all unrun and unclaimed. No ledger unit, C16d batch, R-021 risk,
save or hatch policy, or Required Gate is affected. v1.8 remains IN_PROGRESS.
Next step: Root commits the three paths, then a different agent reviews the
actual diff and runs the targeted JUnit class on committed source.

Rollback: delete the three new files; nothing else references them.
