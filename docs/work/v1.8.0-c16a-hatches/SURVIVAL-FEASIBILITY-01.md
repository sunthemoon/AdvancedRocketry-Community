# First-lathe / hatch survival dependency inspection01

Status:read-only static preparation; three current acquisition gaps are open.
This is not the C16d full recipe graph, a new crafting contract or permission
to substitute recipes. Proposal01 and existing accepted contracts stay unchanged.

The exact declared resource-root projection has187 physical/184 active recipe
IDs, excluding only the three already-reviewed historical process copies;
all184 active recipe shapes parse and no other duplicate ID is found.

- Steel has no built-in seed producer yet. All motor tiers inherit the base
  steel requirement; nugget/block/dust conversion does not create a steel seed.
  Accepted steel production belongs to the still-pending electric arc furnace.
- Lathe/five new hatches have no implemented acquisition recipes; the approved
  AR lathe's control/Item-IO/UI components are still C16d work. Existing similarly
  named precision recipes do not produce those boards.
- The registered Small Plate Press has processing/self-loot but no built-in
  acquisition producer. Its iron-plate operation is not itself an obtainable
  catalyst. This gap must be resolved under a reviewed crafting decision.

Existing iron/copper rod crafting (three ingots→four rods) prevents the supposed
rod/lathe bootstrap cycle; it does not prove the eventual full graph acyclic.
Combustion has an independent unpowered crafting route. Casing/circuit routes
are conditional on actual tag suppliers and quartz/Level access. A placed native
fixture is useful process evidence but does not establish new-world survival.

[Frozen preparation](survival-feasibility-01.zip):560,763 bytes/441 entries,
SHA256 `3fbd4f62282cf672949914446faa7f16dbe16db55501b789f075a4f777e08b39`;
manifest `d9fdf0327853744c59b3734ac1b83bcd5019eef1b8f078ea61417bfa448c7cb0`.
Report SHA `e2079989944aabe5b02135b46a96e01bb94c60205c053232823f6468ba9da473`.
Both static commands exit0;414+11 named inputs remain unchanged,36 report links
resolve. Root verifies CRC, safe unique paths and every manifest hash/size.
No Java, native server, source/recipe edit, API or Required Gate admission.

## Subsequent Small Plate Press correction

The report above remains an immutable historical inspection. The separate
[press acquisition contract](../v1.8.0-c15a-materials/PRESS-ACQUISITION-CONTRACT-01.md)
has since passed independent contract review and been adopted as ADR-063 revision7.
Root adds only the original unpowered piston/iron crafting route and its ordinary
unlock. Four resource tests first fail on missing resources, then pass after
DataGen; coherent candidate12 passes1,642 JUnit/314 suites and repeat generation
writes0. Root and independent registered crafting/fullGT pass all460 required
tests,and the independent exact-source review has0 unresolved findings;see
[press verification](../v1.8.0-c15a-materials/PRESS-ACQUISITION-VERIFICATION.md).
Neither this correction nor a placed fixture closes the
steel/controller/hatch/component paths or the full C16d survival graph.
