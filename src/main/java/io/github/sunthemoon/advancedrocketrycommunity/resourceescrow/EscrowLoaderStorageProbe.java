package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.level.ChunkDataEvent;

/**
 * Real-loader selections of the existing E2/E4 release faults, plus W107C's two R43 storage cuts: E1's one gated
 * own-store fault ({@link EscrowStoreFault}) and E2's incompressible fixture data, which vanilla writes as an external
 * {@code .mcc}. Neither changes a decision, an order, a saved format, a proof or a lease.
 */
final class EscrowLoaderStorageProbe {
    /** E2_EXTERNAL's fixture chest, in the source loader's terrain chunk (4,4) but clear of the loader and rocket. */
    static final net.minecraft.core.BlockPos NOISE = new net.minecraft.core.BlockPos(76, 100, 76);
    /** 1.5 MiB of incompressible bytes: past vanilla's 1 MiB inline record, inside the barrier's 4 MiB decoded bound. */
    static final int NOISE_BYTES = 1_572_864;
    private final MinecraftServer server;
    private final String cut, boundary;
    private final Consumer<String> pause;
    private final Runnable unload;
    private boolean injected, resave, resaved, capture;
    private Consumer<ChunkDataEvent.Save> listener;

    EscrowLoaderStorageProbe(MinecraftServer server, String cut, String boundary, Consumer<String> pause, Runnable unload) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.cut = cut; this.boundary = boundary; this.pause = pause; this.unload = unload;
    }

    static boolean selected(String boundary) {
        return boundary.equals("R1_UNKNOWN_UNLOAD") || boundary.equals("E2_CAPTURE") || boundary.equals("E1_OWN_STORE")
                || boundary.equals("E2_EXTERNAL") || boundary.matches("E4L[12]_(FAIL|UNKNOWN)|E4L2_RESAVE(_RESOLVED)?");
    }

    /**
     * R43 item 3, before the fixture's baseline save: deterministic random bytes in a vanilla chest's Forge data in the
     * captured source chunk, so every save of that chunk, the barrier's own store included, is an external record.
     */
    void place(net.minecraft.server.level.ServerLevel level) {
        if (!boundary.equals("E2_EXTERNAL")) { return; }
        level.setBlockAndUpdate(NOISE, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
        var chest = level.getBlockEntity(NOISE);
        EscrowTags.require(chest != null && new net.minecraft.world.level.ChunkPos(NOISE).equals(EscrowStoreFault.SLOT), "Noise fixture was not placed");
        var bytes = new byte[NOISE_BYTES]; new java.util.Random(0x107CL).nextBytes(bytes);
        chest.getPersistentData().putByteArray("arce_w107c_noise", bytes); chest.setChanged();
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_NOISE cut={} pos={},{},{} bytes={}", cut, NOISE.getX(), NOISE.getY(),
                NOISE.getZ(), NOISE_BYTES);
    }

    /** Aliases select the already gated committer, including its existing one-shot resave variant. */
    static String writerCase(String selected) {
        return switch (selected) {
            case "LF_M_E4L1_UNKNOWN", "LF_M_R1_UNKNOWN_UNLOAD" -> "E4L1_UNKNOWN";
            case "LF_M_E4L2_UNKNOWN" -> "E4L2_UNKNOWN";
            case "LF_M_E4L2_RESAVE", "LF_M_E4L2_RESAVE_RESOLVED" -> "E4L2_RESAVE";
            default -> selected;
        };
    }

    void checkpoint(String name, FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
        if (!injected && (boundary.equals("E4L1_FAIL") && name.equals("K1")
                || boundary.equals("E4L2_FAIL") && name.equals("K8"))) {
            injected = true; EscrowReleaseProbe.definiteFailure();
        }
        if (boundary.equals("E2_CAPTURE") && !injected && name.equals("C4_SOURCE_SELECTED") && listener == null) {
            capture = true;
            listener = event -> {
                if (!capture || injected || event.getLevel() != server.overworld()
                        || !event.getChunk().getPos().equals(new net.minecraft.world.level.ChunkPos(loader.getBlockPos()))) { return; }
                injected = true;
                EscrowReleaseProbe.newerSave(server.overworld(), loader.getBlockPos());
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        }
        if (name.equals("WRITE_UNKNOWN") && (boundary.startsWith("E4") || boundary.equals("R1_UNKNOWN_UNLOAD"))) {
            retained(loader, rocket, true);
            if (boundary.equals("R1_UNKNOWN_UNLOAD")) { unload.run(); }
            else if (boundary.startsWith("E4L2_RESAVE")) { resave = true; }
            else { pause.accept(name); }
        }
        if (name.equals("WRITE_FAILED_BEFORE_REPLACE") && boundary.startsWith("E4")) {
            retained(loader, rocket, false);
            pause.accept(name);
        }
    }

    void tick(FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
        var data = ResourceEscrowSavedData.existing(server);
        if (resave && !resaved) {
            resaved = true;
            var coordinator = data.bind(server); var op = data.ledger().records().get(0);
            EscrowTags.require(coordinator.reconciliationCheckpointProved(op.id()), "L2 lost proof before the overlapping save");
            EscrowReleaseProbe.newerSave(server.overworld(), loader.getBlockPos());
            EscrowTags.require(!coordinator.reconciliationCheckpointProved(op.id()), "Overlapping save kept an old L2 proof valid");
            retained(loader, rocket, true);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_RESAVE cut={} proof_before=true proof_after=false", cut);
            if (boundary.equals("E4L2_RESAVE")) { pause.accept("E4L2_RESAVE"); }
        }
        if (boundary.equals("E2_CAPTURE") && injected && EscrowRuntime.of(server).barrier().supersedingSaves() == 1) {
            close();
            var coordinator = data.bind(server); var op = data.ledger().records().get(0);
            EscrowTags.require(data.ledger().records().size() == 1 && coordinator.locked(op.source().key())
                    && coordinator.locked(op.destination().key()) && !coordinator.reconciliationCheckpointProved(op.id())
                    && EscrowRuntime.of(server).barrier().ioFailures() == 0, "Superseding save released or proved the fill");
            // The original E2_CAPTURE fixture forces the newer vanilla write, not an own-store proof.
            server.overworld().getChunkSource().chunkMap.worker.synchronize(true).join();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_SUPERSEDED cut={} saves=1 io_failures=0 records=1 pending=1", cut);
            pause.accept("E2_CAPTURE");
        }
        if (boundary.equals("E1_OWN_STORE") || boundary.equals("E2_EXTERNAL")) { proofFailure(data, loader, rocket); }
    }

    /**
     * E1 and E2 stop at the first failed proof of any kind and log the reason that reconciliation actually received
     * (E1 expects BARRIER_IO, E2 BARRIER_EXTERNAL; the harness compares, so another refusal is recorded, not hidden).
     * Whatever the reason, the fill must keep its durable L1, both locks and its reservation: no L2 and no unlock.
     */
    private void proofFailure(ResourceEscrowSavedData data, FluidFuelLoaderBlockEntity loader, RocketEntity rocket) {
        var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier();
        if (data == null || barrier.ioFailures() + barrier.refusals() + barrier.supersedingSaves() == 0) { return; }
        var coordinator = data.bind(server); var op = data.ledger().records().get(0);
        EscrowTags.require(data.ledger().records().size() == 1 && data.ledger().issuedThrough() == 1
                && coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                && !coordinator.reconciliationCheckpointProved(op.id()) && coordinator.admissionAuthority().pendingCount() == 1
                && op.phase() == EscrowOperation.Phase.PREPARED && loader.escrowLocked() && !RocketEscrowGate.idle(rocket),
                "A failed proof released, retired or reclassified the fill");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_PROOF_FAILED cut={} reason={} io_failures={} refusals={} superseding={} records=1 pending=1",
                cut, runtime.reconciliationScheduler().view(op.id()).reason(), barrier.ioFailures(), barrier.refusals(), barrier.supersedingSaves());
        pause.accept(boundary);
    }

    private void retained(FluidFuelLoaderBlockEntity loader, RocketEntity rocket, boolean unknown) {
        var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
        boolean l1 = boundary.startsWith("E4L1") || boundary.equals("R1_UNKNOWN_UNLOAD");
        int pending = l1 && !unknown ? 0 : 1;
        EscrowTags.require(data.ledger().records().size() == (l1 ? 0 : 1)
                && data.ledger().issuedThrough() == (l1 ? 0 : 1)
                && coordinator.admissionAuthority().pendingCount() == pending
                && coordinator.writerUnknown() == unknown && coordinator.writerIdle() != unknown
                && amount(loader) == (l1 ? 5_000 : 4_000) && bank(rocket, false) == (l1 ? 0 : 1_000)
                && section(loader).epoch() == (l1 ? 0 : 1) && rocket.escrowSection().epoch() == (l1 ? 0 : 1)
                && section(loader).markers().size() == (l1 ? 0 : 1) && rocket.escrowSection().markers().size() == (l1 ? 0 : 1),
                "Failure changed native resources, authority or uncertain reservations");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_MATRIX_FAILURE cut={} unknown={} records={} pending={} issued={}",
                cut, unknown, data.ledger().records().size(), pending, data.ledger().issuedThrough());
    }

    void close() {
        if (listener != null) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); listener = null; }
    }
}
