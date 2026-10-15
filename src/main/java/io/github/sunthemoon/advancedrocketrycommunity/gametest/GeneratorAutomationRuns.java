package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternRotation;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternTransform;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayLayout;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarArrayRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.machine.solar.SolarGeneratorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;

/**
 * W68 (W62 S5) generators. Each FE source here is the device's own: burnt fuel for the combustion generator, sunlight
 * for the solar generator and array. A registered electrolyzer beside the generator receives its push, and the test
 * pulls through the generator's capability on two other faces. Exact conservation is generated = buffered + received +
 * pulled, with no FE accepted from outside.
 */
final class GeneratorAutomationRuns {
    static final int ROUNDS = 20;
    private static final Direction[] SIDES = Direction.values();

    /** Twenty bamboo (Forge burn time 50 each), fed by a hopper above and a dropper beside the generator. */
    static void combustion(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos at = helper.absolutePos(new BlockPos(3, 2, 3));
            rig.room(at.offset(-2, -2, -2), at.offset(2, 2, 2));
            var feed = new GeneratorFeed(rig, at, Items.BAMBOO, 15);
            var dropper = feed.dropper(rig, Direction.WEST, ROUNDS - 15);
            var sink = electrolyzer(rig, at.east());
            var state = new Object() { long pulled; int subTick, settle = -1; boolean reloaded; };
            rig.drive(timeoutTicks, () -> {
                for (int i = 0; i < 20; i++) {
                    state.subTick++; feed.tick(); rig.tickHoppers();
                    if (state.subTick % 16 == 0 && !AutomationRig.empty(dropper)) { rig.pulse(dropper); }
                }
                for (Direction side : new Direction[]{Direction.NORTH, Direction.SOUTH}) {
                    state.pulled += port(feed.generator, side).extractEnergy(500, false);
                }
                if (!state.reloaded && feed.supplied() - feed.unburnt() >= ROUNDS / 2) {
                    state.reloaded = true;
                    long generated = feed.generated(); int buffered = feed.buffered();
                    for (Direction side : SIDES) {
                        helper.assertTrue(port(feed.generator, side).receiveEnergy(1_000, false) == 0,
                                "The combustion generator accepted FE from side " + side);
                        var items = feed.generator.getCapability(ForgeCapabilities.ITEM_HANDLER, side).orElseThrow(() -> new IllegalStateException("Fuel"));
                        helper.assertTrue(items.insertItem(0, new ItemStack(Items.DIRT), false).getCount() == 1,
                                "The combustion generator took a non-fuel item from side " + side);
                    }
                    // Unburnt fuel may leave by design; one item out on one face and back on another changes nothing.
                    var north = feed.generator.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.NORTH).orElseThrow(() -> new IllegalStateException("Fuel"));
                    var south = feed.generator.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.SOUTH).orElseThrow(() -> new IllegalStateException("Fuel"));
                    ItemStack taken = north.extractItem(0, 1, false);
                    helper.assertTrue(taken.isEmpty() || south.insertItem(0, taken, false).isEmpty(), "Withdrawn fuel could not return");
                    feed.reload();
                    helper.assertTrue(feed.generated() == generated && feed.buffered() == buffered, "Generator save and load changed its burn ledger");
                }
                if (state.settle < 0 && state.reloaded && feed.spent() && AutomationRig.empty(dropper)) { state.settle = 0; }
                return state.settle >= 0 && ++state.settle > 3;
            }, () -> {
                long generated = feed.generated(), accounted = (long) feed.buffered() + sink.energyStored() + state.pulled;
                helper.assertTrue(feed.spent() && generated == (long) ROUNDS * feed.burnTicks() * 40,
                        "Combustion generator did not burn exactly its 20 fuel items: " + generated + " FE");
                helper.assertTrue(generated == accounted, "Combustion FE: generated " + generated + ", buffer " + feed.buffered()
                        + " + received " + sink.energyStored() + " + pulled " + state.pulled);
                helper.assertTrue(rig.looseStacks(at, 6) == 0, "Combustion automation spilled items into the world");
            });
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    /** The registered solar generator at the build limit in clear daylight, as SolarGeneratorGameTests samples it. */
    static void solarGenerator(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos anchor = helper.absolutePos(new BlockPos(1, 2, 1));
            BlockPos at = new BlockPos(anchor.getX(), rig.level.getMaxBuildHeight() - 2, anchor.getZ());
            var day = SolarDay.begin(rig, at.above());
            rig.place(at, ModBlocks.SOLAR_GENERATOR.get().defaultBlockState());
            var generator = (SolarGeneratorBlockEntity) rig.level.getBlockEntity(at);
            var sink = electrolyzer(rig, at.east());
            new SolarLedger(rig, day, generator, sink, generator::actualCredit, generator::energyStored,
                    () -> SolarGeneratorBlockEntity.serverTick(rig.level, at, generator.getBlockState(), generator),
                    () -> generator.load(generator.saveWithFullMetadata())).start(timeoutTicks);
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    /** A registered solar array with nine panels, its two power output plugs and the production exposure. */
    static void solarArray(GameTestHelper helper, int timeoutTicks) {
        var rig = new AutomationRig(helper);
        try {
            BlockPos anchor = helper.absolutePos(new BlockPos(5, 2, 5));
            BlockPos at = new BlockPos(anchor.getX(), rig.level.getMaxBuildHeight() - 2, anchor.getZ());
            var day = SolarDay.begin(rig, at.above());
            var transform = new PatternTransform(PatternRotation.ZERO, false);
            var controller = new PatternPosition(at.getX(), at.getY(), at.getZ());
            BlockPos firstPlug = null, secondPlug = null; int panels = 0;
            for (var cell : SolarArrayLayout.cells()) {
                var world = transform.localToWorld(cell.local(), SolarArrayLayout.ANCHOR, controller);
                var position = new BlockPos(world.x(), world.y(), world.z());
                if (cell.collector()) {
                    if (panels++ < 9) { rig.place(position, ModBlocks.SOLAR_ARRAY_PANEL.get().defaultBlockState()); }
                } else if (cell.local().equals(SolarArrayLayout.ANCHOR)) {
                    rig.place(position, ModBlocks.SOLAR_ARRAY.get().defaultBlockState().setValue(SolarArrayBlock.FACING, Direction.NORTH));
                } else {
                    rig.place(position, ModBlocks.POWER_OUTPUT_PLUG.get().defaultBlockState());
                    if (firstPlug == null) { firstPlug = position; } else { secondPlug = position; }
                }
            }
            var array = (SolarArrayBlockEntity) rig.level.getBlockEntity(at);
            // The receiver goes in first: a later neighbour change would release the plugs until the next validation.
            var sink = electrolyzer(rig, firstPlug.north());
            SolarArrayRuntime.manager(rig.level).tick(); SolarArrayRuntime.manager(rig.level).tick();
            helper.assertTrue(array.collectors() == 9, "W68 solar array did not form with nine panels: " + array.structure());
            BlockPos pullPlug = secondPlug;
            new SolarLedger(rig, day, rig.level.getBlockEntity(pullPlug), sink, array::actualCredit, array::energyStored,
                    () -> SolarArrayBlockEntity.serverTick(rig.level, at, array.getBlockState(), array),
                    () -> { array.load(array.saveWithFullMetadata()); SolarArrayRuntime.manager(rig.level).tick();
                        SolarArrayRuntime.manager(rig.level).tick(); }).start(timeoutTicks);
        } catch (RuntimeException | Error failure) { rig.close(); throw failure; }
    }

    private static ElectrolyzerBlockEntity electrolyzer(AutomationRig rig, BlockPos position) {
        rig.place(position, ModBlocks.ELECTROLYZER.get().defaultBlockState().setValue(ElectrolyzerBlock.FACING, Direction.NORTH));
        return (ElectrolyzerBlockEntity) rig.level.getBlockEntity(position);
    }
    private static IEnergyStorage port(BlockEntity entity, Direction side) {
        return entity.getCapability(ForgeCapabilities.ENERGY, side).orElseThrow(() -> new IllegalStateException("Energy port"));
    }

    /** Clear noon for the fixture's sky cell, restored when the rig closes, as the existing solar surface tests do. */
    record SolarDay(ServerLevel level, BlockPos sky, long time, float rain, float thunder) {
        static SolarDay begin(AutomationRig rig, BlockPos sky) {
            var level = rig.level;
            var day = new SolarDay(level, sky, level.getDayTime(), level.getRainLevel(1), level.getThunderLevel(1));
            rig.beforeClose(() -> { level.setDayTime(day.time()); level.setRainLevel(day.rain()); level.setThunderLevel(day.thunder()); });
            level.setDayTime(6_000); level.setRainLevel(0); level.setThunderLevel(0);
            return day;
        }
        boolean ready() { return level.isDay() && level.canSeeSky(sky); }
    }

    /**
     * Checks every solar operation on its own: each generator tick's published credit must equal the FE it added to its
     * buffer plus what it pushed into the electrolyzer, and each pull must take exactly what it returns. The server's
     * own tick runs between test ticks; its change, when there is one, must match the credit it published. Twenty
     * rounds of four extra ticks and two pulls on two faces; the buffer is saved and loaded once mid-run.
     */
    private static final class SolarLedger {
        private final AutomationRig rig; private final SolarDay day; private final BlockEntity output;
        private final ElectrolyzerBlockEntity sink; private final IntSupplier credit, stored; private final Runnable tick, reload;
        private final GameTestTickPacer pacer;
        private long credited, pulled, lastTotal; private int rounds;
        SolarLedger(AutomationRig rig, SolarDay day, BlockEntity output, ElectrolyzerBlockEntity sink, IntSupplier credit,
                    IntSupplier stored, Runnable tick, Runnable reload) {
            this.rig = rig; this.day = day; this.output = output; this.sink = sink; this.credit = credit; this.stored = stored;
            this.tick = tick; this.reload = reload; pacer = new GameTestTickPacer(rig.level.getServer()); lastTotal = total();
        }
        private long total() { return (long) stored.getAsInt() + sink.energyStored(); }
        void start(int timeoutTicks) {
            rig.drive(timeoutTicks, this::gameTick, this::verify, () -> "rounds " + rounds + ", day " + day.ready() + ", output bound "
                    + output.getCapability(ForgeCapabilities.ENERGY).isPresent() + ", credited " + credited + ", stored " + stored.getAsInt());
        }
        private boolean gameTick() {
            var helper = rig.helper;
            long natural = total() - lastTotal;
            helper.assertTrue(natural == 0 || natural == credit.getAsInt(), "The server's solar tick moved " + natural
                    + " FE but published " + credit.getAsInt());
            credited += natural;
            // Daylight and, for the array, a bound output plug (formation runs on the server tick) come first.
            if (!day.ready() || !output.getCapability(ForgeCapabilities.ENERGY).isPresent()) { pacer.pace(); lastTotal = total(); return false; }
            for (int i = 0; i < 4; i++) {
                long before = total(); tick.run();
                helper.assertTrue(total() - before == credit.getAsInt(), "A solar tick published " + credit.getAsInt()
                        + " FE but moved " + (total() - before));
                credited += credit.getAsInt();
            }
            for (Direction side : new Direction[]{Direction.UP, Direction.SOUTH}) {
                int before = stored.getAsInt(), taken = port(output, side).extractEnergy(300, false);
                helper.assertTrue(before - stored.getAsInt() == taken, "A solar pull returned " + taken + " FE but took " + (before - stored.getAsInt()));
                pulled += taken;
            }
            if (++rounds == ROUNDS / 2) {
                for (Direction side : SIDES) {
                    helper.assertTrue(port(output, side).receiveEnergy(1_000, false) == 0, "A solar output accepted FE from side " + side);
                }
                int before = stored.getAsInt(); reload.run();
                helper.assertTrue(stored.getAsInt() == before, "Solar save and load changed the stored FE");
            }
            lastTotal = total();
            return rounds >= ROUNDS;
        }
        private void verify() {
            long accounted = total() + pulled;
            rig.helper.assertTrue(credited > 0, "No daylight credit was published");
            rig.helper.assertTrue(credited == accounted, "Solar FE: credited " + credited + ", stored " + stored.getAsInt()
                    + " + received " + sink.energyStored() + " + pulled " + pulled);
        }
    }

    private GeneratorAutomationRuns() { }
}
