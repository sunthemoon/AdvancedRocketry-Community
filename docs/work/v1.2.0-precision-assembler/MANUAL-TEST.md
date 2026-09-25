# Precision Assembler short manual checks

These checks use a development or packaged dedicated server with the current build.
They are **not executed evidence**. Record tester, build hash, server log, actual result
and screenshots before using them for a Gate decision.

## Setup

Build the complete 3×3×4 structure defined by
`data/advancedrocketrycommunity/machine_patterns/precision_assembler.json`.
Use the controller at local `(1,0,0)`, five Item-input cells, two Item-output cells
and one Energy-input cell exactly as listed in that definition. Keep all structure
chunks loaded. Supply resources through the typed port capabilities rather than
editing BlockEntity NBT.

## Cases

| ID | Steps | Expected | Status |
|---|---|---|---|
| `PREC-M-01` | Insert 2 iron ingots into `item_input_0`, 2 redstone into `item_input_1`, and supply 800 FE. Wait at least 20 server ticks. | Both inputs decrease by 2 exactly once; `item_output_0` contains one Advanced Circuit and `item_output_1` contains two Redstone Torches. | NOT_EXECUTED |
| `PREC-M-02` | Insert the same first two inputs plus one gold ingot, one quartz and one copper ingot into `item_input_2..4`. Supply 1,000 FE, then another 500 FE while the recipe is running. | The five-input recipe completes once with one Comparator in `item_output_0`; the unused second output stays empty. | NOT_EXECUTED |
| `PREC-M-03` | Fill `item_output_0` to its stack limit, provide the two-input recipe materials, then clear that output. Repeat with no FE, then supply 800 FE. | Output blockage and missing Energy pause without consuming inputs or creating either product; clearing each condition resumes the same batch. | NOT_EXECUTED |
| `PREC-M-04` | Start a batch, save and stop the same server world, then restart and continue it. Repeat with ports on both sides of a chunk boundary. | Progress and every Item/Energy amount persist; the batch completes once with no duplicated or lost output. | NOT_EXECUTED |

`PREC-M-04` is a short slice-local restart check. It does not replace the integrated
long-duration acceptance campaign scheduled by ADR-018.
