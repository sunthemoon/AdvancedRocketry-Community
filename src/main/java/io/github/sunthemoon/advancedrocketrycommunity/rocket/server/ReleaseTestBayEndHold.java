package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * W363's gated readiness seam for B17.3 (an unloaded restart group ahead of healthy groups), in the style of W332's
 * {@code ReleaseTestLoadFailure}. With the start property {@value #PROPERTY} naming one logical rocket, the release-test
 * hooks set, a dedicated server and the complete release-test gate open, R2's END_LOADING wait of that rocket's restart
 * group never ends in this session, as if one of its ends stayed unloaded. It changes no ticket: the coordinator renews
 * the group's tickets exactly as before, and every other group reads the real readiness. Without the property it returns
 * at its first line; with the hooks, the dedicated server or the gate missing it has no effect. No production path sets
 * the property. It has no static state and no static initializer.
 */
final class ReleaseTestBayEndHold {
    static final String PROPERTY = "advancedrocketrycommunity.bayEndUnready";

    private ReleaseTestBayEndHold() { }

    /** Asked only after the real readiness check passed, on the server thread. */
    static boolean holds(MinecraftServer server, UUID logical) {
        String value = System.getProperty(PROPERTY, "");
        if (value.isEmpty()) { return false; }
        return decide(value, logical, Boolean.getBoolean(EscrowTestEndpoints.HOOKS), server.isDedicatedServer(),
                () -> EscrowTestEndpoints.enabled(server));
    }

    /** The whole decision, without side effects; the gate is consulted only when everything else selects the rocket. */
    static boolean decide(String property, UUID logical, boolean hooks, boolean dedicated, BooleanSupplier gate) {
        return property != null && property.equals(logical.toString()) && hooks && dedicated && gate.getAsBoolean();
    }
}
