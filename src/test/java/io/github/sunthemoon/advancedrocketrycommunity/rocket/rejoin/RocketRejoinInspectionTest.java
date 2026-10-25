package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinCodecTest.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketRejoinInspectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void limitsEightEntriesAndSixteenReleasedIdsWithoutChangingTheRoot() {
        var players = LongStream.range(100, 116).mapToObj(RocketRejoinCodecTest::id).toList();
        var root = RocketRejoinRoot.empty().admit(batch(1, 0, players), players.stream().map(p -> entry(1, p)).toList());
        var encoded = RocketRejoinCodec.encode(root);
        var view = RocketRejoinInspection.of(root, List.of(), release -> "B1", null);
        assertEquals(16, view.total()); assertEquals(8, view.rows().size());
        assertTrue(view.rows().stream().allMatch(r -> r.batch().released().size() <= 16 && r.stage().equals("B1")));
        assertEquals(1, RocketRejoinInspection.of(root, List.of(), release -> "B1", id(115)).rows().size());
        assertTrue(RocketRejoinInspection.of(root, List.of(), release -> "B1", id(999)).rows().isEmpty());
        assertEquals(encoded, RocketRejoinCodec.encode(root));
    }

    @Test void includesUnpublishedAdmissionButDoesNotDuplicateAPublishedOne() {
        var root = fixture();
        var b = batch(2, 1, List.of(id(101)));
        var admission = new RocketReleasePlan(b, List.of(entry(2, id(101))),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketPassengerManifest.empty(1));
        var view = RocketRejoinInspection.of(root, List.of(admission), release -> "B0", null);
        assertEquals(2, view.total());
        var published = root.admit(b, admission.entries());
        assertEquals(2, RocketRejoinInspection.of(published, List.of(admission), release -> "S1", null).total());
    }
}
