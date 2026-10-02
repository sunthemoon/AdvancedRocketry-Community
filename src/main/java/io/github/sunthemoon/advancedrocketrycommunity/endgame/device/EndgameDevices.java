package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleDataReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.blackhole.BlackHoleSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldIndex;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityFieldLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.gravity.GravityTrust;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.intent.EndgameRateLimiter;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTableReloadListener;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.laser.LaserDrillTables;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameLimits;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSettings;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.railgun.RailgunSettings;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

/**
 * Server-thread runtime of loaded endgame devices (ADR-054 sections 2.1, 4 and 7): active-device admission, the
 * per-tick work caps served in ID order, the structure validation budget and box index, and per-player intent
 * spacing. Nothing here is persisted; it is cleared when the server stops.
 */
public final class EndgameDevices {
    private final Supplier<EndgameSettings> settings;
    private final Supplier<LaserDrillSettings> laserSettings;
    private final Supplier<GravityFieldLimits> gravityLimits;
    private final Supplier<BlackHoleSettings> blackHoleSettings;
    private final Supplier<RailgunSettings> railgunSettings;
    private final Supplier<ElevatorSettings> elevatorSettings;
    private final BlackHoleDataReloadListener.Manager blackHoleData;
    private final GravityFieldIndex fields = new GravityFieldIndex();
    private final GravityTrust trust = new GravityTrust();
    private final MultiblockPatternCatalogManager patterns;
    private final CelestialCatalogManager celestial;
    private final LaserDrillTableReloadListener.Manager laserTables;
    private final EndgameActiveDevices active = new EndgameActiveDevices();
    private final RoundRobinBudget laserOperations = new RoundRobinBudget();
    private final RoundRobinBudget laserLayers = new RoundRobinBudget();
    private final RoundRobinBudget structureBudget = new RoundRobinBudget();
    private final RoundRobinBudget railgunLaunches = new RoundRobinBudget();
    private final RoundRobinBudget elevatorLaunches = new RoundRobinBudget();
    private final ElevatorRides elevatorRides = new ElevatorRides();
    private final EndgameStructureTracker structures = new EndgameStructureTracker();
    private final EndgameRateLimiter rates = new EndgameRateLimiter();
    private int laserOperationsLastTick;
    private int laserLayersLastTick;
    private int railgunLaunchesLastTick;

    public EndgameDevices(Supplier<EndgameSettings> settings, Supplier<LaserDrillSettings> laserSettings,
                          Supplier<GravityFieldLimits> gravityLimits, Supplier<BlackHoleSettings> blackHoleSettings,
                          Supplier<RailgunSettings> railgunSettings, Supplier<ElevatorSettings> elevatorSettings,
                          MultiblockPatternCatalogManager patterns,
                          CelestialCatalogManager celestial,
                          LaserDrillTableReloadListener.Manager laserTables,
                          BlackHoleDataReloadListener.Manager blackHoleData) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.laserSettings = Objects.requireNonNull(laserSettings, "laserSettings");
        this.gravityLimits = Objects.requireNonNull(gravityLimits, "gravityLimits");
        this.blackHoleSettings = Objects.requireNonNull(blackHoleSettings, "blackHoleSettings");
        this.railgunSettings = Objects.requireNonNull(railgunSettings, "railgunSettings");
        this.elevatorSettings = Objects.requireNonNull(elevatorSettings, "elevatorSettings");
        this.blackHoleData = Objects.requireNonNull(blackHoleData, "blackHoleData");
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

    public BlackHoleSettings blackHoleSettings() {
        return blackHoleSettings.get();
    }

    public RailgunSettings railgunSettings() {
        return railgunSettings.get();
    }

    /** ADR-056 section 4: launches per server tick (at most 4), granted in railgun ID order, round-robin. */
    public RoundRobinBudget railgunLaunches() {
        return railgunLaunches;
    }

    public ElevatorSettings elevatorSettings() {
        return elevatorSettings.get();
    }

    /** ADR-059 section 6: elevator cargo launches per server tick (at most 4), round-robin in endpoint ID order. */
    public RoundRobinBudget elevatorLaunches() {
        return elevatorLaunches;
    }

    /** ADR-059 section 8: pending passenger rides, runtime only. */
    public ElevatorRides elevatorRides() {
        return elevatorRides;
    }

    /** ADR-054 section 9: a position's live body and star system over the current catalog. */
    public Optional<EndgameStations.Body> body(MinecraftServer server, ResourceLocation level, long pos) {
        return celestial.current().flatMap(catalog -> EndgameStations.body(server, celestial, catalog, level, pos));
    }

    /** ADR-057 singularity profiles and fuel tables; empty until the first complete load. */
    public BlackHoleData blackHoleData() {
        return blackHoleData.current().orElse(BlackHoleData.EMPTY);
    }

    public GravityFieldLimits gravityLimits() {
        return gravityLimits.get();
    }

    /** ADR-058 section 4: the runtime field index, never persisted. */
    public GravityFieldIndex fields() {
        return fields;
    }

    public GravityTrust trust() {
        return trust;
    }

    /**
     * ADR-058 section 4: the field layer in front of the station override, for one player in the living-tick hook;
     * one bounded bucket lookup. Empty while the system is disabled.
     */
    public OptionalDouble fieldGravity(ServerPlayer player) {
        if (fields.size() == 0 || !settings.get().enabled(EndgameSystem.GRAVITY_FIELD)) {
            return OptionalDouble.empty();
        }
        long start = System.nanoTime();
        try {
            BlockPos position = player.blockPosition();
            return fields.at(player.level().dimension().location(), position.getX(), position.getY(),
                    position.getZ(), player.getUUID(), trust.trusted(player));
        } finally {
            io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime.timings().add(
                    io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameTimings.Place.FIELD_LOOKUPS,
                    System.nanoTime() - start);
        }
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
        railgunLaunchesLastTick = railgunLaunches.servedLastTick();
        laserOperations.resetCounters();
        laserLayers.resetCounters();
        railgunLaunches.resetCounters();
        elevatorLaunches.resetCounters();
        structureBudget.resetCounters();
        laserOperations.endTick(laserSettings.get().logicalOperationsPerTick());
        if (!settings.get().enabled(EndgameSystem.GRAVITY_FIELD)) {
            // The switch turning off clears the whole index that tick (ADR-058 section 4).
            fields.clear();
        }
        laserLayers.endTick(laserSettings.get().layersPerTick());
        structureBudget.endTick(EndgameLimits.STRUCTURE_VALIDATIONS_PER_TICK);
        railgunLaunches.endTick(railgunSettings.get().launchesPerTick());
        elevatorLaunches.endTick(elevatorSettings.get().launchesPerTick());
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        rates.forget(event.getEntity().getUUID());
        trust.forget(event.getEntity().getUUID());
    }

    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            fields.clearLevel(level.dimension().location());
        }
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
                + structures.tracked() + " validations_waiting=" + structureBudget.waitingLastTick()
                + "; gravity_fields active=" + fields.size() + "; black_hole_generators active="
                + active.count(EndgameSystem.BLACK_HOLE_GENERATOR) + "; railgun launches_last_tick="
                + railgunLaunchesLastTick + " waiting=" + railgunLaunches.waitingLastTick() + "; elevator rides="
                + elevatorRides.size() + " ride_tickets=" + elevatorRides.tickets() + " launches_waiting="
                + elevatorLaunches.waitingLastTick() + "; intent_players="
                + rates.size();
    }

    public void clear() {
        active.clear();
        laserOperations.clear();
        laserLayers.clear();
        structureBudget.clear();
        railgunLaunches.clear();
        elevatorLaunches.clear();
        structures.clear();
        rates.clear();
        fields.clear();
        trust.clear();
        laserOperationsLastTick = 0;
        laserLayersLastTick = 0;
        railgunLaunchesLastTick = 0;
    }

    private void changed(net.minecraft.world.level.LevelAccessor accessor, BlockPos position) {
        if (accessor instanceof ServerLevel level && structures.tracked() > 0) {
            structures.changed(level.dimension(), position);
        }
    }
}
