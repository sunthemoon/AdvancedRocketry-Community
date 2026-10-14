package io.github.sunthemoon.advancedrocketrycommunity.station.warp;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.data.PlanetaryCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.model.CelestialBodyDefinition;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.StarSystemKnowledge;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.ClassicFirstEventDelivery;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventCutSeams;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventDisposition;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent.FirstEventKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.FirstWarpRequest;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits;
import io.github.sunthemoon.advancedrocketrycommunity.station.elevator.ElevatorStationGuard;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNotice;
import io.github.sunthemoon.advancedrocketrycommunity.station.notice.StationNoticeService;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationActorContext;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationLocalActor;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteBudget;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteCooldown;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
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
    /** ADR-065 section 5.3 notices; stateless, its summaries live in their own SavedData. */
    private final StationNoticeService notices;
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
        this(catalogs, systemHasRoutes, settings, writeBudget, new StationNoticeService());
    }

    /** With the notice service to use; a GameTest passes one whose outcome delivery fails (W22 review F7). */
    public StationWarpService(CelestialCatalogManager catalogs, Predicate<ResourceLocation> systemHasRoutes,
                              Supplier<WarpSettings> settings, StationWriteBudget writeBudget,
                              StationNoticeService notices) {
        this.writeBudget = Objects.requireNonNull(writeBudget, "writeBudget");
        this.catalogs = Objects.requireNonNull(catalogs, "catalogs");
        this.systemHasRoutes = Objects.requireNonNull(systemHasRoutes, "systemHasRoutes");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.notices = Objects.requireNonNull(notices, "notices");
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

    /** A warp confirmation or countdown is pending for the station: an elevator bind waits (ADR-059 section 3). */
    public boolean warpPending(MinecraftServer server, UUID stationId) {
        return countdowns.get(stationId).isPresent() || confirmations.pending(stationId, server.getTickCount());
    }

    public WarpSettings settings() {
        return settings.get();
    }

    /** The server-wide checked-write spacing; the first-warp acknowledgement is charged to it (W44 section 1.3). */
    public StationWriteBudget writeBudget() {
        return writeBudget;
    }

    /**
     * The bodies a {@code warp_controller} screen offers: present, orbitable and known, not the current orbit,
     * sorted by ID and at most {@code limit}. A screen sends only an index into this server list; every request
     * is still quoted and checked in full.
     */
    public java.util.List<ResourceLocation> warpTargets(MinecraftServer server, StationState station, int limit) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(station, "station");
        Optional<CelestialCatalog> catalog = catalogs.current();
        if (catalog.isEmpty() || limit <= 0) {
            return java.util.List.of();
        }
        CelestialSavedData discoveries = CelestialSavedData.get(server);
        return catalog.orElseThrow().definitions().stream()
                .filter(body -> body.capabilities().orbitable() && !body.id().equals(station.orbitBody()))
                .map(CelestialBodyDefinition::id)
                .filter(id -> StarSystemKnowledge.bodyKnown(catalog.orElseThrow(), id,
                        known -> discoveries.isWritableSchema() && discoveries.get(known).isPresent()))
                .sorted(java.util.Comparator.comparing(ResourceLocation::toString))
                .limit(limit)
                .toList();
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
        return request(player, StationActorContext.command(issuedByPlayer), target);
    }

    /**
     * The quote-and-issue step through the shared context validator (ADR-065 section 5.1): a command needs the
     * player's own server ray pick on a core of the station; a {@code warp_controller} screen needs a loaded core
     * of the station within the 5-block core reach of the controller. Opening a screen alone does nothing.
     */
    public StationWarpResult request(ServerPlayer player, StationActorContext context, ResourceLocation target) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(target, "target");
        StationLocalActor.Located located = StationLocalActor.locate(access, player, context);
        if (located.code() != null) {
            return audit("request", player, StationWarpResult.of(located.code(), located.station()));
        }
        StationState station = located.station();
        MinecraftServer server = player.getServer();
        if (!settings.get().enabled()) {
            return audit("request", player, StationWarpResult.of(StationManagementCode.WARP_DISABLED, station));
        }
        if (!coreReachable(player, context, located.authority(), station)) {
            return audit("request", player, StationWarpResult.of(context.viaController()
                    ? StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER : StationManagementCode.NO_WARP_CORE, station));
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
        return confirm(player, StationActorContext.command(issuedByPlayer), stationId);
    }

    /** The confirmation step through the shared context validator; a screen confirms its own station. */
    public StationWarpResult confirm(ServerPlayer player, StationActorContext context, UUID stationId) {
        return confirm(player, context, stationId, null);
    }

    /**
     * As {@link #confirm(ServerPlayer, StationActorContext, UUID)}; with {@code expected} (a {@code warp_controller}
     * screen, W07 review R1) only that exact issued quote, the one the screen shows, can start. The actor's other
     * confirmation is a mismatch and is consumed, as any other mismatch is.
     */
    public StationWarpResult confirm(ServerPlayer player, StationActorContext context, UUID stationId,
                                     WarpQuote expected) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(stationId, "stationId");
        MinecraftServer server = player.getServer();
        if (server == null) {
            return StationWarpResult.of(StationManagementCode.AUTHORITY_UNAVAILABLE, null);
        }
        if (!StationLocalActor.localPlayer(server, player, context.issuedByPlayer())) {
            // Rejected before take(): another source cannot consume the player's confirmation.
            return audit("confirm", player, StationWarpResult.of(StationManagementCode.NOT_LOCAL_PLAYER, null));
        }
        // ADR-046 UI-03: an actor who could not act here now is refused before take(), with the reason.
        StationLocalActor.Located actor = StationLocalActor.locate(access, player, context);
        if (actor.code() != null) {
            return audit("confirm", player, StationWarpResult.of(actor.code(), actor.station()));
        }
        // ADR-065 section 5.1 (W07 review R2): a screen also needs its live 5-block core reach before take(), so a
        // core removed, moved or unloaded since the quote refuses without consuming it. The command path keeps its
        // own rule, which has no ray pick at confirmation.
        if (context.viaController() && !coreReachable(player, context, actor.authority(), actor.station())) {
            return audit("confirm", player, StationWarpResult.of(StationManagementCode.NO_WARP_CORE_NEAR_CONTROLLER,
                    actor.station()));
        }
        StationRegistrySavedData data = StationRegistrySavedData.get(server);
        var outcome = confirmations.take(player.getUUID(), stationId, data, server.getTickCount(), expected);
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
        StationLocalActor.Located located = StationLocalActor.locate(access, player, context);
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
            notices.deliver(server, confirmed.stationId(), confirmed.actorId(), StationNotice.started(
                    confirmed.observed().name(), confirmed.observed().orbitBody(), confirmed.target(),
                    StationWarpCountdowns.START_SECONDS, costClass(confirmed), confirmed.cost()), false);
            announce(server, confirmed, StationWarpCountdowns.START_SECONDS);
        }
        return audit("confirm", player, StationWarpResult.quoted(started, confirmed, current.balance()));
    }

    /** Whether {@code quote} is still the player's live, unexpired confirmation; a screen offers Confirm only then. */
    public boolean confirmable(ServerPlayer player, WarpQuote quote) {
        Objects.requireNonNull(player, "player");
        MinecraftServer server = player.getServer();
        return server != null && confirmations.holds(player.getUUID(), quote, server.getTickCount());
    }

    /**
     * W07 review R1: a screen that stops showing {@code quote} (another target, a new or failed request, a closed
     * or replaced menu) withdraws it, so nothing can confirm it later. Another confirmation of the actor stays.
     */
    public void withdraw(UUID actorId, WarpQuote quote) {
        confirmations.withdraw(actorId, quote);
    }

    /** {@code /arce station warp cancel}: the owner or an operator standing in the station. */
    public StationWarpResult cancel(ServerPlayer player, boolean issuedByPlayer) {
        return cancel(player, StationActorContext.command(issuedByPlayer));
    }

    /** The cancel step through the shared context validator. */
    public StationWarpResult cancel(ServerPlayer player, StationActorContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
        StationLocalActor.Located located = StationLocalActor.locate(access, player, context);
        if (located.code() != null) {
            return audit("cancel", player, StationWarpResult.of(located.code(), located.station()));
        }
        Optional<StationWarpCountdowns.Countdown> cancelled = countdowns.cancel(located.station().stationId());
        if (cancelled.isEmpty()) {
            return audit("cancel", player, StationWarpResult.of(StationManagementCode.WARP_NO_COUNTDOWN,
                    located.station()));
        }
        WarpQuote quote = cancelled.orElseThrow().quote();
        notices.deliverOutcome(player.getServer(), quote.stationId(), player.getUUID(), StationNotice.aborted(
                quote.observed().name(), StationManagementCode.WARP_CANCELLED, quote.target()));
        return audit("cancel", player, StationWarpResult.quoted(StationManagementCode.WARP_CANCELLED,
                cancelled.orElseThrow().quote(), located.authority().warpEnergy(located.station().stationId())));
    }

    /**
     * Read-only status for the owner, members and operators inside the station. It never folds (WARP
     * review R6): the balance is the folded one, and callers show {@link #pendingCredit} beside it.
     */
    public StationWarpResult status(ServerPlayer player) {
        return status(player, StationActorContext.command(true));
    }

    /** The status of the station at the player or, for a screen, at its controller (which must be in reach). */
    public StationWarpResult status(ServerPlayer player, StationActorContext context) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(context, "context");
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
        BlockPos at = context.controller().map(StationActorContext.Controller::position).orElse(player.blockPosition());
        Optional<StationState> station = data.findAt(at.getX(), at.getZ());
        if (station.isEmpty()) {
            return StationWarpResult.of(StationManagementCode.NOT_IN_STATION, null);
        }
        if (context.viaController()
                && !StationLocalActor.reachesController(player, context.controller().orElseThrow(), station.get(), data)) {
            return StationWarpResult.of(StationManagementCode.CONTROLLER_OUT_OF_REACH, null);
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
        if (code == StationManagementCode.WARP_COMMITTED) {
            result.station().ifPresent(station -> ClassicAdvancementEvents.stationWarped(presentVisitors(server, station)));
            ClassicFirstEventDelivery.sourceChanged(server, FirstEventKind.FIRST_STATION_WARP);
        }
        // W22 review F7: the notices follow the checked write, so a failure in them is logged once and never escapes
        // the tick, skips the audit line below or changes the outcome.
        try {
            String name = confirmed.observed().name();
            notices.deliverOutcome(server, confirmed.stationId(), confirmed.actorId(),
                    code == StationManagementCode.WARP_COMMITTED
                            ? StationNotice.committed(name, confirmed.observed().orbitBody(), confirmed.target(),
                            costClass(confirmed), confirmed.cost(), result.balance())
                            : StationNotice.aborted(name, code, confirmed.target()));
        } catch (RuntimeException failure) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_STATION_NOTICE_FAILED station={} code={} error={}",
                    confirmed.stationId(), code, failure.getClass().getSimpleName());
        }
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
        // W44 section 1.2: the participants are captured in this commit tick with the same bounded builder as the
        // quote, and the checked call decides the election; the four-argument fixture overload never elects.
        FirstEventDisposition first = firstWarpDisposition(server, data);
        java.util.List<UUID> participants = first == FirstEventDisposition.ALLOW
                ? eligibleVisitors(server, station) : java.util.List.of();
        if (participants.size() > FirstEventKind.FIRST_STATION_WARP.maxParticipants()) {
            return StationWarpResult.of(StationManagementCode.WARP_FIRST_EVENT_OVER_BUDGET, station);
        }
        FirstWarpRequest request = new FirstWarpRequest(confirmed.actorId(), participants, first,
                () -> firstWarpDisposition(server, data));
        StationRegistrySavedData.CheckedUpdate written;
        try {
            written = data.checkedRelocation(server, station, confirmed.target(), confirmed.cost(), request);
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
            case FIRST_EVENT_OVER_BUDGET -> StationManagementCode.WARP_FIRST_EVENT_OVER_BUDGET;
            case FIRST_EVENT_UNAVAILABLE -> StationManagementCode.WARP_FIRST_EVENT_UNAVAILABLE;
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
        // W44 section 5: a potentially first warp waits while the authority is unavailable, and is refused over
        // budget after a saturating count of eligible visitors, before any list is retained or anything is debited.
        FirstEventDisposition first = firstWarpDisposition(server, data);
        if (first == FirstEventDisposition.DEFER) {
            return new Quoted(StationManagementCode.WARP_FIRST_EVENT_UNAVAILABLE, quote, balance);
        }
        if (first == FirstEventDisposition.ALLOW) {
            int visitors = FirstEventCutSeams.warpVisitorCount(server).orElseGet(() -> countEligibleVisitors(server,
                    station, FirstEventKind.FIRST_STATION_WARP.maxParticipants() + 1));
            if (visitors > FirstEventKind.FIRST_STATION_WARP.maxParticipants() || !data.admitsFirstWarp(visitors)) {
                return new Quoted(StationManagementCode.WARP_FIRST_EVENT_OVER_BUDGET, quote, balance);
            }
        }
        return new Quoted(null, quote, balance);
    }

    /** Refreshed on the server thread before each potentially first step (W44 section 3.3, step 4). */
    private static FirstEventDisposition firstWarpDisposition(MinecraftServer server, StationRegistrySavedData data) {
        return ClassicFirstEventDelivery.disposition(server, FirstEventKind.FIRST_STATION_WARP, data.durableFirstWarp(),
                data.firstWarpRam());
    }

    /** The eligible station visitors, counted with saturation at {@code limit}; no list is retained. */
    private int countEligibleVisitors(MinecraftServer server, StationState station, int limit) {
        int count = 0;
        for (ServerPlayer player : visitorCandidates(server)) {
            if (count >= limit) {
                break;
            }
            if (eligibleVisitor(player, station)) {
                count++;
            }
        }
        return count;
    }

    /**
     * W44 section 5: the first-warp participants, sorted; the same predicate as {@code station_warp} plus the one
     * shared eligibility predicate. The builder stops one past the bound, so an excess is refused, never truncated.
     */
    private java.util.List<UUID> eligibleVisitors(MinecraftServer server, StationState station) {
        java.util.Optional<java.util.List<UUID>> fixed = FirstEventCutSeams.participants(server,
                FirstEventKind.FIRST_STATION_WARP);
        if (fixed.isPresent()) {
            return fixed.orElseThrow();
        }
        int limit = FirstEventKind.FIRST_STATION_WARP.maxParticipants() + 1;
        java.util.List<UUID> result = new java.util.ArrayList<>();
        for (ServerPlayer player : visitorCandidates(server)) {
            if (result.size() >= limit) {
                break;
            }
            if (eligibleVisitor(player, station)) {
                result.add(player.getUUID());
            }
        }
        return result.stream().sorted().toList();
    }

    private static java.util.List<ServerPlayer> visitorCandidates(MinecraftServer server) {
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        return space == null ? java.util.List.of() : java.util.List.copyOf(space.players());
    }

    private boolean eligibleVisitor(ServerPlayer player, StationState station) {
        return station.region().contains(player.getBlockX(), player.getBlockZ())
                && access.allowed(station, player.getUUID(), player.hasPermissions(2), StationAccessAction.VISIT)
                && ClassicAdvancementEvents.eligible(player);
    }

    /**
     * A command needs the server ray pick from the player's eyes; a controller screen needs a loaded core whose
     * centre is within {@link StationLimits#WARP_CORE_REACH} of the controller's centre ({@link WarpCoreReach}).
     * Either core must stand inside this station's committed region. Only loaded chunks are inspected.
     */
    private static boolean coreReachable(ServerPlayer player, StationActorContext context, StationRegistrySavedData data,
                                         StationState station) {
        if (context.controller().isEmpty()) {
            return lookingAtCore(player, data, station);
        }
        BlockPos controller = context.controller().orElseThrow().position();
        ServerLevel level = player.serverLevel();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        return WarpCoreReach.reachable(controller.getX(), controller.getY(), controller.getZ(), new WarpCoreReach.Probe() {
            @Override
            public boolean loaded(int x, int y, int z) {
                cursor.set(x, y, z);
                return level.isInWorldBounds(cursor) && level.hasChunkAt(cursor);
            }

            @Override
            public boolean core(int x, int y, int z) {
                return level.getBlockState(cursor.set(x, y, z)).is(ModBlocks.WARP_CORE.get());
            }

            @Override
            public boolean inStation(int x, int z) {
                return data.findAt(x, z).map(StationState::stationId).filter(station.stationId()::equals).isPresent();
            }
        });
    }

    /** ADR-066 §6.2: players in Space inside the committed station region who hold live VISIT access. */
    private java.util.List<ServerPlayer> presentVisitors(MinecraftServer server, StationState station) {
        ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
        if (space == null) {
            return java.util.List.of();
        }
        return space.players().stream()
                .filter(player -> station.region().contains(player.getBlockX(), player.getBlockZ()))
                .filter(player -> access.allowed(station, player.getUUID(), player.hasPermissions(2),
                        StationAccessAction.VISIT))
                .toList();
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

    /** ADR-065 section 5.3: the countdown uses only the action bar. */
    private void announce(MinecraftServer server, WarpQuote quote, int secondsLeft) {
        notices.deliver(server, quote.stationId(), quote.actorId(),
                StationNotice.countdown(quote.observed().name(), quote.target(), secondsLeft), true);
    }

    private static String costClass(WarpQuote quote) {
        return quote.costClass().name().toLowerCase(java.util.Locale.ROOT);
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
