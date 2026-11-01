package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSelector;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position;
import java.util.*;
import java.util.function.BiPredicate;
import net.minecraft.server.level.ServerPlayer;

/** Capped server-lifetime RAM only. Live identity and anti-refresh history have different lifetimes. */
final class BayPassengerExitState {
    static final int LIVE_LIMIT = 1024, HISTORY_LIMIT = 1024, TRANSFER_LIMIT = 64, HINT_LIMIT = 128;
    record Pair(UUID player, UUID transfer) { }
    record History(UUID logical, long firstTick, boolean issued) { long deadline() { return firstTick + 200; } }
    record Hint(UUID transfer, UUID endpoint, String hash, Object level) { }
    static final class Slot {
        final BayPassengerExitPermit permit;
        final BayPassengerExitWorld world;
        BayExitSelector.Context context;
        BayExitSelector.Result result;
        BayPassengerExitDescent descent;
        boolean completed;
        Slot(BayPassengerExitPermit permit, BayPassengerExitWorld world) { this.permit = permit; this.world = world; }
    }
    interface Scope extends AutoCloseable { @Override void close(); }
    final Map<ServerPlayer, Slot> live = new IdentityHashMap<>();
    private final Map<ServerPlayer, Integer> internal = new IdentityHashMap<>();
    private final Map<Pair, History> history = new LinkedHashMap<>();
    private final Map<UUID, UUID> transfers = new LinkedHashMap<>();
    private final Map<Hint, Position> hints = new LinkedHashMap<>();
    private final Thread owner = Thread.currentThread();
    void checkThread() {
        if (Thread.currentThread() != owner) { throw new IllegalStateException("Bay exit is server-thread only"); }
    }
    boolean internal(ServerPlayer player) { checkThread(); return internal.containsKey(player); }
    Scope enter(ServerPlayer player, Runnable revoke) {
        checkThread(); revoke.run();
        if (internal.size() == LIVE_LIMIT && !internal.containsKey(player)) {
            throw new IllegalStateException("Too many nested passenger transitions");
        }
        internal.merge(player, 1, Integer::sum);
        return new Scope() {
            boolean closed;
            @Override public void close() {
                checkThread();
                if (!closed) { closed = true; internal.compute(player, (key, depth) -> depth == 1 ? null : depth - 1); }
            }
        };
    }
    /** Null means no row was admitted; an existing row is never renewed or evicted. */
    History admit(UUID player, UUID transfer, UUID logical, long tick) {
        checkThread();
        var key = new Pair(player, transfer);
        if (history.containsKey(key) || history.size() == HISTORY_LIMIT
                || !transfers.containsKey(transfer) && transfers.size() == TRANSFER_LIMIT) { return null; }
        var row = new History(logical, tick, false);
        history.put(key, row); transfers.put(transfer, logical); return row;
    }
    void issued(UUID player, UUID transfer) {
        checkThread(); var key = new Pair(player, transfer); var row = history.get(key);
        if (row != null) { history.put(key, new History(row.logical, row.firstTick, true)); }
    }
    History history(UUID player, UUID transfer) { checkThread(); return history.get(new Pair(player, transfer)); }
    int histories() { return history.size(); }
    /** Caller proves current+acknowledged absence and no live reference once per capped T bucket. */
    void clean(BiPredicate<UUID, UUID> ended) {
        checkThread(); var removed = new HashSet<UUID>();
        transfers.forEach((transfer, logical) -> { if (ended.test(transfer, logical)) { removed.add(transfer); } });
        history.keySet().removeIf(pair -> removed.contains(pair.transfer));
        transfers.keySet().removeAll(removed);
        hints.keySet().removeIf(hint -> removed.contains(hint.transfer));
    }
    Optional<Position> hint(Hint key) { checkThread(); return Optional.ofNullable(hints.get(key)); }
    void hint(Hint key, Position position) {
        checkThread();
        hints.keySet().removeIf(old -> old.endpoint.equals(key.endpoint) && !old.equals(key));
        if (hints.containsKey(key) || hints.size() < HINT_LIMIT) { hints.put(key, position); }
    }
    void clear() { checkThread(); live.clear(); internal.clear(); history.clear(); transfers.clear(); hints.clear(); }
}
