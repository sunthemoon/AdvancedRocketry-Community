# W269 design: two-client disposable campaign gate

Date: 2026-11-03. Implementer: Codex worker. Base: `2f24c7542ecf8a73a6e30228f02088b18dea67d2`.
Authority: Root decision R298 in [TASK-W269](../TASK-W269-V2-CAMPAIGN-GATE.md), choosing W254 section 0.1 option 1.
This note precedes implementation; it is not an ADR amendment or a V2 result.

## Selection and boundary

The opt-in JVM property is `advancedrocketrycommunity.bayV2CampaignPlayers`. Its value is exactly two distinct,
canonical hyphenated UUIDs separated by one comma (case-insensitive hexadecimal; no whitespace). Absence preserves
the existing gate. Empty, abbreviated, duplicate, extra or otherwise malformed values refuse dedicated-server
startup with a bounded message naming the property and required format, without echoing arbitrary input. Validation
also runs when hooks are off, so a typo cannot silently select another policy. GameTest and integrated servers ignore
this property entirely; neither their startup nor their gate is changed.

The world root must contain a regular, non-symlink file `.arce-bay-v2-campaign`, whose exact ASCII bytes are
`ARCE_BAY_V2_DISPOSABLE_WORLD_V1\n` (LF, no BOM). The campaign creates it only after copying its world. Startup checks
the small fixed size and contents, never creates it, and treats unreadable, absent, nonregular or different contents
as unmarked. This explicit, world-local acknowledgment prevents a JVM property alone from opening an ordinary world.
It is an operator safety interlock, not cryptographic proof that a world was copied: never mark the source world.
The marker is sampled once at startup; changing it requires a restart. It is not a saved-data schema or migration.

Only a native dedicated server with hooks on, RCON off and that marker can allow the two selected UUIDs alongside
exact facility-owned player/connection pairs. Any other connected player closes the gate. Existing owner-thread and
GameTest checks remain. Selection is immutable per server start in a lifecycle-cleared server-keyed map; it holds
no Levels or players. No selection means no marker I/O in gate queries. Facility ownership is never conferred on
the named clients: test-player login/respawn/pumping retains its existing identity checks.

## Visibility

For a set, valid property, startup emits exactly one INFO message:
`ARCE_BAY_V2_CAMPAIGN startup property=<property> clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=<boolean>`.
Each named UUID login emits exactly one INFO message:
`ARCE_BAY_V2_CAMPAIGN connect property=<property> clients=<uuid1>,<uuid2> world=<absolute-world-root> marker=<boolean> player=<uuid>`.
These describe selection, not a promise the gate is open: hooks, RCON, thread and all connections are still checked.
Unset emits neither line. Malformed selection throws before normal startup and emits neither campaign INFO line.

## Write scope, verification and non-goals

Write scope: the campaign selector and its event wiring, `EscrowTestEndpoints`, a connection-policy overload in
`ReleaseTestPlayers`, focused unit tests, a packaged-server smoke harness with its tests if needed, this design,
[W254-V2-CHECKLIST](W254-V2-CHECKLIST.md) section 0.1 and the W269 record. Temporary scripts and logs belong under
`D:/GitHub/ARCE-Task-Evidence/root-queue/W269/`. No status files, ledger, build scripts, ADR, packets or persistence
are edited. Original implementation only; no upstream files or assets are imported.

First add failing gate tests, then implement and run the gate/bay/escrow tests and complete unit suite. Commit the
tested source and build its packaged JAR. Under the heavy-JVM lock, run the full GameTest/checker and native smoke:
unset with an unowned test connection refuses the fixture; marked selection logs; malformed selection refuses startup;
unmarked selection with a named test connection refuses the fixture. Test connections are not real clients or V2.
Run the strict repository validator and diff checks. Keep command results, source SHA and JAR hash outside Git;
commit a short record. Root arranges the other-engine review, integration and hosted regression.

Non-goals: real clients, the V2 campaign, LAN gate changes, fixture behavior/commands, bay/cargo admission, owner risk
decisions, other release-test gates and ADR decision text. Draft the section 10 note for Root without applying it.
