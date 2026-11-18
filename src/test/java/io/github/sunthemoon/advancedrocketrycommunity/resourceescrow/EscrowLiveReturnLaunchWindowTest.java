package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLiveReturnLaunchWindowTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void waitsThroughTheWholeCaptureWindowIncludingTheAutosaveTick() {
        for (int autosave : new int[] {6_000, 24_000, 66_000}) {
            var journalCalls = new AtomicInteger();
            var launches = new AtomicInteger();
            for (int tick = autosave - 60; tick <= autosave; tick++) {
                EscrowGovernedLiveReturnGameTests.prepareWhenReady(tick,
                        () -> { journalCalls.incrementAndGet(); return true; }, launches::incrementAndGet);
            }
            assertEquals(0, launches.get(), "A capture overlapped autosave " + autosave);
            assertEquals(0, journalCalls.get(), "A deferred fixture pushed a journal write");
            EscrowGovernedLiveReturnGameTests.prepareWhenReady(autosave + 1,
                    () -> { journalCalls.incrementAndGet(); return true; }, launches::incrementAndGet);
            assertEquals(1, launches.get(), "The first post-autosave tick did not admit the fixture");
            assertEquals(1, journalCalls.get());
        }
    }

    @Test void safeWindowStillRequiresTheCheckedJournal() {
        var launches = new AtomicInteger();
        EscrowGovernedLiveReturnGameTests.prepareWhenReady(6_001, () -> false, launches::incrementAndGet);
        assertEquals(0, launches.get());
        EscrowGovernedLiveReturnGameTests.prepareWhenReady(6_002, () -> true, launches::incrementAndGet);
        assertEquals(1, launches.get());
    }

    @Test void admitsTheLastSafePreWindowTickButNotItsSuccessor() {
        var launches = new AtomicInteger();
        EscrowGovernedLiveReturnGameTests.prepareWhenReady(GameTestAutosave.PERIOD_TICKS - 61,
                () -> true, launches::incrementAndGet);
        assertEquals(1, launches.get(), "The safe pre-window tick was unnecessarily deferred");
        EscrowGovernedLiveReturnGameTests.prepareWhenReady(GameTestAutosave.PERIOD_TICKS - 60,
                () -> true, launches::incrementAndGet);
        assertEquals(1, launches.get(), "The inclusive window boundary admitted a capture");
    }
}
