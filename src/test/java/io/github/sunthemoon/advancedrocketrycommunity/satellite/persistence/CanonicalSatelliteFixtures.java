package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteMissionRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** Raw on-disk fixtures shared by canonical-load, startup and native checks. */
public final class CanonicalSatelliteFixtures {
    private CanonicalSatelliteFixtures() { }

    public static CompoundTag recordRoot(int rawBytes, int count) {
        CompoundTag root = SatelliteMissionSavedData.create(0).save(new CompoundTag());
        root.putLong("save_epoch", 2);
        ListTag missions = new ListTag();
        for (int i = 0; i < count; i++) {
            CompoundTag mission = DecoderPreflightTest.rawMission(rawBytes, true);
            mission.putUUID("mission_id", new UUID(31, i));
            missions.add(mission);
        }
        root.put("missions", missions);
        return root;
    }

    public static CompoundTag legacy(CompoundTag current) {
        CompoundTag legacy = current.copy();
        legacy.putInt("schema_version", 3);
        legacy.putString("format_epoch", ManagedSavedDataType.SATELLITE_MISSIONS.formatEpoch(3));
        legacy.remove("ground_surveys");
        return legacy;
    }

    public static CompoundTag exactRoot(int excessAfterNormalization) {
        SatelliteMissionRegistry registry = SatelliteMissionRegistry.restore(0, 0, 2, CodecRecordSizer.INSTANCE);
        GroundExactPreflightTest.fillTo(registry, SatelliteLimits.MAX_REGISTRY_NBT_BYTES);
        CompoundTag root = SatelliteRegistryPayload.encodeCurrent(registry, new CompoundTag());
        if (excessAfterNormalization > 0) {
            CompoundTag first = root.getList("missions", Tag.TAG_COMPOUND).getCompound(0);
            String qualified = first.getString("definition_id");
            // Keep raw bytes constant; a bare spelling adds exactly ten bytes when encoded canonically.
            first.putString("definition_id", "d".repeat(qualified.length()));
        }
        return root;
    }
}
