package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.RocketEscrowGate;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import java.util.Objects;
import java.util.UUID;

/**
 * W170 D5's one-use entity permit (W27A-DESIGN section 8, permit scope): the bay coordinator replaces the exact source's
 * snapshot S with its consumed version S'. Only the coordinator constructs one, for one entity, one logical rocket and
 * owner, and the exact snapshot object it observed; it changes nothing else (flight data, wrapper, position). The
 * escrow-only protections still refuse it: a bay permit bypasses only the bay part of the lock.
 */
public final class BaySourceConsumption {
    private final UUID entity, logical, owner;
    private final RocketStructureSnapshot before, after;
    private boolean used;

    BaySourceConsumption(UUID entity, UUID logical, UUID owner, RocketStructureSnapshot before, RocketStructureSnapshot after) {
        this.entity = Objects.requireNonNull(entity); this.logical = Objects.requireNonNull(logical);
        this.owner = Objects.requireNonNull(owner); this.before = Objects.requireNonNull(before); this.after = Objects.requireNonNull(after);
        if (!before.snapshotId().equals(after.snapshotId()) || before.contentHash().equals(after.contentHash())) {
            throw new IllegalArgumentException("A bay consumption keeps the snapshot ID and changes its content");
        }
    }

    /** RocketEntity only: the consumed snapshot, once, for exactly the bound entity in its observed state. */
    public RocketStructureSnapshot consume(RocketEntity rocket) {
        if (used || rocket.level().isClientSide || !rocket.getUUID().equals(entity)
                || rocket.assemblyTransactionId().filter(logical::equals).isEmpty() || rocket.ownerId().filter(owner::equals).isEmpty()
                || rocket.snapshot().orElse(null) != before || !RocketEscrowGate.escrowIdle(rocket)) {
            throw new IllegalStateException("Bay consumption permit does not match the source");
        }
        used = true;
        return after;
    }
}
