package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * W98's gated release-test pause for the N3 native cuts, apart from the coordinator (W116, W98-R10). Silent unless a
 * release-test run sets {@link EscrowTestEndpoints#HOOKS} and selects one boundary with {@link #CUT_PROPERTY}; it then
 * pauses there once, for 20 seconds, and changes no state. Production never sets either property.
 */
final class RocketRetirementTestHook {
    static final String CUT_PROPERTY = "advancedrocketrycommunity.ordinaryRetirementCut";
    static final String REMOVAL_BEFORE = "ORDINARY_REMOVAL_BEFORE";

    private RocketRetirementTestHook() { }

    /** One attempt's sink: silent, or a single pause at the selected boundary. */
    static Consumer<String> sink(MinecraftServer server) {
        String cut = Boolean.getBoolean(EscrowTestEndpoints.HOOKS) ? System.getProperty(CUT_PROPERTY, "") : "";
        if (cut.isEmpty()) { return label -> { }; }
        EscrowTestEndpoints.requireEnabled(server);
        var once = new AtomicBoolean();
        int separator = cut.lastIndexOf(':');
        String selected = separator < 0 ? cut : cut.substring(0, separator);
        int occurrence = separator < 0 ? 1 : Integer.parseInt(cut.substring(separator + 1));
        if (occurrence < 1 || occurrence > RocketOrdinaryRetirement.MAX_SLOTS) { throw new IllegalArgumentException("Invalid cut occurrence"); }
        var seen = new java.util.concurrent.atomic.AtomicInteger();
        return label -> {
            if (!label.equals(selected) || seen.incrementAndGet() != occurrence || !once.compareAndSet(false, true)) { return; }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N3_RETIREMENT_PAUSED boundary={} occurrence={}", label, occurrence);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            for (long remaining; (remaining = deadline - System.nanoTime()) > 0; ) {
                try { TimeUnit.NANOSECONDS.sleep(remaining); } catch (InterruptedException ignored) { }
            }
            AdvancedRocketryCommunity.LOGGER.info("ARCE_N3_RETIREMENT_PAUSE_EXPIRED boundary={}", label);
        };
    }
}
