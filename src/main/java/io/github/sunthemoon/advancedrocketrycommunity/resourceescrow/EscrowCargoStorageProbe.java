package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.event.level.ChunkDataEvent;

/** W138 observations and fixture faults; no production admission or proof decisions. */
final class EscrowCargoStorageProbe {
    static final String E1 = "FC_LOAD_E1_OWN_STORE";
    static final String E2 = "FC_LOAD_E2_CAPTURE";
    static final String EXTERNAL = "FC_LOAD_E2_ROCKET_EXTERNAL";
    static final int NOISE_BYTES = 1_048_576;
    private static final Set<String> CASES = Set.of(E1, E2, EXTERNAL,
            "FC_LOAD_E4L1_FAIL", "FC_LOAD_E4L1_UNKNOWN", "FC_LOAD_E4L2_UNKNOWN");
    private final MinecraftServer server;
    private final String name;
    private final Consumer<String> pause;
    private boolean injected;
    private Consumer<ChunkDataEvent.Save> listener;

    static boolean selected(boolean hooks, String name, boolean recovery) {
        return hooks && !recovery && name != null && CASES.contains(name);
    }
    static String writerCase(String name) {
        return switch (name) {
            case "FC_LOAD_E4L1_UNKNOWN" -> "E4L1_UNKNOWN";
            case "FC_LOAD_E4L2_UNKNOWN" -> "E4L2_UNKNOWN";
            default -> name;
        };
    }
    static boolean d2Cut(boolean hooks, String name, boolean recovery, int stores, String label) {
        if (!hooks || recovery || name == null || !name.matches("FC_(LOAD|UNLOAD)_D2_C[2567]")) { return false; }
        return switch (name.substring(name.length() - 2)) {
            case "C2" -> label.equals("C2_DESTINATION_AFTER");
            case "C5" -> stores == 2 && label.equals("BARRIER_FORCE_AFTER");
            case "C6" -> label.equals("C6_JOINT_READBACK");
            case "C7" -> label.equals("K9");
            default -> false;
        };
    }
    EscrowCargoStorageProbe(MinecraftServer server, String name, Consumer<String> pause) {
        EscrowTestEndpoints.requireEnabled(server);
        this.server = server; this.name = name; this.pause = pause;
    }
    void checkpoint(String label, RocketEntity rocket, EscrowCargoProbeDevice device) {
        if (!injected && name.equals("FC_LOAD_E4L1_FAIL") && label.equals("K1")) {
            injected = true; EscrowReleaseProbe.definiteFailure();
        }
        if (!injected && name.equals(EXTERNAL) && label.equals("C2_DESTINATION_AFTER")) {
            injected = true;
            byte[] bytes = new byte[NOISE_BYTES]; new java.util.Random(0x138L).nextBytes(bytes);
            rocket.getPersistentData().putByteArray("arce_w138_noise", bytes);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_STORAGE_NOISE cut={} bytes={}", name, bytes.length);
        }
        if (!injected && name.equals(E2) && label.equals("C4_SOURCE_SELECTED") && listener == null) {
            listener = event -> {
                if (injected || event.getLevel() != server.overworld() || !event.getChunk().getPos().equals(EscrowStoreFault.SLOT)) { return; }
                injected = true;
                EscrowReleaseProbe.newerSave(server.overworld(), new BlockPos(64, 100, 64));
            };
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(listener);
        }
        if (name.startsWith("FC_LOAD_E4") && (label.equals("WRITE_UNKNOWN") || label.equals("WRITE_FAILED_BEFORE_REPLACE"))) {
            var data = ResourceEscrowSavedData.existing(server); var coordinator = data.bind(server);
            boolean l1 = name.startsWith("FC_LOAD_E4L1"), unknown = label.equals("WRITE_UNKNOWN");
            int epoch = l1 ? 0 : 1;
            EscrowTags.require(data.ledger().records().size() == epoch && data.ledger().issuedThrough() == epoch
                    && coordinator.admissionAuthority().pendingCount() == (l1 && !unknown ? 0 : 1)
                    && coordinator.writerUnknown() == unknown && coordinator.writerIdle() != unknown
                    && device.amount() == 5_000 - epoch * 1_000 && device.section().epoch() == epoch
                    && rocket.escrowSection().epoch() == epoch, "Cargo writer fault changed resources or reservations");
            AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_STORAGE_WRITER cut={} unknown={} pending={} records={}",
                    name, unknown, coordinator.admissionAuthority().pendingCount(), data.ledger().records().size());
            pause.accept(label);
        }
    }
    void tick(ResourceEscrowSavedData data) {
        var runtime = EscrowRuntime.of(server); var barrier = runtime.barrier();
        if (name.equals(E2) && injected && barrier.supersedingSaves() > 0) {
            close();
            server.overworld().getChunkSource().chunkMap.worker.synchronize(true).join();
        } else if (!(name.equals(E1) || name.equals(EXTERNAL))
                || barrier.ioFailures() + barrier.refusals() + barrier.supersedingSaves() == 0) { return; }
        var coordinator = data.bind(server);
        EscrowTags.require(data.ledger().records().size() == 1, "Cargo proof failure lost L1");
        var op = data.ledger().records().get(0);
        EscrowTags.require(coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                && coordinator.admissionAuthority().pendingCount() == 1 && !coordinator.reconciliationCheckpointProved(op.id())
                && op.phase() == EscrowOperation.Phase.PREPARED, "Failed cargo proof released authority");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_CARGO_STORAGE_PROOF cut={} reason={} io={} refusals={} superseding={} records=1 pending=1",
                name, runtime.reconciliationScheduler().view(op.id()).reason(), barrier.ioFailures(), barrier.refusals(), barrier.supersedingSaves());
        pause.accept(name.substring(8));
    }
    void close() {
        if (listener != null) { net.minecraftforge.common.MinecraftForge.EVENT_BUS.unregister(listener); listener = null; }
    }
    static void heldRestart(MinecraftServer server, ResourceEscrowSavedData data, RocketEntity rocket,
                            EscrowCargoProbeDevice device, String name) {
        var coordinator = data.bind(server);
        EscrowTags.require(data.ledger().records().size() == 1, "External cargo record retired on restart");
        var op = data.ledger().records().get(0);
        EscrowTags.require(coordinator.locked(op.source().key()) && coordinator.locked(op.destination().key())
                && coordinator.logicalLocked(rocket.assemblyTransactionId().orElseThrow())
                && device.amount() == 4_000 && device.section().epoch() == 1 && rocket.escrowSection().epoch() == 1
                && !coordinator.reconciliationCheckpointProved(op.id()), "External cargo restart released authority");
        AdvancedRocketryCommunity.LOGGER.info("ARCE_RELEASE_CARGO_RESULT cut={} reason={} records=1 locked=true",
                name, EscrowRuntime.of(server).reconciliationScheduler().view(op.id()).reason());
    }
}
