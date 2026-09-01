package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.VentOperatingStatus;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereLevelMetrics;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.AtmosphereRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AtmospherePerformanceGameTests {
    private static final String TEMPLATE = "atmosphere_test";

    private AtmospherePerformanceGameTests() {
    }

    @GameTest(template = TEMPLATE, batch = "atmosphere_performance", timeoutTicks = 6_100)
    public static void sixteenVentsRespectBudgetsForFiveMinutes(GameTestHelper helper) {
        ServerLevel moon = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
        helper.assertTrue(moon != null, "Moon Level is unavailable for the 16-Vent run");
        BlockPos allocation = helper.absolutePos(BlockPos.ZERO);
        List<BlockPos> ventPositions = new ArrayList<>();
        for (int gridX = 0; gridX < 4; gridX++) {
            for (int gridZ = 0; gridZ < 4; gridZ++) {
                ventPositions.add(new BlockPos(
                        allocation.getX() + 1 + gridX * 4,
                        32,
                        allocation.getZ() + 1 + gridZ * 4
                ));
            }
        }
        Set<ChunkPos> forcedChunks = forceVentChunks(moon, ventPositions);
        for (BlockPos position : ventPositions) {
            buildOneCellShell(moon, position);
        }
        boolean[] placementReady = {false};
        boolean[] ventsPlaced = {false};
        int[] completedActiveTicks = {0};
        int[] startingOxygen = new int[ventPositions.size()];
        boolean[] measuring = {false};
        int[] warmupTicks = {0};
        int[] peakInspections = {0};
        long[] startingInspections = {-1L};
        long[] startingServiceTicks = {-1L};
        long[] startingGameTime = {-1L};
        long startedAt = System.nanoTime();
        helper.runAfterDelay(1, () -> placementReady[0] = true);

        helper.onEachTick(() -> {
            try {
                if (!placementReady[0]) {
                    return;
                }
                if (!ventsPlaced[0]) {
                    for (BlockPos position : ventPositions) {
                        placePreparedVent(moon, position);
                    }
                    ventsPlaced[0] = true;
                    return;
                }
                List<OxygenVentBlockEntity> vents = loadedVents(moon, ventPositions);
                for (OxygenVentBlockEntity vent : vents) {
                    refillEnergy(vent);
                }
                AtmosphereLevelMetrics metrics = AtmosphereRuntime.metrics(moon).orElseThrow(
                        () -> new IllegalStateException("Runtime atmosphere metrics are unavailable")
                );
                peakInspections[0] = Math.max(peakInspections[0], metrics.lastTickInspections());
                helper.assertTrue(
                        metrics.lastTickInspections() <= AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK,
                        "16-Vent run exceeded the per-Level inspection budget"
                );

                if (!measuring[0]) {
                    warmupTicks[0]++;
                    if (warmupTicks[0] < 90) {
                        return;
                    }
                    long activeVents = vents.stream()
                            .filter(vent -> vent.status() == VentOperatingStatus.ACTIVE)
                            .count();
                    AdvancedRocketryCommunity.LOGGER.info(
                            "ARCE_ATMOSPHERE_PERF_WARMUP ticks={} active_vents={} tracked={} pending={} "
                                    + "inspections={} dirty={} statuses={}",
                            warmupTicks[0],
                            activeVents,
                            metrics.trackedVents(),
                            metrics.pendingScanTasks(),
                            metrics.lastTickInspections(),
                            metrics.dirtyPositions(),
                            vents.stream().map(OxygenVentBlockEntity::status).toList()
                    );
                    helper.assertTrue(
                            activeVents == vents.size()
                                    && metrics.activeProviders() == vents.size()
                                    && metrics.pendingScanTasks() == 0,
                            "16-Vent warmup did not converge within 90 ticks: statuses="
                                    + vents.stream().map(OxygenVentBlockEntity::status).toList()
                                    + " metrics=" + metrics
                    );
                    for (int index = 0; index < vents.size(); index++) {
                        startingOxygen[index] = vents.get(index).oxygenUnits();
                    }
                    startingInspections[0] = metrics.totalInspections();
                    startingServiceTicks[0] = metrics.completedServiceTicks();
                    startingGameTime[0] = moon.getGameTime();
                    measuring[0] = true;
                    return;
                }

                helper.assertTrue(
                        vents.stream().allMatch(vent -> vent.status() == VentOperatingStatus.ACTIVE)
                                && metrics.activeProviders() == vents.size(),
                        "A Vent left ACTIVE during the five-minute measurement: statuses="
                                + vents.stream().map(OxygenVentBlockEntity::status).toList()
                                + " metrics=" + metrics
                );
                long elapsedGameTicks = moon.getGameTime() - startingGameTime[0];
                long completedServiceTicks = metrics.completedServiceTicks() - startingServiceTicks[0];
                helper.assertTrue(elapsedGameTicks >= 0L && elapsedGameTicks <= 6_001L,
                        "Atmosphere runtime did not complete 6000 supply passes within the fixed bound");
                helper.assertTrue(completedServiceTicks >= 0L && completedServiceTicks <= 6_000L,
                        "Atmosphere service crossed the exact 6000-pass boundary");
                completedActiveTicks[0] = Math.toIntExact(completedServiceTicks);
                // completedServiceTicks advances only after the server authority applies supply,
                // independent of GameTest callback ordering around ServerTick END.
                if (completedServiceTicks < 6_000L) {
                    return;
                }

                helper.assertTrue(metrics.activeProviders() >= 16,
                        "Runtime did not retain the 16 independent active providers");
                for (int index = 0; index < vents.size(); index++) {
                    OxygenVentBlockEntity vent = vents.get(index);
                    helper.assertTrue(vent.status() == VentOperatingStatus.ACTIVE,
                            "A Vent stopped during the five-minute run");
                    helper.assertTrue(startingOxygen[index] - vent.oxygenUnits() == 300,
                            "A Vent consumed a non-deterministic oxygen amount over 6000 active ticks: position="
                                    + vent.getBlockPos() + " start=" + startingOxygen[index]
                                    + " end=" + vent.oxygenUnits() + " energy=" + vent.energyStored());
                }
                double elapsedSeconds = (System.nanoTime() - startedAt) / 1_000_000_000.0D;
                AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_ATMOSPHERE_PERF vents=16 simulated_ticks={} active={} peak_inspections={} "
                                + "measured_inspections={} elapsed_seconds={}",
                        completedActiveTicks[0],
                        metrics.activeProviders(),
                        peakInspections[0],
                        metrics.totalInspections() - startingInspections[0],
                        String.format(java.util.Locale.ROOT, "%.3f", elapsedSeconds)
                );
                clearVentRooms(moon, ventPositions);
                releaseForcedChunks(moon, forcedChunks);
                helper.succeed();
            } catch (RuntimeException failure) {
                clearVentRooms(moon, ventPositions);
                releaseForcedChunks(moon, forcedChunks);
                throw failure;
            }
        });
    }

    private static void buildOneCellShell(ServerLevel level, BlockPos ventPosition) {
        buildShell(
                level,
                ventPosition.offset(-1, 0, -1),
                ventPosition.offset(1, 2, 1)
        );
    }

    private static void buildShell(ServerLevel level, BlockPos minimum, BlockPos maximum) {
        for (int x = minimum.getX(); x <= maximum.getX(); x++) {
            for (int y = minimum.getY(); y <= maximum.getY(); y++) {
                for (int z = minimum.getZ(); z <= maximum.getZ(); z++) {
                    boolean boundary = x == minimum.getX() || x == maximum.getX()
                            || y == minimum.getY() || y == maximum.getY()
                            || z == minimum.getZ() || z == maximum.getZ();
                    level.setBlock(
                            new BlockPos(x, y, z),
                            (boundary ? Blocks.IRON_BLOCK : Blocks.AIR).defaultBlockState(),
                            Block.UPDATE_ALL
                    );
                }
            }
        }
    }

    private static void placePreparedVent(ServerLevel level, BlockPos position) {
        level.setBlock(position, ModBlocks.OXYGEN_VENT.get().defaultBlockState(), Block.UPDATE_ALL);
        OxygenVentBlockEntity vent = (OxygenVentBlockEntity) level.getBlockEntity(position);
        if (vent == null) {
            throw new IllegalStateException("Missing placed Vent BlockEntity");
        }
        IItemHandler input = vent.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                .orElseThrow(() -> new IllegalStateException("Missing Vent input capability"));
        ItemStack remainder = input.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get()), false);
        if (!remainder.isEmpty()) {
            throw new IllegalStateException("Could not insert Vent test oxygen");
        }
        OxygenVentBlockEntity.serverTick(level, position, vent.getBlockState(), vent);
        refillEnergy(vent);
    }

    private static void refillEnergy(OxygenVentBlockEntity vent) {
        IEnergyStorage energy = vent.getCapability(ForgeCapabilities.ENERGY, Direction.NORTH)
                .orElseThrow(() -> new IllegalStateException("Missing Vent energy capability"));
        energy.receiveEnergy(AtmosphereLimits.VENT_ENERGY_CAPACITY, false);
    }

    private static List<OxygenVentBlockEntity> loadedVents(ServerLevel level, List<BlockPos> positions) {
        List<OxygenVentBlockEntity> vents = new ArrayList<>(positions.size());
        for (BlockPos position : positions) {
            if (!(level.getBlockEntity(position) instanceof OxygenVentBlockEntity vent)) {
                throw new IllegalStateException("Missing loaded Vent at " + position);
            }
            vents.add(vent);
        }
        return vents;
    }

    private static Set<ChunkPos> forceVentChunks(ServerLevel level, List<BlockPos> positions) {
        Set<ChunkPos> chunks = new LinkedHashSet<>();
        for (BlockPos position : positions) {
            ChunkPos chunk = new ChunkPos(position);
            if (chunks.add(chunk)) {
                level.setChunkForced(chunk.x, chunk.z, true);
            }
        }
        return chunks;
    }

    private static void releaseForcedChunks(ServerLevel level, Set<ChunkPos> chunks) {
        for (ChunkPos chunk : chunks) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static void clearVentRooms(ServerLevel level, List<BlockPos> positions) {
        for (BlockPos position : positions) {
            for (BlockPos target : BlockPos.betweenClosed(
                    position.offset(-1, 0, -1),
                    position.offset(1, 2, 1)
            )) {
                level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }
}
