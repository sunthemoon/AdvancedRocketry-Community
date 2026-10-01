package io.github.sunthemoon.advancedrocketrycommunity.endgame.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** ADR-054 section 13: line bound, per-tick limit, summaries, ring and pages. */
class EndgameAuditTest {
    private static final UUID DEVICE = new UUID(0L, 1L);

    @Test
    void aLineIsOneParsableRecordOfAtMost512Bytes() {
        List<String> sink = new ArrayList<>();
        EndgameAudit audit = new EndgameAudit(sink::add);
        audit.line(1L, "laser drill", "start=x", "OK", DEVICE, null, DEVICE, "note=" + "é".repeat(400));
        String line = sink.get(0);
        assertTrue(line.startsWith("ARCE_ENDGAME system=laser_drill action=start_x result=OK device="
                + DEVICE + " owner=- actor=" + DEVICE), line);
        assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 512, "bounded");
        assertTrue(line.endsWith("..."), "truncated without splitting a character");
        assertFalse(new String(line.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8).contains("�"));
    }

    @Test
    void atMostSixtyFourLinesATickAndTheRestAreCountedInTheNextSummary() {
        List<String> sink = new ArrayList<>();
        EndgameAudit audit = new EndgameAudit(sink::add);
        for (int i = 0; i < 70; i++) {
            audit.line(1_200L, "endgame", "zone_add", "OK", null, null, null, "");
        }
        assertEquals(64, sink.size());
        assertEquals(6, audit.suppressedLines());
        assertTrue(audit.line(1_201L, "endgame", "zone_add", "OK", null, null, null, ""), "a new tick resets");
        audit.count("laser_drill", "operations", 5);
        audit.count("laser_drill", "operations", 2);
        audit.summarizeIfDue(2_400L);
        assertTrue(sink.stream().anyMatch(line -> line.contains("system=laser_drill action=summary")
                && line.contains("operations=7")), sink.toString());
        assertTrue(sink.stream().anyMatch(line -> line.contains("suppressed_lines=6")));
        assertEquals(0, audit.suppressedLines());
        int before = sink.size();
        audit.summarizeIfDue(2_401L);
        assertEquals(before, sink.size(), "one summary per 1,200 ticks");
    }

    @Test
    void theRingKeepsTheLast512LinesNewestFirstSixteenPerPage() {
        EndgameAudit audit = new EndgameAudit(line -> { });
        for (int i = 0; i < 600; i++) {
            audit.line(i, i % 2 == 0 ? "laser_drill" : "gravity_field", "n" + i, "OK", null, null, null, "");
        }
        assertEquals(512, audit.ringSize());
        List<String> first = audit.page(null, 0);
        assertEquals(16, first.size());
        assertTrue(first.get(0).contains("action=n599"));
        List<String> laser = audit.page("laser_drill", 0);
        assertTrue(laser.stream().allMatch(line -> line.contains("system=laser_drill ")));
        assertTrue(laser.get(0).contains("action=n598"));
        assertTrue(audit.page(null, 31).size() < 16 || audit.page(null, 32).isEmpty());
        assertTrue(audit.page(null, 40).isEmpty());
        audit.clear();
        assertEquals(0, audit.ringSize());
    }

    @Test
    void aProtectionRefusalIsAuditedOncePerDeviceAndCodePer1200Ticks() {
        EndgameAudit audit = new EndgameAudit(line -> { });
        assertTrue(audit.protectionNoticeDue(100L, DEVICE, EndgameCode.TARGET_PROTECTED));
        assertFalse(audit.protectionNoticeDue(1_299L, DEVICE, EndgameCode.TARGET_PROTECTED));
        assertTrue(audit.protectionNoticeDue(1_299L, DEVICE, EndgameCode.TARGET_UNLOADED), "another code");
        assertTrue(audit.protectionNoticeDue(1_300L, DEVICE, EndgameCode.TARGET_PROTECTED));
    }
}
