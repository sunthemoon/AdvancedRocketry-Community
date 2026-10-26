package io.github.sunthemoon.advancedrocketrycommunity.persistence.migration;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Discovery must not silently ignore a file bearing the stable rejoin identity. */
class RocketRejoinDiscoveryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path world;
    @Test void invalidSidecarIsDiscoveredBeforeStartup() throws Exception { refuses(1, -1); }
    @Test void futureSidecarIsDiscoveredBeforeStartup() throws Exception { refuses(2, 0); }
    private void refuses(int schema, long sequence) throws Exception {
        Path file = Files.createDirectories(world.resolve("data")).resolve("advancedrocketrycommunity_rocket_rejoins.dat");
        var payload = new CompoundTag(); payload.putInt("schema_version", schema);
        payload.putString("format_epoch", "v1.8.0-rocket-rejoins"); payload.putLong("next_sequence", sequence);
        payload.put("batches", new ListTag()); payload.put("entries", new ListTag());
        var outer = new CompoundTag(); outer.put("data", payload); NbtIo.writeCompressed(outer, file.toFile());
        byte[] before = Files.readAllBytes(file);
        for (int i = 0; i < 2; i++) {
            assertThrows(SavedDataMigrationException.class, () -> new WorldDataMigrationService().migrate(world));
            assertArrayEquals(before, Files.readAllBytes(file));
        }
    }
}
