package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.GZIPOutputStream;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RocketRejoinPlayerFileTest {
    @TempDir Path directory;
    static final UUID PLAYER = new UUID(159, 1), RELEASE = new UUID(159, 2), GENERATION = new UUID(159, 3);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    static RocketRejoinPlayerFile.Expected expected() {
        return new RocketRejoinPlayerFile.Expected(PLAYER, RELEASE, GENERATION, "minecraft:overworld", 2.5, 70, -3.5);
    }
    static ListTag vector(double x, double y, double z) {
        var list = new ListTag(); for (double v : new double[]{x, y, z}) { list.add(DoubleTag.valueOf(v)); } return list;
    }
    static CompoundTag valid() {
        var root = new CompoundTag(); root.putUUID("UUID", PLAYER); root.putString("Dimension", "minecraft:overworld");
        root.put("Pos", vector(2.5, 70, -3.5)); root.put("Motion", vector(0, 0, 0)); root.putFloat("FallDistance", 0);
        root.putFloat("Health", 1); root.putShort("DeathTime", (short) 0);
        var forge = new CompoundTag(); forge.put(RocketRejoinMarker.KEY, RocketRejoinMarker.encode(new RocketRejoinMarker.Value(RELEASE, PLAYER, GENERATION)));
        root.put("ForgeData", forge); return root;
    }
    Path file() { return directory.resolve(PLAYER + ".dat"); }
    void write(CompoundTag tag) throws Exception { try (var out = Files.newOutputStream(file())) { NbtIo.writeCompressed(tag, out); } }
    RocketRejoinPlayerFile.Result scan() { return RocketRejoinPlayerFile.scan(directory, expected()); }
    @Test void exactInstalledFileAndUnknownSubtrees() throws Exception {
        var tag = valid(); tag.putByteArray("InventoryPayload", new byte[2_000_000]);
        var unknown = new CompoundTag(); unknown.putString("UUID", "not a needed nested key"); tag.put("Unknown", unknown);
        write(tag); assertEquals(RocketRejoinPlayerFile.Status.MATCH, scan().status());
    }
    @Test void everyProofFieldIsRequiredAndExact() throws Exception {
        for (Consumer<CompoundTag> change : List.<Consumer<CompoundTag>>of(
                r -> r.putUUID("UUID", UUID.randomUUID()), r -> r.putString("Dimension", "minecraft:the_nether"),
                r -> r.put("Pos", vector(Math.nextUp(2.5), 70, -3.5)), r -> r.put("Pos", vector(Double.NaN, 70, -3.5)),
                r -> r.put("Motion", vector(0, 0.001, 0)), r -> r.putFloat("FallDistance", 1),
                r -> r.putFloat("Health", 0), r -> r.putFloat("Health", Float.NaN), r -> r.putShort("DeathTime", (short) 1),
                r -> r.put("RootVehicle", new CompoundTag()), r -> r.getCompound("ForgeData").remove(RocketRejoinMarker.KEY),
                r -> r.getCompound("ForgeData").getCompound(RocketRejoinMarker.KEY).putInt("schema", 2),
                r -> r.getCompound("ForgeData").getCompound(RocketRejoinMarker.KEY).putUUID("generation", UUID.randomUUID()))) {
            var tag = valid(); change.accept(tag); write(tag); assertNotEquals(RocketRejoinPlayerFile.Status.MATCH, scan().status());
        }
        for (String key : valid().getAllKeys()) { var tag = valid(); tag.remove(key); write(tag); assertNotEquals(RocketRejoinPlayerFile.Status.MATCH, scan().status(), key); }
    }
    @Test void duplicateNeededKeysAndMalformedLengthsAreUnverifiable() throws Exception {
        var raw = new ByteArrayOutputStream(); NbtIo.write(valid(), new DataOutputStream(raw));
        var duplicate = new ByteArrayOutputStream(); duplicate.write(raw.toByteArray(), 0, raw.size() - 1);
        var out = new DataOutputStream(duplicate); out.writeByte(5); out.writeUTF("Health"); out.writeFloat(1); out.writeByte(0);
        gzip(duplicate.toByteArray()); assertNotEquals(RocketRejoinPlayerFile.Status.MATCH, scan().status());
        raw.reset(); out = new DataOutputStream(raw); out.writeByte(10); out.writeUTF("");
        out.writeByte(7); out.writeUTF("Unknown"); out.writeInt(Integer.MAX_VALUE);
        gzip(raw.toByteArray()); assertEquals(RocketRejoinPlayerFile.Status.OVERSIZED, scan().status());
    }
    @Test void diskInflatedDepthAndTimeCeilingsAreIndependent() throws Exception {
        write(valid());
        assertEquals(RocketRejoinPlayerFile.Status.OVERSIZED, RocketRejoinPlayerFile.scan(directory, expected(),
                new RocketRejoinPlayerFile.Limits(8, 512L << 20, 512, 10_000_000_000L), System::nanoTime).status());
        var tag = valid(); tag.putByteArray("Large", new byte[4096]); write(tag);
        assertEquals(RocketRejoinPlayerFile.Status.OVERSIZED, RocketRejoinPlayerFile.scan(directory, expected(),
                new RocketRejoinPlayerFile.Limits(64L << 20, 1024, 512, 10_000_000_000L), System::nanoTime).status());
        tag = valid(); var nested = tag; for (int i = 0; i < 513; i++) { var next = new CompoundTag(); nested.put("n", next); nested = next; }
        write(tag); assertEquals(RocketRejoinPlayerFile.Status.DEPTH, scan().status());
        write(valid()); var clock = new java.util.concurrent.atomic.AtomicLong();
        assertEquals(RocketRejoinPlayerFile.Status.TIMEOUT, RocketRejoinPlayerFile.scan(directory, expected(),
                RocketRejoinPlayerFile.Limits.DEFAULT, () -> clock.getAndAdd(1_000_000_000L)).status());
    }
    @Test void ignoresBackupAndRefusesNonregularFiles() throws Exception {
        write(valid()); Files.move(file(), directory.resolve(PLAYER + ".dat_old"));
        assertEquals(RocketRejoinPlayerFile.Status.IO, scan().status());
        Files.createDirectory(file()); assertEquals(RocketRejoinPlayerFile.Status.IO, scan().status());
    }
    void gzip(byte[] raw) throws Exception { try (var out = new GZIPOutputStream(Files.newOutputStream(file()))) { out.write(raw); } }
}
