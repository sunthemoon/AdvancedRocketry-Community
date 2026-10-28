package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.DISTANCE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.FOREIGN_OWNER;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.RATE;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.SPECTATOR;
import static io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check.UNLOADED;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityFixture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Outcome;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityProbe;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * W179: the classic machine menus of the menu-authority matrix, on the fixtures of their own GameTests. The lathe
 * (the menu type of every classic processing controller) has no owner and no client action. The observatory's
 * research menu opens for anyone, but only the research owner's intents are served, ten a second.
 */
public final class ClassicMenuAuthorityProbes {
    /** A status no research intent answers, set before each action to see whether the action reached the service. */
    private static final ResearchStatus UNANSWERED = ResearchStatus.IDLE;
    /** Claiming the selected job: the service answers it for every caller, and with no job it changes nothing. */
    private static final int CLAIM = 4;

    private ClassicMenuAuthorityProbes() {
    }

    public static List<MenuAuthorityProbe> all() {
        return List.of(
                MenuAuthorityProbe.of("lathe", false, 100, Lathe::new, DISTANCE, SPECTATOR, UNLOADED),
                MenuAuthorityProbe.of("ground_research", true, 200, Research::new,
                        DISTANCE, SPECTATOR, FOREIGN_OWNER, RATE, UNLOADED));
    }

    private static final class Lathe extends MenuAuthorityFixture.OfBlock {
        private final ClassicLatheGameTests.Fixture fixture;

        private Lathe(GameTestHelper helper, ClassicLatheGameTests.Fixture fixture) {
            super(helper.getLevel(), fixture.origin, UUID.randomUUID());
            this.fixture = fixture;
        }

        Lathe(GameTestHelper helper) {
            this(helper, new ClassicLatheGameTests.Fixture(helper, Direction.NORTH, false));
        }

        @Override
        public boolean ready() {
            return fixture.controller.formed();
        }

        @Override
        public void close() {
            fixture.close();
        }
    }

    private static final class Research extends MenuAuthorityFixture.OfBlock {
        private final ResearchFixture fixture;

        private Research(GameTestHelper helper, UUID owner, ResearchFixture fixture) {
            super(helper.getLevel(), fixture.origin, owner);
            this.fixture = fixture;
        }

        private Research(GameTestHelper helper, UUID owner) {
            this(helper, owner, ResearchGameTests.fixture(helper, false, Direction.NORTH, owner, 0));
        }

        Research(GameTestHelper helper) {
            this(helper, UUID.randomUUID());
        }

        @Override
        public boolean ready() {
            return fixture.ticking() && fixture.controller.formed();
        }

        /**
         * Accepted when the claim reached the research service for this player, refused when the service answered
         * that the player is not the research owner, and rate-limited when the intent limiter stopped it first.
         */
        @Override
        public Outcome act(ServerPlayer player, AbstractContainerMenu menu) {
            fixture.controller.status(UNANSWERED);
            menu.clickMenuButton(player, CLAIM);
            ResearchStatus answer = fixture.controller.researchStatus();
            return answer == UNANSWERED ? Outcome.RATE_LIMITED
                    : answer == ResearchStatus.UNAUTHORIZED ? Outcome.REFUSED : Outcome.ACCEPTED;
        }

        @Override
        public void close() {
            fixture.close();
        }
    }
}
