package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Explicit native benchmark export only. Normal builds compile this as test code and never package it. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class W197I6NativeCost {
    private final MinecraftServer server;
    private final RocketTransferSavedData journal;
    private final RocketOrdinaryRetirement coordinator;
    private final W197CostObserver observation;
    private final List<RocketTransferRecord> records = new ArrayList<>();
    private final List<RocketEntity> rockets = new ArrayList<>();
    private final List<RocketLandedDisassemblyGameTestSupport.Requester> players = new ArrayList<>();
    private final List<ChunkPos> columns = new ArrayList<>();
    private final List<Integer> offered = new ArrayList<>();
    private int phaseTicks, warmColumn, roundStartProofs, roundsWithoutProgress, drainedAt = -1;
    private boolean done;
    private String failure;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.w197I6Cost")) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        MinecraftForge.EVENT_BUS.register(new W197I6NativeCost(event.getServer()));
    }

    private W197I6NativeCost(MinecraftServer server) {
        this.server = server; journal = RocketTransferSavedData.get(server);
        coordinator = RocketSourceRetirementFixtures.recovery(RocketRuntime.installedTransfers()).retirements();
        observation = new W197CostObserver(server);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void start(TickEvent.ServerTickEvent event) {
        if (done) { return; }
        if (event.phase == TickEvent.Phase.START) { observation.start(); }
        else { observation.beginEnd(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void end(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || done) { return; }
        String previousPhase = observation.phase;
        try { step(); }
        catch (Exception error) {
            failure = error.toString();
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W197_COST_FIXTURE_FAILED", error);
        }
        // The final setup tick includes a checked journal flush, not baseline work.
        if (!previousPhase.equals("SETUP")) { observation.end(coordinator); } else { observation.reset(); }
        if (failure != null) { finish("FAILED"); }
        else if (observation.phase.equals("DONE")) { finish("MEASURED"); }
    }

    private void phase(String value) {
        observation.phase = value; phaseTicks = 0;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W197_COST_PHASE phase={} round={} proofs={}", value,
                observation.round, observation.handedOff.size());
    }

    private void step() {
        phaseTicks++;
        require(phaseTicks < 6000, "Fixture phase deadline: " + observation.phase);
        switch (observation.phase) {
            case "SETUP" -> {
                if (records.size() < W197MaximumFixture.QUEUE) { seed(records.size()); return; }
                if (phaseTicks < W197MaximumFixture.QUEUE + 80 || !journal.flushChecked(server)) { return; }
                require(records.stream().noneMatch(record -> RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record)),
                        "Preparation is not cold");
                phase("BASELINE");
            }
            case "BASELINE" -> {
                if (phaseTicks < 200) { return; }
                phase("PREPARATION");
                for (int i = 0; i < records.size(); i++) {
                    final int index = i;
                    observation.timed("entry.prepare", () -> { RocketRuntime.requestDisassembly(players.get(index), rockets.get(index)); return null; });
                    require(players.get(i).token == null, "Cold demand issued consent");
                    observation.attach(coordinator, records.get(i).logicalRocketId(), i);
                }
                require(coordinator.size() == 64, "Preparation queue is not full");
            }
            case "PREPARATION" -> {
                if (coordinator.size() != 0) { return; }
                require(observation.prepared.size() == 64, "Not all preparations became ready");
                require(records.stream().allMatch(record -> journal.find(record.transferId()).orElse(null) == record),
                        "Preparation changed the journal");
                // Change phase on the following tick, preserving the final measured readiness tick.
                if (drainedAt < 0) { drainedAt = phaseTicks; return; }
                drainedAt = -1; phase("WARMUP");
            }
            case "WARMUP" -> {
                // Fixture-only forcing is excluded. It prevents ticket expiry from turning a fresh proof into preparation.
                for (int count = 0; count < 8 && warmColumn < columns.size(); count++) {
                    var column = columns.get(warmColumn++);
                    server.overworld().setChunkForced(column.x, column.z, true);
                    server.overworld().getChunk(column.x, column.z);
                }
                if (warmColumn < columns.size() || !records.stream().allMatch(
                        record -> RocketTransferEntities.recoveryEndsAlreadyLoaded(server, record))) { return; }
                if (drainedAt < 0) { drainedAt = phaseTicks; return; }
                if (phaseTicks - drainedAt < 100) { return; }
                drainedAt = -1; offer();
            }
            case "OFFERED" -> {
                if (phaseTicks < 2) { return; }
                phase("PROOF");
                for (int i : offered) {
                    var player = players.get(i);
                    int result = observation.timed("entry.confirm", () -> server.getCommands().performPrefixedCommand(
                            player.createCommandSourceStack(), "arce rocket-disassembly confirm " + player.token));
                    require(result == 1, "Confirmation rejected for " + i + " " + player.lastActionBar());
                    observation.attach(coordinator, records.get(i).logicalRocketId(), i);
                }
                require(coordinator.size() == offered.size(), "Fresh requests did not enter the queue");
                observation.event(-1, "queue_admitted", coordinator.size());
            }
            case "PROOF" -> {
                if (coordinator.size() > 0) { return; }
                if (drainedAt < 0) { drainedAt = phaseTicks; return; }
                drainedAt = -1; phase("DRAIN");
            }
            case "DRAIN" -> {
                // Preserve expired/refused requests as outcomes. Never extend their 200-tick consent.
                if (phaseTicks < 200 || !RocketRuntime.installedTransfers().landedDisassemblies().logicalIds().isEmpty()) { return; }
                if (observation.handedOff.size() == 64) { phase("DONE"); return; }
                roundsWithoutProgress = observation.handedOff.size() == roundStartProofs ? roundsWithoutProgress + 1 : 0;
                require(roundsWithoutProgress < 2 && observation.round < 32, "Fresh requests made no bounded proof progress");
                offer();
            }
            default -> throw new IllegalStateException("Unknown phase " + observation.phase);
        }
    }

    private void offer() {
        offered.clear(); observation.round++; roundStartProofs = observation.handedOff.size();
        phase("OFFERED");
        for (int i = 0; i < records.size(); i++) {
            if (observation.handedOff.containsKey(records.get(i).logicalRocketId())) { continue; }
            require(rockets.get(i).isAlive(), "Unproved destination vanished");
            var player = players.get(i); player.token = null;
            RocketRuntime.requestDisassembly(player, rockets.get(i));
            require(player.token != null, "Fresh offer missing for " + i + " " + player.lastActionBar());
            offered.add(i);
        }
    }

    private void seed(int index) {
        var level = server.overworld();
        var source = new BlockPos(4096 + index % 8 * 80, 100, 4096 + index / 8 * 80);
        var target = new BlockPos(8192 + index % 8 * 80, 100, 8192 + index / 8 * 80);
        var initial = RocketSourceRetirementFixtures.countdown(level, source, target, List.of(), 0).record();
        var snapshot = W197MaximumFixture.maximumSnapshot(initial.sourceSnapshot());
        var arrival = snapshot.relocated(initial.destinationSnapshot().snapshotId(), level.dimension().location(),
                initial.destinationSnapshot().sourceOrigin(), snapshot.createdAtGameTime());
        for (var end : List.of(snapshot, arrival)) {
            require(RocketDiscoveryColumns.of(end).count() == 16 && new SealedPlanAdmission(end).allowed(), "Wrong geometry");
            require(RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(end)) == 1_048_576, "Wrong byte ceiling");
            columns.addAll(RocketDiscoveryColumns.of(end).columns());
        }
        var column = new ChunkPos(target); level.setChunkForced(column.x, column.z, true); level.getChunk(column.x, column.z);
        var rocket = Objects.requireNonNull(ModEntities.ROCKET.get().create(level));
        rocket.initializeTransferred(arrival, initial.logicalRocketId(), initial.ownerId(), initial.destinationFlightData().land(level.getGameTime()));
        RocketTransferEntities.positionAtOrigin(rocket, arrival.sourceOrigin());
        require(level.addFreshEntity(rocket), "Fixture entity refused");
        var record = RocketTransferRecord.create(initial.transferId(), initial.logicalRocketId(), initial.ownerId(),
                initial.sourceEntityId(), snapshot, arrival, initial.sourceFlightData(), initial.destinationFlightData(),
                initial.requiredFuel(), initial.createdAtGameTime()).destinationSpawned(rocket.getUUID())
                .advance(RocketTransferPhase.PASSENGERS_TRANSFERRED).advance(RocketTransferPhase.SOURCE_REMOVED)
                .advance(RocketTransferPhase.COMMITTED);
        journal.put(record); RocketSourceRetirementFixtures.settled(RocketRuntime.installedTransfers()).add(record.transferId());
        records.add(record); rockets.add(rocket);
        var player = new RocketLandedDisassemblyGameTestSupport.Requester(level, record.ownerId());
        player.setPos(rocket.getX() + 2, rocket.getY(), rocket.getZ()); players.add(player);
    }

    private static void require(boolean condition, String message) { if (!condition) { throw new IllegalStateException(message); } }

    private void finish(String outcome) {
        if (done) { return; } done = true;
        try {
            var root = server.getWorldPath(LevelResource.ROOT);
            Files.write(root.resolve("w197-ticks.csv"), observation.ticks);
            Files.write(root.resolve("w197-steps.csv"), observation.steps);
            Files.write(root.resolve("w197-proofs.csv"), observation.proofs);
            Files.write(root.resolve("w197-events.csv"), observation.events);
            var result = new LinkedHashMap<String, Object>();
            result.put("outcome", outcome); result.put("failure", failure); result.put("phase", observation.phase);
            result.put("queue", records.size()); result.put("peak_pending", observation.peakPending);
            result.put("peak_active", observation.peakActive); result.put("prepared", observation.prepared.size());
            result.put("handed_off", observation.handedOff.size()); result.put("rounds", observation.round);
            result.put("confirmed_captures", observation.confirmedCaptures); result.put("refused_captures", observation.refusedCaptures);
            result.put("blocks", 2048); result.put("volume", 32768); result.put("snapshot_bytes", 1048576);
            result.put("payloads", 128); result.put("columns_per_end", 16); result.put("sealed_plan_slots", 20);
            Files.writeString(root.resolve("w197-result.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));
        } catch (Exception error) { throw new IllegalStateException(error); }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W197_COST_DONE outcome={}", outcome); server.halt(false);
    }

    @SubscribeEvent public void stopping(ServerStoppingEvent event) { if (!done) { finish("EXTERNAL_STOP"); } }
}
