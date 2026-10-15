package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Unsupported native data is never normalized; oversized data is retained by reference. */
public abstract class ForceFieldNativeBlockEntity extends BlockEntity {
    private Tag refused;
    protected ForceFieldNativeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }
    protected abstract String rootName();
    protected abstract boolean bounded(Tag root);
    protected abstract void decode(Tag root);
    protected abstract CompoundTag encode();
    protected boolean validLocation() { return true; }
    @Override public final boolean onlyOpCanSetNbt() { return true; }
    public final boolean quarantined() { return refused != null; }
    public final boolean oversized() { return refused != null && !bounded(refused); }
    @Override public final void load(CompoundTag tag) {
        Runnable replaced = replacing();
        super.load(tag);
        Tag raw = tag.get(rootName());
        refused = null;
        try {
            if (raw == null || !bounded(raw)) { throw ForceFieldCodecs.invalid(); }
            decode(raw);
            if (!validLocation()) { throw ForceFieldCodecs.invalid(); }
        } catch (IllegalArgumentException failure) {
            // A missing owned root is preserved as an explicit invalid root, not a new identity.
            refused = raw == null ? new CompoundTag() : bounded(raw) ? raw.copy() : raw;
        }
        observeSaveGuard();
        replaced.run();
    }
    /** Runs before load replaces the root; the returned step runs after it. A chunk load from disk has no Level yet. */
    protected Runnable replacing() { return () -> { }; }
    @Override protected final void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(rootName(), refused == null ? encode() : bounded(refused) ? refused.copy() : refused);
        observeSaveGuard();
    }
    @Override public void onLoad() {
        super.onLoad();
        if (!quarantined() && !validLocation()) { refused = encode(); }
        observeSaveGuard();
    }
    private void observeSaveGuard() {
        if (oversized() && level instanceof ServerLevel server) {
            GuardedChunkSaves.recordObservedDenial(server, new ChunkPos(worldPosition),
                    "Oversized force-field root; back up and repair before restarting");
        }
    }
}
