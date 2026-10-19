package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only protection for C-A devices; never admits, captures, queues or mutates escrow. */
public final class RocketCargoDeviceHold {
    private RocketCargoDeviceHold() { }

    public static boolean locked(ServerLevel level, UUID device, UUID ledger, String type, BlockPos position) {
        if (!level.getServer().isSameThread()) { return true; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return ledger != null; }
        if (ledger != null && !ledger.equals(data.ledger().id())) { return true; }
        var coordinator = data.bind(level.getServer());
        var key = new EscrowEndpoint.Key(EscrowEndpoint.Kind.BLOCK_ENTITY, level.dimension().location().toString(),
                device, null, type, position.getX(), position.getY(), position.getZ(), EscrowEndpoint.Unit.TANK);
        return !coordinator.unresolvedNaming(device).isEmpty() || coordinator.frozenIdentity(device) || coordinator.locked(key);
    }
}
