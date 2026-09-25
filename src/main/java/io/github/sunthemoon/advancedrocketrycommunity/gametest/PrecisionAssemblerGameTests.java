package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerGameTests {
    private static final BlockPos CONTROLLER = new BlockPos(2, 1, 1);
    private static final BlockPos INPUT_0 = new BlockPos(1, 1, 1);
    private static final BlockPos OUTPUT_0 = new BlockPos(3, 1, 3);
    private static final BlockPos ENERGY = new BlockPos(3, 1, 4);
    private static final BlockPos BREAK_CASING = new BlockPos(2, 2, 1);
    private static final Map<BlockPos, String> CHANNELS = channels();

    private PrecisionAssemblerGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void formedPortsUseFixedChannelsAndTypedAutomation(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerBlockEntity controller = controller(helper);
            helper.assertTrue(controller.formationState() == MultiblockFormationState.FORMED,
                    "Precision Assembler did not form");
            helper.assertTrue(controller.generation() == 1L, "First formation did not use generation one");
            for (Map.Entry<BlockPos, String> entry : CHANNELS.entrySet()) {
                PrecisionAssemblerPortBlockEntity port = port(helper, entry.getKey());
                helper.assertTrue(port.bindingStatus().orElse(null) == PartBindingValidationStatus.VALID,
                        "Formed port binding was not valid");
                helper.assertTrue(port.assignedChannel().orElseThrow().equals(entry.getValue()),
                        "Physical port was assigned by placement order instead of local cell");
            }
            IItemHandler input = port(helper, INPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER, Direction.UP).resolve().orElseThrow();
            IItemHandler output = port(helper, OUTPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY).getCapability(
                    ForgeCapabilities.ENERGY, Direction.DOWN).resolve().orElseThrow();
            helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                    "Input did not accept an untagged Item stack");
            helper.assertTrue(output.insertItem(0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Output accepted external insertion");
            helper.assertTrue(energy.receiveEnergy(5_000, false) == 1_000,
                    "Energy input ignored the receive limit");
            helper.assertTrue(!port(helper, ENERGY).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Energy port exposed Item capability");
            helper.assertTrue(!port(helper, INPUT_0).getCapability(ForgeCapabilities.ENERGY).isPresent(),
                    "Item port exposed Energy capability");
            BlockPos forgedPosition = new BlockPos(4, 1, 1);
            helper.setBlock(forgedPosition, ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get());
            PrecisionAssemblerPortBlockEntity forged = port(helper, forgedPosition);
            forged.setMultiblockBinding(port(helper, INPUT_0).multiblockBinding());
            helper.assertTrue(!forged.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Copied binding exposed an out-of-structure port capability");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void mirroredStructureSelectsMirroredLocalChannels(GameTestHelper helper) {
        placeStructure(helper);
        helper.setBlock(new BlockPos(1, 1, 3), ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get());
        helper.setBlock(new BlockPos(3, 1, 3), ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get());
        helper.setBlock(new BlockPos(1, 1, 4), ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get());
        helper.setBlock(new BlockPos(3, 1, 4), ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get());

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(controller(helper).formationState() == MultiblockFormationState.FORMED,
                    "Mirrored Precision Assembler did not form");
            helper.assertTrue(controller(helper).controllerState().selectedTransform().mirroredLocalX(),
                    "Mirrored structure did not select the mirrored transform");
            helper.assertTrue(port(helper, new BlockPos(3, 1, 3)).assignedChannel()
                    .orElseThrow().equals("item_input_4"),
                    "Mirrored input lost its fixed local channel");
            helper.assertTrue(port(helper, new BlockPos(1, 1, 3)).assignedChannel()
                    .orElseThrow().equals("item_output_0"),
                    "Mirrored output lost its fixed local channel");
            helper.assertTrue(port(helper, new BlockPos(1, 1, 4)).assignedChannel()
                    .orElseThrow().equals("energy_input"),
                    "Mirrored energy port lost its fixed local channel");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 50)
    public static void brokenCasingRetainsResourcesAndRetiresOldViews(GameTestHelper helper) {
        AtomicReference<IItemHandler> retiredItem = new AtomicReference<>();
        AtomicReference<IEnergyStorage> retiredEnergy = new AtomicReference<>();
        placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            IItemHandler item = port(helper, INPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IEnergyStorage energy = port(helper, ENERGY).getCapability(
                    ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(item.insertItem(0, new ItemStack(Items.GOLD_INGOT, 3), false).isEmpty(),
                    "Input fixture was rejected");
            helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000,
                    "Energy fixture was rejected");
            retiredItem.set(item);
            retiredEnergy.set(energy);
            helper.setBlock(BREAK_CASING, Blocks.AIR);
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(controller(helper).formationState() == MultiblockFormationState.UNFORMED,
                    "Broken casing did not invalidate formation");
            helper.assertTrue(!port(helper, INPUT_0).getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Unformed Item port advertised a capability");
            helper.assertTrue(retiredItem.get().insertItem(
                    0, new ItemStack(Items.IRON_INGOT), false).getCount() == 1,
                    "Retired Item view modified an unformed port");
            helper.assertTrue(retiredEnergy.get().receiveEnergy(100, false) == 0,
                    "Retired Energy view modified an unformed port");
            helper.setBlock(BREAK_CASING, ModBlocks.MACHINE_CASING.get());
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(controller(helper).formationState() == MultiblockFormationState.FORMED,
                    "Restored casing did not reform the structure");
            helper.assertTrue(controller(helper).generation() == 2L,
                    "Rebuild did not advance the generation once");
            IItemHandler currentItem = port(helper, INPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            IEnergyStorage currentEnergy = port(helper, ENERGY).getCapability(
                    ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(currentItem.getStackInSlot(0).getCount() == 3,
                    "Structure break lost Item resources");
            helper.assertTrue(currentEnergy.getEnergyStored() == 1_000,
                    "Structure break lost Energy resources");
            helper.assertTrue(retiredItem.get().getStackInSlot(0).isEmpty(),
                    "Retired Item view became live after rebuild");
            helper.assertTrue(retiredEnergy.get().getEnergyStored() == 0,
                    "Retired Energy view became live after rebuild");

            CompoundTag itemSaved = port(helper, INPUT_0).saveWithFullMetadata();
            CompoundTag energySaved = port(helper, ENERGY).saveWithFullMetadata();
            port(helper, INPUT_0).load(itemSaved);
            port(helper, ENERGY).load(energySaved);
            helper.assertTrue(port(helper, INPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .getStackInSlot(0).getCount() == 3,
                    "Item NBT reload changed the quantity");
            helper.assertTrue(port(helper, ENERGY).getCapability(
                    ForgeCapabilities.ENERGY).resolve().orElseThrow().getEnergyStored() == 1_000,
                    "Energy NBT reload changed the quantity");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void futureResourceAndUnloadedForgedBindingFailClosed(GameTestHelper helper) {
        placeStructure(helper);
        PrecisionAssemblerPortBlockEntity input = port(helper, INPUT_0);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("marker", "preserve");
        CompoundTag parent = new CompoundTag();
        parent.put("arce_precision_port", future.copy());
        input.load(parent);

        BlockPos forgedPosition = new BlockPos(4, 1, 1);
        helper.setBlock(forgedPosition, ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get());
        PrecisionAssemblerPortBlockEntity forged = port(helper, forgedPosition);
        BlockPos distant = new BlockPos(1_000_000, 64, 1_000_000);
        helper.assertTrue(!helper.getLevel().hasChunkAt(distant), "Distant fixture chunk was already loaded");
        forged.setMultiblockBinding(Optional.of(new MultiblockPartBinding(
                MultiblockPartBinding.SCHEMA_VERSION,
                helper.getLevel().dimension(),
                distant,
                UUID.randomUUID(),
                1L
        )));

        helper.runAtTickTime(8, () -> {
            helper.assertTrue(controller(helper).formationState() != MultiblockFormationState.FORMED,
                    "Future port resource participated in formation");
            helper.assertTrue(!input.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Future resource root exposed a capability");
            helper.assertTrue(future.equals(input.saveWithFullMetadata().get("arce_precision_port")),
                    "Future resource root was not preserved exactly");
            helper.assertTrue(!forged.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Forged distant binding exposed a capability");
            helper.assertTrue(!helper.getLevel().hasChunkAt(distant),
                    "Capability lookup loaded a distant controller chunk");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 30)
    public static void brokenItemPortDropsStoredItemsOnce(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            IItemHandler input = port(helper, INPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_INGOT, 3), false).isEmpty(),
                    "Drop fixture was rejected");
            helper.assertTrue(helper.getLevel().destroyBlock(helper.absolutePos(INPUT_0), true),
                    "Item input port could not be broken");
        });
        helper.succeedWhen(() -> helper.assertItemEntityCountIs(Items.IRON_INGOT, INPUT_0, 1.0D, 3));
    }

    private static void placeStructure(GameTestHelper helper) {
        for (int z = 0; z < 4; z++) {
            for (int y = 0; y < 3; y++) {
                for (int x = 0; x < 3; x++) {
                    helper.setBlock(CONTROLLER.offset(x - 1, y, z), ModBlocks.MACHINE_CASING.get());
                }
            }
        }
        for (BlockPos position : CHANNELS.keySet()) {
            if (position.equals(ENERGY)) {
                helper.setBlock(position, ModBlocks.PRECISION_ASSEMBLER_ENERGY_INPUT_PORT.get());
            } else if (position.equals(new BlockPos(1, 1, 4)) || position.equals(OUTPUT_0)) {
                helper.setBlock(position, ModBlocks.PRECISION_ASSEMBLER_ITEM_OUTPUT_PORT.get());
            } else {
                helper.setBlock(position, ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get());
            }
        }
        helper.setBlock(CONTROLLER, ModBlocks.PRECISION_ASSEMBLER.get().defaultBlockState()
                .setValue(PrecisionAssemblerBlock.FACING, Direction.NORTH));
        helper.assertTrue(helper.getBlockState(INPUT_0).is(ModBlocks.PRECISION_ASSEMBLER_ITEM_INPUT_PORT.get()),
                "Fixture input block was replaced during placement");
        helper.assertTrue(helper.getBlockEntity(INPUT_0) instanceof PrecisionAssemblerPortBlockEntity,
                "Fixture input BlockEntity is " + helper.getBlockEntity(INPUT_0).getClass().getName()
                        + " for " + BuiltInRegistries.BLOCK.getKey(helper.getBlockState(INPUT_0).getBlock()));
    }

    private static PrecisionAssemblerBlockEntity controller(GameTestHelper helper) {
        return (PrecisionAssemblerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    private static PrecisionAssemblerPortBlockEntity port(GameTestHelper helper, BlockPos position) {
        return (PrecisionAssemblerPortBlockEntity) helper.getBlockEntity(position);
    }

    private static Map<BlockPos, String> channels() {
        Map<BlockPos, String> positions = new LinkedHashMap<>();
        positions.put(new BlockPos(1, 1, 1), "item_input_0");
        positions.put(new BlockPos(3, 1, 1), "item_input_1");
        positions.put(new BlockPos(1, 1, 2), "item_input_2");
        positions.put(new BlockPos(3, 1, 2), "item_input_3");
        positions.put(new BlockPos(1, 1, 3), "item_input_4");
        positions.put(new BlockPos(3, 1, 3), "item_output_0");
        positions.put(new BlockPos(1, 1, 4), "item_output_1");
        positions.put(new BlockPos(3, 1, 4), "energy_input");
        return Map.copyOf(positions);
    }
}
