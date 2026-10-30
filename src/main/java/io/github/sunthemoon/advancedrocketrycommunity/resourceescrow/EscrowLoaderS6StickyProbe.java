package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** R203's single native representative; capacity remains separately measured by the reservation units. */
final class EscrowLoaderS6StickyProbe {
    static final String CASE = "LF_S6_STICKY_RESERVATION";
    private static final BlockPos DEVICE = new BlockPos(66, 100, 64);
    private final MinecraftServer server;
    private final boolean recovery;
    private final EscrowLoaderCutProbe setup;
    private boolean held, done;
    private long began = -1;

    static boolean selected(boolean hooks, String cut) { return hooks && CASE.equals(cut); }
    static boolean boundary(String point) { return "C6_JOINT_READBACK".equals(point); }
    static boolean durable(EscrowOperation operation) {
        if (operation.reason() == EscrowReason.NONE) { return false; }
        EscrowTags.require(operation.reason() == EscrowReason.JOURNAL_GUARD_MISMATCH,
                "Sticky representative lost its selected durable repair reason");
        return true;
    }
    EscrowLoaderS6StickyProbe(MinecraftServer server, boolean recovery) {
        EscrowTestEndpoints.requireEnabled(server);
        EscrowTags.require(selected(Boolean.getBoolean(EscrowTestEndpoints.HOOKS),
                System.getProperty("advancedrocketrycommunity.escrowCase")), "Sticky requires its exact release gate");
        this.server = server; this.recovery = recovery;
        setup = recovery ? null : new EscrowLoaderCutProbe(server, "LF_M_K7", false);
    }
    void startTick() { if (!held && setup != null) { setup.startTick(); } }
    void checkpoint(String point) {
        if (recovery || held || done) { return; }
        if (!boundary(point)) { setup.checkpoint(point); return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.ledger().records().size() == 1, "Sticky lost its proved real fill");
        held = true;
        var coordinator = data.bind(server);
        coordinator.transition(data.ledger().records().get(0).id(), EscrowReason.JOURNAL_GUARD_MISMATCH);
        coordinator.persistTransitions();
    }
    void tick() {
        if (done) { return; }
        if (began < 0) { began = server.getTickCount(); }
        EscrowTags.require(server.getTickCount() - began <= 2_400, "Sticky exceeded the existing fixture bound");
        if (!recovery && !held) { setup.tick(); return; }
        var level = server.overworld();
        if (!level.isPositionEntityTicking(DEVICE) || !(level.getBlockEntity(DEVICE) instanceof FluidFuelLoaderBlockEntity loader)) { return; }
        if (!(level.getEntity(loader.root().orElseThrow().binding().physicalRocket()) instanceof RocketEntity rocket)) { return; }
        var data = ResourceEscrowSavedData.existing(server);
        EscrowTags.require(data != null && data.ledger().records().size() == 1, "Sticky lost its pending operation");
        var coordinator = data.bind(server);
        if (!coordinator.writerIdle() || !durable(data.ledger().records().get(0))) { return; }
        var operation = data.ledger().records().get(0);
        EscrowTags.require(data.ledger().writerGeneration() == 3 && data.ledger().issuedThrough() == 1
                && operation.settlement().isEmpty() && coordinator.admissionAuthority().pendingCount() == 1
                && coordinator.locked(operation.source().key()) && coordinator.locked(operation.destination().key())
                && loader.escrowLocked() && !RocketEscrowGate.idle(rocket)
                && amount(loader) == 4_000 && bank(rocket, false) == 1_000 && bank(rocket, true) == 0
                && section(loader).epoch() == 1 && rocket.escrowSection().epoch() == 1
                && section(loader).markers().size() == 1 && rocket.escrowSection().markers().size() == 1
                && RocketTransferSavedData.get(server).entries().isEmpty(), "Sticky changed its checked native hold");
        if (recovery && server.getTickCount() - began < 100) { return; }
        done = true;
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_STICKY_STATE cut={} generation=3 records=1 pending=1 reason=JOURNAL_GUARD_MISMATCH tank=4000 bank=1000 epochs=1,1 markers=1,1", CASE);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_STICKY_{} cut={}", recovery ? "RESULT" : "PAUSED", CASE);
        if (recovery) { return; }
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException failure) { Thread.currentThread().interrupt(); throw new IllegalStateException(failure); }
        throw new IllegalStateException("Sticky forced stop did not arrive within 30 seconds");
    }
}
