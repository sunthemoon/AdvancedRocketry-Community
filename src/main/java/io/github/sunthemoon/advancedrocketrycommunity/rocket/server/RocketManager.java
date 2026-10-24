package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.compat.rocket.component.RocketComponentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferInspection;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockEntityAdapters;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;

/** Lifecycle-owned facade for bounded rocket assembly, flight and recovery services. */
public final class RocketManager implements RocketOperationService {
    public static final double MAX_INTERACTION_DISTANCE_SQUARED =
            RocketInteraction.MAX_INTERACTION_DISTANCE_SQUARED;

    private final RocketTransactionExecutor transactions;
    private final RocketAssemblerScanService assemblerScans;
    private final RocketTransactionRecoveryService recovery;
    private final RocketFlightService flights;
    private final RocketDisassemblyService disassembly;
    private final RocketTargetContextService targetContexts;
    private final RocketFlightLifecycleController flightLifecycle = new RocketFlightLifecycleController();

    public RocketManager() {
        this(RocketBlockEntityAdapters.defaults(), null, null);
    }

    public RocketManager(RocketBlockEntityAdapters adapters) {
        this(adapters, null, null);
    }

    public RocketManager(CelestialCatalogManager celestialCatalogs) {
        this(RocketBlockEntityAdapters.defaults(), Objects.requireNonNull(celestialCatalogs, "celestialCatalogs"), null);
    }

    public RocketManager(CelestialCatalogManager celestialCatalogs, PlanetaryCatalogManager planetaryCatalogs) {
        this(
                RocketBlockEntityAdapters.defaults(),
                Objects.requireNonNull(celestialCatalogs, "celestialCatalogs"),
                Objects.requireNonNull(planetaryCatalogs, "planetaryCatalogs")
        );
    }

    public RocketManager(
            RocketBlockEntityAdapters adapters,
            CelestialCatalogManager celestialCatalogs,
            PlanetaryCatalogManager planetaryCatalogs
    ) {
        this(adapters, celestialCatalogs, planetaryCatalogs, RocketComponentCatalog.empty());
    }

    public RocketManager(
            RocketBlockEntityAdapters adapters,
            CelestialCatalogManager celestialCatalogs,
            PlanetaryCatalogManager planetaryCatalogs,
            RocketComponentCatalog components
    ) {
        RocketBlockEntityAdapters requiredAdapters = Objects.requireNonNull(adapters, "adapters");
        if (planetaryCatalogs != null && celestialCatalogs != planetaryCatalogs.celestialView()) {
            throw new IllegalArgumentException("Rocket context and flight planning must share one planetary catalog owner");
        }
        flights = new RocketFlightService(celestialCatalogs, planetaryCatalogs);
        transactions = new RocketTransactionExecutor(requiredAdapters);
        assemblerScans = new RocketAssemblerScanService(transactions, components);
        disassembly = new RocketDisassemblyService(transactions, flights);
        targetContexts = new RocketTargetContextService(celestialCatalogs);
        recovery = new RocketTransactionRecoveryService(requiredAdapters);
    }

    @Override
    public void onInstalled() {
        flightLifecycle.onInstalled();
    }

    boolean sealedPayloadKnown(io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload payload) {
        return transactions.adapters().supportsPayload(payload);
    }

    public boolean resourceMutationAllowed(RocketEntity rocket) {
        if (!flights.resourceMutationAllowed(rocket)) { return false; }
        var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData
                .get(((ServerLevel) rocket.level()).getServer());
        return journal.operational() && !transactions.hasPendingRecovery(journal, rocket.snapshot().orElseThrow());
    }

    void retireBeforeFill(RocketEntity rocket) { if (resourceMutationAllowed(rocket)) { flights.retireBeforeFill(rocket); } }

    boolean escrowReconciliationBoundary(RocketEntity rocket) {
        return escrowReconciliationBoundary(rocket, null);
    }

    boolean escrowReconciliationBoundary(RocketEntity rocket,
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard guard) {
        if (!flights.escrowReconciliationBoundary(rocket, guard)) { return false; }
        var journal = io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketTransactionSavedData
                .get(((ServerLevel) rocket.level()).getServer());
        return journal.operational() && !transactions.hasPendingRecovery(journal, rocket.snapshot().orElseThrow());
    }

    @Override
    public void requestAssembler(ServerPlayer player, BlockPos assemblerPosition, boolean assemble) {
        assemblerScans.requestAssembler(player, assemblerPosition, assemble);
    }

    /** Queues an operator-authorized scan without inventing a fake player identity. */
    public RocketValidationCode requestAdminAssembler(
            ServerLevel level,
            BlockPos assemblerPosition,
            UUID ownerId,
            boolean assemble
    ) {
        return assemblerScans.requestAdminAssembler(level, assemblerPosition, ownerId, assemble);
    }

    @Override
    public void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        disassembly.requestDisassembly(player, rocket);
    }

    public boolean confirmDisassembly(ServerPlayer player, UUID confirmation) {
        return disassembly.confirmDisassembly(player, confirmation);
    }

    @Override
    public void openFlightMenu(ServerPlayer player, RocketEntity rocket) {
        flights.openMenu(player, rocket);
    }

    @Override
    public Optional<TravelTarget> resolveCurrentTarget(ServerLevel level, BlockPos position) {
        return targetContexts.resolveCurrentTarget(level, position);
    }

    @Override
    public Optional<RocketFlightData> migrateLegacyFlightData(ServerLevel level, RocketFlightData legacy) {
        return targetContexts.migrateLegacyFlightData(level, legacy);
    }

    @Override
    public Optional<RocketTransferRecord> migrateCommittedLegacyTransfer(
            MinecraftServer server,
            RocketTransferRecord legacy
    ) {
        return targetContexts.migrateCommittedLegacyTransfer(server, legacy);
    }

    @Override
    public RocketFlightQuotes flightQuotes(ServerPlayer player, RocketEntity rocket) {
        return flights.quotes(player, rocket);
    }

    @Override
    public io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation navigation(ServerPlayer player, RocketEntity rocket) {
        return flights.navigation(player, rocket);
    }

    @Override
    public Optional<io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket> navigationCatalog(
            ServerPlayer player, RocketEntity rocket) {
        return flights.navigationCatalog(player, rocket);
    }

    @Override
    public void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        flights.request(player, rocketEntityId, action, target, requestId);
    }

    @Override public void requestFlightIntent(ServerPlayer player, int rocketEntityId, RocketFlightAction action,
            TravelTarget target, UUID requestId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad) {
        flights.request(player, rocketEntityId, action, target, requestId, pad);
    }

    /** Server-only test/operator entry, with the same station/pad staging as ordinary flights. */
    public RocketFlightRequestResult requestAdminPadFlight(RocketEntity rocket, UUID stationId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad, UUID requestId) {
        return flights.requestAdminPadFlight(rocket, stationId, pad, requestId);
    }

    @Override
    public RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            TravelTarget destination,
            UUID requestId
    ) {
        return flights.requestAdminFlight(rocket, destination, requestId);
    }

    public RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        return requestAdminFlight(
                rocket,
                LegacyTravelTargetAdapter.fromLegacy(destination, null),
                requestId
        );
    }

    @Override
    public RocketFlightRequestResult requestAdminStationFlight(
            RocketEntity rocket,
            UUID stationId,
            UUID requestId
    ) {
        return flights.requestAdminStationFlight(rocket, stationId, requestId);
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tick(event.getServer());
        }
    }

    public void tick(MinecraftServer server) {
        Objects.requireNonNull(server, "server");
        if (flightLifecycle.active()) {
            flights.tick(server);
            flightLifecycle.pauseIfReached(server);
        }
        if (!flightLifecycle.recoverySuppressedForReleaseTest()) {
            RocketTransactionRecoveryService.Outcome outcome = recovery.recoverOne(server);
            if (outcome == RocketTransactionRecoveryService.Outcome.RECOVERED
                    || outcome == RocketTransactionRecoveryService.Outcome.CONFLICT) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_ROCKET_RECOVERY outcome={}", outcome);
            }
        }
        assemblerScans.tick(server);
    }

    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (flightLifecycle.active() && event.getEntity() instanceof ServerPlayer player) {
            flights.onPlayerLoggedIn(player);
        }
    }

    void onRocketJoined(RocketEntity rocket) { flights.onRocketJoined(rocket); }
    void installBayRecoveryGate(RocketTransferBayRecoveryGate gate) { flights.installBayRecoveryGate(gate); }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            flights.onPlayerLoggedOut(player.getUUID());
            disassembly.onLogout(player.getUUID());
        }
    }

    /**
     * ADR-044 §5: true while a rocket that can still move uses the given Space station region, or
     * while the transfer journal or its recovery cannot tell (fail closed). Server thread only.
     */
    public boolean stationRegionInMotion(MinecraftServer server, int minX, int minZ, int maxX, int maxZ) {
        return flights.stationRegionInMotion(server, minX, minZ, maxX, maxZ);
    }

    /** Operator diagnostics for the warp in-motion rule (journal state and recovery classification). */
    public String transferJournalDiagnostics(MinecraftServer server) {
        return flights.transferJournalDiagnostics(server);
    }

    public int activeTransferCount(MinecraftServer server) {
        return flights.activeTransferCount(server);
    }

    // BEGIN transfer service diagnostics
    /** Reads only this manager's lifecycle and scalar/membership observations. */
    public String transferFailureDiagnostics(UUID transferId) {
        return flights.transferFailureDiagnostics(transferId, flightLifecycle.active());
    }
    // END transfer service diagnostics

    public Optional<RocketTransferInspection> inspectTransfer(MinecraftServer server, UUID transferId) {
        return flights.inspectTransfer(server, transferId);
    }

    /** W90b: the destination warm-up of logical rocket {@code logicalRocketId}, read-only. */
    public Optional<String> inspectWarmup(MinecraftServer server, UUID logicalRocketId) {
        return flights.inspectWarmup(server, logicalRocketId);
    }

    /** W90b GameTest support only: this session's transfer service, for its warm-ups and reservation inputs. */
    RocketTransferService transfers() {
        return flights.transfers();
    }

    public RocketTransferRecoveryReport recoverTransfer(MinecraftServer server, UUID transferId) {
        return flights.recoverTransfer(server, transferId);
    }

    public void clear() {
        recovery.clear();
        assemblerScans.clear();
        transactions.clear();
        flights.clear();
        disassembly.clear();
        flightLifecycle.clear();
    }

    /** Prevents the deliberately staged release-test record from recovering before shutdown. */
    public void suppressRecoveryUntilStopForReleaseTest() {
        flightLifecycle.suppressRecoveryUntilStopForReleaseTest();
    }

    /** Arms an exact, bounded flight checkpoint for packaged restart evidence. */
    public void armFlightCheckpointForReleaseTest(
            UUID transferId,
            RocketFlightReleaseCheckpoint checkpoint
    ) {
        flightLifecycle.arm(transferId, checkpoint);
    }

    public void cancelFlightCheckpointForReleaseTest(UUID transferId) {
        flightLifecycle.cancel(transferId);
    }

    /** Uses the production transaction against a landed test rocket without a fake player. */
    public RocketValidationCode disassembleForReleaseTest(RocketEntity rocket) {
        return disassembleForReleaseTest(rocket, -1L);
    }

    /** Gated bridge to the existing landed-reservation operation, with its normal checked acknowledgement. */
    public boolean releaseLandedReservationForFirstEventTest(RocketEntity rocket) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints
                .requireEnabled(((net.minecraft.server.level.ServerLevel) rocket.level()).getServer());
        if (io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams
                .configured().orElseThrow() != io.github.sunthemoon.advancedrocketrycommunity.progression.classic
                .firstevent.FirstEventCutSeams.Cut.FM12A) { throw new IllegalStateException("FM12a is not armed"); }
        return flights.releaseLandedReservation(rocket);
    }

    public RocketValidationCode disassembleForReleaseTest(RocketEntity rocket, long expectedDiscardFuel) {
        flightLifecycle.requireReleaseTestHooks();
        return disassembly.disassembleForReleaseTest(rocket, expectedDiscardFuel);
    }

    public int pendingScans() {
        return assemblerScans.pendingScans();
    }
}
