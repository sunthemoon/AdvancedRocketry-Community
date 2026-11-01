package io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.*;

final class BayExitCandidatesTest {
    static Position pos(double x, double y, double z) { return new Position("test:level", x, y, z); }

    @Test void exactHintThenGantryThenColumnAndHeightOrderHasThirtyTwoEntries() {
        var expected = new ArrayList<>(List.of(pos(90, 80, 90), pos(12, 70, 20), pos(10, 70, 22),
                pos(8, 70, 20), pos(10, 70, 18)));
        int[][] columns = {{0, 0}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
        for (int[] column : columns) {
            for (int y : new int[]{64, 65, 63}) { expected.add(pos(10.5 + column[0], y, 20.5 + column[1])); }
        }
        assertEquals(32, expected.size());
        assertEquals(expected, BayExitCandidates.ordered(pos(10, 70, 20), Optional.of(pos(90, 80, 90)),
                Optional.of(new Anchor("test:level", 10, 64, 20))));
    }

    @Test void deduplicatesHintGantryAndAnchorWithoutChangingFirstOccurrence() {
        var actual = BayExitCandidates.ordered(pos(.5, 64, .5), Optional.of(pos(2.5, 64, .5)),
                Optional.of(new Anchor("test:level", 2, 64, 0)));
        assertEquals(pos(2.5, 64, .5), actual.get(0));
        assertEquals(pos(.5, 64, 2.5), actual.get(1));
        assertEquals(30, actual.size());
        assertEquals(actual.size(), actual.stream().distinct().count());
        assertThrows(UnsupportedOperationException.class, () -> actual.add(pos(0, 0, 0)));
    }

    @Test void unprovedAnchorAndForeignOrNonfiniteHintNeverAddADistantCandidate() {
        var local = List.of(pos(2, 100, 0), pos(0, 100, 2), pos(-2, 100, 0), pos(0, 100, -2));
        assertEquals(local, BayExitCandidates.ordered(pos(0, 100, 0), Optional.empty(), Optional.empty()));
        assertEquals(local, BayExitCandidates.ordered(pos(0, 100, 0),
                Optional.of(new Position("test:elsewhere", 5, 64, 5)),
                Optional.of(new Anchor("test:elsewhere", 5, 64, 5))));
        assertEquals(local, BayExitCandidates.ordered(pos(0, 100, 0), Optional.of(pos(Double.NaN, 64, 0)),
                Optional.empty()));
        assertTrue(BayExitCandidates.ordered(pos(Double.POSITIVE_INFINITY, 64, 0),
                Optional.empty(), Optional.empty()).isEmpty());
    }
}
