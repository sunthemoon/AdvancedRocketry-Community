# TASK W269: a campaign-only release-test gate for the bay exit's two-client V2 evidence (R298)

Root: Claude. Date: 2026-11-03. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w269` (new; branch `codex/v1.8.0-w269` from the shared head named in your prompt) |
| Inputs | `records/W254-V2-CHECKLIST.md` section 0.1 (the open choice), `records/W230-DESIGN.md` section 7, `records/W254.md`, ADR-065 section 10, `EscrowTestEndpoints.enabled`, `ReleaseTestPlayers` and `BayReleaseTestGateTest` |
| Record | `docs/work/v1.8.0-root-queue/records/W269.md`, a one-page design note `records/W269-DESIGN.md` committed before the code; logs under `D:/GitHub/ARCE-Task-Evidence/root-queue/W269/` |

**Root decision R298**, under the owner's standing delegation for design points, answering W254's checklist 0.1:
option 1. The bay exit's V2 campaign runs the existing gated fixture with two real clients through a campaign-only
variant of the release-test gate. Bay admission stays off (owner decision Q1, R243); option 2 (turning admission on in
a campaign world) is not used. The campaign itself, and when it runs, remain the owner's.

Today `EscrowTestEndpoints.enabled` opens on a dedicated server only when the release-test hooks property is set, RCON
is off and the release-test facility created every connected player, so a real client closes it. The variant:

1. **Opt-in, never by default.** A separate system property names exactly two player UUIDs. It works only together
   with the existing hooks property, on a dedicated server with RCON off. Any other real player connected closes the
   gate as today. Unset, malformed, duplicate or more than two UUIDs: the gate behaves exactly as now, and a malformed
   value stops startup with a clear message rather than being ignored.
2. **A disposable world only.** Decide and justify, in the design note, a check that the world is a disposable
   campaign copy (for example a marker file the campaign creates in the world folder), so the property cannot act on
   an ordinary world by mistake.
3. **Visible.** One INFO line at startup and one each time a named UUID connects, naming the property, the two UUIDs
   and the world; no line when unset.
4. **Narrow.** It changes only who may be connected while the gate is open. It does not change what the fixture
   commands do, add commands, touch admission switches, persistence or packets, or affect GameTest servers.
5. **Tests.** Extend `BayReleaseTestGateTest` (or add a sibling) so that: unset keeps today's behaviour; the two named
   clients plus test players open it only with hooks, dedicated, RCON off and the marker; a third real player, a
   missing marker, RCON on or hooks off each close it; malformed values fail startup. Failing first where possible.
6. **The checklist.** Update `records/W254-V2-CHECKLIST.md` section 0.1 to state the decision and the exact property,
   marker and log lines, and draft (do not apply) the ADR-065 section 10 note text for Root.

## Verification

The gate tests, the bay and escrow unit tests, the whole unit suite, the full GameTest run (heavy-JVM lock), and a
native dedicated-server smoke on the packaged JAR showing: the gate closed with the property unset; the startup line
with it set on a marked world; startup refused on a malformed value; and the gate closed on an unmarked world. The
repository validator.

Commit on the branch; do not push. Root reviews and pushes for a sharded run, and a fresh session of the other engine
reviews before the merge.

## Not in this task

Running the V2 campaign or any real client, bay or cargo admission, the fixture commands' behaviour, other gates, the
owner's risk decisions (W203-Q4), and the ADR text itself (Root applies the drafted note).
