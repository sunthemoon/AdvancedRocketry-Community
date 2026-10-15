package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EscrowUnavailableBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @TempDir Path directory;

    @Test void threeUnknownCompletionsNeedTwoFullOwnerTickBackoffsBeforeUnavailable() {
        var io = new AwaitedLedgerIo(); var attempts = new AtomicInteger();
        var completions = new ArrayList<EscrowLedgerWriter.Completion>();
        var root = EscrowTestFixtures.ledger(EscrowTestFixtures.operation(1)).generation(1);
        try (var writer = io.writer(directory.resolve(ManagedSavedDataType.RESOURCE_ESCROW.fileName()), 0, (staged, target) -> {
            if (attempts.incrementAndGet() <= 3) { throw new IOException("Injected ambiguous move"); }
            CheckedSavedDataFile.atomicMove(staged, target);
        })) {
            assertTrue(writer.request(() -> EscrowLedgerWriter.Snapshot.of(root, Set.of()), completions::add));
            for (int tick = 1; tick <= 402; tick++) {
                io.awaitWrites(); writer.tick(tick);
                assertFalse(writer.unavailable(), "Unavailable requires the third completed UNKNOWN, not elapsed wall time");
                if (tick == 200 || tick == 401) {
                    assertEquals(tick == 200 ? 1 : 2, attempts.get());
                }
            }
            io.awaitWrites(); writer.tick(403);
            assertTrue(writer.unavailable()); assertEquals(3, attempts.get());
            assertEquals(3, completions.size());
            assertTrue(completions.stream().allMatch(c -> c.outcome() == EscrowLedgerWriter.Outcome.UNKNOWN));
            assertEquals(1, completions.stream().map(EscrowLedgerWriter.Completion::digest).distinct().count());
            io.tickUntil(writer::tick, 404, writer::idle);
            assertTrue(writer.idle()); assertFalse(writer.unavailable()); assertEquals(4, attempts.get());
            assertEquals(EscrowLedgerWriter.Outcome.COMMITTED, completions.get(3).outcome());
        }
    }
}
