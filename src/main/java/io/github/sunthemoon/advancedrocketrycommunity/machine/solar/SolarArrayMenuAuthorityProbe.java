package io.github.sunthemoon.advancedrocketrycommunity.machine.solar;

import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityFixture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityMatrix.Check;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.MenuAuthorityProbe;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;

/**
 * W179: the solar array of the menu-authority matrix, built by {@link SolarArrayFixture} as its own GameTests build
 * it, in the {@code solar_array_test} template. The array has no owner and its scalar menu no client action.
 */
public final class SolarArrayMenuAuthorityProbe {
    private SolarArrayMenuAuthorityProbe() {
    }

    public static MenuAuthorityProbe probe() {
        return MenuAuthorityProbe.of("solar_array", false, 40, Array::new, Check.DISTANCE, Check.SPECTATOR,
                Check.UNLOADED).in("solar_array_test");
    }

    private static final class Array extends MenuAuthorityFixture.OfBlock {
        private final SolarArrayFixture fixture;

        private Array(GameTestHelper helper, SolarArrayFixture fixture) {
            super(helper.getLevel(), fixture.position, UUID.randomUUID());
            this.fixture = fixture;
        }

        Array(GameTestHelper helper) {
            this(helper, install(helper));
        }

        private static SolarArrayFixture install(GameTestHelper helper) {
            BlockPos anchor = helper.absolutePos(new BlockPos(5, 2, 5));
            var fixture = new SolarArrayFixture(helper.getLevel(),
                    new BlockPos(anchor.getX(), helper.getLevel().getMaxBuildHeight() - 2, anchor.getZ()));
            try {
                fixture.install(fixture.position, 1);
                return fixture;
            } catch (RuntimeException | Error failed) {
                fixture.close();
                throw failed;
            }
        }

        @Override
        public boolean ready() {
            return fixture.array.structure() == SolarArrayRules.Structure.FORMED;
        }

        @Override
        public void close() {
            fixture.close();
        }
    }
}
