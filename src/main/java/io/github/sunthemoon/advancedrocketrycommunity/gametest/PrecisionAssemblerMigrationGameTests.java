package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerChannels;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Legacy physical Item roots migrate once and become inert after controller ownership. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerMigrationGameTests {
    static final BlockPos[] ITEM_PORTS = {
            PrecisionAssemblerGameTests.INPUT_0,
            PrecisionAssemblerGameTests.INPUT_1,
            new BlockPos(1, 1, 2),
            new BlockPos(3, 1, 2),
            new BlockPos(1, 1, 3),
            PrecisionAssemblerGameTests.OUTPUT_0,
            PrecisionAssemblerGameTests.OUTPUT_1
    };

    private PrecisionAssemblerMigrationGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 55)
    public static void legacyPortItemsMoveToControllerOnceAndDoNotDropTwice(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            stageLegacySave(helper, false);
            helper.assertTrue(!PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Unmigrated Item port remained writable");
        });
        helper.runAtTickTime(23, () -> {
            assertMigrated(helper);
            helper.assertTrue(helper.getLevel().destroyBlock(
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER), true),
                    "Migrated controller could not be broken");
            helper.assertTrue(helper.getLevel().destroyBlock(
                    helper.absolutePos(PrecisionAssemblerGameTests.INPUT_0), true),
                    "Migrated input port could not be broken");
            helper.assertTrue(helper.getLevel().destroyBlock(
                    helper.absolutePos(PrecisionAssemblerGameTests.INPUT_1), true),
                    "Second migrated input port could not be broken");
        });
        helper.runAtTickTime(28, () -> {
            helper.assertItemEntityCountIs(Items.IRON_INGOT,
                    PrecisionAssemblerGameTests.CONTROLLER, 1.5D, 2);
            helper.assertItemEntityCountIs(Items.REDSTONE,
                    PrecisionAssemblerGameTests.CONTROLLER, 1.5D, 2);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 45)
    public static void preparedLegacySnapshotResumesWithoutReimport(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            stageLegacySave(helper, true);
        });
        helper.runAtTickTime(24, () -> {
            assertMigrated(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 45)
    public static void partiallySavedLegacyMarkersResumeWithoutReimport(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            stageLegacySave(helper, true);
            UUID machineId = PrecisionAssemblerGameTests.controller(helper)
                    .controllerState().machineInstanceId();
            for (int index = 0; index < 3; index++) {
                PrecisionAssemblerPortBlockEntity port = PrecisionAssemblerGameTests.port(
                        helper, ITEM_PORTS[index]);
                CompoundTag saved = port.saveWithFullMetadata();
                CompoundTag marker = new CompoundTag();
                marker.putInt("schema_version", 1);
                marker.putString("machine_id", machineId.toString());
                marker.putString("channel", PrecisionAssemblerChannels.input(index));
                saved.put("arce_precision_port_migration", marker);
                port.load(saved);
                port.setChanged();
            }
        });
        helper.runAtTickTime(24, () -> {
            assertMigrated(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 35)
    public static void changedLegacyPortStopsPreparedMigrationWithoutOverwrite(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            stageLegacySave(helper, true);
            PrecisionAssemblerPortBlockEntity port = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.INPUT_0);
            CompoundTag saved = port.saveWithFullMetadata();
            saved.getCompound("arce_precision_port").put("item",
                    new ItemStack(Items.GOLD_INGOT).save(new CompoundTag()));
            port.load(saved);
            port.setChanged();
        });
        helper.runAtTickTime(22, () -> {
            PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue("preparing".equals(controller.saveWithFullMetadata()
                            .getCompound("arce_precision_resources").getString("phase")),
                    "Changed legacy Item root was activated without a matching controller snapshot");
            helper.assertTrue(!PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0)
                            .getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Changed legacy Item root exposed an automation capability");
            helper.assertTrue(PrecisionAssemblerGameTests.storedControllerItemCount(
                            helper, PrecisionAssemblerGameTests.INPUT_0) == 2,
                    "Changed legacy port overwrote the prepared controller snapshot");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 65)
    public static void controllerSnapshotsKeepItemBatchTogetherAcrossPortSaveCuts(GameTestHelper helper) {
        AtomicReference<CompoundTag> before = new AtomicReference<>();
        AtomicReference<CompoundTag> after = new AtomicReference<>();
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.ENERGY)
                    .getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(800, false) == 800,
                    "Crash-cut fixture rejected Energy");
            before.set(PrecisionAssemblerGameTests.controller(helper).saveWithFullMetadata());
        });
        helper.runAtTickTime(39, () -> {
            PrecisionAssemblerGameTests.assertCompletedBatch(helper);
            after.set(PrecisionAssemblerGameTests.controller(helper).saveWithFullMetadata());
            for (BlockPos position : ITEM_PORTS) {
                CompoundTag local = PrecisionAssemblerGameTests.port(helper, position)
                        .saveWithFullMetadata().getCompound("arce_precision_port");
                helper.assertTrue(local.getCompound("item").isEmpty(),
                        "New Item port persisted a second active resource copy");
            }
            PrecisionAssemblerGameTests.controller(helper).load(before.get().copy());
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(45, () -> {
            PrecisionAssemblerGameTests.assertInputs(helper, 2, 2);
            helper.assertTrue(PrecisionAssemblerGameTests.storedControllerItemCount(
                    helper, PrecisionAssemblerGameTests.OUTPUT_0) == 0,
                    "Pre-batch controller snapshot inherited a post-batch output port");
            PrecisionAssemblerGameTests.controller(helper).load(after.get().copy());
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(52, () -> {
            PrecisionAssemblerGameTests.assertCompletedBatch(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void futureControllerItemRootRemainsPreservedAndBlocked(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = controller.saveWithFullMetadata();
            CompoundTag future = saved.getCompound("arce_precision_resources").copy();
            future.putInt("schema_version", 2);
            future.putString("unknown_payload", "keep");
            saved.put("arce_precision_resources", future.copy());
            controller.load(saved);
            IItemHandler input = PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null);
            helper.assertTrue(input == null,
                    "Future controller Item root exposed an automation capability");
            helper.assertTrue(future.equals(controller.saveWithFullMetadata()
                    .get("arce_precision_resources")),
                    "Future controller Item root was rewritten");
            helper.succeed();
        });
    }

    static void stageLegacySave(GameTestHelper helper, boolean prepared) {
        PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
        CompoundTag controllerSaved = controller.saveWithFullMetadata();
        ListTag items = controllerSaved.getCompound("arce_precision_resources")
                .getList("items", Tag.TAG_COMPOUND);
        for (int slot = 0; slot < ITEM_PORTS.length; slot++) {
            PrecisionAssemblerPortBlockEntity port = PrecisionAssemblerGameTests.port(helper, ITEM_PORTS[slot]);
            CompoundTag portSaved = port.saveWithFullMetadata();
            portSaved.getCompound("arce_precision_port").put("item", items.getCompound(slot).copy());
            port.load(portSaved);
            port.setChanged();
        }
        if (prepared) {
            controllerSaved.getCompound("arce_precision_resources").putString("phase", "preparing");
        } else {
            controllerSaved.remove("arce_precision_resources");
        }
        controller.load(controllerSaved);
        controller.setChanged();
        PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
    }

    private static void assertMigrated(GameTestHelper helper) {
        PrecisionAssemblerBlockEntity controller = PrecisionAssemblerGameTests.controller(helper);
        CompoundTag saved = controller.saveWithFullMetadata();
        CompoundTag resources = saved.getCompound("arce_precision_resources");
        helper.assertTrue("active".equals(resources.getString("phase")),
                "Legacy Item resources did not activate in the controller");
        PrecisionAssemblerGameTests.assertInputs(helper, 2, 2);
        helper.assertTrue(PrecisionAssemblerGameTests.storedControllerItemCount(
                helper, PrecisionAssemblerGameTests.INPUT_0) == 2,
                "Migrated controller lost its first input");
        UUID machineId = controller.controllerState().machineInstanceId();
        for (BlockPos position : ITEM_PORTS) {
            CompoundTag portSaved = PrecisionAssemblerGameTests.port(helper, position)
                    .saveWithFullMetadata();
            helper.assertTrue(portSaved.contains("arce_precision_port_migration", Tag.TAG_COMPOUND),
                    "Legacy Item port has no durable migration marker");
            helper.assertTrue(machineId.toString().equals(portSaved
                    .getCompound("arce_precision_port_migration").getString("machine_id")),
                    "Legacy port marker belongs to another machine");
        }
    }
}
