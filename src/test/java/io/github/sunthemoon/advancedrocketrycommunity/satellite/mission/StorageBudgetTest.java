package io.github.sunthemoon.advancedrocketrycommunity.satellite.mission;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

final class StorageBudgetTest {
    private static final UUID ID = new UUID(1, 1);

    @ParameterizedTest @EnumSource(StorageBudget.Section.class)
    void replacementAndRemovalMaintainProofCountAndBytes(StorageBudget.Section section) {
        var budget = new StorageBudget();
        assertTrue(budget.allProven());
        budget.reserve(section, ID, new RecordSizer.Reservation(4096, false));
        assertFalse(budget.allProven());
        assertEquals(4096, budget.reserved(section));
        budget.reserve(section, ID, new RecordSizer.Reservation(4096, false));
        budget.reserve(section, ID, new RecordSizer.Reservation(4000, true));
        assertTrue(budget.allProven());
        assertEquals(4000, budget.reserved(section));
        budget.reserve(section, ID, new RecordSizer.Reservation(4096, false));
        budget.release(section, ID);
        budget.release(section, ID);
        assertTrue(budget.allProven());
        assertEquals(0, budget.reserved(section));
    }

    @Test void sameIdentityInDifferentSectionsKeepsBothFlagsUntilRemoved() {
        var budget = new StorageBudget();
        budget.reserve(StorageBudget.Section.MISSIONS, ID, new RecordSizer.Reservation(4096, false));
        budget.reserve(StorageBudget.Section.INSTANCES, ID, new RecordSizer.Reservation(4096, false));
        budget.release(StorageBudget.Section.MISSIONS, ID);
        assertFalse(budget.allProven());
        budget.reserve(StorageBudget.Section.INSTANCES, ID, 4096);
        assertTrue(budget.allProven());
        assertEquals(4096, budget.reserved(StorageBudget.Section.INSTANCES));
    }
}
