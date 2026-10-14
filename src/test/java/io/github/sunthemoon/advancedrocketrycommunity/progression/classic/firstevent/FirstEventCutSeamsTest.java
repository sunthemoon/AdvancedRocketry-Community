package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** W44 A0 case g: every seam is its production value unless both the cut and the hooks property are set. */
class FirstEventCutSeamsTest {
    @AfterEach void clear() {
        System.clearProperty(FirstEventCutSeams.PROPERTY);
        System.clearProperty(EscrowTestEndpoints.HOOKS);
    }

    @Test void absentPropertyOrDisabledHooksKeepEverySeamAtItsProductionValue() {
        assertTrue(FirstEventCutSeams.configured().isEmpty());
        // A null server proves the production path never consults the server gate.
        for (ManagedSavedDataType type : ManagedSavedDataType.values()) {
            assertDoesNotThrow(() -> FirstEventCutSeams.beforeLoad(null, type));
        }
        System.setProperty(FirstEventCutSeams.PROPERTY, "FP9");
        assertTrue(FirstEventCutSeams.configured().isEmpty(), "hooks disabled");
        assertDoesNotThrow(() -> FirstEventCutSeams.beforeLoad(null, ManagedSavedDataType.CLASSIC_PROGRESSION));
        System.setProperty(EscrowTestEndpoints.HOOKS, "true");
        assertEquals(FirstEventCutSeams.Cut.FP9, FirstEventCutSeams.configured().orElseThrow());
        // An unrelated root is unaffected by the load-fault cut.
        assertDoesNotThrow(() -> FirstEventCutSeams.beforeLoad(null, ManagedSavedDataType.STATIONS));
        System.setProperty(FirstEventCutSeams.PROPERTY, "FX-Ma");
        assertThrows(IllegalStateException.class, FirstEventCutSeams::configured, "an unknown ID fails at startup");
    }

    @Test void theInventoryNamesExactlyTheThirtyNineHeadlessRows() {
        Set<String> ids = Arrays.stream(FirstEventCutSeams.Cut.values()).map(FirstEventCutSeams.Cut::id)
                .collect(Collectors.toSet());
        assertEquals(39, ids.size());
        assertTrue(ids.containsAll(Set.of("FM1", "FM2a", "FM12b", "FW4b", "FW11", "FP7a", "FP11")));
        assertFalse(ids.contains("FM10a"), "X rows use no seam");
        assertFalse(ids.contains("FW10a"), "V rows use no seam");
    }
}
