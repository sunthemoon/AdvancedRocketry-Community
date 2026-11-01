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
        if (selecting > 0) { return; } // Do not allocate a second capability from a nested world callback.
        revoke(player);
        if (state.live.size() == BayPassengerExitState.LIVE_LIMIT) { return; }
        var permit = BayPassengerExitPermit.capture(player, rocket, motion.generation(player));
        if (permit == null) { return; }
        // Vanilla's subsequent portal check uses a creating lookup. Cache only an already loaded FULL column.
        if (player.serverLevel().getChunkSource().getChunk(rocket.chunkPosition().x, rocket.chunkPosition().z,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false) == null) { return; }
        var slot = new BayPassengerExitState.Slot(permit, new BayPassengerExitWorld(player));
        state.live.put(player, slot);
        var size = player.getDimensions(Pose.STANDING);
        var request = new BayExitSelector.Request(position(player, rocket.position()), position(player, permit.before),
                permit.hint == null ? Optional.empty() : state.hint(permit.hint), Optional.ofNullable(permit.anchor), size.width, size.height);
        slot.context = selector.capture(request, () -> current(slot), BayExitSafety.loaded(slot.world));
        contexts.put(slot.context, slot);
    }
    private boolean current(BayPassengerExitState.Slot slot) {
        return state.live.get(slot.permit.player) == slot && !slot.completed
                && slot.permit.current(motion, true) && slot.permit.player.position().equals(slot.permit.before);
    }
    Vec3 location(ServerPlayer player, RocketEntity rocket) {
        if (!thread() || state.internal(player)) { return rocket.position().add(1.5, 0, 0); }
        var slot = state.live.get(player);
        if (slot == null || slot.permit.rocket != rocket) {
            return BayReceipts.holdsRocket(server, rocket.assemblyTransactionId().orElse(null)) ? player.position()
                    : rocket.position().add(1.5, 0, 0);
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
        // Selection may evict capture's cache entry. Prime only the already loaded target
        // immediately before vanilla's creating lookup; never acquire a missing column.
        var targetBlock = net.minecraft.core.BlockPos.containing(target);
        if (player.serverLevel().getChunkSource().getChunk(targetBlock.getX() >> 4, targetBlock.getZ() >> 4,
                net.minecraft.world.level.chunk.ChunkStatus.FULL, false) == null) { revoke(player); return; }
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
        if (!safe && result.status() != BayExitSelector.Status.EXIT_BUDGET_EXHAUSTED && row != null && landing.available()) {
            slot.descent = new BayPassengerExitDescent(permit, after, row.deadline());
            state.issued(player.getUUID(), permit.transfer.transferId());
        }
        String outcome = safe ? "safe" : slot.descent != null ? "fall_offer"
                : permit.transfer == null ? "authority_unavailable" : row == null ? "capacity_or_used"
                : result.status() == BayExitSelector.Status.EXIT_BUDGET_EXHAUSTED ? "budget_exhausted" : "no_safe_exit";
        BayPassengerExitFeedback.completed(slot, outcome);
    }
    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult heldLeave(ServerPlayer player, RocketEntity rocket) {
        if (!thread() || !BayReceipts.holdsRocket(server, rocket.assemblyTransactionId().orElse(null))) { return null; }
        var code = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.NOT_BOARDED;
        if (player.getVehicle() == rocket) {
            code = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED;
            if (!BayPassengerExitPermit.guarded(player) && !RocketReleaseAccess.fenced(rocket)) {
                player.stopRiding();
                if (player.getVehicle() != rocket) { code = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode.SUCCESS; }
            }
        }
        return new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult(code, 0);
    }
    void sample(ServerPlayer player) {
        if (!thread()) { return; }
        var slot = state.live.get(player);
        if (slot != null && slot.descent != null) {
            if (++observations > 1024) { revoke(player); return; }
            slot.descent.sample(motion, budget, slot.world);
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
