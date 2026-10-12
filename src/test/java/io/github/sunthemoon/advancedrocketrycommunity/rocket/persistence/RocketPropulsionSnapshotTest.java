package io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import java.util.*;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class RocketPropulsionSnapshotTest {
    private static RocketStructureSnapshot snapshot(RocketPropulsion descriptor) {
        var block = new RocketBlock(new RocketPosition(0, 0, 0), new RocketBlockState(ModIdentity.id("rocket_motor"), Map.of()), null);
        return RocketStructureSnapshot.create(UUID.randomUUID(), ModIdentity.id("earth"), new RocketPosition(0, 64, 0),
                List.of(block), List.of(), new RocketStats(1, 100, 1_000, 1_000, 1, 0, 0, 0), 10, descriptor);
    }
    @Test void newSchemaAndIndependentDescriptorRoundTripAndRelocationKeepCapturedSettings() {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 3, 5,
                RocketPropulsion.Settings.capture(1.3333, 0.1, 4, false, true, false));
        var snapshot = snapshot(descriptor); assertEquals(2, snapshot.schemaVersion());
        var root = RocketSnapshotNbtCodec.encode(snapshot);
        var decoded = RocketSnapshotNbtCodec.decode(root); assertEquals(RocketSnapshotDecodeResult.Status.VALID, decoded.status());
        assertEquals(root, RocketSnapshotNbtCodec.encode(decoded.snapshot().orElseThrow()));
        var relocated = decoded.snapshot().orElseThrow().relocated(UUID.randomUUID(), ModIdentity.id("moon"), new RocketPosition(32, 70, 32), 20);
        assertEquals(descriptor, relocated.propulsion().orElseThrow()); assertNotEquals(snapshot.contentHash(), relocated.contentHash());
    }
    @Test void oldSchemaKeepsItsOriginalHashAndNeverGetsFluidEligibility() {
        var old = snapshot(null); var root = RocketSnapshotNbtCodec.encode(old);
        assertEquals(1, root.getInt("schema_version")); assertFalse(root.contains("propulsion"));
        var decoded = RocketSnapshotNbtCodec.decode(root).snapshot().orElseThrow();
        assertEquals(old.contentHash(), decoded.contentHash()); assertTrue(decoded.propulsion().isEmpty());
        assertEquals(root, RocketSnapshotNbtCodec.encode(decoded));
    }
    @Test void descriptorsAreHashBoundAndUnknownSchemasArePreserved() {
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS);
        var root = RocketSnapshotNbtCodec.encode(snapshot(descriptor));
        root.getCompound("propulsion").putBoolean("required", false);
        assertEquals(io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode.HASH_MISMATCH,
                RocketSnapshotNbtCodec.decode(root).code());
        root.getCompound("propulsion").putInt("schema", 99);
        assertEquals(root, RocketSnapshotNbtCodec.decode(root).preservedPayload().orElseThrow());
        assertTrue(RocketSnapshotNbtCodec.decode(root).snapshot().isEmpty());
    }
    @Test void oversizedSnapshotRootIsNeverCopiedOrDecodedAndRemainsTheSameReference() {
        var root = new CompoundTag(); root.putByteArray("opaque", new byte[RocketLimits.MAX_TOTAL_NBT_BYTES + 1]);
        var result = RocketSnapshotNbtCodec.decode(root); assertTrue(result.snapshot().isEmpty());
        assertSame(root, result.preservedPayload().orElseThrow());
    }
    @Test void assemblyCapturesAllFuelListsWithinTheIndependentDescriptorBudget() {
        var allowed = new EnumMap<RocketPropulsion.Role, List<String>>(RocketPropulsion.Role.class);
        for (var role : RocketPropulsion.Role.values()) {
            var ids = new ArrayList<String>();
            for (int i = 0; i < 16; i++) { ids.add("test:" + "a".repeat(118) + String.format(Locale.ROOT, "%05d", role.ordinal() * 16 + i)); }
            allowed.put(role, ids);
        }
        var settings = RocketPropulsion.Settings.DEFAULTS.withAllowedFluids(allowed);
        allowed.clear();
        var descriptor = new RocketPropulsion(RocketPropulsion.Mode.MONO, 1_000, 0, 1, 1, settings);
        assertEquals(descriptor, RocketPropulsionCodec.decode(RocketPropulsionCodec.encode(descriptor)));
        assertEquals(64, settings.allowedFluids().values().stream().mapToInt(List::size).sum());
        assertNotEquals(descriptor.signature(), new RocketPropulsion(RocketPropulsion.Mode.MONO,
                1_000, 0, 1, 1, RocketPropulsion.Settings.DEFAULTS).signature());
    }
}
