package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import java.util.function.BooleanSupplier;
import net.minecraft.server.MinecraftServer;

/**
 * W332's gated load-failure seam for R-042's "present but not loaded" cases (W332-DESIGN section 2, item 1). The two
 * load-only lookups of the bay receipts and the escrow ledger pass vanilla a loader that asks this seam first; vanilla
 * runs that loader only when it actually reads the file, once per session, and caches the result. With the start property
 * {@value #PROPERTY} naming the file's type, the release-test hooks set, a dedicated server and the complete release-test
 * gate open, the seam throws, so vanilla logs its own load ERROR and caches the miss that a present file which failed to
 * load leaves. The file's bytes stay valid, so the pre-start check passes them. With the gate closed it refuses with one
 * WARN and the file loads normally; without the property, the hooks or a dedicated server it does nothing. No production
 * path sets the property. It has no static state and no static initializer.
 */
public final class ReleaseTestLoadFailure {
    public static final String PROPERTY = "advancedrocketrycommunity.releaseTestLoadFailure";
    /** The property names another type, or a part of the gate other than the connections is absent: no effect. */
    static final int NONE = 0;
    /** The property names this type on a hooked dedicated server, but the release-test gate is closed: refused. */
    static final int REFUSED = 1;
    /** Everything is open: the loader throws. */
    static final int INJECT = 2;

    private ReleaseTestLoadFailure() { }

    /** Called from the type's vanilla loader on the thread that reads the file; throws to inject the failure. */
    static void beforeLoad(MinecraftServer server, ManagedSavedDataType type) {
        String value = System.getProperty(PROPERTY, "");
        if (value.isEmpty()) { return; }
        switch (decide(value, type, Boolean.getBoolean(EscrowTestEndpoints.HOOKS), server.isDedicatedServer(),
                () -> EscrowTestEndpoints.enabled(server))) {
            case REFUSED -> com.mojang.logging.LogUtils.getLogger().warn(
                    "ARCE_RELEASE_LOAD_FAILURE refused type={}: the release-test gate is closed", type);
            case INJECT -> {
                com.mojang.logging.LogUtils.getLogger().info("ARCE_RELEASE_LOAD_FAILURE type={} injected=true", type);
                throw new IllegalStateException("Release-test injected load failure of " + type.dataName());
            }
            default -> { }
        }
    }

    /** The whole decision, without side effects; the gate is consulted only when everything else selects the type. */
    static int decide(String property, ManagedSavedDataType type, boolean hooks, boolean dedicated, BooleanSupplier gate) {
        if (property == null || !property.equals(type.name()) || !hooks || !dedicated) { return NONE; }
        return gate.getAsBoolean() ? INJECT : REFUSED;
    }
}
