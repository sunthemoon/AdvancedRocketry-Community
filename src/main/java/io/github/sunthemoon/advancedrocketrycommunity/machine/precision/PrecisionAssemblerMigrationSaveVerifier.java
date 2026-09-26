package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockControllerNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockPartBindingNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;

/** Confirms the exact migration roots were flushed, without loading world chunks. */
final class PrecisionAssemblerMigrationSaveVerifier {
    private static final String[] CONTROLLER_ROOTS = {
            MultiblockControllerNbtCodec.ROOT,
            ProcessStatePersistence.ROOT,
            ProcessJournalPersistence.ROOT,
            PrecisionAssemblerResourcePersistence.ROOT
    };
    private static final String[] PORT_ROOTS = {
            MultiblockPartBindingNbtCodec.ROOT,
            PrecisionAssemblerPortPersistence.ROOT,
            PrecisionAssemblerPortMigrationPersistence.ROOT
    };

    private final Path regionDirectory;
    private final Map<ChunkPos, Optional<CompoundTag>> chunks = new HashMap<>();
    private String lastMismatch = "none";

    PrecisionAssemblerMigrationSaveVerifier(ServerLevel level) {
        Path world = level.getServer().getWorldPath(LevelResource.ROOT);
        regionDirectory = DimensionType.getStorageFolder(level.dimension(), world).resolve("region");
    }

    boolean controllerSaved(PrecisionAssemblerBlockEntity controller) throws IOException {
        return verify(controller.saveWithFullMetadata(), controller.getBlockPos(), CONTROLLER_ROOTS);
    }

    boolean portsSaved(PrecisionAssemblerPortSet ports) throws IOException {
        for (PrecisionAssemblerPortBlockEntity port : ports.inputs()) {
            if (!portSaved(port)) {
                return false;
            }
        }
        for (PrecisionAssemblerPortBlockEntity port : ports.outputs()) {
            if (!portSaved(port)) {
                return false;
            }
        }
        // Energy stays physically owned, but its repaired binding must survive
        // restart before PREPARING-only binding repair is no longer available.
        return portSaved(ports.energy());
    }

    private boolean portSaved(PrecisionAssemblerPortBlockEntity port) throws IOException {
        return verify(port.saveWithFullMetadata(), port.getBlockPos(), PORT_ROOTS);
    }

    String lastMismatch() {
        return lastMismatch;
    }

    private boolean verify(CompoundTag expected, BlockPos position, String... roots) throws IOException {
        Optional<String> mismatch = mismatch(expected, read(position), roots);
        lastMismatch = mismatch.map(reason -> position + ": " + reason).orElse("none");
        return mismatch.isEmpty();
    }

    private Optional<CompoundTag> read(BlockPos position) throws IOException {
        ChunkPos chunk = new ChunkPos(position);
        Optional<CompoundTag> root = chunks.get(chunk);
        if (root == null) {
            root = PrecisionAssemblerPersistedChunkReader.readChunk(regionDirectory, chunk);
            chunks.put(chunk, root);
        }
        return root.flatMap(value -> PrecisionAssemblerPersistedChunkReader.findBlockEntity(value, position));
    }

    static boolean matches(CompoundTag expected, Optional<CompoundTag> persisted, String... roots) {
        return mismatch(expected, persisted, roots).isEmpty();
    }

    private static Optional<String> mismatch(
            CompoundTag expected,
            Optional<CompoundTag> persisted,
            String... roots
    ) {
        if (persisted.isEmpty()) {
            return Optional.of("missing or invalid saved BlockEntity");
        }
        CompoundTag actual = persisted.orElseThrow();
        if (!expected.getString("id").equals(actual.getString("id"))
                || expected.getInt("x") != actual.getInt("x")
                || expected.getInt("y") != actual.getInt("y")
                || expected.getInt("z") != actual.getInt("z")) {
            return Optional.of("saved BlockEntity identity differs");
        }
        for (String root : roots) {
            if (!Objects.equals(expected.get(root), actual.get(root))) {
                return Optional.of(root + " differs (expected hash="
                        + Objects.hashCode(expected.get(root)) + ", saved hash="
                        + Objects.hashCode(actual.get(root)) + ")");
            }
        }
        return Optional.empty();
    }
}
