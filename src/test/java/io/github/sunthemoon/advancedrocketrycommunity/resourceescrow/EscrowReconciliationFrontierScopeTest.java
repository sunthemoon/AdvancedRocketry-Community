package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowReconciliationFrontierScopeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void singleMemberCheckpointSelectsItsEndpointsFromTheCompleteComponentView() {
        var f = EscrowReconciliationBindingBoundaryTest.sample(); var root = f.ledger();
        var a = source(root, f.a()); var b = source(root, f.b());
        var rocket = EscrowSettlementDisk.observe(EscrowReconciliationCheckpointTest.rocketSlot(f.a().destination(), f.after()),
                f.a().destination(), root.id());
        var component = Map.of(a.endpoint().key(), a.state(), b.endpoint().key(), b.state(), rocket.endpoint().key(), rocket.state());
        try (var checkpoint = new EscrowReconciliationCheckpoint(root, Set.of(f.a().id()), List.of(a, rocket), () -> true, component)) {
            assertEquals(Set.of(a.endpoint().key(), rocket.endpoint().key()), checkpoint.frontiers.keySet());
            assertEquals(rocket.state(), checkpoint.frontiers.get(rocket.endpoint().key()));
            assertEquals(3, component.size());
        }
        assertThrows(IllegalArgumentException.class, () -> new EscrowReconciliationCheckpoint(root, Set.of(f.a().id()),
                List.of(a, rocket), () -> true, Map.of(a.endpoint().key(), a.state())));
    }

    private static EscrowSettlementIntent.Observation source(EscrowLedger root, EscrowOperation op) {
        var endpoint = op.source();
        return new EscrowSettlementIntent.Observation(endpoint, new EscrowPrefix.State(root.id(), endpoint.afterEpoch(),
                endpoint.afterSignature(), List.of(new EscrowPrefix.Link(op, endpoint, "SOURCE").marker())), true, 4_900);
    }
}
