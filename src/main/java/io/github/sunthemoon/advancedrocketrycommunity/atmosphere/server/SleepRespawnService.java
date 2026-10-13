package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Cell;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.Options;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.RespawnOutcome;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.SleepRefusal;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.SleepRespawnRules.StationAccess;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * ADR-066 section 3.3 adapter for sleep and the two planet-respawn options on a non-Overworld mapped surface and in
 * Space. The Overworld, and Levels that are neither this mod's nor mapped by the catalog, keep their vanilla rules.
 *
 * <ul>
 *   <li>Sleep is refused at an unloaded, unpermitted, PENDING or unbreathable bed cell. Owner decision 05: in Space a
 *       supplied room outside committed stations qualifies; inside one, live VISIT permission is required.</li>
 *   <li>D1 outcome 3: an admitted off-world bed operation keeps the player's saved respawn point; only the native
 *       setter of that same operation is cancelled.</li>
 *   <li>Respawn: {@code PlayerList.respawn} removes the old player from its Level before it reads the saved point.
 *       That removal decides; a refused point is cleared from the discarded player, so vanilla uses its existing
 *       Overworld respawn, and the new player gets the point back. Nothing loads a chunk.</li>
 * </ul>
 *
 * Both transient maps live at most until the end of the server tick; there is no persisted or per-Level state.
 */
public final class SleepRespawnService {
    /** The vanilla bed and anchor stand-up searches read blocks at most this far from the saved block. */
    public static final int ARRIVAL_REACH = 3;
    private static final String SLEEP_PREFIX = "message." + ModIdentity.MOD_ID + ".sleep.";
    private static final String RESPAWN_PREFIX = "message." + ModIdentity.MOD_ID + ".planet_respawn.";
    private static final StationAccessService ACCESS = new StationAccessService();

    private final AtmosphereManager atmosphere;
    private final CelestialCatalogManager catalogs;
    private final Supplier<Options> options;
    private final Map<UUID, BedOperation> bedOperations = new HashMap<>();
    private final Map<UUID, Refusal> refusals = new HashMap<>();

    public SleepRespawnService(AtmosphereManager atmosphere, CelestialCatalogManager catalogs, Supplier<Options> options) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** The COMMON {@code PLANET} options, read on every respawn. */
    public static Options configuredOptions() {
        return new Options(CommonConfig.allowPlanetRespawn(), CommonConfig.forcePlanetRespawn());
    }

    public static String sleepKey(SleepRefusal refusal) {
        return SLEEP_PREFIX + refusal.key();
    }

    public static String respawnKey(RespawnOutcome outcome) {
        return RESPAWN_PREFIX + outcome.key();
    }

    public void register(IEventBus bus) {
        bus.addListener(this::onSleepInBed);
        bus.addListener(this::onSetSpawn);
        bus.addListener(this::onLeaveLevel);
        bus.addListener(this::onRespawn);
        bus.addListener(this::onServerTick);
        bus.addListener(this::onServerStopped);
    }

    /** A non-Overworld mapped surface or Space: the Space Level, any Level of this mod, or a Level the catalog maps. */
    public boolean offWorldHost(ResourceKey<Level> dimension) {
        if (dimension.equals(Level.OVERWORLD)) {
            return false;
        }
        if (dimension.equals(CelestialIds.SPACE_LEVEL) || ModIdentity.MOD_ID.equals(dimension.location().getNamespace())) {
            return true;
        }
        return catalogs.current().map(catalog -> !catalog.candidatesForLevel(dimension).isEmpty()).orElse(false);
    }

    public void onSleepInBed(PlayerSleepInBedEvent event) {
        if (event.getResultStatus() != null || !(event.getEntity() instanceof ServerPlayer player)
                || event.getOptionalPos().isEmpty()) {
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.getServer().isSameThread() || !offWorldHost(level.dimension())) {
            return;
        }
        BlockPos head = event.getOptionalPos().orElseThrow().immutable();
        List<Cell> cells = new ArrayList<>(2);
        for (BlockPos position : occupied(level, head)) {
            cells.add(cell(level, player, position));
        }
        SleepRefusal refusal = SleepRespawnRules.admitSleep(cells);
        if (refusal == SleepRefusal.NONE) {
            bedOperations.put(player.getUUID(), new BedOperation(level.dimension(), head));
            return;
        }
        event.setResult(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        player.displayClientMessage(Component.translatable(sleepKey(refusal)), true);
    }

    /** D1 outcome 3: the native setter of an admitted off-world bed operation, and nothing else, is cancelled. */
    public void onSetSpawn(PlayerSetSpawnEvent event) {
        if (bedOperations.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BedOperation operation = bedOperations.get(player.getUUID());
        if (operation != null && !event.isForced() && operation.level().equals(event.getSpawnLevel())
                && operation.head().equals(event.getNewSpawn())) {
            bedOperations.remove(player.getUUID());
            event.setCanceled(true);
        }
    }

    public void onLeaveLevel(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.getRemovalReason() != Entity.RemovalReason.DISCARDED
                || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        PlayerList players = server.getPlayerList();
        // PlayerList.respawn removes the old player from the player list, then discards it from its Level, and only
        // then reads its saved point. Logout and dimension changes use other removal reasons.
        if (!server.isSameThread() || players.getPlayer(player.getUUID()) != player
                || players.getPlayers().contains(player)) {
            return;
        }
        beforeRespawn(server, player);
    }

    /** The decision for a player about to respawn; a refused off-world point is set aside for this respawn only. */
    void beforeRespawn(MinecraftServer server, ServerPlayer player) {
        BlockPos position = player.getRespawnPosition();
        ResourceKey<Level> dimension = player.getRespawnDimension();
        if (position == null || !offWorldHost(dimension)) {
            return;
        }
        SavedPoint saved = new SavedPoint(dimension, position.immutable(), player.getRespawnAngle(),
                player.isRespawnForced());
        RespawnOutcome outcome = SleepRespawnRules.decideRespawn(options.get(), new Probe(server, player, saved));
        if (outcome.accepted()) {
            return;
        }
        player.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
        if (player.getRespawnPosition() != null) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_PLANET_RESPAWN_NOT_CLEARED player={} level={} outcome={}; "
                    + "another listener kept the saved point, so the native respawn continues",
                    player.getUUID(), dimension.location(), outcome);
            return;
        }
        refusals.put(player.getUUID(), new Refusal(saved, outcome));
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (refusals.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Refusal refusal = refusals.remove(player.getUUID());
        if (refusal == null) {
            return;
        }
        // The point is kept: the options and checks decline it for this respawn only.
        if (player.getRespawnPosition() == null) {
            SavedPoint saved = refusal.saved();
            player.setRespawnPosition(saved.dimension(), saved.position(), saved.angle(), saved.forced(), false);
        }
        player.sendSystemMessage(Component.translatable(respawnKey(refusal.outcome())));
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            clear();
        }
    }

    public void onServerStopped(ServerStoppedEvent event) {
        clear();
    }

    public void clear() {
        bedOperations.clear();
        refusals.clear();
    }

    /** The bed head and, for a vanilla bed, its foot; one cell for any other sleeping place. */
    private static List<BlockPos> occupied(ServerLevel level, BlockPos head) {
        if (!level.isOutsideBuildHeight(head) && level.hasChunkAt(head)) {
            BlockState state = level.getBlockState(head);
            if (state.getBlock() instanceof BedBlock) {
                return List.of(head, head.relative(state.getValue(BedBlock.FACING).getOpposite()));
            }
        }
        return List.of(head);
    }

    private Cell cell(ServerLevel level, ServerPlayer player, BlockPos position) {
        if (level.isOutsideBuildHeight(position) || !level.hasChunkAt(position)) {
            return Cell.unloaded();
        }
        return new Cell(true, atmosphere.breathabilityAt(level, position),
                station(level.getServer(), level.dimension(), player, position));
    }

    private static StationAccess station(MinecraftServer server, ResourceKey<Level> dimension, ServerPlayer player,
                                         BlockPos position) {
        if (!dimension.equals(CelestialIds.SPACE_LEVEL)) {
            return StationAccess.NOT_APPLICABLE;
        }
        StationRegistrySavedData stations = StationRegistrySavedData.get(server);
        if (!stations.operational()) {
            return StationAccess.UNAVAILABLE;
        }
        return stations.findAt(position.getX(), position.getZ())
                .map(station -> ACCESS.allowed(station, player.getUUID(), player.hasPermissions(2),
                        StationAccessAction.VISIT) ? StationAccess.VISIT : StationAccess.DENIED)
                .orElse(StationAccess.OUTSIDE);
    }

    /** Lazy facts for one saved point; each is read only after every earlier fact passed. */
    private final class Probe implements SleepRespawnRules.RespawnProbe {
        private final MinecraftServer server;
        private final ServerPlayer player;
        private final SavedPoint saved;
        private ServerLevel level;
        private BlockState state;
        private Vec3 arrival;

        private Probe(MinecraftServer server, ServerPlayer player, SavedPoint saved) {
            this.server = server;
            this.player = player;
            this.saved = saved;
        }

        @Override
        public boolean bodyPresent() {
            level = server.getLevel(saved.dimension());
            return level != null && catalogs.current()
                    .map(catalog -> catalog.candidatesForLevel(saved.dimension()).size() == 1).orElse(false);
        }

        @Override
        public boolean loaded() {
            BlockPos position = saved.position();
            if (level.isOutsideBuildHeight(position) || !level.getWorldBorder().isWithinBounds(position)) {
                return false;
            }
            for (int x = (position.getX() - ARRIVAL_REACH) >> 4; x <= (position.getX() + ARRIVAL_REACH) >> 4; x++) {
                for (int z = (position.getZ() - ARRIVAL_REACH) >> 4; z <= (position.getZ() + ARRIVAL_REACH) >> 4; z++) {
                    if (level.getChunkSource().getChunkNow(x, z) == null) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** Exactly vanilla's choice: a bed where beds work, or an anchor where anchors work, charged unless forced. */
        @Override
        public boolean spawnBlock() {
            state = level.getBlockState(saved.position());
            if (state.getBlock() instanceof RespawnAnchorBlock) {
                return RespawnAnchorBlock.canSetSpawn(level)
                        && (saved.forced() || state.getValue(RespawnAnchorBlock.CHARGE) > 0);
            }
            return state.getBlock() instanceof BedBlock && BedBlock.canSetSpawn(level);
        }

        @Override
        public boolean arrivalFound() {
            Optional<Vec3> found = state.getBlock() instanceof RespawnAnchorBlock
                    ? RespawnAnchorBlock.findStandUpPosition(EntityType.PLAYER, level, saved.position())
                    : BedBlock.findStandUpPosition(EntityType.PLAYER, level, saved.position(),
                            state.getValue(BedBlock.FACING), saved.angle());
            arrival = found.orElse(null);
            return arrival != null && Vec3.atCenterOf(saved.position()).closerThan(arrival, ARRIVAL_REACH)
                    && level.hasChunkAt(BlockPos.containing(arrival));
        }

        /** A solid collision, a liquid or a burning block in the standing body, or a burning floor. */
        @Override
        public boolean hazard() {
            AABB body = EntityType.PLAYER.getDimensions().makeBoundingBox(arrival);
            if (!level.noCollision(body)) {
                return true;
            }
            int minX = Mth.floor(body.minX), minY = Mth.floor(body.minY), minZ = Mth.floor(body.minZ);
            int maxX = Mth.floor(body.maxX), maxY = Mth.floor(body.maxY), maxZ = Mth.floor(body.maxZ);
            for (BlockPos cell : BlockPos.betweenClosed(minX, minY - 1, minZ, maxX, maxY, maxZ)) {
                BlockState inside = level.getBlockState(cell);
                if (inside.isBurning(level, cell) || cell.getY() >= minY && !inside.getFluidState().isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public StationAccess station() {
            return SleepRespawnRules.worse(SleepRespawnService.station(server, saved.dimension(), player, saved.position()),
                    SleepRespawnService.station(server, saved.dimension(), player, BlockPos.containing(arrival)));
        }

        @Override
        public BreathabilityState air() {
            BlockPos feet = BlockPos.containing(arrival);
            BlockPos eye = BlockPos.containing(arrival.x, arrival.y + player.getEyeHeight(Pose.STANDING), arrival.z);
            return SleepRespawnRules.worse(atmosphere.breathabilityAt(level, feet), atmosphere.breathabilityAt(level, eye));
        }
    }

    private record BedOperation(ResourceKey<Level> level, BlockPos head) {
    }

    private record SavedPoint(ResourceKey<Level> dimension, BlockPos position, float angle, boolean forced) {
    }

    private record Refusal(SavedPoint saved, RespawnOutcome outcome) {
    }
}
