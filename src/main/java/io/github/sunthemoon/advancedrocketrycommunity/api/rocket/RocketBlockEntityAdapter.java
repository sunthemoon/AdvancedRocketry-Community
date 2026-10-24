package io.github.sunthemoon.advancedrocketrycommunity.api.rocket;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * A trusted, server-thread container integration. Available since API 1.1.
 * Callbacks must not load chunks or modify unrelated world resources. Each
 * callback is rejected if it returns after 5 ms; this is not preemption.
 * At sealed unseal, restore and recapture receive a host-created target with no
 * Level, on the owning server thread. A provider requiring a Level refuses restore;
 * the original sealed payload is retained. Failed unpublished targets are marked
 * removed: providers must refuse resource access through removed references.
 * Capability attachment, serialization and invalidation must not move resources.
 */
public interface RocketBlockEntityAdapter {
    /** Read-only source eligibility check, not permission to bypass host block tags. */
    boolean canMove(BlockEntity blockEntity);

    /** Return owned data only; root x/y/z/id fields are forbidden. Must not mutate state. */
    CompoundTag capture(BlockEntity blockEntity);

    /** Modify only the host-created target's owned data (possibly level-less); false requests checked cleanup. */
    boolean restore(BlockEntity blockEntity, CompoundTag data);
}
