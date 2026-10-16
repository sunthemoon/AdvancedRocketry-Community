package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LandingPadIndexTest {
    @Test void terminalTransferReleaseIsExactIdempotentAndSurvivesReload() {
        var index = new LandingPadIndex(); var first = register(index, 1, "First"); var other = register(index, 2, "Other");
        var transfer = UUID.randomUUID(); var retained = UUID.randomUUID();
        assertTrue(index.claim(STATION, first.reference(), first.physicalId(), transfer));
        assertTrue(index.claim(STATION, other.reference(), other.physicalId(), retained));
        index = LandingPadIndex.load(index.save(new CompoundTag()));
        assertTrue(index.releaseTransfer(transfer)); assertTrue(index.releaseTransfer(transfer));
        assertNull(index.find(STATION, first.reference()).orElseThrow().claim());
        assertEquals(retained, index.find(STATION, other.reference()).orElseThrow().claim());
        var invalid = index.save(new CompoundTag()); invalid.putInt("schema", 999);
        var blocked = LandingPadIndex.load(invalid); assertFalse(blocked.releaseTransfer(transfer));
        assertEquals(invalid, blocked.save(new CompoundTag()));
    }
    private static final UUID STATION = new UUID(1, 1);
    private static final UUID OWNER = new UUID(2, 2);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static LandingPadEntry register(LandingPadIndex index, int x, String name) {
        return index.register(STATION, OWNER, UUID.randomUUID(), name, new StationPosition(x, 80, 0));
    }

    @Test void eightPadsAndThirtyTwoCharactersAreHardLimits() {
        var index = new LandingPadIndex();
        for (int i = 0; i < 8; i++) { register(index, i, "p".repeat(32)); }
        assertThrows(IllegalArgumentException.class, () -> register(index, 8, "ninth"));
        assertEquals(8, index.entries(STATION).size());
        for (var name : List.of("", " ", " pad", "pad ", "x\n", "x".repeat(33))) {
            assertThrows(IllegalArgumentException.class, () -> register(new LandingPadIndex(), 0, name));
        }
    }
    @Test void namesClaimsAndEpochsRoundTripCanonically() {
        var index = new LandingPadIndex(); var entry = register(index, 0, "Dock A"); var transfer = UUID.randomUUID();
        assertTrue(index.claim(STATION, entry.reference(), entry.physicalId(), transfer));
        var root = index.save(new CompoundTag()); var restored = LandingPadIndex.load(root);
        assertTrue(restored.operational()); assertEquals(root, restored.save(new CompoundTag()));
        assertEquals(transfer, restored.find(STATION, entry.reference()).orElseThrow().claim());
        assertTrue(restored.release(STATION, entry.reference(), transfer));
        assertNull(restored.find(STATION, entry.reference()).orElseThrow().claim());
    }
    @Test void removedOrReplacedPadsNeverInheritClaims() {
        var index = new LandingPadIndex(); var old = register(index, 0, "Old"); var transfer = UUID.randomUUID();
        assertTrue(index.claim(STATION, old.reference(), old.physicalId(), transfer));
        assertFalse(index.remove(STATION, old.reference(), UUID.randomUUID()));
        assertTrue(index.remove(STATION, old.reference(), old.physicalId()));
        var replacement = register(index, 0, "New");
        assertNotEquals(old.padId(), replacement.padId()); assertTrue(replacement.epoch() > old.epoch());
        assertFalse(index.claim(STATION, old.reference(), old.physicalId(), transfer));
        assertFalse(index.release(STATION, old.reference(), transfer)); assertNull(replacement.claim());
    }
    @Test void wrongStationEpochPhysicalIdentityAndCompetingTransfersFailClosed() {
        var index = new LandingPadIndex(); var entry = register(index, 0, "Dock"); var a = UUID.randomUUID(); var b = UUID.randomUUID();
        assertTrue(index.find(UUID.randomUUID(), entry.reference()).isEmpty());
        assertTrue(index.find(STATION, new LandingPadReference(entry.padId(), entry.epoch() + 1)).isEmpty());
        assertFalse(index.claim(STATION, entry.reference(), UUID.randomUUID(), a));
        assertTrue(index.claim(STATION, entry.reference(), entry.physicalId(), a));
        assertTrue(index.claim(STATION, entry.reference(), entry.physicalId(), a));
        assertFalse(index.claim(STATION, entry.reference(), entry.physicalId(), b));
        assertFalse(index.release(STATION, entry.reference(), b));
        assertTrue(index.release(STATION, entry.reference(), a));
    }
    @Test void codecRejectsDuplicateEntriesWrongTypesAndFutureRootsWithoutNormalization() {
        var index = new LandingPadIndex(); register(index, 0, "Dock");
        var original = index.save(new CompoundTag());
        var duplicate = original.copy(); duplicate.getList("pads", 10).add(duplicate.getList("pads", 10).getCompound(0).copy());
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(duplicate));
        var future = original.copy(); future.putInt("schema", 2);
        var blocked = LandingPadIndex.load(future); assertFalse(blocked.operational()); assertEquals(future, blocked.save(new CompoundTag()));
        var wrongType = original.copy(); wrongType.getList("pads", 10).getCompound(0).putString("epoch", "0");
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(wrongType));
        var stale = original.copy(); stale.putLong("next_epoch", 0);
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(stale));
        var unknown = original.copy(); unknown.putInt("extra", 1);
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(unknown));
    }
    @Test void overTwoMiBAndDeepRootsArePreflightedBeforeCopy() {
        CompoundTag oversized = new CompoundTag(); oversized.putByteArray("huge", new byte[LandingPadIndexCodec.MAX_BYTES]);
        assertFalse(LandingPadIndexCodec.fits(oversized));
        assertSame(oversized, LandingPadIndex.load(oversized).save(new CompoundTag()));
        CompoundTag deep = new CompoundTag(); CompoundTag cursor = deep;
        for (int i = 0; i < 32; i++) { CompoundTag next = new CompoundTag(); cursor.put("deep", next); cursor = next; }
        assertFalse(LandingPadIndexCodec.fits(deep)); assertSame(deep, LandingPadIndex.load(deep).save(new CompoundTag()));
    }
    @Test void codecRejectsNineRecordsPerStationAndUnboundedStationCount() {
        var entries = new java.util.ArrayList<LandingPadEntry>();
        for (int i = 0; i < 9; i++) { entries.add(new LandingPadEntry(STATION, UUID.randomUUID(), UUID.randomUUID(), OWNER,
                "Dock", new StationPosition(i, 80, 0), i, null)); }
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(LandingPadIndexCodec.encode(9, entries, false)));
        entries.clear();
        for (int i = 0; i <= LandingPadIndexCodec.MAX_STATIONS; i++) {
            entries.add(new LandingPadEntry(new UUID(0, i), UUID.randomUUID(), UUID.randomUUID(), OWNER,
                    "Dock", new StationPosition(0, 80, 0), i, null));
        }
        assertThrows(IllegalArgumentException.class, () -> LandingPadIndexCodec.decode(LandingPadIndexCodec.encode(5000, entries, false)));
    }
    @Test void oversizedIndexNeverOverwritesExistingFile(@TempDir java.nio.file.Path directory) throws Exception {
        var file = directory.resolve("pads.dat"); byte[] before = {1, 2, 3}; java.nio.file.Files.write(file, before);
        var root = new CompoundTag(); root.putByteArray("huge", new byte[LandingPadIndexCodec.MAX_BYTES]);
        var data = LandingPadIndex.load(root); data.setDirty(); data.save(file.toFile());
        assertArrayEquals(before, java.nio.file.Files.readAllBytes(file)); assertTrue(data.isDirty());
    }
    @Test void actorMutationRateIsTenTicksAndLifecycleBounded() {
        var index = new LandingPadIndex(); var actor = UUID.randomUUID();
        assertTrue(index.allowInteraction(actor, 0)); assertFalse(index.allowInteraction(actor, 9)); assertTrue(index.allowInteraction(actor, 10));
        for (int i = 0; i < 127; i++) { assertTrue(index.allowInteraction(new UUID(7, i), 10)); }
        assertFalse(index.allowInteraction(UUID.randomUUID(), 10)); assertTrue(index.allowInteraction(UUID.randomUUID(), 20));
    }
    @Test void perPlayerPadSelectionIsBoundedTransientAndClearedOnLogout() {
        var index = new LandingPadIndex(); var actor = UUID.randomUUID(); var station = UUID.randomUUID();
        var pad = new LandingPadReference(UUID.randomUUID(), 0);
        assertTrue(index.choose(actor, station, pad)); assertEquals(pad, index.choice(actor).landingPad());
        assertNull(LandingPadIndex.load(index.save(new CompoundTag())).choice(actor).landingPad());
        index.forgetChoice(actor); assertNull(index.choice(actor).landingPad());
        for (int i = 0; i < 128; i++) { assertTrue(index.choose(new UUID(3, i), station, pad)); }
        assertFalse(index.choose(UUID.randomUUID(), station, pad));
    }
}
