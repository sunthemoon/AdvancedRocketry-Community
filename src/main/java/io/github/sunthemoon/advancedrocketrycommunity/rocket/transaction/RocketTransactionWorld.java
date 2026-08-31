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

    Optional<RocketWorldBlock> readBlock(RocketPosition absolutePosition);

    boolean removeBlockNoDrops(RocketPosition absolutePosition, RocketWorldBlock expected);

    boolean placeBlockIfEmpty(RocketPosition absolutePosition, RocketWorldBlock block);

    Optional<UUID> spawnRocket(RocketStructureSnapshot snapshot, UUID transactionId);

    boolean rocketMatches(UUID rocketId, UUID snapshotId, String contentHash);

    boolean removeRocket(UUID rocketId, UUID snapshotId);
}
