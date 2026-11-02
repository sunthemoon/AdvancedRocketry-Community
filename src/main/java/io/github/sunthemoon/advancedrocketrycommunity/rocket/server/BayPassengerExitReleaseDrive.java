package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.BayReceiptTestHooks;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.EscrowTestEndpoints;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.slf4j.Logger;

/**
 * W254's client stand-in for one facility-owned rider: what a real client sends after a held exit (teleport acceptance,
 * one move packet per tick, the landing packet), plus the read-only measurements the native harness checks. It runs only
 * for a drive that a gated {@link BayPassengerExitReleaseTest} command armed, on a release-test dedicated server whose
 * players are all facility-owned; it never touches the exit service's state, budgets or decisions.
 */
final class BayPassengerExitReleaseDrive {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Descent packets are three blocks apart, as W242's fixture used; a drive never sends more than this many. */
    static final double STEP = 3.0D;
    static final int MAX_PACKETS = 64;
    /** A frozen server thread lets the harness kill the process at an exact point; never longer than this. */
    static final long FREEZE_MILLIS = 30_000L;
    private static final Map<MinecraftServer, Drive> DRIVES = new IdentityHashMap<>();
    private static Field tickets;
    private static boolean listening;

    private BayPassengerExitReleaseDrive() { }

    /**
     * Called by a gated command once it passed the release-test gate, so an ordinary server never gets these listeners.
     * They are added long after the exit service's own subscribers at the same priority, so they see the final outcome.
     */
    static synchronized void listen() {
        if (listening) { return; }
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, TickEvent.ServerTickEvent.class,
                BayPassengerExitReleaseDrive::tick);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingAttackEvent.class, BayPassengerExitReleaseDrive::attack);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingFallEvent.class, BayPassengerExitReleaseDrive::fall);
        // Read-only trace of the exit service's own observation points: after its player-tick and server-END samples.
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.PlayerTickEvent.class,
                BayPassengerExitReleaseDrive::afterPlayerSample);
        MinecraftForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TickEvent.ServerTickEvent.class,
                BayPassengerExitReleaseDrive::afterServerSample);
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent stopped) -> forget(stopped.getServer()));
        listening = true;
    }

    /** Forgets this server's drive: when it stops, or after a GameTest batch that used one (W257-R6). */
    static synchronized void forget(MinecraftServer server) { DRIVES.remove(server); }

    static final class Drive {
        final ServerPlayer player;
        final UUID logical;
        int acked = Integer.MIN_VALUE;
        Double landY;
        int steps, sent;
        boolean descending, capturing;
        RocketEntity inputRocket;
        Measure inputBefore;
        long inputTick;
        BayPassengerExitState.Slot tracked;
        int trace;
        String then;
        float fall = -1;
        final List<String> attacks = new ArrayList<>();
        /** W262: the client's fall per packet; a GameTest may arm a faster one, a native drive keeps {@link #STEP}. */
        double step = STEP;
        /**
         * W262 (R288): the artificial same-tick pair, two packets bunched into one server tick, which no steadily sending
         * client produces. Off by default: a real client's landing packet follows its last airborne one a full tick later.
         */
        boolean pair;
        /** The tick of the last airborne packet, its height, vanilla's temporary step at PlayerTick END, and the landing. */
        long staged = -1, landed = -1;
        double stagedY = Double.NaN, physicsY = Double.NaN;
        Drive(ServerPlayer player, UUID logical) { this.player = player; this.logical = logical; }
    }

    /** The drive of this rider and rocket, created on first use; a different rider instance starts a new one. */
    static synchronized Drive drive(ServerPlayer player, UUID logical) {
        var server = Objects.requireNonNull(player.getServer());
        var drive = DRIVES.get(server);
        if (drive == null || drive.player != player || !drive.logical.equals(logical)) {
            drive = new Drive(player, logical);
            DRIVES.put(server, drive);
        }
        return drive;
    }

    /** One read-only observation: chunks, tickets, the authority stores the exit must not change, and the rider. */
    record Measure(int chunks, Map<String, String> tickets, Object transfer, String digest, Object snapshot, Object passengers,
                   Object fuel, List<?> receipts, int pending, int counter, double y, float health) {
        static Measure take(BayPassengerExitReleaseTest.View view) {
            var level = view.server().overworld();
            var rocket = view.rocket();
            var journal = view.journal();
            var generation = view.exits() == null || view.rider() == null ? null : view.exits().motion.generation(view.rider());
            return new Measure(level.getChunkSource().getLoadedChunksCount(), BayPassengerExitReleaseDrive.tickets(level),
                    journal == null || view.record() == null ? null : journal.find(view.record().transferId()).orElse(null),
                    journal == null ? null : journal.logicalGuard(view.logical()).map(guard -> guard.recordDigest()).orElse(null),
                    rocket == null ? null : rocket.snapshot().orElse(null),
                    rocket == null ? null : rocket.flightData().map(data -> data.passengers()).orElse(null),
                    rocket == null ? null : rocket.flightData().map(data -> data.fuel()).orElse(null),
                    RocketRuntime.installedBays().map(bays -> bays.sessionForTests().receipts().stream()
                            .filter(receipt -> receipt.rocket().equals(view.logical())).toList()).orElse(List.of()),
                    BayReceiptTestHooks.authority(view.server()).pendingCount(),
                    generation == null ? -1 : generation.counter(),
                    view.rider() == null ? Double.NaN : view.rider().getY(), view.rider() == null ? -1 : view.rider().getHealth());
        }
        /** Identity for the stored record and snapshot, equality for the rest: an exit replaces or rewrites none of them. */
        boolean sameAuthority(Measure after) {
            return transfer == after.transfer && Objects.equals(digest, after.digest) && snapshot == after.snapshot
                    && Objects.equals(passengers, after.passengers) && Objects.equals(fuel, after.fuel)
                    && receipts.equals(after.receipts) && pending == after.pending;
        }
        String ticketChange(Measure after) {
            if (tickets.equals(after.tickets)) { return "same"; }
            var changed = new TreeMap<String, String>();
            tickets.forEach((key, value) -> { if (!value.equals(after.tickets.get(key))) { changed.put(key, value + "->" + after.tickets.get(key)); } });
            after.tickets.forEach((key, value) -> { if (!tickets.containsKey(key)) { changed.put(key, "absent->" + value); } });
            String detail = changed.toString().replace(' ', '_');
            return "changed:" + changed.size() + ":" + (detail.length() > 400 ? detail.substring(0, 400) + "..." : detail);
        }
    }

    /** DistanceManager.tickets, read-only and resolved through Forge's name remapping in both dev and packaged runs. */
    static synchronized Map<String, String> tickets(ServerLevel level) {
        try {
            if (tickets == null) { tickets = ObfuscationReflectionHelper.findField(DistanceManager.class, "f_140761_"); }
            var map = (Map<?, ?>) tickets.get(level.getChunkSource().chunkMap.getDistanceManager());
            var snapshot = new TreeMap<String, String>();
            map.forEach((key, value) -> snapshot.put(String.valueOf(key), String.valueOf(value)));
            return snapshot;
        } catch (ReflectiveOperationException | RuntimeException failure) {
            throw new IllegalStateException("Ticket observation unavailable", failure);
        }
    }

    /** Like a client, accept the newest teleport once; every teleport since the last acceptance left one awaited. */
    static void acknowledge(Drive drive, BayPassengerExitService exits) {
        var generation = exits.motion.generation(drive.player);
        if (generation != null && generation.counter() != drive.acked) {
            drive.player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(generation.counter()));
            drive.acked = generation.counter();
        }
    }

    /** Reports a completed exit command; a frozen exit then holds the server thread so the harness can kill it there. */
    static void left(BayPassengerExitReleaseTest.View view, String mode, Measure before, Measure after, boolean freeze) {
        var player = view.rider();
        boolean detached = player != null && player.getVehicle() == null;
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_LEFT logical={} mode={} detached={} counter={}>{} chunks={}>{} tickets={} authority={} "
                        + "riderY={}>{} riderHealth={}>{} {}", view.logical(), mode, detached, before.counter(), after.counter(), before.chunks(),
                after.chunks(), before.ticketChange(after), before.sameAuthority(after) ? "same" : "changed", before.y(), after.y(),
                before.health(), after.health(), view.describe());
        if (freeze) {
            var slot = view.exits() == null ? null : view.exits().state.live.get(player);
            var descent = slot == null ? null : slot.descent;
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_FROZEN logical={} window={} accepted={} counter={} tick={}", view.logical(),
                    descent == null ? "none" : descent.closed ? "closed" : "open", descent != null && descent.corrections.accepted,
                    after.counter(), view.server().getTickCount());
            long until = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(FREEZE_MILLIS);
            try {
                while (System.nanoTime() < until) { Thread.sleep(100); }
            } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_THAWED logical={}", view.logical());
        } else if (detached) {
            var drive = drive(player, view.logical());
            acknowledge(drive, view.exits());
            var slot = view.exits() == null ? null : view.exits().state.live.get(player);
            if (slot != null && slot.descent != null) { drive.tracked = slot; drive.trace = TRACE_TICKS; }
        }
    }

    /**
     * A dedicated server runs console commands after the Level tick, so the sneak input is honoured by the next tick's
     * passenger tick ({@code Player.rideTick}); report at the first END that sees the rider detached, or give up.
     */
    static void awaitInput(BayPassengerExitReleaseTest.View view, Measure before) {
        var drive = drive(view.rider(), view.logical());
        drive.inputRocket = view.rocket(); drive.inputBefore = before; drive.inputTick = view.server().getTickCount();
    }

    /** Ticks after an exit whose exit-service observations are traced; the closing observation is always reported. */
    static final int TRACE_TICKS = 6;
    static final int INPUT_TICKS = 5;

    private static void afterPlayerSample(TickEvent.PlayerTickEvent event) {
        if (event.player instanceof ServerPlayer player && player.getServer() != null) {
            Drive drive;
            synchronized (BayPassengerExitReleaseDrive.class) { drive = DRIVES.get(player.getServer()); }
            // Read-only: where vanilla's temporary step put the rider between its last airborne and its landing packet.
            if (drive != null && drive.player == player && event.phase == TickEvent.Phase.END
                    && drive.staged == player.getServer().getTickCount()) { drive.physicsY = player.getY(); }
            observe(player.getServer(), player, "PLAYER_" + event.phase.name());
        }
    }

    private static void afterServerSample(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) { return; }
        Drive drive;
        synchronized (BayPassengerExitReleaseDrive.class) { drive = DRIVES.get(event.getServer()); }
        if (drive != null) {
            observe(event.getServer(), drive.player, "SERVER_END");
            if (drive.trace > 0) { drive.trace--; }
        }
    }

    /** Read-only: the rider's sample at this point, and why the tracked offer ended once it ends. */
    private static void observe(MinecraftServer server, ServerPlayer player, String point) {
        Drive drive;
        synchronized (BayPassengerExitReleaseDrive.class) { drive = DRIVES.get(server); }
        if (drive == null || drive.player != player || drive.tracked == null || !EscrowTestEndpoints.enabled(server)) { return; }
        var exits = RocketRuntime.passengerExits().orElse(null);
        if (exits == null) { return; }
        var slot = drive.tracked;
        long tick = server.getTickCount();
        var now = BayPassengerExitMotion.sample(player, tick);
        var generation = exits.motion.generation(player);
        boolean open = exits.state.live.get(player) == slot && !slot.descent.closed;
        if (drive.trace > 0) {
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_TRACE logical={} point={} tick={} now={} counter={} open={}", drive.logical, point, tick,
                    sample(now), generation == null ? -1 : generation.counter(), open);
        }
        if (!open) {
            var descent = slot.descent;
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_CLOSED logical={} point={} tick={} slot={} closed={} authority={} lease={} deadline={} "
                            + "counter={} window={}:{}:{} last={} now={} contact={} inspected={} first={} usage={}", drive.logical, point,
                    tick, exits.state.live.get(player) == slot ? "same" : "removed", descent.closed, slot.permit.authority(),
                    slot.permit.lease.getAsBoolean(), descent.deadline, generation == null ? -1 : generation.counter(),
                    descent.corrections.current == null ? -1 : descent.corrections.current.counter(), descent.corrections.accepted,
                    descent.corrections.closed, sample(descent.continuity.last), sample(now), slot.world.contact, slot.world.inspected,
                    String.valueOf(slot.world.firstContact).replace(' ', '_'), String.valueOf(exits.budget.usage()).replace(' ', '_'));
            drive.tracked = null;
        }
    }

    private static String sample(BayPassengerExitMotion.Sample sample) {
        return sample == null ? "none" : String.format(java.util.Locale.ROOT, "%d:%.4f:%.4f:%.4f:%.4f:%s:%s", sample.tick(),
                sample.position().x, sample.position().y, sample.position().z, sample.velocity().y, sample.ordinary(), sample.airborne());
    }

    /** Arms a descent of {@code steps} packets, or to the recorded pad when {@code steps} is zero. */
    static void descend(ServerPlayer player, UUID logical, int steps) {
        var drive = drive(player, logical);
        if (steps == 0 && drive.landY == null) { throw new IllegalStateException("No landing pad recorded for this drive"); }
        drive.steps = steps; drive.sent = 0; drive.descending = true; drive.step = STEP; drive.pair = false;
        drive.staged = -1; drive.landed = -1; drive.stagedY = Double.NaN; drive.physicsY = Double.NaN;
        if (steps > 0) { drive.landY = null; }
    }

    /** W262: a GameTest descent at {@code step} blocks per packet, or as the named artificial same-tick pair. */
    static void descend(ServerPlayer player, UUID logical, int steps, double step, boolean pair) {
        if (!(step > 0) || step > 4) { throw new IllegalArgumentException("A client step must be in (0, 4]: " + step); }
        descend(player, logical, steps);
        var drive = drive(player, logical);
        drive.step = step; drive.pair = pair;
    }

    private static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer() == null) { return; }
        var server = event.getServer();
        Drive drive;
        synchronized (BayPassengerExitReleaseDrive.class) { drive = DRIVES.get(server); }
        // W257-R6: no lookups or entity scans unless an input exit, a stimulus or a descent is armed.
        if (drive == null || drive.inputRocket == null && drive.then == null && !drive.descending
                || !EscrowTestEndpoints.enabled(server)) { return; }
        var view = BayPassengerExitReleaseTest.View.of(server, drive.logical);
        if (drive.inputRocket != null) {
            if (view.rider() == drive.player && drive.player.getVehicle() == drive.inputRocket
                    && server.getTickCount() - drive.inputTick < INPUT_TICKS) { return; }
            var before = drive.inputBefore;
            drive.inputRocket = null; drive.inputBefore = null;
            left(view, "input", before, Measure.take(view), false);
            return;
        }
        if (drive.then != null) {
            String then = drive.then;
            drive.then = null;
            if (then.equals("move")) { BayPassengerExitReleaseTest.move(view); } else { firstTick(drive, view, then.equals("first-revoked")); }
            return;
        }
        if (drive.descending) { step(drive, view); }
    }

    /** Arms one stimulus for this tick's END, which on a dedicated server follows the exit command directly. */
    static void then(ServerPlayer player, UUID logical, String then) { drive(player, logical).then = then; }

    /**
     * W242's first-tick landing, by packets: a 3x3 pad three to four blocks under the rider, one airborne packet that
     * falls more than three blocks, then the landing packet, all before vanilla's tracker correction and before the
     * server-side player tick moves the rider. {@code revoke} first sends two same-Level teleports a tenth of a block
     * east, two counter steps inside the correction window, which allows only one.
     */
    private static void firstTick(Drive drive, BayPassengerExitReleaseTest.View view, boolean revoke) {
        var player = drive.player;
        if (view.rider() != player || player.getVehicle() != null || view.exits() == null) {
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_DESCENT logical={} stopped=rider sent=0 {}", drive.logical, view.describe());
            return;
        }
        int top = (int) Math.floor(player.getY()) - 3;
        double airborne = top + .05D;
        if (player.getY() - airborne <= 3 || player.getY() - top > 4) { throw new IllegalStateException("No first-tick landing geometry"); }
        var pad = net.minecraft.core.BlockPos.containing(player.getX(), top - 1, player.getZ());
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                view.server().overworld().setBlockAndUpdate(pad.offset(x, 0, z), net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
        }
        if (revoke) {
            int before = view.exits().motion.generation(player).counter();
            for (int n = 0; n < 2; n++) {
                player.connection.teleport(player.getX() + .1D, player.getY(), player.getZ(), player.getYRot(), player.getXRot());
            }
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_TELEPORTED logical={} counter={}>{} teleports=2 {}", drive.logical, before,
                    view.exits().motion.generation(player).counter(), view.describe());
        }
        acknowledge(drive, view.exits());
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX(), airborne, player.getZ(), false));
        drive.landY = (double) top;
        // Both packets precede the exit's first server-side player tick; W242's first-tick shape, not a descent.
        drive.staged = view.server().getTickCount(); drive.stagedY = airborne; drive.physicsY = Double.NaN;
        land(drive, view);
    }

    private static void step(Drive drive, BayPassengerExitReleaseTest.View view) {
        var player = drive.player;
        if (view.rider() != player || !player.isAlive() || view.exits() == null) {
            drive.descending = false;
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_DESCENT logical={} stopped=rider sent={} {}", drive.logical, drive.sent, view.describe());
            return;
        }
        acknowledge(drive, view.exits());
        if (drive.landY != null && player.getY() <= drive.landY + .25D + 1e-6) { land(drive, view); return; }
        if (drive.sent >= MAX_PACKETS) {
            drive.descending = false;
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_DESCENT logical={} stopped=packets sent={} {}", drive.logical, drive.sent, view.describe());
            return;
        }
        double next = drive.landY == null ? player.getY() - drive.step : Math.max(drive.landY + .25D, player.getY() - drive.step);
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX(), next, player.getZ(), false));
        drive.sent++;
        if (drive.landY != null && next <= drive.landY + .25D + 1e-6) {
            // W262 (R288): a real client sends one move packet per tick. Its last airborne packet stays less than one
            // server fall step above the pad; the server then runs a full tick, whose temporary step reaches the pad,
            // and the landing packet follows at the next tick. Only the named artificial pair lands at once.
            drive.staged = view.server().getTickCount(); drive.stagedY = next; drive.physicsY = Double.NaN;
            if (drive.pair) { land(drive, view); return; }
        }
        if (drive.landY == null && drive.sent >= drive.steps) {
            drive.descending = false;
            LOGGER.info("ARCE_RELEASE_BAY_EXIT_DESCENT logical={} stopped=steps sent={} airborne={} {}", drive.logical, drive.sent,
                    !player.onGround(), view.describe());
        }
    }

    private static void land(Drive drive, BayPassengerExitReleaseTest.View view) {
        var player = drive.player;
        var before = Measure.take(view);
        drive.attacks.clear(); drive.fall = -1; drive.capturing = true;
        try {
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX(), drive.landY, player.getZ(), true));
        } finally { drive.capturing = false; drive.descending = false; }
        var after = Measure.take(view);
        drive.landed = view.server().getTickCount();
        long cancelled = drive.attacks.stream().filter(attack -> attack.endsWith(":true")).count();
        LOGGER.info("ARCE_RELEASE_BAY_EXIT_LANDED logical={} fall={} landingHealth={}>{} attacks=[{}] cancelled={} landedY={} onGround={} chunks={}>{} "
                        + "tickets={} authority={} staged={} stagedY={} physicsY={} padY={} {}", drive.logical, drive.fall, before.health(),
                after.health(), String.join(",", drive.attacks), cancelled, player.getY(), player.onGround(), before.chunks(), after.chunks(),
                before.ticketChange(after), before.sameAuthority(after) ? "same" : "changed", drive.staged, drive.stagedY, drive.physicsY,
                drive.landY, BayPassengerExitReleaseTest.View.of(view.server(), drive.logical).describe());
    }

    /** The pad top that a zero-step descent lands on; damage attempts are recorded only while that landing packet runs. */
    static void landAt(ServerPlayer player, UUID logical, double landY) { drive(player, logical).landY = landY; }

    private static void attack(LivingAttackEvent event) {
        var drive = capturing(event.getEntity());
        if (drive != null) { drive.attacks.add(event.getSource().type().msgId() + ":" + event.isCanceled()); }
    }

    private static void fall(LivingFallEvent event) {
        var drive = capturing(event.getEntity());
        if (drive != null) { drive.fall = event.getDistance(); }
    }

    private static Drive capturing(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof ServerPlayer player) || player.getServer() == null) { return null; }
        Drive drive;
        synchronized (BayPassengerExitReleaseDrive.class) { drive = DRIVES.get(player.getServer()); }
        return drive != null && drive.capturing && drive.player == player ? drive : null;
    }
}
