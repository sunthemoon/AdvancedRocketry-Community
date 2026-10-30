package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Real-loader operator decisions on a deliberately held, fully applied fill. Release-gated fixture only. */
final class EscrowLoaderS6SettlementProbe {
    static final String BEFORE = "LF_S6_O1_BEFORE", REPLACED = "LF_S6_O1_REPLACED", ACK = "LF_S6_O1_ACK";
    private static final List<String> CASES = List.of(BEFORE, REPLACED, ACK);
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private static final BlockPos DEVICE = new BlockPos(66, 100, 64);
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery;
    private final EscrowLoaderCutProbe setup;
    private boolean held, baseline, queued, done;
    private volatile boolean writingIntent;
    private long began = -1;

    static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }
    static boolean boundary(String cut, String hook) {
        return BEFORE.equals(cut) && "K1".equals(hook) || REPLACED.equals(cut) && "K2".equals(hook)
                || ACK.equals(cut) && "O1_ACK".equals(hook);
    }
    static boolean manualHoldDurable(EscrowReason reason) {
        // Idle is not acknowledgement: the ordinary ten-tick writer pace can defer this transition.
        if (reason == EscrowReason.NONE) { return false; }
        EscrowTags.require(reason == EscrowReason.OTHER_STATE, "Settlement manual repair setup was not durable");
        return true;
    }
    EscrowLoaderS6SettlementProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "Settlement requires its exact per-device release gate");
        this.server = server; this.cut = cut; this.recovery = recovery;
        setup = recovery ? null : new EscrowLoaderCutProbe(server, "LF_M_K7", false);
    }
    void startTick() { if (setup != null && !held) { setup.startTick(); } }
    void checkpoint(String hook) {
        if (recovery || done) { return; }
        if (writingIntent && boundary(cut, hook)) { pause(hook); }
        if (held) { return; }
        setup.checkpoint(hook);
        if (hook.equals("C2_DESTINATION_AFTER")) {
            var data = ResourceEscrowSavedData.existing(server);
            EscrowTags.require(data != null && data.ledger().records().size() == 1, "Settlement setup lost its real fill");
            var coordinator = data.bind(server);
            coordinator.transition(data.ledger().records().get(0).id(), EscrowReason.OTHER_STATE);
            coordinator.persistTransitions(); held = true;
        }
    }
    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 settlement exceeded the existing fixture bound");
        if (!recovery && !held) { setup.tick(); return; }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(DEVICE) || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity loader)) { return; }
        var root = loader.root().orElseThrow();
        if (!(level.getEntity(root.binding().physicalRocket()) instanceof RocketEntity rocket)) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null, "Settlement lost its ledger");
        var coordinator = data.bind(server);
        EscrowTags.require(amount(loader) == 4_000 && bank(rocket, false) == 1_000 && bank(rocket, true) == 0
                && section(loader).epoch() == 1 && rocket.escrowSection().epoch() == 1 && data.ledger().issuedThrough() == 1,
                "Settlement changed its exact resources or epochs");
        if (!coordinator.writerIdle()) { return; }
        if (recovery) {
            if (BEFORE.equals(cut)) {
                EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().tombstones().isEmpty(), "Pre-O1 invented a decision");
                var op = data.ledger().records().get(0);
                EscrowTags.require(op.settlement().isEmpty() && op.reason() == EscrowReason.OTHER_STATE
                        && loader.escrowLocked() && !RocketEscrowGate.idle(rocket), "Pre-O1 released its manual hold");
            } else {
                if (!data.ledger().records().isEmpty() || data.ledger().tombstones().isEmpty()
                        || !section(loader).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
                EscrowTags.require(data.ledger().tombstones().size() == 1 && data.ledger().tombstones().get(0).getBoolean("released")
                        && !loader.escrowLocked() && RocketEscrowGate.idle(rocket), "Settlement failed to release its chosen survivors");
            }
            if (server.getTickCount() - began < 100) { return; }
            done = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_O_RESULT cut={} tank=4000 bank=1000 epochs=1,1 records={} receipts={} state={}",
                    cut, data.ledger().records().size(), data.ledger().tombstones().size(), BEFORE.equals(cut) ? "PRE_O1" : "RELEASED");
            return;
        }
        if (!baseline) {
            EscrowTags.require(data.ledger().records().size() == 1, "Settlement lost its pending manual hold");
            if (!manualHoldDurable(data.ledger().records().get(0).reason())) { return; }
            // Seed for the operator experiment, before O1: not a post-cut save or a repair of its oracle.
            EscrowTags.require(server.saveEverything(false, true, true), "Settlement pre-decision baseline save refused");
            baseline = true; return;
        }
        if (queued) { return; }
        var selected = data.ledger().records().get(0).id();
        var operator = EscrowRuntime.of(server).settlement();
        var preview = operator.preview(server, selected, EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "Server");
        writingIntent = true;
        queued = operator.confirm(server, selected, preview.choice(), ACTOR, preview.digest(), committed -> {
            EscrowTags.require(committed, "S6 operator intent was not acknowledged");
            if (boundary(cut, "O1_ACK")) { pause("O1_ACK"); }
            writingIntent = false;
        });
        if (!queued) { writingIntent = false; }
    }
    private void pause(String hook) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_O_PAUSED cut={} hook={}", cut, hook);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 operator forced stop did not arrive within 30 seconds");
    }
}
