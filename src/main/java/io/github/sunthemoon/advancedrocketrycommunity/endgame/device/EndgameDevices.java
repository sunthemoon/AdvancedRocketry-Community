package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameRateLimiter;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTables;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Server-thread runtime of loaded endgame devices (ADR-054 sections 2.1, 4 and 7): active-device admission, the
 * per-tick work caps served in ID order, the structure validation budget and box index, and per-player intent
 * spacing. Nothing here is persisted; it is cleared when the server stops.
 */
public final class EndgameDevices {
    private final Supplier<EndgameSettings> settings;
    private final Supplier<LaserDrillSettings> laserSettings;
    private final MultiblockPatternCatalogManager patterns;
    private final CelestialCatalogManager celestial;
    private final LaserDrillTableReloadListener.Manager laserTables;
    private final EndgameActiveDevices active = new EndgameActiveDevices();
    private final RoundRobinBudget laserOperations = new RoundRobinBudget();
    private final RoundRobinBudget laserLayers = new RoundRobinBudget();
    private final RoundRobinBudget structureBudget = new RoundRobinBudget();
    private final EndgameStructureTracker structures = new EndgameStructureTracker();
    private final EndgameRateLimiter rates = new EndgameRateLimiter();
    private int laserOperationsLastTick;
    private int laserLayersLastTick;

    public EndgameDevices(Supplier<EndgameSettings> settings, Supplier<LaserDrillSettings> laserSettings,
                          MultiblockPatternCatalogManager patterns, CelestialCatalogManager celestial,
                          LaserDrillTableReloadListener.Manager laserTables) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.laserSettings = Objects.requireNonNull(laserSettings, "laserSettings");
        this.patterns = Objects.requireNonNull(patterns, "patterns");
        this.celestial = Objects.requireNonNull(celestial, "celestial");
        this.laserTables = Objects.requireNonNull(laserTables, "laserTables");
    }

    public EndgameSettings settings() {
        return settings.get();
    }

    public LaserDrillSettings laserSettings() {
        return laserSettings.get();
    }

    public Optional<MultiblockPatternDefinition> pattern(String id) {
        return patterns.current().flatMap(catalog -> catalog.get(id));
    }

    public Optional<CelestialCatalog> celestial() {
        return celestial.current();
    }

    public LaserDrillTables laserTables() {
        return laserTables.current().orElse(LaserDrillTables.EMPTY);
    }

    public EndgameActiveDevices active() {
        return active;
    }

    public RoundRobinBudget laserOperations() {
        return laserOperations;
    }

    /** ADR-055 section 4: physical layers per server tick (at most 7, 63 cells). */
    public RoundRobinBudget laserLayers() {
        return laserLayers;
    }

    public RoundRobinBudget structureBudget() {
        return structureBudget;
    }

    public EndgameStructureTracker structures() {
        return structures;
    }

    public EndgameRateLimiter rates() {
        return rates;
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        laserOperationsLastTick = laserOperations.servedLastTick();
        laserLayersLastTick = laserLayers.servedLastTick();
        laserOperations.resetCounters();
        laserLayers.resetCounters();
        structureBudget.resetCounters();
        laserOperations.endTick(laserSettings.get().logicalOperationsPerTick());
        laserLayers.endTick(laserSettings.get().layersPerTick());
        structureBudget.endTick(EndgameLimits.STRUCTURE_VALIDATIONS_PER_TICK);
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        rates.forget(event.getEntity().getUUID());
    }

    public void onServerStopped(ServerStoppedEvent event) {
        clear();
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

    /** One status line for {@code /arce endgame status}: active devices and last-tick work. */
    public String status() {
        return "laser_drill active=" + active.count(EndgameSystem.LASER_DRILL) + " operations_last_tick="
                + laserOperationsLastTick + " waiting=" + laserOperations.waitingLastTick() + " layers_last_tick="
                + laserLayersLastTick + " layers_waiting=" + laserLayers.waitingLastTick() + "; structures tracked="
                + structures.tracked() + " validations_waiting=" + structureBudget.waitingLastTick() + "; intent_players="
                + rates.size();
    }

    public void clear() {
        active.clear();
        laserOperations.clear();
        laserLayers.clear();
        structureBudget.clear();
        structures.clear();
        rates.clear();
        laserOperationsLastTick = 0;
        laserLayersLastTick = 0;
    }

    private void changed(net.minecraft.world.level.LevelAccessor accessor, BlockPos position) {
        if (accessor instanceof ServerLevel level && structures.tracked() > 0) {
            structures.changed(level.dimension(), position);
        }
    }
}
