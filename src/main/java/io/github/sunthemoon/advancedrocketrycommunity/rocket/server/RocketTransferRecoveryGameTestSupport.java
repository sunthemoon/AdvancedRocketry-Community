package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** W381 GameTest support: a read-only view of one manager's ordinary source-retirement entry. Never product authority. */
public final class RocketTransferRecoveryGameTestSupport {
    private RocketTransferRecoveryGameTestSupport() { }

    /** Whether the manager holds a return for the logical rocket, the attempts it started, its last wait or failure reason, and all entries. */
    public record Retirement(boolean pending, int attempts, String reason, int entries) {
        @Override public String toString() {
            return "pending=" + pending + " attempts=" + attempts + " reason=" + reason + " entries=" + entries;
        }
    }

    /** The product's allowance for one proof attempt: a force batch and its read-back, with margin. */
    public static int attemptTicks() { return RocketOrdinaryRetirement.ATTEMPT_TICKS; }

    public static Retirement retirement(MinecraftServer server, RocketManager manager, UUID logical) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("Retirement observation requires a GameTestServer");
        }
        var retirements = manager.transfers().ordinaryRetirements();
        return new Retirement(retirements.pending(logical), retirements.attempts(logical), retirements.reason(logical), retirements.size());
    }
}
