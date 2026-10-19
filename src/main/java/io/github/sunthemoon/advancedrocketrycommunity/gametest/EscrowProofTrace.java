package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;

/**
 * Test-only INFO lines for one escrow proof GameTest (W111). A hosted timeout then names the stage a proof waited on:
 * its fixture preparation, its capture, or the barrier's own store, force and readback. Barrier checkpoints arrive on
 * the barrier's IO and readback threads, so those lines carry only wall-clock milliseconds since the test started.
 */
final class EscrowProofTrace {
    private final String test;
    private final MinecraftServer server;
    private final int startedAt;
    private final long startedNanos = System.nanoTime();

    EscrowProofTrace(String test, MinecraftServer server) {
        this.test = test; this.server = server; startedAt = server.getTickCount();
    }

    /** A server-thread stage of one proof. */
    void stage(String proof, String stage, Object detail) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_PROOF test={} proof={} stage={} fixture_ticks={} ms={} detail={}",
                test, proof, stage, server.getTickCount() - startedAt, millis(), detail);
    }

    /** The checkpoint sink of this test's barrier; it only logs, from any thread. */
    Consumer<String> barrier() {
        return label -> {
            if (!label.equals("K6")) { // K6 is the force-before cut under another name.
                AdvancedRocketryCommunity.LOGGER.info("ARCE_GAMETEST_PROOF test={} proof=barrier stage={} ms={}", test, label, millis());
            }
        };
    }

    private long millis() { return (System.nanoTime() - startedNanos) / 1_000_000L; }
}
