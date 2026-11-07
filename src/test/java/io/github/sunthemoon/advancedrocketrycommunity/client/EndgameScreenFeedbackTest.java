package io.github.sunthemoon.advancedrocketrycommunity.client;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameIntentGuard;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.network.EndgameDeviceView;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EndgameScreenFeedbackTest {
    private static final String VIEW = "advancedrocketrycommunity.endgame.view.";

    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void onlyStructuredEndgameRefusalsArePinnedAndProgressIsNot() {
        var nonRefusals = Set.of(EndgameCode.OK, EndgameCode.COMPLETE, EndgameCode.STOPPED,
                EndgameCode.GENERATING, EndgameCode.RIDE_COUNTDOWN, EndgameCode.CONFIRM_REQUIRED,
                EndgameCode.AWAITING_WORLD_SAVE);
        for (EndgameCode code : EndgameCode.values()) {
            var message = message(code);
            assertEquals(!nonRefusals.contains(code), EndgameScreenFeedback.refusal(message).isPresent(), code.name());
        }
        assertTrue(EndgameScreenFeedback.refusal(null).isEmpty());
        assertTrue(EndgameScreenFeedback.refusal(net.minecraft.network.chat.Component.literal("Not enough energy [INSUFFICIENT_ENERGY]")).isEmpty());
        assertTrue(EndgameScreenFeedback.refusal(net.minecraft.network.chat.Component.translatable("other.key", "INSUFFICIENT_ENERGY")).isEmpty());
        assertTrue(EndgameScreenFeedback.refusal(net.minecraft.network.chat.Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, "text", "UNKNOWN")).isEmpty());
        assertTrue(EndgameScreenFeedback.refusal(net.minecraft.network.chat.Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY, "text")).isEmpty());
    }

    @Test void eachSystemReleasesOnItsSelectionsSettingsAndRelevantStateChanges() {
        for (EndgameSystem system : systems()) {
            var before = view(system, EndgameCode.UNFORMED);
            assertTrue(answers(EndgameCode.UNFORMED, before, view(system, EndgameCode.OK)));
            assertTrue(answers(EndgameCode.UNAUTHORIZED, before,
                    new EndgameDeviceView(7, system, EndgameCode.UNFORMED, EndgameCode.OK, false, List.of())));
        }
        for (String field : List.of("destination", "destination_at", "route", "auto", "redstone", "min_stack")) {
            changedFieldAnswers(EndgameSystem.RAILGUN, field);
        }
        for (String field : List.of("anchor", "pair", "far_end", "far_end_at", "auto", "redstone")) {
            changedFieldAnswers(EndgameSystem.SPACE_ELEVATOR, field);
        }
        for (String field : List.of("running", "mode", "redstone", "lens", "body", "table", "target", "target_at", "link", "warning")) {
            changedFieldAnswers(EndgameSystem.LASER_DRILL, field);
        }
        for (String field : List.of("structure", "body", "fuel_table")) {
            changedFieldAnswers(EndgameSystem.BLACK_HOLE_GENERATOR, field);
        }
    }

    @Test void counterUpdatesAndTheRefusalItselfDoNotDismissTheNotice() {
        for (EndgameSystem system : systems()) {
            var before = view(system, EndgameCode.OK);
            var refused = new EndgameDeviceView(7, system, EndgameCode.INSUFFICIENT_ENERGY,
                    EndgameCode.INSUFFICIENT_ENERGY, true, List.of());
            assertFalse(answers(EndgameCode.INSUFFICIENT_ENERGY, before, refused));
            assertTrue(answers(EndgameCode.INSUFFICIENT_ENERGY, refused, before));
            var recorded = new EndgameDeviceView(7, system, EndgameCode.OK,
                    EndgameCode.INSUFFICIENT_ENERGY, true, List.of());
            assertFalse(answers(EndgameCode.INSUFFICIENT_ENERGY, before, recorded));
            assertTrue(answers(EndgameCode.INSUFFICIENT_ENERGY, recorded, before));
            for (String field : List.of("operations", "output", "outbox", "in_transit", "incoming", "receipts")) {
                assertFalse(answers(EndgameCode.UNAUTHORIZED, view(system, EndgameCode.OK, line(field, "0")),
                        view(system, EndgameCode.OK, line(field, "1"))));
            }
            assertFalse(answers(EndgameCode.UNAUTHORIZED, before, view(system, EndgameCode.GENERATING)));
        }
        assertFalse(answers(EndgameCode.TARGET_PROTECTED,
                view(EndgameSystem.LASER_DRILL, EndgameCode.OK, line("link", "laser_target abcd @ moon paid=10")),
                view(EndgameSystem.LASER_DRILL, EndgameCode.OK, line("link", "laser_target abcd @ moon paid=9"))));
        assertFalse(answers(EndgameCode.UNFORMED, view(EndgameSystem.RAILGUN, EndgameCode.OK),
                view(EndgameSystem.RAILGUN, EndgameCode.UNFORMED, line("structure", EndgameCode.UNFORMED.translationKey()))));
        assertFalse(answers(EndgameCode.NO_TARGET, view(EndgameSystem.RAILGUN, EndgameCode.OK),
                view(EndgameSystem.RAILGUN, EndgameCode.OK, line("route", "NO_TARGET"))));
    }

    @Test void outboxChangesAnswerOnlyAnOutboxRefusal() {
        for (EndgameSystem system : List.of(EndgameSystem.RAILGUN, EndgameSystem.SPACE_ELEVATOR)) {
            var full = view(system, EndgameCode.OK, line("outbox", "64 / 64"));
            var available = view(system, EndgameCode.OK, line("outbox", "63 / 64"));
            assertTrue(answers(EndgameCode.OUTBOX_FULL, full, available));
            assertFalse(answers(EndgameCode.OUTBOX_FULL, available, available));
            assertFalse(answers(EndgameCode.UNAUTHORIZED, full, available));
            assertFalse(answers(EndgameCode.OUTBOX_FULL, available, full));
            assertFalse(answers(EndgameCode.OUTBOX_FULL, full, view(system, EndgameCode.OK, line("outbox", "?"))));
        }
    }

    @Test void railgunEnergyRefusalEndsOnlyWhenItsServerQuoteIsAffordable() {
        var before = view(EndgameSystem.RAILGUN, EndgameCode.OK, line("route", "LOCAL 40000 FE 20 t"));
        assertFalse(EndgameScreenFeedback.answers(EndgameCode.INSUFFICIENT_ENERGY, before, before, 0, 39999));
        assertTrue(EndgameScreenFeedback.answers(EndgameCode.INSUFFICIENT_ENERGY, before, before, 39999, 40000));
        assertFalse(EndgameScreenFeedback.answers(EndgameCode.UNAUTHORIZED, before, before, 0, 40000));
        for (String route : List.of("-", "IN_SYSTEM 40000 FE 20 t", "LOCAL bad FE 20 t", "LOCAL 9999999999 FE 20 t")) {
            var malformed = view(EndgameSystem.RAILGUN, EndgameCode.OK, line("route", route));
            assertFalse(EndgameScreenFeedback.answers(EndgameCode.INSUFFICIENT_ENERGY, malformed, malformed, 0, 500000));
        }
    }

    @Test void missingQuotesAndForeignViewsCannotReleaseARefusal() {
        for (EndgameSystem system : systems()) {
            var before = view(system, EndgameCode.OK);
            assertFalse(EndgameScreenFeedback.answers(EndgameCode.INSUFFICIENT_ENERGY, before, before, 0, 1000000));
            assertFalse(answers(EndgameCode.UNFORMED, before,
                    new EndgameDeviceView(8, system, EndgameCode.OK, EndgameCode.OK, true, List.of())));
        }
        assertFalse(answers(EndgameCode.NO_TARGET, view(EndgameSystem.RAILGUN, EndgameCode.OK),
                view(EndgameSystem.SPACE_ELEVATOR, EndgameCode.OK)));
    }

    private static void changedFieldAnswers(EndgameSystem system, String field) {
        var before = view(system, EndgameCode.OK, line(field, "old"));
        var after = view(system, EndgameCode.OK, line(field, "new"));
        assertTrue(answers(EndgameCode.NO_TARGET, before, after), system + ": " + field);
    }

    static net.minecraft.network.chat.Component message(EndgameCode code) {
        return net.minecraft.network.chat.Component.translatable(EndgameIntentGuard.STATUS_LINE_KEY,
                net.minecraft.network.chat.Component.translatable(code.translationKey()), code.name());
    }

    static EndgameDeviceView view(EndgameSystem system, EndgameCode code, EndgameDeviceView.Line... lines) {
        return new EndgameDeviceView(7, system, code, EndgameCode.OK, true, List.of(lines));
    }

    static EndgameDeviceView.Line line(String field, String value) { return EndgameDeviceView.Line.text(VIEW + field, value); }

    private static List<EndgameSystem> systems() {
        return List.of(EndgameSystem.RAILGUN, EndgameSystem.SPACE_ELEVATOR, EndgameSystem.LASER_DRILL, EndgameSystem.BLACK_HOLE_GENERATOR);
    }

    private static boolean answers(EndgameCode code, EndgameDeviceView before, EndgameDeviceView after) {
        return EndgameScreenFeedback.answers(code, before, after, 0, 0);
    }
}
