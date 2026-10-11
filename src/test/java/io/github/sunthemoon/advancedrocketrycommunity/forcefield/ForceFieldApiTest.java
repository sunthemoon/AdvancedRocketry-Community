package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersion;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
import org.junit.jupiter.api.Test;

class ForceFieldApiTest {
    @Test void oldEnumOrdinalsAndStableNamesAreUnchangedAndProjectionIsAppended() {
        var effects = EndgameEffect.values();
        assertEquals(4, effects.length);
        String[] names = {"BLOCK_BREAK", "ENTITY_GRAVITY", "TELEPORT", "FIELD_PROJECTION"};
        for (int i = 0; i < names.length; i++) {
            assertEquals(names[i], effects[i].name());
            assertEquals(i, effects[i].ordinal());
            assertSame(effects[i], EndgameEffect.valueOf(names[i]));
        }
        assertEquals(new ApiVersion(1, 9), ApiVersions.current());
    }
    @Test void throwingAndExhaustiveOldListenersRefuseInsteadOfAllowing() {
        assertFalse(ForceFieldProtection.safeEvent(() -> { throw new IllegalArgumentException("unsupported effect"); }));
        assertFalse(ForceFieldProtection.safeEvent(() -> { throw new IncompatibleClassChangeError(); }));
        assertFalse(ForceFieldProtection.safeEvent(() -> true));
        assertTrue(ForceFieldProtection.safeEvent(() -> false));
    }
}
