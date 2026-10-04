package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.*;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.LegacyKernelRecipeProof;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Registered-adapter synthetic schema-1 cuts. These are not native historical world/restart evidence. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeSignatureMigrationGameTests {
    private static final String ROLLING_SHA = "575101496cbaf0038049fe2d373a662a05016d570cc54d0f7a6f2db026cb59cc";
    private static final String PRECISION_SHA = "ea5522eb8e75090c8afc4b7891829648a248f380781897b7c3ea1fdfb2b6e5a9";
    private static final String ELECTRO_SHA = "5eacb5d39d636c9045ea0e1e198e41e89d411dc2cc8f13c9994466cdfb53d152";

    private RecipeSignatureMigrationGameTests() { }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 20)
    public static void allThreeTagSerializersSyncUnboundJsonWithoutResolving(GameTestHelper helper) {
        RollingMachineRecipe oldRolling = oldRolling();
        JsonObject rollingJson = oldRolling.jsonPayload(); rollingJson.add("ingredient", unbound());
        var rollingSerializer = new RollingMachineRecipe.Serializer();
        RollingMachineRecipe rolling = rollingSerializer.fromJson(oldRolling.getId(), rollingJson);
        PrecisionAssemblerRecipe oldPrecision = oldPrecision();
        JsonObject precisionJson = oldPrecision.jsonPayload();
        precisionJson.getAsJsonArray("inputs").get(0).getAsJsonObject().add("ingredient", unbound());
        var precisionSerializer = new PrecisionAssemblerRecipe.Serializer();
        PrecisionAssemblerRecipe precision = precisionSerializer.fromJson(oldPrecision.getId(), precisionJson);
        ElectrolyzerRecipe oldElectro = oldElectro();
        JsonObject electroJson = oldElectro.jsonPayload(); electroJson.add("ingredient", unbound());
        var electroSerializer = new ElectrolyzerRecipe.Serializer();
        ElectrolyzerRecipe electro = electroSerializer.fromJson(oldElectro.getId(), electroJson);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            rollingSerializer.toNetwork(buffer, rolling);
            helper.assertTrue(rolling.signature().equals(rollingSerializer.fromNetwork(rolling.getId(), buffer).signature()),
                    "Rolling wire changed the unbound JSON signature");
            precisionSerializer.toNetwork(buffer, precision);
            helper.assertTrue(precision.signature().equals(precisionSerializer.fromNetwork(precision.getId(), buffer).signature()),
                    "Precision wire changed the unbound JSON signature");
            electroSerializer.toNetwork(buffer, electro);
            helper.assertTrue(electro.signature().equals(electroSerializer.fromNetwork(electro.getId(), buffer).signature()),
                    "Electrolyzer wire changed the unbound JSON signature");
            helper.assertTrue(buffer.readableBytes() == 0, "Recipe wire left unread bytes");
            helper.assertFalse(rolling.available() || precision.available() || electro.available(),
                    "An unbound tag was treated as a valid recipe");
        } finally { buffer.release(); }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 100)
    public static void rollingHistoricalPartialIsRetainedAcrossTicksAndTwoNbtLoads(GameTestHelper helper) {
        AtomicReference<RetainedCut> cut = new AtomicReference<>();
        RollingMachineGameTestFixtures.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            seedRolling(helper, true);
            var machine = RollingMachineGameTestFixtures.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            legacyProgress(saved, oldRolling().processDefinition(), 40, machine.resourceRevision(), ROLLING_SHA);
            cut.set(rollingCut(helper, saved));
            machine.load(saved); RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(15, () -> {
            var machine = RollingMachineGameTestFixtures.controller(helper);
            cut.get().assertUnchanged(helper, machine.saveWithFullMetadata());
            int ticks = machine.processProgress().orElseThrow().progressTicks();
            machine.load(machine.saveWithFullMetadata());
            helper.assertTrue(machine.processProgress().orElseThrow().progressTicks() == ticks, "First load changed progress");
            machine.load(machine.saveWithFullMetadata());
            helper.assertTrue(machine.processProgress().orElseThrow().progressTicks() == ticks, "Second load changed progress");
            RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(90, () -> {
            cut.get().assertUnchanged(helper, RollingMachineGameTestFixtures.controller(helper).saveWithFullMetadata());
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 40)
    public static void rollingHistoricalPreparedJournalIsRetainedWithoutResourceReplay(GameTestHelper helper) {
        AtomicReference<RetainedCut> cut = new AtomicReference<>();
        RollingMachineGameTestFixtures.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            seedRolling(helper, false);
            var machine = RollingMachineGameTestFixtures.controller(helper);
            var recipe = (RollingMachineRecipe) helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "rolling_iron_bars")).orElseThrow();
            ProcessResourceSnapshot before = rollingBefore(machine.resourceRevision());
            CompoundTag saved = machine.saveWithFullMetadata();
            legacyProgress(saved, recipe.processDefinition(), 100, before.revision(), ROLLING_SHA);
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                    UUID.randomUUID(), machine.controllerState().machineInstanceId(),
                    ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow())));
            cut.set(rollingCut(helper, saved));
            machine.load(saved); RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(20, () -> {
            var machine = RollingMachineGameTestFixtures.controller(helper);
            cut.get().assertUnchanged(helper, machine.saveWithFullMetadata());
            machine.load(machine.saveWithFullMetadata()); machine.load(machine.saveWithFullMetadata());
            RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(30, () -> {
            cut.get().assertUnchanged(helper, RollingMachineGameTestFixtures.controller(helper).saveWithFullMetadata());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 60)
    public static void precisionHistoricalPartialIsRetainedAcrossTicksAndTwoNbtLoads(GameTestHelper helper) {
        AtomicReference<RetainedCut> cut = new AtomicReference<>();
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().receiveEnergy(800, false);
            var machine = PrecisionAssemblerGameTests.controller(helper); CompoundTag saved = machine.saveWithFullMetadata();
            legacyProgress(saved, oldPrecision().processDefinition(), 10, machine.resourceRevision(), PRECISION_SHA);
            cut.set(precisionCut(helper, saved));
            machine.load(saved); PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(12, () -> {
            var machine = PrecisionAssemblerGameTests.controller(helper);
            cut.get().assertUnchanged(helper, machine.saveWithFullMetadata());
            int ticks = machine.processProgress().orElseThrow().progressTicks();
            machine.load(machine.saveWithFullMetadata()); machine.load(machine.saveWithFullMetadata());
            helper.assertTrue(machine.processProgress().orElseThrow().progressTicks() == ticks, "Loads changed precision progress");
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(45, () -> {
            cut.get().assertUnchanged(helper, PrecisionAssemblerGameTests.controller(helper).saveWithFullMetadata());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 40)
    public static void precisionHistoricalPreparedJournalIsRetainedWithoutResourceReplay(GameTestHelper helper) {
        AtomicReference<RetainedCut> cut = new AtomicReference<>();
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper); var machine = PrecisionAssemblerGameTests.controller(helper);
            var recipe = oldPrecision(); var pending = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            CompoundTag saved = machine.saveWithFullMetadata();
            legacyProgress(saved, recipe.processDefinition(), 20, machine.resourceRevision(), PRECISION_SHA);
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(pending));
            cut.set(precisionCut(helper, saved));
            machine.load(saved); PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(20, () -> {
            var machine = PrecisionAssemblerGameTests.controller(helper);
            cut.get().assertUnchanged(helper, machine.saveWithFullMetadata());
            machine.load(machine.saveWithFullMetadata()); machine.load(machine.saveWithFullMetadata());
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(30, () -> {
            cut.get().assertUnchanged(helper, PrecisionAssemblerGameTests.controller(helper).saveWithFullMetadata());
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 100)
    public static void electrolyzerHistoricalPartialIsRetainedAcrossTicksAndTwoNbtLoads(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = seedElectro(helper, true); CompoundTag saved = machine.saveWithoutMetadata();
        legacyProgress(saved, oldElectro().processDefinition(), 40,
                saved.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision"), ELECTRO_SHA);
        saved.getCompound("arce_machine").putInt("progress", 40);
        saved.getCompound("arce_machine").putString("active_recipe", oldElectro().getId().toString());
        RetainedCut cut = new RetainedCut(retainedRoots(saved), Map.of()); machine.load(saved);
        helper.runAtTickTime(8, () -> {
            cut.assertUnchanged(helper, machine.saveWithoutMetadata()); int ticks = machine.progress();
            machine.load(machine.saveWithoutMetadata()); machine.load(machine.saveWithoutMetadata());
            helper.assertTrue(machine.progress() == ticks, "Loads changed electrolyzer progress");
        });
        helper.runAtTickTime(90, () -> { cut.assertUnchanged(helper, machine.saveWithoutMetadata()); helper.succeed(); });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 35)
    public static void electrolyzerHistoricalPreparedJournalIsRetainedWithoutResourceReplay(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = seedElectro(helper, false); var recipe = oldElectro();
        CompoundTag saved = machine.saveWithoutMetadata();
        long revision = saved.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision");
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(revision, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", ModItems.EMPTY_CANISTER.getId().toString()), new ProcessResourceBalance(2, 16),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(1_000, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.HYDROGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.OXYGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16)));
        legacyProgress(saved, recipe.processDefinition(), 100, revision, ELECTRO_SHA);
        saved.getCompound("arce_machine").putInt("progress", 100);
        saved.getCompound("arce_machine").putString("active_recipe", recipe.getId().toString());
        UUID owner = UUID.nameUUIDFromBytes((AdvancedRocketryCommunity.MOD_ID + ":electrolyzer|"
                + helper.getLevel().dimension().location() + "|" + machine.getBlockPos().asLong()).getBytes(StandardCharsets.UTF_8));
        saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                UUID.randomUUID(), owner, ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow())));
        RetainedCut cut = new RetainedCut(retainedRoots(saved), Map.of()); machine.load(saved);
        helper.runAtTickTime(8, () -> {
            cut.assertUnchanged(helper, machine.saveWithoutMetadata());
            machine.load(machine.saveWithoutMetadata()); machine.load(machine.saveWithoutMetadata());
        });
        helper.runAtTickTime(25, () -> { cut.assertUnchanged(helper, machine.saveWithoutMetadata()); helper.succeed(); });
    }

    @GameTest(template = "rocket_test", batch = "recipe_signatures", timeoutTicks = 40)
    public static void rollingCurrentPreparedJournalRecoversOriginalPlanOnce(GameTestHelper helper) {
        RollingMachineGameTestFixtures.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            seedRolling(helper, false);
            var machine = RollingMachineGameTestFixtures.controller(helper);
            var recipe = (RollingMachineRecipe) helper.getLevel().getRecipeManager()
                    .byKey(new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "rolling_iron_bars")).orElseThrow();
            helper.assertTrue(recipe.hasTagIngredients(), "Current journal fixture did not use the live tag recipe");
            helper.assertFalse(recipe.signature().equals(oldRolling().signature()),
                    "Current tag recipe was confused with historical item-only JSON");
            ProcessResourceSnapshot before = rollingBefore(machine.resourceRevision());
            CompoundTag saved = machine.saveWithFullMetadata();
            legacyProgress(saved, recipe.processDefinition(), 100, before.revision(), recipe.signature());
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                    UUID.randomUUID(), machine.controllerState().machineInstanceId(),
                    ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow())));
            new RecipeSignatureMigration().save(saved, recipe.getId().toString());
            machine.load(saved); RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(20, () -> {
            var machine = RollingMachineGameTestFixtures.controller(helper); assertRollingComplete(helper, 0);
            helper.assertFalse(machine.saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT), "Current journal was not cleared");
            machine.load(machine.saveWithFullMetadata()); machine.load(machine.saveWithFullMetadata());
            RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(30, () -> { assertRollingComplete(helper, 0); helper.succeed(); });
    }

    @GameTest(template = "empty", batch = "recipe_signatures", timeoutTicks = 35)
    public static void electrolyzerCurrentPreparedJournalRecoversOriginalPlanOnce(GameTestHelper helper) {
        ElectrolyzerBlockEntity machine = seedElectro(helper, false);
        var recipe = (ElectrolyzerRecipe) helper.getLevel().getRecipeManager()
                .byKey(new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "electrolyzer_water")).orElseThrow();
        CompoundTag saved = machine.saveWithoutMetadata();
        long revision = saved.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision");
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(revision, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", ModItems.EMPTY_CANISTER.getId().toString()), new ProcessResourceBalance(2, 16),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(1_000, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.HYDROGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16),
                key(ProcessResourceKind.ITEM, "item_output", ModItems.OXYGEN_CANISTER.getId().toString()), new ProcessResourceBalance(0, 16)));
        legacyProgress(saved, recipe.processDefinition(), 100, revision, recipe.signature());
        saved.getCompound("arce_machine").putInt("progress", 100);
        saved.getCompound("arce_machine").putString("active_recipe", recipe.getId().toString());
        UUID owner = UUID.nameUUIDFromBytes((AdvancedRocketryCommunity.MOD_ID + ":electrolyzer|"
                + helper.getLevel().dimension().location() + "|" + machine.getBlockPos().asLong()).getBytes(StandardCharsets.UTF_8));
        saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(ProcessTransactionJournal.prepared(
                UUID.randomUUID(), owner, ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow())));
        new RecipeSignatureMigration().save(saved, recipe.getId().toString()); machine.load(saved);
        helper.runAtTickTime(8, () -> {
            assertElectroComplete(helper, machine, 0);
            machine.load(machine.saveWithoutMetadata()); machine.load(machine.saveWithoutMetadata());
        });
        helper.runAtTickTime(25, () -> { assertElectroComplete(helper, machine, 0); helper.succeed(); });
    }

    private static void legacyProgress(CompoundTag parent, ProcessDefinition recipe, int ticks, long revision, String sha) {
        parent.remove(RecipeSignatureMigration.ROOT);
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, revision, Optional.of(new ProcessProgress(recipe.id(), ticks,
                        (long) ticks * recipe.energyPerTick())), Optional.of(sha), Optional.empty(), ProcessFailure.NONE)));
    }

    private static CompoundTag retainedRoots(CompoundTag parent) {
        CompoundTag selected = new CompoundTag();
        for (String root : List.of(ProcessStatePersistence.ROOT, ProcessJournalPersistence.ROOT,
                "arce_machine", "arce_precision_resources", "arce_rolling_port", "arce_precision_port")) {
            if (parent.contains(root)) { selected.put(root, parent.get(root).copy()); }
        }
        return selected;
    }

    private static RetainedCut rollingCut(GameTestHelper helper, CompoundTag parent) {
        Map<BlockEntity, CompoundTag> ports = new LinkedHashMap<>();
        for (var port : RollingMachineGameTestFixtures.allPorts(helper)) {
            ports.put(port, retainedRoots(port.saveWithoutMetadata()));
        }
        return new RetainedCut(retainedRoots(parent), Map.copyOf(ports));
    }

    private static RetainedCut precisionCut(GameTestHelper helper, CompoundTag parent) {
        Map<BlockEntity, CompoundTag> ports = new LinkedHashMap<>();
        for (BlockPos position : List.of(PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1,
                PrecisionAssemblerGameTests.OUTPUT_0, PrecisionAssemblerGameTests.OUTPUT_1, PrecisionAssemblerGameTests.ENERGY)) {
            var port = PrecisionAssemblerGameTests.port(helper, position);
            ports.put(port, retainedRoots(port.saveWithoutMetadata()));
        }
        return new RetainedCut(retainedRoots(parent), Map.copyOf(ports));
    }

    private record RetainedCut(CompoundTag controller, Map<BlockEntity, CompoundTag> ports) {
        private void assertUnchanged(GameTestHelper helper, CompoundTag saved) {
            helper.assertFalse(saved.contains(RecipeSignatureMigration.ROOT), "Unwitnessed work was marked current");
            helper.assertTrue(controller.equals(retainedRoots(saved)), "Old work/resources were rewritten or replayed");
            for (var entry : ports.entrySet()) {
                helper.assertTrue(entry.getValue().equals(retainedRoots(entry.getKey().saveWithoutMetadata())),
                        "Port input/output/fluid/energy changed while historical work was paused");
            }
        }
    }

    private static void seedRolling(GameTestHelper helper, boolean energy) {
        RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_INPUT)
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow().insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
        RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.FLUID_INPUT)
                .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                .fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
        if (energy) {
            var storage = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ENERGY_INPUT)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            storage.receiveEnergy(1_000, false); storage.receiveEnergy(1_000, false);
        }
    }

    private static ProcessResourceSnapshot rollingBefore(long revision) {
        return new ProcessResourceSnapshot(revision, Map.of(
                key(ProcessResourceKind.ITEM, "item_input", "minecraft:iron_ingot"), new ProcessResourceBalance(2, 64),
                key(ProcessResourceKind.FLUID, "fluid_input", "minecraft:water"), new ProcessResourceBalance(100, 4_000),
                key(ProcessResourceKind.ITEM, "item_output", "minecraft:iron_bars"), new ProcessResourceBalance(0, 64)));
    }

    private static void assertRollingComplete(GameTestHelper helper, int energy) {
        var input = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_INPUT);
        var output = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_OUTPUT);
        helper.assertTrue(input.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow().getStackInSlot(0).isEmpty(),
                "Rolling retained a consumed input");
        ItemStack result = output.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow().getStackInSlot(0);
        helper.assertTrue(result.is(Items.IRON_BARS) && result.getCount() == 8, "Rolling result was not produced exactly once");
        helper.assertTrue(RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.FLUID_INPUT)
                .getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow().getFluidInTank(0).isEmpty(), "Rolling water changed incorrectly");
        helper.assertTrue(RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ENERGY_INPUT)
                .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow().getEnergyStored() == energy, "Rolling energy changed incorrectly");
    }

    private static ElectrolyzerBlockEntity seedElectro(GameTestHelper helper, boolean energy) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.ELECTROLYZER.get());
        var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
        machine.getCapability(ForgeCapabilities.ITEM_HANDLER, net.minecraft.core.Direction.UP).resolve().orElseThrow()
                .insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false);
        machine.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
        if (energy) {
            var storage = machine.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            storage.receiveEnergy(1_000, false); storage.receiveEnergy(1_000, false);
        }
        return machine;
    }

    private static void assertElectroComplete(GameTestHelper helper, ElectrolyzerBlockEntity machine, int energy) {
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_INPUT).isEmpty(), "Electrolyzer retained input");
        helper.assertTrue(machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_HYDROGEN).getCount() == 1
                && machine.menuInventory().getStackInSlot(ElectrolyzerBlockEntity.SLOT_OXYGEN).getCount() == 1,
                "Electrolyzer outputs were not produced exactly once");
        helper.assertTrue(machine.waterAmount() == 0 && machine.energyStored() == energy, "Electrolyzer resources changed incorrectly");
        helper.assertFalse(machine.saveWithoutMetadata().contains(ProcessJournalPersistence.ROOT), "Electrolyzer retained its journal");
    }

    private static RollingMachineRecipe oldRolling() {
        var id = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "rolling_iron_bars");
        return new RollingMachineRecipe.Serializer().fromJson(id,
                LegacyKernelRecipeProof.payload(id, "advancedrocketrycommunity:rolling"));
    }
    private static PrecisionAssemblerRecipe oldPrecision() {
        var id = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "precision_control_circuit");
        return new PrecisionAssemblerRecipe.Serializer().fromJson(id,
                LegacyKernelRecipeProof.payload(id, "advancedrocketrycommunity:precision_assembling"));
    }
    private static ElectrolyzerRecipe oldElectro() {
        var id = new ResourceLocation(AdvancedRocketryCommunity.MOD_ID, "electrolyzer_water");
        return new ElectrolyzerRecipe.Serializer().fromJson(id,
                LegacyKernelRecipeProof.payload(id, "advancedrocketrycommunity:electrolyzing"));
    }
    private static com.google.gson.JsonElement unbound() { return JsonParser.parseString("{\"tag\":\"arce_test:unbound\"}"); }
    private static ProcessResourceKey key(ProcessResourceKind kind, String channel, String id) {
        return new ProcessResourceKey(kind, channel, id);
    }
}
