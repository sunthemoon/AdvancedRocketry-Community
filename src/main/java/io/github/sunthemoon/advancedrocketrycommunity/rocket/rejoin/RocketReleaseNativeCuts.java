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
    }
    public static CheckedSavedDataPolicy policy(MinecraftServer server, ManagedSavedDataType type) {
        if (selection().isEmpty()) { return CheckedSavedDataPolicy.atomic(type); }
        validate(server);
        return new CheckedSavedDataPolicy(type, (staged, target) -> {
            var payload = CheckedSavedDataFile.readPayload(staged, type).orElseThrow();
            checkpoint(replacement(type, payload, false));
            CheckedSavedDataFile.atomicMove(staged, target);
            checkpoint(replacement(type, payload, true));
        });
    }
    static String replacement(ManagedSavedDataType type, CompoundTag candidate, boolean after) {
        if (type == ManagedSavedDataType.ROCKET_TRANSFERS) { return after ? "C5" : ""; }
        boolean pending = candidate.getList("entries", 10).stream()
                .anyMatch(raw -> ((CompoundTag) raw).getString("seat").equals("PENDING"));
        return pending ? after ? "C2" : "C1" : after ? "C6" : "";
    }
    public static void arm(MinecraftServer server) {
        if (!selection().isEmpty()) { validate(server); ARMED.set(true); }
    }
    public static void checkpoint(String boundary) {
        String selected = selection();
        if (boundary.isEmpty() || !ARMED.get() || !selected.endsWith(":" + "seed")
                || !(selected.split(":")[1].equals(boundary)
                    || selected.contains(":C17:") && boundary.equals("C6")) || !PAUSED.compareAndSet(false, true)) { return; }
        AdvancedRocketryCommunity.LOGGER.info("ARCE_W158_PAUSED case={} boundary={}", selected, boundary);
        try { TimeUnit.SECONDS.sleep(30); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        throw new IllegalStateException("W158 external process stop did not arrive in 30 seconds");
    }
    public static void reset() { ARMED.set(false); PAUSED.set(false); }
}
