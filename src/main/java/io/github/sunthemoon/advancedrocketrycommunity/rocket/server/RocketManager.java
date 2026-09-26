package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
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
import io.github.sunthemoon.advancedrocketrycommunity.travel.route.service.RouteCatalogManager;
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

    public RocketManager(CelestialCatalogManager celestialCatalogs, RouteCatalogManager routeCatalogs) {
        this(
                RocketBlockEntityAdapters.defaults(),
                Objects.requireNonNull(celestialCatalogs, "celestialCatalogs"),
                Objects.requireNonNull(routeCatalogs, "routeCatalogs")
        );
    }

    public RocketManager(
            RocketBlockEntityAdapters adapters,
            CelestialCatalogManager celestialCatalogs,
            RouteCatalogManager routeCatalogs
    ) {
        RocketBlockEntityAdapters requiredAdapters = Objects.requireNonNull(adapters, "adapters");
        flights = new RocketFlightService(celestialCatalogs, routeCatalogs);
        transactions = new RocketTransactionExecutor(requiredAdapters);
        assemblerScans = new RocketAssemblerScanService(transactions);
        disassembly = new RocketDisassemblyService(transactions, flights);
        targetContexts = new RocketTargetContextService(celestialCatalogs);
        recovery = new RocketTransactionRecoveryService(requiredAdapters);
    }

    @Override
    public void onInstalled() {
        flightLifecycle.onInstalled();
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
    public void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        flights.request(player, rocketEntityId, action, target, requestId);
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

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            flights.onPlayerLoggedOut(player.getUUID());
            disassembly.onLogout(player.getUUID());
        }
    }

    public int activeTransferCount(MinecraftServer server) {
        return flights.activeTransferCount(server);
    }

    public Optional<RocketTransferInspection> inspectTransfer(MinecraftServer server, UUID transferId) {
        return flights.inspectTransfer(server, transferId);
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

    public RocketValidationCode disassembleForReleaseTest(RocketEntity rocket, long expectedDiscardFuel) {
        flightLifecycle.requireReleaseTestHooks();
        return disassembly.disassembleForReleaseTest(rocket, expectedDiscardFuel);
    }

    public int pendingScans() {
        return assemblerScans.pendingScans();
    }
}
