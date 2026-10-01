# V170-GOV-01 / V170-CON-01 preparation verification

Date: 2026-10-01. Scope: the v1.7 development baseline and the endgame
contracts: the shared authority, protection, rate, energy, transit and audit
framework, the orbital laser drill, the railgun, the black-hole generator, the
area gravity controller and space-elevator logistics. The packet also holds the
legacy audit and the reference models. **No v1.7 production feature, world
migration, candidate or Required Gate is delivered here.**

Baseline: `ab10fb53a580e53a9a1c24097a487f2a7c54ad52` (v1.6 handoff).
Branch: `codex/v1.7.0-endgame-systems`. The canonical task tree is the
[implementation log](../v1.7.0-implementation-log.md). Inherited G0–G9 stay open.
No upstream code or asset is imported, and the public API does not change.

## Decisions

| ADR | Content | Result |
|---|---|---|
| [ADR-053](../../decisions/ADR-053-V170-DEVELOPMENT-BASELINE-EXCEPTION.md) | v1.7 development from `ab10fb5`, separate from inherited acceptance | ACCEPTED, revision 3 |
| [ADR-054](../../decisions/ADR-054-ENDGAME-AUTHORITY-PROTECTION-AND-AUDIT.md) | System switches, device identity and authority, protection chain, structure-only multiblocks, endpoint index and retirement, the `ENDGAME` root, the transit ledger, budgets and audit | ACCEPTED, revision 4 |
| [ADR-055](../../decisions/ADR-055-ORBITAL-LASER-DRILL.md) | Logical output by default, opt-in physical mining, layer-by-layer payment | ACCEPTED, revision 3 |
| [ADR-056](../../decisions/ADR-056-RAILGUN-CARGO-AND-TARGETING.md) | Endpoint-addressed cargo, energy quotes, exactly-once delivery through the ledger | ACCEPTED, revision 4 |
| [ADR-057](../../decisions/ADR-057-BLACK-HOLE-GENERATOR.md) | Fuel tables, a per-tick cap, the only device that creates energy | ACCEPTED, revision 3 |
| [ADR-058](../../decisions/ADR-058-AREA-GRAVITY-CONTROLLER.md) | Area fields, consent through trust lists, a 1.00 g cap inside stations | ACCEPTED, revision 3 |
| [ADR-059](../../decisions/ADR-059-SPACE-ELEVATOR-LOGISTICS.md) | Pairs built on ADR-045, rides with arrival pre-load tickets, the station guard, cargo | ACCEPTED, revision 4 |

Root recorded the acceptance on 2026-10-01 after the maintainer confirmed it in
the development session, following the reviews below. The reviews supply
findings and scoped verification, not maintainer authority or Gate approval. The
[coverage table](../v1.7.0-contract-coverage.md) maps every item of the version
document to a contract section, test level and chunk, or to a recorded
disposition. Deferred and rejected items are rows in `docs/PORTING_MATRIX.md`.

## Legacy audit

The [audit](../v1.7.0-legacy-audit.md) fetched 30 upstream files from the pinned
MIT commit `c5cd5af` into a Temp directory **outside** the repository. All 30
SHA-256 values equal `legacy-manifest/java-files.csv`. Per-file hashes are in
`upstream-audit-fetch.json` inside `root-checks.zip`; the upstream bytes are not
archived.

## Independent reviews

One general-purpose reviewer did four rounds and a confirmation round. It had
read-only repository access and wrote only to its own Temp directory. Each round
reviewed a `git archive` of a committed tree, ran the checker and the mutation
harness, and added its own probes and traces.

| Round | Commit | Critical / High / Medium / Low / Info | Verdicts | Dispositions |
|---|---|---|---|---|
| 1 | `10e3d2d` | 0 / 3 / 12 / 14 / 4 | 053 accept; 054–059 accept with changes | [review-01](review-01-dispositions.md) |
| 2 | `1b3923a` | 0 / 2 / 3 / 10 / 4 | 053, 055, 056, 057, 059 accept; 054, 058 with changes | [review-02](review-02-dispositions.md) |
| 3 | `9b57a61` | 0 / 1 / 2 / 7 / 2 | all accept except 054 (with changes) | [review-03](review-03-dispositions.md) |
| 4 | `1e7d6c8` | 0 / 0 / 0 / 5 / 2 | all accept | [review-04](review-04-dispositions.md) |
| Confirmation | `30ab77b` | 0 / 0 / 0 / 0 / 1 | all accept; every round-4 Low resolved | [review-04](review-04-dispositions.md) |

Every finding was answered in its own commit.

Round 1's main findings:
- a pruned transfer could be registered again from a restored source (R1-H1);
- a claimed reward withdrawn before the destination's chunk saved could be paid
  twice. This is the third-store duplicate that finding F02 of the external
  v1.3–v1.6 report found in ADR-051 (R1-H2);
- removal by causes other than a player's break was unspecified (R1-H3).

Round 2's main findings:
- a removed destination that returns could be paid twice after a redirect
  (R2-H1, answered with retirement);
- a source removal could lose registered records that were not yet durable
  (R2-H2).

Round 3's main findings:
- a player could force a retired destination's tombstone out (R3-H1, answered
  with the freeze rule);
- retirements without live state left claims unsettled (R3-M1);
- tombstone growth was unbounded (R3-M2).

Root's own model work also found two defects of earlier revisions, each fixed in
its own commit:
- `resolve` destroyed a retired destination's own move after a crash (found
  while closing R3-L6);
- a retired source discarded an unregistered payload (R4-L2, confirmed by the
  reviewer's trace).

[independent-review.zip](independent-review.zip) holds the five unmodified
reports, logs, probes and diffs, indexed in
[independent-review-files.json](independent-review-files.json). It leaves out
the reviewer's repository copies (`tree/`), its Minecraft/Forge bytecode
disassembly (`mc/`), fetched upstream sources (`upstream/`) and the mutation
copies of this checker (`mutations-out/`).

## Reference models and vectors

[examples.json](examples.json) and [check_examples.py](check_examples.py) hold 27
standard-library tests:
- SplitMix64 against its published seed-0 outputs, the `laser-v1` draws, laser
  costs and shaft geometry;
- the laser payment counter model, over 97,656 event sequences and five named cuts;
- railgun quotes, black-hole burns, gravity fields, jump heights and consent;
- the authority and protection orders, and elevator binds.

The protocol models explore every interleaving up to their crash and fault caps:

| Model | States | Result |
|---|---|---|
| `Transit` (source, ledger, destination), two payloads, two crashes | 72,618 | exactly once |
| `Transit`, one lost write | 24,011 / 27,578 | only the audited `SOURCE_ROLLBACK` residual |
| `Transit` with capped tombstone eviction | 1,385 / 33,057 | as above |
| `Delivery` (incoming gate, a player as third store) | 1,622 / 4,011 | only the container torn-save class |
| `Redirect` (retirement, freeze, eviction, `MISSING`, movers, resolve) | 8,122 | exactly once |

[check_mutations.py](check_mutations.py) changes one rule at a time. A control
must be clean and every mutation must be caught. A search that outgrows its
state limit counts as a failure. The final run catches all 26 mutations
(`mutations-final.log`).

State counts and draw lists are generated pins for the Java slices. They are not
independent evidence. These models check protocols, not the Java
implementation, NBT, permissions or performance.

## Commands actually executed

Windows host, Java `C:/Program Files/Java/jdk-17.0.7`, Gradle 8.8, Python 3.13.

| Command | Result |
|---|---|
| `gradlew clean build test runData runGameTestServer --no-daemon` on `30ab77b` (later commits change documentation only) | Exit 0, 200 s. **290 required GameTests** passed. `:test` was **FROM-CACHE**. `git diff --exit-code` after DataGen: exit 0 |
| `gradlew test --rerun --no-daemon` | Exit 0, 135 s. **1,157 JUnit tests / 218 suites executed**, 0 failures, errors or skips |
| `python -B docs/work/v1.7.0-preparation/check_examples.py` | Final: exit 0, 27 tests (`examples-final.log`); 17 earlier runs retained |
| `python -B docs/work/v1.7.0-preparation/check_mutations.py <Temp dir>` | Final: exit 0, control clean, 26 of 26 caught; earlier runs retained, including those with failing pattern matches |
| `python -B -m unittest tests.test_v1plus_planning` | Before this file existed: exit 1, 2 failures on the link to it (`validation-01-pre-packet.log`). Final, after packaging: see below |
| `python -B scripts/validate_repository.py --require-approved-identity` | Final, after packaging: see below |
| `python -B scripts/validate_v1plus_planning.py` | Final, after packaging: see below |
| `python -B scripts/validate_bootstrap_provenance.py` | Exit 0 |
| Temp `fetch_upstream.py` | 30 of 30 manifest hashes match |

Final validator results after packaging: `validate_repository.py` exit 0;
`validate_v1plus_planning.py` exit 0; `tests.test_v1plus_planning` exit 0. They
need the finished packet, so they ran after packaging. Their log is
`packaging/out/validation-02.log` in the Temp evidence directory, which is
deliberately outside `root-checks.zip`.

The three rebuilt JARs are **byte-identical** to the v1.6 C9 build
(`artifact-comparison.json`), and no implementation file changed
(`implementation-files.json` is empty). The runtime stays `1.20.1-1.6.0-dev`.

The full build log has **18 ERROR lines and 0 FATAL**. They come from
intentional failure-injection GameTests (migration save failures, satellite
launch failures, test-region chunk saves) and the missing `server.properties`.
This is not a clean-log claim. Startup also reports running 3,350 ms (67 ticks)
behind. Passing functional checks do not turn that into performance acceptance.

## Retained defects in the evidence trail

- Several Python patch scripts failed on Git Bash heredoc escaping. They stopped
  on their own exact-match asserts and wrote nothing; the corrected scripts are in
  `tools/`.
- The first mutation run after the R4-L2 model change stopped with a pattern
  that no longer matched (exit 1). Its log was overwritten by the corrected run in
  `mutations-r4l2.log`, so only the corrected run is retained.
- Two orphaned model explorations grew to about 35 GB and were killed. Later
  runs are bounded by timeouts and state limits.
- The auto-mode permission classifier blocked the acceptance edits twice. Nothing
  was written in between, until the maintainer restarted the session without
  that check.
- External finding F01 (satellite registry recovery bounds) is fixed on
  `fix/v1.6.0-recovery-account-bounds` (`eab795a`) after this baseline. It is not
  part of this packet and is integrated before C11's runtime work.

## Not verified

- Runtime behaviour, the flush and tick budgets, and churn rates. These need C13
  on a running server.
- Third-party block movers and their opt-out tags. The protocol no longer
  relies on them.
- The operator guide, which C13 writes.

Next chunk: C11 (ADR-054 framework, ADR-055 laser drill, ADR-058 gravity
controller) in [COMPLETION-PLAN](../../status/COMPLETION-PLAN.md).
