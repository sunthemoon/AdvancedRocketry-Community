package io.github.sunthemoon.advancedrocketrycommunity.progression.classic.firstevent;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootPresence;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicAdvancementEvents;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicEvent;
import io.github.sunthemoon.advancedrocketrycommunity.progression.classic.ClassicMilestoneAdvancements;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationWriteBudget;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.warp.StationWarpService;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.advancements.Advancement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * W44 section 3.3: the world-first delivery service. One server-lifetime instance, created when the server has
 * started and cleared on stop; no static world state beyond the installed instance. Each END tick it runs at most one
 * step per kind from the source's tagged durable view: source event, then durable receipt, then award, then DELIVERED
 * (invariants I1 to I4). Progression never chooses a winner; it can only refuse or defer an election.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID)
public final class ClassicFirstEventDelivery {
    /** Source, progression and receipt-barrier retries run at most once per this many ticks. */
    static final long RETRY_TICKS = 200L;
    /** The automatic station acknowledgement backs off from 200 to at most this many ticks (W44 N2). */
    static final long MAX_ACK_BACKOFF_TICKS = 6_000L;
    /** Participant UUIDs looked up per tick by one kind's award cursor. */
    static final int CURSOR_PER_TICK = 16;
    private static volatile ClassicFirstEventDelivery current;

    private final MinecraftServer server;
    private final FirstEventSources sources;
    private final ClassicProgressionSavedData progression;
    private final java.nio.file.Path progressionFile;
    private final EnumMap<FirstEventKind, KindState> kinds = new EnumMap<>(FirstEventKind.class);
    private final FirstEventGrantQueue queue = new FirstEventGrantQueue();
    private final Set<String> warned = new HashSet<>();
    /** The receipt barrier of the whole progression root: at most one attempt per 200 ticks while a kind is pending. */
    private final FirstEventRetryStreak barrier = FirstEventRetryStreak.fixed(RETRY_TICKS);

    /** Session state of one kind. */
    static final class KindState {
        final FirstEventKind kind;
        String hold;
        boolean permanent;
        FirstEvent ready;
        List<UUID> cursor = List.of();
        int cursorIndex;
        boolean cursorDone;
        boolean deliveredRequested;
        boolean deliveredSeen;
        /** The station acknowledgement: 200 ticks after the first failure, doubling to at most 6,000 (N2). */
        final FirstEventRetryStreak acknowledgement = new FirstEventRetryStreak(RETRY_TICKS, MAX_ACK_BACKOFF_TICKS);

        KindState(FirstEventKind kind) {
            this.kind = kind;
        }
    }

    private ClassicFirstEventDelivery(MinecraftServer server, FirstEventSources sources,
                                      ClassicProgressionSavedData progression, java.nio.file.Path progressionFile) {
        this.server = server;
        this.sources = sources;
        this.progression = progression;
        this.progressionFile = progressionFile;
        for (FirstEventKind kind : FirstEventKind.values()) {
            kinds.put(kind, new KindState(kind));
        }
    }

    // ---- Source API: refreshed on the server thread before each potentially first action -------------------------

    /** Section 3.3, step 4. Before this session's service is initialized, and from any other thread, DEFER. */
    public static FirstEventDisposition disposition(MinecraftServer server, FirstEventKind kind,
                                                    FirstEventSourceView source, FirstEventOutbox ram) {
        Objects.requireNonNull(server, "server");
        ClassicFirstEventDelivery service = current;
        if (!server.isSameThread() || service == null || service.server != server) {
            return FirstEventDisposition.DEFER;
        }
        return FirstEventElection.disposition(kind, source, ram, service.progression);
    }

    /** A source committed or published a change of {@code kind}; the next END tick re-reads its durable view. */
    public static void sourceChanged(MinecraftServer server, FirstEventKind kind) {
        Objects.requireNonNull(server, "server");
        Objects.requireNonNull(kind, "kind");
        ClassicFirstEventDelivery service = current;
        if (service != null && service.server == server) {
            service.kinds.get(kind).acknowledgement.wake();
        }
    }

    /**
     * A detached service over detached roots for the isolated A1 GameTest batch (W44 section 10); it is never installed,
     * so the world's sources and elections never see it. The fixture drives {@link #tick()} and the login methods.
     */
    public static ClassicFirstEventDelivery detached(MinecraftServer server, FirstEventSources sources,
                                                     ClassicProgressionSavedData progression,
                                                     java.nio.file.Path progressionFile) {
        if (!(server instanceof net.minecraft.gametest.framework.GameTestServer)) {
            throw new IllegalStateException("A detached first-event delivery is a GameTest fixture only");
        }
        return new ClassicFirstEventDelivery(server, sources, progression, progressionFile);
    }

    /** The disposition of {@code kind} against this service's progression store, as the sources ask before electing. */
    public FirstEventDisposition disposition(FirstEventKind kind, FirstEventSourceView source, FirstEventOutbox ram) {
        return server.isSameThread() ? FirstEventElection.disposition(kind, source, ram, progression)
                : FirstEventDisposition.DEFER;
    }

    /** The installed service of {@code server}, for the status command and GameTests. */
    static Optional<ClassicFirstEventDelivery> installed(MinecraftServer server) {
        ClassicFirstEventDelivery service = current;
        return service != null && service.server == server ? Optional.of(service) : Optional.empty();
    }

    // ---- Lifecycle --------------------------------------------------------------------------------------------------

    /**
     * Startup order (section 3.3, steps 1 and 2): the pre-start validators already accepted every managed root; obtain
     * both sources and the progression store, which classifies each root's presence when it is first obtained.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        FirstEventCutSeams.validateAtStartup(server);
        StationRegistrySavedData.get(server);
        RocketTransferSavedData.get(server);
        current = new ClassicFirstEventDelivery(server, FirstEventSources.world(server),
                ClassicProgressionSavedData.get(server),
                ManagedRootSupplier.dataFile(server, ManagedSavedDataType.CLASSIC_PROGRESSION));
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        installed(event.getServer()).ifPresent(ClassicFirstEventDelivery::clear);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        installed(event.getServer()).ifPresent(service -> {
            service.clear();
            current = null;
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            installed(event.getServer()).ifPresent(ClassicFirstEventDelivery::tick);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            installed(player.getServer()).ifPresent(service -> service.onLogin(player));
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getServer() != null) {
            installed(player.getServer()).ifPresent(service -> service.onLogout(player));
        }
    }

    private void clear() {
        queue.clear();
        kinds.values().forEach(state -> {
            state.cursor = List.of();
            state.cursorDone = false;
        });
    }

    // ---- The END tick -------------------------------------------------------------------------------------------------

    /** One END tick of this service: at most one step per kind, then the bounded deferred queue. */
    public void tick() {
        long now = server.getTickCount();
        for (KindState state : kinds.values()) {
            step(state, now);
        }
        queue.tick(now, (player, kind, connection) -> examine(player, kind, connection));
    }

    /** At most one step of {@code state}'s kind, from its tagged durable view (section 3.3 table). */
    private void step(KindState state, long now) {
        FirstEventKind kind = state.kind;
        if (!progression.operational()) {
            hold(state, progression.presence() == ManagedRootPresence.UNREADABLE ? "ROOT_UNREADABLE" : "ROOT_BLOCKED",
                    false);
            return;
        }
        FirstEventSourceView view = view(kind);
        if (!view.known()) {
            hold(state, view.status() == FirstEventSourceView.Status.SOURCE_UNCONFIRMED ? "SOURCE_UNCONFIRMED"
                    : view.reason().name(), false);
            return;
        }
        Optional<FirstEventElection.Hold> permanent = FirstEventElection.hold(kind, view, progression);
        if (permanent.isPresent()) {
            hold(state, permanent.orElseThrow().name(), true);
            return;
        }
        release(state);
        if (view.outbox().isEmpty()) {
            return;
        }
        FirstEvent event = view.outbox().event();
        if (progression.validatedAbsent(kind)) {
            // I1: the receipt is created only from the source event in its durable view.
            progression.insert(event);
            barrier.wake();
        }
        if (progression.receiptPending(kind)) {
            if (!barrier.due(now)) {
                return;
            }
            if (!barrierStep(progression, progressionFile, FirstEventCutSeams.progressionCommitter(server),
                    FirstEventCutSeams.directoryForce(server), barrier, now)) {
                FirstEventCutSeams.boundary(server, FirstEventCutSeams.RECEIPT_BARRIER_FAILED, kind, event);
            }
            if (progression.receiptPending(kind)) {
                return;
            }
        }
        if (!event.equals(progression.confirmedReceipt(kind).orElse(null))) {
            return;
        }
        if (!event.equals(state.ready)) {
            // Readiness: start the one-time bounded cursor and wake this kind's deferred entries.
            state.ready = event;
            state.cursor = event.participants();
            state.cursorIndex = 0;
            state.cursorDone = false;
            FirstEventCutSeams.boundary(server, FirstEventCutSeams.RECEIPT_CONFIRMED, kind, event);
            queue.wake(kind, now);
        }
        if (!state.cursorDone) {
            advanceCursor(state, now);
            if (!state.cursorDone) {
                return;
            }
            FirstEventCutSeams.boundary(server, FirstEventCutSeams.AWARDS_DISPATCHED, kind, event);
        }
        if (view.outbox().state() == FirstEventOutbox.State.ELECTED) {
            requestDelivered(state, event, now);
        } else if (state.deliveredRequested && !state.deliveredSeen) {
            state.deliveredSeen = true;
            FirstEventCutSeams.boundary(server, FirstEventCutSeams.DELIVERED_PUBLISHED, kind, event);
        }
    }

    private FirstEventSourceView view(FirstEventKind kind) {
        return sources.view(kind);
    }

    /**
     * The receipt barrier for every pending kind; one ERROR with its stack trace per failure streak, then only a count,
     * then one INFO on recovery (N2). True when the captured receipts are confirmed.
     */
    static boolean barrierStep(ClassicProgressionSavedData progression, java.nio.file.Path file,
                               io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile
                                       .Committer committer,
                               io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy
                                       .DirectoryForce force, FirstEventRetryStreak streak, long now) {
        String pending = String.join(",", java.util.Arrays.stream(FirstEventKind.values())
                .filter(progression::receiptPending).map(FirstEventKind::serializedName).toList());
        var result = progression.barrier(file, committer, force);
        if (result.confirmed()) {
            int ended = streak.succeeded();
            if (ended > 0) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_PROGRESSION_RECEIPT_BARRIER_RECOVERED failures={}", ended);
            }
            return true;
        }
        if (result.stage() == ClassicProgressionSavedData.Barrier.Stage.UNAVAILABLE) {
            return false;
        }
        if (streak.failed(now)) {
            AdvancedRocketryCommunity.LOGGER.error("ARCE_PROGRESSION_RECEIPT_BARRIER_FAILED kinds={} stage={}",
                    pending, result.stage().name().toLowerCase(java.util.Locale.ROOT), result.failure());
        }
        return false;
    }

    /** I3: DELIVERED is requested only after the receipt is durable and the award pass has run. */
    private void requestDelivered(KindState state, FirstEvent event, long now) {
        if (state.kind == FirstEventKind.FIRST_MOON_LANDING) {
            if (!state.deliveredRequested && sources.requestMoonDelivered(event)) {
                state.deliveredRequested = true;
            }
            return;
        }
        if (!state.acknowledgement.due(now)) {
            return;
        }
        StationWriteBudget budget = StationWarpRuntime.service().map(StationWarpService::writeBudget)
                .orElse(StationWriteBudget.unbounded());
        if (!budget.ready(now, sources.stationRecords())) {
            return;
        }
        state.deliveredRequested = true;
        var written = sources.acknowledgeWarp(event, progression.confirmedReceipt(state.kind).orElse(null),
                new AcknowledgementLog(state.kind, event, state.acknowledgement));
        budget.record(now);
        acknowledged(state.kind, written == StationRegistrySavedData.CheckedUpdate.COMMITTED
                || written == StationRegistrySavedData.CheckedUpdate.UNCHANGED, state.acknowledgement, now);
        if (written == StationRegistrySavedData.CheckedUpdate.OUTCOME_UNKNOWN) {
            FirstEventCutSeams.boundary(server, FirstEventCutSeams.ACKNOWLEDGEMENT_UNKNOWN, state.kind, event);
        }
    }

    /**
     * After one automatic acknowledgement attempt: success ends the streak with one recovery INFO; a failure is counted
     * and spaces the next attempt. The writer's diagnostics already logged the streak's first failure.
     */
    static void acknowledged(FirstEventKind kind, boolean committed, FirstEventRetryStreak streak, long now) {
        if (committed) {
            int ended = streak.succeeded();
            if (ended > 0) {
                AdvancedRocketryCommunity.LOGGER.info("ARCE_FIRST_EVENT_ACK_RECOVERED kind={} failures={}",
                        kind.serializedName(), ended);
            }
        } else {
            streak.failed(now);
        }
    }

    /** The automatic acknowledgement logs once per failure streak; named station operations keep their per-failure log. */
    static final class AcknowledgementLog implements io.github.sunthemoon.advancedrocketrycommunity.station
            .persistence.StationCheckedWriter.Diagnostics {
        private final FirstEventKind kind;
        private final FirstEvent event;
        private final FirstEventRetryStreak streak;

        AcknowledgementLog(FirstEventKind kind, FirstEvent event, FirstEventRetryStreak streak) {
            this.kind = kind;
            this.event = event;
            this.streak = streak;
        }

        private void failed(Throwable failure) {
            if (streak.failures() == 0) {
                AdvancedRocketryCommunity.LOGGER.error("ARCE_FIRST_EVENT_ACK_WRITE_FAILED kind={} event={}",
                        kind.serializedName(), event.eventId(), failure);
            }
        }

        @Override public void beforeReplace(Throwable failure) { failed(failure); }

        @Override public void replaceFailed(boolean filePresent, Throwable failure) { failed(failure); }

        @Override public void unknown(java.nio.file.Path file, Throwable failure) {
            AdvancedRocketryCommunity.LOGGER.error(
                    "ARCE_STATION_UPDATE_OUTCOME_UNKNOWN kind={} event={} file={}; updates disabled until restart",
                    kind.serializedName(), event.eventId(), file, failure);
        }

        @Override public void replacedDespiteError(Throwable failure) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_STATION_UPDATE_REPLACED_DESPITE_ERROR kind={} event={}",
                    kind.serializedName(), event.eventId(), failure);
        }
    }

    /** One WARN per kind, reason and start; a temporary hold logs one INFO when it clears. */
    private void hold(KindState state, String reason, boolean permanent) {
        state.hold = reason;
        state.permanent = permanent;
        if (warned.add(state.kind.serializedName() + "/" + reason)) {
            AdvancedRocketryCommunity.LOGGER.warn("ARCE_FIRST_EVENT_HELD kind={} reason={}",
                    state.kind.serializedName(), reason);
            if (permanent) {
                FirstEventCutSeams.boundary(server, FirstEventCutSeams.HELD, state.kind, null);
            } else if (reason.startsWith("ROOT_")) {
                FirstEventCutSeams.boundary(server, FirstEventCutSeams.FIRST_STEP, state.kind, null);
            }
        }
    }

    private void release(KindState state) {
        if (state.hold != null && !state.permanent) {
            AdvancedRocketryCommunity.LOGGER.info("ARCE_FIRST_EVENT_HOLD_CLEARED kind={} reason={}",
                    state.kind.serializedName(), state.hold);
        }
        state.hold = null;
        state.permanent = false;
    }

    // ---- Awards -------------------------------------------------------------------------------------------------------

    /** Looks up at most {@link #CURSOR_PER_TICK} participants: eligible ones are granted, ineligible ones queued. */
    private void advanceCursor(KindState state, long now) {
        int end = Math.min(state.cursor.size(), state.cursorIndex + CURSOR_PER_TICK);
        for (; state.cursorIndex < end; state.cursorIndex++) {
            ServerPlayer player = server.getPlayerList().getPlayer(state.cursor.get(state.cursorIndex));
            if (player != null && !grantNow(player, state.kind)) {
                defer(player, state.kind, now);
            }
        }
        state.cursorDone = state.cursorIndex >= state.cursor.size();
    }

    /** Section 3.5: the immediate login grant, or one pending bit for this connection. */
    public void onLogin(ServerPlayer player) {
        long now = server.getTickCount();
        for (FirstEventKind kind : FirstEventKind.values()) {
            if (participantOfDecodedReceipt(player.getUUID(), kind) && !grantNow(player, kind)) {
                defer(player, kind, now);
            }
        }
    }

    /** Every disconnect removes that connection's pending entries; nothing is kept for offline players. */
    public void onLogout(ServerPlayer player) {
        queue.removeConnection(player.getUUID(), connectionOf(player));
    }

    /** Only a strictly decoded immutable receipt can make a connected player a pending participant. */
    private boolean participantOfDecodedReceipt(UUID player, FirstEventKind kind) {
        return progression.receipt(kind).filter(receipt -> receipt.participant(player)).isPresent();
    }

    private void defer(ServerPlayer player, FirstEventKind kind, long now) {
        if (participantOfDecodedReceipt(player.getUUID(), kind) && !done(player, kind)) {
            queue.add(player.getUUID(), kind, connectionOf(player), now);
        }
    }

    private FirstEventGrantQueue.Outcome examine(UUID uuid, FirstEventKind kind, int connection) {
        ServerPlayer player = server.getPlayerList().getPlayer(uuid);
        if (player == null || connectionOf(player) != connection || !participantOfDecodedReceipt(uuid, kind)
                || done(player, kind)) {
            return FirstEventGrantQueue.Outcome.SETTLED;
        }
        return grantNow(player, kind) ? FirstEventGrantQueue.Outcome.SETTLED : FirstEventGrantQueue.Outcome.RETRY;
    }

    /**
     * Section 3.5: grants only with a durable receipt, no hold, the source's durable view ELECTED or DELIVERED with the
     * same event, and an eligible participant of the immutable receipt. True when the advancement is complete.
     */
    public boolean grantNow(ServerPlayer player, FirstEventKind kind) {
        if (done(player, kind)) {
            return true;
        }
        KindState state = kinds.get(kind);
        FirstEvent receipt = progression.confirmedReceipt(kind).orElse(null);
        if (receipt == null || state.hold != null || !receipt.participant(player.getUUID())
                || !ClassicAdvancementEvents.eligible(player)) {
            return false;
        }
        FirstEventSourceView view = view(kind);
        if (!view.known() || !receipt.equals(view.outbox().event())) {
            return false;
        }
        ClassicAdvancementEvents.TRIGGER.trigger(player, event(kind));
        return done(player, kind);
    }

    public boolean done(ServerPlayer player, FirstEventKind kind) {
        Advancement advancement = server.getAdvancements().getAdvancement(advancement(kind));
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    static ResourceLocation advancement(FirstEventKind kind) {
        return ResourceLocation.tryParse((kind == FirstEventKind.FIRST_MOON_LANDING
                ? ClassicMilestoneAdvancements.Milestone.ONE_SMALL_STEP
                : ClassicMilestoneAdvancements.Milestone.FIRST_STATION_WARP).id());
    }

    static ClassicEvent event(FirstEventKind kind) {
        return kind == FirstEventKind.FIRST_MOON_LANDING ? ClassicEvent.FIRST_MOON_LANDING : ClassicEvent.FIRST_STATION_WARP;
    }

    private static int connectionOf(ServerPlayer player) {
        return System.identityHashCode(player.connection);
    }

    // ---- Status (read-only) -----------------------------------------------------------------------------------------

    /** The number of pending deferred entries of {@code kind}. */
    public int deferred(FirstEventKind kind) {
        return queue.size(kind);
    }

    /** Section 3.3 diagnostics; never prints unconfirmed RAM as durable EMPTY. */
    public List<String> status() {
        List<String> lines = new java.util.ArrayList<>();
        lines.add(String.format(java.util.Locale.ROOT,
                "progression root=%s stations=%s journal=%s barrier_failures=%d next_barrier=%s deferred=%d",
                progression.presence(), sources.stationPresence(), sources.journalPresence(), barrier.failures(),
                barrier.next() == 0L ? "none" : Long.toString(barrier.next()), queue.size()));
        for (KindState state : kinds.values()) {
            FirstEventSourceView view = view(state.kind);
            String source = view.known() ? "KNOWN:" + view.outbox().state() : view.status() + ":" + view.reason();
            String event = view.knownEvent().map(value -> value.eventId().toString()).orElse("none");
            String receipt = !progression.operational() ? "UNAVAILABLE"
                    : progression.receiptPending(state.kind) ? "PENDING"
                    : progression.confirmedReceipt(state.kind).isPresent() ? "CONFIRMED" : "ABSENT";
            lines.add(String.format(java.util.Locale.ROOT,
                    "kind=%s source=%s event=%s receipt=%s hold=%s retry=%s failures=%d deferred_online=%d"
                            + " participants=%d",
                    state.kind.serializedName(), source, event, receipt, state.hold == null ? "none" : state.hold,
                    state.acknowledgement.next() == 0L ? "none" : Long.toString(state.acknowledgement.next()),
                    state.acknowledgement.failures(),
                    queue.size(state.kind), view.knownEvent().map(value -> value.participants().size()).orElse(0)));
        }
        return lines;
    }
}
