package io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;

/**
 * W90b (Root decisions R21 item 4 and R22 amendments 1 to 4): the governed destination warm-ups, server-free.
 * <p>
 * A warm-up is RAM state for one logical rocket L: one plain ticket on each column of the server-selected destination's
 * checked set (at most 16), added exactly once at its start because re-adding an equal ticket resets its age, and a
 * reservation of that exact region. It is never renewed, never saved, and never evidence for a census: F1 and F2
 * still read the world. One warm-up per L, at most {@link RocketWarmupSettings#globalCap()} in every state together
 * and {@link RocketWarmupSettings#perPlayer()} per initiating player or monitor owner; admin and release-test warm-ups have no player
 * and count only globally.
 * <p>
 * States: WARMING from the start; READY when the service tick first finds the margin loaded and entity-ticking (a hint
 * only); BOUND from F1's acceptance of a request for the same destination until the handover after T's first
 * successful ACTIVE margin refresh. Disconnect and source removal end only WARMING and READY; every state ends at its
 * deadline. A release removes exactly the started set in the warm-up's own Level and drops the reservation, and only
 * the live entry can be released, so a stale event never removes a later warm-up of the same rocket.
 */
public final class RocketWarmupRegistry {
    public enum State { WARMING, READY, BOUND }

    public enum Release { HANDOVER, LAUNCH_ENDED, REPLACED, DISCONNECTED, ROCKET_REMOVED, EXPIRED, STOPPED }

    /** PROCEED calls F1's begin, with or without a warm-up to bind; LOADING and LIMIT refuse the request. */
    public enum Outcome { PROCEED, LOADING, LIMIT }

    /** The warm-up ticket calls of one Level, keyed by its dimension; a recording sink in unit tests. */
    public interface Tickets {
        boolean present(ResourceLocation level);
        void add(ResourceLocation level, ChunkPos column, UUID logical);
        void remove(ResourceLocation level, ChunkPos column, UUID logical);
    }

    /** The read-only facts that the service tick polls, at most one of each per live warm-up and tick. */
    public interface Probe {
        boolean online(UUID player);
        boolean sourcePresent(Warmup warmup);
        boolean ready(Warmup warmup);
        void destinationReady(UUID player);
    }

    /**
     * One governed launch at the decision, after every existing check and the server's pad selection. {@code player}
     * is the quota owner (initiating player or monitor owner), or null for admin and release-test launches.
     * {@code requiresOnline} is false for unattended monitor requests; {@code target} is the plan's destination
     * target, compared by equality.
     */
    public record Request(UUID logical, UUID player, UUID requestId, ResourceLocation sourceLevel, UUID sourceEntity,
                          RocketStructureSnapshot source, Object target, RocketStructureSnapshot destination, boolean requiresOnline) {
        public Request(UUID logical, UUID player, UUID requestId, ResourceLocation sourceLevel, UUID sourceEntity,
                       RocketStructureSnapshot source, Object target, RocketStructureSnapshot destination) {
            this(logical, player, requestId, sourceLevel, sourceEntity, source, target, destination, player != null);
        }
        public Request {
            Objects.requireNonNull(logical, "logical"); Objects.requireNonNull(requestId, "requestId");
            Objects.requireNonNull(sourceLevel, "sourceLevel"); Objects.requireNonNull(sourceEntity, "sourceEntity");
            Objects.requireNonNull(source, "source"); Objects.requireNonNull(target, "target");
            Objects.requireNonNull(destination, "destination");
        }
    }

    /** One live warm-up; {@code lifetime} is the setting captured at its start, {@code transfer} is set while BOUND. */
    public record Warmup(UUID logical, UUID player, UUID request, ResourceLocation sourceLevel, UUID sourceEntity,
                         UUID sourceSnapshot, String sourceHash, Object target, RocketStructureSnapshot destination,
                         List<ChunkPos> columns, long started, int lifetime, State state, UUID transfer, boolean requiresOnline) {
        public ResourceLocation level() { return destination.sourceDimension(); }
        public RocketRegion region() { return RocketRegion.fromSnapshot(destination); }
        public long age(long now) { return Math.max(0L, now - started); }
        public long remaining(long now) { return Math.max(0L, lifetime - age(now)); }

        private Warmup with(State next, UUID bound) {
            return new Warmup(logical, player, request, sourceLevel, sourceEntity, sourceSnapshot, sourceHash, target,
                    destination, columns, started, lifetime, next, bound, requiresOnline);
        }

        /** The same Level and exact region, from the same source geometry (R22 amendment 1). */
        private boolean sameDestination(Request other) {
            return level().equals(other.destination().sourceDimension())
                    && region().equals(RocketRegion.fromSnapshot(other.destination())) && sameSource(other.source());
        }

        private boolean sameSource(RocketStructureSnapshot other) {
            return sourceSnapshot.equals(other.snapshotId()) && sourceHash.equals(other.contentHash());
        }
    }

    /** One request's decision; F1's acceptance binds {@code bindable} (design 7.2), its refusal or discard releases it. */
    public static final class Decision {
        private final Outcome outcome;
        private final UUID logical, transfer;
        private final boolean bindable;
        private boolean ended;

        private Decision(Outcome outcome, UUID logical, UUID transfer, boolean bindable) {
            this.outcome = outcome; this.logical = logical; this.transfer = transfer; this.bindable = bindable;
        }

        public Outcome outcome() { return outcome; }
        public boolean bindable() { return bindable; }
    }

    private final Map<UUID, Warmup> live = new LinkedHashMap<>();
    private final Tickets tickets;
    private final Consumer<String> log;
    private Decision pending;

    public RocketWarmupRegistry(Tickets tickets, Consumer<String> log) {
        this.tickets = Objects.requireNonNull(tickets, "tickets");
        this.log = Objects.requireNonNull(log, "log");
    }

    /**
     * Design 7.2's decision table with R22's answers 1 and 2. {@code available} is F1's side-effect-free preconditions;
     * when they fail, nothing is allocated or bound and an existing warm-up keeps running to its deadline. A BOUND
     * warm-up belongs to its launch and is never replaced. {@code ready} is the census's readiness predicate on the
     * selected destination, read after any replacement.
     */
    public Decision decide(Request request, boolean available, BooleanSupplier ready, long now, RocketWarmupSettings settings) {
        Objects.requireNonNull(request, "request"); Objects.requireNonNull(settings, "settings");
        pending = null;
        var current = live.get(request.logical());
        if (!available || current != null && current.state() == State.BOUND) { return proceed(request, false); }
        if (current != null && !current.sameDestination(request)) {
            release(current, Release.REPLACED, now);
            var replaced = current;
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowLaunchDiscard.log(log, "ARCE_GOVERNED_WARMUP_REPLACED",
                    () -> "logical=" + request.logical() + " request=" + request.requestId()
                            + " previous=" + replaced.request() + " from=" + replaced.region()
                            + " to=" + RocketRegion.fromSnapshot(request.destination()) + " same_source=" + replaced.sameSource(request.source()));
            current = null;
        }
        boolean destinationReady = ready.getAsBoolean();
        if (current == null) {
            if (!slot(request.player(), settings)) { return destinationReady ? proceed(request, false) : new Decision(Outcome.LIMIT,
                    request.logical(), request.requestId(), false); }
            var columns = RocketDiscoveryColumns.of(request.destination()).refreshSet();
            // Selection admits only checked destinations; anything else continues exactly as without a warm-up.
            if (columns.isEmpty() || !tickets.present(request.destination().sourceDimension())) { return proceed(request, false); }
            start(request, columns, now, settings);
        }
        return destinationReady ? proceed(request, true) : new Decision(Outcome.LOADING, request.logical(), request.requestId(), false);
    }

    /** F1's begin returned: bind on acceptance, otherwise (or after a discard during begin) release. */
    public void afterBegin(Decision decision, boolean started, long now) {
        if (decision == null || decision != pending) { return; }
        pending = null;
        var current = live.get(decision.logical);
        if (!decision.bindable || current == null || current.state() == State.BOUND) { return; }
        if (started && !decision.ended) {
            var bound = current.with(State.BOUND, decision.transfer);
            live.put(bound.logical(), bound);
            line("BOUND", bound, now, "none");
        } else {
            release(current, Release.LAUNCH_ENDED, now);
        }
    }

    /**
     * The launch's {@code discarded} callback (R22 amendment 4). It may run after {@code journal.put}, so it is no proof
     * that nothing was published: it releases only the warm-up bound to exactly this transfer, never any journal,
     * pending or named-pad authority, which this registry does not own.
     */
    public void launchEnded(UUID logical, UUID transfer, long now) {
        var current = live.get(logical);
        if (current != null && current.state() == State.BOUND && transfer.equals(current.transfer())) {
            release(current, Release.LAUNCH_ENDED, now);
        } else if (pending != null && pending.logical.equals(logical) && pending.transfer.equals(transfer)) {
            pending.ended = true;
        }
    }

    /** Called right after T's successful ACTIVE margin refresh, in the same visit, so the columns keep a ticket. */
    public void handover(UUID transfer, long now) {
        boundTo(transfer).ifPresent(current -> release(current, Release.HANDOVER, now));
    }

    /** Deadline, disconnect, source removal and readiness polling; at most one poll of each per warm-up. */
    public void tick(long now, Probe probe) {
        for (var current : List.copyOf(live.values())) {
            if (current.age(now) >= current.lifetime()) { release(current, Release.EXPIRED, now); continue; }
            if (current.state() == State.BOUND) { continue; }
            if (current.requiresOnline() && current.player() != null && !probe.online(current.player())) { release(current, Release.DISCONNECTED, now); continue; }
            if (!probe.sourcePresent(current)) { release(current, Release.ROCKET_REMOVED, now); continue; }
            if (current.state() == State.WARMING && probe.ready(current)) {
                var ready = current.with(State.READY, null);
                live.put(ready.logical(), ready);
                line("READY", ready, now, "none");
                if (ready.player() != null) { probe.destinationReady(ready.player()); }
            }
        }
    }

    /** Stop: {@code releaseTickets} while the Levels stay loaded (in-process fixtures); otherwise the map is dropped. */
    public void clear(boolean releaseTickets, long now) {
        pending = null;
        for (var current : List.copyOf(live.values())) {
            if (releaseTickets) {
                release(current, Release.STOPPED, now);
            } else {
                live.remove(current.logical());
                line("RELEASED", current, now, Release.STOPPED.name());
            }
        }
    }

    /** R22 amendment 2: while a warm-up lives, its starting request is refused, whatever the operation ledger kept. */
    public boolean startedBy(UUID request) {
        return live.values().stream().anyMatch(current -> current.request().equals(request));
    }

    /** The third reservation input: every live warm-up's region except the requester's own (design 7.4). */
    public List<RocketRegion> reservations(UUID requester) {
        var regions = new ArrayList<RocketRegion>(live.size());
        live.values().stream().filter(current -> !current.logical().equals(requester)).forEach(current -> regions.add(current.region()));
        return List.copyOf(regions);
    }

    /** R22 amendment 1: an unbound warm-up's stored origin for the same destination and source geometry. */
    public Optional<RocketPosition> kept(UUID logical, ResourceLocation level, RocketStructureSnapshot source, Object target) {
        var current = live.get(logical);
        return current != null && current.state() != State.BOUND && current.level().equals(level)
                && current.target().equals(target) && current.sameSource(source)
                ? Optional.of(current.destination().sourceOrigin()) : Optional.empty();
    }

    public Optional<Warmup> warmup(UUID logical) { return Optional.ofNullable(live.get(logical)); }

    public Optional<Warmup> boundTo(UUID transfer) {
        return live.values().stream().filter(current -> current.state() == State.BOUND && transfer.equals(current.transfer())).findFirst();
    }

    public Collection<Warmup> live() { return List.copyOf(live.values()); }

    public int count(UUID player) {
        return (int) live.values().stream().filter(current -> Objects.equals(current.player(), player)).count();
    }

    private boolean slot(UUID player, RocketWarmupSettings settings) {
        return live.size() < settings.globalCap() && (player == null || count(player) < settings.perPlayer());
    }

    /** Read-only pre-selection capacity check, including replacement of this logical rocket's unbound allocation. */
    public boolean canAllocate(UUID logical, UUID player, RocketWarmupSettings settings) {
        var own = live.get(logical);
        int other = live.size() - (own == null ? 0 : 1);
        int owners = count(player) - (own != null && Objects.equals(own.player(), player) ? 1 : 0);
        return other < settings.globalCap() && (player == null || owners < settings.perPlayer());
    }

    private Decision proceed(Request request, boolean bindable) {
        pending = new Decision(Outcome.PROCEED, request.logical(), request.requestId(), bindable);
        return pending;
    }

    private void start(Request request, List<ChunkPos> columns, long now, RocketWarmupSettings settings) {
        var started = new Warmup(request.logical(), request.player(), request.requestId(), request.sourceLevel(),
                request.sourceEntity(), request.source().snapshotId(), request.source().contentHash(), request.target(),
                request.destination(), List.copyOf(columns), now, settings.lifetimeTicks(), State.WARMING, null, request.requiresOnline());
        live.put(started.logical(), started);
        // Exactly once: no repeated request, poll, bind or handover adds these tickets again.
        for (var column : started.columns()) { tickets.add(started.level(), column, started.logical()); }
        line("STARTED", started, now, "none");
    }

    /** Only the live entry; an absent Level holds no tickets, so it is skipped and the warm-up is still dropped. */
    private void release(Warmup current, Release reason, long now) {
        if (live.get(current.logical()) != current) { return; }
        live.remove(current.logical());
        if (tickets.present(current.level())) {
            for (var column : current.columns()) { tickets.remove(current.level(), column, current.logical()); }
        }
        line("RELEASED", current, now, reason.name());
    }

    private void line(String event, Warmup current, long now, String reason) {
        log.accept("ARCE_GOVERNED_WARMUP event=" + event + " logical=" + current.logical() + " request=" + current.request()
                + " transfer=" + (current.transfer() == null ? "none" : current.transfer()) + " level=" + current.level()
                + " columns=" + current.columns().size() + " age=" + current.age(now) + " reason=" + reason);
    }
}
