package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.sealed.SealedDisassembly;
import net.minecraft.server.level.ServerLevel;

/** Test-only pacing and budget facts; consumer code still compiles against the public API alone. */
public final class SealedAdapterTestAccess {
    public static int retryTicks() { return RocketOrdinaryRetirement.RETRY_TICKS; }

    public static int completionTicks(int members, int operations, boolean reload) {
        if (members < 1 || members > RocketLimits.MAX_BLOCKS || operations < 1 || operations > 2) {
            throw new IllegalArgumentException("Invalid adapter fixture size");
        }
        // PREPARE, SEALED and COMPLETE each allow one superseded attempt, its retry and a fresh proof.
        // ROLLBACK is not exercised by these C5 provider failures. Phase/cursor work shares the 256 allowance.
        int proofs = SealedDisassembly.Goal.values().length - 1;
        int work = (members * SealedDisassembly.Phase.values().length
                + RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK - 1) / RocketLimits.MAX_SCAN_INSPECTIONS_PER_TICK;
        int operation = Math.toIntExact(RocketDisassemblyConfirmations.LIFETIME_TICKS
                + proofs * (2 * RocketOrdinaryRetirement.ATTEMPT_TICKS + retryTicks()) + work);
        // The hold is observed for a complete normal retry interval; reload gets another lazy-work interval.
        return operations * operation + (reload ? 2 * retryTicks() : 0);
    }

    public static Runnable pacer(ServerLevel level) {
        EscrowTestEndpoints.requireEnabled(level.getServer());
        return new GameTestTickPacer(level.getServer())::pace;
    }

    private SealedAdapterTestAccess() { }
}
