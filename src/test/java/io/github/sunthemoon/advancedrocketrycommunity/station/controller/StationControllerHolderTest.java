package io.github.sunthemoon.advancedrocketrycommunity.station.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerHolder.Refusal;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ADR-065 section 11.2: a controller menu's holder must stay the connected, living, non-spectator opener in the
 * controller's Level, with the exact controller block loaded within 8 blocks. An unloaded controller chunk is
 * refused without reading the block, which a live GameTest cannot stage within reach of a connected player.
 */
final class StationControllerHolderTest {
    @Test
    void aValidHolderPassesAndEachConditionRefusesInItsOrder() {
        assertEquals(Optional.empty(), StationControllerHolder.check(new Fake()));
        assertEquals(Optional.of(Refusal.NOT_HOLDER), StationControllerHolder.check(new Fake().holder(false)));
        assertEquals(Optional.of(Refusal.DISCONNECTED), StationControllerHolder.check(new Fake().connected(false)));
        assertEquals(Optional.of(Refusal.NOT_PLAYING), StationControllerHolder.check(new Fake().alive(false)));
        assertEquals(Optional.of(Refusal.NOT_PLAYING), StationControllerHolder.check(new Fake().spectator(true)));
        assertEquals(Optional.of(Refusal.OTHER_LEVEL), StationControllerHolder.check(new Fake().sameLevel(false)));
        assertEquals(Optional.of(Refusal.CONTROLLER_GONE), StationControllerHolder.check(new Fake().present(false)));
        // The first failure wins: a stranger is refused before anything about the controller is read.
        Fake stranger = new Fake().holder(false).loaded(false);
        assertEquals(Optional.of(Refusal.NOT_HOLDER), StationControllerHolder.check(stranger));
        assertFalse(stranger.chunkRead);
    }

    @Test
    void anUnloadedControllerIsRefusedWithoutReadingItsBlock() {
        Fake unloaded = new Fake().loaded(false);
        assertEquals(Optional.of(Refusal.CHUNK_UNLOADED), StationControllerHolder.check(unloaded));
        assertTrue(unloaded.chunkRead);
        assertFalse(unloaded.blockRead, "The block of an unloaded chunk was read");
        assertFalse(unloaded.distanceRead);
    }

    @Test
    void theReachIsEightBlocksFromTheControllersCentreInclusive() {
        double edge = StationLimits.CONTROLLER_REACH * StationLimits.CONTROLLER_REACH;
        assertEquals(Optional.empty(), StationControllerHolder.check(new Fake().distanceSquared(edge)));
        assertEquals(Optional.of(Refusal.OUT_OF_REACH),
                StationControllerHolder.check(new Fake().distanceSquared(Math.nextUp(edge))));
        assertEquals(Optional.of(Refusal.OUT_OF_REACH),
                StationControllerHolder.check(new Fake().distanceSquared(12.0D * 12.0D)));
    }

    private static final class Fake implements StationControllerHolder.Probe {
        private boolean holder = true;
        private boolean connected = true;
        private boolean alive = true;
        private boolean spectator;
        private boolean sameLevel = true;
        private boolean loaded = true;
        private boolean present = true;
        private double distanceSquared = 4.0D;
        private boolean chunkRead;
        private boolean blockRead;
        private boolean distanceRead;

        Fake holder(boolean value) {
            holder = value;
            return this;
        }

        Fake connected(boolean value) {
            connected = value;
            return this;
        }

        Fake alive(boolean value) {
            alive = value;
            return this;
        }

        Fake spectator(boolean value) {
            spectator = value;
            return this;
        }

        Fake sameLevel(boolean value) {
            sameLevel = value;
            return this;
        }

        Fake loaded(boolean value) {
            loaded = value;
            return this;
        }

        Fake present(boolean value) {
            present = value;
            return this;
        }

        Fake distanceSquared(double value) {
            distanceSquared = value;
            return this;
        }

        @Override
        public boolean holder() {
            return holder;
        }

        @Override
        public boolean connected() {
            return connected;
        }

        @Override
        public boolean alive() {
            return alive;
        }

        @Override
        public boolean spectator() {
            return spectator;
        }

        @Override
        public boolean sameLevel() {
            return sameLevel;
        }

        @Override
        public boolean controllerChunkLoaded() {
            chunkRead = true;
            return loaded;
        }

        @Override
        public boolean controllerPresent() {
            blockRead = true;
            return present;
        }

        @Override
        public double distanceSquared() {
            distanceRead = true;
            return distanceSquared;
        }
    }
}
