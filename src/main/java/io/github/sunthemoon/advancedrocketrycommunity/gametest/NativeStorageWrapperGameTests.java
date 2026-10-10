package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/** Public native storage observations; no published-tag mutation or hosted race attribution. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class NativeStorageWrapperGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SAMPLES = 4;
    private static final int CHUNK_BYTES = 1_048_576;
    private static final int CHUNK_DEPTH = 32;
    private static final int CHUNK_NODES = 65_536;

    private NativeStorageWrapperGameTests() { }

    @GameTest(template = "empty", batch = "native_storage_wrapper_records", timeoutTicks = 100)
    public static void publicWrapperPreservesSubmittedValuesAcrossFlushAndReopen(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getServer().isSameThread(), "Storage fixture needs the native server thread");
        try {
            Path directory = ownedDirectory("records-");
            CompoundTag[] roots = new CompoundTag[SAMPLES];
            CompoundTag[] children = new CompoundTag[SAMPLES];
            boolean[] initialPresent = new boolean[SAMPLES];
            boolean[] initialRootIdentity = new boolean[SAMPLES];
            boolean[] initialChildIdentity = new boolean[SAMPLES];
            try (ChunkStorage storage = new ChunkStorage(directory, level.getServer().getFixerUpper(), true)) {
                for (int index = 0; index < SAMPLES; index++) {
                    CompoundTag child = new CompoundTag();
                    child.putInt("child_marker", 17 + index);
                    CompoundTag root = new CompoundTag();
                    root.putInt("root_marker", 11 + index);
                    root.put("child", child);
                    roots[index] = root;
                    children[index] = child;
                }
                // Construct all inputs before publication. Only identity/value reads follow each write.
                for (int index = 0; index < SAMPLES; index++) {
                    storage.write(new ChunkPos(index, 0), roots[index]);
                }
                for (int index = 0; index < SAMPLES; index++) {
                    Optional<CompoundTag> read = await(storage.read(new ChunkPos(index, 0)));
                    initialPresent[index] = read.isPresent();
                    initialRootIdentity[index] = read.orElse(null) == roots[index];
                    initialChildIdentity[index] = read.isPresent() && read.get().get("child") == children[index];
                    if (read.isPresent()) { assertMarkers(helper, read.get(), index); }
                }
                // Native flush/close have internal joins. The owning command also has a wall-clock deadline.
                storage.flushWorker();
                for (int index = 0; index < SAMPLES; index++) {
                    CompoundTag read = await(storage.read(new ChunkPos(index, 0))).orElseThrow();
                    assertMarkers(helper, read, index);
                    LOGGER.info("NATIVE_WRAPPER_RECORD index={} initial_present={} initial_root_identity={} "
                                    + "initial_child_identity={} flushed_root_identity={} flushed_child_identity={}",
                            index, initialPresent[index], initialRootIdentity[index], initialChildIdentity[index],
                            read == roots[index], read.get("child") == children[index]);
                }
            }
            // A fresh wrapper crosses a closed-worker lifetime, not a crash or power-loss boundary.
            try (ChunkStorage reopened = new ChunkStorage(directory, level.getServer().getFixerUpper(), true)) {
                for (int index = 0; index < SAMPLES; index++) {
                    CompoundTag read = await(reopened.read(new ChunkPos(index, 0))).orElseThrow();
                    assertMarkers(helper, read, index);
                    LOGGER.info("NATIVE_WRAPPER_REOPEN index={} root_identity={} child_identity={}",
                            index, read == roots[index], read.get("child") == children[index]);
                }
            }
            LOGGER.info("NATIVE_WRAPPER_RECORDS closed=true reopened_records={} directory={}", SAMPLES,
                    directory.getFileName());
            helper.succeed();
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Public native storage fixture interrupted", failure);
        } catch (IOException | ExecutionException | TimeoutException failure) {
            throw new IllegalStateException("Public native storage fixture failed", failure);
        }
    }

    @GameTest(template = "empty", batch = "native_storage_wrapper_conversion", timeoutTicks = 100)
    public static void currentVersionConversionUsesAnExclusiveUnpublishedNativeChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.assertTrue(level.getServer().isSameThread(), "Conversion fixture needs the native server thread");
        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        helper.assertTrue(chunk != null && chunk.getLevel() == level, "Conversion needs an already loaded chunk");
        CompoundTag serialized = ChunkSerializer.write(level, chunk);
        helper.assertTrue(fits(serialized), "Native conversion input exceeds the fixture's NBT bounds");
        // Native capability providers may retain children. Conversion operates only on a bounded deep copy.
        CompoundTag exclusive = serialized.copy();
        CompoundTag before = exclusive.copy();
        int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        helper.assertTrue(exclusive.getInt("DataVersion") == currentVersion,
                "Native serialization did not supply the current DataVersion");
        try {
            Path directory = ownedDirectory("conversion-");
            try (ChunkStorage storage = new ChunkStorage(directory, level.getServer().getFixerUpper(), true)) {
                CompoundTag contextProbe = exclusive.copy();
                ChunkStorage.injectDatafixingContext(contextProbe, level.dimension(), Optional.empty());
                helper.assertTrue(contextProbe.contains("__context", Tag.TAG_COMPOUND),
                        "The public context helper did not populate the exclusive probe");
                helper.assertTrue(contextProbe.getCompound("__context").getString("dimension")
                                .equals(level.dimension().location().toString()),
                        "The context helper did not retain the actual dimension identity");
                helper.assertTrue(exclusive.equals(before), "The separate context probe modified the conversion input");
                CompoundTag converted = storage.upgradeChunkTag(level.dimension(), level::getDataStorage,
                        exclusive, Optional.empty());
                helper.assertTrue(fits(converted), "Native conversion output exceeds the fixture's NBT bounds");
                helper.assertFalse(converted.contains("__context"), "Conversion leaked temporary context into its result");
                helper.assertTrue(converted.equals(before), "Current-version conversion changed native chunk values");
                helper.assertTrue(serialized.equals(before), "Exclusive conversion modified the serializer's tree");
                LOGGER.info("NATIVE_WRAPPER_CONVERSION data_version={} dimension={} "
                                + "generator_key_present=false result_root_identity={} "
                                + "context_probe_mutated=true published=false serialized_values_unchanged=true",
                        currentVersion, level.dimension().location(), converted == exclusive);
            }
            LOGGER.info("NATIVE_WRAPPER_CONVERSION closed=true directory={}", directory.getFileName());
            helper.succeed();
        } catch (IOException failure) {
            throw new IllegalStateException("Exclusive native conversion fixture failed", failure);
        }
    }

    private static boolean fits(CompoundTag tag) {
        return BoundedNbt.fits(tag, CHUNK_BYTES, CHUNK_DEPTH, CHUNK_NODES);
    }

    private static Path ownedDirectory(String prefix) throws IOException {
        Path parent = Path.of("native-storage-wrapper-contract").toAbsolutePath().normalize();
        Files.createDirectories(parent);
        return Files.createTempDirectory(parent, prefix);
    }

    private static void assertMarkers(GameTestHelper helper, CompoundTag tag, int index) {
        helper.assertTrue(tag.getInt("root_marker") == 11 + index
                        && tag.contains("child", Tag.TAG_COMPOUND)
                        && tag.getCompound("child").getInt("child_marker") == 17 + index,
                "Native wrapper changed a submitted marker at sample " + index);
    }

    private static <T> T await(CompletableFuture<T> future)
            throws InterruptedException, ExecutionException, TimeoutException {
        return future.get(5, TimeUnit.SECONDS);
    }
}
