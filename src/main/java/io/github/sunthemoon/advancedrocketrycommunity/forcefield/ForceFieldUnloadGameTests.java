package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldUnloadGameTests {
    @GameTest(template = "empty", batch = "force_field_unload", timeoutTicks = 400)
    public static void differentChunksActuallyUnloadReloadWithoutLosingSealedClaims(GameTestHelper helper) {
        var f = new ForceFieldGameTests.Fixture(helper, Direction.EAST);
        boolean previous = ForceFieldConfig.enabled();
        ForceFieldConfig.ENABLED.set(false);
        f.power(true);
        var field = f.field(32, f.identity);
        f.source.claims(FieldIdentity.bit(32)); f.data.reserve(f.identity, FieldIdentity.bit(32));
        var global = FieldCleanupSavedData.get(f.level.getServer()); global.reserve(f.identity, FieldIdentity.bit(32));
        ChunkPos sourceChunk = new ChunkPos(f.identity.source());
        ChunkPos targetChunk = new ChunkPos(field.getBlockPos());
        helper.assertTrue(!sourceChunk.equals(targetChunk), "Fixture does not cross a chunk boundary");
        // A FULL-only target ticket keeps just this fixture chunk available; production creates no tickets.
        f.level.getChunkSource().addRegionTicket(TicketType.PORTAL, targetChunk, 0, field.getBlockPos());
        f.level.getChunkAt(f.identity.source()).setUnsaved(true);
        poll(helper, f, targetChunk, field.getBlockPos(), previous, 0);
    }
    private static void poll(GameTestHelper helper, ForceFieldGameTests.Fixture f, ChunkPos targetChunk,
            net.minecraft.core.BlockPos target, boolean previous, int attempts) {
        helper.runAfterDelay(1, () -> {
            if (ForceFieldService.loaded(f.level, f.identity.source()) && attempts < 350) {
                poll(helper, f, targetChunk, target, previous, attempts + 1);
                return;
            }
            try {
                helper.assertTrue(!ForceFieldService.loaded(f.level, f.identity.source()), "Source never actually unloaded");
                helper.assertTrue(ForceFieldService.loaded(f.level, target), "Target chunk did not remain available");
                var before = f.level.getForcedChunks();
                ForceFieldService.work(f.level.getServer(), f.data, f.data.view().get(f.identity.key()));
                ForceFieldService.validate(f.level.getServer(), f.data, new FieldCleanupSavedData.Observation(f.identity, target));
                helper.assertTrue(!ForceFieldService.loaded(f.level, f.identity.source()) && before.equals(f.level.getForcedChunks())
                        && !f.data.view().get(f.identity.key()).orphan(), "Unavailable source was loaded or treated as removed");
                var state = f.level.getBlockState(target);
                helper.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(f.level, target)), "Unload changed collision");
                var view = new ServerLevelVolumeWorldView(f.level, false);
                helper.assertTrue(view.observe(new VolumePosition(target.getX(), target.getY(), target.getZ())) == CellObservation.SEALED,
                        "Unload made the physical field permeable");
                f.load(f.identity.source());
                var restored = (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.identity.source());
                helper.assertTrue(restored != f.source && restored.identity().equals(f.identity)
                        && restored.state().claims() == FieldIdentity.bit(32), "Native chunk reload lost projector claims");
                helper.assertTrue(ForceFieldService.validate(f.level.getServer(), f.data,
                        new FieldCleanupSavedData.Observation(f.identity, target)), "Reload did not reconcile an exact field");
                helper.succeed();
            } finally {
                f.level.getChunkSource().removeRegionTicket(TicketType.PORTAL, targetChunk, 0, target);
                for (int i = 1; i <= 32; i++) { f.load(f.identity.target(i)); }
                f.close();
                ForceFieldConfig.ENABLED.set(previous);
            }
        });
    }
    private ForceFieldUnloadGameTests() { }
}
