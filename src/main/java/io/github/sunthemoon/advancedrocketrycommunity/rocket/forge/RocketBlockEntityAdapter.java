package io.github.sunthemoon.advancedrocketrycommunity.rocket.forge;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;

public interface RocketBlockEntityAdapter {
    ResourceLocation id();

    boolean supports(BlockEntity blockEntity);

    RocketBlockEntityPayload capture(BlockEntity blockEntity);

    boolean restore(BlockEntity blockEntity, RocketBlockEntityPayload payload);

    /** Internal R97 path; the caller owns the server thread and an unpublished target. */
    default RocketBlockEntityPayload captureDetached(Thread owner, BlockEntity target) {
        requireDetached(owner, target);
        return capture(target);
    }

    default boolean restoreDetached(Thread owner, BlockEntity target, RocketBlockEntityPayload payload) {
        requireDetached(owner, target);
        return restore(target, payload);
    }

    static void requireDetached(Thread owner, BlockEntity target) {
        if (Thread.currentThread() != owner || target.getLevel() != null || target.isRemoved()) {
            throw new IllegalStateException("Detached preparation requires the owning thread and a fresh target");
        }
    }

    default boolean canRestorePayload(RocketBlockEntityPayload payload) {
        return id().equals(payload.adapterId());
    }
}
