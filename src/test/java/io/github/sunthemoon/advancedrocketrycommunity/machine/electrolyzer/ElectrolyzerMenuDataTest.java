package io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ElectrolyzerMenuDataTest {
    @Test
    void appendedReasonDoesNotMoveExistingFieldsOrAllowWrites() {
        AtomicInteger reason = new AtomicInteger(5);
        ElectrolyzerMenuData data = new ElectrolyzerMenuData(
                () -> 11, () -> 12, () -> 13, () -> 14, () -> 7, reason::get);
        assertEquals(8, data.getCount());
        assertEquals(11, data.get(0));
        assertEquals(12, data.get(1));
        assertEquals(13, data.get(2));
        assertEquals(ElectrolyzerBlockEntity.ENERGY_CAPACITY, data.get(3));
        assertEquals(14, data.get(4));
        assertEquals(ElectrolyzerBlockEntity.WATER_CAPACITY, data.get(5));
        assertEquals(7, data.get(6));
        assertEquals(5, data.get(7));
        data.set(7, 1);
        assertEquals(5, reason.get());
        assertEquals(5, data.get(7));
        reason.set(6);
        assertEquals(6, data.get(7));
    }
}
