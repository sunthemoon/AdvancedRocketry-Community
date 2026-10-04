package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.precision.PrecisionAssemblerRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalPhase;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/** World-level rejection cases for process identity and revision mismatch. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerProcessSafetyGameTests {
    private PrecisionAssemblerProcessSafetyGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 75)
    public static void activeProgressRoundTripCompletesOneBatch(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.ENERGY
            ).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(800, false) == 800,
                    "Active-progress fixture did not receive energy");
        });
        helper.runAtTickTime(16, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            ProcessProgress before = machine.processProgress().orElseThrow();
            CompoundTag controllerSaved = machine.saveWithFullMetadata();
            machine.load(controllerSaved);
            for (BlockPos portPosition : new BlockPos[]{
                    PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1,
                    PrecisionAssemblerGameTests.OUTPUT_0, PrecisionAssemblerGameTests.OUTPUT_1,
                    PrecisionAssemblerGameTests.ENERGY
            }) {
                var port = PrecisionAssemblerGameTests.port(helper, portPosition);
                port.load(port.saveWithFullMetadata());
            }
            helper.assertTrue(machine.processProgress().orElseThrow().equals(before),
                    "Active progress changed during NBT round-trip");
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(55, () -> {
            PrecisionAssemblerGameTests.assertCompletedBatch(helper);
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processProgress().isEmpty(),
                    "Reloaded progress did not finish");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 35)
    public static void futureProcessAndJournalRootsRemainBlockedAndPreserved(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            CompoundTag futureProcess = saved.getCompound(ProcessStatePersistence.ROOT).copy();
            futureProcess.putInt("schema_version", 2);
            futureProcess.putString("marker", "keep-process");
            CompoundTag futureJournal = new CompoundTag();
            futureJournal.putInt("schema_version", 2);
            futureJournal.putString("marker", "keep-journal");
            saved.put(ProcessStatePersistence.ROOT, futureProcess.copy());
            saved.put(ProcessJournalPersistence.ROOT, futureJournal.copy());
            machine.load(saved);
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
            helper.assertTrue(machine.processState() == ProcessMachineState.UNSUPPORTED_DATA,
                    "Future process data was not blocked");
            helper.assertTrue(!PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.INPUT_0)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent(),
                    "Future process data exposed an automation capability");
            CompoundTag roundTrip = machine.saveWithFullMetadata();
            helper.assertTrue(futureProcess.equals(roundTrip.get(ProcessStatePersistence.ROOT))
                            && futureJournal.equals(roundTrip.get(ProcessJournalPersistence.ROOT)),
                    "Future process or journal root was rewritten");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 80)
    public static void fiveInputRecipeAcceptsEnergyWhileRunning(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            helper.assertTrue(helper.getLevel().getRecipeManager()
                    .byKey(ModIdentity.id("precision_guidance_module")).isPresent(),
                    "Five-input recipe was not loaded");
            PrecisionAssemblerGameTests.insertInputs(helper);
            insert(helper, new BlockPos(1, 1, 2), Items.GOLD_INGOT);
            insert(helper, new BlockPos(3, 1, 2), Items.QUARTZ);
            insert(helper, new BlockPos(1, 1, 3), Items.COPPER_INGOT);
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.ENERGY
            ).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(1_000, false) == 1_000,
                    "Initial energy was not accepted");
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processProgress().isPresent(),
                    "Five-input recipe did not start");
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.ENERGY
            ).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(500, false) == 500,
                    "Running process rejected replenishment energy");
        });
        helper.runAtTickTime(58, () -> {
            for (BlockPos input : new BlockPos[]{
                    PrecisionAssemblerGameTests.INPUT_0, PrecisionAssemblerGameTests.INPUT_1,
                    new BlockPos(1, 1, 2), new BlockPos(3, 1, 2), new BlockPos(1, 1, 3)
            }) {
                helper.assertTrue(storedCount(helper, input) == 0,
                        "Five-input process retained a consumed input");
            }
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_0) == 1,
                    "Five-input result was not produced once");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_1) == 0,
                    "Five-input recipe wrote to an unused output");
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processProgress().isEmpty(),
                    "Five-input process did not complete");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 45)
    public static void recipeSignatureChangeDoesNotConsumeInputs(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            PrecisionAssemblerGameTests.insertInputs(helper);
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.ENERGY
            ).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(800, false) == 800,
                    "Active-progress fixture did not receive energy");
        });
        helper.runAtTickTime(14, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(machine.processProgress().isPresent(),
                    "Active-progress fixture did not start");
            CompoundTag saved = machine.saveWithFullMetadata();
            saved.getCompound(ProcessStatePersistence.ROOT).putString("recipe_signature", "0".repeat(64));
            machine.load(saved);
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(25, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(machine.processState() == ProcessMachineState.INVALID_RECIPE
                            && machine.processFailure().code() == ProcessFailureCode.INVALID_RECIPE,
                    "Changed recipe signature was not rejected");
            PrecisionAssemblerGameTests.assertInputs(helper, 2, 2);
            helper.assertTrue(PrecisionAssemblerGameTests.port(helper, PrecisionAssemblerGameTests.OUTPUT_0)
                    .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow()
                    .getStackInSlot(0).isEmpty(), "Changed recipe signature produced an output");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void staleJournalRevisionDoesNotPartiallyCommit(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var recipe = PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            ProcessTransactionJournal prepared = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            IEnergyStorage energy = PrecisionAssemblerGameTests.port(
                    helper, PrecisionAssemblerGameTests.ENERGY
            ).getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.receiveEnergy(100, false) == 100,
                    "Stale-revision fixture did not advance the resource revision");
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(prepared));
            PrecisionAssemblerGameTests.completedProgress(saved, recipe);
            machine.load(saved);
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).processState()
                    == ProcessMachineState.RECOVERY_REQUIRED,
                    "Stale journal did not require recovery");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.INPUT_0) == 2
                            && storedCount(helper, PrecisionAssemblerGameTests.INPUT_1) == 2,
                    "Stale journal changed an input resource");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_0) == 0,
                    "Stale journal partially produced the first output");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_1) == 0,
                    "Stale journal partially produced the second output");
            helper.assertTrue(PrecisionAssemblerGameTests.controller(helper).saveWithFullMetadata()
                    .contains(ProcessJournalPersistence.ROOT), "Stale journal was silently discarded");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void savedFullProgressWithoutJournalDoesNotSettleMixedPorts(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var recipe = PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            PrecisionAssemblerGameTests.completedProgress(saved, recipe);
            machine.load(saved);
            replaceStoredItem(helper, PrecisionAssemblerGameTests.INPUT_0, ItemStack.EMPTY);
            replaceStoredItem(helper, PrecisionAssemblerGameTests.OUTPUT_0, recipe.outputs().get(0));
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(20, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(machine.processState() == ProcessMachineState.RECOVERY_REQUIRED,
                    "Saved full progress without a journal was allowed to settle");
            helper.assertTrue(machine.processFailure().code() == ProcessFailureCode.RECOVERY_DIVERGED,
                    "Saved full progress did not report a recovery failure");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.INPUT_0) == 0
                            && storedCount(helper, PrecisionAssemblerGameTests.INPUT_1) == 2
                            && storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_0) == 1
                            && storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_1) == 0,
                    "Saved full progress rewrote mixed port resources");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void preparedJournalReconcilesMixedSavedPortsOnce(GameTestHelper helper) {
        reconcileMixedSavedPorts(helper, false);
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void appliedJournalReconcilesMixedSavedPortsOnce(GameTestHelper helper) {
        reconcileMixedSavedPorts(helper, true);
    }

    private static void reconcileMixedSavedPorts(GameTestHelper helper, boolean applied) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var recipe = PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            var prepared = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            var journal = applied
                    ? prepared.advance(ProcessJournalPhase.APPLYING).advance(ProcessJournalPhase.APPLIED)
                    : prepared;
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(journal));
            PrecisionAssemblerGameTests.completedProgress(saved, recipe);
            if (applied) {
                saved.getCompound(ProcessStatePersistence.ROOT).putLong(
                        "resource_revision", journal.after().revision());
                saved.getCompound(ProcessStatePersistence.ROOT).putString(
                        "last_applied_transaction", journal.transactionId().toString());
            }
            machine.load(saved);
            replaceStoredItem(helper, PrecisionAssemblerGameTests.INPUT_0, ItemStack.EMPTY);
            replaceStoredItem(helper, PrecisionAssemblerGameTests.OUTPUT_0, recipe.outputs().get(0));
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(20, () -> {
            PrecisionAssemblerGameTests.assertCompletedBatch(helper);
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(machine.processProgress().isEmpty()
                            && !machine.saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT),
                    "Mixed-port journal was not finalized");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "precision_assembler", timeoutTicks = 40)
    public static void journalWithForeignPortContentsRemainsBlocked(GameTestHelper helper) {
        PrecisionAssemblerGameTests.placeStructure(helper);
        helper.runAtTickTime(8, () -> {
            var recipe = PrecisionAssemblerGameTests.requireProcessRecipe(helper);
            PrecisionAssemblerGameTests.insertInputs(helper);
            var journal = PrecisionAssemblerGameTests.preparedJournal(helper, recipe);
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            CompoundTag saved = machine.saveWithFullMetadata();
            saved.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(journal));
            PrecisionAssemblerGameTests.completedProgress(saved, recipe);
            machine.load(saved);
            replaceStoredItem(helper, PrecisionAssemblerGameTests.OUTPUT_0, new ItemStack(Items.DIRT));
            PrecisionAssemblerRuntime.markProcessReady(helper.getLevel(),
                    helper.absolutePos(PrecisionAssemblerGameTests.CONTROLLER));
        });
        helper.runAtTickTime(20, () -> {
            PrecisionAssemblerBlockEntity machine = PrecisionAssemblerGameTests.controller(helper);
            helper.assertTrue(machine.processState() == ProcessMachineState.RECOVERY_REQUIRED
                            && machine.saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT),
                    "Foreign port state was overwritten or the journal was cleared");
            helper.assertTrue(storedCount(helper, PrecisionAssemblerGameTests.INPUT_0) == 2
                            && storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_0) == 1
                            && storedCount(helper, PrecisionAssemblerGameTests.OUTPUT_1) == 0,
                    "Foreign port state was partially replaced");
            helper.succeed();
        });
    }

    private static void replaceStoredItem(GameTestHelper helper, BlockPos position, ItemStack stack) {
        PrecisionAssemblerGameTests.replaceControllerItem(helper, position, stack);
    }

    private static int storedCount(GameTestHelper helper, BlockPos position) {
        return PrecisionAssemblerGameTests.storedControllerItemCount(helper, position);
    }

    private static void insert(GameTestHelper helper, BlockPos position, net.minecraft.world.item.Item item) {
        IItemHandler input = PrecisionAssemblerGameTests.port(helper, position)
                .getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        helper.assertTrue(input.insertItem(0, new ItemStack(item), false).isEmpty(),
                "Five-input port rejected its fixture item");
    }
}
