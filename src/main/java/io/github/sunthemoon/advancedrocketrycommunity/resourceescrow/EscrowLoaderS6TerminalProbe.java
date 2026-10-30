package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Base64;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;

/** Read-only selection of the product's ordered F6 absence/survivor proofs. */
final class EscrowLoaderS6TerminalProbe {
    private static final Set<String> CASES = Set.of("LF_S6_F6_PROOF_BEFORE_0", "LF_S6_F6_PROOF_AFTER_0",
            "LF_S6_F6_PROOF_BEFORE_1", "LF_S6_F6_PROOF_AFTER_1");
    private final MinecraftServer server;
    private final String cut;
    private final AtomicInteger completed = new AtomicInteger();
    private volatile boolean landed;

    EscrowLoaderS6TerminalProbe(MinecraftServer server, String cut) { this.server = server; this.cut = cut; }
    static boolean selected(String cut) { return cut != null && CASES.contains(cut); }
    static boolean boundary(String cut, String point, boolean landed, int completed) {
        return landed && selected(cut) && completed >= 0 && completed < 2 && cut.endsWith("_" + completed)
                && point.equals(cut.contains("_BEFORE_") ? "BARRIER_STORE_BEFORE" : "BARRIER_READBACK_AFTER");
    }

    static String capture(CompoundTag payload) {
        EscrowLoaderS6JournalProbe.transferDigest(payload);
        try {
            var bytes = new ByteArrayOutputStream();
            NbtIo.write(payload, new DataOutputStream(bytes));
            EscrowTags.require(bytes.size() <= 65_536, "S6 checked capture exceeds its fixed witness bound");
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static void observeCapture(String cut, CompoundTag payload) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_CAPTURE cut={} nbt={}", cut, capture(payload));
    }

    void checkpoint(String point) {
        if (point.equals("F6_LANDED")) {
            var row = RocketTransferSavedData.get(server).entries().stream().findFirst().orElseThrow();
            var arrival = (RocketEntity) server.overworld().getEntity(row.destinationEntityId().orElseThrow());
            var state = arrival.flightData().orElseThrow();
            AdvancedRocketryCommunity.LOGGER.info("ARCE_LOADER_S6_FLIGHT_LANDED cut={} landed_time={} digest={}",
                    cut, state.stateStartedGameTime(), EscrowGovernedEnvelope.digest(EscrowGovernedEnvelope.capture(arrival)));
            landed = true;
        }
        int index = completed.get();
        if (boundary(cut, point, landed, index)) {
            EscrowLoaderS6JournalProbe.pause(cut, point + "_" + index);
        }
        if (landed && point.equals("BARRIER_READBACK_AFTER")) { completed.incrementAndGet(); }
    }
}
