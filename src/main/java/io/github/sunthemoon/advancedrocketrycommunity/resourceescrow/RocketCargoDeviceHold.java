package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** Read-only protection for cargo devices; never admits, captures, queues or mutates escrow. */
public final class RocketCargoDeviceHold {
    private RocketCargoDeviceHold() { }

    /** A fluid device, whose endpoint unit is {@code TANK}. */
    public static boolean locked(ServerLevel level, UUID device, UUID ledger, String type, BlockPos position) {
        return locked(level, device, ledger, type, position, EscrowEndpoint.Unit.TANK);
    }

    /** Any cargo device, by the unit its operations name ({@code CONTAINER} for the item devices). */
    public static boolean locked(ServerLevel level, UUID device, UUID ledger, String type, BlockPos position,
                                 EscrowEndpoint.Unit unit) {
        if (!level.getServer().isSameThread()) { return true; }
        var data = ResourceEscrowSavedData.existing(level.getServer());
        if (data == null) { return ledger != null; }
        if (ledger != null && !ledger.equals(data.ledger().id())) { return true; }
        var coordinator = data.bind(level.getServer());
        var key = new EscrowEndpoint.Key(EscrowEndpoint.Kind.BLOCK_ENTITY, level.dimension().location().toString(),
                device, null, type, position.getX(), position.getY(), position.getZ(), unit);
        return !coordinator.unresolvedNaming(device).isEmpty() || coordinator.frozenIdentity(device) || coordinator.locked(key);
    }
}
