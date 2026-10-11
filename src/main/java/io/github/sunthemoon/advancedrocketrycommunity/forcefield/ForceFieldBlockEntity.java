package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

public final class ForceFieldBlockEntity extends ForceFieldNativeBlockEntity {
    private FieldIdentity identity;
    public ForceFieldBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.FORCE_FIELD.get(), pos, state); }
    @Override protected String rootName() { return ForceFieldCodecs.FIELD_ROOT; }
    @Override protected boolean bounded(Tag raw) { return ForceFieldCodecs.fieldBounded(raw); }
    @Override protected void decode(Tag raw) { identity = ForceFieldCodecs.field(raw); }
    @Override protected CompoundTag encode() { return identity == null ? new CompoundTag() : ForceFieldCodecs.field(identity); }
    public FieldIdentity identity() { return quarantined() ? null : identity; }
    public void initialize(FieldIdentity value) {
        if (quarantined() || identity != null || value.offset(worldPosition) == 0 || !value.level().equals(level.dimension())) {
            throw new IllegalStateException("Cannot assign a force-field claim");
        }
        identity = value;
        setChanged();
        queue();
    }
    @Override public void onLoad() { super.onLoad(); queue(); }
    private void queue() {
        if (identity() != null && level instanceof ServerLevel server) {
            FieldCleanupSavedData.get(server.getServer()).observe(identity, worldPosition);
        }
    }
}
