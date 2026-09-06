# v1.2.0 contract data samples

These JSON files are newly authored, non-runtime examples for contract and test review.
They are deliberately stored below `docs/work/`; Forge, DataGen and the release JAR do
not discover them.

| File | Purpose |
|---|---|
| `process-valid-mixed-io.json` | bounded Item + Fluid process definition |
| `process-invalid-over-limit.json` | one field above the draft duration limit |
| `pattern-valid-rotatable.json` | controller-local pattern with exact/tag/air/port cells |
| `electrolyzer-schema1-logical.json` | logical JSON view of the accepted NBT schema 1 shape |
| `formation-diagnostics.json` | mismatch and unloaded result examples |

The field names remain draft input to the Framework ADR. These files do not freeze a
codec and must not be copied into `src/main/resources` without contract approval.
