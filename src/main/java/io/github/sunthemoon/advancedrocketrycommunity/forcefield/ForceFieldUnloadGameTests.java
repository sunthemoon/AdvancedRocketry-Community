package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.VolumePosition;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.ServerLevelVolumeWorldView;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.ChunkEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldUnloadGameTests {
    private static final TicketType<ChunkPos> FIXTURE_TICKET = TicketType.create(
            "arce_force_field_unload_fixture", java.util.Comparator.comparingLong(ChunkPos::toLong));
    @GameTest(template = "empty", batch = "force_field_unload", timeoutTicks = 400)
    public static void differentChunksActuallyUnloadReloadWithoutLosingSealedClaims(GameTestHelper helper) {
        var driver = new GameTestTickDriver(helper);
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
        f.level.getChunkSource().addRegionTicket(FIXTURE_TICKET, targetChunk, 0, targetChunk);
        f.load(field.getBlockPos());
        f.level.getChunkAt(f.identity.source()).setUnsaved(true);
        AtomicBoolean unloaded = new AtomicBoolean();
        AtomicBoolean targetUnloaded = new AtomicBoolean();
        Consumer<ChunkEvent.Unload> witness = event -> {
            if (event.getLevel() == f.level && event.getChunk().getPos().equals(sourceChunk)) { unloaded.set(true); }
            if (event.getLevel() == f.level && event.getChunk().getPos().equals(targetChunk)) { targetUnloaded.set(true); }
        };
        MinecraftForge.EVENT_BUS.addListener(witness);
        poll(helper, driver, f, targetChunk, field, previous, unloaded, targetUnloaded, witness, 0, false);
    }
    private static void poll(GameTestHelper helper, GameTestTickDriver driver, ForceFieldGameTests.Fixture f, ChunkPos targetChunk,
            ForceFieldBlockEntity originalField, boolean previous, AtomicBoolean unloaded, AtomicBoolean targetUnloaded,
            Consumer<ChunkEvent.Unload> witness, int attempts, boolean releasedTarget) {
        driver.after(1, () -> {
            var target = originalField.getBlockPos();
            boolean sourceUnavailable = !ForceFieldService.loaded(f.level, f.identity.source());
            boolean diskReady = unloaded.get() && targetUnloaded.get() && sourceUnavailable && !ForceFieldService.loaded(f.level, target);
            if ((!releasedTarget && !sourceUnavailable || releasedTarget && !diskReady) && attempts < 350) {
                poll(helper, driver, f, targetChunk, originalField, previous, unloaded, targetUnloaded, witness, attempts + 1, releasedTarget);
                return;
            }
            boolean handedOff = false;
            try {
                if (!releasedTarget) {
                    helper.assertTrue(sourceUnavailable, "Source never lost FULL availability");
                    helper.assertTrue(ForceFieldService.loaded(f.level, target), "Target chunk did not remain available");
                    var before = f.level.getForcedChunks();
                    ForceFieldService.work(f.level.getServer(), f.data, f.data.view().get(f.identity.key()));
                    ForceFieldService.validate(f.level.getServer(), f.data, new FieldCleanupSavedData.Observation(f.identity, target));
                    helper.assertTrue(!ForceFieldService.loaded(f.level, f.identity.source()) && before.equals(f.level.getForcedChunks())
                            && !f.data.view().get(f.identity.key()).orphan(), "Unavailable source was loaded or treated as removed");
                    assertSealed(helper, f, target);
                    // A nearby FULL chunk retains generation dependencies. Release our ticket to witness true disk unloads.
                    f.level.getChunkSource().removeRegionTicket(FIXTURE_TICKET, targetChunk, 0, targetChunk);
                    poll(helper, driver, f, targetChunk, originalField, previous, unloaded, targetUnloaded, witness, attempts + 1, true);
                    handedOff = true;
                    return;
                }
                helper.assertTrue(diskReady, "Source and field never actually unloaded");
                f.load(f.identity.source());
                f.load(target);
                var restored = (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.identity.source());
                helper.assertTrue(restored != f.source && restored.identity().equals(f.identity)
                        && restored.state().claims() == FieldIdentity.bit(32), "Native chunk reload lost projector claims"
                        + " sameInstance=" + (restored == f.source) + " state=" + restored.state());
                var restoredField = (ForceFieldBlockEntity) f.level.getBlockEntity(target);
                helper.assertTrue(restoredField != originalField && f.identity.equals(restoredField.identity()), "Native field did not reload its exact identity");
                assertSealed(helper, f, target);
                helper.assertTrue(ForceFieldService.validate(f.level.getServer(), f.data,
                        new FieldCleanupSavedData.Observation(f.identity, target)), "Reload did not reconcile an exact field");
                helper.succeed();
            } finally {
                if (!handedOff) {
                    MinecraftForge.EVENT_BUS.unregister(witness);
                    f.level.getChunkSource().removeRegionTicket(FIXTURE_TICKET, targetChunk, 0, targetChunk);
                    for (int i = 1; i <= 32; i++) { f.load(f.identity.target(i)); }
                    f.close();
                    ForceFieldConfig.ENABLED.set(previous);
                }
            }
        });
    }
    private static void assertSealed(GameTestHelper helper, ForceFieldGameTests.Fixture f, net.minecraft.core.BlockPos target) {
        var state = f.level.getBlockState(target);
        helper.assertTrue(Block.isShapeFullBlock(state.getCollisionShape(f.level, target)), "Unload changed collision");
        var view = new ServerLevelVolumeWorldView(f.level, false);
        helper.assertTrue(view.observe(new VolumePosition(target.getX(), target.getY(), target.getZ())) == CellObservation.SEALED,
                "Unload made the physical field permeable");
    }
    private ForceFieldUnloadGameTests() { }
}
