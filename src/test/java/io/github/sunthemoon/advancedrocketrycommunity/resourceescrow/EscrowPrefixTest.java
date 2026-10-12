package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowPrefixTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void reachablePrefixesAreClassifiedByTheLatestContiguousFrontier() {
        var first = EscrowTestFixtures.operation(1, 99, 0); var second = EscrowTestFixtures.operation(3, 99, 1);
        var ledger = EscrowTestFixtures.ledger(first, second); var chain = EscrowPrefix.chains(ledger.records()).get(first.destination().key());
        assertEquals(EscrowPrefix.Classification.BEFORE, EscrowPrefix.classify(ledger.id(), chain,
                new EscrowPrefix.State(ledger.id(), 0, first.destination().beforeSignature(), List.of())));
        assertEquals(EscrowPrefix.Classification.PREFIX, EscrowPrefix.classify(ledger.id(), chain,
                new EscrowPrefix.State(ledger.id(), 1, first.destination().afterSignature(), List.of(chain.get(0).marker()))));
        var frontier = new EscrowPrefix.State(ledger.id(), 2, second.destination().afterSignature(), chain.stream().map(EscrowPrefix.Link::marker).toList());
        assertEquals(EscrowPrefix.Classification.AFTER, EscrowPrefix.classify(ledger.id(), chain, frontier));
        assertEquals(EscrowPrefix.Classification.OTHER, EscrowPrefix.classify(ledger.id(), chain,
                new EscrowPrefix.State(ledger.id(), 2, first.destination().afterSignature(), frontier.markers())));
        assertEquals(EscrowPrefix.Classification.OTHER, EscrowPrefix.classify(ledger.id(), chain,
                new EscrowPrefix.State(ledger.id(), 2, second.destination().afterSignature(), List.of(chain.get(1).marker()))));
    }
    @Test void l2CannotRetireAProvenLaterLinkPastAStalledEarlierLoader() {
        var first = EscrowTestFixtures.operation(1, 99, 0).withReason(EscrowReason.BARRIER_IO);
        var second = EscrowTestFixtures.operation(2, 99, 1); var ledger = EscrowTestFixtures.ledger(first, second);
        assertThrows(IllegalArgumentException.class, () -> ledger.retire(Set.of(second.id())));
        assertEquals(List.of(second), ledger.retire(Set.of(first.id())).records());
        assertTrue(ledger.retire(Set.of(first.id(), second.id())).records().isEmpty());
        assertEquals(2, ledger.retire(Set.of(first.id(), second.id())).issuedThrough());
    }
    @Test void repairReasonsPersistAndSupersedingSavesAreNotIoFailures() {
        for (var reason : EscrowReason.values()) {
            var op = EscrowTestFixtures.operation(1).withReason(reason);
            assertEquals(op, EscrowCodec.operation(EscrowCodec.operation(op)));
            if (reason.manual()) { assertEquals(EscrowOperation.Phase.REPAIR, op.phase()); }
        }
        assertFalse(EscrowReason.SUPERSEDING_SAVE.barrierFailure());
        assertThrows(IllegalArgumentException.class, () -> EscrowTestFixtures.operation(1)
                .withReason(EscrowReason.MISSING_ENDPOINT).withReason(EscrowReason.NONE));
        assertEquals(100, EscrowReason.backoff(1)); assertEquals(400, EscrowReason.backoff(2)); assertEquals(1_200, EscrowReason.backoff(3));
        assertEquals(1_200, EscrowReason.backoff(Integer.MAX_VALUE));
    }
    @Test void aLaterOwnCaptureInvalidatesTheEarlierProofOfTheSameSlot() {
        var monitor = new EscrowSaveMonitor(); var pos = new net.minecraft.world.level.ChunkPos(4, 4);
        var first = monitor.watch("minecraft:overworld", false, pos); long captured = first.get();
        var second = monitor.watch("minecraft:overworld", false, pos);
        assertSame(first, second); assertTrue(second.get() > captured);
        monitor.release("minecraft:overworld", false, pos); long next = second.get();
        monitor.observed("minecraft:overworld", false, pos); assertEquals(next + 1, second.get());
        monitor.release("minecraft:overworld", false, pos);
        monitor.observed("minecraft:overworld", false, pos); assertEquals(next + 1, second.get());
    }
}
