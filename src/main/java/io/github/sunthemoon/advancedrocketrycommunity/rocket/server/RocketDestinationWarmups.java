package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowGovernedPreparation;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestCode;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightRequestResult;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupRegistry;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketWarmupSettings;
import io.github.sunthemoon.advancedrocketrycommunity.station.pad.LandingPadService;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

/**
 * W90b (Root decisions R21 item 4 and R22 amendments 1 to 4): the Forge side of the governed destination warm-up.
 * {@link RocketWarmupRegistry} holds the state machine; this class supplies the plain ticket type, the Levels, the
 * players and the source entities, and the small hooks that {@link RocketTransferService} calls. Every hook runs on
 * the server thread; nothing here is saved.
 */
final class RocketDestinationWarmups {
    /** Plain: never forced, never saved. Its name must not contain {@code arce_rocket_flight}, a test scan's substring. */
    static final TicketType<UUID> WARMUP_TICKET = TicketType.create("arce_rocket_warmup", Comparator.<UUID>naturalOrder(),
            RocketWarmupSettings.MAX_LIFETIME_TICKS);
    static final int TICKET_DISTANCE = 2;
    static final String READY_MESSAGE = "message.advancedrocketrycommunity.flight.destination_ready";
    private final Supplier<RocketWarmupSettings> settings;
    private final RocketWarmupRegistry registry;
    private MinecraftServer server;
    private RocketWarmupRegistry.Decision decision;

    RocketDestinationWarmups() {
        this(CommonConfig::destinationWarmupSettings);
    }

    RocketDestinationWarmups(Supplier<RocketWarmupSettings> settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        registry = new RocketWarmupRegistry(new LevelTickets(), line -> AdvancedRocketryCommunity.LOGGER.info("{}", line));
    }

    /** R22 amendment 4: the launch's discard releases the warm-up bound to it, and nothing else. */
    RocketRequestCompletion observe(RocketEntity rocket, RocketFlightData countdown, RocketRequestCompletion completion) {
        return new RocketRequestCompletion(completion.accepted(), () -> {
            countdown.plan().ifPresent(plan -> launchEnded(rocket.getServer(), countdown.logicalRocketId(), plan.requestId()));
            completion.discarded().run();
        }, completion.requester(), completion.requiresOnline());
    }

    /**
     * Design 7.2's decision, immediately before F1's begin: null to call begin, or the refusal. A refusal releases the
     * request's tentative named-pad claim at once, so no saved claim survives the wait (design 7.4). {@link #afterBegin}
     * then binds the warm-up on acceptance or releases it.
     */
    RocketFlightRequestResult decide(MinecraftServer current, RocketEntity rocket, RocketFlightData countdown,
                                     RocketStructureSnapshot destination, RocketRequestCompletion completion) {
        server = current;
        var plan = countdown.plan().orElseThrow();
        var request = new RocketWarmupRegistry.Request(countdown.logicalRocketId(), completion.requester(), plan.requestId(),
                rocket.level().dimension().location(), rocket.getUUID(), rocket.snapshot().orElseThrow(),
                plan.destinationTarget(), destination, completion.requiresOnline());
        decision = registry.decide(request, EscrowGovernedPreparation.available(rocket, countdown),
                () -> RocketTransferEntities.endAlreadyLoaded(current, destination), current.getTickCount(), settings.get());
        if (decision.outcome() == RocketWarmupRegistry.Outcome.PROCEED) { return null; }
        LandingPadService.release(current, plan);
        return RocketFlightRequestResult.failure(decision.outcome() == RocketWarmupRegistry.Outcome.LOADING
                ? RocketFlightRequestCode.DESTINATION_LOADING : RocketFlightRequestCode.DESTINATION_LOADING_LIMIT, plan.requiredFuel());
    }

    void afterBegin(boolean started) {
        var made = decision;
        decision = null;
        if (server != null) { registry.afterBegin(made, started, server.getTickCount()); }
    }

    /** Every tick, before the transfer journal's early returns, so a blocked journal never keeps a warm-up alive. */
    void tick(MinecraftServer current) {
        server = current;
        if (registry.live().isEmpty()) { return; }
        registry.tick(current.getTickCount(), new RocketWarmupRegistry.Probe() {
            @Override public boolean online(UUID player) { return current.getPlayerList().getPlayer(player) != null; }
            @Override public boolean sourcePresent(RocketWarmupRegistry.Warmup warmup) {
                var level = RocketTransferEntities.level(current, warmup.sourceLevel());
                return level != null && level.getEntity(warmup.sourceEntity()) instanceof RocketEntity rocket && !rocket.isRemoved();
            }
            @Override public boolean ready(RocketWarmupRegistry.Warmup warmup) {
                return RocketTransferEntities.endAlreadyLoaded(current, warmup.destination());
            }
            @Override public void destinationReady(UUID player) {
                var online = current.getPlayerList().getPlayer(player);
                if (online != null) { online.displayClientMessage(Component.translatable(READY_MESSAGE), true); }
            }
        });
    }

    /** The governed dispatcher's successful ACTIVE margin refresh of T, in the same visit. */
    void handover(MinecraftServer current, UUID transfer) {
        server = current;
        registry.handover(transfer, current.getTickCount());
    }

    /** prepareLaunch's reservations plus every other rocket's warm-up region (design 7.4). */
    List<RocketRegion> reservations(List<RocketRegion> base, UUID requester) {
        var warming = registry.reservations(requester);
        return warming.isEmpty() ? base : Stream.concat(base.stream(), warming.stream()).toList();
    }

    boolean startedBy(UUID request) {
        return registry.startedBy(request);
    }

    /** R22 amendment 1, surface pads only: the station and named pads have one candidate each. */
    RocketPosition kept(RocketFlightData countdown, RocketEntity rocket) {
        var plan = countdown.plan().orElseThrow();
        if (plan.landingPad() != null || plan.destinationStation().isPresent()) { return null; }
        return registry.kept(countdown.logicalRocketId(), plan.destinationDimension(), rocket.snapshot().orElseThrow(),
                plan.destinationTarget()).orElse(null);
    }

    /** In-process fixtures clear while the Levels stay loaded, so each warm-up is released first; a stop drops them. */
    void clear() {
        var current = server;
        boolean live = current != null && current.isRunning() && current.isSameThread();
        registry.clear(live, current == null ? 0L : current.getTickCount());
        server = null;
        decision = null;
    }

    /** {@code /arce rocket inspect <logical>} without a journal entry: one read-only line; no ticket or load. */
    Optional<String> inspect(MinecraftServer current, UUID logical) {
        long now = current.getTickCount();
        return registry.warmup(logical).map(warmup -> "Warm-up logical=" + warmup.logical() + " state=" + warmup.state()
                + " request=" + warmup.request() + " transfer=" + (warmup.transfer() == null ? "none" : warmup.transfer())
                + " level=" + warmup.level() + " columns=" + readyColumns(current, warmup) + "/" + warmup.columns().size()
                + " age=" + warmup.age(now) + " remaining=" + warmup.remaining(now)
                + " player=" + (warmup.player() == null ? "none" : warmup.player()));
    }

    /** The transfer line's field: {@code BOUND remaining=<ticks>} until the handover, then {@code none}. */
    String bound(MinecraftServer current, UUID transfer) {
        return registry.boundTo(transfer).map(warmup -> "BOUND remaining=" + warmup.remaining(current.getTickCount())).orElse("none");
    }

    RocketWarmupRegistry registry() {
        return registry;
    }

    private static int readyColumns(MinecraftServer current, RocketWarmupRegistry.Warmup warmup) {
        var level = RocketTransferEntities.level(current, warmup.level());
        if (level == null) { return 0; }
        int y = warmup.destination().sourceOrigin().y();
        return (int) warmup.columns().stream().filter(column -> RocketTransferEntities.columnReady(level, column.x, column.z, y)).count();
    }

    private void launchEnded(MinecraftServer current, UUID logical, UUID transfer) {
        if (current == null) { return; }
        if (!current.isSameThread()) { current.execute(() -> launchEnded(current, logical, transfer)); return; }
        server = current;
        registry.launchEnded(logical, transfer, current.getTickCount());
    }

    /** One plain region ticket per column, distance 2, keyed by L, in the warm-up's own Level. */
    private final class LevelTickets implements RocketWarmupRegistry.Tickets {
        @Override public boolean present(ResourceLocation level) {
            return server != null && RocketTransferEntities.level(server, level) != null;
        }
        @Override public void add(ResourceLocation level, ChunkPos column, UUID logical) {
            RocketTransferEntities.level(server, level).getChunkSource().addRegionTicket(WARMUP_TICKET, column, TICKET_DISTANCE, logical);
        }
        @Override public void remove(ResourceLocation level, ChunkPos column, UUID logical) {
            RocketTransferEntities.level(server, level).getChunkSource().removeRegionTicket(WARMUP_TICKET, column, TICKET_DISTANCE, logical);
        }
    }
}
