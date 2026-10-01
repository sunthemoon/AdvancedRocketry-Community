package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EndgameEffectGameTests {
    private EndgameEffectGameTests() { }

    @GameTest(templateNamespace = SatellitePayloadFixture.HOST, template = "empty", timeoutTicks = 20)
    public static void aListenerOnTheForgeBusVetoesOnlyTheEffectsItProtectsAgainst(GameTestHelper helper) {
        UUID stranger = UUID.randomUUID();
        UUID trusted = UUID.randomUUID();
        EndgameEffectFixture.VETOED_OWNERS.add(stranger);
        try {
            BlockPos corner = helper.absolutePos(BlockPos.ZERO);
            EndgameEffectEvent vetoed = event(stranger, helper, corner);
            helper.assertTrue(MinecraftForge.EVENT_BUS.post(vetoed) && vetoed.isCanceled(), "The veto did not cancel");
            helper.assertTrue(EndgameEffectFixture.lastSeen == vetoed, "The fixture did not see the event");
            EndgameEffectEvent allowed = event(trusted, helper, corner);
            helper.assertTrue(!MinecraftForge.EVENT_BUS.post(allowed), "An unrelated owner was vetoed");
            helper.assertTrue(allowed.actorId().isEmpty() && allowed.max().equals(corner.offset(2, 0, 2)),
                    "The event changed its batch");
        } finally {
            EndgameEffectFixture.VETOED_OWNERS.remove(stranger);
        }
        helper.succeed();
    }

    private static EndgameEffectEvent event(UUID owner, GameTestHelper helper, BlockPos corner) {
        return new EndgameEffectEvent(SatellitePayloadFixture.host("laser_drill"),
                EndgameEffect.BLOCK_BREAK, owner, Optional.empty(), helper.getLevel().dimension(), corner,
                corner.offset(2, 0, 2));
    }
}
