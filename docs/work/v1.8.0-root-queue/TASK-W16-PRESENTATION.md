# TASK W16: classic presentation (HUD bars, client configuration, sounds)

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w16-presentation` |
| Branch | `codex/v1.8.0-w16-presentation` (local only; Root merges) |
| Ledger plan | C18d (`CLIENT.*` HUD position and mode keys, `CLIENT.advancedVFX`, the sky override keys, `sound_event` rows, the render events `RocketEventHandler.onScreenRender`, `RenderComponents.renderPostSpecial`, `ClientProxy.modelBakeEvent`, and the space elevator capsule entity, if §7 places it here) |
| Record | `docs/work/v1.8.0-root-queue/records/W16.md` |

## Goal

Players see the classic HUD (atmosphere, oxygen and hydrogen bars, the suit panel) at
configurable positions. They hear the classic sound events at the right server-driven
moments. Client-only settings change presentation only, never authority.

## Read

- ADR-066 (`docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`):
  - section 7 in full: 7.1 the runtime presentation contracts, 7.2 the client configuration
    mapping, 7.3 the per-asset closure;
  - section 2;
  - section 8 (verification).
- The existing client code under `client/**`: the HUD layout work from v1.8
  (`C18d-HUD-ENV-O2-01`, `docs/work/v1.8.0-c18d-hud-layout/`), the life-support status
  packet, and the jetpack and visor client code (W06).
- AGENTS.md section 3.4: common and server code never references `net.minecraft.client.*`.

## Scope

1. **HUD** (§7.1): the atmosphere, oxygen and hydrogen bars and the suit panel. Use
   synchronized, bounded server data only, and the existing HUD layout model.
2. **Client configuration** (§7.2):
   - the `CLIENT.*` keys that §7.2 maps (bar and panel X, Y and mode, `advancedVFX`, the sky
     overrides);
   - CLIENT scope, bounded;
   - presentation only.

   Follow WORKER-RULES for configuration counts and log expectations. CLIENT keys may live
   in a client config spec; check what exists.
3. **Sounds** (§7.1 and §7.3):
   - register the classic `sound_event` IDs that §7 lists, with original sounds or
     individually approved imports only (§7.3, AGENTS.md section 3.2);
   - add `sounds.json` through data generation;
   - play them only from server-driven events or client presentation of synchronized state.
4. **Render hooks:** the screen-render, special-render and model-bake rows that §7 assigns,
   in the client package.
5. **Per-asset closure** (§7.3): every new asset has a provenance entry or is original.

## Tests (required)

- JUnit:
  - the client configuration bounds and defaults;
  - the HUD layout math at extreme positions and GUI scales, with no overlap and no
    off-screen bars;
  - every sound event has a `sounds.json` entry;
  - the asset provenance closure.
- A check (an existing scanner or a unit test) that no common or server class references
  `net.minecraft.client`.
- GameTests only where server-side triggers are added.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals. Record what still needs V1
  visual evidence.

## Not in this task

- Gameplay behaviour.
- New network messages, unless §7 requires one; if it does, write a short design note
  first and say so in the record.
- Status files and the ledger.
