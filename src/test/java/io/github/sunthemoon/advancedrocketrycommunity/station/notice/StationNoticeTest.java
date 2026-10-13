package io.github.sunthemoon.advancedrocketrycommunity.station.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.WarpCostClass;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The notice envelope of ADR-065 section 5.3: stable code-token keys, strict argument roles, exact byte accounting
 * against the real NBT writer, and the 512-byte admission that falls back to the brief outcome.
 */
class StationNoticeTest {
    private static final ResourceLocation EARTH = ModIdentity.id("earth");
    private static final ResourceLocation MOON = ModIdentity.id("moon");
    private static final String PREFIX = "station.advancedrocketrycommunity.notice.";

    @BeforeAll
    static void bootstrap() {
        MinecraftBootstrap.initialize();
    }

    static long payload(StationNotice notice) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        StationNoticeCodec.encode(notice).write(new DataOutputStream(bytes));
        return bytes.size();
    }

    @Test
    void everyKindHasAStableCodeTokenKey() {
        assertEquals(List.of(PREFIX + "warp_started", PREFIX + "warp_countdown", PREFIX + "warp_committed",
                PREFIX + "warp_aborted", PREFIX + "warp_outcome", PREFIX + "warp_visitor"),
                Arrays.stream(StationNoticeKind.values()).map(StationNoticeKind::key).toList());
        assertEquals(PREFIX + "while_away", StationNoticeKind.WHILE_AWAY);
        assertEquals(List.of(false, false, true, true, true, false),
                Arrays.stream(StationNoticeKind.values()).map(StationNoticeKind::stored).toList());
        assertEquals(Arrays.stream(WarpCostClass.values()).map(value -> value.name().toLowerCase(Locale.ROOT)).toList(),
                StationNoticeKind.COST_CLASSES);
        assertEquals(7, StationNoticeKind.MAX_ARGUMENTS);
        for (StationNoticeKind kind : StationNoticeKind.values()) {
            assertEquals(StationNoticeKind.Role.TEXT, kind.roles().get(0), kind + " names the station first");
        }
    }

    @Test
    void factoriesBuildCanonicalArgumentsWithoutNamesOrIds() {
        StationNotice started = StationNotice.started("Alpha", EARTH, MOON, 10, "in_system", 2_000_000);
        assertEquals(List.of("Alpha", "WARP_STARTED", EARTH.toString(), MOON.toString(), "10", "in_system", "2000000"),
                started.arguments());
        assertEquals(List.of("Alpha", MOON.toString(), "5"), StationNotice.countdown("Alpha", MOON, 5).arguments());
        assertEquals(List.of("Alpha", "WARP_COMMITTED", EARTH.toString(), MOON.toString(), "interstellar", "8000000",
                "0"), StationNotice.committed("Alpha", EARTH, MOON, "interstellar", 8_000_000, 0).arguments());
        assertEquals(List.of("Alpha", "WARP_CANCELLED", MOON.toString()),
                StationNotice.aborted("Alpha", StationManagementCode.WARP_CANCELLED, MOON).arguments());
        assertEquals("Alpha", started.station());
    }

    /** W22 review F2: a visitor without VISIT access learns the station, the code and the target, never the energy. */
    @Test
    void theVisitorFormDropsTheCostAndTheBalance() {
        StationNotice started = StationNotice.started("Alpha", EARTH, MOON, 10, "in_system", 2_000_000).forVisitors();
        assertEquals(StationNoticeKind.WARP_VISITOR, started.kind());
        assertEquals(List.of("Alpha", "WARP_STARTED", MOON.toString()), started.arguments());
        StationNotice committed = StationNotice.committed("Alpha", EARTH, MOON, "interstellar", 8_000_000, 500_000)
                .forVisitors();
        assertEquals(List.of("Alpha", "WARP_COMMITTED", MOON.toString()), committed.arguments());
        for (StationNotice unchanged : List.of(StationNotice.countdown("Alpha", MOON, 5),
                StationNotice.aborted("Alpha", StationManagementCode.WARP_ACTOR_CHANGED, MOON), committed)) {
            assertSame(unchanged, unchanged.forVisitors(), unchanged.toString());
        }
        assertFalse(StationNoticeKind.WARP_VISITOR.stored());
        assertThrows(IllegalStateException.class, committed::summary);
        for (StationManagementCode code : StationManagementCode.values()) {
            assertEquals(code == StationManagementCode.WARP_STARTED || code == StationManagementCode.WARP_COMMITTED,
                    StationNoticeKind.WARP_VISITOR.allows(code), code.name());
        }
        TranslatableContents contents = (TranslatableContents) StationNoticeService.component(started).getContents();
        assertEquals(PREFIX + "warp_visitor", contents.getKey());
        assertEquals("station.advancedrocketrycommunity.code.warp_started",
                ((TranslatableContents) ((Component) contents.getArgs()[1]).getContents()).getKey());
    }

    @Test
    void theComponentUsesTheCodeKeyAndTheCostClassKey() {
        Component component = StationNoticeService.component(
                StationNotice.committed("Alpha", EARTH, MOON, "in_system", 2_000_000, 500_000));
        TranslatableContents contents = (TranslatableContents) component.getContents();
        assertEquals(PREFIX + "warp_committed", contents.getKey());
        Object[] arguments = contents.getArgs();
        assertEquals(7, arguments.length);
        assertEquals("Alpha", ((Component) arguments[0]).getString());
        assertEquals("station.advancedrocketrycommunity.code.warp_committed",
                ((TranslatableContents) ((Component) arguments[1]).getContents()).getKey());
        assertEquals("station.advancedrocketrycommunity.warp_class.in_system",
                ((TranslatableContents) ((Component) arguments[4]).getContents()).getKey());
        assertEquals("500000", ((Component) arguments[6]).getString());
    }

    @Test
    void everyRoleRefusesNonCanonicalValues() {
        String longest = "x".repeat(StationLimits.MAX_NAME_LENGTH);
        assertEquals(longest, StationNotice.countdown(longest, MOON, 1).station());
        for (String name : new String[] {"", " Alpha", "Alpha ", "x".repeat(StationLimits.MAX_NAME_LENGTH + 1),
                "Al\npha", "Al\u0000pha"}) {
            assertThrows(IllegalArgumentException.class, () -> StationNotice.countdown(name, MOON, 1), name);
        }
        for (List<String> arguments : List.of(
                List.of("Alpha", "moon", "1"),
                List.of("Alpha", "Advanced:Moon", "1"),
                List.of("Alpha", MOON.toString(), "-1"),
                List.of("Alpha", MOON.toString(), "01"),
                List.of("Alpha", MOON.toString(), "2147483648"),
                List.of("Alpha", MOON.toString()),
                List.of("Alpha", MOON.toString(), "1", "1"))) {
            assertThrows(IllegalArgumentException.class,
                    () -> new StationNotice(StationNoticeKind.WARP_COUNTDOWN, arguments), arguments.toString());
        }
        assertEquals("2147483647", StationNotice.countdown("Alpha", MOON, Integer.MAX_VALUE).arguments().get(2));
        // Each outcome carries only its own code: a start or commit its success code, an abort a cancel or refusal.
        assertThrows(IllegalArgumentException.class, () -> new StationNotice(StationNoticeKind.WARP_COMMITTED,
                List.of("Alpha", "WARP_STARTED", EARTH.toString(), MOON.toString(), "in_system", "1", "1")));
        assertThrows(IllegalArgumentException.class,
                () -> StationNotice.aborted("Alpha", StationManagementCode.WARP_COMMITTED, MOON));
        assertThrows(IllegalArgumentException.class,
                () -> StationNotice.aborted("Alpha", StationManagementCode.WARP_ISSUED, MOON));
        assertThrows(IllegalArgumentException.class, () -> new StationNotice(StationNoticeKind.WARP_ABORTED,
                List.of("Alpha", "NO_SUCH_CODE", MOON.toString())));
        assertThrows(IllegalArgumentException.class,
                () -> StationNotice.started("Alpha", EARTH, MOON, 10, "IN_SYSTEM", 1));
        for (StationManagementCode code : StationManagementCode.values()) {
            boolean abort = code == StationManagementCode.WARP_CANCELLED || !code.success();
            assertEquals(abort, StationNoticeKind.WARP_ABORTED.allows(code), code.name());
        }
    }

    @Test
    void envelopeBytesMatchTheNbtWriter() throws IOException {
        for (StationNotice notice : List.of(
                StationNotice.started("Alpha", EARTH, MOON, 10, "in_system", 2_000_000),
                StationNotice.countdown("空间站 Alpha", MOON, 3),
                StationNotice.committed("é".repeat(48), EARTH, MOON, "interstellar", 10_000_000, 10_000_000),
                StationNotice.aborted("Alpha", StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER, MOON),
                StationNotice.aborted("Alpha", StationManagementCode.WARP_CANCELLED, MOON).brief())) {
            assertEquals(payload(notice), StationNoticeCodec.envelopeBytes(notice), notice.toString());
        }
    }

    /** Section 5.3 byte-budget admission: a full outcome is kept only within 512 bytes, else its brief outcome. */
    @Test
    void anOutcomeOverTheEnvelopeBudgetIsAdmittedOnlyAsItsBriefOutcome() throws IOException {
        StationNotice ordinary = StationNotice.committed("Alpha", EARTH, MOON, "in_system", 2_000_000, 500_000);
        assertSame(ordinary, ordinary.summary());
        // Grow the target body one character at a time across the budget.
        StationNotice atBudget = null;
        StationNotice overBudget = null;
        for (int length = 1; overBudget == null; length++) {
            StationNotice candidate = StationNotice.committed("Alpha", EARTH,
                    ModIdentity.id("b".repeat(length)), "in_system", 1, 1);
            if (payload(candidate) <= StationNoticeLimits.MAX_ENVELOPE_BYTES) {
                atBudget = candidate;
            } else {
                overBudget = candidate;
            }
        }
        assertEquals(StationNoticeLimits.MAX_ENVELOPE_BYTES, payload(atBudget), "the budget boundary is reachable");
        assertSame(atBudget, atBudget.summary());
        assertEquals(StationNoticeLimits.MAX_ENVELOPE_BYTES + 1, payload(overBudget));
        StationNotice brief = overBudget.summary();
        assertEquals(StationNoticeKind.WARP_OUTCOME, brief.kind());
        assertEquals(List.of("Alpha", "WARP_COMMITTED"), brief.arguments());
        // The worst brief outcome: the longest three-byte name and the longest code name.
        String longestCode = Arrays.stream(StationManagementCode.values()).map(Enum::name)
                .max(java.util.Comparator.comparingInt(String::length)).orElseThrow();
        StationNotice worst = new StationNotice(StationNoticeKind.WARP_OUTCOME,
                List.of("空".repeat(StationLimits.MAX_NAME_LENGTH), longestCode));
        assertTrue(payload(worst) <= 250, "the brief outcome always fits: " + payload(worst));
        assertThrows(IllegalStateException.class, () -> StationNotice.countdown("Alpha", MOON, 1).summary());
        assertFalse(StationNoticeKind.WARP_STARTED.stored());
    }
}
