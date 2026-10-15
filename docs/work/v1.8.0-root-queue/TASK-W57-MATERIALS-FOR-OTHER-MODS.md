# TASK W57: close `makeMaterialsForOtherMods` (disclosure, CHANGELOG, whole-catalog tag test)

Root: Claude. Date: 2026-10-15. Read [WORKER-RULES](WORKER-RULES.md) first. You are a new session.

| Item | Value |
|---|---|
| Worktree | `D:/GitHub/arce-v180-w57-materials-tags` (from the shared branch at `3f91b66e` plus this task) |
| Branch | `codex/v1.8.0-w57-materials-tags` (local only; Root merges) |
| Record | `docs/work/v1.8.0-root-queue/records/W57.md` (new) |
| Input | `records/W56.md` section 1 (what ships, what is missing, suggested wording) |

The ledger row `config:CATEGORY_GENERAL.makeMaterialsForOtherMods` (C16d) stays PLANNED because:

- the owner's decision (`docs/work/v1.8.0-c16d-components-graph/OWNER-DECISIONS-02.md` lines 7 to 18)
  requires the legacy behaviour gap to be disclosed, and nothing a player or pack author reads says it;
- `CHANGELOG.md` (v1.8.0 section, the "Data packs:" bullet) still says Rolling Machine, Precision
  Assembler and Electrolyzer recipes refuse tag ingredients. `85a19757` ended that rule;
- `CONTRACT-02.md` section 2.4 names the evidence as "the tag-based catalog of every kernel and classic
  recipe type", and no committed test checks the whole catalog (W56 used a scratch script).

## What to do

1. **CHANGELOG.** In the v1.8.0 section only:
   - Correct the "Data packs:" bullet. Root checked the codecs: Rolling Machine, Precision Assembler,
     Electrolyzer, Small Plate Press and the classic kernel recipes all decode ingredients through
     `BoundedItemIngredientCodec.decode`: one `item` or one `tag` per entry, 1 to 32 entries, the
     whole ingredient resolves to 1 to 32 items, no Forge custom ingredient types. Confirm this in
     the code and the tests before writing, and keep the Small Plate Press details of that bullet that
     are still true.
   - Add the disclosure entry. W56's suggested text is a starting point: the legacy
     `makeMaterialsForOtherMods` setting is gone; machine recipes take common `forge:` tags, so other
     mods' ingots, plates and dusts work wherever this mod uses the same material, and this mod's
     products carry the same tags; recipes for materials this mod does not define (for example plates
     of another mod's metal) are no longer generated, and a data pack can add them.
   - Do not edit any other version's section.
2. **The whole-catalog test.** One JUnit test (no Minecraft bootstrap if the existing resource tests
   manage without it) over the shipped v1.8 generated data and resources:
   - every recipe of every machine recipe type this mod registers (kernel, classic, rolling, press,
     precision, electrolyzer, and any others you find; list them in the record with file and line)
     is read;
   - every ingredient entry that names an item of a **material form** that has a common `forge:` tag
     in this mod (ingots, nuggets, plates, sheets, rods, gears, dusts, blocks, ores, raw materials,
     coils, and any other form you find in the material tables) must use the tag instead;
   - the item inputs W56 listed as acceptable (`minecraft:sand`, `minecraft:charcoal`,
     `minecraft:redstone_block`, `advancedrocketrycommunity:silicon_wafer`, vanilla logs, bone, the
     ender eye, this mod's own components) stay allowed. Derive the rule, do not hard-code an allow
     list of recipe IDs. If `silicon_wafer` should use the `silicon_wafers` tag that `TEST-DESIGN-01`
     named, say so in the record and do not change the recipes;
   - the test fails on a planted item ingredient (show it with a negative control in the record, then
     remove the plant);
   - also assert that no COMMON, SERVER or CLIENT config value named `makeMaterialsForOtherMods`
     exists.
3. **Evidence and ledger proposal.**
   - Write `docs/work/v1.8.0-c16d-components-graph/VERIFICATION-MATERIALS-TAGS.md` (new file; do not
     edit the existing `VERIFICATION.md`). Name the commits (`66996a1f`, `85a19757`, your commits),
     the tests (yours, `V180KernelRecipeResourcesTest`, `V180MaterialResourcesTest`), and the
     disclosure.
   - In your record, put a JSON block in W56's shape for Root's script. Proposed disposition:
     REDESIGNED (CONTRACT-02 line 147). The target must not contain `advancedrocketrycommunity:`
     unless it is a registry ID; plain text such as "common forge item tags in machine recipes" is
     right. The section to append is the content after your file's title.
   - Check the proposal on a scratch copy outside the repository with
     `scripts/validate_v180_content_ledger.py --require-accepted`, as W56 did.

## Commands

- The whole unit suite once (`gradlew test`, JDK 17).
- `python -B scripts/validate_v180_content_ledger.py --require-accepted` on the unchanged tree.
- `git diff --check`.
- No GameTest or `runData` run is needed unless you change generated data, which this task does not
  ask for. If you find a recipe that breaks the rule, stop and report it instead of changing it.

## Not in this task

- The ledger, status files, ADRs and the risk register (Root).
- Any recipe, tag or generated data change.
