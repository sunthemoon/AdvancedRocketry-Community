package io.github.sunthemoon.advancedrocketrycommunity.beacon;

import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStations;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameStructureTracker;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.RoundRobinBudget;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.forge.PatternBlockCache;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * The beacon slice's server services (ADR-065 section 7): the planetary beacon pattern of the current pattern catalog,
 * the loaded controllers' pattern boxes for change-driven revalidation, the shared budget of structure validations per
 * tick, body resolution over the current celestial catalog and the finder's marker stream. Everything here is runtime
 * state of one server and is cleared when it stops; the index itself is {@code BeaconIndexSavedData}.
 */
public final class BeaconServices {
    public static final String PATTERN_ID = "advancedrocketrycommunity:planetary_beacon";
    /** Validations granted per server tick to all loaded beacons together. */
    public static final int VALIDATIONS_PER_TICK = 8;
    /** A formed or unformed beacon validates again at least this often. */
    public static final int REVALIDATION_TICKS = 200;

    private final MultiblockPatternCatalogManager patterns;
    private final CelestialCatalogManager celestial;
    private final EndgameStructureTracker structures = new EndgameStructureTracker();
    private final RoundRobinBudget validations = new RoundRobinBudget();
    private final PatternBlockCache patternBlocks = new PatternBlockCache();
    private final BeaconFinderService finder;
    @Nullable
    private MultiblockPatternCatalog patternCatalog;
    private Optional<MultiblockPatternDefinition> pattern = Optional.empty();

    public BeaconServices(MultiblockPatternCatalogManager patterns, CelestialCatalogManager celestial,
                          BeaconFinderService.Sender sender) {
        this.patterns = Objects.requireNonNull(patterns, "patterns");
        this.celestial = Objects.requireNonNull(celestial, "celestial");
        this.finder = new BeaconFinderService(sender, this);
    }

    /** The beacon pattern of the current catalog; empty while no valid catalog defines it. */
    public Optional<MultiblockPatternDefinition> pattern() {
        Optional<MultiblockPatternCatalog> current = patterns.current();
        if (current.isEmpty()) {
            patternCatalog = null;
            pattern = Optional.empty();
            return pattern;
        }
        if (current.get() != patternCatalog) {
            patternCatalog = current.get();
            pattern = patternCatalog.get(PATTERN_ID);
            patternBlocks.clear();
        }
        return pattern;
    }

    /** The server-resolved body of a position, or empty while the catalog or the body is unavailable. */
    public Optional<String> body(MinecraftServer server, ResourceKey<Level> level, BlockPos position) {
        return celestial.current().flatMap(catalog -> EndgameStations.body(server, celestial, catalog,
                level.location(), position.asLong())).map(body -> body.body().toString());
    }

    public EndgameStructureTracker structures() {
        return structures;
    }

    public RoundRobinBudget validations() {
        return validations;
    }

    public PatternBlockCache patternBlocks() {
        return patternBlocks;
    }

    public BeaconFinderService finder() {
        return finder;
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        validations.resetCounters();
        validations.endTick(VALIDATIONS_PER_TICK);
        finder.tick(event.getServer(), event.getServer().getTickCount());
    }

    public void onBlockBroken(BlockEvent.BreakEvent event) {
        changed(event.getLevel(), event.getPos());
    }

    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event instanceof BlockEvent.EntityMultiPlaceEvent multiPlace) {
            for (BlockSnapshot snapshot : multiPlace.getReplacedBlockSnapshots()) {
                changed(snapshot.getLevel(), snapshot.getPos());
            }
            return;
        }
        changed(event.getLevel(), event.getPos());
    }

    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || structures.tracked() == 0) {
            return;
        }
        structures.changed(level.dimension(), event.getPos());
        for (Direction direction : event.getNotifiedSides()) {
            structures.changed(level.dimension(), event.getPos().relative(direction));
        }
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        finder.forget(event.getEntity().getUUID());
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        finder.clientCleared(event.getEntity().getUUID());
    }

    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        finder.clientCleared(event.getEntity().getUUID());
    }

    /** A data reload can change the catalog: the next update resends whatever the finder shows. */
    public void onDatapackSync(OnDatapackSyncEvent event) {
        ServerPlayer player = event.getPlayer();
        if (player == null) {
            finder.reloaded();
        } else {
            finder.clientCleared(player.getUUID());
        }
    }

    public void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    public void clear() {
        structures.clear();
        validations.clear();
        patternBlocks.clear();
        finder.clear();
        patternCatalog = null;
        pattern = Optional.empty();
    }

    private void changed(LevelAccessor accessor, BlockPos position) {
        if (accessor instanceof ServerLevel level && structures.tracked() > 0) {
            structures.changed(level.dimension(), position);
        }
    }
}
