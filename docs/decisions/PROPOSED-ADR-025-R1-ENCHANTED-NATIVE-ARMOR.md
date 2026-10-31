# ADR-025 additive revision 1 (proposal) - Enchanted native armor eligibility

```yaml
status: ACCEPTED
revises: ADR-025
revision: additive 1
date: 2026-10-29
owner: sunthemoon
deciders: [sunthemoon]
accepted_by: sunthemoon
accepted_at: 2026-10-31
acceptance_basis: owner decision D16, "接受这八条规则（推荐）", in the Claude Code session on 2026-10-31
target_version: v1.8.0
source: ADR-066 section 5.1 (owner decision D1)
supersedes: ""
```

**Accepted on 2026-10-31.** The owner accepted the eight rules below as written (decision D16 in
[OWNER-DECISIONS-20261031](../work/v1.8.0-root-queue/records/OWNER-DECISIONS-20261031.md)). The
2026-10-15 note at the end of ADR-025 now points here. No code changes: the rules describe the
shipped behaviour. The acceptance approves no Required Gate, version or release. The file keeps
its name so existing references stay valid. The next paragraph is the proposal's original
preface, kept as it was written.

This is a proposal for the owner. It changes nothing until the owner decides. It does not
change the status of [ADR-025](ADR-025-SUIT-OXYGEN-PROVIDERS.md) or
[ADR-066](ADR-066-CLASSIC-LIFE-SUPPORT-EQUIPMENT-RESEARCH-AND-PRESENTATION.md), and it accepts
no Required Gate, version or release. The file name does not start with `ADR-`, so the content
ledger validator does not read its `PROPOSED` status as the status of ADR-025.

## Context

ADR-066 section 5.1 (lines 642 to 661) extends the ADR-025 host to native armor that carries the
`advancedrocketrycommunity:space_breathing` enchantment. It asks for that eligibility extension to
be reviewed "as an ADR-025 additive revision" and keeps the public function signatures and the
external 2,000-unit bound unchanged. W03 implemented the rule and the W03 review covered it. W32
added the only way to apply the enchantment, the chemical reactor recipe.

On 2026-10-15 Root appended a section "Additive revision note: enchanted native armor (v1.8.0)"
to ADR-025 (commit `d2e482d3`). That section calls itself a development implementation note.
It was not proposed separately, has no owner decision, and does not record every rule the code
applies. The [W56 record](../work/v1.8.0-root-queue/records/W56.md) (lines 130 to 134) lists the
revision as still open. This file is that revision, written against the shipped code at
`e7e7dc52`.

### Legacy behaviour (facts, not requirements)

From the pinned Advanced Rocketry commit `c5cd5af6`:

- `ItemAirUtils.isStackValidAirContainer` (lines 117 to 131) accepts any stack that carries the
  space breathing enchantment at any level.
- `ItemAirWrapper.protectsFromSubstance` (lines 166 to 174) seals any enchanted `ItemArmor`. Only
  the chest spends air, one unit per protection check. In vacuum that check runs every 10 ticks
  (`AtmosphereVacuum.java` line 27, `AtmosphereNeedsSuit.java` lines 39 to 46).
- The chest keeps an `air` integer directly in its item tag. Its maximum is
  `spaceSuitO2Buffer * 1200`, which is 36,000 at the default of 30 (`ItemAirUtils.java` line 114,
  `ARConfiguration.java` line 441). That lasts about five hours in vacuum. A chest without a tag
  reads as full (lines 29 to 37).
- The gas charge pad filled enchanted chests (`TileGasChargePad.java` line 65), and the chemical
  reactor applied the enchantment (`TileChemicalReactor.java` lines 99 to 100 and 173).

There is no direct 1.12.2 world conversion (ADR-066 section 1), so the legacy `air` tag is not read.

## Proposed additive text for ADR-025

The host's existing rules (the provider API, the bounds, the detached commit and the failure
lifecycle) are unchanged. The host additionally recognizes enchanted native armor as follows.

1. **Eligibility.** The host counts a worn stack as a sealed suit piece when all of these hold, checked
   again for every live worn stack on every read:
   - the item is a native `ArmorItem` whose own equipment slot is the slot it is worn in;
   - the stack's maximum stack size is 1, its count is 1, and both its tag and its whole serialized
     stack fit 16,384 bytes, depth 16 and 256 nodes;
   - the stack carries `advancedrocketrycommunity:space_breathing` at exactly level 1;
   - no ADR-025 provider registration claims the item, whether that provider is enabled or disabled;
   - `equipment.classicEnabled` is true.

   No item-to-slot mapping is registered, no provider is registered for the enchantment, and an
   unenchanted stack of the same item is never counted.
2. **Precedence.** A registered provider always owns its items. The enchantment never claims,
   aliases or rewrites a provider's `arce_suit_provider` root. Built-in suit pieces keep their own
   path and their original `arce_space_suit_oxygen` root; the recipe refuses to enchant them.
3. **Mixing.** Enchanted pieces count toward the four slots together with built-in and provider
   pieces, as ADR-025 already allows for mixed providers. Only the chest supplies oxygen.
4. **Payload.** Only an eligible enchanted chest owns the item tag key `arce_enchanted_suit_oxygen`.
   The root is a compound with exactly `schema_version` (int, 1) and `oxygen_units` (int, 0 to
   2,000). A missing root reads as present zero, so an enchanted chest starts empty. Any other
   shape is unusable: the chest supplies no oxygen and refuses refills, and the root is kept
   unchanged. Other item NBT is never touched.
5. **Commit.** A debit or refill edits a detached copy, checks the whole tag against the bounds
   above, and commits only while the worn chest is the same stack with the same count and an
   unchanged tag and is still eligible. A failed commit is recomputed as zero usable oxygen with
   the original cadence phase, as ADR-025 already requires.
6. **Resources.** The cadence is ADR-025's: one unit per 20 vacuum ticks. One whole oxygen canister
   adds 1,000 units, up to the `OXYGEN.spaceSuitO2Buffer` target (default 2,000), the same target as
   the built-in chest. A refill is refused while the chest holds more than that target. Enchanted
   armor takes no modules, no auxiliary tank and no ADR-066 D1 reserve transfer.
7. **Application.** Only the C16 chemical reactor recipe applies the enchantment, at level 1. It
   refuses unsafe stacks, non-armor items, wrong slots, built-in suit pieces, provider-claimed items
   (enabled or disabled), already enchanted stacks, incompatible enchantments, and a chest whose
   existing `arce_enchanted_suit_oxygen` root is unusable. Enchanting tables, books, trades and
   loot never offer it.
8. **Unchanged.** The three API types that ADR-025 exported at API 1.3 keep their signatures, and
   this revision adds no API type and no API version step. The 2,000-unit external bound, the
   provider limits and the built-in root are unchanged. A host without this rule does not seal
   enchanted armor and leaves the new key alone.

## Differences from legacy

| Rule | Legacy | This revision |
|---|---|---|
| Enchantment level | any | exactly 1 |
| Chest capacity | 36,000 air at 1 per 10 vacuum ticks (about 5 hours) | 2,000 units at 1 per 20 vacuum ticks (about 33 minutes) |
| Missing chest data | reads as full | reads as empty |
| Refill | gas charge pad | oxygen canisters; the pad waits for ADR-066 D4 |
| Provider-owned items | not applicable | never counted through the enchantment |

The capacity follows the owner's D1 decision (ADR-066 section 9 item 1), not legacy parity.

## Evidence

The rule ships in `EnchantedSuitOxygen.java` (lines 14 to 36), `SuitEquipmentService.java`
(lines 38 to 47, 81 to 101, 119, 151 to 216) and `SpaceBreathingApplication.java` (lines 24 to 43).
Its tests are `SpaceBreathingApplicationTest`, `ClassicSpecialRecipeTest`,
`ChemicalSpecialGameTests` and
`SuitWorkstationGameTests.enchantedNativeArmorSealsOnlyWhenWornAndHasFiniteOxygen`. The W03
review (SHA-256 `4b74bf77…`) covered the rule; W06 fixed its findings M1 and L1. The gas charge
pad design ([W174-DESIGN](../work/v1.8.0-root-queue/records/W174-DESIGN.md) section 4.4, W174-R5 and
W174-R15 item 2) already assumes the same eligibility, precedence and active-buffer target.

## If the owner accepts

Root records the owner's words and the date, sets this file's status to ACCEPTED, and decides
whether the 2026-10-15 section at the end of ADR-025 becomes a pointer to this file. No code
change is needed: the text above describes the shipped behaviour. Rules 1 (the
`equipment.classicEnabled` condition and disabled providers) and 6 (the shared canister target)
are not in ADR-066 or the 2026-10-15 note. If the owner rejects either, the change is a separate
code task.

## Revisit when

Revisit when the gas charge pad (ADR-066 D4) gains a route that fills enchanted chests, when a
provider asks to share its items with the enchantment, or when the public API version changes.
