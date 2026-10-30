package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/** R52-R3: preserve historical named-fill crash fixtures without opening named production admission. */
final class EscrowNamedFillTestHook {
    private static final Set<String> CASES = Set.of("LF_M_NAMED_E5", "LF_M_NAMED_CROSS_K2", "LF_M_NAMED_CROSS_K9",
            "LF_M_R1_FLOORS_255_DISTINCT", "LF_M_R1_FLOORS_255_DISTINCT_ACK", "LF_M_R1_FLOORS_255_COALESCED",
            "LF_M_R1_FLOORS_255_COALESCED_ACK", "LF_M_R1_FLOORS_256",
            "LF_S6_O3J_BEFORE", "LF_S6_O3J_REPLACED", "LF_S6_O3J_ACK");
    private EscrowNamedFillTestHook() { }

    static boolean allowed(MinecraftServer server) {
        return allowed(Boolean.getBoolean(EscrowTestEndpoints.HOOKS), System.getProperty("advancedrocketrycommunity.escrowCase", ""),
                () -> EscrowTestEndpoints.enabled(server));
    }

    static boolean allowed(boolean hooks, String testCase, BooleanSupplier releaseGate) {
        return hooks && CASES.contains(testCase) && releaseGate.getAsBoolean();
    }
}
