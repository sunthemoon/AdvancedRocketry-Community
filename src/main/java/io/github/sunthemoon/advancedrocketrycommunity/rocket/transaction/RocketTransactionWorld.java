package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Server-world mutation boundary. Implementations must never load a chunk for these calls. */
public interface RocketTransactionWorld {
    ResourceLocation dimension();

    boolean isRegionLoaded(RocketRegion region);

    /** Checks restoration dependencies before mutation, without loading world chunks. */
    default boolean canRestoreSnapshot(RocketStructureSnapshot snapshot) {
        return true;
    }

    Optional<RocketWorldBlock> readBlock(RocketPosition absolutePosition);

    /** False means unchanged; uncertain partial mutation must throw with ROLLBACK_FAILED. */
    boolean removeBlockNoDrops(RocketPosition absolutePosition, RocketWorldBlock expected);

    /** False means no new block remains; failed cleanup must throw with ROLLBACK_FAILED. */
    boolean placeBlockIfEmpty(RocketPosition absolutePosition, RocketWorldBlock block);

    Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId);

    boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash);

    boolean removeRocket(UUID rocketId, UUID snapshotId);
}
