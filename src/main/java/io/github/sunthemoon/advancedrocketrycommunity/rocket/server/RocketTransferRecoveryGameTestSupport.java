package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.UUID;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.server.MinecraftServer;

/** W381 GameTest support: a read-only view of one manager's ordinary source-retirement entry, for failure text only. */
public final class RocketTransferRecoveryGameTestSupport {
    private RocketTransferRecoveryGameTestSupport() { }

    /** Whether the manager still holds a return for the logical rocket, its started attempts and its last wait or failure reason. */
    public static String retirement(MinecraftServer server, RocketManager manager, UUID logical) {
        if (!(server instanceof GameTestServer) || !server.isSameThread()) {
            throw new IllegalStateException("Retirement observation requires a GameTestServer");
        }
        var retirements = manager.transfers().ordinaryRetirements();
        return "pending=" + retirements.pending(logical) + " attempts=" + retirements.attempts(logical)
                + " reason=" + retirements.reason(logical) + " entries=" + retirements.size();
    }
}
