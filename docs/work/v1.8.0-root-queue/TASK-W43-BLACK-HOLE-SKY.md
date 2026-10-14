# TASK W43: C18d black-hole sky, client rendering

Root: Claude. Date: 2026-10-14. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w43-black-hole-sky` (from the shared branch) |
| Branch | `codex/v1.8.0-w43-black-hole-sky` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W43.md` (new) |
| Specification | ADR-066 section 7.1, the "Black-hole sky" bullet; ADR-065 section 11.1; ADR-057 (the singularity profile and the controller's accretion effect) |

The server half already exists:
- `StationSkyContextPacket` is at payload schema 2;
- `CelestialNetwork` is at protocol 4;
- the server alone sets `SkyKind.SINGULARITY`, and only from a current ADR-057 profile
  (`AdvancedRocketryCommunity.java`, around line 253).

What is missing is the physical-client rendering of that kind.

## What to do

1. Find how the client renders the station sky today for `SPACE_FALLBACK` and `PLANETARY`, and the
   station sky switch it respects. Cite the classes in your record before writing code.
2. For `SINGULARITY`, render the sky as ADR-066 section 7.1 says:
   - one dark disc plus a bounded accretion or ring mesh of at most 512 vertices;
   - at most eight particles per tick;
   - no block or entity attraction, and no server or FE change;
   - it supplements ADR-057's controller effect (`AccretionDiscRenderer`) and does not replace it;
   - it clears on a context change, a Level change, a disconnect or a resource reload;
   - it respects the station sky switch;
   - an unknown or missing context uses the existing fallback.
3. Use the orbit body's distance and the three orientation phases and rates from the packet, as the
   planetary sky does, so the disc sits where the body sits.
4. **Art.** Draw procedurally, or use a NEW texture with pre-authoring provenance as the existing
   NEW assets do. Do not import a legacy texture; all legacy textures are REVIEW.
5. All new code is physical-client only. Common or server code must not reference
   `net.minecraft.client.*`.

## Tests

- Unit tests for the client-side logic, without starting a client:
  - the mesh builder stays at or below 512 vertices for every bounded input;
  - the particle cap;
  - the state clears on context change and on reset;
  - the switch;
  - the fallback for unknown and missing contexts.
- The existing GameTests and client-logic tests still pass.
- No V1 visual claim. Real-GPU checks are in the final campaign. Say in your record what a V1
  reviewer should look at.

## Commands

- The whole unit suite once.
- `runData` under the lock, only if generated data changes.
- `runGameTestServer` under the lock, then the log checker.
- `runClient` is not required. If you run it, say so; do not claim it as V1.
- Delete your generated `build/gametest/world` at the end with a plain `rm -rf build/gametest/world`
  from the worktree root. If the tool refuses, record it and Root deletes it.

## Not in this task

- Any packet, protocol or payload change. If one seems necessary, stop and report.
- The elevator capsule, sounds, OBJ models.
- Status files and the ledger (Root).
