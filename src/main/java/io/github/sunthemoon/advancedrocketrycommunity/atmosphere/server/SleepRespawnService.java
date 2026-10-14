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
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup.Backup;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.RespawnPointBackup.Point;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
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
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * ADR-066 section 3.3 adapter for sleep and the two planet-respawn options on a non-Overworld mapped surface and in
 * Space. The Overworld, and Levels that are neither this mod's nor mapped by the catalog, keep their vanilla rules.
 *
 * <ul>
 *   <li>Sleep is refused at an unloaded, unpermitted, PENDING or unbreathable bed cell. Owner decision 05: in Space a
 *       supplied room outside committed stations qualifies; inside one, live VISIT permission is required.</li>
 *   <li>The operation-scoped bed spawn setter boundary (D1 outcome 3) is deferred to W37b.</li>
 *   <li>Death respawn and the End exit: the server tick's END decides for each dead player in the player list, even
 *       after KILLED corpse removal and without any player tick, and for each player watching the End credits.
 *       DISCARDED removal remains the final check for immediate respawn and a mod's living-player respawn call.
 *       A refused point is durably set aside so vanilla uses the Overworld, then restored to the replacement.</li>
 *   <li>A newer point always wins: a set-aside point governs only its own restored tuple or the exact cleared tuple,
 *       whoever wrote it. It never overwrites any other tuple.</li>
 * </ul>
 *
 * A set-aside tuple is owned by the player's persisted Forge data, not a tick-local map. No transient state decides
 * which point governs. Transient state limits diagnostics and tracks this service's own copies into an unadopted
 * connection player; it never authorizes replacing an unrelated tuple and is lost on an abnormal stop.
 */
public final class SleepRespawnService {
    /** The vanilla bed and anchor stand-up searches read blocks at most this far from the saved block. */
    public static final int ARRIVAL_REACH = 3;
    private static final String SLEEP_PREFIX = "message." + ModIdentity.MOD_ID + ".sleep.";
    private static final String RESPAWN_PREFIX = "message." + ModIdentity.MOD_ID + ".planet_respawn.";
    private static final StationAccessService ACCESS = new StationAccessService();

    /** F5: the fail-open and kept-verbatim cases, each logged once per player in a server session. */
    private enum Diagnostic {
        UNENCODABLE_TUPLE("unencodable_tuple", "the saved respawn tuple is outside the backup bounds, so its refusal is"
                + " skipped and vanilla reads it", "set a valid respawn point, for example with /spawnpoint"),
        FOREIGN_PARENT("foreign_persisted_parent", "ForgeData.PlayerPersisted is not a compound, so a refused point"
                + " cannot be set aside and vanilla reads it", "with the player offline, back up the player file and"
                + " make ForgeData.PlayerPersisted a compound"),
        UNREADABLE_BACKUP("unreadable_backup", "ForgeData.PlayerPersisted." + RespawnPointBackup.KEY + " is unreadable"
                + " or from a later version and is kept verbatim, so the player respawns without it",
                "with the player offline, back up the player file and copy its tuple to the Spawn fields");

        private final String token;
        private final String effect;
        private final String recovery;

        Diagnostic(String token, String effect, String recovery) {
            this.token = token;
            this.effect = effect;
            this.recovery = recovery;
        }
    }

    private final AtmosphereManager atmosphere;
    private final CelestialCatalogManager catalogs;
    private final Supplier<Options> options;
    /** Players already reported per diagnostic in this server session (server thread). */
    private final Map<Diagnostic, Set<UUID>> reported = new EnumMap<>(Diagnostic.class);
    private final RespawnSaveReconciler saveCopies = new RespawnSaveReconciler();
    /** Original identity through native Clone -> respawn dispatch; expired no later than the next END. */
    private final Map<ServerPlayer, ServerPlayer> cloneOriginals = new IdentityHashMap<>();

    public SleepRespawnService(AtmosphereManager atmosphere, CelestialCatalogManager catalogs, Supplier<Options> options) {
        this.atmosphere = Objects.requireNonNull(atmosphere, "atmosphere");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.options = Objects.requireNonNull(options, "options");
    }

    /** The COMMON {@code PLANET} options, read on every dead tick and final respawn check. */
    public static Options configuredOptions() {
        return new Options(CommonConfig.allowPlanetRespawn(), CommonConfig.forcePlanetRespawn());
    }

    public static String sleepKey(SleepRefusal refusal) {
        return SLEEP_PREFIX + refusal.key();
    }

    public static String respawnKey(RespawnOutcome outcome) {
        return RESPAWN_PREFIX + outcome.key();
    }

    /** For a kept point whose stored reason this version does not know. */
    public static String genericRespawnKey() {
        return RESPAWN_PREFIX + "generic";
    }

    public void register(IEventBus bus) {
        bus.addListener(this::onSleepInBed);
        bus.addListener(this::onLeaveLevel);
        bus.addListener(EventPriority.LOWEST, this::onClone);
        bus.addListener(this::onRespawn);
        bus.addListener(this::onLogin);
        bus.addListener(EventPriority.LOWEST, this::onLogout);
        // After every Level and connection tick, and after the other END listeners (the atmosphere authority's too).
        bus.addListener(EventPriority.LOWEST, this::onServerTick);
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
            return;
        }
        event.setResult(Player.BedSleepingProblem.NOT_POSSIBLE_HERE);
        player.displayClientMessage(Component.translatable(sleepKey(refusal)), true);
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
        Optional<Backup> pending = governing(player);
        BlockPos position = player.getRespawnPosition();
        Point saved = pending.map(Backup::point).orElse(null);
        if (saved == null && position != null) {
            try {
                // A newer native tuple, including one that has just superseded a backup, is a fresh saved point.
                saved = new Point(player.getRespawnDimension(), position, player.getRespawnAngle(), player.isRespawnForced());
            } catch (IllegalArgumentException unencodableNativePoint) {
                // A foreign/native tuple outside our codec's bounds cannot safely be set aside. Keep it and any
                // unreadable backup untouched rather than failing the server tick or silently truncating data.
                report(player, Diagnostic.UNENCODABLE_TUPLE);
                return;
            }
        }
        if (saved == null || !offWorldHost(saved.dimension())) {
            return;
        }
        RespawnOutcome outcome = SleepRespawnRules.decideRespawn(options.get(), new Probe(server, player, saved));
        if (outcome.accepted()) {
            pending.ifPresent(backup -> restore(player, backup, false));
            return;
        }
        CompoundTag persisted = persisted(player, true);
        if (persisted == null) { // A foreign malformed Forge parent is not overwritten.
            report(player, Diagnostic.FOREIGN_PARENT);
            return;
        }
        Backup backup = new Backup(saved, outcome);
        if (!pending.map(backup::equals).orElse(false)) { // F7: an unchanged refusal is not re-encoded each tick.
            RespawnPointBackup.store(persisted, backup);
        }
        clear(player);
    }

    public void onClone(PlayerEvent.Clone event) {
        if (event.getEntity() instanceof ServerPlayer replacement && event.getOriginal() instanceof ServerPlayer original
                && replacement.server.isSameThread()) {
            if (backup(replacement).isPresent()) {
                cloneOriginals.put(replacement, original);
                reconcileOriginal(replacement, original);
            }
        }
    }

    private void reconcileOriginal(ServerPlayer replacement, ServerPlayer original) {
        if (!cleared(original) && backup(replacement).isPresent()) {
            // Native restoreFrom shares PlayerPersisted before Clone, but copies no respawn fields. A replacement's
            // defaults therefore cannot resurrect a backup already superseded on the original at the native read.
            release(replacement);
        }
    }

    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread()) {
            ServerPlayer original = cloneOriginals.remove(player);
            if (original != null) { reconcileOriginal(player, original); }
            restore(player, true);
        }
    }

    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread() && !player.isDeadOrDying()) {
            restore(player, true);
        }
    }

    /** PlayerList.remove posts this before the native save, so a superseded backup is not saved. */
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.server.isSameThread()) {
            ServerPlayer replacement = player.server.getPlayerList().getPlayer(player.getUUID());
            boolean pending = backup(player).isPresent();
            if (replacement != null && replacement != player && saveCopies.reconcile(replacement, pending)) {
                if (pending) { release(player); }
            } else { governing(player); }
            saveCopies.forget(player);
            cloneOriginals.entrySet().removeIf(entry -> entry.getKey() == player || entry.getValue() == player);
        }
    }

    public void onServerStopped(ServerStoppedEvent event) {
        reported.clear();
        saveCopies.clear();
        cloneOriginals.clear();
    }

    /**
     * The per-tick decision, run last in the server tick. A dead player, or one watching the End credits, is decided;
     * any other player's pending recovery is retried (an interrupted respawn can leave the connection on the old
     * player). The End exit removes the player with CHANGED_DIMENSION, so no DISCARDED check follows; the credits
     * packet's respawn(player, true) reads the point this decision left. Until either respawn packet's native read,
     * only the inter-tick task queue (packets, commands, /reload completion) and the config watcher can change what
     * it read.
     */
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            MinecraftServer server = event.getServer();
            saveCopies.prune(server);
            cloneOriginals.clear();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (player instanceof FakePlayer) { continue; }
                saveCopies.reconcile(player, false); // Follow later changes even after the original backup was released.
                if (player.isDeadOrDying() || player.wonGame) { beforeRespawn(server, player); } else { restore(player, true); }
            }
        }
    }

    private Optional<Backup> backup(ServerPlayer player) {
        CompoundTag persisted = persisted(player, false);
        Tag root = persisted == null ? null : persisted.get(RespawnPointBackup.KEY);
        Optional<Backup> decoded = RespawnPointBackup.decode(root);
        if (root != null && decoded.isEmpty()) { report(player, Diagnostic.UNREADABLE_BACKUP); }
        return decoded;
    }

    /** Fail-open stays as designed; the WARN only lets an operator find the player. Bounded: once per player. */
    private void report(ServerPlayer player, Diagnostic diagnostic) {
        if (reported.computeIfAbsent(diagnostic, unused -> new HashSet<>()).add(player.getUUID())) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_PLANET_RESPAWN_DIAGNOSTIC player={} case={}: {}; recovery: {}"
                    + " (ADR-066 planet respawn backup note)", player.getUUID(), diagnostic.token, diagnostic.effect,
                    diagnostic.recovery);
        }
    }

    /**
     * F1, W37I: the pending backup that still governs this player's point. Only a changed native tuple is newer: the
     * backup is then removed without writing a field. The exact cleared tuple governs, whoever wrote it.
     */
    private Optional<Backup> governing(ServerPlayer player) {
        Optional<Backup> pending = backup(player);
        if (pending.isPresent() && !RespawnPointBackup.governs(pending.orElseThrow().point(), player.getRespawnDimension(),
                player.getRespawnPosition(), player.getRespawnAngle(), player.isRespawnForced())) {
            saveCopies.reconcile(player, true);
            release(player);
            return Optional.empty();
        }
        return pending;
    }

    /** Removes the pending backup; an opaque retained root returns to its key. */
    private static void release(ServerPlayer player) {
        RespawnPointBackup.complete(persisted(player, false));
    }

    /** This service's own clear; the setter is a cancellable gameplay operation, not a preservation boundary. */
    private static void clear(ServerPlayer player) {
        if (!cleared(player)) {
            player.respawnDimension = Level.OVERWORLD;
            player.respawnPosition = null;
            player.respawnAngle = 0.0F;
            player.respawnForced = false;
        }
    }

    /** The exact cleared tuple: Overworld, no position, raw angle 0.0F, not forced. */
    private static boolean cleared(ServerPlayer player) {
        return player.respawnPosition == null && Level.OVERWORLD.equals(player.respawnDimension)
                && Float.floatToRawIntBits(player.respawnAngle) == 0 && !player.respawnForced;
    }

    private static CompoundTag persisted(ServerPlayer player, boolean create) {
        CompoundTag forge = player.getPersistentData();
        Tag parent = forge.get(Player.PERSISTED_NBT_TAG);
        if (parent instanceof CompoundTag compound) { return compound; }
        if (parent != null || !create) { return null; }
        CompoundTag compound = new CompoundTag();
        forge.put(Player.PERSISTED_NBT_TAG, compound);
        return compound;
    }

    private void restore(ServerPlayer player, boolean notify) {
        governing(player).ifPresent(backup -> restore(player, backup, notify)); // Unknown/corrupt data is kept verbatim.
    }

    /** A governing backup is either already restored, or is written into this service's exact clear. */
    private void restore(ServerPlayer player, Backup backup, boolean notify) {
        Point saved = backup.point();
        boolean alreadyRestored = matches(player, saved);
        if (!alreadyRestored) {
            player.respawnDimension = saved.dimension();
            player.respawnPosition = saved.position();
            player.respawnAngle = saved.angle();
            player.respawnForced = saved.forced();
        }
        if (matches(player, saved)) {
            // restoreFrom aliases PlayerPersisted between old/new objects. Until the packet handler adopts the
            // replacement, the old connection player can still be saved on disconnect with its native point empty.
            // Keep the backup through that window (and an exception later in respawn-event dispatch).
            if (player.connection != null && player.connection.player == player) {
                release(player);
            }
            if (notify && !alreadyRestored) {
                player.sendSystemMessage(Component.translatable(backup.outcome() == null
                        ? genericRespawnKey() : respawnKey(backup.outcome())));
            }
        }
    }

    private static boolean matches(ServerPlayer player, Point saved) {
        return saved.dimension().equals(player.getRespawnDimension()) && saved.position().equals(player.getRespawnPosition())
                && Float.floatToRawIntBits(saved.angle()) == Float.floatToRawIntBits(player.getRespawnAngle())
                && saved.forced() == player.isRespawnForced();
    }

    /** The bed head and, for a vanilla bed, its foot; one cell for any other sleeping place. */
    private static List<BlockPos> occupied(ServerLevel level, BlockPos head) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(head.getX() >> 4, head.getZ() >> 4);
        if (!level.isOutsideBuildHeight(head) && chunk != null) {
            BlockState state = chunk.getBlockState(head);
            if (state.getBlock() instanceof BedBlock) {
                return List.of(head, head.relative(state.getValue(BedBlock.FACING).getOpposite()));
            }
        }
        return List.of(head);
    }

    private Cell cell(ServerLevel level, ServerPlayer player, BlockPos position) {
        if (level.isOutsideBuildHeight(position)
                || level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4) == null) {
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
        private final Point saved;
        private ServerLevel level;
        private BlockState state;
        private Vec3 arrival;

        private Probe(MinecraftServer server, ServerPlayer player, Point saved) {
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

}
