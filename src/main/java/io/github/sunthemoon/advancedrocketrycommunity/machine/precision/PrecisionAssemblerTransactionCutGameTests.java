package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.PrecisionAssemblerMigrationSaveFailureGameTests;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.recipe.RecipeSignatureMigration;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Full controller snapshots captured during real commit and recovery calls. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PrecisionAssemblerTransactionCutGameTests {
    private PrecisionAssemblerTransactionCutGameTests() {
    }

    @GameTest(template = "empty", batch = "precision_transaction_cuts", timeoutTicks = 40)
    public static void commitJournalCutsRecoverExactlyOnce(GameTestHelper helper) {
        exerciseCommitCuts(helper, false);
    }

    @GameTest(template = "empty", batch = "precision_transaction_cuts", timeoutTicks = 40)
    public static void fiveInputCommitCutsRecoverExactlyOnce(GameTestHelper helper) {
        exerciseCommitCuts(helper, true);
    }

    private static void exerciseCommitCuts(GameTestHelper helper, boolean fiveInputs) {
        PrecisionAssemblerTransactionTestFixture.run(helper, fiveInputs, fixture -> {
            fixture.feed(1);
            CompoundTag before = fixture.controller.saveWithFullMetadata();
            var cuts = fixture.completeBatch();
            CompoundTag completed = fixture.controller.saveWithFullMetadata();
            CompoundTag energy = fixture.energyRoot();
            assertCutCoverage(fixture, cuts, before, completed, true);
            for (int slot = 0; slot < 5; slot++) {
                helper.assertTrue(fixture.controller.storedItemCopy(slot).isEmpty(), "Commit retained a consumed input");
            }
            for (int output = 0; output < 2; output++) {
                ItemStack expected = output < fixture.recipe.outputs().size()
                        ? fixture.recipe.outputs().get(output) : ItemStack.EMPTY;
                helper.assertTrue(ItemStack.matches(expected, fixture.controller.storedItemCopy(5 + output)),
                        "Commit did not produce the recipe's exact ItemStack");
            }
            helper.assertTrue(fixture.ports.energy().storedEnergy() == 200, "Commit Energy budget differed");
            int checked = 0;
            for (var cut : cuts) {
                if (cut.saved().contains(ProcessJournalPersistence.ROOT)) {
                    assertReplay(fixture, cut.saved(), completed, energy, "commit cut " + checked++);
                }
            }
            helper.assertTrue(checked == 12, "Commit matrix missed a journal or resource mutation cut");
            assertReplay(fixture, completed, completed, energy, "completed idle snapshot");
        });
    }

    @GameTest(template = "empty", batch = "precision_transaction_cuts", timeoutTicks = 40)
    public static void recoveryJournalCutsCanRecoverAgain(GameTestHelper helper) {
        PrecisionAssemblerTransactionTestFixture.run(helper, fixture -> {
            fixture.feed(1);
            var commitCuts = fixture.completeBatch();
            CompoundTag completed = fixture.controller.saveWithFullMetadata();
            CompoundTag energy = fixture.energyRoot();
            CompoundTag prepared = commitCuts.stream()
                    .filter(cut -> "prepared".equals(PrecisionAssemblerTransactionTestFixture.phase(cut.saved())))
                    .findFirst().orElseThrow().saved();
            fixture.controller.load(prepared.copy());
            var recoveryCuts = fixture.capture(fixture::tick);
            helper.assertTrue(recoveryCuts.stream().filter(cut -> cut.source().equals("items")).count() == 7,
                    "Recovery capture missed a real Item mutation");
            fixture.assertResources(completed, "initial recovery");
            int checked = 0;
            for (var cut : recoveryCuts) {
                if (cut.saved().contains(ProcessJournalPersistence.ROOT)) {
                    assertReplay(fixture, cut.saved(), completed, energy, "recovery cut " + checked++);
                }
            }
            helper.assertTrue(checked == 11,
                    "Recovery matrix missed journal, revision or applied-marker notifications");
            assertCutCoverage(fixture, recoveryCuts, prepared, completed, false);
        });
    }

    @GameTest(template = "empty", batch = "precision_transaction_cuts", timeoutTicks = 40)
    public static void fullProgressCutsWithoutJournalRemainBlocked(GameTestHelper helper) {
        PrecisionAssemblerTransactionTestFixture.run(helper, fixture -> {
            fixture.feed(1);
            var cuts = fixture.completeBatch();
            CompoundTag energy = fixture.energyRoot();
            int checked = 0;
            for (var cut : cuts) {
                if (!cut.saved().contains(ProcessJournalPersistence.ROOT)
                        && cut.saved().getCompound(ProcessStatePersistence.ROOT).getInt("progress_ticks")
                                == fixture.recipe.processDefinition().durationTicks()) {
                    fixture.controller.load(cut.saved().copy());
                    fixture.tick();
                    fixture.tick();
                    helper.assertTrue(fixture.controller.processState() == ProcessMachineState.RECOVERY_REQUIRED,
                            "Full-progress snapshot without a journal was allowed to settle");
                    fixture.assertResources(cut.saved(), "full-progress cut " + checked++);
                    helper.assertTrue(energy.equals(fixture.energyRoot()), "Blocked recovery consumed Energy");
                }
            }
            helper.assertTrue(checked == 2, "Missing pre-journal or post-clear full-progress boundary");
        });
    }

    @GameTest(template = "empty", batch = "precision_transaction_cuts", timeoutTicks = 40)
    public static void twoMigratedBatchesIgnoreOldShadowsAndRejectOldJournal(GameTestHelper helper) {
        PrecisionAssemblerTransactionTestFixture.run(helper, fixture -> {
            fixture.feed(2);
            helper.getLevel().setBlockAndUpdate(fixture.controller.getBlockPos().north(),
                    Blocks.REDSTONE_BLOCK.defaultBlockState());
            List<PrecisionAssemblerPortBlockEntity> itemPorts = new ArrayList<>(fixture.ports.inputs());
            itemPorts.addAll(fixture.ports.outputs());
            List<CompoundTag> oldPorts = new ArrayList<>();
            List<CompoundTag> markedPorts = new ArrayList<>();
            for (int index = 0; index < itemPorts.size(); index++) {
                var port = itemPorts.get(index);
                CompoundTag legacy = port.saveWithFullMetadata();
                ItemStack stack = fixture.controller.storedItemCopy(index);
                legacy.getCompound("arce_precision_port").put("item",
                        stack.isEmpty() ? new CompoundTag() : stack.save(new CompoundTag()));
                port.load(legacy);
                port.setChanged();
                oldPorts.add(legacy.copy());
            }
            CompoundTag legacy = fixture.controller.saveWithFullMetadata();
            legacy.remove("arce_precision_resources");
            fixture.controller.load(legacy);
            fixture.controller.setChanged();
            var manager = PrecisionAssemblerMigrationSaveFailureGameTests.fixtureManager(helper.getLevel());
            try {
                manager.observeController(helper.getLevel(), fixture.controller);
                manager.tick(helper.getLevel().getServer());
                helper.assertTrue(fixture.controller.ownsItems(), "Two-batch fixture did not migrate");
                for (var port : itemPorts) {
                    helper.assertTrue(port.migrationMarker().isPresent(), "Migration did not mark every Item port");
                    markedPorts.add(port.saveWithFullMetadata());
                }
            } finally {
                manager.clear();
            }
            helper.getLevel().setBlockAndUpdate(fixture.controller.getBlockPos().north(),
                    Blocks.AIR.defaultBlockState());
            var firstCuts = fixture.completeBatch();
            CompoundTag first = fixture.controller.saveWithFullMetadata();
            fixture.completeBatch();
            CompoundTag second = fixture.controller.saveWithFullMetadata();
            CompoundTag energy = fixture.energyRoot();
            helper.assertTrue(fixture.controller.storedItemCopy(0).isEmpty()
                            && fixture.controller.storedItemCopy(1).isEmpty()
                            && fixture.controller.storedItemCopy(5).getCount() == 2
                            && fixture.controller.storedItemCopy(6).is(Items.REDSTONE_TORCH)
                            && fixture.controller.storedItemCopy(6).getCount() == 4
                            && fixture.ports.energy().storedEnergy() == 200,
                    "Two actual batches did not conserve inputs, outputs and Energy");
            for (int index = 0; index < itemPorts.size(); index++) {
                helper.assertTrue(itemPorts.get(index).saveWithFullMetadata().getCompound("arce_precision_port")
                                .equals(oldPorts.get(index).getCompound("arce_precision_port")),
                        "Processing changed an inert legacy shadow");
                itemPorts.get(index).load(markedPorts.get(index).copy());
            }
            assertReplay(fixture, second, second, energy, "second batch with old physical roots");

            // This is deliberately stale input, not a claimed coherent commit cut.
            CompoundTag stale = second.copy();
            CompoundTag preparedSnapshot = firstCuts.stream()
                    .filter(cut -> "prepared".equals(PrecisionAssemblerTransactionTestFixture.phase(cut.saved())))
                    .findFirst().orElseThrow().saved();
            helper.assertTrue(preparedSnapshot.contains(RecipeSignatureMigration.ROOT, Tag.TAG_COMPOUND),
                    "Actual current-format prepared cut is missing its signature marker");
            CompoundTag oldJournal = preparedSnapshot.getCompound(ProcessJournalPersistence.ROOT).copy();
            stale.put(ProcessJournalPersistence.ROOT, oldJournal);
            stale.put(RecipeSignatureMigration.ROOT, preparedSnapshot.get(RecipeSignatureMigration.ROOT).copy());
            fixture.controller.load(stale);
            fixture.tick();
            fixture.tick();
            helper.assertTrue(fixture.controller.processState() == ProcessMachineState.RECOVERY_REQUIRED
                            && oldJournal.equals(fixture.controller.saveWithFullMetadata().get(ProcessJournalPersistence.ROOT)),
                    "First-batch journal was replayed or discarded over second-batch resources");
            fixture.assertResources(second, "stale first-batch journal");

            helper.getLevel().setBlockAndUpdate(fixture.controller.getBlockPos().north(),
                    Blocks.REDSTONE_BLOCK.defaultBlockState());
            fixture.controller.load(first.copy());
            fixture.tick();
            fixture.assertResources(first, "first-batch snapshot with old shadows");
            helper.assertTrue(energy.equals(fixture.energyRoot()), "Shadow or journal replay consumed Energy");
            for (int index = 0; index < itemPorts.size(); index++) {
                helper.assertTrue(markedPorts.get(index).equals(itemPorts.get(index).saveWithFullMetadata()),
                        "Controller reload rewrote a supplied old port root");
            }
        });
    }

    private static void assertReplay(PrecisionAssemblerTransactionTestFixture fixture,
                                     CompoundTag cut, CompoundTag completed, CompoundTag energy, String label) {
        CompoundTag recovered = null;
        for (int reload = 0; reload < 3; reload++) {
            // Check both recovery-result reload and repeated original-input replay.
            fixture.controller.load((reload == 1 ? recovered : cut).copy());
            fixture.tick();
            fixture.tick();
            fixture.helper.assertTrue(fixture.controller.processProgress().isEmpty()
                            && !fixture.controller.saveWithFullMetadata().contains(ProcessJournalPersistence.ROOT)
                            && fixture.controller.processState() != ProcessMachineState.RECOVERY_REQUIRED,
                    label + ": recovery did not finalize");
            fixture.assertResources(completed, label);
            fixture.helper.assertTrue(energy.equals(fixture.energyRoot()), label + ": replay consumed Energy");
            recovered = fixture.controller.saveWithFullMetadata();
        }
    }

    private static void assertCutCoverage(PrecisionAssemblerTransactionTestFixture fixture,
                                         List<PrecisionAssemblerTransactionTestFixture.Cut> cuts,
                                         CompoundTag before, CompoundTag after, boolean commit) {
        long beforeRevision = before.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision");
        long afterRevision = after.getCompound(ProcessStatePersistence.ROOT).getLong("resource_revision");
        String marker = after.getCompound(ProcessStatePersistence.ROOT).getString("last_applied_transaction");
        ListTag prefix = before.getCompound("arce_precision_resources").getList("items", Tag.TAG_COMPOUND).copy();
        ListTag result = after.getCompound("arce_precision_resources").getList("items", Tag.TAG_COMPOUND);
        int written = 0;
        int prepared = 0;
        int applyingBefore = 0;
        int applyingAfter = 0;
        int applyingMarked = 0;
        int applied = 0;
        for (var cut : cuts) {
            String phase = PrecisionAssemblerTransactionTestFixture.phase(cut.saved());
            CompoundTag process = cut.saved().getCompound(ProcessStatePersistence.ROOT);
            long revision = process.getLong("resource_revision");
            boolean marked = marker.equals(process.getString("last_applied_transaction"));
            if (cut.source().equals("items")) {
                prefix.set(written, result.getCompound(written).copy());
                written++;
                fixture.helper.assertTrue(phase.equals("applying") && revision == beforeRevision && !marked
                                && prefix.equals(cut.saved().getCompound("arce_precision_resources")
                                        .getList("items", Tag.TAG_COMPOUND)),
                        "Captured Item prefix did not match its ordered APPLYING snapshot");
            } else if (phase.equals("prepared") && revision == beforeRevision && !marked) {
                prepared++;
            } else if (phase.equals("applying") && revision == beforeRevision && !marked) {
                applyingBefore++;
            } else if (phase.equals("applying") && revision == afterRevision && !marked) {
                applyingAfter++;
            } else if (phase.equals("applying") && revision == afterRevision && marked) {
                applyingMarked++;
            } else if (phase.equals("applied") && revision == afterRevision && marked) {
                applied++;
            }
        }
        fixture.helper.assertTrue(written == 7 && prepared == (commit ? 1 : 0) && applyingBefore == 1
                        && applyingAfter == 1 && applyingMarked == 1 && applied == 1,
                "Captured transaction omitted a phase/revision/marker boundary");
    }
}
