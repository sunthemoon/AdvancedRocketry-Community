package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.device;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class LifeSupportCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void legacyVentMigrationPreservesEveryBalanceAndOxygenPhase() {
        CompoundTag root = OxygenVentPersistence.encode(16, 15, 3999, 19999, 19);
        root.putInt("schema_version", 1); root.remove("scrubbers");
        CompoundTag parent = new CompoundTag(); parent.put(OxygenVentPersistence.DATA_KEY, root);
        var decoded = OxygenVentPersistence.decode(parent);
        assertEquals(OxygenVentPersistence.DecodeStatus.VALID, decoded.status());
        assertEquals(16, decoded.inputCount()); assertEquals(15, decoded.outputCount());
        assertEquals(3999, decoded.oxygenUnits()); assertEquals(19999, decoded.energy()); assertEquals(19, decoded.oxygenPhase());
        assertNull(decoded.scrubbers());
    }
    @Test void ventBanksRoundTripGenerationAndPhaseWithoutDuplicatingStorage() {
        CompoundTag bank = new ScrubberBank().encode(); bank.putInt("phase", 3);
        assertEquals(bank, ScrubberBank.decode(bank).encode());
        CompoundTag root = OxygenVentPersistence.encode(1, 2, 10, 20, 7, bank);
        CompoundTag outer = new CompoundTag(); outer.put(OxygenVentPersistence.DATA_KEY, root);
        assertEquals(bank, OxygenVentPersistence.decode(outer).scrubbers());
        root.getCompound("scrubbers").putInt("phase", 4);
        assertEquals(OxygenVentPersistence.DecodeStatus.INVALID, OxygenVentPersistence.decode(outer).status());
    }
    @Test void bankAndDetectorStrictTypesKeysEnumsAndBoundsRefuseWithoutMutating() {
        CompoundTag bank = new ScrubberBank().encode(); bank.putByte("phase", (byte) 0);
        CompoundTag before = bank.copy(); assertNull(ScrubberBank.decode(bank)); assertEquals(before, bank);
        bank = new ScrubberBank().encode(); bank.putString("generation", "not-a-uuid"); assertNull(ScrubberBank.decode(bank));
        bank = new ScrubberBank().encode(); bank.putInt("unknown", 1); assertNull(ScrubberBank.decode(bank));
        bank = new ScrubberBank().encode(); ListTag list = new ListTag();
        for (int i = 0; i < 7; i++) { list.add(new CompoundTag()); }
        bank.put("banks", list); assertNull(ScrubberBank.decode(bank));
        CompoundTag mode = new CompoundTag(); mode.putInt("schema_version", 1); mode.putString("mode", "HIGH_PRESSURE");
        assertEquals(DetectorMode.HIGH_PRESSURE, AtmosphereDetectorBlockEntity.decode(mode));
        mode.putString("mode", "LOW_OXYGEN"); assertNull(AtmosphereDetectorBlockEntity.decode(mode));
        mode.putString("mode", "VACUUM"); mode.putInt("schema_version", 2); assertNull(AtmosphereDetectorBlockEntity.decode(mode));
        assertNull(AtmosphereDetectorBlockEntity.decode(StringTag.valueOf("raw")));
    }
    @Test void unsupportedRawNbtHasABoundedNonrecursivePreflight() {
        CompoundTag deep = new CompoundTag(); CompoundTag cursor = deep;
        for (int i = 0; i < 18; i++) { CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child; }
        assertFalse(DeviceProtection.bounded(deep)); assertNull(ScrubberBank.decode(deep));
        CompoundTag oversized = new CompoundTag(); oversized.putByteArray("large", new byte[65_537]);
        assertFalse(DeviceProtection.bounded(oversized)); assertNull(ScrubberBank.decode(oversized));
    }
    @Test void directVentCodecPreflightsAnUncopyableFutureRootBeforeRecursiveCopy() {
        CompoundTag future = new CompoundTag(); future.putInt("schema_version", 3);
        CompoundTag cursor = future;
        for (int i = 0; i < 1_000; i++) { CompoundTag child = new CompoundTag(); cursor.put("child", child); cursor = child; }
        CompoundTag outer = new CompoundTag(); outer.put(OxygenVentPersistence.DATA_KEY, future);
        var decoded = OxygenVentPersistence.decode(outer);
        assertEquals(OxygenVentPersistence.DecodeStatus.INVALID, decoded.status());
        assertSame(future, decoded.preservedFutureData(), "Uncopyable original must remain retained, not recursively copied");
        assertEquals(3, future.getInt("schema_version"));
    }
}
