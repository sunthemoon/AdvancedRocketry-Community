package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/** Generates the one-block empty structure used by the bootstrap GameTest. */
public final class EmptyGameTestStructureProvider implements DataProvider {
    private final Path outputPath;

    public EmptyGameTestStructureProvider(PackOutput output) {
        this.outputPath = output
                .getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(AdvancedRocketryCommunity.MOD_ID)
                .resolve("structures/empty.nbt");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        try {
            byte[] payload = createStructurePayload();
            output.writeIfNeeded(outputPath, payload, Hashing.sha256().hashBytes(payload));
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public String getName() {
        return "ARCE bootstrap GameTest structure";
    }

    private static byte[] createStructurePayload() throws IOException {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        structure.put("size", coordinateList(1, 1, 1));

        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        ListTag palette = new ListTag();
        palette.add(air);
        structure.put("palette", palette);

        CompoundTag block = new CompoundTag();
        block.put("pos", coordinateList(0, 0, 0));
        block.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(block);
        structure.put("blocks", blocks);
        structure.put("entities", new ListTag());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(structure, bytes);
        return bytes.toByteArray();
    }

    private static ListTag coordinateList(int x, int y, int z) {
        ListTag coordinates = new ListTag();
        coordinates.add(IntTag.valueOf(x));
        coordinates.add(IntTag.valueOf(y));
        coordinates.add(IntTag.valueOf(z));
        return coordinates;
    }
}
