package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * ADR-051 section 7: the reconciliation table between the registry and one terminal's receipt for one mission.
 * Pure: it classifies both sides and names the action; the registry and the terminal apply it.
 */
public final class DeliveryReconciliation {
    /** The registry side, as seen from one terminal ("here"). */
    public enum RegistryView {
        UNAVAILABLE, ABSENT, ACTIVE_HERE, READY_HERE, ACTIVE_ELSEWHERE, READY_ELSEWHERE, CLAIMED_PAID_HERE,
        CLAIMED_ACK_PENDING, CLAIMED_ACK_DURABLE, CLAIMED_PAID_ELSEWHERE, CANCELLED, QUARANTINED
    }

    /** The receipt this terminal holds for the mission. */
    public enum ReceiptView { NONE, UNPERSISTED, PERSISTED }

    public enum Action {
        NONE_REGISTRY_BLOCKED, NONE, DROP_RECEIPT, SET_CLAIMED_PAID_HERE, SET_CLAIMED_PAID_HERE_BIND_BACK,
        REMATERIALIZE, WAIT, ACKNOWLEDGE, KEEP_AUDIT_DOUBLE_PAY, KEEP_AUDIT_PAID_THEN_CANCELLED, KEEP_MARK_RECEIPT_SEEN
    }

    private DeliveryReconciliation() {
    }

    /** Classifies a mission (empty when absent) for the terminal {@code here}. */
    public static RegistryView view(boolean operational, Optional<MissionState> mission, UUID here, long saveEpoch) {
        Objects.requireNonNull(here, "here");
        if (!operational) {
            return RegistryView.UNAVAILABLE;
        }
        if (mission.isEmpty()) {
            return RegistryView.ABSENT;
        }
        MissionState state = mission.get();
        if (!(state.payload() instanceof MissionPayload.Resource resource)) {
            return RegistryView.ABSENT;
        }
        boolean boundHere = resource.boundTerminal().equals(here);
        return switch (state.status()) {
            case ACTIVE -> boundHere ? RegistryView.ACTIVE_HERE : RegistryView.ACTIVE_ELSEWHERE;
            case READY -> boundHere ? RegistryView.READY_HERE : RegistryView.READY_ELSEWHERE;
            case CLAIMED -> {
                if (!resource.paidTerminal().filter(here::equals).isPresent()) {
                    yield RegistryView.CLAIMED_PAID_ELSEWHERE;
                }
                if (!resource.acknowledged()) {
                    yield RegistryView.CLAIMED_PAID_HERE;
                }
                yield resource.ackEpoch().orElseThrow() < saveEpoch
                        ? RegistryView.CLAIMED_ACK_DURABLE : RegistryView.CLAIMED_ACK_PENDING;
            }
            case CANCELLED -> RegistryView.CANCELLED;
            case QUARANTINED -> RegistryView.QUARANTINED;
            case CLAIM_PENDING_DISCOVERY -> RegistryView.ABSENT;
        };
    }

    /** The table of ADR-051 section 7, every registry state against every receipt state. */
    public static Action decide(RegistryView registry, ReceiptView receipt) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(receipt, "receipt");
        if (registry == RegistryView.UNAVAILABLE) {
            return Action.NONE_REGISTRY_BLOCKED;
        }
        boolean held = receipt != ReceiptView.NONE;
        return switch (registry) {
            case ABSENT -> held ? Action.DROP_RECEIPT : Action.NONE;
            case ACTIVE_HERE, READY_HERE -> held ? Action.SET_CLAIMED_PAID_HERE : Action.NONE;
            case ACTIVE_ELSEWHERE, READY_ELSEWHERE -> held ? Action.SET_CLAIMED_PAID_HERE_BIND_BACK : Action.NONE;
            case CLAIMED_PAID_HERE -> switch (receipt) {
                case NONE -> Action.REMATERIALIZE;
                case UNPERSISTED -> Action.WAIT;
                case PERSISTED -> Action.ACKNOWLEDGE;
            };
            case CLAIMED_ACK_PENDING -> held ? Action.WAIT : Action.NONE;
            case CLAIMED_ACK_DURABLE -> held ? Action.DROP_RECEIPT : Action.NONE;
            case CLAIMED_PAID_ELSEWHERE -> held ? Action.KEEP_AUDIT_DOUBLE_PAY : Action.NONE;
            case CANCELLED -> held ? Action.KEEP_AUDIT_PAID_THEN_CANCELLED : Action.NONE;
            case QUARANTINED -> held ? Action.KEEP_MARK_RECEIPT_SEEN : Action.NONE;
            case UNAVAILABLE -> Action.NONE_REGISTRY_BLOCKED;
        };
    }
}
