package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockPartBinding;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.PartBindingValidationStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerPortBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceBalance;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRecipe;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
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
    static final BlockPos CONTROLLER = new BlockPos(2, 1, 1);
    static final BlockPos INPUT_0 = new BlockPos(1, 1, 1);
    static final BlockPos INPUT_1 = new BlockPos(3, 1, 1);
    static final BlockPos OUTPUT_0 = new BlockPos(3, 1, 3);
    static final BlockPos OUTPUT_1 = new BlockPos(1, 1, 4);
    static final BlockPos ENERGY = new BlockPos(3, 1, 4);
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

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 65)
    public static void twoOutputRecipeConsumesInputsOnce(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            requireProcessRecipe(helper);
            feedProcess(helper);
        });
        helper.runAtTickTime(14, () -> {
            helper.assertTrue(controller(helper).processProgress().isPresent(),
                    "Precision Assembler did not begin the recipe");
            helper.assertTrue(controller(helper).processState() == ProcessMachineState.RUNNING,
                    "Precision Assembler is not in a running state");
        });
        helper.runAtTickTime(38, () -> {
            assertCompletedBatch(helper);
            helper.assertTrue(controller(helper).processProgress().isEmpty(),
                    "Completed process still has progress");
        });
        helper.runAtTickTime(55, () -> {
            assertCompletedBatch(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 80)
    public static void blockedOutputAndMissingEnergyPreserveInputs(GameTestHelper helper) {
        placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            replaceControllerItem(helper, OUTPUT_0, new ItemStack(ModItems.ADVANCED_CIRCUIT.get(), 64));
            insertInputs(helper);
        });
        helper.runAtTickTime(16, () -> {
            helper.assertTrue(controller(helper).processState() == ProcessMachineState.WAITING_OUTPUT,
                    "Full output did not pause the process");
            assertInputs(helper, 2, 2);
            helper.assertTrue(port(helper, OUTPUT_1).getCapability(ForgeCapabilities.ITEM_HANDLER)
                    .resolve().orElseThrow().getStackInSlot(0).isEmpty(),
                    "Blocked recipe produced its second output");
            IItemHandler output = port(helper, OUTPUT_0).getCapability(
                    ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
            helper.assertTrue(output.extractItem(0, 64, false).getCount() == 64,
                    "Full output could not be cleared");
        });
        helper.runAtTickTime(24, () -> {
            helper.assertTrue(controller(helper).processState() == ProcessMachineState.WAITING_ENERGY,
                    "Missing energy did not pause the process");
            assertInputs(helper, 2, 2);
            receiveEnergy(helper, 800);
        });
        helper.runAtTickTime(55, () -> {
            assertCompletedBatch(helper);
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 65)
    public static void preparedJournalReplaysWithoutDuplicateOutputs(GameTestHelper helper) {
        placeStructure(helper);
        AtomicReference<ProcessTransactionJournal> savedJournal = new AtomicReference<>();
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerRecipe recipe = requireProcessRecipe(helper);
            insertInputs(helper);
            PrecisionAssemblerBlockEntity machine = controller(helper);
            ProcessTransactionJournal prepared = preparedJournal(helper, recipe);
            savedJournal.set(prepared);
            CompoundTag saved = machine.saveWithFullMetadata();
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(prepared));
            completedProgress(saved, recipe);
            machine.load(saved);
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), helper.absolutePos(CONTROLLER));
        });
        helper.runAtTickTime(16, () -> {
            assertCompletedBatch(helper);
            PrecisionAssemblerBlockEntity machine = controller(helper);
            helper.assertTrue(!machine.saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT),
                    "Recovered journal was not cleared");
            CompoundTag replay = machine.saveWithFullMetadata();
            replay.getCompound(ProcessStatePersistence.ROOT).putString("last_applied_transaction", "");
            replay.put(ProcessJournalPersistence.ROOT,
                    ProcessJournalPersistence.encode(savedJournal.get().advance(ProcessJournalPhase.APPLYING)));
            completedProgress(replay, requireProcessRecipe(helper));
            machine.load(replay);
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(), helper.absolutePos(CONTROLLER));
        });
        helper.runAtTickTime(26, () -> {
            assertCompletedBatch(helper);
            helper.assertTrue(!controller(helper).saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT),
                    "Replayed journal was not finalized");
            helper.succeed();
        });
    }

    static PrecisionAssemblerRecipe requireProcessRecipe(GameTestHelper helper) {
        ResourceLocation id = ModIdentity.id("precision_control_circuit");
        var loaded = helper.getLevel().getRecipeManager().byKey(id);
        helper.assertTrue(loaded.isPresent() && loaded.orElseThrow() instanceof PrecisionAssemblerRecipe,
                "Precision Assembler process recipe is absent from the server");
        return (PrecisionAssemblerRecipe) loaded.orElseThrow();
    }

    private static void feedProcess(GameTestHelper helper) {
        insertInputs(helper);
        receiveEnergy(helper, 800);
    }

    static void insertInputs(GameTestHelper helper) {
        IItemHandler iron = port(helper, INPUT_0).getCapability(
                ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        IItemHandler redstone = port(helper, INPUT_1).getCapability(
                ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        helper.assertTrue(iron.insertItem(0, new ItemStack(Items.IRON_INGOT, 2), false).isEmpty(),
                "Iron input fixture was rejected");
        helper.assertTrue(redstone.insertItem(0, new ItemStack(Items.REDSTONE, 2), false).isEmpty(),
                "Redstone input fixture was rejected");
    }

    private static void receiveEnergy(GameTestHelper helper, int amount) {
        IEnergyStorage energy = port(helper, ENERGY).getCapability(
                ForgeCapabilities.ENERGY).resolve().orElseThrow();
        helper.assertTrue(energy.receiveEnergy(amount, false) == amount,
                "Energy input fixture was rejected");
    }

    static void assertInputs(GameTestHelper helper, int iron, int redstone) {
        helper.assertTrue(port(helper, INPUT_0).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve().orElseThrow().getStackInSlot(0).getCount() == iron,
                "Iron input count changed unexpectedly");
        helper.assertTrue(port(helper, INPUT_1).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve().orElseThrow().getStackInSlot(0).getCount() == redstone,
                "Redstone input count changed unexpectedly");
    }

    static void assertCompletedBatch(GameTestHelper helper) {
        assertInputs(helper, 0, 0);
        helper.assertTrue(port(helper, OUTPUT_0).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve().orElseThrow().getStackInSlot(0).getCount() == 1,
                "First result was not produced exactly once");
        helper.assertTrue(port(helper, OUTPUT_1).getCapability(ForgeCapabilities.ITEM_HANDLER)
                .resolve().orElseThrow().getStackInSlot(0).getCount() == 2,
                "Second result was not produced exactly once");
    }

    static void replaceControllerItem(GameTestHelper helper, BlockPos portPosition, ItemStack replacement) {
        PrecisionAssemblerBlockEntity machine = controller(helper);
        CompoundTag saved = machine.saveWithFullMetadata();
        ListTag items = saved.getCompound("arce_precision_resources").getList("items", Tag.TAG_COMPOUND);
        items.set(controllerItemSlot(helper, portPosition),
                replacement.isEmpty() ? new CompoundTag() : replacement.save(new CompoundTag()));
        machine.load(saved);
    }

    static int storedControllerItemCount(GameTestHelper helper, BlockPos portPosition) {
        CompoundTag resources = controller(helper).saveWithFullMetadata()
                .getCompound("arce_precision_resources");
        ListTag items = resources.getList("items", Tag.TAG_COMPOUND);
        return ItemStack.of(items.getCompound(controllerItemSlot(helper, portPosition))).getCount();
    }

    private static int controllerItemSlot(GameTestHelper helper, BlockPos portPosition) {
        String channel = port(helper, portPosition).assignedChannel().orElseThrow();
        for (int index = 0; index < 5; index++) {
            if (channel.equals("item_input_" + index)) {
                return index;
            }
        }
        for (int index = 0; index < 2; index++) {
            if (channel.equals("item_output_" + index)) {
                return 5 + index;
            }
        }
        throw new IllegalArgumentException("Position is not a Precision Item port");
    }

    private static ProcessResourceKey resource(String channel, net.minecraft.world.item.Item item) {
        return new ProcessResourceKey(ProcessResourceKind.ITEM, channel,
                BuiltInRegistries.ITEM.getKey(item).toString());
    }

    private static ProcessResourceBalance balance(int amount, net.minecraft.world.item.Item item) {
        return new ProcessResourceBalance(amount, item.getMaxStackSize());
    }

    static ProcessTransactionJournal preparedJournal(GameTestHelper helper, PrecisionAssemblerRecipe recipe) {
        PrecisionAssemblerBlockEntity machine = controller(helper);
        Map<ProcessResourceKey, ProcessResourceBalance> balances = new LinkedHashMap<>();
        balances.put(resource("item_input_0", Items.IRON_INGOT), balance(2, Items.IRON_INGOT));
        balances.put(resource("item_input_1", Items.REDSTONE), balance(2, Items.REDSTONE));
        balances.put(resource("item_output_0", ModItems.ADVANCED_CIRCUIT.get()),
                balance(0, ModItems.ADVANCED_CIRCUIT.get()));
        balances.put(resource("item_output_1", Items.REDSTONE_TORCH),
                balance(0, Items.REDSTONE_TORCH));
        ProcessResourceSnapshot before = new ProcessResourceSnapshot(machine.resourceRevision(), balances);
        return ProcessTransactionJournal.prepared(UUID.randomUUID(),
                machine.controllerState().machineInstanceId(),
                ProcessMachineLogic.simulate(recipe.processDefinition(), before).plan().orElseThrow());
    }

    /** The journal is written at the completion cut, while matching completed progress is still retained. */
    static void completedProgress(CompoundTag parent, PrecisionAssemblerRecipe recipe) {
        CompoundTag process = parent.getCompound(ProcessStatePersistence.ROOT);
        process.putString("state", "running");
        process.putString("definition_id", recipe.getId().toString());
        process.putString("recipe_signature", recipe.signature());
        process.putInt("progress_ticks", recipe.processingTicks());
        process.putLong("consumed_energy", recipe.processDefinition().totalEnergy());
        new RecipeSignatureMigration().save(parent, recipe.getId().toString());
    }

    static void placeStructure(GameTestHelper helper) {
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

    static PrecisionAssemblerBlockEntity controller(GameTestHelper helper) {
        return (PrecisionAssemblerBlockEntity) helper.getBlockEntity(CONTROLLER);
    }

    static Set<BlockPos> portPositions() {
        return CHANNELS.keySet();
    }

    static PrecisionAssemblerPortBlockEntity port(GameTestHelper helper, BlockPos position) {
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
