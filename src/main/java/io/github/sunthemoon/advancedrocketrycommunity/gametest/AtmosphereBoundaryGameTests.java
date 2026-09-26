package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelService;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereManager;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereServerEvents;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialEnvironmentService;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.AtmosphereBoundaryRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmosphereBoundaryGameTests {
    private static final String TEMPLATE = "atmosphere_test";

    private AtmosphereBoundaryGameTests() {
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void compiledRulesPreserveLegacyPriorityAndLoadedGuards(GameTestHelper helper) {
        AtmosphereBoundaryCatalog catalog;
        try (AtmosphereBoundaryRegistry registry = registry()) {
            var handle = registry.forOwner("boundary_test");
            handle.register(id("open"), Set.of(key(Blocks.STONE), key(ModBlocks.MACHINE_CASING.get()),
                    key(Blocks.OAK_TRAPDOOR)), state -> AtmosphereBoundary.PERMEABLE);
            handle.register(id("closed"), Set.of(key(Blocks.IRON_BARS), key(Blocks.OAK_FENCE_GATE)),
                    state -> AtmosphereBoundary.SEALED);
            handle.register(id("default"), Set.of(key(Blocks.DIRT)), state -> AtmosphereBoundary.DEFAULT);
            catalog = registry.freeze();
        }
        ServerLevel level = helper.getLevel();
        ServerLevelVolumeWorldView view = new ServerLevelVolumeWorldView(level, false, catalog);
        BlockPos position = helper.absolutePos(new BlockPos(3, 2, 3));
        assertCell(helper, view, position, Blocks.STONE.defaultBlockState(), CellObservation.TRAVERSABLE);
        assertCell(helper, view, position, ModBlocks.MACHINE_CASING.get().defaultBlockState(), CellObservation.SEALED);
        assertCell(helper, view, position, Blocks.IRON_BARS.defaultBlockState(), CellObservation.TRAVERSABLE);
        assertCell(helper, view, position, Blocks.OAK_TRAPDOOR.defaultBlockState(), CellObservation.SEALED);
        assertCell(helper, view, position, Blocks.OAK_FENCE_GATE.defaultBlockState()
                .setValue(BlockStateProperties.OPEN, true), CellObservation.TRAVERSABLE);
        assertCell(helper, view, position, Blocks.DIRT.defaultBlockState(), CellObservation.SEALED);
        assertCell(helper, view, position, Blocks.WATER.defaultBlockState(), CellObservation.SEALED);
        BlockPos unloaded = new BlockPos(29_000_000, 100, 29_000_000);
        helper.assertTrue(!level.hasChunkAt(unloaded), "Unloaded fixture already exists");
        helper.assertTrue(view.observe(cell(unloaded)) == CellObservation.UNLOADED, "Unloaded guard was bypassed");
        helper.assertTrue(!level.hasChunkAt(unloaded), "Observation loaded an arbitrary chunk");
        helper.assertTrue(view.observe(new VolumePosition(0, level.getMaxBuildHeight(), 0)) == CellObservation.OPEN,
                "Build height guard was bypassed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void dirtyBacklogCannotRepublishAnAlreadyObservedWall(GameTestHelper helper) {
        ServerLevel moon = moon(helper);
        BlockPos position = allocated(helper);
        OxygenVentBlockEntity vent = room(moon, position);
        AtmosphereLevelService service = new AtmosphereLevelService(moon, false, true,
                AtmosphereLimits.MAX_VOLUME_CELLS, 2, basaltCatalog());
        // Fresh cross-dimension placement finishes its heightmap update at the tick boundary.
        helper.runAfterDelay(1, () -> {
            try {
                service.observeVent(vent);
                service.tick();
                service.tick(); // Seed, east, west and roof have now been observed.
                helper.assertTrue(service.metrics().activeScanTasks() == 1, "Fixture needs an in-flight scan");
                for (int index = 0; index < 200; index++) {
                    service.markDirty(position.offset(1000 + index * 4, 0, 1000));
                }
                helper.assertTrue(service.metrics().dirtyPositions() > AtmosphereLimits.MAX_DIRTY_POSITIONS_PER_TICK,
                        "Fixture did not create a bounded dirty backlog");
                moon.setBlock(position.above(2), Blocks.BASALT.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS, Direction.Axis.X), Block.UPDATE_ALL);
                service.markDirty(position.above(2));
                helper.assertTrue(service.metrics().activeScanTasks() == 0,
                        "Affected scan stayed queued behind unrelated dirty positions");
                for (int tick = 0; tick < 8; tick++) {
                    service.tick();
                    helper.assertTrue(service.breathabilityAt(position.above()) != BreathabilityState.BREATHABLE,
                            "A stale closed-wall scan republished breathable authority");
                    helper.assertTrue(service.metrics().lastTickInspections() <= 2, "Scan exceeded its tick budget");
                }
                helper.succeed();
            } finally {
                service.clear();
                moon.removeBlock(position, false);
            }
        });
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 20)
    public static void reloadRevokesCachedAndInFlightScansWithoutChangingResources(GameTestHelper helper) {
        ServerLevel moon = moon(helper);
        BlockPos position = allocated(helper);
        OxygenVentBlockEntity vent = room(moon, position);
        AtmosphereBoundaryCatalog catalog = basaltCatalog();
        AtmosphereManager manager = new AtmosphereManager(
                new CelestialEnvironmentService(new CelestialCatalogManager()), catalog);
        AtmosphereLevelService pending = new AtmosphereLevelService(moon, false, true,
                AtmosphereLimits.MAX_VOLUME_CELLS, 1, catalog);
        helper.runAfterDelay(1, () -> {
            try {
                manager.observeVent(moon, vent);
                manager.tick(moon.getServer());
                helper.assertTrue(manager.breathabilityAt(moon, position.above()) == BreathabilityState.BREATHABLE,
                        "Closed fixture did not establish cached authority");
                int oxygen = vent.oxygenUnits();
                int energy = vent.energyStored();
                new AtmosphereServerEvents(manager).onDatapackSync(
                        new OnDatapackSyncEvent(moon.getServer().getPlayerList(), null));
                helper.assertTrue(manager.breathabilityAt(moon, position.above()) != BreathabilityState.BREATHABLE,
                        "Server reload retained cached authority");
                helper.assertTrue(oxygen == vent.oxygenUnits() && energy == vent.energyStored(),
                        "Invalidation mutated oxygen or energy");
                manager.observeVent(moon, vent);
                manager.tick(moon.getServer());
                helper.assertTrue(manager.breathabilityAt(moon, position.above()) == BreathabilityState.BREATHABLE,
                        "The frozen rule was lost on reload");
                pending.observeVent(vent);
                pending.tick();
                helper.assertTrue(pending.metrics().activeScanTasks() == 1, "Expected an unfinished scan");
                pending.invalidateBoundaries();
                helper.assertTrue(pending.metrics().activeScanTasks() == 0 && pending.metrics().indexedVolumes() == 0,
                        "Reload retained an in-flight scan");
                manager.clear();
                helper.assertTrue(manager.metrics(moon.dimension()).isEmpty(), "Server stop retained a level cache");
                helper.succeed();
            } finally {
                manager.clear();
                pending.clear();
                moon.removeBlock(position, false);
            }
        });
    }

    private static void assertCell(GameTestHelper helper, ServerLevelVolumeWorldView view, BlockPos position,
                                   BlockState state, CellObservation expected) {
        helper.getLevel().setBlock(position, state, Block.UPDATE_CLIENTS);
        helper.assertTrue(view.observe(cell(position)) == expected, "Incorrect boundary priority for " + state);
    }

    private static AtmosphereBoundaryCatalog basaltCatalog() {
        try (AtmosphereBoundaryRegistry registry = registry()) {
            registry.forOwner("boundary_test").register(id("basalt"), Set.of(key(Blocks.BASALT)), state ->
                    state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y
                            ? AtmosphereBoundary.SEALED : AtmosphereBoundary.PERMEABLE);
            return registry.freeze();
        }
    }

    private static AtmosphereBoundaryRegistry registry() {
        return new AtmosphereBoundaryRegistry(id -> ForgeRegistries.BLOCKS.containsKey(id)
                ? ForgeRegistries.BLOCKS.getValue(id) : null);
    }

    private static OxygenVentBlockEntity room(ServerLevel level, BlockPos position) {
        for (BlockPos pos : BlockPos.betweenClosed(position.offset(-1, 0, -1), position.offset(1, 2, 1))) {
            level.setBlock(pos, Blocks.IRON_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(position.above(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(position.above(2), Blocks.BASALT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(position, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
        OxygenVentBlockEntity vent = (OxygenVentBlockEntity) level.getBlockEntity(position);
        vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing vent items"))
                .insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
        vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing vent energy")).receiveEnergy(40000, false);
        OxygenVentBlockEntity.serverTick(level, position, vent.getBlockState(), vent);
        return vent;
    }

    private static ServerLevel moon(GameTestHelper helper) {
        ServerLevel level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(level != null, "Moon Level is missing");
        return level;
    }

    private static BlockPos allocated(GameTestHelper helper) {
        BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
        return new BlockPos(pos.getX(), 220, pos.getZ());
    }

    private static VolumePosition cell(BlockPos position) {
        return new VolumePosition(position.getX(), position.getY(), position.getZ());
    }

    private static ResourceLocation key(Block block) {
        return ForgeRegistries.BLOCKS.getKey(block);
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.tryParse("boundary_test:" + path);
    }
}
