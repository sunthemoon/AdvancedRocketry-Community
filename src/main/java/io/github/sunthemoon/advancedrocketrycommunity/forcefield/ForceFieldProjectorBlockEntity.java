package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.util.FakePlayer;

/** Minecraft bridge only; cleanup ownership and scheduling belong to SavedData. */
public final class ForceFieldProjectorBlockEntity extends ForceFieldNativeBlockEntity {
    private ProjectorState state;
    private int next = 1;
    public ForceFieldProjectorBlockEntity(BlockPos pos, BlockState blockState) {
        super(ModBlockEntities.FORCE_FIELD_PROJECTOR.get(), pos, blockState);
        state = new ProjectorState(UUID.randomUUID(), Optional.empty(), 1,
                blockState.getValue(ForceFieldProjectorBlock.FACING), 0);
    }
    @Override protected String rootName() { return ForceFieldCodecs.PROJECTOR_ROOT; }
    @Override protected boolean bounded(Tag raw) { return ForceFieldCodecs.projectorBounded(raw); }
    @Override protected void decode(Tag raw) { state = ForceFieldCodecs.projector(raw); }
    @Override protected CompoundTag encode() { return ForceFieldCodecs.projector(state); }
    @Override protected boolean validLocation() {
        return state.direction() == getBlockState().getValue(ForceFieldProjectorBlock.FACING);
    }
    public ProjectorState state() { return state; }
    public FieldIdentity identity() {
        return new FieldIdentity(state.id(), state.owner().orElseThrow(), state.generation(),
                level.dimension(), worldPosition, state.direction());
    }
    public boolean bind(LivingEntity player) {
        if (quarantined() || state.owner().isPresent() || !(player instanceof ServerPlayer connected)
                || player instanceof FakePlayer || connected.server.getPlayerList().getPlayer(connected.getUUID()) != connected
                || !connected.isAlive() || connected.isSpectator() || connected.level() != level) { return false; }
        ProjectorState candidate = state.bind(player.getUUID());
        FieldIdentity identity = new FieldIdentity(candidate.id(), player.getUUID(), candidate.generation(),
                level.dimension(), worldPosition, candidate.direction());
        if (!FieldCleanupSavedData.get(connected.server).reserve(identity, 0)) { return false; }
        state = candidate;
        setChanged();
        return true;
    }
    public void claims(int mask) { state = state.withClaims(mask); setChanged(); }
    int next() { return next; }
    void advance() { next = next == 32 ? 1 : next + 1; }
    @Override public void onLoad() {
        super.onLoad();
        if (!quarantined() && level instanceof ServerLevel server && state.owner().isPresent()) {
            FieldCleanupSavedData.get(server.getServer()).reserve(identity(), state.claims());
        }
    }
}
