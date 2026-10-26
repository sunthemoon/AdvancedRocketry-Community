package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPropulsionFuel;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Exercises the native cut fixture's actual entity initialization and publication. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EscrowItemCutFixtureGameTests {
    @GameTest(template = "rocket_test", batch = "item_cut_fixture", timeoutTicks = 40)
    public static void itemCutRocketPublishesWithItsOwnMonoFuel(GameTestHelper helper) {
        var rocket = EscrowItemCutProbe.cargoRocket(helper.getLevel(), helper.absolutePos(new BlockPos(1, 1, 1)), 10);
        try {
            var vector = rocket.flightData().orElseThrow().fuel().propulsion().orElseThrow();
            // Reconstruct through the production bank-role validator, not a fixture-only acceptance predicate.
            var checked = new RocketPropulsionFuel(vector.descriptor(), vector.primary(), vector.oxidizer(), vector.revision());
            helper.assertTrue(checked.descriptor().equals(EscrowItemCutProbe.PROPULSION)
                    && ModIdentity.id("rocket_fuel").equals(checked.primary().fluidId())
                    && checked.primary().amount() == 1_000 && checked.oxidizer().amount() == 0,
                    "Published fixture fuel does not match its selected MONO propulsion");
            helper.assertTrue(rocket.snapshot().orElseThrow().propulsion().orElseThrow().equals(checked.descriptor())
                    && EscrowItemCutProbe.cargo(rocket) == 10 && rocket.escrowSection().epoch() == 0,
                    "Fixture creation changed the propulsion binding, cargo or neutral wrapper");
        } finally { rocket.discard(); }
        helper.succeed();
    }
}
