package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class RocketRejoinSafetyTest {
    static final class World implements RocketRejoinSafety.Probe {
        boolean bounds = true, loaded = true, harmless = true, support = true, clear = true, survival = true;
        int reads, loads;
        public boolean bounds(int x, int y, int z) { return bounds; }
        public boolean loaded(int x, int z) { loads++; return loaded; }
        public boolean harmless(int x, int y, int z) {
            assertTrue(x >= 9 && x <= 11 && y >= 69 && y <= 72 && z >= 19 && z <= 21); reads++; return harmless;
        }
        public boolean supported(int x, int y, int z) { assertEquals(69, y); return support; }
        public boolean clear() { return clear; }
        public boolean survives(int x, int y, int z) { assertEquals(71, y); return survival; }
    }
    @Test void boundedEnvelopeAndNoReadsOrLoadsAfterUnknownBoundary() {
        var world = new World(); assertTrue(RocketRejoinSafety.check(world, 10, 70, 20));
        assertEquals(36, world.reads); assertEquals(9, world.loads);
        world = new World(); world.loaded = false; assertFalse(RocketRejoinSafety.check(world, 10, 70, 20)); assertEquals(0, world.reads);
        world = new World(); world.bounds = false; assertFalse(RocketRejoinSafety.check(world, 10, 70, 20)); assertEquals(0, world.loads);
    }
    @Test void everySafetyConditionIsNecessary() {
        for (int field = 0; field < 4; field++) {
            var world = new World();
            switch (field) { case 0 -> world.harmless = false; case 1 -> world.support = false; case 2 -> world.clear = false; case 3 -> world.survival = false; }
            assertFalse(RocketRejoinSafety.check(world, 10, 70, 20)); assertTrue(world.reads <= 36);
        }
    }
}
