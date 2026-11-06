package io.github.sunthemoon.advancedrocketrycommunity.rocket.entity;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketSeatedConsoleGameTests.*;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.FirstEventGameTestPlayers;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W311: the seated use key reaches the rider's own rocket wherever the rocket's base sits in its entity section. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketSeatedPickGameTests {
    /** The last block of a section: vanilla's 4-block search margin then ends above the rocket's section. */
    private static final int WORST_SECTION_OFFSET = 15;

    private RocketSeatedPickGameTests() { }

    @GameTest(template = "rocket_test", batch = "rocket_seated_console")
    public static void seatedUseKeyTargetsTheRocketOutsideVanillasSectionMargin(GameTestHelper helper) {
        var level = helper.getLevel();
        var at = sectionAligned(helper, WORST_SECTION_OFFSET);
        var rider = FirstEventGameTestPlayers.join(level.getServer(), UUID.randomUUID(), "SeatPick", level, at);
        var rocket = rocket(helper, at, rider.getUUID());
        try {
            seat(helper, rocket, rider);
            // Precondition: a level look searches sections from 4 blocks below (box - 1) upward, which excludes the base's.
            int searched = SectionPos.posToSectionCoord(rider.getBoundingBox().minY - 1.0D - 4.0D);
            helper.assertTrue(searched > SectionPos.blockToSectionCoord(rocket.getBlockY()),
                    "Precondition: the rocket's section is inside vanilla's search margin");
            int vanillaMisses = 0;
            for (float[] view : VIEWS) {
                look(rider, view);
                HitResult hit = pick(rider);
                var picked = hit instanceof EntityHitResult entity ? entity.getEntity() : null;
                vanillaMisses += picked == rocket ? 0 : 1;
                var target = picked != null ? picked : RocketSeatedPick.target(rider, hit).orElse(null);
                helper.assertTrue(target == rocket, "The seated use key missed the rocket at yaw " + view[0]
                        + ", pitch " + view[1] + " (vanilla missed " + vanillaMisses + "): " + hit);
            }
            helper.assertTrue(RocketSeatedPick.target(rider, new EntityHitResult(rider)).isEmpty(),
                    "An entity that vanilla picked was replaced");
            var miss = BlockHitResult.miss(rider.getEyePosition(), net.minecraft.core.Direction.UP, rider.blockPosition());
            rider.stopRiding();
            rider.teleportTo(level, rocket.getX(), rocket.getY() + TOP_SEAT.y() + 0.15D, rocket.getZ(), 0.0F, 0.0F);
            helper.assertTrue(rocket.getBoundingBox().contains(rider.getEyePosition())
                    && RocketSeatedPick.target(rider, miss).isEmpty(), "A player who is not seated was redirected");
            helper.succeed();
        } finally {
            rider.stopRiding(); rocket.discard(); level.getServer().getPlayerList().remove(rider);
        }
    }
}
