# TASK W06: jetpack, motion and visor upgrades, and the W03 review follow-ups

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w06-motion` |
| Branch | `codex/v1.8.0-w06-motion` (local only; Root merges) |
| Ledger plan | C18b (`jetPack`, `toggleJetpack`, the jetpack force and low-gravity-boots configuration keys, the motion upgrade effects; the client nausea setting) |
| Record | `docs/work/v1.8.0-root-queue/records/W06.md` |

## Goal

The upgrade modules that W03 made installable now work:
- a hydrogen-paid jetpack with NORMAL and HOVER modes;
- speed and bionic leg assistance;
- padded landing boots;
- the two visors.

All of them are server-authoritative, finite and removed cleanly when unequipped.

## Read

- ADR-066 (`docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`):
  - section 5.2 in full: it is the specification;
  - section 2 (shared authority, limits and failure);
  - section 5.1 for the slot layout;
  - section 9 (decision status).
- The W03 code: `equipment/suit/**`, `equipment/workstation/**`, `model/api/equipment/**`. Also
  its record, `docs/work/v1.8.0-root-queue/records/W03.md`, and its review,
  `docs/work/v1.8.0-root-queue/records/W03-REVIEW.md`.
- The existing network code: how other C2S packets in this mod validate the sender
  (search for `SimpleChannel` or `NetworkRegistry`).

## Scope

1. **Design note first.** This slice adds a C2S packet, so AGENTS.md section 13 applies.
   Your first commit is `docs/work/v1.8.0-root-queue/records/W06-DESIGN.md`, at most one
   page. It covers:
   - the packet and its validation;
   - the rate limit;
   - intent expiry;
   - the hydrogen debit point;
   - how each effect is removed.

   Root has it reviewed before the merge.
2. **Jetpack** (§5.2, first item):
   - The toggle key sends `TOGGLE` or `CYCLE_MODE`. Held thrust sends a boolean heartbeat:
     at most 10 packets per second, and the intent expires after 5 ticks without one.
   - NORMAL and HOVER are an enum. HOVER needs `hover_upgrade` at HEAD.
   - All the server checks listed in §5.2 apply.
   - 1 hydrogen unit is spent before each correction. The thrust increment is
     `0.08 * jetPackForce` (config 0.05 to 4, default 1.3). Vertical speed is capped at
     0.6 and horizontal speed at 0.8.
   - Never set creative-flight flags, and never apply velocity calculated by the client.
3. **Leg and boot upgrades:**
   - `flight_speed_upgrade`: 20% horizontal assistance while the jetpack is actively paid
     for.
   - `bionic_leg_upgrade`: a transient +20% sprint modifier, removed at once on unequip or
     when disabled. Keep other mods' modifiers.
   - `padded_landing_boots`: cancel fall damage only; `lowGravityBoots` (default false)
     narrows this to an ARCE gravity multiplier below 1.
4. **Visors** (client side, in the client package only):
   - `anti_fog_visor` removes only ARCE atmospheric fog and nausea. It is gated by the
     CLIENT setting `atmosphericNausea` (default true).
   - `earthbright_visor` gives Space-Level brightness rendering only.

   Both clear on resource reload, world change or equipment change. If the existing client
   hooks cannot support these safely, implement the server-visible parts. Then record what
   real-client (V1) verification remains.
5. **W03 review follow-ups**, each in its own commit:
   - **M1:** `SuitWorkstationSave` decodes slot stacks from detached copies, so a refused
     root keeps its original armor tag exactly. Add the review's round-trip test.
   - **L1:** the ADR-066 implementation note and R-026 name the noncanonical-NBT refusal
     trigger (for example a typed empty list), as the review describes.
6. **Configuration**: `jetPackForce` and `lowGravityBoots` (COMMON), and
   `atmosphericNausea` (CLIENT). Follow WORKER-RULES for:
   - the exact configuration count in `CommonConfigTest`;
   - the cold-start log expectations.
7. **Data**: the key mapping language, en_us and zh_cn names, and any models you need, all
   through data generation.

## Tests (required)

- JUnit:
  - the packet codec, its bounds and the rate limiter;
  - intent expiry;
  - the thrust arithmetic and its caps;
  - modifier add and remove.
- GameTests, with a fake player server-side:
  - no thrust without hydrogen, and an exact hydrogen debit per correction;
  - expiry after 5 ticks;
  - HOVER refused without `hover_upgrade`;
  - refused in elytra flight or in a rocket seat;
  - the bionic modifier removed on unequip;
  - padded boots cancel fall damage only, and the `lowGravityBoots` narrowing;
  - the W03 M1 round trip.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.

## Not in this task

- The laser gun and mining tools (§5.3).
- Gas charging stations (C18a).
- The beacon finder item (C17c).
- HUD bars (C18d).
- Status files and the ledger.
