package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import net.minecraft.SharedConstants;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;

/** Empty 16x8x16 allocation that keeps atmosphere GameTests spatially isolated. */
public final class AtmosphereGameTestStructureProvider implements DataProvider {
    private final Path outputPath;

    public AtmosphereGameTestStructureProvider(PackOutput output) {
        outputPath = output.getOutputFolder(PackOutput.Target.DATA_PACK)
                .resolve(AdvancedRocketryCommunity.MOD_ID)
                .resolve("structures/atmosphere_test.nbt");
    }

    @Override
    public CompletableFuture<?> run(CachedOutput output) {
        try {
            byte[] payload = createPayload();
            output.writeIfNeeded(outputPath, payload, Hashing.sha256().hashBytes(payload));
            return CompletableFuture.completedFuture(null);
        } catch (IOException exception) {
            return CompletableFuture.failedFuture(exception);
        }
    }

    @Override
    public String getName() {
        return "ARCE atmosphere GameTest structure";
    }

    private static byte[] createPayload() throws IOException {
        CompoundTag structure = new CompoundTag();
        structure.putInt("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        structure.put("size", coordinates(16, 8, 16));
        CompoundTag air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        ListTag palette = new ListTag();
        palette.add(air);
        structure.put("palette", palette);
        CompoundTag origin = new CompoundTag();
        origin.put("pos", coordinates(0, 0, 0));
        origin.putInt("state", 0);
        ListTag blocks = new ListTag();
        blocks.add(origin);
        structure.put("blocks", blocks);
        structure.put("entities", new ListTag());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtIo.writeCompressed(structure, bytes);
        return bytes.toByteArray();
    }

    private static ListTag coordinates(int x, int y, int z) {
        ListTag values = new ListTag();
        values.add(IntTag.valueOf(x));
        values.add(IntTag.valueOf(y));
        values.add(IntTag.valueOf(z));
        return values;
    }
}
