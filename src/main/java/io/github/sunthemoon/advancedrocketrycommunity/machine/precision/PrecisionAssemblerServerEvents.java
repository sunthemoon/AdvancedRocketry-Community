package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;

/** Forge events feed the bounded Precision Assembler dirty queue. */
public final class PrecisionAssemblerServerEvents {
    private final PrecisionAssemblerManager machines;

    public PrecisionAssemblerServerEvents(PrecisionAssemblerManager machines) {
        this.machines = Objects.requireNonNull(machines, "machines");
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            machines.tick(event.getServer());
        }
    }

    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (PrecisionAssemblerRemovalPolicy.blocksRemoval(event.getLevel(), event.getPos())) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.translatable(
                    "message.advancedrocketrycommunity.precision_assembler.removal_blocked"), true);
            return;
        }
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

    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        machines.markDirty(level, event.getPos());
        for (net.minecraft.core.Direction direction : event.getNotifiedSides()) {
            BlockPos neighbor = event.getPos().relative(direction);
            if (level.hasChunkAt(neighbor)) {
                machines.markDirty(level, neighbor);
            }
        }
    }

    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            machines.onChunkChanged(level, event.getChunk().getPos().x, event.getChunk().getPos().z);
        }
    }

    public void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            machines.onChunkUnloading(level, event.getChunk().getPos().x, event.getChunk().getPos().z);
        }
    }

    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        machines.clear();
        PrecisionAssemblerRuntime.install(machines);
    }

    public void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            machines.onRecipesReloaded();
        }
    }

    private void mark(net.minecraft.world.level.LevelAccessor accessor, BlockPos position) {
        if (accessor instanceof ServerLevel level) {
            machines.markDirty(level, position);
        }
    }
}
