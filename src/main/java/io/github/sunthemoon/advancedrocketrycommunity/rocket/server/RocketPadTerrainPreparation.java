package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupSettings;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntFunction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;

/** Session-owned FULL-terrain acquisition, not a selected landing pad or a launch continuation. */
final class RocketPadTerrainPreparation {
    // FULL is the same level acquired by the old synchronous getChunk, without managedBlock or entity-ticking authority.
    private static final int TERRAIN_DISTANCE = 0;
    private final RocketWarmupRegistry registry;
    private final Map<UUID, Pending> pending = new LinkedHashMap<>();
    private long lastAdvanced = Long.MIN_VALUE;

    RocketPadTerrainPreparation(RocketWarmupRegistry registry) { this.registry = registry; }

    boolean waiting(RocketEntity rocket, RocketFlightData countdown, RocketRequestCompletion completion) {
        var current = pending.get(countdown.logicalRocketId());
        if (current == null) { return false; }
        if (current.matches(rocket, countdown, completion)) { return true; }
        release(current, true, "REPLACED"); return false;
    }

    boolean start(RocketEntity rocket, RocketFlightData countdown, RocketRequestCompletion completion,
            ServerLevel target, RocketLandingPadSelection demand, IntFunction<RocketLandingPadSelection> select,
            RocketWarmupSettings settings) {
        if (demand.terrainColumns().isEmpty() || countdown.plan().orElseThrow().landingPad() != null) { return false; }
        var slot = registry.claimTerrain(countdown.logicalRocketId(), completion.requester(),
                countdown.plan().orElseThrow().requestId(), target.getServer().getTickCount(), settings).orElse(null);
        if (slot == null) { return false; }
        var entry = new Pending(slot, rocket, countdown, completion, target, select);
        pending.put(slot.logical(), entry);
        changeDemand(entry, demand);
        report(entry, "STARTED"); return true;
    }

    void tick(MinecraftServer server) {
        for (var entry : List.copyOf(pending.values())) {
            if (entry.slot.expired(server.getTickCount())) { release(entry, true, "EXPIRED"); }
            else if (entry.completion.requiresOnline() && entry.slot.player() != null
                    && server.getPlayerList().getPlayer(entry.slot.player()) == null) { release(entry, true, "DISCONNECTED"); }
            else if (!entry.sourcePresent(server)) { release(entry, true, "SOURCE_CHANGED"); }
        }
        if (pending.isEmpty() || lastAdvanced == server.getTickCount()) { return; }
        lastAdvanced = server.getTickCount();
        // Round robin, one candidate continuation per tick even if several requests arrived in one burst.
        var entry = pending.values().iterator().next();
        pending.remove(entry.slot.logical()); pending.put(entry.slot.logical(), entry);
        if (entry.columns.stream().anyMatch(c -> entry.target.getChunkSource().getChunkNow(c.x, c.z) == null)) { return; }
        if (!EscrowGovernedPreparation.available(entry.rocket, entry.countdown)) { return; }
        var selection = entry.select.apply(entry.cursor);
        if (!selection.terrainColumns().isEmpty()) {
            if (selection.terrainStep() < entry.cursor) { throw new IllegalStateException("Terrain cursor moved backwards"); }
            changeDemand(entry, selection); return;
        }
        if (!selection.success()) { release(entry, true, "UNAVAILABLE"); return; }
        // The exact selected region only becomes a reservation here, after all terrain/occupancy/reservation checks.
        removeTickets(entry);
        var plan = entry.countdown.plan().orElseThrow();
        var request = new RocketWarmupRegistry.Request(entry.slot.logical(), entry.slot.player(), entry.slot.request(),
                entry.source.sourceDimension(), entry.rocket.getUUID(), entry.source, plan.destinationTarget(),
                selection.snapshot().orElseThrow(), entry.completion.requiresOnline());
        boolean completed = registry.completeTerrain(entry.slot, request, server.getTickCount());
        pending.remove(entry.slot.logical(), entry);
        if (!completed) { registry.releaseTerrain(entry.slot); }
        report(entry, completed ? "SELECTED" : "REFUSED");
    }

    private void changeDemand(Pending entry, RocketLandingPadSelection demand) {
        if (entry.columns.equals(demand.terrainColumns())) { entry.cursor = demand.terrainStep(); return; }
        removeTickets(entry);
        entry.columns = demand.terrainColumns(); entry.cursor = demand.terrainStep();
        for (var column : entry.columns) {
            entry.target.getChunkSource().addRegionTicket(RocketDestinationWarmups.WARMUP_TICKET,
                    column, TERRAIN_DISTANCE, entry.slot.logical());
        }
    }

    private static void removeTickets(Pending entry) {
        for (var column : entry.columns) {
            entry.target.getChunkSource().removeRegionTicket(RocketDestinationWarmups.WARMUP_TICKET,
                    column, TERRAIN_DISTANCE, entry.slot.logical());
        }
        entry.columns = List.of();
    }

    private void release(Pending entry, boolean tickets, String reason) {
        if (pending.get(entry.slot.logical()) != entry) { return; }
        if (tickets) { removeTickets(entry); }
        pending.remove(entry.slot.logical()); registry.releaseTerrain(entry.slot); report(entry, reason);
    }

    void clear(boolean tickets) {
        for (var entry : List.copyOf(pending.values())) { release(entry, tickets, "STOPPED"); }
        lastAdvanced = Long.MIN_VALUE;
    }

    Optional<String> inspect(UUID logical, long now) {
        return Optional.ofNullable(pending.get(logical)).map(entry -> "Warm-up logical=" + logical
                + " state=TERRAIN request=" + entry.slot.request() + " level=" + entry.target.dimension().location()
                + " remaining=" + Math.max(0, entry.slot.settings().lifetimeTicks() - (now - entry.slot.started())));
    }

    int size() { return pending.size(); }
    boolean contains(UUID logical) { return pending.containsKey(logical); }

    private static void report(Pending entry, String event) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_ROCKET_TERRAIN_PREPARATION logical={} request={} event={} age={}",
                entry.slot.logical(), entry.slot.request(), event, entry.target.getServer().getTickCount() - entry.slot.started());
    }

    private static final class Pending {
        final RocketWarmupRegistry.Terrain slot;
        final RocketEntity rocket;
        final RocketFlightData countdown, originalFlight;
        final RocketStructureSnapshot source;
        final RocketRequestCompletion completion;
        final ServerLevel target;
        final IntFunction<RocketLandingPadSelection> select;
        List<ChunkPos> columns = List.of();
        int cursor = -1;

        Pending(RocketWarmupRegistry.Terrain slot, RocketEntity rocket, RocketFlightData countdown,
                RocketRequestCompletion completion, ServerLevel target, IntFunction<RocketLandingPadSelection> select) {
            this.slot = slot; this.rocket = rocket; this.countdown = countdown; this.completion = completion;
            this.target = target; this.select = select;
            source = rocket.snapshot().orElseThrow(); originalFlight = rocket.flightData().orElseThrow();
        }

        boolean sourcePresent(MinecraftServer server) {
            var level = RocketTransferEntities.level(server, source.sourceDimension());
            return level != null && level.getEntity(rocket.getUUID()) == rocket && rocket.isAlive() && rocket.operational()
                    && RocketTransferEntities.level(server, target.dimension().location()) == target && sameSource(rocket);
        }

        boolean sameSource(RocketEntity other) {
            var snapshot = other.snapshot().orElse(null);
            return other == rocket && snapshot != null && snapshot.snapshotId().equals(source.snapshotId())
                    && snapshot.contentHash().equals(source.contentHash()) && other.flightData().filter(originalFlight::equals).isPresent();
        }

        boolean matches(RocketEntity other, RocketFlightData flight, RocketRequestCompletion request) {
            return sameSource(other) && countdown.plan().orElseThrow().destinationTarget().equals(flight.plan().orElseThrow().destinationTarget())
                    && java.util.Objects.equals(countdown.plan().orElseThrow().landingPad(), flight.plan().orElseThrow().landingPad())
                    && java.util.Objects.equals(slot.player(), request.requester()) && completion.requiresOnline() == request.requiresOnline();
        }
    }
}
