package io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlockEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;

/** Opaque, non-ticking storage, including before the server's journals and adapters become available. */
public final class SealedRocketBlockEntity extends BlockEntity {
    private SealedRocketRoot root = SealedRocketRoot.absent();
    private boolean initialized;
    private boolean warned;
    private long nextUnsealAttempt = Long.MIN_VALUE;

    public SealedRocketBlockEntity(BlockPos pos, BlockState state) { super(ModBlockEntities.SEALED_ROCKET_BLOCK.get(), pos, state); }

    /** Only a fresh detached carrier can receive a staging root; loaded/installed members cannot be rewritten. */
    public void initialize(SealedRocketRoot staged) {
        if (initialized || hasLevel() || isRemoved() || staged.member().isEmpty()
                || !staged.member().orElseThrow().position().equals(
                        new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(
                                worldPosition.getX(), worldPosition.getY(), worldPosition.getZ()))) {
            throw new IllegalStateException("Sealed carrier initialization requires a fresh detached matching member");
        }
        root = staged; initialized = true;
    }

    public Optional<SealedRocketMember> member() { return root.member(); }
    /** Ephemeral retry scheduling only; no authority or payload state is changed or persisted. */
    public boolean unsealAttemptDue(long tick) { return tick >= nextUnsealAttempt; }
    public void deferUnsealUntil(long tick) { nextUnsealAttempt = Math.max(nextUnsealAttempt, tick); }
    @Override public boolean onlyOpCanSetNbt() { return true; }
    @Override public <T> LazyOptional<T> getCapability(Capability<T> capability, Direction side) { return LazyOptional.empty(); }
    @Override public CompoundTag getUpdateTag() { return new CompoundTag(); }

    @Override public void load(CompoundTag tag) {
        if (level instanceof ServerLevel server) { RocketRuntime.sealedTerrainChanged(server, worldPosition); }
        super.load(tag);
        root = SealedRocketRoot.load(tag); initialized = true; warned = false; nextUnsealAttempt = Long.MIN_VALUE;
        if (level instanceof ServerLevel server && !available(server)) { warnHeld(); }
    }
    @Override protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        root.save(tag);
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) { RocketRuntime.sealedTerrainChanged(server, worldPosition); }
        if (initialized && level instanceof ServerLevel server && !available(server)) { warnHeld(); }
        // A bounded hint neither asks for journal authority nor constructs/publishes a native target.
        if (initialized && level instanceof ServerLevel server) { RocketRuntime.sealedHint(server, worldPosition); }
    }

    /** Pure availability checks only. Adapter callbacks and authority checks belong to the coordinator. */
    public boolean available(ServerLevel server) {
        var member = root.member().orElse(null);
        if (member == null || !member.dimension().equals(server.dimension().location())
                || member.position().x() != worldPosition.getX() || member.position().y() != worldPosition.getY()
                || member.position().z() != worldPosition.getZ()) { return false; }
        var state = RocketBlockStateAdapter.restore(member.state()).orElse(null);
        return state != null && !state.isAir() && !(state.getBlock() instanceof SealedRocketBlock)
                && state.hasBlockEntity() == (member.payload() != null)
                && (member.payload() == null || RocketRuntime.sealedPayloadKnown(server, member.payload()));
    }

    /** One bounded diagnostic per load, never from saving; no tag contents enter the log. */
    public void warnHeld() {
        if (!warned) {
            warned = true;
            LogUtils.getLogger().warn("ARCE_SEALED_MEMBER_HELD position={} reason=unavailable-member repair=offline-whole-world",
                    worldPosition.asLong());
        }
    }
}
