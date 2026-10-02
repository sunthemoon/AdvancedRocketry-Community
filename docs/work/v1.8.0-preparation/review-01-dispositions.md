# C14 review round 1 — dispositions

Review of revision 1 (`f05eec2`) by the independent contract reviewer:
0 Critical, 2 High, 12 Medium, 7 Low, 6 Info. Verdicts: ADR-060, ADR-061 and
ADR-062 accept with required changes (H1 and H2 block ADR-061). The report is
archived with the preparation evidence. Every finding is answered in its own
commit; root's own finding S1 came before the report.

| Finding | Summary | Disposition | Commit |
|---|---|---|---|
| S1 (root) | Edited upstream pixels were recorded as `NEW` | Fixed: derivatives keep the source's status and notice; only art drawn from scratch is `NEW` | `0c905bc` |
| H1 | Vanilla derivation judged by file name; recoloured Minecraft art scheduled for import | Fixed: `tools/audit/vanilla_derivation.py` measures every legacy asset against vanilla 1.12.2 and 1.20.1 (45 HIT, 42 SUSPECT, 12 UNSUPPORTED); HIT excluded, SUSPECT and name collisions under REVIEW; ADR-061 §4.8–§4.9; validator and tests | `d6fc437`, `5c46cbe` |
| H2 | LibVulpes approved by category without a file manifest | Fixed: LibVulpes is not an approved source in v1.8 (`984d6747` took textures from an unmerged third-party pull request); the two byte-identical AR files are REVIEW; the art is drawn new | `4f5e065` |
| M1 | Configuration surfaces outside `ARConfiguration` missing | Fixed: every Java file scanned; 19 keys plus 14 XML files added with dispositions | `36aa20e` |
| M2 | Coremod rules missing (planet gravity on all entities, cargo access, underwater breathing, blaze items) | Fixed: three `asm_rule` units; living-entity gravity planned in C18a, the non-living half rejected with player impact, cargo access deferred | `ffacd6c` |
| M3 | Steel fan and early power missing | Fixed: fan, gem and rod products, vanilla iron and gold products, a curated coal-generator unit; a combustion generator planned in C16a | `bec283d` |
| M4 | Dispositions naming mechanisms that do not exist | Fixed: configuration rows, the holographic selector, the discovery chance, the landing float and the ore scanner texture | `2856d6d` |
| M5 | 37 undelivered rows hidden as REDESIGNED v1.8.0 | Fixed: PLANNED with their batch; an `evidence` column is required for any v1.8.0 delivery | `4334abc` |
| M6 | 15 of 19 validator mutations survived | Fixed: per-family registered IDs, ADR-062 required for DEFERRED/REJECTED, owning units, a pinned import allowlist, origin findings, `--closure`; 12 tests | `d9e358d` |
| M7 | Batch dependencies understated; model textures after their models | Fixed: dependencies follow the legacy recipes; textures move to their model's batch | `062db2a` |
| M8 | Proposed dispositions written into the matrix as decided; ADR-046 waiver unassigned | Fixed: proposed rows marked; the waiver resolved and assigned to C17b | `c6d0065` |
| M9 | ADR-018's trigger made unsatisfiable without saying so | Fixed: ADR-062 §8 puts the choice to the owner; ADR-018 unchanged until then | `001d66a` |
| M10 | "No placeholder IDs exist" was inaccurate | Fixed: fourteen development components and the placeholder machine models listed with their formalisation and the C19 upgrade check | `a6ff85c` |
| M11 | Edited upstream files would be recorded as `NEW` | Fixed by S1, and §4.8 adds that a derivative inherits its source's EXCLUDE or REVIEW handling | `0c905bc`, `d6fc437` |
| M12 | Recipe graph blind to location and progression locks | Fixed: energy, Level-access and research prerequisites; a reviewed exemption file | `a3c7a72` |
| L1 | Factual slips | Fixed: ore keys, sound events, LEO wording, pump, laser gun, pipe entities, the no-op crafting event | `68aed37`, `5c46cbe` |
| L2 | Asset-plan inconsistencies | Fixed: one rule per model and model texture with its owner; drill and tubes textures excluded; motor files REVIEW | `57fc7d2` |
| L3 | Two advancements depend on undelivered content | Fixed: new triggers named | `6bf0d82` |
| L4 | Coverage gaps | Fixed: ADR-018-deferred evidence stated; player guide, disable switches, deferred migration | `c271a88` |
| L5 | Terrain changes without seam disclosure | Fixed: ADR-061 §6 and the C15b row | `a0adeed` |
| L6 | Generator robustness | Fixed: method-scoped commands, registered packets, conditional block entities, parser tests | `ed5fd42`, `726279f` |
| L7 | Mis-merge and redesigns citing only ADR-062 | Fixed: pump merge; version documents cited; validator rule and test | `c99cad3` |
| I1 | Keep both CI commands; document it | Answered in the validator docstring | `d9e358d` |
| I2 | Ledger vocabulary vs docs/16 verdicts | Answered in ADR-062 §1 | `d4ef1c3` |
| I3 | Deferring terraforming and the hovercraft is a product choice | Stated in ADR-062 §3 and put to the owner at acceptance | `d4ef1c3` |
| I4 | 16x texture chain of the v0.1.0 imports | Recorded in the audit for the G0 review | `4f5e065` |
| I5 | Low oxygen needed only a helmet | Stated in ADR-062 §2 | `d4ef1c3` |
| I6 | Carry the v1.7 chunk-loading and disable outcomes into v1.8 | Added to ADR-060 | `d4ef1c3` |
