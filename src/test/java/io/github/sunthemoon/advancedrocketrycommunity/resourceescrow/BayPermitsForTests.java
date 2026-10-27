package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import java.util.UUID;
import java.util.function.BiPredicate;

/** Unit-test access to the package-private permit constructor; production permits come only from the session handle. */
public final class BayPermitsForTests {
    private BayPermitsForTests() { }

    public static BayRegistryPermit permit(BiPredicate<UUID, UUID> intent, UUID op, UUID satellite) {
        return new BayRegistryPermit(intent, op, satellite);
    }
}
