package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipts;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.util.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;

/** Server-owned voluntary exit orchestration. All authority stores are read-only. */
final class BayPassengerExitService {
    private final MinecraftServer server;
    final BayPassengerExitState state = new BayPassengerExitState();
    final BayExitBudget budget;
    private final BayExitSelector selector;
    final BayPassengerExitMotion motion = new BayPassengerExitMotion();
    final BayPassengerExitLanding landing = new BayPassengerExitLanding();
    private final Map<BayExitSelector.Context, BayPassengerExitState.Slot> contexts = new IdentityHashMap<>();
    private final Set<BayExitSelector.Context> retiring = Collections.newSetFromMap(new IdentityHashMap<>());
    private int selecting, observations;
    private long begunTick;
    private boolean stopped;
    private java.util.function.BiConsumer<ServerPlayer, RocketEntity> locationProbe;

    BayPassengerExitService(MinecraftServer server) {
        this.server = server;
        begunTick = server.getTickCount();
        budget = new BayExitBudget(server.getTickCount());
        selector = new BayExitSelector(budget, (box, probe, reservations) -> BayExitSafety.Verdict.UNPROVED);
    }
    private boolean thread() { return !stopped && server.isSameThread(); }
    private static Position position(ServerPlayer player, Vec3 point) {
        return new Position(player.serverLevel().dimension().location().toString(), point.x, point.y, point.z);
    }
    void capture(ServerPlayer player, RocketEntity rocket) {
        if (!thread() || player.getServer() != server || state.internal(player)
                || !BayReceipts.holdsRocket(server, rocket.assemblyTransactionId().orElse(null))) { return; }
        var old = state.live.get(player);
        if (old != null && !old.completed && old.permit.rocket == rocket) { return; }
        // Do not allocate a second capability from a nested world callback.
        if (selecting > 0) { refuse(player, rocket, BayPassengerExitFeedback.REENTRANT_REFUSED); return; }
        revoke(player);
        if (state.live.size() == BayPassengerExitState.LIVE_LIMIT) { refuse(player, rocket, BayPassengerExitFeedback.EXIT_CAPACITY); return; }
        var permit = BayPassengerExitPermit.capture(player, rocket, motion.generation(player));
        if (permit == null) {
            // A guarded rider's detach is the rejoin guard's own trusted pin, which reports through that guard.
            if (!BayPassengerExitPermit.guarded(player)) { refuse(player, rocket, BayPassengerExitFeedback.AUTHORITY_UNAVAILABLE); }
            return;
        }
        var slot = new BayPassengerExitState.Slot(permit, new BayPassengerExitWorld(player));
        state.live.put(player, slot);
        var size = player.getDimensions(Pose.STANDING);
        var request = new BayExitSelector.Request(position(player, rocket.position()), position(player, permit.before),
                permit.hint == null ? Optional.empty() : state.hint(permit.hint), Optional.ofNullable(permit.anchor), size.width, size.height);
        slot.context = selector.capture(request, () -> current(slot), BayExitSafety.loaded(slot.world));
        contexts.put(slot.context, slot);
    }
    private void refuse(ServerPlayer player, RocketEntity rocket, String outcome) {
        if (state.refusals.size() < BayPassengerExitState.LIVE_LIMIT || state.refusals.containsKey(player)) {
            state.refusals.put(player, new BayPassengerExitState.Refusal(rocket, outcome));
        }
    }
    private boolean current(BayPassengerExitState.Slot slot) {
        return state.live.get(slot.permit.player) == slot && !slot.completed
                && slot.permit.current(motion, true) && slot.permit.player.position().equals(slot.permit.before);
    }
    /** W242-R1: a gated, read-only GameTest observer of the location hook's entry; never set on an ordinary server. */
    void probeLocationForTests(java.util.function.BiConsumer<ServerPlayer, RocketEntity> probe) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        locationProbe = probe;
    }
    Vec3 location(ServerPlayer player, RocketEntity rocket) {
        if (locationProbe != null && thread()) { locationProbe.accept(player, rocket); }
        if (!thread() || state.internal(player)) { return rocket.position().add(1.5, 0, 0); }
        var slot = state.live.get(player);
        if (slot == null || slot.permit.rocket != rocket) {
            boolean held = BayReceipts.holdsRocket(server, rocket.assemblyTransactionId().orElse(null));
            var refusal = state.refusals.remove(player);
            if (held && refusal != null && refusal.rocket() == rocket) { BayPassengerExitFeedback.refused(player, rocket, refusal.outcome()); }
            return held ? player.position() : rocket.position().add(1.5, 0, 0);
        }
        if (slot.completed) { return vector(slot.result.position()); }
        BayExitSelector.Result result;
        selecting++;
        try { result = selector.select(slot.context); }
        finally {
            if (--selecting == 0) { retiring.forEach(selector::release); retiring.clear(); }
        }
        if (result.status() == BayExitSelector.Status.PENDING || result.status() == BayExitSelector.Status.STALE) { return player.position(); }
        finish(slot, result);
        return slot.completed ? vector(result.position()) : player.position();
    }
    private static Vec3 vector(Position position) { return new Vec3(position.x(), position.y(), position.z()); }
    private void finish(BayPassengerExitState.Slot slot, BayExitSelector.Result result) {
        if (!current(slot)) { return; }
        var permit = slot.permit;
        var player = permit.player;
        slot.result = result;
        // Detach is proved now. Even a safe first exit closes the UUID/T offer permanently for this lifetime.
        var row = permit.transfer == null ? null : state.admit(player.getUUID(), permit.transfer.transferId(), permit.logical, server.getTickCount());
        RocketRuntime.baySourceChanged(permit.rocket);
        player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0;
        var before = motion.generation(player);
        var target = vector(result.position());
        player.connection.teleport(target.x, target.y, target.z, player.getYRot(), player.getXRot());
        var after = motion.generation(player);
        if (state.live.get(player) != slot || !permit.authority() || player.isPassenger()
                || !BayPassengerExitMotion.ownCorrection(before, after) || !player.position().equals(target)) {
            revoke(player); return;
        }
        slot.completed = true;
        selector.applied(slot.context);
        boolean safe = result.status() == BayExitSelector.Status.SAFE;
        if (safe && permit.hint != null) { state.hint(permit.hint, result.position()); }
        if (BayPassengerExitFeedback.grants(result.status(), row != null, landing.available())) {
            slot.descent = new BayPassengerExitDescent(permit, after, row.deadline());
            state.issued(player.getUUID(), permit.transfer.transferId());
        }
        BayPassengerExitFeedback.completed(slot, BayPassengerExitFeedback.outcome(result.status(), permit.transfer != null,
                row != null, slot.descent != null));
    }
    /** W242-R10: a held self-exit's own exit message replaces the flight request's "accepted" action-bar line. */
    static final io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult HELD_EXIT =
            new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult(
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS, 0);
    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult heldLeave(ServerPlayer player, RocketEntity rocket) {
        if (!thread() || !BayReceipts.holdsRocket(server, rocket.assemblyTransactionId().orElse(null))) { return null; }
        var code = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.NOT_BOARDED;
        if (player.getVehicle() == rocket) {
            code = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED;
            if (!BayPassengerExitPermit.guarded(player) && !RocketReleaseAccess.fenced(rocket)) {
                player.stopRiding();
                if (player.getVehicle() != rocket) { return HELD_EXIT; }
            }
        }
        return new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult(code, 0);
    }
    void sample(ServerPlayer player) {
        sample(player, BayPassengerExitMotion.Point.SERVER_END);
    }
    void sample(ServerPlayer player, BayPassengerExitMotion.Point point) {
        if (!thread()) { return; }
        var slot = state.live.get(player);
        if (slot != null && slot.descent != null) {
            if (++observations > 1024) { revoke(player); return; }
            slot.descent.sample(motion, budget, slot.world, point);
            if (slot.descent.closed) { revoke(player); }
        }
    }
    void fall(LivingFallEvent event, ServerPlayer player) {
        if (!thread()) { return; }
        var slot = state.live.get(player);
        if (slot != null && slot.descent != null) {
            if (++observations > 1024) { revoke(player); return; }
            slot.descent.fall(event, motion, landing, budget, slot.world);
        }
    }
    void attack(LivingAttackEvent event, ServerPlayer player) {
        if (!thread()) { return; }
        var slot = state.live.get(player);
        if (slot != null && slot.descent != null) {
            if (++observations > 1024) { revoke(player); return; }
            if (slot.descent.attack(event, motion, landing, budget, slot.world)) { event.setCanceled(true); }
        }
    }
    void revoke(ServerPlayer player) {
        if (!thread()) { return; }
        state.refusals.remove(player);
        var slot = state.live.remove(player);
        if (slot != null) {
            if (slot.descent != null) { slot.descent.close(); }
            contexts.remove(slot.context);
            if (selecting > 0) { retiring.add(slot.context); } else { selector.release(slot.context); }
        }
    }
    BayPassengerExitState.Scope internal(ServerPlayer player) { return state.enter(player, () -> revoke(player)); }
    void beginTick() {
        if (thread() && server.getTickCount() > begunTick) {
            begunTick = server.getTickCount(); budget.advanceTick(begunTick); observations = 0; landing.endTick();
        }
    }
    void endTick() {
        if (!thread()) { return; }
        selecting++;
        try {
            selector.drain(context -> {
                var slot = contexts.get(context);
                if (slot != null) { finish(slot, context.result().orElseThrow()); }
            });
        } finally {
            if (--selecting == 0) { retiring.forEach(selector::release); retiring.clear(); }
        }
        for (var entry : List.copyOf(state.live.entrySet())) {
            var slot = entry.getValue();
            if (!slot.completed || slot.descent == null) { revoke(entry.getKey()); }
            else { sample(entry.getKey()); }
        }
        // A hook bypass detached without the location hook; a cancelled detach still rides and hears nothing.
        for (var entry : List.copyOf(state.refusals.entrySet())) {
            var refusal = entry.getValue();
            if (entry.getKey().getVehicle() != refusal.rocket()) { BayPassengerExitFeedback.refused(entry.getKey(), refusal.rocket(), refusal.outcome()); }
        }
        state.refusals.clear();
        var active = new HashSet<UUID>();
        state.live.values().forEach(slot -> { if (slot.permit.transfer != null) { active.add(slot.permit.transfer.transferId()); } });
        var journal = RocketTransferSavedData.existing(server);
        if (journal != null && journal.operational() && journal.clean()) {
            state.clean((transfer, logical) -> !active.contains(transfer) && journal.find(transfer).isEmpty()
                    && journal.findByLogicalRocket(logical).isEmpty() && journal.logicalGuard(logical)
                        .filter(guard -> guard.transfer() == null && guard.recordDigest() == null).isPresent());
        }
    }
    void clear() {
        if (!thread()) { return; }
        for (var player : List.copyOf(state.live.keySet())) { revoke(player); }
        state.clear(); contexts.clear(); retiring.clear(); stopped = true;
    }
}
