package io.github.sunthemoon.advancedrocketrycommunity.rocket.menu;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketFlightFeedbackTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void boardingAndLeavingHaveTheirOwnSuccessfulRepliesWithoutFuelArguments() {
        var success = new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, 0);
        for (var action : new RocketFlightAction[]{RocketFlightAction.BOARD, RocketFlightAction.LEAVE}) {
            var contents = (TranslatableContents) RocketFlightFeedback.reply(action, success).getContents();
            assertEquals(action == RocketFlightAction.BOARD ? RocketFlightFeedback.BOARDED : RocketFlightFeedback.LEFT,
                    contents.getKey());
            assertEquals(0, contents.getArgs().length);
        }
    }

    @Test void launchCancelAndEveryRefusalKeepTheirExistingCodeAndCost() {
        for (var code : RocketFlightRequestCode.values()) {
            for (var action : RocketFlightAction.values()) {
                if (code == RocketFlightRequestCode.SUCCESS
                        && (action == RocketFlightAction.BOARD || action == RocketFlightAction.LEAVE)) { continue; }
                var result = new RocketFlightRequestResult(code, 123);
                var contents = (TranslatableContents) RocketFlightFeedback.reply(action, result).getContents();
                assertEquals(code.translationKey(), contents.getKey());
                assertArrayEquals(new Object[]{123L}, contents.getArgs());
            }
        }
    }

    /** W311 (finding 7-5): the console showed "All fixed destination pads are occupied or unsafe" for a named pad. */
    @Test void aNamedPadRefusalRepliesWithThePadsOwnReasonAsTheChatLineDoes() {
        String reason = "message.advancedrocketrycommunity.pad.reason.identity";
        var result = new RocketFlightRequestResult(RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE, 527, reason);
        var reply = RocketFlightFeedback.reply(RocketFlightAction.LAUNCH, result);
        var contents = (TranslatableContents) reply.getContents();
        assertEquals("message.advancedrocketrycommunity.pad.landing_refused", contents.getKey());
        assertEquals(1, contents.getArgs().length);
        var argument = assertInstanceOf(net.minecraft.network.chat.Component.class, contents.getArgs()[0]);
        assertEquals(reason, ((TranslatableContents) argument.getContents()).getKey());
        assertTrue(RocketFlightFeedback.refusal(reply));
    }

    @Test void everyRefusedRequestIsARefusalButSuccessProgressAndOtherTextAreNot() {
        for (var code : RocketFlightRequestCode.values()) {
            var reply = RocketFlightFeedback.reply(RocketFlightAction.LAUNCH, new RocketFlightRequestResult(code, 1));
            assertEquals(code != RocketFlightRequestCode.SUCCESS && code != RocketFlightRequestCode.JOURNAL_PENDING,
                    RocketFlightFeedback.refusal(reply), code.name());
        }
        var success = new RocketFlightRequestResult(RocketFlightRequestCode.SUCCESS, 0);
        assertFalse(RocketFlightFeedback.refusal(RocketFlightFeedback.reply(RocketFlightAction.BOARD, success)));
        assertFalse(RocketFlightFeedback.refusal(RocketFlightFeedback.reply(RocketFlightAction.LEAVE, success)));
        assertFalse(RocketFlightFeedback.refusal(net.minecraft.network.chat.Component.literal(
                RocketFlightRequestCode.LANDING_PAD_UNAVAILABLE.translationKey())));
        assertFalse(RocketFlightFeedback.refusal(null));
    }
}
