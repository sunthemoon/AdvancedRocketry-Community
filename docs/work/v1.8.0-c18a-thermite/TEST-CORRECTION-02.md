# CL18A-THERMITE TEST-CORRECTION-02 (unreviewed, nothing run)

Corrects TEST-DESIGN-01 for CONTRACT-CORRECTION-02 only. Superseded: T03,
the torch half of T04, G08, N04; N01 is narrowed below. Other cases stay as
written and unaccepted. Expected values remain proposals.

## A0 (JUnit on generated JSON)

| ID | Case | Fails when |
|---|---|---|
| T03' | torch: shapeless, item `minecraft:stick` + tag `forge:dusts/thermite`, count 4 | exact thermite item, other type/count |
| T04' | torch unlock criterion is that tag; reward is the torch recipe | item criterion, wrong reward |
| T11 | press recipe and both `pressing_*_dust` files equal their 4ac74585 bytes | any change |
| T12 | no leaf recipe names item `advancedrocketrycommunity:thermite` | any match |

## A1 (GameTest)

- G08' `thermite_recipe_tag_substitute_matches`: RecipeManager matches
  thermite for one item of each dust tag, the torch for stick + a
  `forge:dusts/thermite` member, nothing for two aluminum dust or stick
  alone. A **foreign** member must also match; [R] the mechanism (test data
  pack or test-only item) is unbound, so that half stays UNVERIFIED until
  Root binds it. Matching is not player crafting.
- G09 `thermite_press_route_yields_two_dust_each`: reuse the
  MaterialGameTests:74-94 column/power layout for **stone** `aluminum_ore`
  and `minecraft:iron_ore` (the existing test uses deepslate aluminum).
  One ItemEntity of 2 matching dust each; ore gone; obsidian and press stay.
  Those exact stacks then match 2 thermite.
- G10 `thermite_press_second_edge_no_input`: a repeat on the empty cell
  returns NO_INPUT and adds no item; a `raw_aluminum` item entity is untouched.
- G11 `thermite_press_disabled_keeps_ore`: `classic.smallPlatePress` false
  (restored like the existing SWITCH_BATCH teardown): DISABLED, ore and item
  count unchanged; both leaf recipes still resolve.
`helper.setBlock` is setup, not player placement; A1 proves no survival play.

## S1 native survival fixture (Root executes, packaged server)

N05: survival non-OP player, new world, Forge 47.4.10. Root records the
verbatim `/give` setup: Silk Touch pickaxe, 2 obsidian, piston, 3 iron
ingots, lever, 2 sticks. The player:
1. mines one stone aluminum ore and one iron ore with Silk Touch (or keeps a
   natural ore in place with obsidian dug in beneath; record which);
2. crafts the press at a crafting table; records the recipe-book unlock;
3. installs obsidian, ore, press, lever; one off->on toggle; picks up 2
   dust; repeats for the other ore;
4. sees the thermite unlock; crafts twice (-2 aluminum, -2 iron dust,
   +2 thermite); crafts torches twice (-2 sticks, -2 thermite, +8 torches);
5. counts items/blocks before and after; any surplus or loss fails.
A normal save/stop/restart after this is S1 persistence, not S2.
N04': step 3 with the press switch off gives no dust and keeps the ore.

## Unverified [U]

Vanilla iron ore in `forge:ores/iron` beyond the existing test, recipe-book
display, tag/recipe reload order and native torch, vacuum and switch
behaviour.
