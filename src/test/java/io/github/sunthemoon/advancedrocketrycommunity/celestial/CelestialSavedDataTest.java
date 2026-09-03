package io.github.sunthemoon.advancedrocketrycommunity.celestial;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.SavedDataSchemaMigrator;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

class CelestialSavedDataTest {
    @Test
    void discoveryAndFirstVisitRoundTripDeterministically() {
        CelestialSavedData data = CelestialSavedData.create();

        assertEquals(
                CelestialSavedData.MutationResult.CHANGED,
                data.discover(ModIdentity.id("moon"), 20L)
        );
        assertEquals(
                CelestialSavedData.MutationResult.CHANGED,
                data.recordVisit(ModIdentity.id("moon"), 25L)
        );
        assertTrue(data.isDirty());

        CompoundTag encoded = data.save(new CompoundTag());
        CelestialSavedData decoded = CelestialSavedData.load(encoded);
        CompoundTag reencoded = decoded.save(new CompoundTag());

        assertEquals(encoded, reencoded);
        assertEquals(20L, decoded.get(ModIdentity.id("moon")).orElseThrow().discoveredAt());
        assertEquals(25L, decoded.get(ModIdentity.id("moon")).orElseThrow().firstVisitAt().orElseThrow());
        assertFalse(decoded.isDirty());
    }

    @Test
    void firstVisitIsImmutableAndAutomaticallyDiscoversBody() {
        CelestialSavedData data = CelestialSavedData.create();

        assertEquals(
                CelestialSavedData.MutationResult.CHANGED,
                data.recordVisit(ModIdentity.id("space"), 50L)
        );
        assertEquals(
                CelestialSavedData.MutationResult.UNCHANGED,
                data.recordVisit(ModIdentity.id("space"), 80L)
        );

        CelestialSavedData.BodyProgress progress = data.get(ModIdentity.id("space")).orElseThrow();
        assertEquals(50L, progress.discoveredAt());
        assertEquals(50L, progress.firstVisitAt().orElseThrow());
    }

    @Test
    void futureSchemaIsPreservedWithoutDowngradeAndRejectsMutation() {
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", CelestialSavedData.CURRENT_SCHEMA_VERSION + 5);
        future.putString("future_field", "preserve me");
        ListTag futureBodies = new ListTag();
        CompoundTag opaqueBody = new CompoundTag();
        opaqueBody.putInt("unknown_shape", 42);
        futureBodies.add(opaqueBody);
        future.put("bodies", futureBodies);

        CelestialSavedData data = CelestialSavedData.load(future);

        assertFalse(data.isWritableSchema());
        assertEquals(
                CelestialSavedData.MutationResult.UNSUPPORTED_SCHEMA,
                data.recordVisit(ModIdentity.id("moon"), 100L)
        );
        assertFalse(data.isDirty());
        assertEquals(future, data.save(new CompoundTag()));
    }

    @Test
    void duplicateAndMalformedEntriesAreRejected() {
        CompoundTag source = validSingleEntry();
        ListTag bodies = source.getList("bodies", CompoundTag.TAG_COMPOUND);
        bodies.add(bodies.getCompound(0).copy());

        assertThrows(IllegalArgumentException.class, () -> CelestialSavedData.load(source));

        CompoundTag badTime = validSingleEntry();
        badTime.getList("bodies", CompoundTag.TAG_COMPOUND)
                .getCompound(0)
                .putLong("first_visit_at", 1L);
        assertThrows(IllegalArgumentException.class, () -> CelestialSavedData.load(badTime));

        CompoundTag wrongListType = new CompoundTag();
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.CELESTIAL, wrongListType);
        ListTag strings = new ListTag();
        strings.add(net.minecraft.nbt.StringTag.valueOf("not a compound"));
        wrongListType.put("bodies", strings);
        assertThrows(IllegalArgumentException.class, () -> CelestialSavedData.load(wrongListType));
    }

    @Test
    void schemaAndCollectionBoundsAreEnforced() {
        CompoundTag missingSchema = new CompoundTag();
        assertThrows(IllegalArgumentException.class, () -> CelestialSavedData.load(missingSchema));

        CompoundTag oversized = new CompoundTag();
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.CELESTIAL, oversized);
        ListTag bodies = new ListTag();
        for (int index = 0; index <= 128; index++) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", ModIdentity.id("body_" + index).toString());
            entry.putLong("discovered_at", index);
            bodies.add(entry);
        }
        oversized.put("bodies", bodies);

        assertThrows(IllegalArgumentException.class, () -> CelestialSavedData.load(oversized));
    }

    private static CompoundTag validSingleEntry() {
        CompoundTag source = new CompoundTag();
        SavedDataSchemaMigrator.stampCurrent(ManagedSavedDataType.CELESTIAL, source);
        ListTag bodies = new ListTag();
        CompoundTag body = new CompoundTag();
        body.putString("id", ModIdentity.id("moon").toString());
        body.putLong("discovered_at", 10L);
        body.putLong("first_visit_at", 20L);
        bodies.add(body);
        source.put("bodies", bodies);
        return source;
    }
}
