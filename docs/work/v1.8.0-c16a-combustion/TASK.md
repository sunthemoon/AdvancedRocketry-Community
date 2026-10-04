# C16a-02: combustion generator

Date: 2026-10-03. Status: DEVELOPMENT_VERIFIED (not a version Gate PASS).
Contract: accepted ADR-064 revision 2. [Evidence](VERIFICATION.md).
Integrator: root session, sole tracked writer. Current-version exception: ADR-060.

## Scope

Implement the single-block furnace-fuel generator: 40 FE/t, 20,000 FE buffer,
1,000 FE/t shared push/pull budget, fuel containers, pause without wasting fuel,
bounded schema-1 persistence, fail-closed unsupported saves, server menu and
capabilities, original art, generated recipe/loot/tool tags/translations.

Not included: tag/signature migration, shared hatches, machine family, fluids,
tanks, pumps, C16b-d, C17-C19, version release or GPU/multiplayer acceptance.
Existing C15 review work and the user-owned development-docs bundle are untouched.

## Adapter decisions

- A burn tick needs all 40 FE of buffer space. No partial generation or fuel loss.
- Disabling the COMMON switch pauses burning and FE export; stored fuel remains
  withdrawable. The block and recipe remain registered.
- Ordinary destruction drops only unburned fuel or its container, once; stored FE
  and already-consumed burn credit are not portable resources.
- Six loaded adjacent chunks only; no chunk tickets or world scan. Forge external
  transfers have the normal automation guarantee, not arbitrary-crash atomicity.
- Menu fields are fixed-size; burn duration/remaining use split 16-bit values.
- Future/corrupt saves retain the bounded original root and refuse resource access
  and ordinary removal. Oversized input passes its raw Tag reference through BE
  serialization (ADR-027), then an intentional ChunkDataEvent.Save exception vetoes
  the storage write and restores the dirty flag. Throwing inside the BE alone
  would let vanilla omit the BE and is not sufficient.
- A stackable container fuel pauses if its single slot cannot preserve the returned
  container. Unstacked vanilla lava buckets burn normally; no container is discarded.
- Recipe: six tagged iron ingots, a crafted copper coil, furnace and redstone;
  no powered recipe is needed to obtain the first generator.

## Verification checklist

- [x] A0 domain, codec, resource and menu boundary tests (24 new JUnit cases).
- [x] A1 real Forge fuel/container/full buffer/FE/lifecycle/quarantine tests (17 new cases).
- [x] S1 packaged-server save and two repeat restarts, including on-disk refusal.
- [x] Independent source review, targeted JUnit, full GameTests and native packet audit.
- [x] Mandatory build, DataGen reproducibility, GameTests and repository validators.
- [x] Archive command logs, source identity and results.

V1 real-GPU and V2 multiplayer evidence remain unperformed under ADR-018.
No Required Gate is declared passed by this task record.
