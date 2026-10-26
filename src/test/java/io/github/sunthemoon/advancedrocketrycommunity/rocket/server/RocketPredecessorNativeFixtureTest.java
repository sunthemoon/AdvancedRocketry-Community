package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketPredecessorNativeFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void sameSlotStillHasADistinctRelocationAtEveryColumnEdge() {
        for (int x : new int[] {-17, -16, -1, 0, 15, 16, 384}) {
            var source = new RocketPosition(x, 100, x);
            var destination = RocketPredecessorNativeFixture.destinationOrigin(true, source);
            assertNotEquals(source, destination, "An identical origin has the same content hash and is not a transfer");
            assertEquals(source.x() >> 4, destination.x() >> 4);
            assertEquals(source.z() >> 4, destination.z() >> 4);
            assertEquals(source.y(), destination.y());
        }
    }

    @Test void crossDimensionFixtureKeepsItsDeclaredDestination() {
        assertEquals(new RocketPosition(8, 100, 8),
                RocketPredecessorNativeFixture.destinationOrigin(false, new RocketPosition(384, 100, 384)));
    }
}
