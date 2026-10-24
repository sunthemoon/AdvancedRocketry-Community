package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketFlightQuotes;
import io.github.sunthemoon.advancedrocketrycommunity.travel.migration.LegacyTravelTargetAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.travel.model.TravelTarget;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import java.util.Optional;

/** Narrow lifecycle bridge for blocks/entities; all mutable state belongs to the installed manager. */
public final class RocketRuntime {
    private static volatile RocketOperationService service;

    private RocketRuntime() {
    }

    public static void install(RocketOperationService installedService) {
        RocketOperationService checked = Objects.requireNonNull(installedService, "installedService");
        checked.onInstalled();
        service = checked;
    }

    public static void clear() {
        service = null;
    }

    /** Availability only: no provider callback, journal access or materialization authority. */
    public static boolean sealedPayloadKnown(ServerLevel level,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockEntityPayload payload) {
        return level.getServer().isSameThread() && service instanceof RocketManager manager
                && manager.sealedPayloadKnown(payload);
    }

    /** Native onPlace bridge, not a public staging or authority API. */
    public static void sealedOnPlace(ServerLevel level, BlockPos position) {
        if (level.getServer().isSameThread() && service instanceof RocketManager manager) { manager.sealedOnPlace(level, position); }
    }
    public static void sealedTerrainChanged(ServerLevel level, BlockPos position) {
        if (level.getServer().isSameThread() && service instanceof RocketManager manager) { manager.sealedTerrainChanged(level, position); }
    }
    public static void sealedEntityChanged(ServerLevel level, net.minecraft.world.entity.Entity entity) {
        if (level.getServer().isSameThread() && service instanceof RocketManager manager) { manager.sealedEntityChanged(level, entity); }
    }
    public static void sealedHint(ServerLevel level, BlockPos position) {
        if (level.getServer().isSameThread() && service instanceof RocketManager manager) { manager.sealedHint(level, position); }
    }
    public static int sealedHintCountForReleaseTest(MinecraftServer server) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        return service instanceof RocketManager manager ? manager.sealedHintCount(server) : 0;
    }
    public static String sealedLazyDiagnosticForReleaseTest(MinecraftServer server) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        return service instanceof RocketManager manager ? manager.sealedLazyDiagnostic(server) : "no-manager";
    }
    public static boolean sealedOnRemove(ServerLevel level, BlockPos position, net.minecraft.world.level.block.state.BlockState next) {
        return level.getServer().isSameThread() && service instanceof RocketManager manager && manager.sealedOnRemove(level, position, next);
    }
    public static boolean unsealForReleaseTest(ServerLevel level,
            io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedRocketBlockEntity carrier) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(level.getServer());
        return service instanceof RocketManager manager && manager.unsealForReleaseTest(level, carrier);
    }
    public static boolean sealedFrozen(RocketEntity rocket) {
        return service instanceof RocketManager manager && manager.sealedFrozen(rocket);
    }
    public static String sealedDiagnosticForReleaseTest(MinecraftServer server, UUID id) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        return service instanceof RocketManager manager ? manager.sealedDiagnostic(id) : "no-manager";
    }
    public static boolean sealedRecoveryTrackedForReleaseTest(MinecraftServer server, UUID id) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        return service instanceof RocketManager manager && manager.sealedRecovering(id);
    }
    public static void sealedNativeObserverForReleaseTest(MinecraftServer server, java.util.function.Consumer<String> observer) {
        io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints.requireEnabled(server);
        if (!(service instanceof RocketManager manager)) { throw new IllegalStateException("No rocket manager"); }
        manager.sealedNativeObserver(server, observer);
    }

    /** Gated native verification only; player disassembly is not enabled by N3-B2. */
    public static UUID beginSealedForReleaseTest(RocketEntity rocket,
            java.util.function.Consumer<io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly.Result> completion) {
        if (!(service instanceof RocketManager manager)) { throw new IllegalStateException("No rocket manager"); }
        return manager.beginSealedForReleaseTest(rocket, completion);
    }

    /** Only this installed server session owns preparation state; nothing is saved or restored. */
    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoPreparations cargoPreparations(MinecraftServer server) {
        if (!server.isSameThread()) { throw new IllegalStateException("Cargo preparation requires the server thread"); }
        return io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.of(server).cargoPreparations();
    }
    public static boolean cargoPreparing(RocketEntity rocket) {
        var server = rocket.getServer();
        if (server == null || server.overworld() == null) { return false; }
        if (!server.isSameThread()) { return true; }
        var runtime = server.overworld().getCapability(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.CAPABILITY)
                .resolve().orElse(null);
        return runtime != null && rocket.assemblyTransactionId().filter(runtime::cargoPreparing).isPresent();
    }
    public static void cargoRemoved(RocketEntity rocket) {
        if (rocket.getServer() != null && rocket.getServer().overworld() != null) {
            rocket.getServer().overworld().getCapability(io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime.CAPABILITY)
                    .ifPresent(runtime -> runtime.cargoRemoved(rocket));
        }
    }

    /** W90b GameTest support only: the installed manager's transfer service, or null without a manager. */
    static RocketTransferService installedTransfers() {
        return service instanceof RocketManager manager ? manager.transfers() : null;
    }

    public static void onRocketJoined(RocketEntity rocket) {
        if (service instanceof RocketManager manager) { manager.onRocketJoined(rocket); }
    }

    /** Internal coordinator registration; the installed manager owns the gate for this server session. */
    public static void installBayRecoveryGate(MinecraftServer server, RocketTransferBayRecoveryGate gate) {
        if (!server.isSameThread() || !(service instanceof RocketManager manager)) {
            throw new IllegalStateException("Bay gate needs the installed server-thread rocket manager");
        }
        manager.installBayRecoveryGate(gate);
    }

    public static RocketFlightRequestResult requestAdminPadFlight(RocketEntity rocket, UUID stationId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad, UUID requestId) {
        if (cargoPreparing(rocket)) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
        return service instanceof RocketManager manager ? manager.requestAdminPadFlight(rocket, stationId, pad, requestId)
                : RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
    }

    /** Server endpoint admission; an unavailable or unsupported lifecycle owner fails closed. */
    public static boolean resourceMutationAllowed(RocketEntity rocket) {
        return !cargoPreparing(rocket) && service instanceof RocketManager manager && manager.resourceMutationAllowed(rocket);
    }

    /** Fuel admission demand only; no startup scan and no authority to bypass the ordinary resource boundary. */
    public static void retireBeforeFill(RocketEntity rocket) {
        if (!cargoPreparing(rocket) && service instanceof RocketManager manager) { manager.retireBeforeFill(rocket); }
    }

    /** Other interlocks only; this read-only boundary never grants an escrow publication permit. */
    public static boolean escrowReconciliationBoundary(RocketEntity rocket) {
        return service instanceof RocketManager manager && manager.escrowReconciliationBoundary(rocket);
    }

    /** Reconciliation of the named loader fill only; this never permits recovery or resource admission. */
    public static boolean escrowReconciliationBoundary(RocketEntity rocket,
            io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGuard guard) {
        return service instanceof RocketManager manager && manager.escrowReconciliationBoundary(rocket, guard);
    }

    /** Empty-server-only fixture; the manager delegates to the normal reservation-release service. */
    public static boolean releaseLandedReservationForFirstEventTest(RocketEntity rocket) {
        if (!(service instanceof RocketManager manager)) { throw new IllegalStateException("No rocket manager"); }
        return manager.releaseLandedReservationForFirstEventTest(rocket);
    }

    /** Server-only operator/test recovery through the installed lifecycle owner. */
    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport recoverTransfer(
            MinecraftServer server, UUID transferId) {
        if (service instanceof RocketManager manager) { return manager.recoverTransfer(server, transferId); }
        return new io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport(
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecoveryReport.Status.NOT_FOUND,
                transferId, Optional.empty(), Optional.empty(), 0, 0);
    }

    // BEGIN transfer service diagnostics
    /** Internal failure observation; unsupported installed services are never invoked. */
    public static String transferFailureDiagnostics(UUID transferId) {
        RocketOperationService current = service;
        return current instanceof RocketManager manager
                ? manager.transferFailureDiagnostics(transferId)
                : "installed=UNSUPPORTED";
    }
    // END transfer service diagnostics

    public static void requestAssembler(
            ServerPlayer player,
            BlockPos assemblerPosition,
            boolean assemble
    ) {
        RocketOperationService current = service;
        if (current == null) {
            player.displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                    true
            );
            return;
        }
        current.requestAssembler(player, assemblerPosition, assemble);
    }

    public static void requestDisassembly(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        if (current == null) {
            player.displayClientMessage(
                    Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                    true
            );
            return;
        }
        current.requestDisassembly(player, rocket);
    }

    public static void openFlightMenu(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        if (current == null) {
            unavailable(player);
            return;
        }
        current.openFlightMenu(player, rocket);
    }

    public static Optional<TravelTarget> resolveCurrentTarget(ServerLevel level, BlockPos position) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.resolveCurrentTarget(level, position);
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData>
            migrateLegacyFlightData(
                    ServerLevel level,
                    io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData legacy
            ) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.migrateLegacyFlightData(level, legacy);
    }

    public static Optional<RocketTransferRecord> migrateCommittedLegacyTransfer(
            MinecraftServer server,
            RocketTransferRecord legacy
    ) {
        RocketOperationService current = service;
        return current == null
                ? Optional.empty()
                : current.migrateCommittedLegacyTransfer(server, legacy);
    }

    public static RocketFlightQuotes flightQuotes(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? RocketFlightQuotes.empty() : current.flightQuotes(player, rocket);
    }

    public static io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation navigation(ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? io.github.sunthemoon.advancedrocketrycommunity.rocket.menu.RocketNavigation.empty()
                : current.navigation(player, rocket);
    }

    public static Optional<io.github.sunthemoon.advancedrocketrycommunity.celestial.network.CelestialSnapshotPacket> navigationCatalog(
            ServerPlayer player, RocketEntity rocket) {
        RocketOperationService current = service;
        return current == null ? Optional.empty() : current.navigationCatalog(player, rocket);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            TravelTarget target,
            UUID requestId
    ) {
        requestFlightIntent(player, rocketEntityId, action, target, requestId, null);
    }

    public static void requestFlightIntent(ServerPlayer player, int rocketEntityId, RocketFlightAction action,
            TravelTarget target, UUID requestId,
            io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadReference pad) {
        RocketOperationService current = service;
        if (current == null) {
            unavailable(player);
            return;
        }
        current.requestFlightIntent(player, rocketEntityId, action, target, requestId, pad);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID requestId
    ) {
        requestFlightIntent(player, rocketEntityId, action, destination, null, requestId);
    }

    public static void requestFlightIntent(
            ServerPlayer player,
            int rocketEntityId,
            RocketFlightAction action,
            RocketDestination destination,
            UUID destinationStationId,
            UUID requestId
    ) {
        requestFlightIntent(
                player,
                rocketEntityId,
                action,
                LegacyTravelTargetAdapter.fromLegacy(destination, destinationStationId),
                requestId
        );
    }

    /** Server-only operator/test boundary; no client data can invoke this method. */
    public static RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            RocketDestination destination,
            UUID requestId
    ) {
        if (cargoPreparing(rocket)) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminFlight(rocket, destination, requestId);
    }

    /** Server-only operator/test path for typed targets. */
    public static RocketFlightRequestResult requestAdminFlight(
            RocketEntity rocket,
            TravelTarget destination,
            UUID requestId
    ) {
        if (cargoPreparing(rocket)) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminFlight(rocket, destination, requestId);
    }

    /** Server-only test/operator path; coordinates are still resolved from StationState. */
    public static RocketFlightRequestResult requestAdminStationFlight(
            RocketEntity rocket,
            UUID stationId,
            UUID requestId
    ) {
        if (cargoPreparing(rocket)) { return RocketFlightRequestResult.failure(RocketFlightRequestCode.TRANSFER_JOURNAL_BLOCKED); }
        RocketOperationService current = service;
        if (current == null) {
            return RocketFlightRequestResult.failure(RocketFlightRequestCode.ENTITY_UNAVAILABLE);
        }
        return current.requestAdminStationFlight(rocket, stationId, requestId);
    }

    private static void unavailable(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.advancedrocketrycommunity.rocket.service_unavailable"),
                true
        );
    }
}
