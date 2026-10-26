package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real sessions and checked file replacement; gates hold the IO thread before its proof check. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinCommitGameTests {
    private RocketRejoinCommitGameTests() { }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_death", timeoutTicks = 240)
    public static void deathBeforeP2InstallationRetainsPendingAndVerifiesRespawn(GameTestHelper helper) { new Fixture(helper, "Death").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_clone", timeoutTicks = 240)
    public static void cloneBeforeP2InstallationRetainsPendingAndVerifiesNewBody(GameTestHelper helper) { new Fixture(helper, "Clone").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_login", timeoutTicks = 240)
    public static void newLoginBeforeP2InstallationRequiresFreshAttempt(GameTestHelper helper) { new Fixture(helper, "Login").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_unsafe", timeoutTicks = 240)
    public static void unsafeBeforeInstallationRetainsObligationAcrossLoginAndServiceRestart(GameTestHelper helper) { new Fixture(helper, "Pre").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_ack", timeoutTicks = 240)
    public static void unsafeAfterInstallationBeforeAckRelocatesWithinSameSession(GameTestHelper helper) { new Fixture(helper, "Ack").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_final", timeoutTicks = 240)
    public static void unsafeImmediatelyBeforeUnguardingRelocatesAndRechecks(GameTestHelper helper) { new Fixture(helper, "Final").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_commit_residual", timeoutTicks = 240)
    public static void postCommitNoCandidateDisconnectPinsAndNextLoginIsOrdinary(GameTestHelper helper) { new Fixture(helper, "Residual").start(); }

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final String mode;
        final UUID id = UUID.randomUUID();
        final AtomicBoolean armed = new AtomicBoolean(true);
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final java.util.function.Consumer<TickEvent.ServerTickEvent> beforeUnguard = this::beforeUnguard;
        RocketRejoinSavedData original, data;
        ServerPlayer player;
        RocketRejoinGuards.Session old;
        Path file;
        UUID generation;
        Attempt oldAttempt;
        Batch batch;
        long completions;
        int phase, ticks, changedAt;
        boolean closed, fresh, changed, borderChanged;
        double borderX, borderZ, borderSize;
        Fixture(GameTestHelper helper, String mode) {
            this.helper = helper; this.mode = mode; server = helper.getLevel().getServer(); pacer = new GameTestTickPacer(server);
        }
        void start() { helper.onEachTick(this::tick); }
        void gate(RocketRejoinRoot root) {
            if (root.entries().stream().noneMatch(e -> e.player().equals(id) && e.placement() == Placement.PENDING)
                    && armed.compareAndSet(true, false)) {
                entered.countDown();
                try { if (!release.await(10, TimeUnit.SECONDS)) { throw new IllegalStateException("P2 gate timed out"); } }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }
        }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++ticks < 230, "Commit fixture stalled: " + mode + " phase=" + phase);
                if (phase == 0) { setup(); phase = 1; }
                else if (phase == 1 && entered.getCount() == 0) { invalidate(); }
                else if (phase == 2 && data.completions() > completions) { retained(); }
                else if (phase == 3) { completed(); }
                else if (phase == 4 && ticks > changedAt + 1) { release.countDown(); phase = mode.equals("Pre") ? 5 : 6; }
                else if (phase == 5 && data.completions() > completions) { unsafeRetained(); }
                else if (phase == 6) { safeHandoff(); }
                if (!closed) { pacer.pace(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() {
            original = RocketRejoinEvents.get(server); RocketRuntime.rejoinAdmissions(server);
            file = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(original.root().equals(RocketRejoinRoot.empty()) && !Files.exists(file), "Fixture needs unused sidecar");
            data = open();
            data.guardsForGameTest(server);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            var center = helper.absolutePos(new BlockPos(2, 5, 2));
            for (int x = -5; x <= 5; x++) { for (int z = -5; z <= 5; z++) { for (int y = -1; y <= 2; y++) {
                helper.getLevel().setBlockAndUpdate(center.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            } } }
            var b = RocketRejoinGuardGameTests.root(id).batches().get(0);
            var p = new Position(center.getX(), center.getY(), center.getZ());
            b = new Batch(b.release(), 0, b.transfer(), b.rocket(), b.authority(), b.hold(), "minecraft:overworld", p, p, p,
                    b.resources(), b.before(), b.scrubbed(), b.released(), List.of(new Slot("minecraft:overworld", p.x() >> 4, p.z() >> 4)));
            batch = b;
            if (mode.equals("Final")) { MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, beforeUnguard); }
            helper.assertTrue(data.submit(new RocketRejoinRoot(1, List.of(b), List.of(new Entry(b.release(), id, 0, Placement.PENDING, Seat.VERIFIED, null)))), "S1 refused");
            player = connect(); old = data.guards(server).session(id);
            helper.assertTrue(old != null, "Early guard missing");
        }
        boolean afterInstall() { return List.of("Ack", "Final", "Residual").contains(mode); }
        RocketRejoinSavedData open() {
            var policy = new CheckedSavedDataPolicy(RocketRejoinSavedData.TYPE, (staged, target) -> {
                CheckedSavedDataFile.atomicMove(staged, target);
                if (afterInstall()) { gate(RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(target, RocketRejoinSavedData.TYPE).orElseThrow())); }
            });
            return RocketRejoinSavedData.open(file, policy, CheckedSavedDataFile::presence,
                    CheckedSavedDataPolicy.DirectoryForce.PLATFORM, root -> { if (!afterInstall()) { gate(root); } });
        }
        ServerPlayer connect() {
            var next = new ServerPlayer(server, helper.getLevel(), new GameProfile(id, "W159F" + mode));
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, next); return next;
        }
        void invalidate() {
            oldAttempt = data.acknowledgedRoot().entries().get(0).attempt(); generation = oldAttempt.generation();
            completions = data.completions();
            if (mode.equals("Pre") || afterInstall()) {
                if (!mode.equals("Final")) { makeUnsafe(); }
                changedAt = ticks; phase = 4; return;
            }
            if (mode.equals("Death")) {
                player.setHealth(0); MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
            } else if (mode.equals("Clone")) { respawn(true); }
            else { server.getPlayerList().remove(player); player = connect(); }
            release.countDown(); phase = 2;
        }
        void makeUnsafe() {
            var feet = BlockPos.containing(oldAttempt.x(), oldAttempt.y(), oldAttempt.z());
            helper.getLevel().setBlockAndUpdate(mode.equals("Ack") ? feet : feet.below(),
                    (mode.equals("Ack") ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            changed = true;
            if (mode.equals("Residual")) {
                var border = server.overworld().getWorldBorder(); borderX = border.getCenterX(); borderZ = border.getCenterZ(); borderSize = border.getSize();
                borderChanged = true; border.setCenter(100_000, 100_000); border.setSize(1);
            }
        }
        void beforeUnguard(TickEvent.ServerTickEvent event) {
            if (!closed && !changed && phase >= 4 && event.getServer() == server && event.phase == TickEvent.Phase.END
                    && data.acknowledgedRoot().entries().isEmpty()) {
                helper.assertTrue(data.guards(server).active(player), "Final safety mutation was not before unguarding");
                makeUnsafe();
            }
        }
        void unsafeRetained() {
            var disk = RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file, RocketRejoinSavedData.TYPE).orElseThrow());
            helper.assertTrue(disk.entries().stream().anyMatch(e -> e.player().equals(id) && e.placement() == Placement.PENDING),
                    "Unsafe pre-install target installed P2");
            helper.assertTrue(data.guards(server).active(player), "Unsafe pre-install handoff unguarded player");
            restartAndLogin(); phase = 3;
        }
        void restartAndLogin() {
            server.getPlayerList().remove(player); data.close();
            data = open(); data.guardsForGameTest(server);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            player = connect();
        }
        void safeHandoff() {
            if (mode.equals("Residual")) {
                if (!old.disconnected) { return; }
                helper.assertTrue(old.disconnectedAt == old.deadline && player.position().equals(old.position)
                        && data.guards(server).active(player) && data.acknowledgedRoot().entries().isEmpty(), "Residual deadline did not retain pinned protection");
                var pinned = old.position; restartAndLogin();
                helper.assertTrue(!data.guards(server).active(player) && player.position().equals(pinned)
                        && !player.getPersistentData().contains(RocketRejoinMarker.KEY), "Post-P2 restart/login was not ordinary at pinned position");
                close(); helper.succeed(); return;
            }
            if (data.guards(server).active(player)) { return; }
            helper.assertTrue(changed && !old.disconnected && data.acknowledgedRoot().entries().isEmpty()
                    && !player.position().equals(new net.minecraft.world.phys.Vec3(oldAttempt.x(), oldAttempt.y(), oldAttempt.z()))
                    && RocketRejoinWorld.safe(player, player.serverLevel(), player.position()), "Post-P2 handoff did not relocate safely in the same session");
            close(); helper.succeed();
        }
        void respawn(boolean alive) {
            player = server.getPlayerList().respawn(player, alive); player.connection.player = player;
        }
        void retained() {
            var disk = RocketRejoinCodec.decode(CheckedSavedDataFile.readPayload(file, RocketRejoinSavedData.TYPE).orElseThrow());
            helper.assertTrue(disk.entries().stream().anyMatch(e -> e.player().equals(id) && e.placement() == Placement.PENDING),
                    "Invalidated session installed P2 instead of retaining PENDING");
            if (mode.equals("Death")) { respawn(false); }
            var next = data.guards(server).session(id);
            helper.assertTrue(next != null && next != old && next.player == player && RocketRejoinProtection.guarded(player), "Replacement is not guarded");
            phase = 3;
        }
        void completed() {
            for (var entry : data.root().entries()) {
                if (entry.player().equals(id) && entry.attempt() != null && !entry.attempt().generation().equals(generation)) { fresh = true; }
            }
            if (data.guards(server).active(player)) { return; }
            helper.assertTrue(fresh && data.acknowledgedRoot().entries().isEmpty() && player.isAlive()
                    && !player.getPersistentData().contains(RocketRejoinMarker.KEY), "Replacement did not verify its fresh attempt");
            helper.assertTrue(RocketRejoinWorld.safe(player, player.serverLevel(), player.position()), "Verified handoff is unsafe");
            close(); helper.succeed();
        }
        void close() {
            if (closed) { return; } closed = true; release.countDown();
            MinecraftForge.EVENT_BUS.unregister(beforeUnguard);
            if (borderChanged) { var border = server.overworld().getWorldBorder(); border.setCenter(borderX, borderZ); border.setSize(borderSize); }
            if (player != null && server.getPlayerList().getPlayer(id) == player) { server.getPlayerList().remove(player); }
            if (data != null) { data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original); }
            try { if (file != null) { Files.deleteIfExists(file); } }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
}
