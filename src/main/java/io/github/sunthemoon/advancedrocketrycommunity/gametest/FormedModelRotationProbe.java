package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicControllerBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.ClassicModelProjection;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime.FormedModelController;
import java.util.Objects;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Checks the vanilla packet and client replica across a same-block rotation, without client classes on a server. */
public final class FormedModelRotationProbe {
    private FormedModelRotationProbe() { }

    public static BlockState rotate(GameTestHelper helper, BlockEntity controller) {
        var before = controller.getBlockState();
        set(helper, controller, before.rotate(Rotation.CLOCKWISE_90));
        return before;
    }

    public static void set(GameTestHelper helper, BlockEntity controller, BlockState state) {
        helper.getLevel().setBlock(controller.getBlockPos(), state, Block.UPDATE_CLIENTS);
        helper.assertTrue(helper.getLevel().getBlockEntity(controller.getBlockPos()) == controller,
                "Rotation replaced the formed controller");
    }

    public static void assertPresentation(GameTestHelper helper, BlockEntity controller, boolean expected) {
        var packet = (ClientboundBlockEntityDataPacket) controller.getUpdatePacket();
        helper.assertTrue(((FormedModelController) controller).modelFormed() == expected
                        && ClassicModelProjection.decode(controller.getUpdateTag()) == expected
                        && packet != null && ClassicModelProjection.decode(packet.getTag()) == expected,
                "Rotated controller published a hull inconsistent with its validated facing: " + controller.getUpdateTag());
        var replica = Objects.requireNonNull(controller.getType().create(controller.getBlockPos(), controller.getBlockState()));
        var saved = replica.saveWithoutMetadata();
        replica.onDataPacket(null, packet);
        helper.assertTrue(((FormedModelController) replica).modelFormed() == expected
                        && replica.getBlockState().getValue(ClassicControllerBlock.FACING)
                                == controller.getBlockState().getValue(ClassicControllerBlock.FACING)
                        && saved.equals(replica.saveWithoutMetadata()),
                "Rotation packet changed client orientation, presentation or saved authority");
    }
}
