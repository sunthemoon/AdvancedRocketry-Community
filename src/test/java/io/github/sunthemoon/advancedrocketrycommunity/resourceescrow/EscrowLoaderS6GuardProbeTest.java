package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.SatelliteBayRoot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6GuardProbeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void exactGateAnd257DistinctBoundedCoordinates() {
        for (String cut : java.util.List.of("LF_S6_G1_VETO", "LF_S6_G1_SATURATION")) {
            assertTrue(EscrowLoaderS6GuardProbe.selected(true, cut));
            assertFalse(EscrowLoaderS6GuardProbe.selected(false, cut));
            assertFalse(EscrowLoaderS6GuardProbe.selected(true, cut + "_OTHER"));
        }
        assertEquals(257, java.util.stream.IntStream.range(0, 257).mapToObj(EscrowLoaderS6GuardProbe::position)
                .map(net.minecraft.world.level.ChunkPos::new).distinct().count());
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6GuardProbe.position(-1));
        assertThrows(IllegalArgumentException.class, () -> EscrowLoaderS6GuardProbe.position(257));
    }
    @Test void nativeIncompatibilityKeepsOriginalRootAndExactNoncanonicalBits() {
        var original = new CompoundTag(); original.putInt("schema_version", 1); original.putInt("quantity", 5000);
        var before = original.copy(); var invalid = EscrowLoaderS6GuardProbe.incompatible(original);
        assertEquals(before, original);
        assertFalse(SatelliteBayRoot.nativelyKeepable(invalid));
        assertEquals(0x7fc00001, Float.floatToRawIntBits(invalid.getFloat("w211_nan")));
        assertEquals(5000, invalid.getInt("quantity"));
    }
}
