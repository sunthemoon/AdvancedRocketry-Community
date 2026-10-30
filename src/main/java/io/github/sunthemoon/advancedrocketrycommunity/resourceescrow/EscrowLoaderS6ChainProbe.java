package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** Dedicated release-gated S6 chain observation fixture. */
final class EscrowLoaderS6ChainProbe {
    private static final List<String> CASES = List.of("LF_S6_CHAIN_BEFORE", "LF_S6_CHAIN_REPLACED", "LF_S6_CHAIN_ACK",
            "LF_S6_CHAIN_BEFORE_BI", "LF_S6_CHAIN_REPLACED_BI", "LF_S6_CHAIN_ACK_BI",
            "LF_S6_CHAIN_VETO", "LF_S6_CHAIN_VETO_BI");
    private static final UUID ACTOR = UUID.nameUUIDFromBytes("arce:escrow:server-console".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    private final MinecraftServer server;
    private final String cut;
    private final boolean recovery, bi, before, veto;
    private final EscrowLoaderCutProbe setup;
    private boolean held, baseline, queued, done;
    private boolean vetoInstalled, holdObserved;
    private volatile boolean writingIntent;
    private long began = -1;

    static boolean selected(boolean hooks, String cut) { return hooks && cut != null && CASES.contains(cut); }
    static boolean veto(String cut) { return "LF_S6_CHAIN_VETO".equals(cut) || "LF_S6_CHAIN_VETO_BI".equals(cut); }
    static boolean boundary(String cut, String hook) {
        if (!selected(true, cut)) { return false; }
        String side = cut.replace("_BI", "").substring("LF_S6_CHAIN_".length());
        return switch (side) {
            case "BEFORE" -> hook.equals("K1");
            case "REPLACED" -> hook.equals("K2");
            case "ACK" -> hook.equals("O1_ACK");
            default -> false;
        };
    }
    static boolean readyToHold(int records, long epoch) {
        EscrowTags.require(records <= 2 && epoch <= 2 && (epoch < 2 || records == 2), "S6 chain lost or added a fill before its hold");
        return records == 2 && epoch == 2;
    }
    EscrowLoaderS6ChainProbe(MinecraftServer server, String cut, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), cut)
                && !Boolean.getBoolean(EscrowLoaderAdmission.SWITCH), "S6 chain requires its exact per-device release gate");
        this.server = server; this.cut = cut; this.recovery = recovery;
        bi = cut.endsWith("_BI"); before = cut.contains("_BEFORE"); veto = veto(cut);
        setup = recovery ? null : new EscrowLoaderCutProbe(server, "LF_M_CHAIN_REPLACED" + (bi ? "_BI" : ""), false);
    }
    void startTick() {
        if (recovery && veto) { installVeto(); }
        if (!held && setup != null) { setup.startTick(); }
    }
    private void installVeto() {
        if (vetoInstalled) { return; }
        GuardedChunkSaves.recordObservedDenial(server.overworld(), new net.minecraft.world.level.ChunkPos(4, 4),
                "Release-test S6 chain O-veto source chunk");
        vetoInstalled = true;
    }
    void checkpoint(String hook) {
        if (!done && vetoInstalled && hook.equals("O_VETO")) { holdObserved = true; }
        if (recovery || done) { return; }
        if (writingIntent && boundary(cut, hook)) { pause(hook); }
        if (held) { return; }
        setup.checkpoint(hook);
        if (hook.equals("C2_DESTINATION_AFTER")) {
            var data = ResourceEscrowSavedData.existing(server);
            var op = data.ledger().records().get(0);
            var rocket = (RocketEntity) server.overworld().getEntity(op.destination().identity());
            if (!readyToHold(data.ledger().records().size(), rocket.escrowSection().epoch())) { return; }
            var coordinator = data.bind(server);
            for (var member : data.ledger().records()) { coordinator.transition(member.id(), EscrowReason.OTHER_STATE); }
            coordinator.persistTransitions(); held = true;
        }
    }
    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "S6 chain exceeded the existing fixture bound");
        if (!recovery && !held) { setup.tick(); return; }
        var level = server.overworld(); var a = new BlockPos(66, 100, 64); var b = a.offset(1, 0, 0);
        if (!level.isPositionEntityTicking(a) || !(level.getBlockEntity(a) instanceof FluidFuelLoaderBlockEntity first)
                || !(level.getBlockEntity(b) instanceof FluidFuelLoaderBlockEntity second)) { return; }
        if (!(level.getEntity(first.root().orElseThrow().binding().physicalRocket()) instanceof RocketEntity rocket)) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null, "S6 chain lost its ledger"); var coordinator = data.bind(server);
        EscrowTags.require(amount(first) == 4_000 && amount(second) == 4_000
                && bank(rocket, false) == (bi ? 1_000 : 2_000) && bank(rocket, true) == (bi ? 1_000 : 0)
                && section(first).epoch() == 1 && section(second).epoch() == 1 && rocket.escrowSection().epoch() == 2
                && data.ledger().issuedThrough() == 2, "S6 chain changed resources or epochs");
        if (!coordinator.writerIdle()) { return; }
        if (veto && (recovery || queued)) {
            if (!holdObserved) { return; }
            EscrowTags.require(data.ledger().records().size() == 2 && data.ledger().tombstones().isEmpty()
                    && data.ledger().rocketAuthorities().isEmpty() && first.escrowLocked() && second.escrowLocked()
                    && !RocketEscrowGate.idle(rocket), "S6 O-veto released a chosen survivor");
            var intent = EscrowSettlementIntent.decode(data.ledger().records().get(0).settlement());
            String reason = EscrowRuntime.of(server).settlementRecovery().view(intent).reason();
            EscrowTags.require(reason.equals("BARRIER_VETO"), "S6 O-veto has another recovery diagnostic: " + reason);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CHAIN_VETO cut={} reason={} records=2 checkpointed=true recovery={}",
                    cut, reason, recovery);
            if (!recovery) { pause("O_VETO"); }
            done = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CHAIN_RESULT cut={} tanks=4000,4000 banks={} epochs=1,1,2 records=2 receipts=0 state=HELD",
                    cut, bi ? "1000,1000" : "2000,0");
            return;
        }
        if (recovery) {
            if (before) {
                EscrowTags.require(data.ledger().records().size() == 2 && data.ledger().tombstones().isEmpty()
                        && data.ledger().records().stream().allMatch(op -> op.settlement().isEmpty() && op.reason() == EscrowReason.OTHER_STATE)
                        && first.escrowLocked() && second.escrowLocked() && !RocketEscrowGate.idle(rocket), "S6 pre-O1 chain changed its hold");
            } else {
                if (!data.ledger().records().isEmpty() || !section(first).markers().isEmpty()
                        || !section(second).markers().isEmpty() || !rocket.escrowSection().markers().isEmpty()) { return; }
                EscrowTags.require(data.ledger().tombstones().size() == 2
                        && data.ledger().tombstones().stream().allMatch(t -> t.getBoolean("released"))
                        && !first.escrowLocked() && !second.escrowLocked() && RocketEscrowGate.idle(rocket), "S6 chain did not release its complete decision");
            }
            if (server.getTickCount() - began < 100) { return; }
            done = true;
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CHAIN_RESULT cut={} tanks=4000,4000 banks={} epochs=1,1,2 records={} receipts={} state={}",
                    cut, bi ? "1000,1000" : "2000,0", before ? 2 : 0, before ? 0 : 2, before ? "PRE_O1" : "RELEASED");
            return;
        }
        if (!baseline) {
            EscrowTags.require(data.ledger().records().size() == 2, "S6 chain lost its manual component");
            for (var op : data.ledger().records()) {
                if (!EscrowLoaderS6SettlementProbe.manualHoldDurable(op.reason())) { return; }
            }
            // Pre-operator experiment baseline only; no endpoint save is added after any selected cut.
            EscrowTags.require(server.saveEverything(false, true, true), "S6 chain pre-decision baseline refused");
            if (veto) { installVeto(); }
            baseline = true; return;
        }
        if (queued) { return; }
        var selected = data.ledger().records().get(0).id(); var operator = EscrowRuntime.of(server).settlement();
        var preview = operator.preview(server, selected, EscrowSettlementIntent.Choice.ACCEPT_CURRENT, ACTOR, "Server");
        EscrowTags.require(preview.members().size() == 2 && preview.observations().size() == 3, "S6 chain preview omitted a member or endpoint");
        writingIntent = true;
        queued = operator.confirm(server, selected, preview.choice(), ACTOR, preview.digest(), committed -> {
            EscrowTags.require(committed, "S6 chain operator intent was not acknowledged");
            if (boundary(cut, "O1_ACK")) { pause("O1_ACK"); }
            writingIntent = false;
        });
        if (!queued) { writingIntent = false; }
    }
    private void pause(String hook) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_CHAIN_PAUSED cut={} hook={}", cut, hook);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("S6 chain forced stop did not arrive within 30 seconds");
    }
}
