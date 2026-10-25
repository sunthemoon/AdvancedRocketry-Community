package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketRejoinSaveClockTest {
    @Test void globalHundredTickCadenceNeverOverlapsOurScan() {
        var saves = new RocketRejoinSaveClock();
        assertFalse(saves.due(1, false, false)); assertFalse(saves.due(1, true, true));
        assertTrue(saves.due(1, true, false)); saves.saved(1);
        for (int tick = 2; tick < 101; tick++) { assertFalse(saves.due(tick, true, false)); }
        assertFalse(saves.due(101, true, true)); assertTrue(saves.due(101, true, false));
        saves.saved(101); assertFalse(saves.due(102, true, false)); assertFalse(saves.due(201, false, false));
    }
}
