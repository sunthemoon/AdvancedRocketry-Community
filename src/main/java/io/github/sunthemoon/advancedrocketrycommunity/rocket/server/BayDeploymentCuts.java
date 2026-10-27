package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceipt;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptCommitCut;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayClaim;
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
    private static final String HOOKS = "advancedrocketrycommunity.releaseTestHooks";
    private static final Logger LOGGER = LogUtils.getLogger();

    enum Cut {
        B1_STAGED(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B1_MOVED(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B9_STAGED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.PAUSE),
        B9_MOVED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B12_UNKNOWN(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.STAGED, BayReceiptCommitCut.Mode.UNKNOWN),
        B18_INTENT(BayReceipt.Phase.INTENT, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE),
        B18_REJECTED(BayReceipt.Phase.REJECTED, BayReceiptCommitCut.Point.MOVED, BayReceiptCommitCut.Mode.PAUSE);

        final BayReceipt.Phase phase;
        final BayReceiptCommitCut.Point point;
        final BayReceiptCommitCut.Mode mode;
        Cut(BayReceipt.Phase phase, BayReceiptCommitCut.Point point, BayReceiptCommitCut.Mode mode) {
            this.phase = phase; this.point = point; this.mode = mode;
        }
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
        LOGGER.info("ARCE_RELEASE_BAY_CUT configured={} phase={} point={} mode={}", cut, cut.phase, cut.point, cut.mode);
        return new BayDeploymentCuts(cut, committer);
    }

    /**
     * B13 (W27A-DESIGN section 9): on the coordinator's first tick of a restarted release-test server, try to decommission
     * and terminal-launch the satellite named by {@code bayFirstTick=<uuid>}, and log both answers. A durable INTENT must
     * refuse both with DEPLOYMENT_PENDING, because the receipt index was loaded at ServerStartingEvent (N6).
     */
    static void firstTick(MinecraftServer server) {
        String value = System.getProperty("advancedrocketrycommunity.bayFirstTick", "");
        if (value.isEmpty() || !Boolean.getBoolean(HOOKS) || !server.isDedicatedServer()) { return; }
        java.util.UUID satellite = java.util.UUID.fromString(value);
        var registry = io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData.get(server);
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

    /** Before an in-session claim's receipt write: the first write of the cut's phase arms the one-shot committer cut. */
    void writing(BayClaim claim, BayReceipt receipt) {
        if (fired || claim.origin() != BayClaim.Origin.CHECKPOINT || receipt.phase() != cut.phase) { return; }
        fired = true;
        committer.arm(cut.point, cut.mode, cut.name(), receipt.op());
    }
}
