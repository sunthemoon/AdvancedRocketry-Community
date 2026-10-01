package io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-056 section 4 as pure decisions: the payload selection, the minimum stack size bounds, the per-railgun cadence
 * and the refusal order of a launch. A refused launch changes nothing.
 */
public final class RailgunLaunch {
    public static final int CADENCE_TICKS = 20;
    public static final int MIN_STACK = 1;
    public static final int MAX_STACK = 64;

    private RailgunLaunch() {
    }

    /** The first input slot, in slot order, whose stack holds at least {@code minimum} items; -1 for none. */
    public static int payloadSlot(List<ItemStack> input, int minimum) {
        for (int slot = 0; slot < input.size(); slot++) {
            ItemStack stack = input.get(slot);
            if (!stack.isEmpty() && stack.getCount() >= minimum) {
                return slot;
            }
        }
        return -1;
    }

    public static boolean cadenceReady(long lastLaunch, long now) {
        return now - lastLaunch >= CADENCE_TICKS;
    }

    /** The minimum stack size after a change by {@code delta}, kept inside 1..64. */
    public static int minimumStack(int current, int delta) {
        return Math.max(MIN_STACK, Math.min(MAX_STACK, current + delta));
    }

    /**
     * What a launch attempt found, in the order it is checked. The per-railgun cadence and the server's per-tick cap
     * are not refusals: a launch waits for them (ADR-054 section 7).
     *
     * @param sourceState     the railgun's own state ({@code OK} when owned, registered here, not frozen, formed)
     * @param payloadSlot     the selected input slot, -1 for none
     * @param payloadFits     the selected stack encodes within the 512-byte payload bound
     * @param route           the route rule's result for the selected destination
     * @param targetDurable   the destination's registration is durable (ADR-054 section 11 step 1)
     * @param escrowBlocked   the source reconciliation just found a rollback (escrow waits a tick)
     * @param admission       the ledger's escrow admission (source registration durable, limits, root growth)
     */
    public record Facts(boolean operational, boolean enabled, boolean authorized, EndgameCode sourceState,
                        int payloadSlot, boolean payloadFits, EndgameCode route, boolean targetDurable,
                        boolean energy, boolean outboxFree, boolean escrowBlocked, EndgameCode admission) {
        public Facts {
            Objects.requireNonNull(sourceState, "sourceState");
            Objects.requireNonNull(route, "route");
            Objects.requireNonNull(admission, "admission");
        }
    }

    /**
     * The first refusal, or {@code OK}: the section 4 order ({@code SYSTEM_DISABLED}, authority, {@code NO_PAYLOAD},
     * the route codes, {@code INSUFFICIENT_ENERGY}, {@code OUTBOX_FULL}, {@code TRANSIT_LIMIT}, {@code ROOT_FULL}),
     * with the framework's own conditions placed where they are first known: the root before everything, the
     * railgun's own state after authority, the payload bound after the payload, the destination's durability after
     * the route, and a rollback before the outbox.
     */
    public static EndgameCode refusal(Facts facts) {
        if (!facts.operational()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        if (!facts.enabled()) {
            return EndgameCode.SYSTEM_DISABLED;
        }
        if (!facts.authorized()) {
            return EndgameCode.UNAUTHORIZED;
        }
        if (facts.sourceState() != EndgameCode.OK) {
            return facts.sourceState();
        }
        if (facts.payloadSlot() < 0) {
            return EndgameCode.NO_PAYLOAD;
        }
        if (!facts.payloadFits()) {
            return EndgameCode.PAYLOAD_TOO_LARGE;
        }
        if (facts.route() != EndgameCode.OK) {
            return facts.route();
        }
        if (!facts.targetDurable()) {
            return EndgameCode.AWAITING_WORLD_SAVE;
        }
        if (!facts.energy()) {
            return EndgameCode.INSUFFICIENT_ENERGY;
        }
        if (facts.escrowBlocked()) {
            return EndgameCode.ROOT_BUSY;
        }
        if (!facts.outboxFree()) {
            return EndgameCode.OUTBOX_FULL;
        }
        return facts.admission();
    }
}
