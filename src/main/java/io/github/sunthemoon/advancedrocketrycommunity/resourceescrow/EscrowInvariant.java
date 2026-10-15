package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import net.minecraft.nbt.CompoundTag;

/** Immutable identity/snapshot projection; boarding, position and ordinary flight clocks are separate checks. */
final class EscrowInvariant {
    private EscrowInvariant() { }
    static String rocket(RocketEntity rocket) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 1);
        tag.putUUID("physical", rocket.getUUID()); tag.putString("type", EscrowAdapterIds.ROCKET);
        tag.putUUID("logical", rocket.assemblyTransactionId().orElseThrow()); tag.putUUID("owner", rocket.ownerId().orElseThrow());
        tag.putUUID("snapshot", rocket.snapshot().orElseThrow().snapshotId());
        tag.putString("snapshot_hash", rocket.snapshot().orElseThrow().contentHash());
        return RocketNativeResources.signature(tag);
    }
    static String rocket(EscrowEndpoint endpoint, java.util.UUID owner,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot snapshot) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 1);
        tag.putUUID("physical", endpoint.identity()); tag.putString("type", endpoint.type());
        tag.putUUID("logical", endpoint.logicalRocket()); tag.putUUID("owner", owner);
        tag.putUUID("snapshot", snapshot.snapshotId()); tag.putString("snapshot_hash", snapshot.contentHash());
        return RocketNativeResources.signature(tag);
    }
    static String reference(EscrowEndpoint endpoint) {
        var tag = new CompoundTag(); tag.putInt("schema_version", 1); tag.putString("kind", endpoint.kind().name());
        tag.putString("level", endpoint.level()); tag.putString("type", endpoint.type()); tag.putUUID("physical", endpoint.identity());
        if (endpoint.logicalRocket() != null) { tag.putUUID("logical", endpoint.logicalRocket()); }
        tag.putInt("x", endpoint.x()); tag.putInt("y", endpoint.y()); tag.putInt("z", endpoint.z());
        return RocketNativeResources.signature(tag);
    }
}
