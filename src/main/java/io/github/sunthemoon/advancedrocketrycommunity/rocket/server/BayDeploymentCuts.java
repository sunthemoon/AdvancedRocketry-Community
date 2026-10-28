package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCommitCut;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import java.util.UUID;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import org.slf4j.Logger;

/**
 * W27d1's gated native cuts (W27A-DESIGN section 9): with {@code releaseTestHooks} and {@code bayCheckpoint=<cut>} on a
 * release-test dedicated server, the first matching receipt write of an in-session claim arms the receipt committer's
 * one-shot cut. It only pauses or injects an unknown outcome; it never launches or registers anything. A clean stop
 * releases a paused write so the drain can finish it.
 */
final class BayDeploymentCuts {
    static final String PROPERTY = "advancedrocketrycommunity.bayCheckpoint";
    static final String FIRST_TICK = "advancedrocketrycommunity.bayFirstTick";
    static final String REGISTRY_BLOCKED = "advancedrocketrycommunity.bayRegistryBlocked";
    static final String REFUSE = "advancedrocketrycommunity.bayRefuseRegistration";
    private static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Cut {
        B1_STAGED(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B1_MOVED(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B9_STAGED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B9_MOVED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B12_UNKNOWN(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.UNKNOWN),
        B18_INTENT(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B18_REJECTED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        // W170: D4's REGISTERED generation, D7's guarded removal (phase null) and the stall after D5's publication.
        B3_STAGED(BayReceipt.Phase.REGISTERED, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B3_MOVED(BayReceipt.Phase.REGISTERED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B12_D4(BayReceipt.Phase.REGISTERED, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.UNKNOWN),
        B15_D4(BayReceipt.Phase.REGISTERED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.UNKNOWN),
        B8_STAGED(null, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B8_MOVED(null, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B12_D7(null, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.UNKNOWN),
        B15_D7(null, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.UNKNOWN),
        B6_D5(null, null, null),
        // W170-R22: B16's pause of D7's guard on the IO thread, before it reads the proof, lease and group token.
        B16_GUARD(null, null, null);

        @Nullable final BayReceipt.Phase phase;
        @Nullable final BayReceiptCommitCut.Point point;
        @Nullable final BayReceiptCommitCut.Mode mode;
        Cut(@Nullable BayReceipt.Phase phase, @Nullable BayReceiptCommitCut.Point point, @Nullable BayReceiptCommitCut.Mode mode) {
            this.phase = phase; this.point = point; this.mode = mode;
        }
        boolean removal() { return phase == null && point != null; }
    }

    private final Cut cut;
    private final BayReceiptCommitCut committer;
    private boolean fired;

    private BayDeploymentCuts(Cut cut, BayReceiptCommitCut committer) { this.cut = cut; this.committer = committer; }

    /** The configured cut on a gated release-test dedicated server; null everywhere else, including GameTests. */
    @Nullable
    static BayDeploymentCuts configured(MinecraftServer server) {
        String value = System.getProperty(PROPERTY, "");
        if (value.isEmpty() || !Boolean.getBoolean(HOOKS) || !server.isDedicatedServer()) { return null; }
        var committer = EscrowRuntime.of(server).bayReceipts(server).commitCutForTests(server)
                .orElseThrow(() -> new IllegalStateException("The receipt committer has no cut seam"));
        Cut cut = Cut.valueOf(value);
        LOGGER.info("ARCE_RELEASE_BAY_CUT configured={} phase={} point={} mode={}", cut, cut.phase == null ? (cut.removal() ? "REMOVAL"
                : cut == Cut.B16_GUARD ? "GUARD" : "STALL") : cut.phase, cut.point, cut.mode);
        return new BayDeploymentCuts(cut, committer);
    }

    /**
     * B13 (W27A-DESIGN section 9): on the coordinator's first tick of a restarted release-test server, try to decommission
     * and terminal-launch the satellite named by {@code bayFirstTick=<uuid>}, and log both answers. A durable INTENT must
     * refuse both with DEPLOYMENT_PENDING, because the receipt index was loaded at ServerStartingEvent (N6).
     */
    static void firstTick(MinecraftServer server) {
        firstTick(server, () -> SatelliteMissionSavedData.get(server));
    }

    /**
     * The operator decommission below can remove any idle satellite, so the complete release-test gate
     * ({@link EscrowTestEndpoints#enabled}: the owner thread, no RCON and only test-owned connections) decides before the
     * UUID is parsed or the registry is reached (W128R-R1).
     */
    static void firstTick(MinecraftServer server, Supplier<SatelliteMissionSavedData> registries) {
        String value = System.getProperty(FIRST_TICK, "");
        if (value.isEmpty() || !Boolean.getBoolean(HOOKS) || !server.isDedicatedServer()) { return; }
        if (!EscrowTestEndpoints.enabled(server)) {
            LOGGER.warn("ARCE_RELEASE_BAY_FIRST_TICK refused: the release-test gate is closed");
            return;
        }
        java.util.UUID satellite = java.util.UUID.fromString(value);
        var registry = registries.get();
        var decommission = registry.decommission(satellite, BayReleaseTestCommands.OWNER, true, true).code();
        var identity = new io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity(satellite,
                BayReleaseTestCommands.OWNER, io.github.sunthemoon.advancedrocketrycommunity.ModIdentity.id("data_satellite"));
        var launch = io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime.launchService()
                .map(service -> service.validate(server, BayReleaseTestCommands.OWNER, identity,
                        io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds.EARTH_ID).result().code().name())
                .orElse("NO_SERVICE");
        LOGGER.info("ARCE_RELEASE_BAY_FIRST_TICK satellite={} tick={} decommission={} launch={}", satellite, server.getTickCount(),
                decommission, launch);
    }

    /**
     * B10's hold half (W128R-R2): with {@code bayRegistryBlocked=true} on a gated release-test dedicated server, the
     * coordinator sees the registry as blocked for the whole session, from ServerStartingEvent, before the first tick.
     * It changes only the coordinator's view: the registry, its fences and its file are untouched.
     */
    static boolean registryBlocked(MinecraftServer server) {
        if (!Boolean.getBoolean(REGISTRY_BLOCKED) || !Boolean.getBoolean(HOOKS) || !server.isDedicatedServer()) { return false; }
        if (!EscrowTestEndpoints.enabled(server)) {
            LOGGER.warn("ARCE_RELEASE_BAY_REGISTRY_BLOCKED refused: the release-test gate is closed");
            return false;
        }
        LOGGER.info("ARCE_RELEASE_BAY_REGISTRY_BLOCKED session=true");
        return true;
    }

    /** Before an in-session claim's receipt write: the first write of the cut's phase arms the one-shot committer cut. */
    void writing(BayClaim claim, BayReceipt receipt) {
        if (fired || claim.origin() != BayClaim.Origin.CHECKPOINT || cut.phase == null || receipt.phase() != cut.phase) { return; }
        fired = true;
        committer.arm(cut.point, cut.mode, cut.name(), receipt.op());
    }

    /** W170 B8, B12 and B15 at D7: before an in-session claim's guarded removal, arm the one-shot committer cut. */
    void removing(BayClaim claim, java.util.UUID op) {
        if (fired || claim.origin() != BayClaim.Origin.CHECKPOINT || !cut.removal()) { return; }
        fired = true;
        committer.arm(cut.point, cut.mode, cut.name(), op);
    }

    /**
     * W170 B16 (W170-R22): an in-session claim's D7 guard pauses once on the receipt writer's IO thread, after the removal
     * was captured and staged but before the guard reads its proof, lease and group token, until the harness releases the
     * committer cut. The harness then moves the source, saves, or adds or reloads an image of L, so the guard must refuse.
     */
    java.util.function.BooleanSupplier guard(BayClaim claim, java.util.function.BooleanSupplier guard) {
        if (cut != Cut.B16_GUARD || claim.origin() != BayClaim.Origin.CHECKPOINT) { return guard; }
        UUID op = claim.group().orElseThrow().operations().get(0);
        return () -> { committer.pauseBeforeGuard(cut.name(), op); return guard.getAsBoolean(); };
    }

    /**
     * W170 B6: once an in-session claim has published S' and T' (D5), it stalls before D6 and prints the pause marker
     * once, so the harness can kill the server between the consumption and its proof. True while stalled.
     */
    boolean stallAfterConsumption(BayClaim claim) {
        if (cut != Cut.B6_D5 || claim.origin() != BayClaim.Origin.CHECKPOINT) { return false; }
        if (!fired) {
            fired = true;
            LOGGER.info("ARCE_RELEASE_BAY_PAUSED cut={} op={}", cut, claim.group().orElseThrow().operations().get(0));
        }
        return true;
    }

    /**
     * W170 B9 and B18 at a refusal: with {@code bayRefuseRegistration=true} on a gated release-test dedicated server, D2
     * refuses every in-session registration with SERVER_ERROR (the code a missing launch service gives), so the REJECTED
     * outcome path runs. It registers nothing.
     */
    static boolean refuseRegistration(MinecraftServer server) {
        if (!Boolean.getBoolean(REFUSE) || !Boolean.getBoolean(HOOKS) || !server.isDedicatedServer()) { return false; }
        return EscrowTestEndpoints.enabled(server);
    }
}
