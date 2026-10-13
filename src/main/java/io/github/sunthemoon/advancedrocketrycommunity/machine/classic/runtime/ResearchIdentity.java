package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Device;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Identity only. Home binding rejects an unloaded idle copy without a job-index lookup. */
public record ResearchIdentity(UUID device, Optional<UUID> owner, ResourceLocation homeLevel,
        long homePosition, boolean collision) {
    public static final int MAX_BYTES = 4_096;
    public ResearchIdentity {
        java.util.Objects.requireNonNull(device); java.util.Objects.requireNonNull(owner);
        if (homeLevel == null || homeLevel.toString().length() > 128) { throw new IllegalArgumentException("Device Level"); }
    }
    public Device home() { return new Device(device, homeLevel, homePosition); }
    public ResearchIdentity quarantined() { return new ResearchIdentity(device, owner, homeLevel, homePosition, true); }
    public static boolean bounded(Tag root) { return BoundedNbt.fits(root, MAX_BYTES, 8, 64); }
    public CompoundTag encode() {
        var root = new CompoundTag(); root.putInt("schema_version", 1); root.putUUID("device_id", device);
        owner.ifPresent(id -> root.putUUID("owner_id", id)); root.putString("home_level", homeLevel.toString());
        root.putLong("home_pos", homePosition); root.putBoolean("collision", collision); return root;
    }
    public static ResearchIdentity decode(Tag raw) {
        if (!bounded(raw) || !(raw instanceof CompoundTag root)) { throw new IllegalArgumentException("Device root bounds/type"); }
        var expected = new HashSet<>(Set.of("schema_version", "device_id", "home_level", "home_pos", "collision"));
        if (root.contains("owner_id")) { expected.add("owner_id"); }
        if (!root.getAllKeys().equals(expected) || !root.contains("schema_version", Tag.TAG_INT)
                || root.getInt("schema_version") != 1 || !root.hasUUID("device_id")
                || root.contains("owner_id") && !root.hasUUID("owner_id")
                || !root.contains("home_level", Tag.TAG_STRING) || root.getString("home_level").length() > 128
                || !root.contains("home_pos", Tag.TAG_LONG) || !root.contains("collision", Tag.TAG_BYTE)
                || root.getByte("collision") < 0 || root.getByte("collision") > 1) { throw new IllegalArgumentException("Device root schema/fields"); }
        return new ResearchIdentity(root.getUUID("device_id"), root.hasUUID("owner_id") ? Optional.of(root.getUUID("owner_id"))
                : Optional.empty(), new ResourceLocation(root.getString("home_level")), root.getLong("home_pos"), root.getBoolean("collision"));
    }
}
