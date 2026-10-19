package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.FluidFuelLoaderEscrowFixtures.*;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.level.ChunkDataEvent;

/** Real-loader selections of the existing E2/E4 release faults; no new failure mechanism. */
final class EscrowLoaderStorageProbe {
    private final MinecraftServer server;
    private final String cut, boundary;
    private final Consumer<String> pause;
    private boolean injected, resave, resaved, capture;
    private Consumer<ChunkDataEvent.Save> listener;

    EscrowLoaderStorageProbe(MinecraftServer server, String cut, String boundary, Consumer<String> pause) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.cut = cut; this.boundary = boundary; this.pause = pause;
    }

    static boolean selected(String boundary) { return boundary.equals("E2_CAPTURE") || boundary.matches("E4L[12]_(FAIL|UNKNOWN)|E4L2_RESAVE(_RESOLVED)?"); }

    /** Aliases select the already gated committer, including its existing one-shot resave variant. */
    static String writerCase(String selected) {
        return switch (selected) {
            case "LF_M_E4L1_UNKNOWN" -> "E4L1_UNKNOWN";
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
        if (name.equals("WRITE_UNKNOWN") && boundary.startsWith("E4")) {
            retained(loader, rocket, true);
            if (boundary.startsWith("E4L2_RESAVE")) { resave = true; }
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
    }

    private void retained(FluidFuelLoaderBlockEntity loader, RocketEntity rocket, boolean unknown) {
        var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
        boolean l1 = boundary.startsWith("E4L1");
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
