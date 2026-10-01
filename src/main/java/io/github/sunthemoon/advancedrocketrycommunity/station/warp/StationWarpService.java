package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.StarSystemKnowledge;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationLocalActor;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteBudget;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteCooldown;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;

/**
 * ADR-044 station warp: charging through stateless cores, owner/operator request and one-shot
 * confirmation, an in-memory countdown, and a commit that moves the orbit and debits the balance in
 * one checked write. Every value is computed on the server; the client supplies only the target id.
 */
public final class StationWarpService {
    private final StationAccessService access = new StationAccessService();
    private final CelestialCatalogManager catalogs;
    private final Predicate<ResourceLocation> systemHasRoutes;
    private final Supplier<WarpSettings> settings;
    private final StationWarpCredits credits = new StationWarpCredits();
    private final StationWarpConfirmations confirmations = new StationWarpConfirmations();
    private final StationWarpCountdowns countdowns = new StationWarpCountdowns();
    private final StationWriteCooldown cooldown = new StationWriteCooldown(StationLimits.WARP_COOLDOWN_TICKS);
    private volatile StationRocketAuthority rocketAuthority = StationRocketAuthority.FAIL_CLOSED;
    private final StationWriteBudget writeBudget;

    public StationWarpService(CelestialCatalogManager catalogs, Predicate<ResourceLocation> systemHasRoutes,
                              Supplier<WarpSettings> settings) {
        this(catalogs, systemHasRoutes, settings, StationWriteBudget.unbounded());
    }

    /** {@code writeBudget} is shared with station expansion and gravity (review B9). */
    public StationWarpService(CelestialCatalogManager catalogs, Predicate<ResourceLocation> systemHasRoutes,
                              Supplier<WarpSettings> settings, StationWriteBudget writeBudget) {
        this.writeBudget = Objects.requireNonNull(writeBudget, "writeBudget");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.systemHasRoutes = Objects.requireNonNull(systemHasRoutes, "systemHasRoutes");
        this.settings = Objects.requireNonNull(settings, "settings");
    }

    /** True when any route of the current planetary catalog touches a body of the given system. */
    public static Predicate<ResourceLocation> routesInSystem(PlanetaryCatalogManager planetary) {
        Objects.requireNonNull(planetary, "planetary");
        return system -> planetary.capture().map(generation -> {
            CelestialCatalog celestial = generation.catalog().celestial();
            return generation.catalog().routes().definitions().stream().anyMatch(route ->
                    celestial.systemOf(route.from().bodyId()).filter(system::equals).isPresent()
                            || celestial.systemOf(route.to().bodyId()).filter(system::equals).isPresent());
        }).orElse(false);
    }

    /** Wires the rocket module's in-motion rule; until then every warp is refused (fail closed). */
    public StationRocketAuthority installRocketAuthority(StationRocketAuthority authority) {
        StationRocketAuthority previous = rocketAuthority;
        rocketAuthority = Objects.requireNonNull(authority, "authority");
        return previous;
    }

    /**
     * Forge Energy accepted by a warp core at {@code position}; see ADR-044 §2. Only the Space Level,
     * the server thread, a committed station region and an operational, unquarantined registry qualify.
     */
    public int receiveEnergy(ServerLevel level, BlockPos position, int maxReceive, boolean simulate) {
        MinecraftServer server = level.getServer();
        if (maxReceive <= 0 || !server.isSameThread() || !level.dimension().equals(CelestialIds.SPACE_LEVEL)
                || server.getLevel(CelestialIds.SPACE_LEVEL) != level) {
            return 0;
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        if (!data.acceptsWarpEnergy()) {
            return 0;
        }
        return data.findAt(position.getX(), position.getZ())
                .map(station -> credits.offer(station.stationId(), maxReceive, data.warpEnergy(station.stationId()),
                        server.getTickCount(), simulate))
                .orElse(0);
    }

    /** Moves pending credits into the registry as one ordinary mutation. */
    public StationRegistrySavedData.WarpCreditFold fold(MinecraftServer server) {
        if (credits.isEmpty()) {
            return new StationRegistrySavedData.WarpCreditFold(0L, 0L);
        }
        return StationRegistrySavedData.get(server).foldWarpCredits(credits.drain());
    }

    public int pendingCredit(UUID stationId) {
        return credits.pending(stationId);
    }

    public Optional<StationWarpCountdowns.Countdown> countdown(UUID stationId) {
        return countdowns.get(stationId);
    }

    public WarpSettings settings() {
        return settings.get();
    }

    /**
     * Operator diagnostics (WARP-05), bounded: one summary line, the rocket port's line, at most 64
     * countdown lines and, for a station, its warp state. Reads only; folds nothing.
     */
    public java.util.List<String> diagnostics(MinecraftServer server, Optional<UUID> stationId) {
        Objects.requireNonNull(server, "server");
        WarpSettings current = settings.get();
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        long now = server.getTickCount();
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add(String.format(java.util.Locale.ROOT,
                "warp enabled=%s cost_in_system=%d cost_interstellar=%d registry_operational=%s quarantined=%s"
                        + " pending_stations=%d pending_energy=%d confirmations=%d countdowns=%d",
                current.enabled(), current.inSystemCost(), current.interstellarCost(), data.operational(),
                data.updatesQuarantined(), credits.size(), credits.total(), confirmations.size(), countdowns.size()));
        StationRocketAuthority authority = rocketAuthority;
        lines.add(authority.diagnostics(server).orElse(authority == StationRocketAuthority.FAIL_CLOSED
                ? "rocket_authority=fail_closed" : "rocket_authority=installed"));
        for (StationWarpCountdowns.Countdown countdown : countdowns.all()) {
            WarpQuote quote = countdown.quote();
            lines.add(String.format(java.util.Locale.ROOT,
                    "countdown station=%s target=%s ticks_left=%d actor=%s class=%s cost=%d",
                    quote.stationId(), quote.target(), countdown.ticksLeft(now), quote.actorId(),
                    quote.costClass().label(), quote.cost()));
        }
        stationId.ifPresent(id -> lines.add(data.find(id).map(station -> String.format(java.util.Locale.ROOT,
                "station=%s orbit=%s balance=%d pending=%d cooldown_ready=%s rockets_in_motion=%s countdown=%s",
                id, station.orbitBody(), data.warpEnergy(id), credits.pending(id), cooldown.ready(id, now),
                authority.inMotion(server, station), countdowns.get(id).isPresent()))
                .orElse("station=" + id + " missing")));
        return lines;
    }

    /** {@code /arce station warp <body>}: quote and issue a one-shot confirmation. */
    public StationWarpResult request(ServerPlayer player, boolean issuedByPlayer, ResourceLocation target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(target, "target");
        StationLocalActor.Located located = StationLocalActor.locate(access, player, issuedByPlayer);
        if (located.code() != null) {
            return audit("request", player, StationWarpResult.of(located.code(), located.station()));
        }
        StationState station = located.station();
        MinecraftServer server = player.getServer();
        if (!settings.get().enabled()) {
            return audit("request", player, StationWarpResult.of(StationManagementCode.WARP_DISABLED, station));
        }
        if (!lookingAtCore(player, located.authority(), station)) {
            return audit("request", player, StationWarpResult.of(StationManagementCode.NO_WARP_CORE, station));
        }
        Quoted quoted = quote(server, located.authority(), station, target, player.getGameProfile());
        if (quoted.code() != null) {
            return audit("request", player, quoted.result(station));
        }
        boolean issued = confirmations.issue(quoted.quote(), located.authority(), server.getTickCount());
        return audit("request", player, StationWarpResult.quoted(
                issued ? StationManagementCode.WARP_ISSUED : StationManagementCode.WARP_CAPACITY_REACHED,
                quoted.quote(), quoted.balance()));
    }

    /** {@code /arce station warp confirm <station_id>}: start the countdown if nothing changed. */
    public StationWarpResult confirm(ServerPlayer player, boolean issuedByPlayer, UUID stationId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(stationId, "stationId");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationWarpResult.of(StationManagementCode.AUTHORITY_UNAVAILABLE, null);
        }
        if (!StationLocalActor.localPlayer(server, player, issuedByPlayer)) {
            // Rejected before take(): another source cannot consume the player's confirmation.
            return audit("confirm", player, StationWarpResult.of(StationManagementCode.NOT_LOCAL_PLAYER, null));
        }
        // ADR-046 UI-03: an actor who could not act here now is refused before take(), with the reason.
        StationLocalActor.Located actor = StationLocalActor.locate(access, player, issuedByPlayer);
        if (actor.code() != null) {
            return audit("confirm", player, StationWarpResult.of(actor.code(), actor.station()));
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        var outcome = confirmations.take(player.getUUID(), stationId, data, server.getTickCount());
        StationManagementCode rejected = switch (outcome.status()) {
            case READY -> null;
            case MISSING -> StationManagementCode.WARP_NO_CONFIRMATION;
            case EXPIRED -> StationManagementCode.WARP_CONFIRMATION_EXPIRED;
            case MISMATCH -> StationManagementCode.CONFIRMATION_MISMATCH;
        };
        if (rejected != null) {
            return audit("confirm", player, StationWarpResult.of(rejected, null));
        }
        WarpQuote confirmed = outcome.quote();
        StationLocalActor.Located located = StationLocalActor.locate(access, player, issuedByPlayer);
        if (located.code() != null) {
            return audit("confirm", player, StationWarpResult.of(located.code(), located.station()));
        }
        if (located.authority() != data || !confirmed.observed().equals(located.station())) {
            return audit("confirm", player, StationWarpResult.of(StationManagementCode.STATION_CHANGED,
                    located.station()));
        }
        if (!settings.get().enabled()) {
            return audit("confirm", player, StationWarpResult.of(StationManagementCode.WARP_DISABLED,
                    located.station()));
        }
        Quoted current = quote(server, data, located.station(), confirmed.target(), confirmed.actor());
        if (current.code() != null) {
            return audit("confirm", player, current.result(located.station()));
        }
        if (current.quote().costClass() != confirmed.costClass() || current.quote().cost() != confirmed.cost()) {
            return audit("confirm", player, StationWarpResult.quoted(StationManagementCode.WARP_QUOTE_CHANGED,
                    current.quote(), current.balance()));
        }
        StationManagementCode started = switch (countdowns.start(confirmed, server.getTickCount())) {
            case STARTED -> StationManagementCode.WARP_STARTED;
            case ALREADY_RUNNING -> StationManagementCode.WARP_COUNTDOWN_ACTIVE;
            case CAPACITY_REACHED -> StationManagementCode.WARP_CAPACITY_REACHED;
        };
        if (started == StationManagementCode.WARP_STARTED) {
            notifyMembers(server, confirmed, Component.literal(String.format(java.util.Locale.ROOT,
                    "Station warp countdown started by %s: %s -> %s in %d seconds (%s, %d FE).",
                    player.getGameProfile().getName(), confirmed.observed().orbitBody(), confirmed.target(),
                    StationWarpCountdowns.START_SECONDS, confirmed.costClass().label(), confirmed.cost())), false);
            announce(server, confirmed, StationWarpCountdowns.START_SECONDS);
        }
        return audit("confirm", player, StationWarpResult.quoted(started, confirmed, current.balance()));
    }

    /** {@code /arce station warp cancel}: the owner or an operator standing in the station. */
    public StationWarpResult cancel(ServerPlayer player, boolean issuedByPlayer) {
        Objects.requireNonNull(player, "player");
        StationLocalActor.Located located = StationLocalActor.locate(access, player, issuedByPlayer);
        if (located.code() != null) {
            return audit("cancel", player, StationWarpResult.of(located.code(), located.station()));
        }
        Optional<StationWarpCountdowns.Countdown> cancelled = countdowns.cancel(located.station().stationId());
        if (cancelled.isEmpty()) {
            return audit("cancel", player, StationWarpResult.of(StationManagementCode.WARP_NO_COUNTDOWN,
                    located.station()));
        }
        notifyMembers(player.getServer(), cancelled.orElseThrow().quote(), Component.literal(
                "Station warp cancelled by " + player.getGameProfile().getName() + "."), false);
        return audit("cancel", player, StationWarpResult.quoted(StationManagementCode.WARP_CANCELLED,
                cancelled.orElseThrow().quote(), located.authority().warpEnergy(located.station().stationId())));
    }

    /**
     * Read-only status for the owner, members and operators inside the station. It never folds (WARP
     * review R6): the balance is the folded one, and callers show {@link #pendingCredit} beside it.
     */
    public StationWarpResult status(ServerPlayer player) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationWarpResult.of(StationManagementCode.AUTHORITY_UNAVAILABLE, null);
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        ServerLevel level = player.serverLevel();
        if (!data.operational()) {
            return StationWarpResult.of(StationManagementCode.AUTHORITY_UNAVAILABLE, null);
        }
        if (!level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            return StationWarpResult.of(StationManagementCode.NOT_IN_SPACE, null);
        }
        Optional<StationState> station = data.findAt(player.getBlockX(), player.getBlockZ());
        if (station.isEmpty()) {
            return StationWarpResult.of(StationManagementCode.NOT_IN_STATION, null);
        }
        if (!access.allowed(station.orElseThrow(), player.getUUID(), player.hasPermissions(2),
                StationAccessAction.VISIT)) {
            return StationWarpResult.of(StationManagementCode.UNAUTHORIZED, null);
        }
        UUID stationId = station.orElseThrow().stationId();
        return new StationWarpResult(StationManagementCode.WARP_STATUS, station,
                countdowns.get(stationId).map(StationWarpCountdowns.Countdown::quote), data.warpEnergy(stationId));
    }

    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.getTickCount();
        if (now % StationLimits.WARP_CREDIT_FOLD_TICKS == 0L) {
            fold(server);
        }
        for (StationWarpCountdowns.Announcement announcement : countdowns.announcements(now)) {
            announce(server, announcement.countdown().quote(), announcement.secondsLeft());
        }
        // ADR-044 §4: at most one commit per server tick; others due now wait and are fully rechecked.
        // A due countdown also waits for the server-wide write spacing (review B9); it stays due.
        countdowns.nextDue(now)
                .filter(countdown -> writeBudget.ready(now, StationRegistrySavedData.get(server).recordCount()))
                .ifPresent(countdown -> {
                    countdowns.cancel(countdown.quote().stationId());
                    commit(server, countdown.quote());
                });
    }

    /** Final fold before the stop save (ADR-044 §2); countdowns are discarded, nothing was persisted. */
    public void onServerStopping(ServerStoppingEvent event) {
        fold(event.getServer());
        countdowns.clear();
        confirmations.clear();
    }

    /** Logging out drops a pending confirmation but never cancels a running countdown. */
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        confirmations.clear(event.getEntity().getUUID());
    }

    public void clear() {
        credits.clear();
        confirmations.clear();
        countdowns.clear();
        cooldown.clear();
    }

    /** Package-private for GameTests through the command; production calls come from the tick. */
    StationWarpResult commit(MinecraftServer server, WarpQuote confirmed) {
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        StationWarpResult result = commitChecked(server, data, confirmed);
        StationManagementCode code = result.code();
        if (code == StationManagementCode.WARP_COMMITTED || code == StationManagementCode.WRITE_FAILED
                || code == StationManagementCode.OUTCOME_UNKNOWN) {
            // A failing disk is not retried every tick (ADR-041 cooldown).
            cooldown.record(confirmed.stationId(), server.getTickCount());
            writeBudget.record(server.getTickCount());
        }
        Component message = code == StationManagementCode.WARP_COMMITTED
                ? Component.literal(String.format(java.util.Locale.ROOT,
                "Station warped: %s -> %s (%s, %d FE); warp energy left %d FE.", confirmed.observed().orbitBody(),
                confirmed.target(), confirmed.costClass().label(), confirmed.cost(), result.balance()))
                : Component.literal("Station warp aborted: " + code.description() + ".");
        notifyMembers(server, confirmed, message, false);
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_WARP action=commit code={} station={} actor={} from={} to={} class={} cost={} balance={}",
                code, confirmed.stationId(), confirmed.actorId(), confirmed.observed().orbitBody(), confirmed.target(),
                confirmed.costClass(), confirmed.cost(), result.balance());
        return result;
    }

    private StationWarpResult commitChecked(MinecraftServer server, StationRegistrySavedData data, WarpQuote confirmed) {
        if (!settings.get().enabled()) {
            return StationWarpResult.of(StationManagementCode.WARP_DISABLED, confirmed.observed());
        }
        if (!data.operational() || data.updatesQuarantined()) {
            return StationWarpResult.of(StationManagementCode.AUTHORITY_UNAVAILABLE, confirmed.observed());
        }
        Optional<StationState> live = data.find(confirmed.stationId());
        if (live.isEmpty() || !live.orElseThrow().equals(confirmed.observed())) {
            return StationWarpResult.of(StationManagementCode.STATION_CHANGED, live.orElse(null));
        }
        StationState station = live.orElseThrow();
        if (!station.ownerId().equals(confirmed.actorId())
                && server.getProfilePermissions(confirmed.actor()) < 2) {
            return StationWarpResult.of(StationManagementCode.WARP_ACTOR_CHANGED, station);
        }
        Quoted current = quote(server, data, station, confirmed.target(), confirmed.actor());
        if (current.code() != null) {
            return current.result(station);
        }
        if (current.quote().costClass() != confirmed.costClass() || current.quote().cost() != confirmed.cost()) {
            return StationWarpResult.quoted(StationManagementCode.WARP_QUOTE_CHANGED, current.quote(),
                    current.balance());
        }
        StationRegistrySavedData.CheckedUpdate written;
        try {
            written = data.checkedRelocation(server, station, confirmed.target(), confirmed.cost());
        } catch (RuntimeException exception) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_WARP_WRITE_FAILED station={} stage=candidate", station.stationId(), exception);
            written = StationRegistrySavedData.CheckedUpdate.WRITE_FAILED;
        }
        StationManagementCode code = switch (written) {
            case COMMITTED -> StationManagementCode.WARP_COMMITTED;
            case UNCHANGED -> StationManagementCode.WARP_SAME_ORBIT;
            case STALE -> StationManagementCode.STATION_CHANGED;
            case UNAVAILABLE -> StationManagementCode.AUTHORITY_UNAVAILABLE;
            case INSUFFICIENT_ENERGY -> StationManagementCode.WARP_INSUFFICIENT_ENERGY;
            case WRITE_FAILED -> StationManagementCode.WRITE_FAILED;
            case OUTCOME_UNKNOWN -> StationManagementCode.OUTCOME_UNKNOWN;
        };
        return new StationWarpResult(code, data.find(station.stationId()), Optional.of(confirmed),
                data.warpEnergy(station.stationId()));
    }

    /**
     * Server-side quote for the station's current state: target present, orbitable, known and not the
     * current orbit; not cooling down or counting down; balance (after a fold) covering the cost; and
     * no rocket that can still move in the region.
     */
    private Quoted quote(MinecraftServer server, StationRegistrySavedData data, StationState station,
                         ResourceLocation target, com.mojang.authlib.GameProfile actor) {
        // ADR-059 section 5: request, confirmation and commit all quote here, so each reads the live pair set; the
        // commit does so in the tick of its checked relocation.
        switch (ElevatorStationGuard.Installed.current().warp(server, station)) {
            case BOUND -> {
                return Quoted.failed(StationManagementCode.ELEVATOR_BOUND);
            }
            case UNAVAILABLE -> {
                return Quoted.failed(StationManagementCode.ENDGAME_UNAVAILABLE);
            }
            default -> {
            }
        }
        if (countdowns.get(station.stationId()).isPresent()) {
            return Quoted.failed(StationManagementCode.WARP_COUNTDOWN_ACTIVE);
        }
        if (!cooldown.ready(station.stationId(), server.getTickCount())) {
            return Quoted.failed(StationManagementCode.WARP_COOLDOWN);
        }
        Optional<CelestialCatalog> catalog = catalogs.current();
        if (catalog.isEmpty()) {
            return Quoted.failed(StationManagementCode.WARP_CATALOG_UNAVAILABLE);
        }
        Optional<CelestialBodyDefinition> body = catalog.orElseThrow().get(target);
        if (body.isEmpty() || !body.orElseThrow().capabilities().orbitable()) {
            return Quoted.failed(StationManagementCode.WARP_TARGET_UNAVAILABLE);
        }
        CelestialSavedData discoveries = CelestialSavedData.get(server);
        if (!StarSystemKnowledge.bodyKnown(catalog.orElseThrow(), target,
                id -> discoveries.isWritableSchema() && discoveries.get(id).isPresent())) {
            return Quoted.failed(StationManagementCode.WARP_TARGET_UNKNOWN);
        }
        if (target.equals(station.orbitBody())) {
            return Quoted.failed(StationManagementCode.WARP_SAME_ORBIT);
        }
        WarpCostClass costClass = WarpCostClass.of(catalog.orElseThrow(), station.orbitBody(), target);
        int cost = settings.get().cost(costClass);
        boolean routes = catalog.orElseThrow().systemOf(target).filter(systemHasRoutes).isPresent();
        WarpQuote quote = new WarpQuote(actor, station, target, costClass, cost, routes);
        fold(server);
        int balance = data.warpEnergy(station.stationId());
        if (balance < cost) {
            return new Quoted(StationManagementCode.WARP_INSUFFICIENT_ENERGY, quote, balance);
        }
        if (rocketAuthority.inMotion(server, station)) {
            return new Quoted(StationManagementCode.WARP_ROCKETS_IN_MOTION, quote, balance);
        }
        return new Quoted(null, quote, balance);
    }

    /** Server ray pick from the player's eyes: a warp core inside this station's committed region. */
    private static boolean lookingAtCore(ServerPlayer player, StationRegistrySavedData data, StationState station) {
        HitResult hit = player.pick(StationLimits.WARP_CORE_REACH, 1.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK || !(hit instanceof BlockHitResult block)) {
            return false;
        }
        BlockPos position = block.getBlockPos();
        ServerLevel level = player.serverLevel();
        return level.hasChunkAt(position)
                && level.getBlockState(position).is(ModBlocks.WARP_CORE.get())
                && data.findAt(position.getX(), position.getZ())
                .map(StationState::stationId).filter(station.stationId()::equals).isPresent();
    }

    private static void announce(MinecraftServer server, WarpQuote quote, int secondsLeft) {
        notifyMembers(server, quote, Component.literal(String.format(java.util.Locale.ROOT,
                "Station warp to %s in %d s", quote.target(), secondsLeft)), true);
    }

    /** Owner, members and the actor, when online; chat, or the action bar for the countdown. */
    private static void notifyMembers(MinecraftServer server, WarpQuote quote, Component message, boolean actionBar) {
        if (server == null) {
            return;
        }
        Set<UUID> recipients = new LinkedHashSet<>();
        recipients.add(quote.observed().ownerId());
        recipients.addAll(quote.observed().sortedMembers());
        recipients.add(quote.actorId());
        for (UUID recipient : recipients) {
            ServerPlayer online = server.getPlayerList().getPlayer(recipient);
            if (online != null) {
                online.displayClientMessage(message, actionBar);
            }
        }
    }

    private static StationWarpResult audit(String action, ServerPlayer player, StationWarpResult result) {
        AdvancedRocketryCommunity.LOGGER.info(
                "ARCE_STATION_WARP action={} code={} station={} actor={} target={} class={} cost={} balance={}",
                action, result.code(), result.station().map(StationState::stationId).orElse(null), player.getUUID(),
                result.quote().map(WarpQuote::target).orElse(null),
                result.quote().map(WarpQuote::costClass).orElse(null),
                result.quote().map(WarpQuote::cost).orElse(null), result.balance());
        return result;
    }

    private record Quoted(StationManagementCode code, WarpQuote quote, int balance) {
        static Quoted failed(StationManagementCode code) {
            return new Quoted(code, null, 0);
        }

        StationWarpResult result(StationState station) {
            return quote == null ? StationWarpResult.of(code, station) : StationWarpResult.quoted(code, quote, balance);
        }
    }
}
