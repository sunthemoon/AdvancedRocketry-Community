package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup.Backup;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup.Point;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RespawnPointBackupTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static Backup backup() {
        return new Backup(new Point(CelestialIds.MOON_LEVEL, new BlockPos(-30_000_000, Integer.MIN_VALUE, 30_000_000),
                -0.0F, true), RespawnOutcome.UNLOADED);
    }

    @Test void exactTupleRoundTripsWithoutPackedPositionOrAngleNormalization() {
        Backup original = backup();
        Backup decoded = RespawnPointBackup.decode(RespawnPointBackup.encode(original)).orElseThrow();
        assertEquals(original, decoded);
        assertEquals(Float.floatToRawIntBits(-0.0F), Float.floatToRawIntBits(decoded.point().angle()));
    }

    @Test void rejectsTypesSchemaUnknownKeysBooleansAndOutcomesWithoutMutation() {
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.putInt("schema", 2), tag -> tag.putInt("schema", 0),
                tag -> tag.putString("schema", "1"), tag -> tag.remove("dimension"),
                tag -> tag.putString("dimension", "bad dimension!"), tag -> tag.putLong("x", 1),
                tag -> tag.putDouble("angle_bits", 1), tag -> tag.putByte("forced", (byte) 2),
                tag -> tag.putString("outcome", "accept"), tag -> tag.putString("outcome", "future"),
                tag -> tag.putInt("extra", 1));
        for (Consumer<CompoundTag> corrupt : corruptions) {
            CompoundTag raw = RespawnPointBackup.encode(backup());
            corrupt.accept(raw);
            CompoundTag before = raw.copy();
            assertTrue(RespawnPointBackup.decode(raw).isEmpty());
            assertEquals(before, raw);
        }
        assertTrue(RespawnPointBackup.decode(StringTag.valueOf("opaque")).isEmpty());
        assertTrue(RespawnPointBackup.decode(null).isEmpty());
    }

    @Test void boundsRejectOversizedIdsCoordinatesAndNonfiniteAngles() {
        List<Consumer<CompoundTag>> corruptions = List.of(
                tag -> tag.putString("dimension", "a:" + "x".repeat(255)),
                tag -> tag.putString("dimension", "a:" + "x".repeat(2000)),
                tag -> tag.putInt("x", 30_000_001), tag -> tag.putInt("z", -30_000_001),
                tag -> tag.putInt("angle_bits", Float.floatToRawIntBits(Float.NaN)),
                tag -> tag.putInt("angle_bits", Float.floatToRawIntBits(Float.POSITIVE_INFINITY)));
        for (Consumer<CompoundTag> corrupt : corruptions) {
            CompoundTag raw = RespawnPointBackup.encode(backup());
            corrupt.accept(raw);
            assertTrue(RespawnPointBackup.decode(raw).isEmpty());
        }
        assertThrows(IllegalArgumentException.class, () -> new Point(CelestialIds.MOON_LEVEL,
                new BlockPos(30_000_001, 0, 0), 0, false));
    }

    @Test void onlyVerifiedCompletionRemovesTheSupportedBackup() {
        CompoundTag persisted = new CompoundTag();
        RespawnPointBackup.store(persisted, backup());
        assertEquals(backup(), RespawnPointBackup.decode(persisted.get(RespawnPointBackup.KEY)).orElseThrow());
        RespawnPointBackup.complete(persisted);
        assertFalse(persisted.contains(RespawnPointBackup.KEY));
    }

    @Test void unreadableAndFutureRootsAreKeptVerbatimOnCompletion() {
        for (var raw : List.of(StringTag.valueOf("opaque"), new CompoundTag())) {
            CompoundTag persisted = new CompoundTag();
            persisted.put(RespawnPointBackup.KEY, raw);
            RespawnPointBackup.complete(persisted);
            assertSame(raw, persisted.get(RespawnPointBackup.KEY));
        }
    }

    @Test void aBackupGovernsOnlyItsOwnTupleOrTheExactClearedTupleWithoutAForeignSetter() {
        Point point = backup().point(); // Moon, angle -0.0F, forced.
        BlockPos at = point.position();
        for (boolean foreign : new boolean[] {false, true}) {
            assertTrue(RespawnPointBackup.governs(point, CelestialIds.MOON_LEVEL, at, -0.0F, true, foreign));
        }
        assertTrue(RespawnPointBackup.governs(point, Level.OVERWORLD, null, 0.0F, false, false));
        assertFalse(RespawnPointBackup.governs(point, Level.OVERWORLD, null, 0.0F, false, true)); // Another party's clear.
        // Every other tuple is newer.
        assertFalse(RespawnPointBackup.governs(point, Level.OVERWORLD, null, -0.0F, false, false));
        assertFalse(RespawnPointBackup.governs(point, Level.OVERWORLD, null, 0.0F, true, false));
        assertFalse(RespawnPointBackup.governs(point, CelestialIds.MOON_LEVEL, null, 0.0F, false, false));
        assertFalse(RespawnPointBackup.governs(point, Level.OVERWORLD, at, -0.0F, true, false));
        assertFalse(RespawnPointBackup.governs(point, CelestialIds.MOON_LEVEL, at.east(), -0.0F, true, false));
        assertFalse(RespawnPointBackup.governs(point, CelestialIds.MOON_LEVEL, at, 0.0F, true, false));
        assertFalse(RespawnPointBackup.governs(point, CelestialIds.MOON_LEVEL, at, -0.0F, false, false));
    }

    @Test void aNewNativeTupleCanBeProtectedWithoutLosingAnOpaqueOldBackup() {
        CompoundTag persisted = new CompoundTag();
        CompoundTag future = new CompoundTag();
        future.putInt("schema", 99);
        future.putString("opaque", "x".repeat(2048));
        CompoundTag original = future.copy();
        persisted.put(RespawnPointBackup.KEY, future);
        RespawnPointBackup.store(persisted, backup());
        assertEquals(backup(), RespawnPointBackup.decode(persisted.get(RespawnPointBackup.KEY)).orElseThrow());
        assertSame(future, persisted.getCompound(RespawnPointBackup.KEY).get(RespawnPointBackup.RETAINED));
        RespawnPointBackup.store(persisted, backup()); // Repeated decisions do not nest envelopes.
        assertSame(future, persisted.getCompound(RespawnPointBackup.KEY).get(RespawnPointBackup.RETAINED));
        RespawnPointBackup.complete(persisted);
        assertSame(future, persisted.get(RespawnPointBackup.KEY));
        assertEquals(original, future);
    }
}
