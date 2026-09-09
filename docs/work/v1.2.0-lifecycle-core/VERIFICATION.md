# v1.2.0 lifecycle core verification

Date: 2026-09-10

Branch: `codex/v1.2.0-controller-lifecycle`

Production commit: `843ff2734d787bfa414228c72d28acc0eb1f2d5e`

Scope: `V120-LIFE-01`, `V120-LIFE-02`, `V120-LIFE-03`

## Verified behavior

- Controller state owns a stable machine-instance UUID, monotonic generation, selected
  transform, explicit formation state and at most 4,095 defensively copied part positions.
- Controller and part binding use independent schema 1 NBT codecs. Unknown future
  schemas and invalid raw roots are preserved without rewriting; malformed, duplicate
  and over-limit values are rejected.
- Part identity uses `ResourceKey<Level>`, controller position, instance UUID and
  generation. Verification rejects wrong dimensions, unloaded controller chunks, stale
  generations and inactive controllers without loading a chunk.
- Formation validates the complete pattern before discovering binding-capable parts.
  Ordinary casing blocks need no BlockEntity; required port targets must resolve.
- Loaded-part replacement preflights all candidates, accepts only a direct generation
  successor, performs no writes on conflict and restores touched bindings when a write
  throws. Binding and unbinding never modify inventory, fluid or energy resources.
- The per-server dirty queue deduplicates controllers and enforces pending, per-tick
  controller and cell budgets. Idle ticks do no validation; re-dirty and thrown
  validation work remain queued.
- Unit tests cover form, unchanged revalidation, waiting on unload, break, controller
  removal, conflict and rebuild. Forge GameTest confirms form → break → rebuild with
  generation advancing from 1 to 2.

## Verification boundary

This slice does not register a playable multiblock controller or part BlockEntity and
does not yet connect block-place/break, piston, explosion, chunk or dimension events.
Those adapters will be proven with the Rolling Machine vertical slice in
`V120-LIFE-04`/`V120-MCH-01`; therefore `V120-LIFE` remains `in_progress`.

The checks here are short slice verification, not dedicated-server restart, genuine
GPU, two-client, long soak or all-machine acceptance. The complete campaign remains
deferred by ADR-018 until the original machines and dimensions are complete.

## Commands and results

See [test-summary.txt](test-summary.txt). The full repository build ran 521 JUnit tests,
and the Forge server ran all 58 Required GameTests.

## Provenance and security

No upstream code or assets were copied, so the provenance inventory is unchanged. The
staged production patch was checked for credential-like content before commit. Common
lifecycle sources contain no `net.minecraft.client` imports and the largest class is
208 lines.

## Evidence integrity

Hashes are recorded in [checksums.txt](checksums.txt).
