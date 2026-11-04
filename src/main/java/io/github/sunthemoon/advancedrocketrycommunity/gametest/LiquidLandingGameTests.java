package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationEnvironmentProfile;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationGridCell;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W290: fluid safety is independent of the legacy Earth/Space support exemptions. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LiquidLandingGameTests {
    private static final String BATCH = "liquid_landing";
    private static final int[][] OFFSETS = {
            {0, 0}, {64, 0}, {-64, 0}, {0, 64}, {0, -64}, {64, 64}, {-64, 64}, {64, -64}
    };

    private LiquidLandingGameTests() { }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void occupiedOverworldSpawnSkipsWaterAtTheSecondPad(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel())) {
            helper.assertTrue(f.level.dimension().equals(Level.OVERWORLD), "Expected an Overworld arrival");
            f.shape(0, Blocks.STONE.defaultBlockState());
            f.shape(1, Blocks.WATER.defaultBlockState());
            f.shape(2, Blocks.STONE.defaultBlockState());
            var parked = ModEntities.ROCKET.get().create(f.level);
            helper.assertTrue(parked != null, "Rocket type unavailable");
            parked.initialize(f.at(0), UUID.randomUUID(), UUID.randomUUID());
            f.rockets.add(parked);
            helper.assertTrue(f.level.addFreshEntity(parked), "Could not park the first rocket");
            helper.assertTrue(!f.selector.available(f.level, f.at(0), null, false), "First pad is not occupied");

            f.assertCandidate(f.select(false), 2);
            helper.assertTrue(parked.isAlive(), "Selection changed the parked rocket");
            // The governed loaded-only path shares the same decision and must not acquire terrain.
            var loadedOnly = f.selector.select(f.level, f.source, UUID.randomUUID(), List.of(),
                    f.level.getGameTime(), true, null, false);
            f.assertCandidate(loadedOnly, 2);
            helper.assertTrue(loadedOnly.chunksLoaded() == 0, "Loaded-only selection acquired chunks");
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void waterLavaAndWaterloggedPadsExhaustOnlyTheEightFixedCandidates(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel())) {
            for (BlockState fluid : List.of(Blocks.WATER.defaultBlockState(), Blocks.LAVA.defaultBlockState(),
                    Blocks.OAK_SLAB.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true))) {
                for (int index = 0; index < OFFSETS.length; index++) { f.shape(index, fluid); }
                var result = f.select(false);
                helper.assertTrue(!result.success() && result.detail().equals("all fixed pads are occupied or unsafe")
                                && result.refusalKey() == null,
                        "All-fluid pads must use the existing unsafe refusal, got " + result + " for " + fluid);
                helper.assertTrue(result.candidatesChecked() == RocketFlightLimits.MAX_LANDING_PAD_CANDIDATES
                                && result.chunksLoaded() <= OFFSETS.length * RocketFlightLimits.MAX_LANDING_CHUNKS,
                        "Selection exceeded its existing candidate/chunk bounds: " + result);
            }
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void dryNonSturdyOverworldSpawnKeepsItsLegacySupportExemption(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel())) {
            BlockState pane = Blocks.GLASS_PANE.defaultBlockState();
            f.shape(0, pane);
            helper.assertTrue(!pane.isFaceSturdy(f.level, f.pad(0), Direction.UP)
                            && pane.getFluidState().isEmpty(), "Expected dry, non-sturdy ground");
            f.assertCandidate(f.select(false), 0);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void fluidUnderAnUpperOnlyColumnCannotHideBehindSturdyMarsSupport(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS)))) {
            f.shape(0, Blocks.STONE.defaultBlockState());
            f.shape(1, Blocks.STONE.defaultBlockState());
            f.set(f.pad(0).east(), Blocks.WATER.defaultBlockState());
            f.assertCandidate(f.select(false), 1);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void aLowerFluidColumnCannotHideBelowAnOverhang(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel().getServer().getLevel(PlanetaryContent.level(PlanetaryContent.MARS)))) {
            f.shape(0, Blocks.STONE.defaultBlockState());
            f.shape(1, Blocks.STONE.defaultBlockState());
            f.set(f.pad(0).east(), Blocks.AIR.defaultBlockState());
            f.set(f.pad(0).east().below(), Blocks.LAVA.defaultBlockState());
            f.assertCandidate(f.select(false), 1);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void aNewMoonArrivalAboveTheLegacyThresholdStillSkipsFluid(GameTestHelper helper) {
        try (var f = new Fixture(helper, helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL))) {
            f.shape(0, Blocks.STONE.defaultBlockState());
            f.shape(1, Blocks.STONE.defaultBlockState());
            f.set(f.pad(0).east(), Blocks.WATER.defaultBlockState());
            f.assertCandidate(f.select(false), 1);
        }
        helper.succeed();
    }

    @GameTest(template = "rocket_test", batch = BATCH)
    public static void theUnnamedSpacePadRejectsFluidWithoutRequiringSturdySupport(GameTestHelper helper) {
        ServerLevel space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
        try (var f = new Fixture(helper, space)) {
            var cell = new StationGridCell(290, 290);
            var station = new StationState(StationLimits.STATE_SCHEMA_VERSION, UUID.randomUUID(), UUID.randomUUID(),
                    "Liquid test", cell, cell.region(), cell.landingPad(), CelestialIds.EARTH_ID, 0,
                    StationEnvironmentProfile.BASIC_SPACE, List.of(), List.of());
            BlockPos pad = new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
            f.set(pad.below(), Blocks.WATER.defaultBlockState());
            var wet = f.selector.selectStation(space, f.source, UUID.randomUUID(), station, List.of(), space.getGameTime());
            helper.assertTrue(!wet.success() && wet.detail().equals("station pad is occupied or unsafe"),
                    "The unnamed Space pad admitted fluid: " + wet);
            f.set(pad.below(), Blocks.AIR.defaultBlockState());
            var dry = f.selector.selectStation(space, f.source, UUID.randomUUID(), station, List.of(), space.getGameTime());
            helper.assertTrue(dry.success(), "Dry legacy Space pad acquired a support requirement: " + dry);
        }
        helper.succeed();
    }

    /** Each case runs synchronously, before fluid ticks, and restores every changed block even on assertion failure. */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos base;
        private final int ground;
        private final RocketStructureSnapshot source;
        private final RocketLandingPadSelector selector = new RocketLandingPadSelector();
        private final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
        private final List<RocketEntity> rockets = new ArrayList<>();

        private Fixture(GameTestHelper helper, ServerLevel level) {
            this.helper = helper;
            this.level = level;
            helper.assertTrue(level != null, "Target Level unavailable");
            base = level.dimension().equals(Level.OVERWORLD) ? level.getSharedSpawnPos()
                    : level.dimension().equals(CelestialIds.MOON_LEVEL) ? new BlockPos(8, 80, 8) : BlockPos.ZERO;
            ground = level.getMaxBuildHeight() - 8;
            var iron = new RocketBlockState(new ResourceLocation("minecraft", "iron_block"), Map.of());
            // The upper block occupies a second horizontal column, with no bottom-layer block below it.
            source = RocketStructureSnapshot.create(UUID.randomUUID(), CelestialIds.SPACE_LEVEL.location(),
                    new RocketPosition(0, 128, 0), List.of(new RocketBlock(new RocketPosition(0, 0, 0), iron),
                            new RocketBlock(new RocketPosition(0, 1, 0), iron),
                            new RocketBlock(new RocketPosition(1, 1, 0), iron)), List.of(),
                    new RocketStats(3, 3, 0, 0, 0, 0, 0, 0), 0);
        }

        private BlockPos pad(int index) { return base.offset(OFFSETS[index][0], 0, OFFSETS[index][1]).atY(ground); }

        private void shape(int index, BlockState state) {
            for (int dx = 0; dx <= 1; dx++) {
                BlockPos column = pad(index).east(dx);
                helper.assertTrue(level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                        column.getX(), column.getZ()) <= ground + 1, "Unexpected terrain above fixture");
                set(column, state);
            }
        }

        private void set(BlockPos position, BlockState state) {
            original.putIfAbsent(position.immutable(), level.getBlockState(position));
            level.setBlock(position, state, Block.UPDATE_CLIENTS);
        }

        private RocketStructureSnapshot at(int index) {
            BlockPos pad = pad(index).above();
            return source.relocated(UUID.randomUUID(), level.dimension().location(),
                    new RocketPosition(pad.getX(), pad.getY(), pad.getZ()), level.getGameTime());
        }

        private RocketLandingPadSelection select(boolean governed) {
            return selector.select(level, source, UUID.randomUUID(), List.of(), level.getGameTime(), governed);
        }

        private void assertCandidate(RocketLandingPadSelection result, int index) {
            helper.assertTrue(result.success(), "No dry fixed pad selected: " + result);
            helper.assertTrue(result.destinationSnapshot().sourceOrigin().equals(at(index).sourceOrigin())
                            && result.candidatesChecked() == index + 1,
                    "Expected dry fixed candidate " + index + ", got " + result);
        }

        @Override public void close() {
            rockets.forEach(RocketEntity::discard);
            var entries = new ArrayList<>(original.entrySet());
            for (int i = entries.size() - 1; i >= 0; i--) {
                var entry = entries.get(i);
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_CLIENTS);
            }
        }
    }
}
