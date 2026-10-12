package io.github.sunthemoon.advancedrocketrycommunity.station.pad;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Physical identity survives a restart but never follows loot or a replacement block. */
public final class LandingPadBlockEntity extends BlockEntity {
    private UUID physicalId;
    private UUID stationId;
    private LandingPadReference reference;
    private Tag preserved;

    public LandingPadBlockEntity(BlockPos position, BlockState state) {
        super(ModBlockEntities.LANDING_PAD.get(), position, state);
    }
    public boolean operational() { return preserved == null; }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public void onLoad() { super.onLoad(); observeSaveGuard(); }
    private void observeSaveGuard() {
        if (preserved != null && !BoundedNbt.fits(preserved, 512, 3, 16)
                && level instanceof net.minecraft.server.level.ServerLevel server) {
            io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves.recordObservedDenial(
                    server, new net.minecraft.world.level.ChunkPos(worldPosition),
                    "Oversized landing-pad root; back up and repair before restarting");
        }
    }
    public UUID physicalId() { return physicalId; }
    public UUID stationId() { return stationId; }
    public LandingPadReference reference() { return reference; }
    public UUID ensurePhysicalId() {
        if (level == null || level.isClientSide || !operational()) { throw new IllegalStateException("Server pad required"); }
        if (physicalId == null) { physicalId = UUID.randomUUID(); setChanged(); }
        return physicalId;
    }
    public void bind(LandingPadEntry entry) {
        if (!entry.physicalId().equals(physicalId)) { throw new IllegalArgumentException("Wrong physical pad"); }
        stationId = entry.stationId(); reference = entry.reference(); setChanged();
    }
    public void unbind() { stationId = null; reference = null; setChanged(); }

    @Override public void load(CompoundTag tag) {
        super.load(tag); physicalId = null; stationId = null; reference = null; preserved = null;
        if (!tag.contains("landing_pad")) { return; }
        Tag raw = tag.get("landing_pad");
        if (!(raw instanceof CompoundTag root)) { preserved = raw; return; }
        try {
            if (!BoundedNbt.fits(root, 512, 3, 16) || !root.contains("schema", Tag.TAG_INT)
                    || root.getInt("schema") != 1 || !root.hasUUID("physical")
                    || !Set.of("schema", "physical", "station", "pad", "epoch").containsAll(root.getAllKeys())) {
                throw new IllegalArgumentException("Unsupported pad identity");
            }
            boolean bound = root.contains("station");
            if (root.size() != (bound ? 5 : 2) || bound && (!root.hasUUID("station") || !root.hasUUID("pad")
                    || !root.contains("epoch", Tag.TAG_LONG))) { throw new IllegalArgumentException("Incomplete pad identity"); }
            physicalId = root.getUUID("physical");
            if (bound) { stationId = root.getUUID("station"); reference = new LandingPadReference(root.getUUID("pad"), root.getLong("epoch")); }
        } catch (IllegalArgumentException invalid) { preserved = root; }
        observeSaveGuard();
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        observeSaveGuard();
        if (preserved != null) {
            tag.put("landing_pad", preserved);
        } else if (physicalId != null) {
            CompoundTag root = new CompoundTag(); root.putInt("schema", 1); root.putUUID("physical", physicalId);
            if (reference != null) {
                root.putUUID("station", stationId); root.putUUID("pad", reference.padId()); root.putLong("epoch", reference.epoch());
            }
            tag.put("landing_pad", root);
        }
    }
}
