package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.config.SwitchOverrides;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W64 (W62 S1): the atmosphere detector's happy path, its failure paths and a mode-root restart, each on its own. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereDetectorGameTests {
    private static final String TEMPLATE = "atmosphere_test";
    private static final BlockPos POS = new BlockPos(4, 2, 4);

    @GameTest(template = TEMPLATE, batch = "w64_detector_happy", timeoutTicks = 40)
    public static void breathableModeSignalsOverworldAirAndOnlyItsOwnMode(GameTestHelper helper) {
        var detector = detector(helper, true); detector.measure();
        helper.assertTrue(detector.mode() == DetectorMode.BREATHABLE && detector.reason() == AtmosphereDetectorBlockEntity.Reason.AVAILABLE
                && output(helper) == 15, "Breathable Overworld air did not power the default mode");
        // Every other mode describes a cell this exterior is not: the reading stays known and the output stays off.
        for (int step = 1; step < DetectorMode.values().length; step++) {
            detector.cycle();
            helper.assertTrue(detector.mode() == DetectorMode.values()[step] && detector.reason() == AtmosphereDetectorBlockEntity.Reason.AVAILABLE
                    && output(helper) == 0, "Mode " + detector.mode() + " matched breathable Overworld air");
        }
        detector.cycle();
        helper.assertTrue(detector.mode() == DetectorMode.BREATHABLE && output(helper) == 15, "The mode cycle did not wrap to a powered BREATHABLE");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "w64_detector_enclosed", timeoutTicks = 40)
    public static void solidNeighboursGiveNoMeasurementAndNoSignal(GameTestHelper helper) {
        var detector = detector(helper, false); detector.measure();
        helper.assertTrue(detector.reason() == AtmosphereDetectorBlockEntity.Reason.UNAVAILABLE && !detector.signal() && output(helper) == 0,
                "A detector enclosed by solid blocks reported a measurement");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "w64_detector_disabled", timeoutTicks = 40)
    public static void eitherDisabledSwitchFailsClosedAndFreezesTheMode(GameTestHelper helper) {
        var detector = detector(helper, true); detector.measure();
        helper.assertTrue(output(helper) == 15, "The enabled detector was not powered");
        for (var key : java.util.List.of(CommonConfig.OXYGEN_DETECTION, CommonConfig.CLASSIC_DEVICES_ENABLED)) {
            SwitchOverrides.set(key, false);
            try {
                detector.measure(); detector.cycle();
                helper.assertTrue(detector.reason() == AtmosphereDetectorBlockEntity.Reason.DISABLED && detector.mode() == DetectorMode.BREATHABLE
                        && !detector.signal() && output(helper) == 0, "A disabled detector kept its output or changed mode");
            } finally { SwitchOverrides.clear(key); }
            detector.measure();
            helper.assertTrue(detector.reason() == AtmosphereDetectorBlockEntity.Reason.AVAILABLE && output(helper) == 15,
                    "The re-enabled detector did not measure again");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "w64_detector_unsupported", timeoutTicks = 40)
    public static void unsupportedModeRootIsKeptVerbatimAndGivesNoSignal(GameTestHelper helper) {
        var detector = detector(helper, true);
        CompoundTag root = new CompoundTag(); root.putInt("schema_version", 1); root.putString("mode", "ULTRAVIOLET");
        CompoundTag outer = new CompoundTag(); outer.put(AtmosphereDetectorBlockEntity.KEY, root); detector.load(outer);
        detector.measure(); detector.cycle();
        var position = helper.absolutePos(POS); var state = helper.getLevel().getBlockState(position);
        helper.assertTrue(detector.protectedData() && !detector.signal() && output(helper) == 0
                && detector.saveWithFullMetadata().get(AtmosphereDetectorBlockEntity.KEY).equals(root),
                "An unsupported mode root was normalized or powered the detector");
        helper.assertTrue(!state.getBlock().canEntityDestroy(state, helper.getLevel(), position, null), "Entity destruction admitted a protected detector");
        // Restore a supported root before the test ends; this case tests retention, not the guarded chunk save.
        detector.load(new CompoundTag()); detector.measure();
        helper.assertTrue(!detector.protectedData() && output(helper) == 15, "The supported default did not recover");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = "w64_detector_restart", timeoutTicks = 40)
    public static void modeRootSurvivesAFreshBlockEntityAndMeasuresInTheRestoredMode(GameTestHelper helper) {
        var detector = detector(helper, true);
        for (int step = 0; step < DetectorMode.values().length && detector.mode() != DetectorMode.COLD; step++) { detector.cycle(); }
        CompoundTag saved = detector.saveWithFullMetadata();
        helper.assertTrue(saved.getCompound(AtmosphereDetectorBlockEntity.KEY).getInt("schema_version") == 1
                && saved.getCompound(AtmosphereDetectorBlockEntity.KEY).getString("mode").equals("COLD"), "The mode root was not saved");
        // As a chunk load does: a fresh block entity decoded from the saved NBT replaces the live one.
        var position = helper.absolutePos(POS);
        BlockEntity loaded = BlockEntity.loadStatic(position, helper.getLevel().getBlockState(position), saved.copy());
        helper.assertTrue(loaded instanceof AtmosphereDetectorBlockEntity, "The saved detector did not load");
        var fresh = (AtmosphereDetectorBlockEntity) loaded; helper.getLevel().setBlockEntity(fresh);
        helper.assertTrue(detector.isRemoved() && helper.getBlockEntity(POS) == fresh && fresh.mode() == DetectorMode.COLD
                && !fresh.protectedData() && fresh.saveWithFullMetadata().equals(saved), "The mode root did not round-trip exactly");
        // A defaulted BREATHABLE mode would power here; the restored COLD mode must not.
        fresh.measure();
        helper.assertTrue(fresh.reason() == AtmosphereDetectorBlockEntity.Reason.AVAILABLE && output(helper) == 0,
                "The restored detector did not measure in its saved mode");
        fresh.cycle();
        helper.assertTrue(fresh.mode() == DetectorMode.BREATHABLE && output(helper) == 15, "The restored detector did not continue its mode cycle");
        helper.succeed();
    }

    /** A detector enclosed by stone, with its east side open to the template's air when {@code open}. */
    private static AtmosphereDetectorBlockEntity detector(GameTestHelper helper, boolean open) {
        helper.setBlock(POS, ModBlocks.ATMOSPHERE_DETECTOR.get());
        for (Direction side : Direction.values()) { helper.setBlock(POS.relative(side), Blocks.STONE); }
        if (open) { helper.setBlock(POS.east(), Blocks.AIR); }
        return (AtmosphereDetectorBlockEntity) helper.getBlockEntity(POS);
    }
    /** The redstone power the detector block gives its open side, as a neighbour reads it. */
    private static int output(GameTestHelper helper) {
        var position = helper.absolutePos(POS);
        return helper.getLevel().getBlockState(position).getSignal(helper.getLevel(), position, Direction.WEST);
    }
    private AtmosphereDetectorGameTests() { }
}
