package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Deliberately incoherent current pending clocks, not claimed historical or reachable commit cuts. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSignatureRecoveryClockGameTests {
    private RecipeSignatureRecoveryClockGameTests() { }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 40)
    public static void rollingCurrentPendingIncompleteClockRetainsPlanAndAllPortResources(GameTestHelper helper) {
        RollingMachineGameTestFixtures.placeStructure(helper);
        AtomicReference<CompoundTag> expected = new AtomicReference<>();
        AtomicReference<List<CompoundTag>> ports = new AtomicReference<>();
        var positions = List.of(RollingMachineGameTestFixtures.ITEM_INPUT, RollingMachineGameTestFixtures.FLUID_INPUT,
                RollingMachineGameTestFixtures.ENERGY_INPUT, RollingMachineGameTestFixtures.ITEM_OUTPUT);
        helper.runAtTickTime(8, () -> {
            RollingMachineGameTestFixtures.port(helper, positions.get(0)).getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .resolve().orElseThrow().insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
            RollingMachineGameTestFixtures.port(helper, positions.get(1)).getCapability(ForgeCapabilities.FLUID_HANDLER)
                    .resolve().orElseThrow().fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
            var machine = RollingMachineGameTestFixtures.controller(helper);
            var recipe = (RollingMachineRecipe) helper.getLevel().getRecipeManager().byKey(id("rolling_iron_bars")).orElseThrow();
            var before = new ProcessResourceSnapshot(machine.resourceRevision(), Map.of(
                    key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot"), new ProcessResourceBalance(2, 64),
                    key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(100, 4_000),
                    key(ProcessResourceKind.ITEM, "item_output", "minecraft:iron_bars"), new ProcessResourceBalance(0, 64)));
            CompoundTag saved = machine.saveWithFullMetadata();
            pendingClock(saved, recipe.processDefinition(), recipe.signature(), before,
                    machine.controllerState().machineInstanceId());
            expected.set(saved.copy());
            ports.set(positions.stream().map(position -> RollingMachineGameTestFixtures.port(helper, position)
                    .saveWithFullMetadata().getCompound("arce_rolling_port").copy()).toList());
            machine.load(saved); RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        for (int tick : new int[]{20, 30}) {
            helper.runAtTickTime(tick, () -> {
                var machine = RollingMachineGameTestFixtures.controller(helper);
                CompoundTag saved = machine.saveWithFullMetadata(); assertRefused(helper, expected.get(), saved);
                for (int index = 0; index < positions.size(); index++) {
                    helper.assertTrue(ports.get().get(index).equals(RollingMachineGameTestFixtures.port(helper, positions.get(index))
                            .saveWithFullMetadata().getCompound("arce_rolling_port")), "Incomplete Rolling clock changed Item/Fluid/FE");
                }
                if (tick == 20) {
                    machine.load(saved); machine.load(machine.saveWithFullMetadata());
                    RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
                } else { helper.succeed(); }
            });
        }
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 40)
    public static void precisionCurrentPendingIncompleteClockRetainsPlanAndControllerResources(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        AtomicReference<CompoundTag> expected = new AtomicReference<>();
        AtomicReference<CompoundTag> energy = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            var machine = PrecisionAssemblerGameTests.controller(helper);
            var recipe = PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            var pending = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            CompoundTag saved = machine.saveWithFullMetadata();
            pendingClock(saved, recipe.processDefinition(), recipe.signature(), pending.before(), pending.machineId());
            expected.set(saved.copy());
            energy.set(PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                    .saveWithFullMetadata().getCompound("arce_precision_port").copy());
            machine.load(saved); PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        for (int tick : new int[]{20, 30}) {
            helper.runAtTickTime(tick, () -> {
                var machine = PrecisionAssemblerGameTests.controller(helper);
                CompoundTag saved = machine.saveWithFullMetadata(); assertRefused(helper, expected.get(), saved);
                helper.assertTrue(expected.get().get("arce_precision_resources").equals(saved.get("arce_precision_resources")),
                        "Incomplete Precision clock changed a controller Item bank");
                helper.assertTrue(energy.get().equals(PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                        .saveWithFullMetadata().getCompound("arce_precision_port")), "Incomplete Precision clock changed FE");
                if (tick == 20) {
                    machine.load(saved); machine.load(machine.saveWithFullMetadata());
                    PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
                } else { helper.succeed(); }
            });
        }
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 40)
    public static void electrolyzerCurrentPendingIncompleteClockRetainsPlanAndNativeResources(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.ELECTROLYZER.get());
        var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow()
                .insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false);
        machine.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        var recipe = (ElectrolyzerRecipe) helper.getLevel().getRecipeManager().byKey(id("electrolyzer_water")).orElseThrow();
        CompoundTag expected = machine.saveWithFullMetadata();
        var before = new ProcessResourceSnapshot(expected.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision"), Map.of(
                key(ProcessResourceKind.ITEM, "item_input", ModItems.EMPTY_CANISTER.getId().toString()), new ProcessResourceBalance(2, 16),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(1_000, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.HYDROGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.OXYGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16)));
        UUID owner = UUID.nameUUIDFromBytes((AdvancedRocketryCommunity.MOD_ID + ":electrolyzer|"
                + helper.getLevel().dimension().location() + "|" + machine.getBlockPos().asLong()).getBytes(StandardCharsets.UTF_8));
        pendingClock(expected, recipe.processDefinition(), recipe.signature(), before, owner);
        expected.getCompound("arce_machine").putInt("progress", recipe.spec().processingTicks() - 1);
        expected.getCompound("arce_machine").putString("active_recipe", recipe.getId().toString());
        machine.load(expected.copy());
        for (int tick : new int[]{10, 20}) {
            helper.runAtTickTime(tick, () -> {
                CompoundTag saved = machine.saveWithFullMetadata(); assertRefused(helper, expected, saved);
                helper.assertTrue(expected.get("arce_machine").equals(saved.get("arce_machine")),
                        "Incomplete Electrolyzer clock changed Item/Fluid/FE or compatibility progress");
                if (tick == 10) { machine.load(saved); machine.load(machine.saveWithFullMetadata()); }
                else { helper.succeed(); }
            });
        }
    }

    private static void pendingClock(CompoundTag parent, ProcessDefinition recipe, String signature,
            ProcessResourceSnapshot before, UUID owner) {
        int ticks = recipe.durationTicks() - 1;
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, before.revision(), Optional.of(new ProcessProgress(recipe.id(), ticks,
                        Math.multiplyExact((long) ticks, recipe.energyPerTick()))), Optional.of(signature), Optional.empty(), ProcessFailure.NONE)));
        parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                UUID.randomUUID(), owner, ProcessMachineLogic.simulate(recipe, before).plan().orElseThrow())));
        new RecipeSignatureMigration().save(parent, recipe.id());
    }

    private static void assertRefused(GameTestHelper helper, CompoundTag expected, CompoundTag saved) {
        CompoundTag process = saved.getCompound(ProcessStatePersistence.ROOT);
        helper.assertTrue("recovery_required".equals(process.getString("state"))
                        && "recovery_diverged".equals(process.getString("failure_code"))
                        && "journal_progress_invalid".equals(process.getString("failure_subject")),
                "Incomplete current journal clock was not a typed recovery refusal");
        for (String field : List.of("resource_revision", "definition_id", "recipe_signature", "progress_ticks", "consumed_energy")) {
            helper.assertTrue(java.util.Objects.equals(expected.getCompound(ProcessStatePersistence.ROOT).get(field), process.get(field)),
                    "Clock refusal changed retained process field " + field);
        }
        helper.assertTrue(expected.get(ProcessJournalPersistence.ROOT).equals(saved.get(ProcessJournalPersistence.ROOT)),
                "Clock refusal changed or cleared the original pending plan");
        helper.assertTrue(expected.get(RecipeSignatureMigration.ROOT).equals(saved.get(RecipeSignatureMigration.ROOT)),
                "Clock refusal changed the current JSON marker");
    }

    private static ResourceLocation id(String path) { return new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, path); }
    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String id) { return new ProcessResourceKey(kind, channel, id); }
}
