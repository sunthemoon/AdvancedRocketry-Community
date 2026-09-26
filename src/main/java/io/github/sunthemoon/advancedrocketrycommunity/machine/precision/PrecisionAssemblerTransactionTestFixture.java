package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.PrecisionAssemblerMigrationSaveFailureGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

/** Bounded, instance-local observation of real controller serialization points. */
final class PrecisionAssemblerTransactionTestFixture {
    final GameTestHelper helper;
    final PrecisionAssemblerBlockEntity controller;
    final PrecisionAssemblerPortSet ports;
    final PrecisionAssemblerRecipe recipe;

    private PrecisionAssemblerTransactionTestFixture(GameTestHelper helper, BlockPos position, boolean fiveInputs) {
        this.helper = helper;
        controller = (PrecisionAssemblerBlockEntity) helper.getLevel().getBlockEntity(position);
        helper.assertTrue(controller != null && controller.formationState() == MultiblockFormationState.FORMED,
                "Transaction fixture did not form");
        ports = PrecisionAssemblerPortSet.resolve(helper.getLevel(), controller).orElseThrow();
        recipe = (PrecisionAssemblerRecipe) helper.getLevel().getRecipeManager()
                .byKey(ModIdentity.id(fiveInputs ? "precision_guidance_module" : "precision_control_circuit"))
                .orElseThrow();
    }

    static void run(GameTestHelper helper, Consumer<PrecisionAssemblerTransactionTestFixture> exercise) {
        run(helper, false, exercise);
    }

    static void run(GameTestHelper helper, boolean fiveInputs,
                    Consumer<PrecisionAssemblerTransactionTestFixture> exercise) {
        BlockPos position = helper.absolutePos(new BlockPos(2, 1, 1));
        PrecisionAssemblerMigrationSaveFailureGameTests.placeStructure(helper.getLevel(), position);
        helper.runAtTickTime(8, () -> {
            exercise.accept(new PrecisionAssemblerTransactionTestFixture(helper, position, fiveInputs));
            helper.succeed();
        });
    }

    void feed(int batches) {
        var iron = ports.inputs().get(0).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        var redstone = ports.inputs().get(1).getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElseThrow();
        helper.assertTrue(iron.insertItem(0, new ItemStack(Items.IRON_INGOT, 2 * batches), false).isEmpty()
                        && redstone.insertItem(0, new ItemStack(Items.REDSTONE, 2 * batches), false).isEmpty(),
                "Transaction fixture inputs were rejected");
        if (recipe.inputs().size() == 5) {
            var additions = List.of(Items.GOLD_INGOT, Items.QUARTZ, Items.COPPER_INGOT);
            for (int slot = 2; slot < 5; slot++) {
                var input = ports.inputs().get(slot).getCapability(ForgeCapabilities.ITEM_HANDLER)
                        .resolve().orElseThrow();
                helper.assertTrue(input.insertItem(0, new ItemStack(additions.get(slot - 2), batches), false).isEmpty(),
                        "Five-input transaction fixture was rejected");
            }
        }
        var energy = ports.energy().getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
        int remaining = Math.toIntExact(recipe.processDefinition().durationTicks()
                * recipe.processDefinition().energyPerTick() * batches + 200);
        for (int attempt = 0; attempt < 4 && remaining > 0; attempt++) {
            remaining -= energy.receiveEnergy(remaining, false);
        }
        helper.assertTrue(remaining == 0, "Transaction fixture Energy was rejected");
    }

    void tick() {
        controller.tickProcess(helper.getLevel());
    }

    List<Cut> completeBatch() {
        int duration = recipe.processDefinition().durationTicks();
        for (int tick = 0; tick < duration - 1; tick++) {
            tick();
        }
        helper.assertTrue(controller.processProgress().orElseThrow().progressTicks() == duration - 1,
                "Fixture did not reach the last processing tick");
        List<Cut> cuts = capture(this::tick);
        helper.assertTrue(cuts.stream().filter(cut -> cut.source().equals("items")).count() == 7,
                "Actual commit did not expose all seven Item mutation notifications");
        helper.assertTrue(cuts.stream().anyMatch(cut -> "prepared".equals(phase(cut.saved())))
                        && cuts.stream().anyMatch(cut -> "applying".equals(phase(cut.saved())))
                        && cuts.stream().anyMatch(cut -> "applied".equals(phase(cut.saved()))),
                "Actual commit did not expose all journal phase notifications");
        return cuts;
    }

    List<Cut> capture(Runnable action) {
        List<Cut> cuts = new ArrayList<>();
        List<Callback> installed = new ArrayList<>();
        try {
            Field process = PrecisionAssemblerBlockEntity.class.getDeclaredField("process");
            process.setAccessible(true);
            observe(process.get(controller), "process", cuts, installed);
            observe(controller.itemBank(), "items", cuts, installed);
            action.run();
            helper.assertTrue(!cuts.isEmpty(), "Controller mutation callbacks were not observed");
            return List.copyOf(cuts);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot observe the local transaction fixture", exception);
        } finally {
            RuntimeException restoreFailure = null;
            for (int index = installed.size() - 1; index >= 0; index--) {
                try {
                    installed.get(index).restore();
                } catch (RuntimeException exception) {
                    if (restoreFailure == null) {
                        restoreFailure = exception;
                    } else {
                        restoreFailure.addSuppressed(exception);
                    }
                }
            }
            if (restoreFailure != null) {
                throw restoreFailure;
            }
        }
    }

    private void observe(Object owner, String source, List<Cut> cuts, List<Callback> installed)
            throws ReflectiveOperationException {
        Field field = owner.getClass().getDeclaredField("changed");
        field.setAccessible(true);
        Runnable original = (Runnable) field.get(owner);
        // This test-only reflection wraps existing callbacks, not the resource
        // implementation or global manager. Required capture counts detect a
        // JVM that continues using the original final field after replacement.
        field.set(owner, (Runnable) () -> {
            original.run();
            helper.assertTrue(cuts.size() < 64, "Transaction capture exceeded its fixed bound");
            cuts.add(new Cut(source, controller.saveWithFullMetadata()));
        });
        installed.add(new Callback(owner, field, original));
    }

    void assertResources(CompoundTag expected, String label) {
        CompoundTag actual = controller.saveWithFullMetadata();
        helper.assertTrue(actual.get("arce_precision_resources").equals(expected.get("arce_precision_resources")),
                label + ": central Item root changed");
        CompoundTag expectedProcess = expected.getCompound(ProcessStatePersistence.ROOT);
        CompoundTag actualProcess = actual.getCompound(ProcessStatePersistence.ROOT);
        helper.assertTrue(actualProcess.getLong("resource_revision") == expectedProcess.getLong("resource_revision")
                        && actualProcess.getString("last_applied_transaction")
                                .equals(expectedProcess.getString("last_applied_transaction")),
                label + ": resource revision or transaction identity changed");
    }

    CompoundTag energyRoot() {
        return ports.energy().saveWithFullMetadata().getCompound("arce_precision_port").copy();
    }

    static String phase(CompoundTag saved) {
        return saved.getCompound(ProcessJournalPersistence.ROOT).getString("phase");
    }

    record Cut(String source, CompoundTag saved) {
    }

    private record Callback(Object owner, Field field, Runnable original) {
        void restore() {
            try {
                field.set(owner, original);
            } catch (IllegalAccessException exception) {
                throw new IllegalStateException("Cannot restore the local fixture callback", exception);
            }
        }
    }
}
