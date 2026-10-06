package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import java.util.Objects;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.eventbus.api.Event;

/** Forge event adapter for loaded-world invalidation and bounded server ticks. */
public final class AtmosphereServerEvents {
    private final AtmosphereManager atmosphere;
    private final Predicate<ItemStack> nonMutatingItem;

    public AtmosphereServerEvents(AtmosphereManager atmosphere) {
        this(atmosphere, stack -> false);
    }

    public AtmosphereServerEvents(AtmosphereManager atmosphere, Predicate<ItemStack> nonMutatingItem) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.nonMutatingItem = Objects.requireNonNull(nonMutatingItem, "nonMutatingItem");
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            atmosphere.tick(event.getServer());
        }
    }

    public void onBlockBroken(BlockEvent.BreakEvent event) {
        mark(event.getLevel(), event.getPos());
    }

    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                mark(snapshot.getLevel(), snapshot.getPos());
            }
            return;
        }
        mark(event.getLevel(), event.getPos());
    }

    public void onFluidPlaced(BlockEvent.FluidPlaceBlockEvent event) {
        mark(event.getLevel(), event.getPos());
    }

    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // ADR-067: only item-only measurement skips preemptive interaction invalidation.
        if (nonMutatingItem.test(event.getItemStack()) && event.getUseBlock() == Event.Result.DENY) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (!level.hasChunkAt(event.getPos())) {
            return;
        }
        BlockState state = level.getBlockState(event.getPos());
        if (isDoorLike(state) || atmosphere.isRegisteredBoundary(state)) {
            atmosphere.markDirty(level, event.getPos());
        }
    }

    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        // The old registered block can already have been replaced with air.
        if (atmosphere.hasBoundaryProviders() || isDoorLike(event.getState())) {
            atmosphere.markDirty(level, event.getPos());
        }
        for (net.minecraft.core.Direction direction : event.getNotifiedSides()) {
            BlockPos neighbor = event.getPos().relative(direction);
            if (level.hasChunkAt(neighbor) && isDoorLike(level.getBlockState(neighbor))) {
                atmosphere.markDirty(level, neighbor);
            }
        }
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            atmosphere.onChunkLoad(
                    level,
                    event.getChunk().getPos().x,
                    event.getChunk().getPos().z
            );
        }
    }

    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            event.getPlayerList().getServer().execute(atmosphere::invalidateBoundaries);
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            atmosphere.onChunkUnload(
                    level,
                    event.getChunk().getPos().x,
                    event.getChunk().getPos().z
            );
        }
    }

    private void mark(net.minecraft.world.level.LevelAccessor accessor, BlockPos position) {
        if (accessor instanceof ServerLevel level) {
            atmosphere.markDirty(level, position);
        }
    }

    private static boolean isDoorLike(BlockState state) {
        return state.getBlock() instanceof DoorBlock
                || state.getBlock() instanceof TrapDoorBlock
                || state.getBlock() instanceof FenceGateBlock;
    }
}
