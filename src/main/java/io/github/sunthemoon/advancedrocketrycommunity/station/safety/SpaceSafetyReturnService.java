package io.github.sunthemoon.advancedrocketrycommunity.station.safety;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.SafeCelestialTravel;
import io.github.sunthemoon.advancedrocketrycommunity.station.orbit.StationOrbitEnvironmentResolver;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.LogicalSide;

/**
 * ADR-066 section 3.3, the space safety return, on the logical server thread. A player tick keeps the
 * {@link SafetyReturnPhase} of each adrift player and admits a {@link SafetyReturnQuery} job; each server END tick
 * serves the jobs within their budgets, then commits each finished one: it revalidates the player, runs the
 * protection check (world border, build height and the public {@code TELEPORT} event, as the elevator ride commit
 * does) and moves the player with {@link SafeCelestialTravel}. Destinations and refusals are announced in translated
 * chat messages, and every outcome writes one INFO audit line. Runtime state only, cleared with the server.
 */
public final class SpaceSafetyReturnService {
    /** The system ID of this service's {@code TELEPORT} events. */
    public static final ResourceLocation SYSTEM_ID = ModIdentity.id("space_safety_return");
    private static final String PREFIX = "message." + ModIdentity.MOD_ID + ".space_safety_return.";
    public static final String ARRIVED_STATION = PREFIX + "station";
    public static final String ARRIVED_OVERWORLD = PREFIX + "overworld";
    public static final String REFUSED = PREFIX + "refused";
    private static final long PRUNE_TICKS = 200L;

    /** Why an attempt moved nobody; each has its own translated reason. */
    public enum Refusal {
        /** The station registry is blocked or its outcome unknown, or the celestial catalog is missing. */
        AUTHORITY_UNAVAILABLE,
        /** The arrival is outside the world, or a listener cancelled the {@code TELEPORT} event. */
        PROTECTED,
        /** The teleport did not arrive, for example because another mod cancelled the Level change. */
        CANCELLED;

        public String key() {
            return PREFIX + "reason." + name().toLowerCase(Locale.ROOT);
        }
    }

    /** True while a rocket transfer or an elevator ride holds the player. */
    @FunctionalInterface
    public interface Transactions {
        boolean holds(MinecraftServer server, UUID player);
    }

    private final CelestialCatalogManager catalogs;
    private final Transactions transactions;
    private final SafeCelestialTravel travel = new SafeCelestialTravel();
    private final StationAccessService access = new StationAccessService();
    private final SafetyReturnQuery query = new SafetyReturnQuery();
    private final Map<UUID, SafetyReturnPhase> phases = new HashMap<>();
    private final Set<UUID> heldForTest = new HashSet<>();

    public SpaceSafetyReturnService(CelestialCatalogManager catalogs, Transactions transactions) {
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    public void onServerAboutToStart(ServerAboutToStartEvent event) {
        clear();
        SpaceSafetyReturnRuntime.install(this);
    }

    public void onServerStopped(ServerStoppedEvent event) {
        clear();
        SpaceSafetyReturnRuntime.clear();
    }

    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.side == LogicalSide.SERVER
                && event.player instanceof ServerPlayer player) {
            tickPlayer(player);
        }
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            tickServer(event.getServer());
        }
    }

    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID player = event.getEntity().getUUID();
        phases.remove(player);
        query.cancel(player);
    }

    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        end(event.getEntity().getUUID());
    }

    /** One player tick: the phase, then at most one admitted job. */
    public void tickPlayer(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null || !server.isSameThread()) {
            return;
        }
        UUID id = player.getUUID();
        if (!adrift(server, player)) {
            end(id);
            return;
        }
        SafetyReturnPhase phase = phases.computeIfAbsent(id, ignored -> new SafetyReturnPhase());
        long now = server.getTickCount();
        if (!phase.tick(true, now) || query.pending(id)) {
            return;
        }
        if (inTransaction(server, id)) {
            phase.clear();
            return;
        }
        if (query.admit(id, player.getX(), player.getZ())) {
            phase.attempted(now);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_SAFETY_RETURN_QUERY player={} x={} z={} jobs={}", id,
                    player.getBlockX(), player.getBlockZ(), query.size());
        }
    }

    /** One server END tick: the bounded query, then each finished job's commit. */
    public void tickServer(MinecraftServer server) {
        if (server.getTickCount() % PRUNE_TICKS == 0L) {
            // Logout clears a phase; this only drops one whose logout event never came.
            phases.keySet().removeIf(id -> server.getPlayerList().getPlayer(id) == null);
        }
        if (query.size() == 0) {
            return;
        }
        List<SafetyReturnQuery.Result> results;
        try {
            results = query.tick(new LiveWorld(server));
        } catch (RuntimeException failure) {
            // Never let the safety return stop the server tick; the players' next attempts start a fresh query.
            AdvancedRocketryCommunity.LOGGER.error("ARCE_SAFETY_RETURN_FAILED stage=query jobs={}", query.size(), failure);
            query.clear();
            return;
        }
        for (SafetyReturnQuery.Result result : results) {
            ServerPlayer player = server.getPlayerList().getPlayer(result.player());
            if (player == null) {
                continue;
            }
            try {
                commit(server, player, result);
            } catch (RuntimeException failure) {
                end(result.player());
                AdvancedRocketryCommunity.LOGGER.error("ARCE_SAFETY_RETURN_FAILED stage=commit player={}",
                        result.player(), failure);
            }
        }
    }

    public boolean pending(UUID player) {
        return query.pending(player);
    }

    public int jobs() {
        return query.size();
    }

    public SafetyReturnQuery.TickUse lastTick() {
        return query.lastTick();
    }

    public int adriftTicks(UUID player) {
        SafetyReturnPhase phase = phases.get(player);
        return phase == null ? 0 : phase.adriftTicks();
    }

    /** GameTest seam: marks a player as held by a transaction, as a rocket transfer or an elevator ride would. */
    public void holdForTest(UUID player, boolean held) {
        if (held) {
            heldForTest.add(player);
        } else {
            heldForTest.remove(player);
        }
    }

    public void clear() {
        phases.clear();
        query.clear();
        heldForTest.clear();
    }

    /**
     * Adrift: an actual connected player, alive and not a spectator, in the Space Level, neither riding nor carrying,
     * and outside every committed region of an operational registry. A blocked registry hides the regions, so nobody
     * is adrift then.
     */
    private static boolean adrift(MinecraftServer server, ServerPlayer player) {
        if (player instanceof FakePlayer || player.connection == null || player.hasDisconnected()
                || server.getPlayerList().getPlayer(player.getUUID()) != player) {
            return false;
        }
        if (!player.isAlive() || player.isRemoved() || player.isSpectator()
                || !player.level().dimension().equals(CelestialIds.SPACE_LEVEL)
                || player.isPassenger() || player.isVehicle()) {
            return false;
        }
        StationRegistrySavedData registry = StationRegistrySavedData.get(server);
        return registry.operational() && registry.findAt(player.getBlockX(), player.getBlockZ()).isEmpty();
    }

    private boolean inTransaction(MinecraftServer server, UUID player) {
        return heldForTest.contains(player) || transactions.holds(server, player);
    }

    private void end(UUID player) {
        SafetyReturnPhase phase = phases.get(player);
        if (phase != null) {
            phase.clear();
        }
        query.cancel(player);
    }

    private void commit(MinecraftServer server, ServerPlayer player, SafetyReturnQuery.Result result) {
        UUID id = player.getUUID();
        // The query revalidated the station; the player must still be adrift in the Space Level, outside any ride.
        if (!adrift(server, player) || inTransaction(server, id)) {
            end(id);
            AdvancedRocketryCommunity.LOGGER.info("ARCE_SAFETY_RETURN player={} outcome=dropped reason=not_adrift", id);
            return;
        }
        if (result instanceof SafetyReturnQuery.Refused) {
            refuse(player, Refusal.AUTHORITY_UNAVAILABLE, "destination=none");
        } else if (result instanceof SafetyReturnQuery.Station station) {
            ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
            SafetyReturnQuery.Target target = station.target();
            String name = StationRegistrySavedData.get(server).find(target.stationId())
                    .map(state -> state.name()).orElse(target.stationId().toString());
            move(player, space, new SafeCelestialTravel.Destination(target.x() + 0.5D, target.y(), target.z() + 0.5D),
                    Component.translatable(ARRIVED_STATION, name),
                    "destination=station station=" + target.stationId() + " passes=" + station.passes());
        } else if (result instanceof SafetyReturnQuery.Overworld overworld) {
            ServerLevel level = server.overworld();
            move(player, level, travel.prepare(level, CelestialIds.EARTH_ID), Component.translatable(ARRIVED_OVERWORLD),
                    "destination=overworld passes=" + overworld.passes());
        }
    }

    private void move(ServerPlayer player, ServerLevel level, SafeCelestialTravel.Destination destination,
                      Component announcement, String audit) {
        BlockPos feet = BlockPos.containing(destination.x(), destination.y(), destination.z());
        if (!permitted(level, player, feet)) {
            refuse(player, Refusal.PROTECTED, audit);
            return;
        }
        travel.teleport(player, level, destination);
        boolean arrived = player.level() == level
                && player.position().distanceToSqr(destination.x(), destination.y(), destination.z()) < 1.0D;
        if (!arrived) {
            refuse(player, Refusal.CANCELLED, audit);
            return;
        }
        end(player.getUUID());
        player.sendSystemMessage(announcement);
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SAFETY_RETURN player={} outcome=returned {} level={} at={}",
                player.getUUID(), audit, level.dimension().location(), feet.toShortString());
    }

    /**
     * ADR-054 section 5 steps 2 and 6 for the arrival column (floor to head), as ADR-059 section 8 runs them for an
     * elevator ride: the world border and build height, then the public cancellable {@code TELEPORT} event with this
     * service's system ID and the player as owner and actor. Endgame zones are not consulted: ADR-054 section 6 limits
     * them to endgame effects. Spawn protection does not apply because no block changes.
     */
    private static boolean permitted(ServerLevel level, ServerPlayer player, BlockPos feet) {
        BlockPos min = feet.below();
        BlockPos max = feet.above();
        WorldBorder border = level.getWorldBorder();
        if (!border.isWithinBounds(min) || !border.isWithinBounds(max)
                || min.getY() < level.getMinBuildHeight() || max.getY() >= level.getMaxBuildHeight()) {
            return false;
        }
        EndgameEffectEvent event = new EndgameEffectEvent(SYSTEM_ID, EndgameEffect.TELEPORT, player.getUUID(),
                Optional.of(player.getUUID()), level.dimension(), min, max);
        return !MinecraftForge.EVENT_BUS.post(event);
    }

    private void refuse(ServerPlayer player, Refusal reason, String audit) {
        player.sendSystemMessage(Component.translatable(REFUSED, Component.translatable(reason.key())));
        AdvancedRocketryCommunity.LOGGER.info("ARCE_SAFETY_RETURN player={} outcome=refused reason={} {}",
                player.getUUID(), reason.name().toLowerCase(Locale.ROOT), audit);
    }

    /** The query's view of the live server: reads only, never loads a chunk. */
    private final class LiveWorld implements SafetyReturnQuery.World {
        private final MinecraftServer server;
        private final StationRegistrySavedData registry;
        private final ServerLevel space;
        private final Optional<CelestialCatalog> catalog;

        private LiveWorld(MinecraftServer server) {
            this.server = server;
            registry = StationRegistrySavedData.get(server);
            space = server.getLevel(CelestialIds.SPACE_LEVEL);
            catalog = catalogs.current();
        }

        @Override
        public boolean authorityAvailable() {
            return registry.updatesAvailable() && space != null && catalog.isPresent();
        }

        @Override
        public long generation() {
            return registry.accessGeneration().orElse(-1L);
        }

        @Override
        public boolean operator(UUID player) {
            ServerPlayer online = server.getPlayerList().getPlayer(player);
            return online != null && online.hasPermissions(2);
        }

        @Override
        public Optional<UUID> next(UUID player, boolean operator, UUID after) {
            return registry.nextAccessCandidate(player, operator, after);
        }

        @Override
        public Optional<SafetyReturnQuery.Target> inspect(UUID player, boolean operator, UUID station) {
            return registry.find(station)
                    .filter(state -> access.allowed(state, player, operator, StationAccessAction.VISIT))
                    .filter(state -> StationOrbitEnvironmentResolver.resolve(state, catalog).orbitBodyAvailable())
                    .map(state -> new SafetyReturnQuery.Target(state.stationId(), state.landingPad().x(),
                            state.landingPad().y(), state.landingPad().z()));
        }

        @Override
        public boolean probe(SafetyReturnQuery.Target target, int step) {
            return SafetyReturnArrival.probe(space, target, step);
        }
    }
}
