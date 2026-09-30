# v1.5 station, core, passenger and rocket recovery matrix (V150-MIG-01)

Development copy. It is evidence for the maintainer, **not a Gate approval**; the
candidate-bound copy belongs in `docs/releases/v1.5.0/` at ACC-02. Earlier rocket
transaction and passenger recovery evidence is in the
[v1.0.0 recovery map](../../releases/v1.0.0/RECOVERY-MATRIX.md) and is not repeated here.

Each row names the state, what an interruption leaves, the rule that decides recovery, and
where the outcome was observed. "Native" means a packaged dedicated server; "JUnit" and
"GameTest" are automated tests in the full runs of the named packet.

## Stations (registry authority)

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| v1.4 (root 2) or v1.5 root-3 world at first start | Upgraded before start to root 4 with a backup; team, orbit, pad and region unchanged | ADR-040 pre-start migration; ADR-044 §6 | Native: [STATION-04](../v1.5.0-station-native/VERIFICATION.md), [WARP-02](../v1.5.0-warp-schema/VERIFICATION.md), this packet (`upgrade`: `migrated=1, backup=...`); JUnit `StationWarpSchemaTest` |
| Station whose orbit body is missing from the catalog | Preserved; reported; evacuation warp at interstellar cost | ADR-041/044 | Native: [MIG-01a](../v1.5.0-mig-missing-orbit/VERIFICATION.md) |
| Checked update (expand, gravity, warp) cut before replacement | No change, no charge | ADR-041 checked commit | JUnit `StationWarpRelocationTest.cutBeforeReplacementIsNoWarpAndNoCharge`, `StationCheckedUpdateTest` |
| Replacement reported failed but verified on disk | Applied and charged once | ADR-044 §3 | JUnit `replacementReportedAsFailedButVerifiedOnDiskIsChargedOnce` |
| Outcome unreadable; publish failure after replacement | Quarantined; consistent orbit/debit pair on both sides | ADR-044 §3 | JUnit `unreadableOutcomeQuarantines...`, `publishFailureAfterReplacementQuarantines...` |
| Process killed right after a warp commit line, with no save | Orbit and debit both on disk (the checked write precedes the line) | ADR-044 §3 | Native: this packet (`interrupted`, kill-now) |
| Growth at the 4 MiB bound | Refused before any change; balances keep their headroom | ADR-044 §2 rev. 4 | JUnit `StationRegistryStorageBoundTest`, `StationStorageBudgetTest` |
| Station deletion while the transfer journal is blocked or references it | Refused (fail closed) | ADR-044 §5 | GameTest `stationDeletionFailsClosedWhileTheTransferJournalIsBlocked` |

## Warp core and energy

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| Energy accepted, not yet folded, orderly stop | Folded by the stop fold and saved | ADR-044 §2 | Native: [WARP-05](../v1.5.0-warp-native/VERIFICATION.md) |
| Energy accepted, not yet folded, crash | Lost (bounded by 200,000 FE per tick for up to 200 ticks, capped at 10,000,000 FE); disclosed | ADR-044 §3 | Disclosure only |
| Folded energy after the source's last chunk save, crash | Can be supplied again (duplication direction); disclosed | ADR-044 §3 | Disclosure only |
| Credit refused by a fold | Lost and logged (a station deleted before the fold, or a registry loaded above the growth limit) | ADR-044 §2/§3 rev. 4 | JUnit `StationRegistryStorageBoundTest`; log `ARCE_STATION_WARP_CREDIT_REFUSED` |
| The core block | Stateless: carried or broken, it holds nothing | ADR-044 §2 | GameTest `coresChargeOnlyTheStationTheyStandInWithinBounds` |

## Countdowns and confirmations (memory only)

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| Orderly stop during a countdown | Discarded; no warp, no charge | ADR-044 §4 | Native: WARP-05 (`restart`) |
| Process killed during a countdown (after a durable save) | Discarded; no warp, no charge; `countdowns=0` after restart | ADR-044 §4 | Native: this packet (`concurrent` kill, then `interrupted`) |
| Owner logs out during a countdown | The countdown continues | ADR-044 §4 | GameTest `countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired` |
| Actor de-opped, kill switch thrown, or target removed during a countdown | Commit aborts with the reason | ADR-044 §4 | GameTests `aDeoppedOperatorsCountdownAborts`, `theKillSwitchAbortsARunningCountdown`, `aTargetRemovedDuringTheCountdownAborts` |
| Two stations confirmed together | Both commit on their own targets, each charged once; commits are serialized one per tick | ADR-044 §4 | Native: this packet (`concurrent`); GameTest `countdownsDueTogetherCommitOnePerTick` |
| A request right after a commit | Refused for 100 ticks (cooldown), then accepted | ADR-041/044 | Native: this packet (`concurrent`) |

## Rockets touching a station

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| **Docked** rocket written by v1.4, through the upgrade, a warp, two kills and restarts | Same entity, logical identity, snapshot, fuel (670) and position; its transfer record stays COMMITTED and settled | ADR-042 item 2; ADR-044 §5 | Native: this packet (R1, checked in every phase) |
| **In-flight** transfer written by v1.4, frozen at `DESTINATION_SPAWNED` from a station, killed, then upgraded | Recovered forward once both ends are loaded (`REMOVE_SOURCE_KEEP_DESTINATION`): exactly one rocket, same logical identity, fuel debited once (1000 → 670) | ADR-042 item 2; transfer recovery decision | Native: this packet (R2); JUnit `RocketTransferRecoveryDecisionTest`; GameTest `transferRecoveryReconcilesAllEntityPresenceCases` |
| Any rocket that can still move in a station's region | Warp refused (`WARP_ROCKETS_IN_MOTION`); an unclassified record blocks every warp until recovery classifies it | ADR-044 §5 rules 1-4 | JUnit `RocketStationMotionRuleTest`; GameTest `countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired` |
| A departure from a station after a warp | The flight records the station's current orbit body | ADR-044 §5 (WARP review R5) | GameTests in `PlanetaryAdmissionGameTests` |
| A record whose ends cannot be loaded (for example, its Level was removed) | Recovery moves on to the other records (WARP review R7). The record stays unclassified, so warps stay refused; the operator line lists it with both ends | ADR-044 §5 rule 2 | JUnit `RocketTransferRecoverySelectionTest`, `diagnosticsListUnclassifiedRecordsForTheOperator` |

## Passengers

| State or interruption | Outcome | Rule | Evidence |
|---|---|---|---|
| Passengers online or offline during a warp | Not moved; player data untouched; the station context changes on the next query | ADR-044 §1 | GameTest `countdownSurvivesOwnerLogoutAndTheRocketRuleIsWired` |
| Passengers inside a rocket at a transfer phase | As in v1.0 (reconnect queue, one-time recovery) | v1.0 passenger contracts | [v1.0.0 recovery map](../../releases/v1.0.0/RECOVERY-MATRIX.md) |

## Known gaps

- **No audited operator path to abandon a record whose Level is gone** (WARP review R7,
  second half). Recovery no longer starves, but such a record keeps warps refused.
  Workaround: restore the removed Level or data pack, or restore the pre-upgrade backup.
  An audited abandon command needs its own ADR and threat model; it is tracked for the
  v1.5 final review (C5) and, if not accepted there, as a v1.6 known issue.
- Not observed natively: a crash cut *inside* a checked write (covered by JUnit fault
  injection); a docked rocket with passengers aboard through a warp; real clients (V2).
- The native in-flight case covers one phase (`DESTINATION_SPAWNED`, from a station).
  The other phases are covered by the recovery decision tests and the v1.0 forced-stop
  matrix, not by a station-sourced native run.
