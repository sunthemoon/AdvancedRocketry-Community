# C16a-06-S1 Pump packaged recovery fixture

Status: verified (clean-stop/restart scope). Owner: root integrator. Version: v1.8.0.

Observable outcome: a fresh copy of the historical world saves Pump resources
and a real partially advanced search, then reconstructs the transient search
after restart while preserving unsupported roots and exact resource metadata.
This is clean-stop/restart evidence, not arbitrary-crash Item/Fluid atomicity.

Scope: one opt-in privileged console-only fixed-cell fixture command, its
bounded seed/report regressions, root command registration, and a reviewed
copy-only Python harness. No ordinary player control, C2S input, configurable
coordinates, chunk loading, protection bypass, resource schema changes,
assets, recipe edits, artificial search frontier persistence or later content.
Production Pump search and protection policy remain unchanged.

Dependencies: reviewed Pump revision-2 implementation and root lifecycle save
guard. Explicit FULL loading belongs to the disposable harness, not the command.
At most 128 fixed positions are checked before any fixture mutation; all are
in chunk 13,13. Nonempty fixtures refuse preparation without replacing data.
Supported/unsupported banks and the one persisted fixture-owner UUID are
auditable inputs, not production ownership or recovery APIs.

Verification: focused seed/boundary tests, independent exact-diff review,
unchanged full root build/DataGen/GameTest, then a fresh native historical-world
copy with exact JAR identities, typed native roots, source identity and same-world
restart checks. Retain failed attempts. No Gate or real-client proof is implied.

- [x] Bounded opt-in fixture adapter and focused tests.
- [x] Harness and actual-diff independent review.
- [x] Mandatory root regression and exact packaged native restart evidence.

Revision checkpoint: original five seed/bound tests pass, but independent
primary bytecode inspection finds one Medium in authority: permission 2 plus
no entity admits native command blocks. Revision 2 requires native-console
permission 4, no entity and its exact `Server` text name, and adds a regression
for command blocks, players and Rcon. Original source/test/report evidence is
retained. Independent revised replay and native execution remain required.

Harness integration review finds a second Medium: `NO_ENERGY` appears while
the successful source operation still has a decreasing cooldown, before the
required stable terminal snapshot. Revision 3 reports WAIT until both
`NO_ENERGY` and cooldown zero hold, with one new regression and unchanged
original six assertions. The same 60-second polling/tick budgets remain.

Actual checkpoint: revision 3 independent replay passes seven Java tests;
unchanged harness passes 25 Python tests, plus the modeled WAIT-to-terminal
counterexample. Root native attempt 1 fails before any world binding because
the launcher uses PowerShell's reserved `$Host` variable; its log/exit/command
are retained. Corrected attempt 2 runs all four phases and exits 0 in 84.8s
against main SHA-256
`58a5ab97a892c4f54f9803d64b6f407eb5c533103be0a758ab64b11e922d3bb0`.
Typed resource roots, one real search checkpoint, 100-FE source consumption,
1,000-mB lava, two clean same-world restarts and unchanged historical inputs
pass. Independent native audit remains in progress. Full root units pass
1,622 tests; the full GameTest run fails 12 Signature-related cases. Therefore
the final integrated-regression checkbox remains open. No forced-stop or
arbitrary-crash atomicity, client, performance or Required Gate is claimed.

Fresh candidate11 checkpoint:required Root regression passes1,638 JUnit/all459
GT, DataGen written0. Pump04 passes the same four actual phases on frozen main
ea311ed0… . The [independent result audit](../v1.8.0-c16a-integration/reviews/INTEGRATION11-EVIDENCE-AUDIT-01.md)
re-decodes exact stopped roots/source state and historical/input hashes with no
evidence inconsistency. Its audit is not a second native replay. See
[development verification](VERIFICATION.md) for the admitted scope and limits.
