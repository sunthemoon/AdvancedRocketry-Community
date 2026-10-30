package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.command.ReleaseTestPlayers;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.RocketFlightConsoleFixtures;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Observer-only native campaign. Included only by the W206 committed-source diagnostic export. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class W206NativeWarmup {
    private static final TicketType<UUID> SOURCE = TicketType.create("w206_source_only", UUID::compareTo);
    private final MinecraftServer server;
    private final ServerLevel earth, moon;
    private final String mode = System.getProperty("advancedrocketrycommunity.w206Mode", "flight");
    private final String shape = System.getProperty("advancedrocketrycommunity.w206Shape", "contained");
    private final boolean timingOnly = Boolean.getBoolean("advancedrocketrycommunity.w212TimingOnly");
    private final int repetitions = Integer.getInteger("advancedrocketrycommunity.w212Repetitions", 1);
    private final List<Map<String, Object>> rounds = new ArrayList<>();
    private final UUID key = UUID.randomUUID();
    private final Gson gson = new GsonBuilder().serializeNulls().create();
    private final List<RocketEntity> rockets = new ArrayList<>();
    private final Map<String, Object> result = new LinkedHashMap<>();
    private final Map<UUID, String> states = new HashMap<>();
    private final Map<UUID, Long> abandoned = new HashMap<>();
    private final Set<String> loadedUnion = new TreeSet<>(), ticketUnion = new TreeSet<>();
    private final ConcurrentLinkedQueue<Map<String, Object>> checkpoints = new ConcurrentLinkedQueue<>();
    private final BufferedWriter ticks, events, snapshots;
    private final List<Map<String, Object>> tickRows = new ArrayList<>(4001);
    private final Runnable unsubscribe;
    private final Path root;
    private ResourceEscrowSavedData data;
    private RocketTransferSavedData journal;
    private StationState station;
    private ServerPlayer player;
    private RocketWarmupRegistry.Warmup first;
    private RocketTransferRecord record;
    private UUID launch;
    private String phase = "SETUP", lastFlight = "";
    private int age, phaseAt, callbackCount, measuredRound;
    private long endStart, requestNanos, transitionObserverNanos;
    private boolean done, requested, seenF2, handover, landingWitness;
    private boolean resetLoaded, resetRemoved;

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("advancedrocketrycommunity.w206")) { return; }
        EscrowTestEndpoints.requireEnabled(event.getServer());
        try { MinecraftForge.EVENT_BUS.register(new W206NativeWarmup(event.getServer())); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private W206NativeWarmup(MinecraftServer server) throws Exception {
        this.server = server; earth = server.overworld(); moon = server.getLevel(CelestialIds.MOON_LEVEL);
        require(repetitions >= 1 && repetitions <= 4, "Diagnostic repetitions outside 1..4");
        root = server.getWorldPath(LevelResource.ROOT);
        ticks = Files.newBufferedWriter(root.resolve("w206-ticks.jsonl"));
        events = Files.newBufferedWriter(root.resolve("w206-events.jsonl"));
        snapshots = Files.newBufferedWriter(root.resolve("w206-columns.jsonl"));
        unsubscribe = W206NativeFixtures.observe(server, checkpoints);
        result.put("mode", mode); result.put("shape", shape);
        result.put("transition_mode", timingOnly ? "timing-only" : "full");
        result.put("expected_columns", W206Observation.candidate(shape, 8, 8).count());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST) public void beginEnd(TickEvent.ServerTickEvent event) {
        if (!done && event.phase == TickEvent.Phase.END) { endStart = System.nanoTime(); }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST) public void tick(TickEvent.ServerTickEvent event) {
        if (done || event.phase != TickEvent.Phase.END) { return; }
        String measuredPhase = phase;
        measuredRound = rounds.size();
        boolean work = requested || allocations() > 0 || journal != null && !journal.entries().isEmpty();
        requested = false; callbackCount = 0; transitionObserverNanos = 0;
        try {
            require(++age <= 4000, "External 4000-tick observation ceiling reached; not a product timeout");
            step();
            var delivered = new ArrayList<Map<String, Object>>();
            while (!checkpoints.isEmpty()) { var point = checkpoints.remove(); write(events, point); delivered.add(point); }
            callbackCount = W206Observation.ownerCallbacks(delivered, server.getTickCount());
            observeWarmups();
            work |= requested || allocations() > 0 || journal != null && !journal.entries().isEmpty();
            long observationStart = System.nanoTime();
            for (var level : server.getAllLevels()) {
                String dimension = level.dimension().location().toString();
                for (var row : W206Observation.chunks(level)) {
                    if (Boolean.TRUE.equals(row.get("full"))) { loadedUnion.add(dimension + ":" + row.get("x") + ":" + row.get("z")); }
                }
                for (var row : W206Observation.tickets(level)) { ticketUnion.add(row.dimension() + ":" + row.x() + ":" + row.z() + ":" + row.type()); }
            }
            long observed = System.nanoTime() - observationStart;
            captureTick(requested ? "REQUEST" : measuredPhase, work, observed);
            if (result.containsKey("outcome")) { finish(); }
        } catch (Exception failure) {
            result.put("outcome", "FAILED"); result.put("error", failure.toString());
            // A failed cold-generation/launch tick is cost evidence too, not a sample to discard.
            try { captureTick(requested ? "REQUEST" : measuredPhase, true, 0); }
            catch (Exception writeFailure) { failure.addSuppressed(writeFailure); }
            AdvancedRocketryCommunity.LOGGER.error("ARCE_W206_FAILED", failure);
            try { snapshot("FAILURE"); finish(); } catch (Exception error) { throw new IllegalStateException(error); }
        }
    }

    private void captureTick(String measuredPhase, boolean work, long observed) throws Exception {
        long recorderStart = System.nanoTime();
        long end = System.nanoTime() - endStart, core = server.tickTimes[server.getTickCount() % server.tickTimes.length];
        var row = new LinkedHashMap<String, Object>(Map.of("tick", server.getTickCount(), "phase", measuredPhase, "core_ns", core,
                "end_ns", end, "tick_ns", W206Observation.fullTick(core, end), "work", work,
                "callbacks", callbackCount, "column_observer_ns", observed, "epoch_ms", System.currentTimeMillis()));
        row.put("transition_observer_ns", transitionObserverNanos);
        row.put("window_start_ns", endStart - core);
        row.put("window_end_ns", endStart + end);
        row.put("round", measuredRound);
        require(tickRows.size() < 4001, "Tick recorder bound exceeded");
        tickRows.add(row);
        // Only this final clock/read and map insertion follow the measured recorder tail.
        row.put("recorder_tail_ns", System.nanoTime() - recorderStart);
    }

    private Collection<RocketWarmupRegistry.Warmup> live() { return RocketRuntime.installedTransfers().warmups.registry().live(); }
    private int allocations() { return RocketRuntime.installedTransfers().warmups.registry().allocationCount(); }
    private boolean preparing(RocketEntity rocket) {
        return RocketRuntime.installedTransfers().warmups.terrain.contains(rocket.assemblyTransactionId().orElseThrow());
    }
    private RocketWarmupRegistry.Warmup warm(RocketEntity rocket) {
        return RocketRuntime.installedTransfers().warmups.registry().warmup(rocket.assemblyTransactionId().orElseThrow()).orElse(null);
    }
    private void event(String kind, Map<String, Object> fields) throws Exception {
        var row = new LinkedHashMap<String, Object>(fields); row.put("kind", kind);
        row.put("tick", server.getTickCount()); row.put("ns", System.nanoTime()); write(events, row);
    }
    private void phase(String next) throws Exception { event("PHASE", Map.of("from", phase, "to", next)); phase = next; phaseAt = age; snapshot(next); }
    private void snapshot(String label) throws Exception {
        long start = System.nanoTime();
        if (timingOnly) {
            // The paired diagnostic omits only transition JSON/column snapshots. Assertions remain live.
            transitionObserverNanos += System.nanoTime() - start;
            return;
        }
        var levels = new LinkedHashMap<String, Object>();
        for (var level : server.getAllLevels()) {
            levels.put(level.dimension().location().toString(), Map.of("tickets", W206Observation.tickets(level), "chunks", W206Observation.chunks(level)));
        }
        write(snapshots, Map.of("label", label, "tick", server.getTickCount(), "levels", levels));
        transitionObserverNanos += System.nanoTime() - start;
    }
    private void observeWarmups() throws Exception {
        for (var warm : live()) {
            String identity = warm.request() + ":" + warm.state();
            if (!identity.equals(states.put(warm.logical(), identity))) {
                event(warm.state() == RocketWarmupRegistry.State.WARMING ? "STARTED" : warm.state().name(),
                        Map.of("logical", warm.logical().toString(), "request", warm.request().toString(),
                                "started", warm.started(), "lifetime", warm.lifetime(), "columns", warm.columns().toString(),
                                "level", warm.level().toString(), "region", warm.region().toString()));
                snapshot("WARMUP_" + warm.state());
            }
        }
    }

    private void step() throws Exception {
        if (phase.equals("REPEAT_SETUP")) { repeatSetup(); return; }
        if (mode.equals("seed")) { seed(); return; }
        if (mode.equals("recover") && !Boolean.TRUE.equals(result.get("restart_source_ready"))) { recover(); return; }
        if (phase.equals("SETUP")) { setup(); return; }
        if (phase.equals("BASELINE")) {
            if (age - phaseAt < 100) { return; }
            if (mode.equals("capacity")) { capacity(); return; }
            coldRequest(); return;
        }
        if (mode.equals("capacity")) { lifecycle(); return; }
        if (mode.equals("restart")) {
            var rocket = rockets.get(0); var logical = rocket.assemblyTransactionId().orElseThrow();
            require(preparing(rocket) || warm(rocket) != null, "Restart cut lost live preparation/warm-up");
            result.put("warmup_before_stop", logical.toString());
            Files.writeString(root.resolve("w206-restart-id.txt"), logical.toString());
            result.put("outcome", "RESTART_CUT"); return;
        }
        if (mode.equals("replace")) { replace(); return; }
        flight();
    }

    private void setup() throws Exception {
        if (data == null) {
            data = ResourceEscrowSavedData.forTests(server); data.bind(server); journal = RocketTransferSavedData.get(server);
            // The synthetic empty ledger is otherwise never dirty before F1. Establish its checked
            // durable identity in SETUP, so restart tests reload it rather than create a new ledger.
            if (mode.equals("restart")) { data.setDirty(); data.bind(server).autosave(); }
            int count = mode.equals("capacity") ? 10 : 1;
            var extent = W206Observation.extent(shape);
            for (int i = 0; i < count; i++) {
                var at = new BlockPos(8200 + i * 320 - extent[0] / 2, 100, 8200 - extent[1] / 2);
                var rocket = W206NativeFixtures.rocket(earth, at, data.ledger().id(), shape); rockets.add(rocket);
                for (var column : RocketDiscoveryColumns.of(rocket.snapshot().orElseThrow()).columns()) {
                    earth.getChunkSource().addRegionTicket(SOURCE, column, 2, key);
                }
            }
            if (mode.equals("capacity") || mode.equals("replace")) {
                station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                        .create(server, UUID.randomUUID(), "W206 cold station", CelestialIds.EARTH_ID, false).station().orElseThrow();
            }
        }
        if (!rockets.stream().allMatch(rocket -> RocketTransferEntities.endAlreadyLoaded(server, rocket.snapshot().orElseThrow()))) { return; }
        for (var rocket : rockets) { if (!rocket.isAddedToWorld()) { require(earth.addFreshEntity(rocket), "Source publication failed"); return; } }
        if (age < 100 || !journal.retryChecked(server) || !data.bind(server).writerIdle()) { return; }
        if (mode.equals("restart")) { require(data.ledger().writerGeneration() > 0, "Restart fixture ledger was not acknowledged"); }
        if (mode.equals("capacity")) {
            player = ReleaseTestPlayers.place(server, UUID.randomUUID(), "W206Pilot", earth, new Vec3(8192.5, 103, 8192.5));
        }
        phase("BASELINE");
    }

    private void coldRequest() throws Exception {
        var predicted = W206Observation.candidate(shape, 8, 8);
        boolean cold = predicted.columns().stream().noneMatch(c -> moon.areEntitiesLoaded(c.toLong()) && moon.isPositionEntityTicking(c.getWorldPosition()));
        boolean noTicket = W206Observation.noInfluence(predicted.columns(), W206Observation.tickets(moon));
        result.put("cold", cold); result.put("no_other_ticket", noTicket);
        var terrain = root.resolve("dimensions/advancedrocketrycommunity/moon/region");
        boolean generated = false;
        if (Files.isDirectory(terrain)) {
            try (var files = Files.list(terrain)) { generated = files.anyMatch(path -> path.getFileName().toString().endsWith(".mca")); }
        }
        result.put("new_terrain", !generated);
        require(cold && noTicket, "Destination cold/no-other-ticket qualification failed");
        snapshot("BEFORE_FIRST_REQUEST");
        var rocket = rockets.get(0); var before = rocket.flightData().orElseThrow();
        phase("REQUEST");
        var answer = request(rocket, false); first = warm(rocket);
        result.put("first_code", answer.code().name());
        boolean unchanged = before.equals(rocket.flightData().orElseThrow()) && journal.entries().isEmpty()
                && data.ledger().armedFlights().isEmpty() && RocketEscrowGate.idle(rocket);
        result.put("source_unchanged", unchanged);
        require(unchanged, "Cold request crossed F1");
        require(answer.code() == RocketFlightRequestCode.DESTINATION_LOADING && (first != null || preparing(rocket)),
                "Cold entry refused before bounded preparation: " + answer);
        result.put("logical", rocket.assemblyTransactionId().orElseThrow().toString());
        if (first == null) {
            require(RocketRuntime.installedTransfers().warmups.registry().reservations(UUID.randomUUID()).isEmpty(),
                    "Unresolved terrain acquired a reservation");
            result.put("unresolved_has_no_reservation", true); phase("TERRAIN"); return;
        }
        selected();
    }

    private void selected() throws Exception {
        var predicted = W206Observation.candidate(shape, 8, 8);
        result.put("selected_columns", first.columns().size());
        result.put("selected_region", first.region().toString()); result.put("logical", first.logical().toString());
        result.put("selected_ticket_columns", W206Observation.owned(server, "arce_rocket_warmup", first.logical()));
        require(first.columns().size() == predicted.count(), "Selected geometry differs from declared cohort");
        var expectedTickets = first.columns().stream().map(c -> first.level() + ":" + c.x + ":" + c.z).sorted().toList();
        require(W206Observation.owned(server, "arce_rocket_warmup", first.logical()).equals(expectedTickets), "Warm-up ticket set differs from server selection");
        phase("WARMING");
    }

    private RocketFlightRequestResult request(RocketEntity rocket, boolean stationTarget) throws Exception {
        requested = true; UUID id = UUID.randomUUID(); long start = System.nanoTime();
        var answer = stationTarget ? RocketRuntime.requestAdminStationFlight(rocket, station.stationId(), id)
                : RocketRuntime.requestAdminFlight(rocket, RocketDestination.MOON, id);
        event("REQUEST", Map.of("logical", rocket.assemblyTransactionId().orElseThrow().toString(), "request", id.toString(),
                "code", answer.code().name(), "duration_ns", System.nanoTime() - start, "station", stationTarget));
        launch = id; requestNanos = start; return answer;
    }

    private void flight() throws Exception {
        var rocket = rockets.get(0); var warm = warm(rocket);
        if (phase.equals("TERRAIN")) {
            require(journal.entries().isEmpty() && data.ledger().armedFlights().isEmpty() && RocketEscrowGate.idle(rocket),
                    "Terrain preparation crossed F1");
            if (warm == null) { require(preparing(rocket), "Terrain preparation ended before selection"); return; }
            first = warm; selected(); return;
        }
        if (phase.equals("WARMING")) {
            require(warm != null, "Warm-up expired or disappeared before READY");
            if (warm.state() != RocketWarmupRegistry.State.READY) { return; }
            // Observe READY before a synchronous relaunch changes it to BOUND.
            observeWarmups(); result.put("ready_ticks", server.getTickCount() - first.started());
            phase("RELAUNCH");
            var answer = request(rocket, false);
            require(answer.code() == RocketFlightRequestCode.JOURNAL_PENDING, "READY relaunch failed: " + answer);
            require(warm(rocket) != null && warm(rocket).state() == RocketWarmupRegistry.State.BOUND, "Relaunch did not bind");
            phase("F1_F2"); return;
        }
        var current = journal.find(launch).orElse(null);
        if (current != null) {
            record = current;
            var entity = moon.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
            if (!landingWitness && entity instanceof RocketEntity landed
                    && landed.flightData().orElseThrow().state() == RocketFlightState.LANDED) {
                var fuel = landed.flightData().orElseThrow().fuel();
                var expected = record.sourceFlightData().fuel().debit(record.sourceFlightData().plan().orElseThrow()).state();
                require(fuel.committedDebits().equals(List.of(launch)) && fuel.totalAmount() == expected.totalAmount()
                        && landed.snapshot().orElseThrow().contentHash().equals(record.destinationSnapshot().contentHash()),
                        "Loaded landed survivor changed its exact debit or snapshot");
                landingWitness = true; result.put("landing_witness", true);
                event("LANDED_WITNESS", Map.of("entity", entity.getUUID().toString(), "fuel", fuel.totalAmount(),
                        "snapshot", landed.snapshot().orElseThrow().contentHash()));
            }
            if (!seenF2 && rocket.flightData().orElseThrow().state() == RocketFlightState.COUNTDOWN && journal.acknowledgedCurrent()) {
                seenF2 = true; result.put("f2", true); event("F2", Map.of("latency_ns", System.nanoTime() - requestNanos));
            }
            if (!handover && warm == null) {
                var expected = new TreeSet<String>();
                for (var end : List.of(record.sourceSnapshot(), record.destinationSnapshot())) {
                    for (var c : RocketDiscoveryColumns.of(end).columns()) { expected.add(end.sourceDimension() + ":" + c.x + ":" + c.z); }
                }
                require(W206Observation.owned(server, "arce_rocket_flight", launch).equals(List.copyOf(expected))
                        && W206Observation.owned(server, "arce_rocket_warmup", first.logical()).isEmpty(), "Handover ticket mismatch");
                handover = true; result.put("handover", true); phase("FLIGHT");
            }
            if (!current.phase().name().equals(lastFlight)) {
                lastFlight = current.phase().name(); event("FLIGHT_PHASE", Map.of("phase", lastFlight)); snapshot(lastFlight);
            }
            return;
        }
        if (record == null) { require(warm != null, "F1 ended without F2"); return; }
        require(seenF2 && handover, "Flight ended without observed F2/handover");
        if (data.ledger().armedFlights().stream().anyMatch(a -> a.transfer().equals(launch))) { return; }
        var arrival = moon.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
        var flightTickets = W206Observation.owned(server, "arce_rocket_flight", launch);
        var warmTickets = W206Observation.owned(server, "arce_rocket_warmup", first.logical());
        result.put("tickets_after_f8", flightTickets); result.put("warmups_after_f8", warmTickets);
        result.put("survivor_still_loaded_after_f8", arrival != null);
        require(W206Observation.released(landingWitness, arrival != null, flightTickets, warmTickets),
                "F8 lacks a landing witness or retained tickets; chunk eviction is independent");
        result.put("f8", true); event("F8", Map.of("latency_ns", System.nanoTime() - requestNanos));
        phase("F8_RELEASED"); result.put("outcome", "MEASURED");
        if (rounds.size() + 1 < repetitions) {
            rounds.add(new LinkedHashMap<>(result));
            result.remove("outcome"); phase("REPEAT_SETUP");
        }
    }

    /** Separate diagnostic reset after proven F8; never used by the eight first-flight comparisons. */
    private void repeatSetup() throws Exception {
        var end = record.destinationSnapshot().sourceOrigin();
        if (!resetLoaded) {
            // Reload only the completed fixture's end to remove its terminal survivor, then let it evict.
            // This is setup, not preparation or measured product work, and adds no persistent/support ticket.
            moon.getChunk(end.x() >> 4, end.z() >> 4); resetLoaded = true; return;
        }
        if (!resetRemoved) {
            var arrival = moon.getEntity(record.nativeTransfer().orElseThrow().armed().destination());
            if (arrival == null) { return; }
            require(arrival instanceof RocketEntity landed && landed.flightData().orElseThrow().state() == RocketFlightState.LANDED,
                    "Repeated-flight reset is not terminal");
            arrival.discard(); resetRemoved = true; return;
        }
        if (!W206Observation.chunks(moon).isEmpty() || !journal.retryChecked(server) || !data.bind(server).writerIdle()) { return; }
        var extent = W206Observation.extent(shape);
        rockets.clear(); rockets.add(W206NativeFixtures.rocket(earth,
                new BlockPos(8200 - extent[0] / 2, 100, 8200 - extent[1] / 2), data.ledger().id(), shape));
        require(earth.addFreshEntity(rockets.get(0)), "Repeated source publication failed");
        seenF2 = false; handover = false; landingWitness = false; record = null; first = null; lastFlight = "";
        resetLoaded = false; resetRemoved = false; states.clear();
        result.clear(); result.put("mode", mode); result.put("shape", shape);
        result.put("transition_mode", timingOnly ? "timing-only" : "full");
        result.put("expected_columns", W206Observation.candidate(shape, 8, 8).count());
        phase("BASELINE");
    }

    private void seed() throws Exception {
        if (age == 1) { phase("SEED"); }
        // Explicit preparation process only: its timings never enter a measured cold restart.
        if (age == 2) {
            for (var c : W206Observation.candidate(shape, 8, 8).columns()) {
                for (int x = -3; x <= 3; x++) { for (int z = -3; z <= 3; z++) { moon.getChunk(c.x + x, c.z + z); } }
            }
        }
        if (age >= 20) { require(server.saveEverything(false, true, true), "Seed save refused"); result.put("outcome", "GENERATED_SEED"); }
    }

    private void recover() throws Exception {
        phase = "RECOVER";
        var logical = UUID.fromString(Files.readString(root.resolve("w206-restart-id.txt")).trim());
        require(allocations() == 0 && W206Observation.owned(server, "arce_rocket_warmup", logical).isEmpty(), "Preparation/warm-up persisted across JVMs");
        require(RocketTransferSavedData.get(server).entries().isEmpty(), "Unbound warm-up launched on restart");
        if (age == 1) { snapshot("FIRST_RESTART_TICK"); }
        if (age == 100) {
            snapshot("RESTART_100"); result.put("restart_absent", true);
            // Recover only the original source; the destination gets no fixture/support ticket.
            for (var c : W206Observation.candidate(shape, 8200, 8200).columns()) {
                earth.getChunkSource().addRegionTicket(SOURCE, c, 2, key);
            }
            data = ResourceEscrowSavedData.existing(server);
            require(data != null, "Restart fixture lost its original ledger");
            data.bind(server); journal = RocketTransferSavedData.get(server);
        }
        if (age <= 100) { return; }
        for (var entity : earth.getAllEntities()) {
            if (entity instanceof RocketEntity rocket && rocket.assemblyTransactionId().filter(logical::equals).isPresent()
                    && RocketTransferEntities.endAlreadyLoaded(server, rocket.snapshot().orElseThrow())
                    && journal.retryChecked(server) && data.bind(server).writerIdle()) {
                require(data.ledger().id().equals(rocket.escrowSection().ledger()), "Restart source ledger identity changed");
                rockets.add(rocket); result.put("restart_source_ready", true); result.put("restart_logical", logical.toString());
                result.put("restart_ledger", data.ledger().id().toString());
                snapshot("RESTART_SOURCE_READY"); phase("BASELINE"); return;
            }
        }
    }

    private void capacity() throws Exception {
        require(W206Observation.tickets(moon).isEmpty(), "Capacity Moon was ticketed before the first request");
        require(W206Observation.chunks(moon).isEmpty(), "Capacity Moon was loaded before the first request");
        result.put("cold", true); result.put("no_other_ticket", true);
        phase("CAPACITY");
        for (int i = 0; i < 3; i++) {
            var rocket = rockets.get(i); player.teleportTo(earth, rocket.getX(), rocket.getY() + 2, rocket.getZ(), 0, 0);
            requested = true; UUID request = UUID.randomUUID();
            require(RocketFlightConsoleFixtures.send(player, rocket, () -> RocketRuntime.requestFlightIntent(player,
                    rocket.getId(), RocketFlightAction.LAUNCH, RocketDestination.MOON, request)), "Player console refused");
            event("PLAYER_REQUEST", Map.of("index", i, "request", request.toString()));
            require(i < 2 ? warm(rocket) != null || preparing(rocket) : warm(rocket) == null && !preparing(rocket), "Per-player cap changed");
        }
        result.put("per_player_cap", allocations()); require(allocations() == 2, "Two player allocations not observed");
        for (int i = 3; i < 9; i++) { require(request(rockets.get(i), false).code() == RocketFlightRequestCode.DESTINATION_LOADING, "Cap allocation failed"); }
        require(allocations() == 8, "Eight preparation/warm-up slots not live"); result.put("peak_warmups", allocations());
        result.put("initial_selected_warmups", live().size());
        require(request(rockets.get(9), true).code() == RocketFlightRequestCode.DESTINATION_LOADING_LIMIT
                && warm(rockets.get(9)) == null && !preparing(rockets.get(9))
                && W206Observation.owned(server, "arce_rocket_warmup", rockets.get(9).assemblyTransactionId().orElseThrow()).isEmpty(), "Ninth was not refused");
        result.put("ninth_refused", true); observeWarmups(); snapshot("EIGHT_AND_NINTH");
        for (int i = 4; i < 9; i++) { abandoned.put(rockets.get(i).assemblyTransactionId().orElseThrow(), (long) server.getTickCount()); }
        server.getPlayerList().remove(player); player = null; rockets.get(3).discard(); phase("EXPIRY");
    }

    private void lifecycle() throws Exception {
        if (player != null) { ReleaseTestPlayers.pump(server); }
        for (var started : abandoned.entrySet()) {
            var current = RocketRuntime.installedTransfers().warmups.registry().warmup(started.getKey()).orElse(null);
            boolean terrain = RocketRuntime.installedTransfers().warmups.terrain.contains(started.getKey());
            if (server.getTickCount() - started.getValue() < 600) {
                require(terrain || current != null && current.started() == started.getValue() && current.lifetime() == 600,
                        "An abandoned allocation ended early or changed its lifetime");
            } else { require(current == null && !terrain, "An abandoned allocation outlived its captured deadline"); }
        }
        if (age - phaseAt == 2) {
            for (int i : List.of(0, 1, 3)) {
                require(warm(rockets.get(i)) == null && !preparing(rockets.get(i)) && W206Observation.owned(server, "arce_rocket_warmup", rockets.get(i).assemblyTransactionId().orElseThrow()).isEmpty(), "Disconnect/removal ticket leak");
            }
            result.put("disconnect_removal", true); snapshot("DISCONNECT_REMOVAL");
        }
        if (age - phaseAt < 602) { return; }
        require(allocations() == 0, "600-tick expiry retained allocations");
        for (var rocket : rockets) { require(W206Observation.owned(server, "arce_rocket_warmup", rocket.assemblyTransactionId().orElseThrow()).isEmpty(), "Expired ticket retained"); }
        result.put("expiry", true); snapshot("EXPIRED"); result.put("outcome", "LIFECYCLE_MEASURED");
    }

    private void replace() throws Exception {
        if (!phase.equals("REPLACING")) {
            require(request(rockets.get(0), true).code() == RocketFlightRequestCode.DESTINATION_LOADING, "Replacement refused");
            phase("REPLACING");
        }
        var replacement = warm(rockets.get(0));
        if (replacement == null) { require(preparing(rockets.get(0)), "Replacement preparation disappeared"); return; }
        require(replacement.level().equals(CelestialIds.SPACE_LEVEL.location()), "Replacement did not change Level");
        require(W206Observation.owned(server, "arce_rocket_warmup", replacement.logical()).stream().noneMatch(s -> s.startsWith(CelestialIds.MOON_LEVEL.location() + ":")), "Old destination tickets retained");
        result.put("replacement", true); snapshot("REPLACED"); result.put("outcome", "REPLACEMENT_MEASURED");
    }

    private void finish() throws Exception {
        done = true; unsubscribe.run();
        // Export outside the measurement; no tick-row JSON or filesystem IO in captureTick.
        for (var row : tickRows) { write(ticks, row); }
        result.put("timing_boundary", "native core + END through capture entry; buffered recorder tail reported separately; final export excluded");
        result.put("loaded_columns_union", loadedUnion); result.put("ticketed_columns_union", ticketUnion);
        result.put("ticks", age); result.put("final_phase", phase);
        result.put("completed_prior_rounds", rounds);
        Files.writeString(root.resolve("w206-result.json"), new GsonBuilder().setPrettyPrinting().create().toJson(result));
        ticks.close(); events.close(); snapshots.close();
        server.halt(false);
    }
    private void write(BufferedWriter stream, Object value) throws Exception { stream.write(gson.toJson(value)); stream.newLine(); }
    private static void require(boolean value, String message) { if (!value) { throw new IllegalStateException(message); } }
}
