package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternMatcher;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exact runtime accounting through registered controllers, production recipes, hatches and manager. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class C16cMachineGameTests {
    @GameTest(template = "empty", batch = "w10_crystal_form", timeoutTicks = 30)
    public static void crystallizerFormsInFourRotationsAndRefusesWrongCrucible(GameTestHelper helper) { formation(helper, "crystallizer"); }
    @GameTest(template = "empty", batch = "w10_chemical_form", timeoutTicks = 30)
    public static void chemicalReactorFormsInFourRotationsAndRefusesWrongMotor(GameTestHelper helper) { formation(helper, "chemical_reactor"); }
    @GameTest(template = "empty", batch = "w10_etcher_form", timeoutTicks = 30)
    public static void etcherFormsInFourRotationsAndRefusesWrongLaserCell(GameTestHelper helper) { formation(helper, "precision_laser_etcher"); }
    @GameTest(template = "empty", batch = "w10_centrifuge_form", timeoutTicks = 30)
    public static void centrifugeFormsInFourRotationsAndRefusesWrongCasing(GameTestHelper helper) { formation(helper, "centrifuge"); }
    private static void formation(GameTestHelper helper, String kind) {
        for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
            try (var fixture = new C16cMachineFixture(helper, kind, facing)) {
                var local = fixture.pattern.cells().entrySet().stream().filter(cell -> cell.getValue() instanceof PatternMatcher.ExactBlock exact
                        && (kind.equals("crystallizer") ? exact.blockId().endsWith(":quartz_crucible")
                        : exact.blockId().endsWith(":endgame_casing")) || kind.equals("chemical_reactor")
                        && cell.getValue() instanceof PatternMatcher.BlockTag tag && tag.tagId().endsWith(":motors")).findFirst().orElseThrow().getKey();
                var position = fixture.world(local); helper.getLevel().setBlock(position, Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
                fixture.manager.changed(position); fixture.manager.tick();
                helper.assertTrue(!fixture.controller.formed(), "Wrong machine part formed facing " + facing);
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_crystal_recipe", timeoutTicks = 30)
    public static void crystallizerRunsExactGemAndBouleRecipes(GameTestHelper helper) {
        run(helper, "crystallizer", "crystallizing_dilithium"); run(helper, "crystallizer", "crystallizing_silicon_boule"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_chemical_recipe", timeoutTicks = 30)
    public static void chemicalReactorRunsExactFuelAndBoneMealRecipes(GameTestHelper helper) {
        run(helper, "chemical_reactor", "chemical_reacting_rocket_fuel"); run(helper, "chemical_reactor", "chemical_reacting_bone_meal"); helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_etcher_recipe", timeoutTicks = 30)
    public static void etcherRunsBothCircuitPlatesWithExactEnergyAndRetainedLens(GameTestHelper helper) {
        for (String tier : List.of("basic", "advanced")) {
            try (var fixture = new C16cMachineFixture(helper, "precision_laser_etcher", Direction.NORTH)) {
                var recipe = fixture.recipe("laser_etching_" + tier + "_circuit_plate"); fixture.seed(recipe);
                // Retain the exact custom payload, not merely a fresh lens of the same Item.
                ItemStack lens = fixture.input().getStackInSlot(3); lens.getOrCreateTag().putString("w10_marker", "kept");
                var inputHatch = fixture.hatches("item_input").get(0);
                fixture.replaceBanks(List.of(fixture.controller.checkpoint().resources().bank(inputHatch.bankKey()).orElseThrow().withItem(3, lens)));
                fixture.tick(1_201); fixture.assertCompleted(recipe);
                helper.assertTrue(ItemStack.matches(lens, fixture.input().getStackInSlot(3)), "Lens payload or input slot changed");
            }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_centrifuge_recipe", timeoutTicks = 30)
    public static void centrifugeRunsExactFluidBatchAndDeterministicNuggets(GameTestHelper helper) { run(helper, "centrifuge", "centrifuging_enriched_lava"); helper.succeed(); }
    private static void run(GameTestHelper helper, String kind, String id) {
        try (var fixture = new C16cMachineFixture(helper, kind, Direction.NORTH)) {
            var emptyResources = fixture.resources();
            for (var hatch : fixture.hatches("fluid_output")) {
                for (var action : net.minecraftforge.fluids.capability.IFluidHandler.FluidAction.values()) {
                    helper.assertTrue(fixture.fluids(hatch).drain(1_000, action).isEmpty(), "Empty hatch drain did not return empty");
                }
            }
            helper.assertTrue(emptyResources.equals(fixture.resources()), "Empty drain changed bank revisions");
            var recipe = fixture.recipe(id); fixture.seed(recipe);
            var expected = choices(fixture, recipe); fixture.tick(recipe.rows().processingTicks() + 1);
            fixture.assertCompleted(recipe); assertChoices(helper, fixture, expected);
            var resources = fixture.resources(); fixture.tick(5); helper.assertTrue(resources.equals(fixture.resources()), "Completed batch duplicated");
        }
    }
    @GameTest(template = "empty", batch = "w10_crystal_full", timeoutTicks = 30)
    public static void crystallizerPausesOnFullOutputWithoutLoss(GameTestHelper helper) { pause(helper, "crystallizer", "crystallizing_dilithium"); }
    @GameTest(template = "empty", batch = "w10_chemical_full", timeoutTicks = 30)
    public static void chemicalReactorPausesOnFullFluidOutputWithoutLoss(GameTestHelper helper) { pause(helper, "chemical_reactor", "chemical_reacting_rocket_fuel"); }
    @GameTest(template = "empty", batch = "w10_etcher_full", timeoutTicks = 30)
    public static void etcherPausesOnFullOutputWithoutDebitingLens(GameTestHelper helper) { pause(helper, "precision_laser_etcher", "laser_etching_basic_circuit_plate"); }
    @GameTest(template = "empty", batch = "w10_centrifuge_full", timeoutTicks = 30)
    public static void centrifugeFullOutputRetryAndChosenSaveLoadNeverReroll(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "centrifuge", Direction.NORTH)) {
            var recipe = fixture.recipe("centrifuging_enriched_lava"); fixture.seed(recipe); fixture.tick(200); fixture.fillOutput();
            var job = fixture.controller.checkpoint().job().orElseThrow(); var chosen = job.outcome().orElseThrow();
            helper.assertTrue(job.progress().progressTicks() == 199 && job.progress().consumedEnergy() == 1_990, "Chance completion pause timing");
            var resources = fixture.resources(); int energy = fixture.controller.storedEnergy(); fixture.tick(7);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.OUTPUT && fixture.resources().equals(resources)
                    && fixture.controller.storedEnergy() == energy && fixture.controller.checkpoint().job().orElseThrow().outcome().orElseThrow().equals(chosen), "Output pause changed choice/resources");
            var saved = fixture.controller.saveWithFullMetadata(); fixture.controller.load(saved.copy()); fixture.manager.observe(fixture.controller); fixture.tick(3);
            helper.assertTrue(!fixture.controller.repairRequired() && fixture.controller.checkpoint().job().orElseThrow().outcome().orElseThrow().equals(chosen), "Chosen save/load rerolled");
            fixture.clearOutput(); fixture.tick(1); fixture.assertCompleted(recipe); assertChoices(helper, fixture, chosen.outputs());
            helper.assertTrue(fixture.controller.checkpoint().lastTransaction().orElseThrow().equals(chosen.transaction()), "Chosen transaction ID was replaced");
        }
        helper.succeed();
    }
    private static void pause(GameTestHelper helper, String kind, String id) {
        try (var fixture = new C16cMachineFixture(helper, kind, Direction.NORTH)) {
            var recipe = fixture.recipe(id); fixture.seed(recipe); fixture.tick(11); fixture.fillOutput();
            int progress = fixture.controller.progressTicks(), energy = fixture.controller.storedEnergy(); var resources = fixture.resources();
            // Do not feed more power during the pause when proving total plug conservation.
            for (int i = 0; i < 10; i++) { fixture.manager.tick(); }
            helper.assertTrue(fixture.controller.reason() == ClassicWait.OUTPUT && fixture.controller.progressTicks() == progress
                    && fixture.controller.storedEnergy() == energy && resources.equals(fixture.resources()), "Full output lost resources/progress/FE");
            fixture.clearOutput(); fixture.tick(recipe.rows().processingTicks() - progress); fixture.assertCompleted(recipe);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_gravity", timeoutTicks = 30)
    public static void crystallizerUsesControllerGravityAndResumesAtLimit(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "crystallizer", Direction.NORTH)) {
            var recipe = fixture.recipe("crystallizing_dilithium"); fixture.seed(recipe);
            SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, 0.5);
            fixture.tick(10); var resources = fixture.resources();
            helper.assertTrue(fixture.controller.reason() == ClassicWait.GRAVITY && fixture.controller.progressTicks() == 0
                    && fixture.controller.storedEnergy() == 6_000, "Gravity above limit did not pause");
            SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, 1.0);
            fixture.tick(11); helper.assertTrue(fixture.controller.progressTicks() == 11 && resources.equals(fixture.resources()), "Gravity equality did not allow processing");
            SwitchOverrides.set(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY, 0.0); fixture.tick(289); fixture.assertCompleted(recipe);
        } finally { SwitchOverrides.clear(CommonConfig.CRYSTALLIZER_MAXIMUM_GRAVITY); }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_unchosen_restart", timeoutTicks = 30)
    public static void centrifugeMidBatchSaveLoadKeepsSeedOrdinalAndExactChoices(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "centrifuge", Direction.NORTH)) {
            var recipe = fixture.recipe("centrifuging_enriched_lava"); fixture.seed(recipe); fixture.tick(51);
            var expected = choices(fixture, recipe); var job = fixture.controller.checkpoint().job().orElseThrow();
            helper.assertTrue(job.outcome().isEmpty() && job.ordinal() == 1, "Chance was chosen before completion or ordinal was not allocated");
            var saved = fixture.controller.saveWithFullMetadata(); fixture.controller.load(saved.copy()); fixture.manager.observe(fixture.controller);
            helper.assertTrue(fixture.controller.checkpoint().job().orElseThrow().equals(job), "Mid-batch job changed on load");
            fixture.tick(150); fixture.assertCompleted(recipe); assertChoices(helper, fixture, expected);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_stale_resources", timeoutTicks = 30)
    public static void centrifugeMissingInputsKeepOutcomeAndReplanAgainstNewBankRevision(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "centrifuge", Direction.NORTH)) {
            var recipe = fixture.recipe("centrifuging_enriched_lava"); fixture.seed(recipe); fixture.tick(200); fixture.fillOutput();
            var chosen = fixture.controller.checkpoint().job().orElseThrow().outcome().orElseThrow();
            var hatch = fixture.hatches("fluid_input").get(0); var original = fixture.controller.checkpoint().resources().bank(hatch.bankKey()).orElseThrow();
            fixture.replaceBanks(List.of(ClassicResourceBank.empty(hatch.bankKey()))); fixture.clearOutput(); fixture.tick(3);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.INPUT && fixture.controller.progressTicks() == 199
                    && fixture.controller.checkpoint().job().orElseThrow().outcome().orElseThrow().equals(chosen)
                    && fixture.controller.storedEnergy() == 10, "Missing inputs lost progress/chance/FE");
            fixture.replaceBanks(List.of(original)); fixture.assertCompleted(recipe); assertChoices(helper, fixture, chosen.outputs());
            helper.assertTrue(fixture.controller.checkpoint().lastTransaction().orElseThrow().equals(chosen.transaction()), "Stale plan replaced transaction");
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "w10_ordinal", timeoutTicks = 30)
    public static void ordinalExhaustionRefusesANewBatchWithoutConsuming(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "centrifuge", Direction.NORTH)) {
            var recipe = fixture.recipe("centrifuging_enriched_lava"); fixture.seed(recipe);
            CompoundTag original = fixture.controller.saveWithFullMetadata(), saved = original.copy();
            saved.getCompound(ClassicCheckpointCodec.ROOT).putLong("batch_ordinal", Long.MAX_VALUE);
            var resources = fixture.resources(); fixture.controller.load(saved); fixture.manager.observe(fixture.controller); fixture.tick(5);
            helper.assertTrue(fixture.controller.repairRequired() && fixture.controller.checkpoint().batchOrdinal() != Long.MIN_VALUE
                    && fixture.controller.storedEnergy() == 2_000 && resources.equals(fixture.resources()), "Ordinal overflow consumed or wrapped");
            helper.assertTrue(fixture.controller.saveWithFullMetadata().getCompound(ClassicCheckpointCodec.ROOT).getLong("batch_ordinal") == Long.MAX_VALUE,
                    "Overflow snapshot was not retained");
            fixture.controller.load(original); fixture.manager.observe(fixture.controller);
        }
        helper.succeed();
    }
    /** W10 review R1: the live guard, not the load-time quarantine, refuses the batch after the last ordinal. */
    @GameTest(template = "empty", batch = "w10_ordinal_live", timeoutTicks = 30)
    public static void lastOrdinalCompletesLiveThenTheNextBatchIsRefusedWithoutConsuming(GameTestHelper helper) {
        try (var fixture = new C16cMachineFixture(helper, "centrifuge", Direction.NORTH)) {
            CompoundTag original = fixture.controller.saveWithFullMetadata(), saved = original.copy();
            saved.getCompound(ClassicCheckpointCodec.ROOT).putLong("batch_ordinal", Long.MAX_VALUE - 1);
            fixture.controller.load(saved); fixture.manager.observe(fixture.controller); fixture.manager.tick();
            helper.assertTrue(!fixture.controller.repairRequired() && fixture.controller.formed()
                    && fixture.controller.checkpoint().batchOrdinal() == Long.MAX_VALUE - 1, "The last-but-one ordinal did not load live");
            var recipe = fixture.recipe("centrifuging_enriched_lava"); fixture.seed(recipe);
            fixture.tick(recipe.rows().processingTicks() + 1);
            helper.assertTrue(fixture.controller.checkpoint().job().isEmpty() && fixture.controller.checkpoint().batchOrdinal() == Long.MAX_VALUE
                    && fixture.controller.checkpoint().lastTransaction().isPresent(), "The last allowed batch did not complete: " + fixture.controller.reason());
            fixture.clearOutput(); fixture.seed(recipe);
            var resources = fixture.resources(); int energy = fixture.controller.storedEnergy();
            helper.assertTrue(energy == recipe.rows().processingTicks() * recipe.rows().energyPerTick(), "The second batch was not fully powered");
            fixture.tick(5);
            helper.assertTrue(fixture.controller.reason() == ClassicWait.LIMIT && fixture.controller.checkpoint().job().isEmpty()
                    && fixture.controller.checkpoint().batchOrdinal() == Long.MAX_VALUE && !fixture.controller.repairRequired()
                    && fixture.controller.storedEnergy() == energy && resources.equals(fixture.resources()),
                    "The live guard did not refuse the batch after the last ordinal without consuming: " + fixture.controller.reason());
            fixture.controller.load(original); fixture.manager.observe(fixture.controller);
        }
        helper.succeed();
    }
    private static List<io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput> choices(C16cMachineFixture fixture, ClassicMachineRecipe recipe) {
        if (recipe.rows().chanceOutputs().isEmpty()) { return List.of(); }
        return ClassicChanceSelection.choose(recipe.rows().chanceOutputs(),
                ClassicChanceSelection.seed(fixture.controller.checkpoint().resources().machineId(), recipe.rows().jsonSignature(), 1), id -> ClassicMachineRecipe.stackLimit(id, 1));
    }
    private static void assertChoices(GameTestHelper helper, C16cMachineFixture fixture,
            List<io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput> expected) {
        if (!fixture.kind.equals("centrifuge")) { return; }
        for (int slot = 0; slot < 4; slot++) {
            ItemStack actual = fixture.output().getStackInSlot(slot);
            helper.assertTrue(slot < expected.size() ? ItemStack.matches(actual,
                    new ItemStack(BuiltInRegistries.ITEM.get(expected.get(slot).itemId()), expected.get(slot).count())) : actual.isEmpty(), "Chosen nuggets changed");
        }
    }
    private C16cMachineGameTests() { }
}
