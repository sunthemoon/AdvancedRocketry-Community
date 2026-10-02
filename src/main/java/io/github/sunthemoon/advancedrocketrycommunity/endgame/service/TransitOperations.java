package io.github.sunthemoon.advancedrocketrycommunity.endgame.service;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameRoot;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndpointRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.OutboxEntry;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitEndpoint;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitPayload;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRules;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.world.item.ItemStack;

/**
 * ADR-054 sections 9 and 11 operator and owner actions on the ledger, each one audited barrier flush with a SHA-256
 * prefix of the payload: redirect (only once the destination's index removal is durable, and only to a target that
 * passes the system's route rule), purge, resettle after a lost settlement write, and resolve of a retired endpoint's
 * frozen contents (or of the conflicts an operator restore left in a registered one). Player-triggered barriers share
 * the section 7 spacing of 20 ticks.
 */
public final class TransitOperations {
    private static final String SYSTEM = "endgame";

    /** A system's route rule for a redirect target (ADR-056 section 3, ADR-059 section 6). */
    @FunctionalInterface
    public interface Route {
        /** {@code OK}, or the refusal for this target. The caller has checked the target is an ACTIVE endpoint. */
        EndgameCode check(EndgameRoot root, TransitRecord record, EndpointRecord target, boolean operator);

        /** The station whose 100-tick cooldown an owner's redirect to this target enters (section 7); none here. */
        default Optional<UUID> station(EndgameRoot root, EndpointRecord target) {
            return Optional.empty();
        }
    }

    /** What a resolve did, item by item. */
    public record Resolved(int returned, int moved, int destroyed, int receipts, int left) {
        public boolean anything() {
            return returned + moved + destroyed + receipts > 0;
        }
    }

    private final EndgameService service;
    private final Map<EndgameSystem, Route> routes = new EnumMap<>(EndgameSystem.class);

    TransitOperations(EndgameService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    /** Railgun and elevator register their route rules; a system without one refuses every redirect. */
    public void route(EndgameSystem system, Route route) {
        routes.put(Objects.requireNonNull(system, "system"), Objects.requireNonNull(route, "route"));
    }

    /** Section 7: player-triggered barrier flushes share one server-wide spacing of 20 ticks. */
    public boolean playerBarrierAllowed(long now) {
        return service.barrierSpacing().admit(null, now);
    }

    private <T> T barrier(boolean operator, Function<EndgameRoot, T> operation) {
        return operator ? service.barrier(operation) : service.spacedBarrier(operation);
    }

    /**
     * Redirects an in-transit or arrived record to another endpoint. The owner may redirect their own cargo whose
     * destination is gone; an operator any. The destination's index removal must be durable (a pending root is
     * written first), so a returning destination is retired or frozen (reviews R2-H1, R3-H1).
     */
    public EndgameCode redirect(TransitKey key, UUID target, UUID actor, boolean operator, long now) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        EndgameRoot root = view.get();
        Optional<TransitRecord> found = root.transits().record(key);
        if (found.isEmpty()) {
            return EndgameCode.TRANSFER_NOT_FOUND;
        }
        TransitRecord record = found.get();
        if (!operator && !record.owner().equals(actor)) {
            return EndgameCode.UNAUTHORIZED;
        }
        boolean destinationActive = root.endpoint(record.destination())
                .filter(endpoint -> endpoint.state() == EndpointRecord.State.ACTIVE).isPresent();
        if (destinationActive || !TransitRules.redirectable(record.facts(record.destination(), root.saveEpoch()), true)) {
            return EndgameCode.DESTINATION_ACTIVE;
        }
        Optional<EndpointRecord> to = root.endpoint(target).filter(endpoint -> endpoint.state()
                == EndpointRecord.State.ACTIVE && root.registrationDurable(target));
        if (to.isEmpty() || target.equals(record.destination())) {
            return EndgameCode.ROUTE_REFUSED;
        }
        if (!operator && !to.get().owner().equals(record.owner())) {
            return EndgameCode.TARGET_FOREIGN;
        }
        Route route = routes.get(record.system());
        EndgameCode routed = route == null ? EndgameCode.ROUTE_REFUSED : route.check(root, record, to.get(), operator);
        if (routed != EndgameCode.OK) {
            return routed;
        }
        // Section 7: owner redirects share the server-wide spacing, and an elevator's the station's cooldown.
        if (!operator && !service.barrierSpacing().admit(route.station(root, to.get()).orElse(null), now)) {
            return EndgameCode.ROOT_BUSY;
        }
        if (service.writePending()) {
            // The removal must be durable before the redirect: write what is pending first.
            barrier(operator, r -> null);
            if (service.writePending()) {
                return EndgameCode.ROOT_BUSY;
            }
        }
        barrier(operator, r -> {
            r.transits().replace(r.transits().record(key).orElseThrow().redirectedTo(target));
            return null;
        });
        audit(now, operator ? "REDIRECT_OPERATOR" : "REDIRECT_OWNER", key, record, actor, "to=" + target);
        return EndgameCode.OK;
    }

    /**
     * Removes a record and destroys its payload; or, for a quarantined outbox entry that has no record, removes it from
     * its loaded source. A source still holding a purged record's entry drops it as stale.
     */
    public EndgameCode purge(TransitKey key, UUID actor, long now) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        Optional<TransitRecord> record = view.get().transits().record(key);
        if (record.isPresent()) {
            service.barrier(r -> r.transits().remove(key));
            audit(now, "TRANSFER_PURGED", key, record.get(), actor, "");
            return EndgameCode.OK;
        }
        Optional<TransitEndpoint> source = service.transits().loaded(key.source());
        Optional<OutboxEntry> entry = source.flatMap(endpoint -> endpoint.source().quarantined().stream()
                .filter(candidate -> candidate.seq() == key.seq()).findFirst());
        if (entry.isEmpty()) {
            return source.isEmpty() && view.get().dispatchedThrough(key.source()) < key.seq()
                    ? EndgameCode.CHUNK_UNLOADED : EndgameCode.TRANSFER_NOT_FOUND;
        }
        source.get().source().remove(key.seq());
        source.get().transitChanged();
        service.audit().line(now, SYSTEM, "OUTBOX_PURGED", "OK", key.source(), source.get().endpointOwner().orElse(null),
                actor, "transfer=" + key + " payload=" + entry.get().payload().hash());
        return EndgameCode.OK;
    }

    /**
     * Applies the logged outcome of a removal settlement that never reached disk (section 9.1): a claimed,
     * unacknowledged record paid at an endpoint that no longer exists returns to arrived ({@code incoming}) or is
     * acknowledged ({@code moved}).
     */
    public EndgameCode resettle(TransitKey key, boolean moved, UUID actor, long now) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return EndgameCode.ROOT_UNAVAILABLE;
        }
        EndgameRoot root = view.get();
        Optional<TransitRecord> found = root.transits().record(key);
        if (found.isEmpty() || found.get().state() != TransitRecord.State.CLAIMED || found.get().acknowledged()) {
            return EndgameCode.TRANSFER_NOT_FOUND;
        }
        UUID paid = found.get().paidEndpoint();
        if (root.endpoint(paid).filter(endpoint -> endpoint.state() == EndpointRecord.State.ACTIVE).isPresent()) {
            return EndgameCode.DESTINATION_ACTIVE;
        }
        service.barrier(r -> {
            TransitRecord record = r.transits().record(key).orElseThrow();
            r.transits().replace(moved ? record.acknowledged(r.saveEpoch()) : record.returned());
            return null;
        });
        audit(now, "TRANSFER_RESETTLED", key, found.get(), actor, "outcome=" + (moved ? "moved" : "incoming"));
        return EndgameCode.OK;
    }

    /**
     * Resolves a retired endpoint's frozen contents item by item (section 9, review R3-M1), or the frozen conflicts
     * of a registered one: an outbox entry goes back to the input buffer when its tombstone proves it unregistered, else
     * it is discarded; an incoming payload whose record is claimed here joins the receive buffer (acknowledged), any
     * other is destroyed; receipts are dropped. With {@code drops} (a removal) every returned or moved payload goes
     * there instead, for the block to drop; otherwise an item that does not fit stays frozen. One barrier flush.
     */
    public Resolved resolve(TransitEndpoint endpoint, @Nullable UUID actor, long now,
                            @Nullable List<ItemStack> drops) {
        return resolve(endpoint, actor, now, drops, false);
    }

    /** As {@link #resolve(TransitEndpoint, UUID, long, List)}; {@code spaced} for an owner's admitted request. */
    public Resolved resolve(TransitEndpoint endpoint, @Nullable UUID actor, long now,
                            @Nullable List<ItemStack> drops, boolean spaced) {
        Optional<EndgameRoot> view = service.root();
        if (view.isEmpty()) {
            return new Resolved(0, 0, 0, 0, 0);
        }
        EndgameRoot root = view.get();
        UUID id = endpoint.endpointId();
        UUID owner = endpoint.endpointOwner().orElse(null);
        boolean frozen = endpoint.transitFrozen();
        long epoch = root.saveEpoch();
        int returned = 0;
        int moved = 0;
        int destroyed = 0;
        int receipts = 0;
        int left = 0;
        if (frozen) {
            // A tombstone, or a MISSING index record (review C12R-L2), keeps dispatched_through: it proves an entry
            // above it was never registered.
            boolean tombstone = root.retired(id);
            long dispatched = root.dispatchedThrough(id);
            if (TransitRules.rollback(endpoint.source().nextSeq(), dispatched)) {
                service.audit().line(now, SYSTEM, "SOURCE_ROLLBACK", "OK", id, owner, actor, "resolve next_seq="
                        + endpoint.source().nextSeq() + " dispatched_through=" + dispatched);
            }
            for (OutboxEntry entry : endpoint.source().outbox()) {
                Optional<List<ItemStack>> stacks = entry.payload().decode();
                if (TransitRules.resolveOutboxToInput(tombstone, entry.seq(), dispatched) && stacks.isPresent()) {
                    if (drops != null) {
                        drops.addAll(stacks.get());
                    } else if (!endpoint.returnToInput(stacks.get())) {
                        left++;
                        continue;
                    }
                    returned++;
                } else {
                    destroyed++;
                    service.audit().line(now, SYSTEM, "OUTBOX_DISCARDED", "OK", id, owner, actor, "seq=" + entry.seq()
                            + " payload=" + entry.payload().hash());
                }
                endpoint.source().remove(entry.seq());
            }
        }
        List<TransitRecord> acknowledgements = new ArrayList<>();
        for (Map.Entry<TransitKey, TransitPayload> entry : endpoint.destination().incoming().entrySet()) {
            TransitKey key = entry.getKey();
            if (!frozen && !endpoint.destination().conflicts().contains(key)) {
                continue;
            }
            Optional<TransitRecord> record = root.transits().record(key);
            TransitRules.RecordFacts facts = record.map(found -> found.facts(id, epoch)).orElse(null);
            Optional<List<ItemStack>> stacks = entry.getValue().decode();
            if (TransitRules.resolveIncoming(facts) == TransitRules.Resolve.TO_RECEIVE_BUFFER && stacks.isPresent()) {
                if (drops != null) {
                    drops.addAll(stacks.get());
                } else if (endpoint.receiveBuffer().fits(List.of(stacks.get()))) {
                    endpoint.receiveBuffer().insert(stacks.get());
                } else {
                    left++;
                    continue;
                }
                if (!record.get().acknowledged()) {
                    acknowledgements.add(record.get().acknowledged(epoch));
                }
                moved++;
            } else {
                destroyed++;
                service.audit().line(now, SYSTEM, "INCOMING_DESTROYED", "OK", id, owner, actor, "transfer=" + key
                        + " payload=" + entry.getValue().hash());
            }
            endpoint.destination().takeIncoming(key);
            endpoint.destination().clearConflict(key);
        }
        for (TransitKey key : endpoint.destination().receipts()) {
            if (frozen || endpoint.destination().conflicts().contains(key)) {
                endpoint.destination().dropReceipt(key);
                endpoint.destination().clearConflict(key);
                receipts++;
            }
        }
        Resolved result = new Resolved(returned, moved, destroyed, receipts, left);
        if (result.anything()) {
            barrier(!spaced, r -> {
                acknowledgements.forEach(record -> r.transits().replace(record));
                return null;
            });
            endpoint.transitChanged();
            service.audit().line(now, SYSTEM, "endpoint_resolve", "OK", id, owner, actor, "returned=" + returned
                    + " moved=" + moved + " destroyed=" + destroyed + " receipts=" + receipts + " left=" + left);
        }
        return result;
    }

    /** One page of 16 records in key order, for {@code transfer list}. */
    public List<String> list(int page) {
        List<String> lines = new ArrayList<>();
        service.root().ifPresent(root -> root.transits().records().stream()
                .skip((long) page * EndgameLimits.AUDIT_PAGE_LINES).limit(EndgameLimits.AUDIT_PAGE_LINES)
                .forEach(record -> lines.add(describe(root, record))));
        return lines;
    }

    public Optional<String> inspect(TransitKey key) {
        return service.root().flatMap(root -> root.transits().record(key).map(record -> describe(root, record)
                + " owner=" + record.owner() + " paid_fe=" + record.paidFe() + " dispatch_epoch="
                + record.dispatchEpoch() + " arrive_at=" + record.arriveAt() + " ack_epoch=" + record.ackEpoch()
                + " redirected=" + record.redirected()));
    }

    private static String describe(EndgameRoot root, TransitRecord record) {
        boolean missing = record.state() != TransitRecord.State.CLAIMED && root.endpoint(record.destination())
                .filter(endpoint -> endpoint.state() == EndpointRecord.State.ACTIVE).isEmpty();
        return record.key() + " " + record.system().id() + " " + record.state()
                + (missing ? " DESTINATION_MISSING" : "") + " to=" + record.destination()
                + (record.paidEndpoint() == null ? "" : " paid=" + record.paidEndpoint())
                + (record.acknowledged() ? " acknowledged" : "") + (record.stub() ? " stub" : " payload="
                + record.payload().hash());
    }

    private void audit(long now, String action, TransitKey key, TransitRecord record, UUID actor, String fields) {
        service.audit().line(now, SYSTEM, action, "OK", key.source(), record.owner(), actor, "transfer=" + key
                + (record.payload() == null ? "" : " payload=" + record.payload().hash())
                + (fields.isEmpty() ? "" : " " + fields));
    }
}
