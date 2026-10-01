# v1.6.0 known issues (development)

Known limitations of the v1.6 development build (`1.20.1-1.6.0-dev`). This is not a
release note and not a Gate approval.

## Compatibility and upgrade

- **Satellite storage.** The satellite registry is root schema 3 (ADR-050 §10), and the
  Satellite Terminal root is schema 2 (ADR-051 §5). v1.5 hosts refuse a world whose
  registry was upgraded. To downgrade, restore the complete pre-upgrade backup; that
  discards every v1.6 satellite, mission, instance and terminal delivery.
- **Satellite channel.** v1.6 adds the `satellite` channel (protocol 1) for the
  terminal view and scan results. Clients and servers must run the same development
  build.
- **Direct 1.12.2 saves** remain unsupported.

## Resource missions and delivery

- **Rebind can pay twice (stated residual).** An operator `mission rebind` is an
  operator decision for a terminal known to be destroyed. Suppose the registry had
  reverted after the old terminal paid, and the new terminal claims before the old
  one returns. Then the reward is paid twice, and `REBIND_DOUBLE_PAY` records it once
  (ADR-051 §9; 21 of the 64 enumerated orderings).
- **Lost chunk write (stated residual).** A chunk write that is lost after its
  `ChunkDataEvent.Save`, when the acknowledgement was already flushed, loses the reward
  and never duplicates it. This is the same class as a torn vanilla save (ADR-051 §11).
- **Declaring a terminal missing.** The operator's declaration is the `mission rebind`
  itself. No separate "declared missing" record exists. A player who claims elsewhere
  sees `TERMINAL_MISSING` only when the bound terminal's chunk is loaded and no longer
  holds it; otherwise the answer is `WRONG_TERMINAL`.
- **One-off conflict lines per load.** `REBIND_DOUBLE_PAY` and `PAID_THEN_CANCELLED`
  are reported once per receipt per load of the terminal.
- **Buffer and automation.** The reward buffer is withdrawn one stack per intent through
  the menu. It is not exposed to hoppers or pipes in v1.6.
- **Torn saves of a terminal (stated residual, C9-L3).** A withdrawn stack, or a terminal
  carried as an item, lives in the player file, while the buffer lives in the chunk. A
  crash between the two writes can duplicate what was withdrawn or carried. This is the
  same class as any vanilla container. If the registry was flushed but the chunk was
  not, rematerialization restores the reward, and the withdrawn part is then held twice.
  The exposure is bounded by what left the buffer before the crash.
- **Releasing a held instance (C9-Q1).** `instance release` of an instance held by an
  operator cancel is the operator's decision (ADR-050 §8). After `PAID_THEN_CANCELLED` the
  reward was already paid, so check that line before releasing.
- **Copied terminals (C9-Q2).** Creative pick-block with NBT, structure blocks, `/clone` or
  third-party movers can copy a terminal with its ID. Two terminals with one ID could
  both materialize the same unacknowledged claim. Creative and operator tools are outside
  the contract.
- **Reconciliation scope (clarification 6).** A terminal holds actions back only for its
  first pass after loading. Before a claim or cancel it reconciles that mission's row, and
  it refuses the action (`SERVER_ERROR`) if that row fails. Other rows are reconciled
  every 20 ticks in the background. The final review accepted this, but it narrows the
  wording of ADR-051 §7, so the next ADR-051 revision must record it.
- **Terminals destroyed without a proper break (C9R2-L1).** Only a survival break with
  the right tool drops a terminal item that carries its ID, buffer and receipts. A
  bare-hand or wrong-tool break, an explosion that drops no item, `/setblock` and creative
  removal delete them, as with vanilla shulker boxes. Missions bound to that ID then need
  an operator `mission rebind`.
- **Logical missions only.** Asteroid fields, rocket-carried drills or intakes and
  station-deployed craft are deferred to the v1.8 porting matrix (ADR-051 §1).
- **Table changes refuse new starts.** A changed or removed asteroid type or gas table
  refuses new starts of its instances and products (`DEFINITION_NOT_FOUND`); missions in
  flight keep their snapshot (ADR-050 §11).

## Performance

- **Flush cost; ADR-050 §2 gate open.** The measurements are from the Windows
  development host, in the [C9 closure](../../work/v1.6.0-c9-closure/VERIFICATION.md):
  - a barrier flush takes 53 ms on average (55 ms at most) with 1,000 missions;
  - on a synthetic worst-case root at the load bounds (8,192 missions, 4,096
    satellites, 2,048 instances), a flush took 427–631 ms across the attempts;
  - the coalesced flush, at most once per 100 ticks while a change is pending, took up
    to 195 ms in any attempt.

  Every tick window measured with 500 and 1,000 missions meets the docs/17 budgets. On
  a worst-case root, which is reachable only as an over-limit legacy root, a burst of
  `data` barrier flushes could push P99 above 100 ms. Barrier flushes remain on `data`
  launches, claims and cancels, decommissions and discovery replay. Reference hardware
  decides: if it exceeds the budgets, ADR-050 §2 requires the follow-up ADR that moves
  the file write to one writer thread before release. This is not waived.
- **Vanilla MSPT omits post-tick work.** Forge 1.20.1 fires the post-tick event after
  vanilla records the tick time, so `/forge tps` does not show the coalesced flush.
  The C9 measurements add the satellite post-tick work to the vanilla tick time.

## Client

- **Delivery panel.** The terminal screen is 352 px wide with the delivery panel. It has
  not had a V1 visual review on real GPUs; long item or asteroid names are truncated.
- **English and Chinese.** The new labels are generated for `en_us` and `zh_cn` only.

## Development tooling

- **Release-test hooks.** `arce satellite release-test …` (clock advance, flush and tick
  timing, the worst-case root, terminal setup, inspection and crash cuts that halt the
  JVM) is registered only with `-Dadvancedrocketrycommunity.releaseTestHooks=true`. It
  must never be enabled on a production server.
