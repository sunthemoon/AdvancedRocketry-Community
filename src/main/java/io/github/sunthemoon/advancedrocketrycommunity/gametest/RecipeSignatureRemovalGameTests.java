package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native Forge destruction hooks and retained resource snapshots, without spawning a boss. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSignatureRemovalGameTests {
    private RecipeSignatureRemovalGameTests() { }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 30)
    public static void rollingEntityRemovalRetainsRepairAndPendingRootsAndBoundPorts(GameTestHelper helper) {
        RollingMachineGameTestFixtures.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            RollingMachineGameTestFixtures.assertPortsValid(helper);
            RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_INPUT)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
            RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.FLUID_INPUT)
                    .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                    .fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
            var machine = RollingMachineGameTestFixtures.controller(helper);
            var recipe = (RollingMachineRecipe) helper.getLevel().getRecipeManager()
                    .byKey(id("rolling_iron_bars")).orElseThrow();
            CompoundTag base = machine.saveWithFullMetadata();
            var before = new ProcessResourceSnapshot(machine.resourceRevision(), Map.of(
                    key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot"), new ProcessResourceBalance(2, 64),
                    key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(100, 4_000),
                    key(ProcessResourceKind.ITEM, "item_output", "minecraft:iron_bars"), new ProcessResourceBalance(0, 64)));
            var pending = prepared(machine.controllerState().machineInstanceId(), recipe.processDefinition(), before);
            checkCases(helper, machine, machine::load, protectedRolling(), base,
                    currentWork(base, recipe.processDefinition(), recipe.signature(), machine.resourceRevision()), pending);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 30)
    public static void precisionEntityRemovalUsesItsExistingOwnershipPolicyForAllFacades(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            var machine = PrecisionAssemblerGameTests.controller(helper);
            var recipe = (PrecisionAssemblerRecipe) helper.getLevel().getRecipeManager()
                    .byKey(id("precision_control_circuit")).orElseThrow();
            CompoundTag base = machine.saveWithFullMetadata();
            var pending = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            List<BlockPos> positions = new ArrayList<>(List.of(PrecisionAssemblerGameTests.CONTROLLER));
            positions.addAll(PrecisionAssemblerGameTests.portPositions());
            int formedPorts = 0;
            for (BlockPos absolute : machine.controllerState().partPositions()) {
                if (helper.getLevel().getBlockEntity(absolute)
                        instanceof io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity) {
                    formedPorts++;
                    helper.assertTrue(positions.stream().map(helper::absolutePos).anyMatch(absolute::equals),
                            "A formed precision facade is absent from the removal coverage");
                }
            }
            helper.assertTrue(formedPorts == 8, "Fixture did not form all eight precision facades");
            helper.assertTrue(positions.size() == 9, "Fixture did not retain all eight precision facades and controller");
            checkCases(helper, machine, machine::load, positions, base,
                    currentWork(base, recipe.processDefinition(), recipe.signature(), machine.resourceRevision()), pending);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 10)
    public static void electrolyzerEntityRemovalRetainsResourcesAndOldRoots(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.ELECTROLYZER.get());
        var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        machine.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.UP).resolve().orElseThrow()
                .insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false);
        machine.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        var recipe = (ElectrolyzerRecipe) helper.getLevel().getRecipeManager().byKey(id("electrolyzer_water")).orElseThrow();
        CompoundTag base = machine.saveWithFullMetadata();
        long revision = base.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision");
        var before = new ProcessResourceSnapshot(revision, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", ModItems.EMPTY_CANISTER.getId().toString()), new ProcessResourceBalance(2, 16),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(1_000, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.HYDROGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.OXYGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16)));
        UUID owner = UUID.nameUUIDFromBytes((AdvancedRocketryCommunity.MOD_ID + ":electrolyzer|"
                + helper.getLevel().dimension().location() + "|" + machine.getBlockPos().asLong()).getBytes(StandardCharsets.UTF_8));
        checkCases(helper, machine, machine::load, List.of(BlockPos.ZERO), base,
                currentWork(base, recipe.processDefinition(), recipe.signature(), revision),
                prepared(owner, recipe.processDefinition(), before));
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 30)
    public static void rollingPortInvalidRootsAndUnavailableOwnerAreProtectedWithoutLoading(GameTestHelper helper) {
        RollingMachineGameTestFixtures.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            RollingMachineGameTestFixtures.assertPortsValid(helper);
            var port = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_INPUT);
            CompoundTag base = port.saveWithFullMetadata();
            for (String root : List.of("arce_part_binding", "arce_rolling_port")) {
                CompoundTag changed = base.copy(); changed.getCompound(root).putInt("schema_version", 2);
                port.load(changed);
                assertGuarded(helper, List.of(RollingMachineGameTestFixtures.ITEM_INPUT));
            }
            CompoundTag changed = base.copy(); BlockPos unavailable = new BlockPos(20_000_000, 64, 20_000_000);
            CompoundTag owner = changed.getCompound("arce_part_binding").getCompound("controller");
            owner.putInt("x", unavailable.getX()); owner.putInt("y", unavailable.getY()); owner.putInt("z", unavailable.getZ());
            helper.assertFalse(helper.getLevel().hasChunkAt(unavailable), "Fixture owner was already loaded");
            port.load(changed); assertGuarded(helper, List.of(RollingMachineGameTestFixtures.ITEM_INPUT));
            helper.assertFalse(helper.getLevel().hasChunkAt(unavailable), "Guard forced the unavailable owner's chunk");
            port.load(base); assertOrdinary(helper, List.of(RollingMachineGameTestFixtures.ITEM_INPUT));
            helper.succeed();
        });
    }

    private static void checkCases(GameTestHelper helper, BlockEntity machine, Consumer<CompoundTag> load,
            List<BlockPos> positions, CompoundTag base, CompoundTag active, ProcessTransactionJournal pending) {
        assertOrdinary(helper, positions);
        load.accept(active); assertOrdinary(helper, positions);
        List<CompoundTag> cases = new ArrayList<>();
        CompoundTag unproven = active.copy(); unproven.remove(RecipeSignatureMigration.ROOT); cases.add(unproven);
        CompoundTag future = base.copy(); future.getCompound(RecipeSignatureMigration.ROOT).putInt("schema_version", 2); cases.add(future);
        CompoundTag malformed = base.copy(); malformed.getCompound(RecipeSignatureMigration.ROOT).putString("format", "unknown"); cases.add(malformed);
        CompoundTag oversized = base.copy(); oversized.getCompound(RecipeSignatureMigration.ROOT).putString("extra", "x".repeat(2_000)); cases.add(oversized);
        CompoundTag futureProcess = base.copy(); futureProcess.getCompound(ProcessStatePersistence.ROOT).putInt("schema_version", 2); cases.add(futureProcess);
        CompoundTag prepared = active.copy(); prepared.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(pending)); cases.add(prepared);
        CompoundTag badJournal = prepared.copy(); badJournal.getCompound(ProcessJournalPersistence.ROOT).putInt("schema_version", 2); cases.add(badJournal);
        for (CompoundTag input : cases) {
            load.accept(input);
            CompoundTag retained = machine.saveWithFullMetadata();
            assertGuarded(helper, positions);
            helper.assertTrue(retained.equals(machine.saveWithFullMetadata()), "Removal checks altered the controller snapshot");
            for (String root : List.of(RecipeSignatureMigration.ROOT, ProcessStatePersistence.ROOT, ProcessJournalPersistence.ROOT, "arce_machine")) {
                var expected = input.get(root);
                if ((input == prepared || input == badJournal) && ProcessStatePersistence.ROOT.equals(root)) {
                    // A retained journal sets the process refusal state before removal is consulted.
                    // Keep all work, signature, revision and failure fields otherwise exact.
                    CompoundTag recovery = input.getCompound(root).copy();
                    recovery.putString("state", input == prepared ? "recovery_required" : "unsupported_data"); expected = recovery;
                }
                helper.assertTrue(java.util.Objects.equals(expected, retained.get(root)), "Refusal changed retained root " + root);
            }
        }
        load.accept(base); assertOrdinary(helper, positions);
    }

    private static void assertGuarded(GameTestHelper helper, List<BlockPos> positions) {
        var wither = new WitherBoss(EntityType.WITHER, helper.getLevel());
        int drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12)).size();
        for (BlockPos relative : positions) {
            BlockPos absolute = helper.absolutePos(relative); var state = helper.getBlockState(relative);
            CompoundTag before = helper.getBlockEntity(relative).saveWithFullMetadata();
            helper.assertFalse(state.canEntityDestroy(helper.getLevel(), absolute, wither), "Block hook permitted guarded entity removal");
            helper.assertFalse(ForgeEventFactory.onEntityDestroyBlock(wither, absolute, state), "Living event permitted guarded entity removal");
            helper.assertTrue(state.equals(helper.getBlockState(relative)), "Removal guard changed the block");
            helper.assertTrue(before.equals(helper.getBlockEntity(relative).saveWithFullMetadata()), "Removal guard changed a resource/binding snapshot");
        }
        helper.assertTrue(drops == helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                new AABB(helper.absolutePos(BlockPos.ZERO)).inflate(12)).size(), "Guarded checks spawned resource drops");
    }

    private static void assertOrdinary(GameTestHelper helper, List<BlockPos> positions) {
        var wither = new WitherBoss(EntityType.WITHER, helper.getLevel());
        for (BlockPos relative : positions) {
            BlockPos absolute = helper.absolutePos(relative); var state = helper.getBlockState(relative);
            helper.assertTrue(state.canEntityDestroy(helper.getLevel(), absolute, wither), "Supported ordinary block was restricted");
            helper.assertTrue(ForgeEventFactory.onEntityDestroyBlock(wither, absolute, state), "Supported ordinary living event was canceled");
        }
    }

    private static CompoundTag currentWork(CompoundTag base, ProcessDefinition recipe, String signature, long revision) {
        CompoundTag parent = base.copy();
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, revision, Optional.of(new ProcessProgress(recipe.id(), recipe.durationTicks(), recipe.totalEnergy())),
                Optional.of(signature), Optional.empty(), ProcessFailure.NONE)));
        if (parent.contains("arce_machine")) {
            parent.getCompound("arce_machine").putInt("progress", recipe.durationTicks());
            parent.getCompound("arce_machine").putString("active_recipe", recipe.id());
        }
        new RecipeSignatureMigration().save(parent, recipe.id()); return parent;
    }

    private static ProcessTransactionJournal prepared(UUID owner, ProcessDefinition recipe, ProcessResourceSnapshot before) {
        return ProcessTransactionJournal.prepared(UUID.randomUUID(), owner,
                ProcessMachineLogic.simulate(recipe, before).plan().orElseThrow());
    }

    private static List<BlockPos> protectedRolling() {
        return List.of(RollingMachineGameTestFixtures.CONTROLLER, RollingMachineGameTestFixtures.ITEM_INPUT,
                RollingMachineGameTestFixtures.FLUID_INPUT, RollingMachineGameTestFixtures.ENERGY_INPUT, RollingMachineGameTestFixtures.ITEM_OUTPUT);
    }
    private static ResourceLocation id(String path) { return new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, path); }
    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String id) { return new ProcessResourceKey(kind, channel, id); }
}
