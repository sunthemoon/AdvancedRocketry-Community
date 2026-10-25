package io.github.sunthemoon.arceadaptertest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.wrapper.InvWrapper;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;

/** Real coordinator ticks, with provider faults scoped to each fixture position until cleanup. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealedPreparationLatchGameTests {
    private static final Map<BlockPos, Trial> ACTIVE = new HashMap<>();

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void falseRestoreIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_FALSE); }
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void throwingRestoreIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_THROW); }
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void slowRestoreIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_SLOW); }
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void mismatchedRecaptureIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_MISMATCH); }
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void levelDependentRestoreIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_NEEDS_LEVEL); }
    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "rocket_test", batch = "w152f_failure_latch", timeoutTicks = 700)
    public static void installedHostRestoreIsLatchedPerLoad(GameTestHelper helper) { run(helper, FixtureAdapterFaults.Mode.RESTORE_NEEDS_INSTALLED); }

    static FixtureAdapterFaults.Probe probe(BlockEntity target) {
        var trial = ACTIVE.get(target.getBlockPos());
        return trial == null || !(target instanceof FixtureContainerBlockEntity) ? null : trial.probe;
    }
    static void attached(BlockEntity target) {
        var trial = ACTIVE.get(target.getBlockPos());
        if (trial != null && target instanceof FixtureContainerBlockEntity) { trial.targets.add(target); }
    }
    private static void run(GameTestHelper helper, FixtureAdapterFaults.Mode mode) {
        var at = helper.absolutePos(new BlockPos(4, 2, 4));
        var trial = new Trial(helper, at, mode);
        // Source capture happens outside the fault scope; only coordinator-created detached targets are counted.
        trial.carrier = SealedProductionProviderGameTests.stage(helper.getLevel(), at);
        trial.saved = trial.carrier.saveWithFullMetadata();
        trial.root = trial.carrier.saveWithoutMetadata();
        ACTIVE.put(at, trial);
        helper.runAtTickTime(699, trial::close);
        helper.onEachTick(() -> {
            if (trial.closed) { return; }
            try { trial.tick(); }
            catch (RuntimeException | Error failure) { trial.close(); throw failure; }
        });
    }
    private static final class Trial implements AutoCloseable {
        final GameTestHelper helper;
        final BlockPos at;
        final FixtureAdapterFaults.Probe probe;
        final List<BlockEntity> targets = new ArrayList<>();
        final Warnings warnings;
        BlockEntity carrier;
        CompoundTag saved, root;
        long attemptedAt = -1;
        int load = 1;
        boolean closed;
        Trial(GameTestHelper helper, BlockPos at, FixtureAdapterFaults.Mode mode) {
            this.helper = helper; this.at = at; probe = new FixtureAdapterFaults.Probe(mode); warnings = new Warnings(at);
        }
        void tick() {
            long now = helper.getLevel().getServer().getTickCount();
            if (probe.calls >= load && attemptedAt < 0) { attemptedAt = now; }
            if (attemptedAt < 0 || now - attemptedAt < 260) { return; }
            helper.assertTrue(probe.calls == load, "Coordinator retried " + probe.mode + ": load=" + load + ", calls=" + probe.calls);
            helper.assertTrue(helper.getLevel().getBlockEntity(at) == carrier && root.equals(carrier.saveWithoutMetadata()),
                    "Original carrier resources changed");
            for (int repeat = 0; repeat < 3; repeat++) {
                helper.assertTrue(!SealedProductionProviderGameTests.unseal(carrier), "Direct attempt published failed C");
            }
            helper.assertTrue(probe.calls == load, "Direct attempt bypassed " + probe.mode + " latch");
            helper.assertTrue(targets.size() == load, "Preparation constructed another target");
            for (var target : targets) {
                helper.assertTrue(target.isRemoved(), "Discarded target is still live");
                var raw = (Container) target; var cached = new InvWrapper(raw);
                helper.assertTrue(raw.getItem(0).isEmpty() && cached.extractItem(0, 64, false).isEmpty(), "Removed target exposes originals");
                var foreign = new ItemStack(Items.GOLD_INGOT, 3);
                helper.assertTrue(cached.insertItem(1, foreign, false).getCount() == 3 && raw.getItem(1).isEmpty(), "Removed target consumed foreign items");
            }
            helper.assertTrue(warnings.lines.size() == load, "Expected one carrier WARN per load: " + warnings.lines);
            if (load == 2) { close(); helper.succeed(); return; }
            // Exercise the native BE unload/reconstruction boundary without unloading the GameTest's own ticket.
            var state = carrier.getBlockState(); var previous = carrier;
            previous.onChunkUnloaded(); helper.getLevel().removeBlockEntity(at);
            carrier = BlockEntity.loadStatic(at, state, saved.copy());
            helper.assertTrue(carrier != null && carrier != previous && previous.isRemoved(), "Carrier was not reconstructed");
            helper.getLevel().setBlockEntity(carrier); carrier.onLoad();
            helper.assertTrue(root.equals(carrier.saveWithoutMetadata()), "Reload changed retained resources");
            load++; attemptedAt = -1;
        }
        @Override public void close() {
            if (closed) { return; }
            closed = true; ACTIVE.remove(at, this); warnings.close();
            helper.getLevel().setBlock(at, Blocks.AIR.defaultBlockState(), 2);
        }
    }
    private static final class Warnings extends AbstractAppender implements AutoCloseable {
        final List<String> lines = new ArrayList<>();
        final LoggerContext context = (LoggerContext) LogManager.getContext(false);
        final org.apache.logging.log4j.core.config.LoggerConfig config = context.getConfiguration().getRootLogger();
        final String prefix;
        Warnings(BlockPos at) {
            super("w152f-latch-" + at.asLong(), null, null, true, Property.EMPTY_ARRAY);
            prefix = "ARCE_SEALED_MEMBER_HELD position=" + at.asLong() + " reason=preparation-failed ";
            start(); config.addAppender(this, null, null); context.updateLoggers();
        }
        @Override public void append(org.apache.logging.log4j.core.LogEvent event) {
            String line = event.getMessage().getFormattedMessage();
            if (event.getLevel() == org.apache.logging.log4j.Level.WARN && line.startsWith(prefix)) { lines.add(line); }
        }
        @Override public void close() { config.removeAppender(getName()); context.updateLoggers(); stop(); }
    }
    private SealedPreparationLatchGameTests() { }
}
