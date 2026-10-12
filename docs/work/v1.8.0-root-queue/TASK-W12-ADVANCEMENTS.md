# TASK W12: classic advancements and the reachability guide

Root: Claude. Date: 2026-10-12. Read [WORKER-RULES](WORKER-RULES.md) first.
Implementer: a headless Claude session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w12-advancements` |
| Branch | `codex/v1.8.0-w12-advancements` (local only; Root merges) |
| Ledger plan | C18c advancements (`advancement:*`: beer, blockpresser, crystalline, dilithium, electrifying, feeltheheat, flightofpheonix, givingitallshesgot, holographic, moonlanding, onesmallstep, rollin, root, spindoctor, suitedup, warp, wenttothemoon) |
| Record | `docs/work/v1.8.0-root-queue/records/W12.md` |

## Goal

The classic advancement tree exists, with stable IDs and modern triggers. Each
advancement is earned only by the real server-side event it describes, and the
world-first milestones keep the owner-confirmed semantics.

## Read

- ADR-066 (`docs/decisions/ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md`):
  - section 6.2 (advancement identities and triggers) in full;
  - section 6.3 (reachability and player guide);
  - section 9, D3 owner-confirmed: preserve the world-first Moon and warp milestones.
- The existing advancement data, `datagen/V180ClassicAdvancementData.java` and
  `V180ClassicAdvancementLanguage.java`. Find out what already exists before adding
  anything.
- The content that the triggers depend on:
  - flight landing (Moon), warp, the suit, the rolling machine, the small plate press;
  - the merged C16b and C16c machines: lathe, arc furnace, cutting machine, and the
    crystallizer and centrifuge if they are merged when you start;
  - the force field, the jetpack.

## Scope

1. **Every advancement listed in the ledger row**, with the stable IDs, parents, frames and
   icons from §6.2:
   - Use vanilla triggers wherever an item or block condition is enough
     (`inventory_changed`, `placed_block` and so on).
   - Add a custom server trigger only for an event that has no vanilla trigger, for example
     landing on the Moon, completing a warp, or completing a machine recipe. Fire it from
     the real server-side completion point, never from a client.
2. **World-first milestones** (D3): keep the world-first Moon landing and warp semantics, as
   §6.2 and D3 define them. They are durable, server-owned and bounded.
3. **Missing content.** If a trigger's content is not in this tree yet (for example a C16c
   machine not merged), add the advancement with a trigger that cannot fire yet, plus a
   clear TODO in your record. Do not invent the content.
4. **The §6.3 reachability guide:** whatever §6.3 requires, for example guide text and an
   advancement-tree check that every advancement can be reached in survival play.
5. **Data:** advancements, en_us and zh_cn titles and descriptions, all through data
   generation.

## Tests (required)

- JUnit: the IDs, parents and that the tree is acyclic; every advancement has en_us and
  zh_cn text.
- A reachability check over the data, using the §6.3 rule.
- GameTests: each custom trigger fires once at its real completion point and not for a
  client-only action. The world-first milestone is granted to the first player only, and
  it survives a save and load.
- The whole unit suite once. At the end: `runData` and `runGameTestServer` (under the
  lock), then the log checker. Report the GameTest totals.

## Not in this task

- The observatory and the research processor (§6.1).
- Any new machine or content.
- Status files and the ledger.
