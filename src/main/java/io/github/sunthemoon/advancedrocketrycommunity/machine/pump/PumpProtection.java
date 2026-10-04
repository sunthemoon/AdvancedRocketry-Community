package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.GuardedChunkSaves;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.protection.ForgeProtectionView;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkDataEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Live ordered protection and refused-root removal/save interception. No world-position registry. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class PumpProtection {
    static PumpCode source(ServerLevel level, UUID owner, BlockPos target, BlockState state) {
        if (owner == null) { return PumpCode.NO_OWNER; }
        EndgameSavedData root = EndgameSavedData.get(level.getServer());
        if (!root.operational()) { return PumpCode.REPAIR_REQUIRED; }
        return PumpProtectionChecks.check(owner, level.dimension(), target,
                new ForgeProtectionView(level, root.view().zones()), () -> MinecraftForge.EVENT_BUS.post(
                        new BlockEvent.BreakEvent(level, target, state,
                                FakePlayerFactory.get(level, new GameProfile(owner, "[ARCE Pump]")))));
    }
    static PumpCode recheck(ServerLevel level, UUID owner, BlockPos target) {
        EndgameSavedData root = EndgameSavedData.get(level.getServer());
        return root.operational() ? PumpProtectionChecks.region(owner, level.dimension(), target,
                new ForgeProtectionView(level, root.view().zones())) : PumpCode.REPAIR_REQUIRED;
    }
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent event) {
        if (event.getState().getBlock() instanceof PumpBlock && PumpBlock.protectedData(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
        }
    }
    @SubscribeEvent public static void explosion(ExplosionEvent.Detonate event) {
        event.getAffectedBlocks().removeIf(pos -> event.getLevel().getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) != null
                && event.getLevel().getBlockState(pos).getBlock() instanceof PumpBlock
                && PumpBlock.protectedData(event.getLevel(), pos));
    }
    @SubscribeEvent public static void chunkSave(ChunkDataEvent.Save event) {
        GuardedChunkSaves.beforeSave(event);
        var entities = event.getData().getList("block_entities", Tag.TAG_COMPOUND);
        if (entities.size() > 256 * event.getChunk().getHeight()) {
            GuardedChunkSaves.refuse(event, "Chunk block-entity count exceeds physical bound");
        }
        for (int index = 0; index < entities.size(); index++) {
            var entity = entities.getCompound(index);
            if (entity.getString("id").equals(ModIdentity.id("pump").toString()) && entity.contains(PumpSave.ROOT)
                    && !PumpSave.bounded(entity.get(PumpSave.ROOT))) {
                GuardedChunkSaves.refuse(event, "Refusing oversized pump chunk save; back up and repair first");
            }
        }
    }
    private PumpProtection() { }
}
