package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.List;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Moves audited named-class initialization out of the first governed launch, without running flight code. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class EscrowRelaunchClasses {
    private static final String PACKAGE = "io.github.sunthemoon.advancedrocketrycommunity.";

    private EscrowRelaunchClasses() { }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        long start = System.nanoTime();
        initialize();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELAUNCH_CLASSES initialized in {} ns", System.nanoTime() - start);
    }

    // JVM initialization is idempotent per defining loader; no server or world is retained.
    static void initialize() {
        ClassLoader loader = EscrowRelaunchClasses.class.getClassLoader();
        for (String name : classNames()) { initializeClass(PACKAGE + name, loader); }
    }

    static void initializeClass(String name, ClassLoader loader) {
        try {
            Class.forName(name, true, loader);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Missing audited relaunch class: " + name, missing);
        }
    }

    // W212's named mod ClassLoad inventory, not a recursive package/dependency scan.
    // Additions require an initializer audit and an updated measured-inventory regression.
    static List<String> classNames() {
        return List.of(
                "resourceescrow.EscrowAdapterIds",
                "resourceescrow.EscrowBarrier$Attempt",
                "resourceescrow.EscrowBarrier$Result",
                "resourceescrow.EscrowChunkStore$Capture",
                "resourceescrow.EscrowChunkStore$EntityList",
                "resourceescrow.EscrowChunkStore$SupersededCapture",
                "resourceescrow.EscrowEndpoint",
                "resourceescrow.EscrowEndpoint$Kind",
                "resourceescrow.EscrowEndpoint$Unit",
                "resourceescrow.EscrowEntityStore",
                "resourceescrow.EscrowFlightAuthority",
                "resourceescrow.EscrowFlightAuthority$Launch",
                "resourceescrow.EscrowFlightCheckpoint$Preparation",
                "resourceescrow.EscrowFlightCheckpoint$Retry",
                "resourceescrow.EscrowFlightJournal",
                "resourceescrow.EscrowFlightJournal$PreparationLease",
                "resourceescrow.EscrowFlightSource",
                "resourceescrow.EscrowFlightTables",
                "resourceescrow.EscrowFlightTables$Outcome",
                "resourceescrow.EscrowFloors",
                "resourceescrow.EscrowFloors$Key",
                "resourceescrow.EscrowFloors$Usage",
                "resourceescrow.EscrowGovernedEnvelope",
                "resourceescrow.EscrowGovernedFrame",
                "resourceescrow.EscrowGovernedLaunch",
                "resourceescrow.EscrowGuard",
                "resourceescrow.EscrowInvariant",
                "resourceescrow.EscrowLaunchTrace",
                "resourceescrow.EscrowNativeEnvelope",
                "resourceescrow.EscrowPrefix$State",
                "resourceescrow.EscrowReconciliationView",
                "resourceescrow.EscrowSaveMonitor$Handle",
                "resourceescrow.EscrowSaveMonitor$Watch",
                "resourceescrow.EscrowSettlementCapacity",
                "resourceescrow.EscrowSettlementCapacity$MarkerLimit",
                "resourceescrow.EscrowSettlementCapacity$Reservation",
                "resourceescrow.EscrowSettlementCapacity$StaleCapture",
                "resourceescrow.EscrowSignatureKind",
                "resourceescrow.EscrowStoreFault",
                "resourceescrow.EscrowSuccessorRetry$Hold",
                "resourceescrow.EscrowTransferEnvelope",
                "resourceescrow.EscrowTransferEnvelope$Expanded",
                "resourceescrow.EscrowTransferProjection",
                "rocket.flight.RocketFuelCode",
                "rocket.flight.RocketFuelMutation",
                "rocket.flight.RocketTransferChecksum",
                "rocket.flight.RocketTransferPhase",
                "rocket.flight.persistence.RocketJournalInsertionBudget",
                "rocket.model.RocketBlockEntityPayload",
                "rocket.server.RocketGovernedLaunchBoundary",
                "rocket.server.RocketTransferBayRecoveryGate$Action",
                "rocket.server.RocketTransferBayRecoveryGate$Boundary",
                "rocket.server.RocketTransferBayRecoveryGate$BoundaryKind",
                "rocket.transfer.RocketWarmupRegistry$Decision",
                "rocket.transfer.RocketWarmupRegistry$Outcome");
    }
}
