# ADR-046 — v1.5 station, team and warp controls are server commands

```yaml
status: ACCEPTED
revision: 2
date: 2026-09-30
deciders: [sunthemoon]
owner: sunthemoon
accepted_by: sunthemoon
accepted_at: 2026-09-30
acceptance_basis: maintainer direction to finish v1.5 with recommended solutions, after independent contract review ("accept with changes"); all nine required changes (8-16) applied in this revision
target_version: v1.5.0
development_dependency: ADR-039, ADR-040, ADR-041, ADR-044, ADR-045
amends: ADR-039 (the v1.5 "UI/security" outcome is the server command interface, with no screen; see the disposition below)
implements: V150-UI-01..03 targets
```

## Context

The v1.5 plan asks for:
- server-authoritative UI, feedback and permissions (§6.9);
- security tests (§12);
- a "multiplayer permission report" (§15);
- GUI-scale and multiplayer visual checks when a screen exists (§11).

The implementation log splits this into:
- UI-01, actions and protocol;
- UI-02, presentation;
- UI-03, stale, forged, unloaded and multiplayer authority.

The station, team and warp actions are Brigadier commands under `/arce station`.
Expansion, gravity and warp accept only the connected player's own non-silent
source (ADR-040/041/044). ADR-044 §7 chose commands as the warp interface. Two
other surfaces touch stations:
- block placement and breaking in a region (BUILD);
- the existing rocket flight C2S packet, whose targets can name a station
  (VISIT).

## Relation to ADR-039

ADR-039 lists "UI/security" as a v1.5 outcome and says metadata or a diagnostic
command alone cannot substitute for it. This record **amends** that outcome: for
v1.5, the UI is the server command interface, and UI/security is met by
behavioural verification (UI-01..03 below), not by this record. This record
defines targets and closes nothing by itself.

**No-screen disposition** (maintainer-accepted):
- **Owner:** sunthemoon.
- **Reason:** every v1.5 control is a short, bounded server command; a screen
  would add a versioned client/server payload and client scope without new
  authority.
- **Reconsider:** no later than v1.8.0 (classic content). The PORTING_MATRIX
  row "station/warp control screens" is marked `DEFERRED` until then.
- **Recovery:** a future screen brings its own ADR, a bounded and versioned
  payload, and the same server checks.

## Decision

### UI-01: actions and protocol

- The station, team and warp controls add **no packet**. Every input is a
  Brigadier argument parsed and bounded on the server:
  - UUIDs;
  - resource locations;
  - integers with ranges;
  - the literal delete token;
  - vanilla's 256-character command limit.

  Every cost, balance, bound and result is computed on the server.
- This does **not** decide ORBIT-03. The per-station sky may add a bounded S2C
  context under its own ADR and protocol bump.
- **Verification:** a test pins all four channel protocol versions (life
  support 1, celestial 2, flight 8, visual 1) and each channel's registered
  message list against a committed table. The table changes only with an ADR.
  The plan YAML records `network_protocol` at ACC.

  > Amended on 2026-09-30 by [ADR-047](ADR-047-PER-STATION-SKY-CONTEXT.md) (the
  > ORBIT-03 carve-out): the celestial channel is now protocol 3, with message 1, the
  > station sky context. The pinned table is updated accordingly.

### UI-02: presentation

- Feedback uses chat and the action bar:
  - every rejection names its reason, with numbers where relevant (cost and
    balance);
  - quotes show the station, bodies, class, cost, balance and warnings;
  - `status` and `environment` change nothing. `status` shows the folded
    balance and the pending credit without folding (ADR-044 revision 4, §2).
- Warp **start, commit and abort always go to chat**. Only the 5/3/2/1-second
  countdown uses the action bar.
- **Verification:** GameTests capture the actual replies for the station and
  warp codes they exercise.
- **Known issue:** messages are literal English.
  - Owner: sunthemoon.
  - Expires no later than v1.8.0.
  - Recovery: translatable keys in the `advancedrocketrycommunity_v150`
    namespace, and tests that assert `StationManagementCode` tokens instead of
    prose.
- Also recorded as gaps:
  - notices do not name the station;
  - visitors in the region are not notified;
  - offline members get no notice at login.

### UI-03: authority matrix and the multiplayer permission report

**Actors:**

| Actor | Construction |
|---|---|
| owner, member, invitee, outsider | connected mock players (embedded channel), standing in the region |
| operator, operator outside the station | the same, with permission level from the server ops list |
| owner of another station | cross-station authority |
| removed member; previous owner after a transfer; de-opped operator | state changes applied first |
| console | server command source |
| `/execute as <owner>` | console-derived source; covers command blocks, whose source is not the player |
| owner's silent own source | `withSuppressedOutput()`; covers `/function` and advancement rewards |
| FakePlayer with the owner's UUID | `FakePlayer` |
| stale player object | the owner's object after logout |

**Expected outcomes.** The matrix GameTest asserts every cell against this table
and fails on any difference. The log line is written after the assertion and
carries the expected and the actual value.

| Action | Allowed | Everyone else gets |
|---|---|---|
| expand, expand confirm, gravity, warp request/confirm/cancel | the owner or an operator, only through their own connected, non-silent source, standing in the region (warp request: also looking at a core) | `NOT_LOCAL_PLAYER` (console-derived, silent, FakePlayer or stale), `UNAUTHORIZED` (team or outsider), `NOT_IN_STATION` (outside the region), or Brigadier `ERROR_NOT_PLAYER` (console) |
| warp status | owner, member or operator in the region | `UNAUTHORIZED` or `NOT_IN_STATION` |
| environment | anyone in a region. Identity is shown to the owner, members and operators only | none (read-only) |
| invite, remove (online player or `uuid <member>`) | the station's owner, or permission level 2, identified by the command's player entity. Team commands are membership management, not local region actions, so `/execute as`, a silent source and a FakePlayer that resolve to the owner are allowed, as in v0.7 | "Station action rejected: …" |
| accept, decline | the invited player | "Station invitation is missing" |
| list | anyone; non-operators see only their own stations | none |
| admin inspect, dump, create, delete, transfer, recover-reservations, warp, elevator check | permission level 2 | Brigadier unknown-command rejection |
| BUILD (place or break in a region) | owner, member or operator | cancelled with a denial notice |
| rocket flight C2S with a station source or destination | owner, member or operator (VISIT) | `UNAUTHORIZED` |

**Cases:**
- **Forged or stale identifiers:**
  - an unknown station UUID;
  - another station's confirmation;
  - a confirmation after a change (`STATION_CHANGED`);
  - a consumed confirmation (replay);
  - an unknown or non-orbitable target;
  - a flight to a private or deleted station.
- **Contention:** different stations never interfere. On one station the first
  commit wins and the others get `STATION_CHANGED` or `WARP_COUNTDOWN_ACTIVE`.
- **Caps:**
  - 128 confirmations;
  - 64 countdowns;
  - 32 members and 32 invitations;
  - inviting yourself, the owner or a member.

**Chunk invariant:** no player-issued action and no read-only admin action
loads or generates a chunk; the loaded-chunk count is unchanged in every Level.
Operator `create` and `delete` load exactly the platform chunks of the named
cell (the 289-block template), and the matrix asserts that count.

**Rate bounds:**
- Non-operator players are bound by vanilla `detectRateSpam`: every chat command
  adds 20 to a counter that decays by 1 per tick, and more than 200 disconnects a
  non-operator. That is about one command per 20 ticks sustained, with bursts of
  about 10.
- Operators are trusted and unbounded.
- Expansion confirmations are one-shot. Gravity and warp have 100-tick
  per-station cooldowns, and warp has confirmation and countdown caps with one
  commit per tick.
- Team commands flush the dirty SavedData. At 4,096 stations with balances,
  encoding the station registry takes about 20 ms; the checked write, with
  fsync, has a median of about 194 ms (WARP-05 scale test). With the vanilla
  limiter, one non-operator can cause at most about one flush per second. No
  per-station team cooldown is added.
- Each command writes at most two audit lines.

**Offline member removal:** `/arce station remove <station_id> uuid <member_uuid>`
removes a member who is offline. It is bounded and adds no packet.

**Report.** The multiplayer permission report has three levels:
- **A1:** the full matrix GameTest.
- **S1:** a named subset on the packaged JAR and a native dedicated server,
  using the fixture probe's connected players. It covers owner, member,
  invitee and outsider on expand, warp request and status, team invite and
  remove, and admin.
- **V2:** a named subset with two real clients: outsider and member rejections
  visible, and countdown/commit received. It stays open for ACC-02.

The report records:
- identity: commit, JAR SHA-256, versions, commands and exit codes;
- the table revision;
- how each actor is constructed;
- one row per cell: action, actor, precondition, expected, actual, PASS/FAIL,
  whether the registry, balances and orbit were changed, and the loaded-chunk
  delta per Level;
- separate tables for forged/stale, contention, caps/replay and rate;
- totals;
- known gaps;
- a statement that it is not a Gate approval.

The development copy goes in `docs/work/v1.5.0-*/`; the candidate-bound copy
goes in `docs/releases/v1.5.0/`.

## Consequences

- No protocol or save-schema change.
- §11.5 GUI scale is not applicable under the no-screen disposition. The V1/V2
  legibility and multiplayer checks stay open for ACC-02.
- Players without command familiarity have no visual controls in v1.5. That is a
  recorded usability gap, not a safety gap.

## Rollback

Documentation, plus additive tests and one additive command branch. Removing the
tests or the `uuid` branch changes no stored data.

## Review history

- **Revision 1** (`2900e66`): an independent contract review found two High
  findings (UI closed by documentation; a matrix with no oracle and at the wrong
  evidence level), five Medium, three Low and one Info.
- **Revision 2** applies required changes 8-16:
  - the ADR-039 amendment and the no-screen disposition;
  - the expected-outcome table and the A1/S1/V2 report;
  - the protocol claim scoped, with an ORBIT-03 carve-out and all four channels
    pinned;
  - the completed matrix, including the flight C2S path and BUILD/VISIT;
  - the scoped chunk invariant;
  - rate bounds;
  - offline removal;
  - the localization waiver;
  - the presentation rules.

  The report is archived in `docs/work/v1.5.0-review-closure/`. Acceptance is not
  a Gate approval.
