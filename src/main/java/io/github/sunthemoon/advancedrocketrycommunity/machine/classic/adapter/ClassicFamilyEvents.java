package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;

/** Event-only bridge; construction and invalidation do not admit world owners. */
public final class ClassicFamilyEvents {
    private static final int MAX_PLACE_SNAPSHOTS = 4_096;
    private final MultiblockPatternCatalogManager patterns;

    public ClassicFamilyEvents(MultiblockPatternCatalogManager patterns) {
        this.patterns = Objects.requireNonNull(patterns, "patterns");
    }

    public void registerCapabilities(RegisterCapabilitiesEvent event) { ClassicLevelServices.register(event); }
    public void attachLevel(AttachCapabilitiesEvent<Level> event) { ClassicLevelServices.attach(event, patterns); }

    public void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            for (ServerLevel level : event.getServer().getAllLevels()) { ClassicLevelServices.tick(level); }
        }
    }

    public void blockBroken(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && event.getLevel() instanceof ServerLevel level) { mark(level, event.getPos()); }
    }

    public void blockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) { return; }
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            var snapshots = multiPlace.getReplacedBlockSnapshots();
            int count = snapshots.size();
            if (count > MAX_PLACE_SNAPSHOTS) {
                ClassicLevelServices.find(level).ifPresent(service -> service.patternsReloaded(level));
                return;
            }
            for (int i = 0; i < count; i++) {
                BlockSnapshot snapshot = snapshots.get(i);
                if (snapshot.getLevel() == level) { mark(level, snapshot.getPos()); }
            }
        } else { mark(level, event.getPos()); }
    }

    public void neighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (event.isCanceled() || !(event.getLevel() instanceof ServerLevel level)) { return; }
        mark(level, event.getPos());
        for (Direction direction : Direction.values()) {
            if (event.getNotifiedSides().contains(direction)) { mark(level, event.getPos().relative(direction)); }
        }
    }

    public void datapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) {
            for (ServerLevel level : event.getPlayerList().getServer().getAllLevels()) {
                ClassicLevelServices.find(level).ifPresent(service -> service.tagsOrRecipesReloaded(level));
            }
        }
    }

    private static void mark(ServerLevel level, BlockPos position) {
        ClassicLevelServices.find(level).ifPresent(service -> service.blockChanged(level, position));
    }
}
