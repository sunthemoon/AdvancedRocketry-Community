package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.ServerLevelData;

/** Test-owned sky/biome cells and exact weather restoration, including failure and timeout. Never loads chunks. */
public final class SkyWeatherFixture implements AutoCloseable, GameTestListener {
    private static final int QUIET = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private final GameTestHelper helper;
    private final ServerLevel level;
    private final String label;
    private final Weather beforeWeather;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();
    private final Map<BlockPos, Holder<Biome>> biomes = new LinkedHashMap<>();
    private final List<ChunkAccess> biomeChunks = new ArrayList<>();
    private final List<Runnable> cleanup = new ArrayList<>();
    private boolean closed;

    public SkyWeatherFixture(GameTestHelper helper, String label) {
        this.helper = helper; level = helper.getLevel(); this.label = label;
        beforeWeather = Weather.capture(level);
        try {
            var field = GameTestHelper.class.getDeclaredField("testInfo"); field.setAccessible(true);
            ((GameTestInfo) field.get(helper)).addListener(this);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot install sky/weather fixture terminal cleanup", failure);
        }
    }

    public void onClose(Runnable action) { cleanup.add(action); }

    /** Clear a complete UP column, not just one roof cell, within the existing high-altitude fixture. */
    public void clearSky(BlockPos first) {
        helper.assertTrue(first.getY() >= level.getMaxBuildHeight() - 16, "Sky fixture must stay in its top 16 cells");
        for (int y = first.getY(); y < level.getMaxBuildHeight(); y++) {
            BlockPos pos = new BlockPos(first.getX(), y, first.getZ());
            LevelChunk chunk = loaded(pos);
            helper.assertTrue(blocks.size() < 32 && !chunk.getBlockEntities().containsKey(pos)
                    && !level.getBlockState(pos).hasBlockEntity(), "Sky fixture requires bounded BE-free cells");
            blocks.putIfAbsent(pos, level.getBlockState(pos));
        }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_WEATHER_FIXTURE label={} sky_before={}", label, blocks);
        blocks.keySet().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(), QUIET));
    }

    /** BiomeManager's zoom can choose adjacent quart cells; own all 27 around the queried lens. */
    public void precipitatingBiome(BlockPos lens) {
        helper.assertTrue(biomes.isEmpty(), "Biome fixture already installed");
        for (int x = -1; x <= 1; x++) { for (int y = -1; y <= 1; y++) { for (int z = -1; z <= 1; z++) {
            int qx = QuartPos.fromBlock(lens.getX()) + x, qy = QuartPos.fromBlock(lens.getY()) + y;
            int qz = QuartPos.fromBlock(lens.getZ()) + z;
            BlockPos block = new BlockPos(QuartPos.toBlock(qx), QuartPos.toBlock(qy), QuartPos.toBlock(qz));
            helper.assertTrue(!level.isOutsideBuildHeight(block), "Biome fixture exceeds build height");
            LevelChunk chunk = loaded(block);
            biomes.put(new BlockPos(qx, qy, qz), chunk.getNoiseBiome(qx, qy, qz));
            if (!biomeChunks.contains(chunk)) { biomeChunks.add(chunk); }
        } } }
        Holder<Biome> wet = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS);
        rewriteBiomes(false, wet);
        helper.assertTrue(level.getBiome(lens).value().hasPrecipitation(), "Lens fixture has no precipitation");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_WEATHER_FIXTURE label={} lens={} biome={} quart_cells={}",
                label, lens, level.getBiome(lens).unwrapKey(), biomes.size());
    }

    private LevelChunk loaded(BlockPos pos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        helper.assertTrue(chunk != null, "Sky/biome fixture requires an already loaded chunk");
        return chunk;
    }

    private void rewriteBiomes(boolean restore, Holder<Biome> wet) {
        for (ChunkAccess chunk : biomeChunks) {
            // As fillbiome does: preserve every other sample, mark dirty and resend the changed chunks to viewers.
            chunk.fillBiomesFromNoise((x, y, z, sampler) -> {
                Holder<Biome> original = biomes.get(new BlockPos(x, y, z));
                return original == null ? chunk.getNoiseBiome(x, y, z) : restore ? original : wet;
            }, level.getChunkSource().randomState().sampler());
            chunk.setUnsaved(true);
        }
        level.getChunkSource().chunkMap.resendBiomesForChunks(biomeChunks);
    }

    /** Samples and weather timers must agree across the natural readiness ticks. */
    public void clearWeather() {
        level.setWeatherParameters(100_000, 0, false, false);
        level.setRainLevel(0); level.setThunderLevel(0); level.updateSkyBrightness();
    }

    @Override public void close() { close(null); }
    private void close(Throwable primary) {
        if (closed) { return; } closed = true;
        var actions = new ArrayList<>(cleanup);
        blocks.forEach((pos, state) -> actions.add(() -> level.setBlock(pos, state, QUIET)));
        if (!biomes.isEmpty()) { actions.add(() -> rewriteBiomes(true, null)); }
        actions.add(() -> beforeWeather.restore(level));
        actions.add(() -> {
            helper.assertTrue(blocks.entrySet().stream().allMatch(entry -> level.getBlockState(entry.getKey()).equals(entry.getValue())),
                    "Sky fixture cells were not restored");
            helper.assertTrue(biomes.entrySet().stream().allMatch(entry -> {
                BlockPos quart = entry.getKey();
                return loaded(new BlockPos(QuartPos.toBlock(quart.getX()), QuartPos.toBlock(quart.getY()), QuartPos.toBlock(quart.getZ())))
                        .getNoiseBiome(quart.getX(), quart.getY(), quart.getZ()).equals(entry.getValue());
            }), "Fixture biome samples were not restored");
            helper.assertTrue(Weather.capture(level).equals(beforeWeather), "Fixture weather state was not restored");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_WEATHER_FIXTURE label={} restored blocks={} biomes={} weather=true",
                    label, blocks.size(), biomes.size());
        });
        Throwable first = primary;
        for (Runnable action : actions) {
            try { action.run(); }
            catch (RuntimeException | Error failure) {
                if (first == null) { first = failure; }
                else if (first != failure) { first.addSuppressed(failure); }
            }
        }
        if (primary == null) {
            if (first instanceof RuntimeException failure) { throw failure; }
            if (first instanceof Error failure) { throw failure; }
        }
    }

    private record Weather(long day, boolean raining, boolean thundering, int clearTime, int rainTime, int thunderTime,
                           float rain, float oldRain, float thunder, float oldThunder) {
        static Weather capture(ServerLevel level) {
            var data = (ServerLevelData) level.getLevelData();
            return new Weather(level.getDayTime(), data.isRaining(), data.isThundering(), data.getClearWeatherTime(),
                    data.getRainTime(), data.getThunderTime(), level.rainLevel, level.oRainLevel, level.thunderLevel, level.oThunderLevel);
        }
        void restore(ServerLevel level) {
            var data = (ServerLevelData) level.getLevelData();
            level.setDayTime(day); data.setRaining(raining); data.setThundering(thundering);
            data.setClearWeatherTime(clearTime); data.setRainTime(rainTime); data.setThunderTime(thunderTime);
            // Preserve both interpolation endpoints; getThunderLevel alone loses the raw value when rain is zero.
            level.rainLevel = rain; level.oRainLevel = oldRain;
            level.thunderLevel = thunder; level.oThunderLevel = oldThunder;
            level.updateSkyBrightness();
        }
    }

    @Override public void testStructureLoaded(GameTestInfo info) { }
    @Override public void testPassed(GameTestInfo info) { close(); }
    @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
}
