# v1.2.0 pattern core verification

Date: 2026-09-06

Branch: `codex/v1.2.0-pattern-core`

Scope: `V120-PAT-01`, `V120-PAT-02`, `V120-PAT-03`

## Verified behavior

- Schema 1 definitions have stable IDs, exact byte accounting, complete bounded cells,
  one anchored controller, at most 64 ports and explicit allowed transforms.
- The strict JSON decoder rejects unknown fields, duplicate coordinates, invalid numeric
  types, malformed UTF-8, recursive optional matchers and definitions over 65,536 bytes.
- The closed matcher set covers exact block, tag, air, controller, typed port and one
  non-recursive optional wrapper.
- Local-X mirror plus all four local-Y rotations preserve the controller anchor and
  round-trip through the inverse transform.
- Validation is deterministic, inspects no more than 4,096 cells and returns no more
  than 32 coordinate-specific diagnostics.
- The Forge adapter checks `ServerLevel.hasChunkAt` before reading block state. Its
  GameTest returns `WAITING_UNLOADED` while the distant chunk remains unloaded.

## Verification boundary

This slice does not register a datapack reload listener, bind controller/part state or
form a playable machine. Those are `V120-LIFE` and representative-machine integration
tasks. Cross-chunk unload/reload with a formed machine belongs to lifecycle validation.
The complete acceptance campaign remains deferred by ADR-018 until original machines
and dimensions are complete; short slice checks remain mandatory.

## Commands and results

See [test-summary.txt](test-summary.txt). The Forge GameTest command includes the two
new adapter checks and all previously required tests.

## Evidence integrity

Hashes are recorded in [checksums.txt](checksums.txt).
