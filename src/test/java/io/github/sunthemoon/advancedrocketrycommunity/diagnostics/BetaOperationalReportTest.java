package io.github.sunthemoon.advancedrocketrycommunity.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import org.junit.jupiter.api.Test;

class BetaOperationalReportTest {
    @Test
    void fixedReportIsBoundedPathFreeAndComplete() {
        BetaOperationalReport report = report(new BetaOperationalReport.RootSummary(
                true, true, true, true, true,
                3, 0, 0, 10, 100
        ));

        String line = report.format();

        assertTrue(line.startsWith("ARCE-BETA-1101 "));
        assertTrue(line.contains("build=1.20.1-0.9.0-beta.1"));
        assertTrue(line.contains("forge=47.4.10"));
        assertTrue(line.contains("jei=absent"));
        assertTrue(line.contains("operational=true roots=11111"));
        assertTrue(line.contains("stations=10 missions=100"));
        assertTrue(line.contains("protocols=life:1,celestial:1,flight:2,visual:1"));
        assertTrue(line.contains("flight_frame_max=39"));
        assertTrue(line.contains("ticket_policy=transient_transfer_only"));
        assertTrue(line.length() < 1_024);
        assertFalse(line.contains("\\"));
        assertFalse(line.contains("/home/"));
        assertFalse(line.contains("C:"));
    }

    @Test
    void blockedRootProducesDeterministicFailureFlags() {
        BetaOperationalReport report = report(new BetaOperationalReport.RootSummary(
                true, true, false, true, true,
                3, 1, 0, 2, 4
        ));

        assertFalse(report.roots().operational());
        assertEquals("11011", report.roots().flags());
        assertTrue(report.format().contains("operational=false roots=11011"));
    }

    @Test
    void unsafeVersionsAndOutOfRangeCountsCannotEnterTheReport() {
        BetaOperationalReport.RuntimeIdentity identity = new BetaOperationalReport.RuntimeIdentity(
                "../../secret",
                "47.4.10\nforged",
                ""
        );
        assertEquals("unknown", identity.build());
        assertEquals("unknown", identity.forge());
        assertEquals("unknown", identity.jei());

        assertThrows(IllegalArgumentException.class, () -> new BetaOperationalReport.ConfigSummary(
                0,
                AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
        ));
        assertThrows(IllegalArgumentException.class, () -> new BetaOperationalReport.RootSummary(
                true, true, true, true, true,
                -1, 0, 0, 0, 0
        ));
    }

    private static BetaOperationalReport report(BetaOperationalReport.RootSummary roots) {
        return new BetaOperationalReport(
                new BetaOperationalReport.RuntimeIdentity(
                        "1.20.1-0.9.0-beta.1",
                        "47.4.10",
                        "absent"
                ),
                roots,
                new BetaOperationalReport.ConfigSummary(
                        AtmosphereLimits.MAX_VOLUME_CELLS,
                        AtmosphereLimits.MAX_LEVEL_INSPECTIONS_PER_TICK
                ),
                new BetaOperationalReport.ProtocolSummary("1", "1", "2", "1"),
                4,
                20
        );
    }
}
