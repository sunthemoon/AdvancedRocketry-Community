package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;

/** Disposable native process-stop seams. No property means no change to production stores. */
public final class RocketReleaseNativeCuts {
    public static final String PROPERTY = "advancedrocketrycommunity.rejoinReleaseProbe";
    public static final String RECOVERY_CUT = "advancedrocketrycommunity.rejoinReleaseRecoveryCut";
    private static final AtomicBoolean ARMED = new AtomicBoolean(), PAUSED = new AtomicBoolean();
    private RocketReleaseNativeCuts() { }

    public static String selection() { return System.getProperty(PROPERTY, ""); }
    public static void validate(MinecraftServer server) {
        EscrowTestEndpoints.requireEnabled(server);
        String[] parts = selection().split(":");
        if (!server.isDedicatedServer() || parts.length != 3 || !parts[0].matches("H[12]")
                || !parts[1].matches("C(?:[0-6]|14|15|16|17)") || !parts[2].matches("seed|recover|check")
                || !server.getWorldData().getLevelName().equals("world-W158-" + parts[0] + "-" + parts[1])) {
            throw new IllegalStateException("Not a disposable W158 release server");
        }
        selectedBoundary(selection(), System.getProperty(RECOVERY_CUT, ""));
    }
    public static CheckedSavedDataPolicy policy(MinecraftServer server, ManagedSavedDataType type) {
        if (selection().isEmpty()) { return CheckedSavedDataPolicy.atomic(type); }
        validate(server);
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            var payload = CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
            boolean release = type != ManagedSavedDataType.ROCKET_TRANSFERS || ARMED.get()
                    && CheckedSavedDataFile.readPayload(target.resolveSibling(ManagedSavedDataType.ROCKET_REJOINS.fileName()),
                            ManagedSavedDataType.ROCKET_REJOINS).map(RocketRejoinCodec::decode)
                            .map(root -> journalRelease(payload, root)).orElse(false);
            if (release) { checkpoint(replacement(type, payload, false)); }
            CheckedSavedDataFile.atomicMove(staged, target);
            if (release) { checkpoint(replacement(type, payload, true)); }
        });
    }
    static boolean journalRelease(CompoundTag candidate, RocketRejoinRoot root) {
        for (var batch : root.batches()) {
            if (root.entries().stream().noneMatch(e -> e.release().equals(batch.release()) && e.seat() == RocketRejoinRoot.Seat.PENDING)) { continue; }
            var record = candidate.getList("transfers", 10).stream().map(CompoundTag.class::cast)
                    .filter(t -> t.hasUUID("transfer_id") && t.getUUID("transfer_id").equals(batch.transfer())).findFirst().orElse(null);
            if (record == null) { if (batch.hold() == RocketRejoinRoot.Hold.H1) { return true; } continue; }
            var version = batch.scrubbed();
            if (!version.equals(batch.before()) && record.getString("checksum").equals(version.checksum())
                    && record.getString("phase").equals(version.phase())
                    && (version.destination() == null ? !record.contains("destination_entity_id")
                        : record.hasUUID("destination_entity_id") && record.getUUID("destination_entity_id").equals(version.destination()))) { return true; }
        }
        return false;
    }
    static String replacement(ManagedSavedDataType type, CompoundTag candidate, boolean after) {
        if (type == ManagedSavedDataType.ROCKET_TRANSFERS) { return after ? "C5" : ""; }
        boolean pending = candidate.getList("entries", 10).stream()
                .anyMatch(raw -> ((CompoundTag) raw).getString("seat").equals("PENDING"));
        return pending ? after ? "C2" : "C1" : after ? "C6" : "S2_PENDING";
    }
    public static void arm(MinecraftServer server) {
        if (!selection().isEmpty()) { validate(server); ARMED.set(true); }
    }
    /** Restored work starts at S1, without the B0 notice that arms a fresh admission. */
    public static void armRestored(MinecraftServer server, RocketRejoinRoot root) {
        if (!selection().isEmpty() && needsRecoveryArm(root)) { arm(server); }
    }
    static boolean needsRecoveryArm(RocketRejoinRoot root) {
        return root.entries().stream().anyMatch(e -> e.seat() == RocketRejoinRoot.Seat.PENDING);
    }
    static String selectedBoundary(String selection, String recovery) {
        if (!recovery.isEmpty() && !java.util.Set.of("B1_CAPTURE", "C3", "C4", "C5", "S2_PENDING", "C6").contains(recovery)) {
            throw new IllegalArgumentException("Invalid W158 recovery cut");
        }
        var parts = selection.split(":");
        if (parts.length != 3) { return ""; }
        return switch (parts[2]) {
            case "seed" -> parts[1].equals("C17") ? "C6" : parts[1];
            case "recover" -> recovery;
            default -> "";
        };
    }
    public static void checkpoint(String boundary) {
        String selected = selection();
        if (boundary.isEmpty() || !ARMED.get()
                || !boundary.equals(selectedBoundary(selected, System.getProperty(RECOVERY_CUT, "")))
                || !PAUSED.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W158_PAUSED case={} boundary={}", selected, boundary);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        throw new IllegalStateException("W158 external process stop did not arrive in 30 seconds");
    }
    public static void reset() { ARMED.set(false); PAUSED.set(false); }
}
