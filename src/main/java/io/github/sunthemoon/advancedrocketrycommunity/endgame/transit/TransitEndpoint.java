package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.world.item.ItemStack;

/**
 * A loaded ledger endpoint (railgun, elevator anchor or terminal) as the ledger's END-tick pass sees it (ADR-054
 * section 7 "where work runs"): its identity, its transit state, its receive buffer, and a way to mark its block entity
 * changed. A frozen endpoint (retired, or quarantined) runs none of the reconciliation rows.
 */
public interface TransitEndpoint {
    UUID endpointId();

    Optional<UUID> endpointOwner();

    /** Retired or quarantined: its contents are frozen and only a resolve or a removal settles them. */
    boolean transitFrozen();

    TransitSourceState source();

    TransitDestinationState destination();

    TransitDestinationState.ReceiveBuffer receiveBuffer();

    /** The block entity's {@code setChanged()}, after any change to its transit state. */
    void transitChanged();

    /** A resolve returns a never-registered outbox payload to the input buffer; false when it does not all fit. */
    boolean returnToInput(List<ItemStack> stacks);

    /**
     * Whether its chunk is FULL. Minecraft marks only a FULL chunk unsaved when a block entity changes, and a chunk
     * near forced chunks stays in memory below FULL after its own tickets went, its block entities still attached; the
     * ledger leaves such an endpoint alone until its chunk is FULL again or unloads (found by the C13 native harness).
     */
    default boolean transitAccessible() {
        return true;
    }
}
