package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.RocketFluidCargoBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightPlan;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPropulsionDebit;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import java.util.Set;
import java.util.UUID;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * X03-V (W367-DESIGN section 5.1): the gated verification mode for a repaired copy whose stale transfer record is gone.
 * It observes ({@code FF_X03_OBSERVE}), names the one device for one ordinary unload ({@code FF_X03_UNLOAD}), or requests
 * one flight through the production entry ({@code FF_X03_LAUNCH}). It is inert unless the release-test hooks, the
 * {@code verify} scenario and one of these three cases are all set.
 *
 * <p>It never creates, moves or edits a rocket, a device, a journal record or a ledger entry, never marks the ledger
 * dirty, never calls {@code RocketRuntime.recoverTransfer} and never sets a success state. It requests ordinary full
 * saves, and the journal's own checked baseline write through {@code retryChecked}, as every cargo cut does. The only
 * authority it grants is the named device switch of the unload case, which it withdraws when the ledger shows the
 * operation. It decides nothing about amounts: the runner and the independent oracle compare its lines with the files.
 */
final class EscrowCargoRepairVerifyProbe extends EscrowCargoCutProbe {
    static final String VERIFY = "verify";
    static final String OBSERVE = "FF_X03_OBSERVE", UNLOAD = "FF_X03_UNLOAD", LAUNCH = "FF_X03_LAUNCH";
    static final Set<String> CASES = Set.of(OBSERVE, UNLOAD, LAUNCH);
    /** The launch route, fixed by the runner: a {@link RocketDestination} name. Absent means the Moon, as the record described. */
    static final String ROUTE = "advancedrocketrycommunity.escrowVerifyRoute";
    /** Every step has this bound; the settle time is the protected restart's own 400 ticks, so the starts compare like for like. */
    static final int WAIT_TICKS = 6_000, SETTLE_TICKS = 400;
    /** A destination that is still warming up refuses without a flight; the request is repeated at most this often. */
    static final int REQUESTS = 20, REQUEST_INTERVAL_TICKS = 100;

    private final RocketDestination route;
    private ResourceEscrowSavedData data;
    private RocketEntity rocket;
    private RocketFluidCargoBlockEntity device;
    private UUID named, logical, request;
    private RocketFlightRequestCode code;
    private RocketPropulsionDebit debit;
    private ResourceLocation fromDimension;
    private RocketPosition fromOrigin;
    private long began = -1, since, nextRequest, requiredFuel = -1, fuelBefore = -1, epochBefore;
    private int step, requests, tankBefore;

    EscrowCargoRepairVerifyProbe(MinecraftServer server) {
        super(gated(server));
        route = LAUNCH.equals(name) ? route(System.getProperty(ROUTE)) : null;
    }

    /** The whole gate, checked before the base class takes its tickets. */
    private static MinecraftServer gated(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        String selectedCase = System.getProperty(CASE);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty(SCENARIO), selectedCase),
                "X03 verification needs the release-test hooks, the verify scenario and one X03 case");
        if (LAUNCH.equals(selectedCase)) { route(System.getProperty(ROUTE)); }
        return server;
    }

    static boolean accepts(String name) { return name != null && CASES.contains(name); }
    /** Hooks, the {@code verify} scenario and one of the three cases; nothing else selects this mode. */
    static boolean selected(boolean hooks, String scenario, String name) {
        return hooks && VERIFY.equals(scenario) && accepts(name);
    }
    /**
     * With the hooks on, exactly one of "the scenario is {@code verify}" and "the case is an X03 case" holds. Such a start
     * must stop before any probe is created, so the dispatch cannot reach the generic release fixture on a repaired world.
     */
    static boolean misrouted(boolean hooks, String scenario, String name) {
        return hooks && VERIFY.equals(scenario) != accepts(name);
    }
    static RocketDestination route(String value) {
        if (value == null || value.isEmpty()) { return RocketDestination.MOON; }
        for (var destination : RocketDestination.values()) {
            if (destination.name().equals(value)) { return destination; }
        }
        throw new IllegalArgumentException("Unknown X03 verification route");
    }
    /** SUCCESS, or JOURNAL_PENDING: the ordinary launch accepted the request and waits for its journal write. */
    static boolean launchAccepted(RocketFlightRequestCode code) {
        return code == RocketFlightRequestCode.SUCCESS || code == RocketFlightRequestCode.JOURNAL_PENDING;
    }
    /** Only a destination that is still loading is asked again; every other refusal fails the case. */
    static boolean launchRepeatable(RocketFlightRequestCode code, int requests) {
        return code == RocketFlightRequestCode.DESTINATION_LOADING && requests < REQUESTS;
    }
    static boolean expired(long now, long since) { return now - since > WAIT_TICKS; }

    /** The playerless Level stops block-entity ticks after 300 ticks, before our 400-tick settle ends. */
    static void keepUnloadTicking(String name, int step, Runnable resetEmptyTime) {
        if (UNLOAD.equals(name) && step >= 2 && step <= 4) { resetEmptyTime.run(); }
    }

    /** A quiet writer alone is insufficient while even a terminal flight row for this rocket survives. */
    static boolean launchSettled(EscrowLedger ledger, UUID logical, boolean writersQuiet) {
        return writersQuiet && ledger.armedFlights().stream().noneMatch(row -> logical.equals(row.logical()));
    }

    @Override void checkpoint(String label) { }

    @Override void tick() {
        if (done) { return; }
        long now = server.getTickCount();
        if (began < 0) { began = since = now; }
        EscrowTags.require(!expired(now, since), "X03 verification exceeded its bounded 6,000 ticks at step " + step);
        if (!loaded()) { return; }
        keepUnloadTicking(name, step, level::resetEmptyTime);
        if (data == null) { data = ResourceEscrowSavedData.existing(server); } // Load-only: this mode never creates a ledger.
        if (data == null) { return; }
        var journal = RocketTransferSavedData.get(server);
        var coordinator = data.bind(server);
        switch (step) {
            case 0 -> { if (now - began >= SETTLE_TICKS && quiet(journal, coordinator)) { save(); advance(1); } }
            case 1 -> { if (saved(journal, coordinator)) { observed(journal); } }
            case 2 -> { if (UNLOAD.equals(name)) { admit(); } else { request(now); } }
            case 3 -> { if (UNLOAD.equals(name)) { admitted(); } else { landed(journal, now); } }
            case 4 -> { if (settled(journal, coordinator)) { save(); advance(5); } }
            case 5 -> { if (saved(journal, coordinator)) { finished(journal); } }
            default -> { }
        }
    }

    private void advance(int next) { step = next; since = server.getTickCount(); }

    private boolean quiet(RocketTransferSavedData journal, EscrowCoordinator coordinator) {
        return journal.retryChecked(server) && coordinator.operational() && coordinator.writerIdle();
    }
    private boolean saved(RocketTransferSavedData journal, EscrowCoordinator coordinator) {
        return journal.acknowledgedCurrent() && coordinator.writerIdle();
    }

    /** The first observation of every case; the observation case ends here. */
    private void observed(RocketTransferSavedData journal) {
        var found = allRockets();
        rocket = found.size() == 1 ? found.get(0) : null;
        device = existingDevice(DEVICE);
        String line = observation(journal, found.size());
        if (OBSERVE.equals(name)) { result("OBSERVED", line); return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_VERIFY_OBSERVED cut={} {}", name, line);
        EscrowTags.require(rocket != null && device != null && device.root().isPresent(),
                "X03 functional verification needs exactly one rocket and the bound device");
        advance(2);
    }

    /** Names the one device through the existing release-test gate; the device's ordinary path then admits one unload. */
    private void admit() {
        named = id(device); tankBefore = tank(device); epochBefore = rocket.escrowSection().epoch();
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, named, true);
        advance(3);
    }

    /** Withdraws the switch as soon as the ledger shows the operation, as the seed fixture does. */
    private void admitted() {
        boolean recorded = data.ledger().records().stream().anyMatch(value -> value.device().identity().equals(named));
        if (!recorded && rocket.escrowSection().epoch() == epochBefore && tank(device) == tankBefore) {
            if (server.getTickCount() % 100 == 0) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_X03_UNLOAD_WAIT device={} status={} game_time={} players={} forced_chunks={}",
                        named, device.displayStatus(), level.getGameTime(), level.players().size(), level.getForcedChunks().size());
            }
            return;
        }
        EscrowCargoSwitches.forTests(server, EscrowOperation.Kind.FLUID_CARGO_UNLOAD, named, false);
        advance(4);
    }

    /** One flight request through the production entry; the flight itself is entirely the product's. */
    private void request(long now) {
        if (now < nextRequest) { return; }
        var flight = rocket.flightData().orElseThrow();
        logical = flight.logicalRocketId(); fromDimension = flight.currentDimension(); fromOrigin = flight.currentOrigin();
        fuelBefore = flight.fuel().totalAmount();
        request = UUID.randomUUID(); requests++;
        var answer = RocketRuntime.requestAdminFlight(rocket, route, request);
        code = answer.code(); requiredFuel = answer.requiredFuel();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_VERIFY_REQUEST cut={} route={} request={} attempt={} code={} required_fuel={}",
                name, route, request, requests, code, requiredFuel);
        if (launchAccepted(code)) { advance(3); return; }
        EscrowTags.require(launchRepeatable(code, requests), "X03 verification launch was refused: " + code);
        nextRequest = now + REQUEST_INTERVAL_TICKS;
    }

    /** Waits for the same logical rocket, landed somewhere other than where it stood; reads the debit the plan carried. */
    private void landed(RocketTransferSavedData journal, long now) {
        if (debit == null) {
            debit = journal.findByLogicalRocket(logical).flatMap(record -> record.sourceFlightData().plan())
                    .map(RocketFlightPlan::propulsionDebit).orElse(null);
        }
        if (now % 10 != 0) { return; }
        for (ServerLevel each : server.getAllLevels()) {
            for (var entity : each.getAllEntities()) {
                if (entity instanceof RocketEntity candidate && candidate.isAlive() && arrived(candidate.flightData().orElse(null))) {
                    rocket = candidate; advance(4); return;
                }
            }
        }
    }
    private boolean arrived(RocketFlightData flight) {
        return flight != null && logical.equals(flight.logicalRocketId()) && flight.state() == RocketFlightState.LANDED
                && !(fromDimension.equals(flight.currentDimension()) && fromOrigin.equals(flight.currentOrigin()));
    }

    /** The unload's retirement, or the journal and the ledger at rest after the landing. */
    private boolean settled(RocketTransferSavedData journal, EscrowCoordinator coordinator) {
        if (LAUNCH.equals(name)) { return launchSettled(data.ledger(), logical, quiet(journal, coordinator)); }
        return data.ledger().records().isEmpty() && section(device).markers().isEmpty() && rocket.escrowSection().markers().isEmpty()
                && coordinator.writerIdle();
    }

    private void finished(RocketTransferSavedData journal) {
        String line = observation(journal, allRockets().size());
        if (UNLOAD.equals(name)) {
            boolean open = EscrowRuntime.of(server).cargoSwitches.allowed(EscrowOperation.Kind.FLUID_CARGO_UNLOAD, named);
            result("UNLOADED", "named_device=" + named + " switch=" + (open ? "open" : "withdrawn") + " " + line);
            return;
        }
        var flight = rocket.flightData().orElseThrow(); var at = flight.currentOrigin();
        result("LANDED", "route=" + route + " request=" + request + " requests=" + requests + " code=" + code + " required_fuel=" + requiredFuel
                + " debit=" + (debit == null ? "unobserved" : "primary:" + debit.primary() + ",oxidizer:" + debit.oxidizer()
                        + ",revision:" + debit.revision() + ",resource:" + debit.resourceSignature() + ",catalog:" + debit.catalogSignature())
                + " fuel_before=" + fuelBefore + " fuel_after=" + flight.fuel().totalAmount()
                + " landing=" + flight.currentDimension() + "," + at.x() + "," + at.y() + "," + at.z() + " " + line);
    }

    /** What the server holds now: the fixture area's rockets, the selected rocket, the device, the journal and the ledger. */
    private String observation(RocketTransferSavedData journal, int inArea) {
        int everywhere = 0;
        for (ServerLevel each : server.getAllLevels()) {
            for (var entity : each.getAllEntities()) { if (entity instanceof RocketEntity) { everywhere++; } }
        }
        var ledger = data.ledger(); var text = new StringBuilder();
        text.append("rockets=").append(inArea).append(" loaded_rockets=").append(everywhere);
        if (rocket == null || !rocket.isAlive()) { text.append(" rocket=absent"); }
        else {
            var flight = rocket.flightData().orElse(null);
            text.append(" rocket=").append(cargoText(rocket)).append(" rocket_epoch=").append(rocket.escrowSection().epoch())
                    .append(" rocket_markers=").append(rocket.escrowSection().markers().size()).append(" rocket_id=").append(rocket.getUUID())
                    .append(" logical=").append(rocket.assemblyTransactionId().map(UUID::toString).orElse("none"))
                    .append(" flight=").append(flight == null ? "none" : flight.state().name())
                    .append(" fuel=").append(flight == null ? -1 : flight.fuel().totalAmount())
                    .append(" mutation_allowed=").append(RocketRuntime.resourceMutationAllowed(rocket));
        }
        if (device == null || device.isRemoved() || device.root().isEmpty()) { text.append(" device=absent"); }
        else {
            text.append(" device=").append(tank(device)).append(" device_epoch=").append(section(device).epoch())
                    .append(" device_markers=").append(section(device).markers().size()).append(" device_id=").append(id(device));
        }
        return text.append(" journal=").append(journal.entries().size()).append(" action_authority=").append(journal.actionAuthorityAvailable())
                .append(" ledger_schema=").append(ledger.schema()).append(" ledger_generation=").append(ledger.writerGeneration())
                .append(" ledger_records=").append(ledger.records().size()).append(" floors=").append(ledger.floors().size())
                .append(" receipts=").append(ledger.tombstones().size()).append(" heads=").append(ledger.rocketAuthorities().size())
                .append(" armed=").append(ledger.armedFlights().size()).append(" launch_serial=").append(ledger.issuedLaunchSerial())
                .append(" fluid_accounts=").append(ledger.fluidAccounts().size()).toString();
    }
    private static String cargoText(RocketEntity value) {
        try { return Integer.toString(cargo(value)); } catch (RuntimeException unreadable) { return "unreadable"; }
    }

    /** One ordinary full save, and the native IO of every Level, so that a landing in another Level is on disk too. */
    @Override void save() {
        EscrowTags.require(server.saveEverything(false, true, true), "X03 verification save refused");
        for (ServerLevel each : server.getAllLevels()) {
            each.entityManager.permanentStorage.flush(true); each.getChunkSource().chunkMap.worker.synchronize(true).join();
        }
    }
}
