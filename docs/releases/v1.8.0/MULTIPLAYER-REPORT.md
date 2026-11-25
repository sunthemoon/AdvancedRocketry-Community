# v1.8.0 multiplayer report — C6 draft

**DRAFT; every C6 V2 result PENDING.** Test candidate
`c8fa16e16be87554f26c402546733df6555d15e6`, host JAR SHA-256
`9f7e9d1ef708e27fa5778ca3c72ea3b3605f889465ae0d0d1054a890eb808dd4`.
Two fake players or two offline profiles are not V2. Server-only GameTests and
historical local multiplayer observations are not candidate acceptance.

## Session prerequisites and identity

- Two independent real clients signed into premium accounts by the owner; A
  is owner, B passenger/non-owner and station-member variants. Record UUIDs in
  private campaign configuration, never passwords/tokens. Actual testers,
  UUID configuration hash, machines/GPU/drivers and session UTCs: **PENDING**.
- Packaged server: `online-mode=true`, `white-list=true`,
  `enforce-whitelist=true`, `enable-rcon=false`; restricted loopback/LAN/SSH
  reachability, synchronized UTC clocks, exact Forge/JEI/config/JAR hashes on
  both clients and server. Account/equipment arrangements O5/O10/O12 in the
  [owner packet](../../work/v1.8.0-root-queue/records/OWNER-FINAL-PACKET-DRAFT.md)
  are PENDING. Some simultaneous actions need a second person, not one operator
  guessing two timings.
- V2 is phase 4, after relevant V1/DS failures are resolved; performance phase 5
  follows it. R682 has decided P4–P9: all six admissions stay off in v1.8.
  A separately approved enabling commit changes the candidate and requires
  requalification; it cannot silently keep the C6 identity.
- Bay/cargo/fuel scenarios use R298's disposable marked world, with the
  [W254 checklist](../../work/v1.8.0-root-queue/records/W254-V2-CHECKLIST.md)
  and [W269 gate](../../work/v1.8.0-root-queue/records/W269.md): two permitted
  account UUIDs, no RCON and required startup/login checks. Fuel additionally
  needs its explicit gated loader property. A third client voids that marked
  session and closes the gate; repeat it, rather than retaining a partial pass.

The 43 phase 2 sessions in [W471](../../work/v1.8.0-root-queue/records/W471.md)
and [W472](../../work/v1.8.0-root-queue/records/W472.md) used one isolated offline
launch-mode client (Root decisions R686–R688/R690), not two authenticated accounts.
Their media and completed input sequences supply no V2 result. R684 selects two
connected clients for soak, but does not supply accounts, a second person, GPU
or time. R691 signs four narrow storage/protection scopes, not passenger safety,
bounded waits, multiplayer correctness or G6/G8.

## Scenario results

| Rows | Scenario / invariant | Server / A / B receipts and actual | Result |
|---|---|---|---|
| A05/A15 | Rocket owner/passenger flight, disconnect/reconnect, seats and witness agreement | PENDING | PENDING |
| A06/A07 | Station invitation/rejection/transfer and authenticated whitelist access | PENDING | PENDING |
| A18 | Ten first-connection trials on each Forge lane; every timeout retained as a finding, no tolerated ratio | PENDING | PENDING |
| B05 | Independent concurrent targets never mix sessions | PENDING | PENDING |
| C03/C08 | Concurrent screen/port actions and remote/unauthorized refusals | PENDING | PENDING |
| D01/D09 | API fixture dual join/rejoin and stale/foreign/out-of-range intents | PENDING | PENDING |
| E02/E03 | Private discovery/reward balances, concurrent fees, owner/member/non-member station access | PENDING | PENDING |
| F02/F03/F06/F07 | Concurrent warp, online/offline passenger, non-member refusal, both clients' context/sky | PENDING | PENDING |
| G03/G07 | Same-tick settlement credits once, both mission views agree | PENDING | PENDING |
| H01/H02/I11 | Contested endgame targets, non-owner/non-operator/member policies, all machine negative cases | PENDING | PENDING |
| J02/J04 | Dedicated bay safe exits, both roles/witness/contention, fallback/fall safeguard/disconnect/death/portal, online S3 | PENDING | PENDING |
| J03 | LAN exit world, each client hosting once; not inferred from dedicated result | PENDING | PENDING |
| J07 | Cargo competing devices, preparation/pending state, passengers/logout with exact resources | PENDING | PENDING |
| J14/J16/J19 | Independent HUD and visible mounts; non-owner drill feedback/railgun observers; research/survey consistency | PENDING | PENDING |
| J17/J18 | World-first participant/non-participant variants, off-world sleep/quorum/time/weather/portal/respawn | PENDING | PENDING |
| J27 | Fuel owner/foreign-owner/competitor interaction and synchronized pending/refusal, no duplicate credit | PENDING | PENDING |
| J28 | Passenger and retained assigned-seat ROCKET_BUSY refusal, fill only after release, no prior fuel movement | PENDING | PENDING |
| J29 | Owner disconnect after L1 while witness stays, reconnect/recovery once; passenger disconnect/seat hold variants | PENDING | PENDING |

Exact actions/expected outcomes are in the
[campaign plan](../../work/v1.8.0-root-queue/records/FINAL-CAMPAIGN-PLAN.md) §5
and linked A–J checklists. Access expectations use W402/R546, not an invented
“all devices are owner-only” policy. For silent refusals K18 receipts and
unchanged server-state readback are mandatory; logs only where the product
actually emits them. The self-only jetpack intent needs no distance target.

## Evidence and unresolved acceptance

Each attempt requires server log plus **both** client logs, paired screenshots
or video, action timeline, before/after endpoint/seat/chunk state, expectation,
actual, tester, UTC, candidate and runtime hashes, and finding/non-coverage
reason. Record whether the second window was driven by a person or harness.
Store under `phase4-v2/<sitting>/` in the candidate evidence root and include
every file hash in its manifest. Missing simultaneous human participation is
NOT_COVERED, not proof of success. Preserve failure attempts next to reruns.

Independent V2 review, actual synchronized observations, media/hashes, findings,
and owner judgment are **PENDING**. No admission or residual-risk signature,
G4/G6/G8 approval, version approval or release decision is made here.
