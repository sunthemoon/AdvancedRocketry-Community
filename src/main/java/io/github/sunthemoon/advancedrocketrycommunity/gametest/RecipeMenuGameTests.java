package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.MachineMenuOpening;
import io.github.sunthemoon.advancedrocketrycommunity.machine.menu.RecipeMenuReason;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStateData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineMenu;
import io.github.sunthemoon.advancedrocketrycommunity.machine.rolling.RollingMachineRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.buffer.Unpooled;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual registered menus and paused-work resources. Not a peer/client or native-restart test. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RecipeMenuGameTests {
    private RecipeMenuGameTests() { }

    @GameTest(template = "rocket_test", batch = "machine_menu", timeoutTicks = 30)
    public static void rollingReasonUsesAppendedSlotWithoutMutatingPausedResources(GameTestHelper helper) {
        RollingMachineGameTestFixtures.placeStructure(helper);
        AtomicReference<CompoundTag> retained = new AtomicReference<>();
        AtomicReference<List<CompoundTag>> resources = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            var machine = RollingMachineGameTestFixtures.controller(helper);
            var input = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.ITEM_INPUT);
            input.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false);
            var water = RollingMachineGameTestFixtures.port(helper, RollingMachineGameTestFixtures.FLUID_INPUT);
            water.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                    .fill(new FluidStack(Fluids.WATER, 100), IFluidHandler.FluidAction.EXECUTE);
            CompoundTag parent = machine.saveWithoutMetadata();
            legacyWork(parent, "advancedrocketrycommunity:rolling_iron_bars", machine.resourceRevision());
            retained.set(parent.getCompound(ProcessStatePersistence.ROOT).copy());
            resources.set(RollingMachineGameTestFixtures.allPorts(helper).stream()
                    .map(BlockEntity::saveWithoutMetadata).toList());
            machine.load(parent);
            Player player = viewer(helper, machine.getBlockPos());
            RollingMachineMenu menu = (RollingMachineMenu) machine.createMenu(60, player.getInventory(), player);
            helper.assertTrue(menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_PENDING,
                    "Rolling server provider did not expose pending reason");
            checkLocalLayout(helper, machine.getBlockPos(), 25, 24,
                    buffer -> new RollingMachineMenu(61, player.getInventory(), buffer));
            RollingMachineRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(20, () -> {
            var machine = RollingMachineGameTestFixtures.controller(helper);
            Player player = viewer(helper, machine.getBlockPos());
            RollingMachineMenu menu = (RollingMachineMenu) machine.createMenu(62, player.getInventory(), player);
            helper.assertTrue(menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_UNPROVEN,
                    "Rolling settled provider did not expose unproven reason");
            helper.assertTrue(retained.get().equals(machine.saveWithoutMetadata().get(ProcessStatePersistence.ROOT)),
                    "Reading Rolling menu changed retained work");
            helper.assertTrue(resources.get().equals(RollingMachineGameTestFixtures.allPorts(helper).stream()
                    .map(BlockEntity::saveWithoutMetadata).toList()), "Rolling paused resources changed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "machine_menu", timeoutTicks = 30)
    public static void precisionReasonUsesAppendedSlotWithoutMutatingPausedResources(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        AtomicReference<CompoundTag> retained = new AtomicReference<>();
        AtomicReference<List<CompoundTag>> resources = new AtomicReference<>();
        List<BlockPos> channels = List.of(PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1,
                new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(1, 1, 3),
                PrecisionAssemblerGameTests.OUTPUT_0, PrecisionAssemblerGameTests.OUTPUT_1,
                PrecisionAssemblerGameTests.ENERGY);
        helper.runAtTickTime(8, () -> {
            var machine = PrecisionAssemblerGameTests.controller(helper);
            PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .insertItem(0, new ItemStack(Items.IRON_INGOT), false);
            CompoundTag parent = machine.saveWithoutMetadata();
            legacyWork(parent, "advancedrocketrycommunity:precision_control_circuit", machine.resourceRevision());
            retained.set(parent.getCompound(ProcessStatePersistence.ROOT).copy());
            resources.set(channels.stream().map(position -> PrecisionAssemblerGameTests.port(helper, position)
                    .saveWithoutMetadata()).toList());
            machine.load(parent);
            Player player = viewer(helper, machine.getBlockPos());
            PrecisionAssemblerMenu menu = (PrecisionAssemblerMenu) machine.createMenu(63, player.getInventory(), player);
            helper.assertTrue(menu != null && menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_PENDING,
                    "Precision server provider did not expose pending reason");
            checkLocalLayout(helper, machine.getBlockPos(), 23, 22,
                    buffer -> new PrecisionAssemblerMenu(64, player.getInventory(), buffer));
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), machine.getBlockPos());
        });
        helper.runAtTickTime(20, () -> {
            var machine = PrecisionAssemblerGameTests.controller(helper);
            Player player = viewer(helper, machine.getBlockPos());
            PrecisionAssemblerMenu menu = (PrecisionAssemblerMenu) machine.createMenu(65, player.getInventory(), player);
            helper.assertTrue(menu != null && menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_UNPROVEN,
                    "Precision settled provider did not expose unproven reason");
            helper.assertTrue(retained.get().equals(machine.saveWithoutMetadata().get(ProcessStatePersistence.ROOT)),
                    "Reading Precision menu changed retained work");
            helper.assertTrue(resources.get().equals(channels.stream().map(position ->
                    PrecisionAssemblerGameTests.port(helper, position).saveWithoutMetadata()).toList()),
                    "Precision paused resources changed");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "machine_menu", timeoutTicks = 30)
    public static void electrolyzerReasonUsesAppendedSlotWithoutMutatingPausedResources(GameTestHelper helper) {
        helper.setBlock(BlockPos.ZERO, ModBlocks.ELECTROLYZER.get());
        AtomicReference<CompoundTag> retained = new AtomicReference<>();
        AtomicReference<CompoundTag> resources = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
            machine.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow()
                    .insertItem(0, new ItemStack(ModItems.EMPTY_CANISTER.get(), 2), false);
            machine.getCapability(ForgeCapabilities.FLUID_HANDLER).resolve().orElseThrow()
                    .fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE);
            CompoundTag parent = machine.saveWithoutMetadata();
            legacyWork(parent, "advancedrocketrycommunity:electrolyzer_water",
                    parent.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision"));
            // The legacy resource mirror must agree with the typed process cut.
            parent.getCompound("arce_machine").putInt("progress", 1);
            parent.getCompound("arce_machine").putString("active_recipe", "advancedrocketrycommunity:electrolyzer_water");
            retained.set(parent.getCompound(ProcessStatePersistence.ROOT).copy());
            resources.set(parent.getCompound("arce_machine").copy());
            machine.load(parent);
            Player player = viewer(helper, machine.getBlockPos());
            ElectrolyzerMenu menu = (ElectrolyzerMenu) machine.createMenu(66, player.getInventory(), player);
            helper.assertTrue(menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_PENDING,
                    "Electrolyzer server provider did not expose pending reason");
            checkLocalLayout(helper, machine.getBlockPos(), 8, 7,
                    buffer -> new ElectrolyzerMenu(67, player.getInventory(), buffer));
        });
        helper.runAtTickTime(20, () -> {
            var machine = (ElectrolyzerBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
            Player player = viewer(helper, machine.getBlockPos());
            ElectrolyzerMenu menu = (ElectrolyzerMenu) machine.createMenu(68, player.getInventory(), player);
            helper.assertTrue(menu.recipeReason() == RecipeMenuReason.SIGNATURE_MIGRATION_UNPROVEN,
                    "Electrolyzer settled provider did not expose unproven reason");
            CompoundTag saved = machine.saveWithoutMetadata();
            helper.assertTrue(retained.get().equals(saved.get(ProcessStatePersistence.ROOT)),
                    "Reading Electrolyzer menu changed retained work");
            helper.assertTrue(resources.get().equals(saved.get("arce_machine")), "Electrolyzer paused resources changed");
            helper.succeed();
        });
    }

    private static void checkLocalLayout(GameTestHelper helper, BlockPos position, int count, int reasonIndex,
                                         Function<FriendlyByteBuf, AbstractContainerMenu> constructor) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            MachineMenuOpening.write(buffer, position, count);
            AbstractContainerMenu menu = constructor.apply(buffer);
            helper.assertTrue(buffer.readableBytes() == 0, "Menu left opening bytes unread");
            // Set the unchanged generic failure/status before the appended display field.
            menu.setData(count == 8 ? 6 : 13, count == 8 ? 7 : 6);
            for (int id = 0; id <= 6; id++) {
                menu.setData(reasonIndex, id);
                helper.assertTrue(reason(menu).networkId() == id, "Appended reason did not round-trip");
            }
            menu.setData(reasonIndex, 99);
            helper.assertTrue(reason(menu) == RecipeMenuReason.NONE, "Unknown reason did not use generic fallback");
            menu.setData(reasonIndex, 1);
            menu.setData(count == 8 ? 6 : 12, 8);
            helper.assertTrue(reason(menu) == RecipeMenuReason.NONE, "Unsupported state lost display precedence");
            boolean overCountRejected = false;
            try { menu.setData(count, 0); } catch (IndexOutOfBoundsException expected) { overCountRejected = true; }
            helper.assertTrue(overCountRejected, "Client allocation accepted an extra data index");
            buffer.clear(); buffer.writeBlockPos(position);
            boolean oldOpeningRejected = false;
            try { constructor.apply(buffer); } catch (IllegalArgumentException expected) { oldOpeningRejected = true; }
            helper.assertTrue(oldOpeningRejected, "Legacy unversioned opening constructed a usable menu");
        } finally { buffer.release(); }
    }

    private static RecipeMenuReason reason(AbstractContainerMenu menu) {
        if (menu instanceof RollingMachineMenu rolling) { return rolling.recipeReason(); }
        if (menu instanceof PrecisionAssemblerMenu precision) { return precision.recipeReason(); }
        return ((ElectrolyzerMenu) menu).recipeReason();
    }

    private static Player viewer(GameTestHelper helper, BlockPos absolute) {
        Player player = helper.makeMockPlayer();
        player.setPos(absolute.getX() + 0.5D, absolute.getY() + 0.5D, absolute.getZ() + 0.5D);
        return player;
    }

    private static void legacyWork(CompoundTag parent, String id, long revision) {
        parent.remove(RecipeSignatureMigration.ROOT);
        parent.put(ProcessStatePersistence.ROOT, ProcessStatePersistence.encode(new ProcessStateData(
                ProcessMachineState.RUNNING, revision, Optional.of(new ProcessProgress(id, 1, 0)),
                Optional.of("a".repeat(64)), Optional.empty(), ProcessFailure.NONE)));
    }
}
