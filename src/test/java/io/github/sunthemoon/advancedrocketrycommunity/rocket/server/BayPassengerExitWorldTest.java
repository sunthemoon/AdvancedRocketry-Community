package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.shapes.Shapes;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayPassengerExitWorldTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void centralizedVanillaHazardTableAndNonhazardousAirAndStone() {
        Map.ofEntries(Map.entry(Blocks.FIRE, Hazard.FIRE), Map.entry(Blocks.SOUL_FIRE, Hazard.SOUL_FIRE),
                Map.entry(Blocks.CAMPFIRE, Hazard.CAMPFIRE), Map.entry(Blocks.SOUL_CAMPFIRE, Hazard.CAMPFIRE),
                Map.entry(Blocks.LAVA, Hazard.LAVA), Map.entry(Blocks.CACTUS, Hazard.CACTUS),
                Map.entry(Blocks.MAGMA_BLOCK, Hazard.MAGMA), Map.entry(Blocks.SWEET_BERRY_BUSH, Hazard.BERRY_BUSH),
                Map.entry(Blocks.POWDER_SNOW, Hazard.POWDER_SNOW), Map.entry(Blocks.WITHER_ROSE, Hazard.WITHER_ROSE),
                Map.entry(Blocks.POINTED_DRIPSTONE, Hazard.POINTED_DRIPSTONE), Map.entry(Blocks.NETHER_PORTAL, Hazard.PORTAL),
                Map.entry(Blocks.END_PORTAL, Hazard.PORTAL), Map.entry(Blocks.END_GATEWAY, Hazard.GATEWAY),
                Map.entry(Blocks.LADDER, Hazard.UNKNOWN), Map.entry(Blocks.VINE, Hazard.UNKNOWN), Map.entry(Blocks.SCAFFOLDING, Hazard.UNKNOWN),
                Map.entry(Blocks.WEEPING_VINES, Hazard.UNKNOWN), Map.entry(Blocks.WEEPING_VINES_PLANT, Hazard.UNKNOWN),
                Map.entry(Blocks.TWISTING_VINES, Hazard.UNKNOWN), Map.entry(Blocks.TWISTING_VINES_PLANT, Hazard.UNKNOWN))
                .forEach((block, expected) -> assertEquals(expected, BayPassengerExitWorld.hazard(block.defaultBlockState()), block.toString()));
        assertEquals(Hazard.NONE, BayPassengerExitWorld.hazard(Blocks.AIR.defaultBlockState()));
        assertEquals(Hazard.NONE, BayPassengerExitWorld.hazard(Blocks.STONE.defaultBlockState()));
    }
    @Test void fullUpperFaceOnlyAndUnstableSupportRefuses() {
        assertEquals(Support.FULL_STABLE, BayPassengerExitWorld.support(Blocks.STONE.defaultBlockState(), Shapes.block()));
        assertEquals(Support.FALLING, BayPassengerExitWorld.support(Blocks.SAND.defaultBlockState(), Shapes.block()));
        assertEquals(Support.MOVING_PISTON, BayPassengerExitWorld.support(Blocks.MOVING_PISTON.defaultBlockState(), Shapes.block()));
        assertEquals(Support.OPEN_TRAPDOOR, BayPassengerExitWorld.support(Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.OPEN, true), Shapes.block()));
        assertEquals(Support.PARTIAL, BayPassengerExitWorld.support(Blocks.STONE.defaultBlockState(), Shapes.box(0, 0, 0, .9, 1, 1)));
        assertEquals(Support.PARTIAL, BayPassengerExitWorld.support(Blocks.STONE.defaultBlockState(), Shapes.box(0, 0, 0, 1, .5, 1)));
    }
}
