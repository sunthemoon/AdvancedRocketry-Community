package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** R156 release-test writer seams. The journal owns the state; no production save or timeout changes. */
final class RocketI6NativeCut {
    static final String PROPERTY = "advancedrocketrycommunity.i6NativeCut";
    private static final ManagedSavedDataType TYPE = ManagedSavedDataType.ROCKET_TRANSFERS;
    private static final Set<String> MODES = Set.of("PLAIN_MOVE", "PLAIN_MOVED", "PLAIN_UNKNOWN", "PLAIN_ACK",
            "K1", "K2", "K3_MOVE", "K3_UNKNOWN", "K4", "K9_REFUSED", "K9_LATE", "K10_ON", "K10_OFF", "K10_FAIL");
    private RocketI6NativeCut() { }

    static String selection(MinecraftServer server) {
        String mode = System.getProperty(PROPERTY, "");
        if (server == null || mode.isEmpty() || !Boolean.getBoolean(EscrowTestEndpoints.HOOKS)) { return ""; }
        EscrowTestEndpoints.requireEnabled(server);
        if (!MODES.contains(mode)) { throw new IllegalArgumentException("Unknown I6 native cut"); }
        return mode;
    }

    static CheckedSavedDataPolicy live(String mode, MinecraftServer server) {
        return policy(mode, boundary -> {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_W164_CUT boundary={}", boundary);
            if (boundary.equals("PLAIN_MOVE") || boundary.equals("PLAIN_MOVED") || boundary.equals("K1")
                    || boundary.equals("K3_MOVE") || boundary.equals("K10_REMOVAL_MOVED")) {
                RocketJournalRemovalCut.awaitBounded(TimeUnit.SECONDS.toNanos(20));
                // K10 deliberately completes its writer wait during a normal stop, rather than a hard cut.
                if (!boundary.equals("K10_REMOVAL_MOVED")) {
                    AdvancedRocketryCommunity.LOGGER.warn("ARCE_W164_CUT_EXPIRED boundary={}", boundary);
                }
            }
        }, () -> !server.isRunning() || server.isStopped());
    }

    static CheckedSavedDataPolicy policy(String mode, Consumer<String> boundary, BooleanSupplier stopping) {
        if (!MODES.contains(mode)) { throw new IllegalArgumentException("Unknown I6 native cut"); }
        // Accessed only by this journal's single writer. Survives policy lookup on later owner ticks and final saving.
        final class State { CompoundTag removed; boolean unknown, refused, held; }
        var state = new State();
        return new CheckedSavedDataPolicy(TYPE, (staged, target) -> {
            var candidate = CheckedSavedDataFile.readPayload(staged, TYPE).orElseThrow();
            var previous = CheckedSavedDataFile.readPayload(target, TYPE).orElse(null);
            CompoundTag removed = removed(candidate, previous);
            if (removed != null && state.removed == null) { state.removed = removed.copy(); }
            boolean writeback = state.removed != null && contains(candidate, state.removed)
                    && !contains(previous, state.removed);
            if (removed != null) {
                if (mode.equals("PLAIN_MOVE") || mode.equals("K1")) { boundary.accept(mode); }
                if ((mode.equals("K9_REFUSED") || mode.equals("K9_LATE")) && !state.refused) {
                    state.refused = true;
                    boundary.accept(mode.equals("K9_LATE") ? "K9_LATE_REFUSED" : mode);
                    throw new IOException("I6 test refusal before the removal move");
                }
            }
            if (writeback && mode.equals("K3_MOVE")) { boundary.accept(mode); }
            if (writeback && mode.equals("K10_FAIL") && stopping.getAsBoolean()) {
                boundary.accept("K10_FINAL_WRITE_REFUSED");
                throw new IOException("I6 test final write-back refusal before replacement");
            }
            CheckedSavedDataFile.atomicMove(staged, target);
            if (removed != null && mode.equals("PLAIN_MOVED")) { boundary.accept(mode); }
            if (removed != null && mode.startsWith("K10_") && !state.held) {
                state.held = true; boundary.accept("K10_REMOVAL_MOVED");
            }
            if (!state.unknown && (removed != null && mode.equals("PLAIN_UNKNOWN")
                    || writeback && mode.equals("K3_UNKNOWN"))) {
                state.unknown = true; boundary.accept(mode);
            }
            if (state.unknown) { throw new IOException("I6 test unknown result after replacement"); }
        }, (file, type) -> {
            if (state.unknown) { throw new IllegalStateException("I6 test unknown readback"); }
            return CheckedSavedDataFile.readPayload(file, type);
        });
    }

    private static boolean contains(CompoundTag root, CompoundTag expected) {
        return root != null && root.getList("transfers", 10).stream().anyMatch(expected::equals);
    }

    private static CompoundTag removed(CompoundTag candidate, CompoundTag previous) {
        if (previous == null) { return null; }
        for (var tag : previous.getList("transfers", 10)) {
            var old = (CompoundTag) tag;
            if (!old.getString("phase").equals("COMMITTED") || old.contains("native_transfer")
                    || !old.hasUUID("transfer_id")) { continue; }
            boolean kept = candidate.getList("transfers", 10).stream().map(CompoundTag.class::cast)
                    .anyMatch(next -> next.hasUUID("transfer_id") && next.getUUID("transfer_id").equals(old.getUUID("transfer_id")));
            if (!kept) { return old; }
        }
        return null;
    }
}
