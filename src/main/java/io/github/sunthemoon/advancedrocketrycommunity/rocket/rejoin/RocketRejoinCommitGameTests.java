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
import net.minecraftforge.event.entity.living.LivingDeathEvent;
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

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final String mode;
        final UUID id = UUID.randomUUID();
        final AtomicBoolean armed = new AtomicBoolean(true);
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        RocketRejoinSavedData original, data;
        ServerPlayer player;
        RocketRejoinGuards.Session old;
        Path file;
        UUID generation;
        long completions;
        int phase, ticks;
        boolean closed, fresh;
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
                if (!closed) { pacer.pace(); }
            } catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() {
            original = RocketRejoinEvents.get(server); RocketRuntime.rejoinAdmissions(server);
            file = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(original.root().equals(RocketRejoinRoot.empty()) && !Files.exists(file), "Fixture needs unused sidecar");
            data = RocketRejoinSavedData.open(file, CheckedSavedDataPolicy.atomic(RocketRejoinSavedData.TYPE),
                    CheckedSavedDataFile::presence, CheckedSavedDataPolicy.DirectoryForce.PLATFORM, this::gate);
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
            helper.assertTrue(data.submit(new RocketRejoinRoot(1, List.of(b), List.of(new Entry(b.release(), id, 0, Placement.PENDING, Seat.VERIFIED, null)))), "S1 refused");
            player = connect(); old = data.guards(server).session(id);
            helper.assertTrue(old != null, "Early guard missing");
        }
        ServerPlayer connect() {
            var next = new ServerPlayer(server, helper.getLevel(), new GameProfile(id, "W159F" + mode));
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, next); return next;
        }
        void invalidate() {
            generation = data.acknowledgedRoot().entries().get(0).attempt().generation();
            completions = data.completions();
            if (mode.equals("Death")) {
                player.setHealth(0); MinecraftForge.EVENT_BUS.post(new LivingDeathEvent(player, player.damageSources().generic()));
            } else if (mode.equals("Clone")) { respawn(true); }
            else { server.getPlayerList().remove(player); player = connect(); }
            release.countDown(); phase = 2;
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
            close(); helper.succeed();
        }
        void close() {
            if (closed) { return; } closed = true; release.countDown();
            if (player != null && server.getPlayerList().getPlayer(id) == player) { server.getPlayerList().remove(player); }
            if (data != null) { data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original); }
            try { if (file != null) { Files.deleteIfExists(file); } }
            catch (java.io.IOException e) { throw new java.io.UncheckedIOException(e); }
        }
    }
}
