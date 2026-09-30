# v1.5.0 known issues (development)

Known limitations of the v1.5 development build (`1.20.1-1.5.0-dev`). This is not a
release note and not a Gate approval.

## Compatibility and upgrade

- **Protocol change.** The celestial display channel moved from protocol 2 to 3
  (ADR-047). A client and a server of different development builds cannot connect.
  The flight (8), life-support (1) and rocket-visual (1) channels are unchanged.
- **Station storage.** The station registry is root schema 4. Older builds refuse it;
  to downgrade, restore the complete pre-upgrade backup, which also discards warp
  balances (ADR-040/044).
- **Direct 1.12.2 saves** remain unsupported.

## Stations and warp

- **No control screen.** Stations, teams and warp are driven by commands (ADR-046).
  The no-screen decision is reconsidered no later than v1.8.0.
- **English only.** Station and warp messages are literal English. The waiver expires
  no later than v1.8.0.
- **Notices.** They do not name the station; visitors in a region are not notified;
  offline members get no notice at login (ADR-046 UI-02).
- **Warp energy and crashes.** Energy accepted by a warp core but not yet folded is
  lost on a crash (bounded). Energy folded after the energy source's last chunk save
  can be supplied again after a crash (ADR-044 §3, disclosed).
- **Stuck transfer records.** A rocket transfer record whose Level is gone stays
  unclassified. It keeps every warp refused until the Level or data pack is restored,
  or the pre-upgrade backup is restored. Recovery handles one record per call,
  round-robin, so it reaches the other records, and the operator line lists the stuck
  record with both ends. There is no audited abandon command yet (WARP review R7,
  final review A13); it is planned with v1.6's mission recovery work at the earliest.
- **Many stations writing at once.** Checked station writes (expansion, gravity, warp
  commit) are spaced server-wide: after one, the next waits `floor(stations × N / 100)`
  ticks, with N from `stations.checkedWriteTicksPer100Stations` (default 3; 0 turns the
  spacing off). With 4,096 stations that is about one write every 6 seconds. A request
  in that window is refused with "the station registry is saving another change" and
  nothing changes; a due warp waits (final review B9).
- **Team changes that change nothing.** Repeating an invitation or a similar no-op team
  command still adds to the registry's growth estimate and marks the registry for
  saving. It stays within the growth bound (final review A12).
- **Physical isolation.** Every star system is a label over the one Space Level; warp
  buys an orbit context, not physical isolation (ADR-044 §1).
- **Space elevator.** Only the read-only, operator-only endpoint check exists
  (ADR-045). Structures, climbers and transport are v1.7 work.

## Presentation

- **Station sky.** The orbited-body disc and its colours are verified by automated
  tests only. There is no V0 smoke on the development host and no V1 or V2 run.
  Colours are provisional. Per-station sun elevation, rings, moons and night-side
  lighting are not shown (ADR-047).

## Persistence bounds

- **Heap headroom.** Loads are bounded by 8 × the file size for every managed type. The
  station registry's measured ratio is far below that, but the headroom is measured on
  the development host only, not on reference hardware (final review B11).
- **Rocket journal heap ratio.** Block-entity and item NBT inside a journalled rocket
  can raise the journal's heap-to-file ratio: 7.79 with filled survival chests and 53.9
  with a creative-made item tag of many empty compounds. One such record saves normally,
  but about 11 would exceed the journal's heap quota, after which journal saves would
  fail as oversized (computed by the reviewer, not run). Records are admitted by raw
  size only, not by heap-accounted size (final review A6).
- **Capacity tests.** The CELESTIAL and ROCKET_TRANSACTIONS stores have no capacity
  test at their record limits yet (final review A6).

- **Satellite registry.** It can outgrow its own 4 MiB bound before reaching its record
  limits (about 6,000 missions), after which its saves fail. This is pre-existing
  (WARP review R3) and tracked for v1.6, which reworks satellites and missions.
- **Sources JAR.** It is not byte-reproducible from `git archive` exports (line
  endings). The main and API JARs are (WARP review R12).

## Testing notes

- **Flaky GameTest.** `earthmoonroundtripconservesfuelandblockedpadreturnssource`
  failed in 2 of about 33 runs of an earlier review (R9). It has not failed in this
  version's own runs, and it is still watched.
- **Config toggling.** Tests must not toggle shared config values: the Forge config
  file watcher can re-apply a stale value on another thread. No GameTest writes the
  shared config file any more: the warp tests use private services, and
  `CommonConfigWiringTest` checks the wiring on an in-memory config (final review A2).
- **Permission matrix counts.** "345/345" includes 9 not-applicable and 2 parse-only
  cells. Force-loaded chunk sets are compared per cell; loads through other ticket
  types are only seen through chunk-holder counts (final review A4, A10).
