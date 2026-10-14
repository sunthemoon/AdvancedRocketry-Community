package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import static io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * W44 A0 case f: the time of one receipt barrier (encode, stage, force, staged readback, atomic move and the
 * platform directory force) at the maximum progression root. A diagnostic record, not a performance Gate.
 */
class ClassicProgressionBarrierBenchmarkTest {
    private static final int RUNS = 9;
    @TempDir Path root;

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void recordsOneBarrierAtTheMaximumRoot() throws Exception {
        var data = ClassicProgressionSavedData.provenAbsent();
        data.insert(warp(1, participants(4_096, 10)));
        data.insert(moon(2, participants(16, 9_000)));
        Path file = root.resolve(ManagedSavedDataType.CLASSIC_PROGRESSION.fileName());
        long[] nanos = new long[RUNS];
        for (int run = 0; run < RUNS; run++) {
            data.setDirty();
            long started = System.nanoTime();
            var barrier = data.barrier(file, CheckedSavedDataFile::atomicMove, CheckedSavedDataPolicy.DirectoryForce.PLATFORM);
            nanos[run] = System.nanoTime() - started;
            assertTrue(barrier.confirmed(), barrier.stage().name());
        }
        Arrays.sort(nanos);
        String report = String.format(java.util.Locale.ROOT,
                "W44 progression receipt barrier at the maximum root (4,096 + 16 participants)%n"
                        + "os=%s runs=%d file_bytes=%d median_ms=%.3f max_ms=%.3f%n",
                System.getProperty("os.name"), RUNS, Files.size(file), nanos[RUNS / 2] / 1e6, nanos[RUNS - 1] / 1e6);
        Path reports = Files.createDirectories(Path.of("build/reports"));
        Files.writeString(reports.resolve("w44-progression-barrier-benchmark.txt"), report);
        System.out.print(report);
    }
}
