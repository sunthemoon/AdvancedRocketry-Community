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
}
