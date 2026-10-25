package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Each asynchronous fixture has its own batch and restores the initially unused sidecar and native directories. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinReadbackGameTests {
    private RocketRejoinReadbackGameTests() { }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_quit", timeoutTicks = 240)
    public static void quitBetweenPinsSavesExactPositionAndCompletesOffline(GameTestHelper helper) { new Fixture(helper, "Quit").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_temp_failure", timeoutTicks = 240)
    public static void realTemporaryWriteFailureKeepsObligationUntilNextPrompt(GameTestHelper helper) { new Fixture(helper, "Temp").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_replace_failure", timeoutTicks = 240)
    public static void realReplacementFailureEventCannotProveOldFile(GameTestHelper helper) { new Fixture(helper, "Replace").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_unsafe", timeoutTicks = 240)
    public static void noSafeCandidateEndsAtProtectedDeadline(GameTestHelper helper) { new Fixture(helper, "Unsafe").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_vehicle", timeoutTicks = 240)
    public static void entityJoinGuardPreventsVanillaRootVehicleRemount(GameTestHelper helper) { new Fixture(helper, "Vehicle").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_vehicle_first", timeoutTicks = 240)
    public static void playerFirstLoginCannotRemountItsSubsequentlyLoadedRootVehicle(GameTestHelper helper) { new Fixture(helper, "VehicleFirst").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_clone", timeoutTicks = 240)
    public static void deathAndRealRespawnDiscardTheOldSessionsReadback(GameTestHelper helper) { new Fixture(helper, "Clone").start(); }

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final String mode;
        final UUID id = UUID.randomUUID();
        final Consumer<PlayerEvent.SaveToFile> listener = this::saved;
        RocketRejoinSavedData original, data;
        ServerPlayer player;
        RocketRejoinGuards.Session session;
        Path sidecar, playerDirectory, movedDirectory, ownedDirectory, backup;
        Entry entry;
        UUID oldGeneration;
        Vec3 oldPosition, target;
        Boat boat;
        boolean closed, blockedDirectory, blockedBackup, borderChanged, quit;
        double borderX, borderZ, borderSize;
        int phase, ticks, saves, lastSaveAt = -1;
        Fixture(GameTestHelper helper, String mode) {
            this.helper = helper; this.mode = mode; server = helper.getLevel().getServer(); pacer = new GameTestTickPacer(server);
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++ticks < 230, "Rejoin fixture did not finish at phase " + phase + " mode=" + mode);
                if (phase == 0) { setup(); phase = 1; }
                else if (mode.equals("Unsafe")) { unsafe(); }
                else if (phase == 1) { placed(); }
                else if (phase == 3) { cloneBody(); }
                else { completed(); }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() throws IOException {
            original = RocketRejoinEvents.get(server); RocketRuntime.rejoinAdmissions(server);
            sidecar = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(original.root().equals(RocketRejoinRoot.empty()) && !Files.exists(sidecar), "Fixture needs an unused sidecar");
            data = RocketRejoinSavedData.open(sidecar); data.guardsForGameTest(server);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            MinecraftForge.EVENT_BUS.addListener(listener);
            playerDirectory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR); Files.createDirectories(playerDirectory);
            backup = playerDirectory.resolve(id + ".dat_old");
            var center = helper.absolutePos(new BlockPos(2, 5, 2));
            for (int x = -5; x <= 5; x++) { for (int z = -5; z <= 5; z++) { for (int y = -1; y <= 2; y++) {
                helper.getLevel().setBlockAndUpdate(center.offset(x, y, z), (y == -1 ? Blocks.STONE : Blocks.AIR).defaultBlockState());
            } } }
            var base = RocketRejoinGuardGameTests.root(id); var b = base.batches().get(0);
            var p = new Position(center.getX(), center.getY(), center.getZ());
            b = new Batch(b.release(), 0, b.transfer(), b.rocket(), b.authority(), b.hold(), "minecraft:overworld", p, p, p,
                    b.resources(), b.before(), b.scrubbed(), b.released(), List.of(new Slot("minecraft:overworld", p.x() >> 4, p.z() >> 4)));
            entry = new Entry(b.release(), id, 0, Placement.PENDING, Seat.VERIFIED, null);
            var root = new RocketRejoinRoot(1, List.of(b), List.of(entry));
            if (mode.startsWith("Vehicle")) {
                player = connect(); player.setHealth(1);
                boat = new Boat(helper.getLevel(), center.getX() + 0.5, center.getY(), center.getZ() + 0.5);
                helper.assertTrue(helper.getLevel().addFreshEntity(boat) && player.startRiding(boat, true), "Old vehicle fixture did not mount");
                server.getPlayerList().remove(player);
                try (var input = Files.newInputStream(playerDirectory.resolve(id + ".dat"))) {
                    helper.assertTrue(net.minecraft.nbt.NbtIo.readCompressed(input).contains("RootVehicle"), "Vanilla did not save the old RootVehicle");
                }
                if (mode.equals("VehicleFirst")) { boat.discard(); }
            }
            helper.assertTrue(data.submit(root), "Fixture S1 refused");
            player = connect(); player.setHealth(1);
            session = data.guards(server).session(id); oldPosition = session.position;
            helper.assertTrue(session != null && player.getVehicle() == null, "Early join did not protect/unmount player");
            if (mode.equals("Replace")) {
                server.getPlayerList().saveAll();
                Files.createDirectory(backup); Files.writeString(backup.resolve("w159-owned"), "fixture"); blockedBackup = true;
            }
            if (mode.equals("Temp")) {
                ownedDirectory = Files.createTempDirectory(server.getWorldPath(LevelResource.ROOT), "w159-playerdata-");
                movedDirectory = ownedDirectory.resolve("playerdata"); Files.move(playerDirectory, movedDirectory);
                Files.writeString(playerDirectory, "W159 temporary-write fixture"); blockedDirectory = true;
            }
            if (mode.equals("Unsafe")) {
                var border = server.overworld().getWorldBorder(); borderX = border.getCenterX(); borderZ = border.getCenterZ(); borderSize = border.getSize();
                borderChanged = true; border.setCenter(100_000, 100_000); border.setSize(1);
            }
        }
        ServerPlayer connect() {
            var next = new ServerPlayer(server, helper.getLevel(), new GameProfile(id, "W159" + mode));
            var connection = new Connection(PacketFlow.SERVERBOUND); new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, next); return next;
        }
        void placed() throws IOException {
            if (!player.getPersistentData().contains(RocketRejoinMarker.KEY)) { return; }
            entry = data.root().entries().stream().filter(e -> e.player().equals(id)).findFirst().orElseThrow();
            var a = entry.attempt(); target = new Vec3(a.x(), a.y(), a.z());
            helper.assertTrue(player.position().equals(target) && player.isAlive() && player.getHealth() == 1,
                    "Placement was not exact or protected");
            if (mode.equals("Quit")) {
                player.setPos(target.add(4, 7, 1)); player.setDeltaMovement(2, -3, 1); player.fallDistance = 20;
                server.getPlayerList().remove(player); quit = true;
                helper.assertTrue(scan().status() == RocketRejoinPlayerFile.Status.MATCH, "Client quit did not save the exact pinned attempt");
            } else if (mode.equals("Temp")) {
                helper.assertTrue(saves == 0 && data.root().entries().get(0).placement() == Placement.PENDING,
                        "Temporary write failure fired a save event or retired placement");
                restoreDirectory();
            } else if (mode.equals("Replace")) {
                helper.assertTrue(saves >= 2 && scan().status() != RocketRejoinPlayerFile.Status.MATCH,
                        "Failed replacement event certified the old file");
                try (var input = Files.newInputStream(playerDirectory.resolve(id + ".dat"))) {
                    var saved = net.minecraft.nbt.NbtIo.readCompressed(input).getList("Pos", 6);
                    helper.assertTrue(saved.getDouble(0) == oldPosition.x && saved.getDouble(1) == oldPosition.y
                            && saved.getDouble(2) == oldPosition.z, "Failed replacement changed the installed old position");
                }
                restoreBackup();
            }
            phase = mode.equals("Clone") ? 3 : 2;
        }
        void cloneBody() {
            var old = session; var originalPlayer = player;
            oldGeneration = entry.attempt().generation();
            player.setHealth(0);
            MinecraftForge.EVENT_BUS.post(new net.minecraftforge.event.entity.living.LivingDeathEvent(player, player.damageSources().generic()));
            player = server.getPlayerList().respawn(player, false);
            // handleClientCommand adopts the returned body immediately after PlayerList.respawn.
            player.connection.player = player;
            session = data.guards(server).session(id);
            helper.assertTrue(old.invalidated && session != null && session != old && session.player == player
                    && player != originalPlayer && player.isAlive() && RocketRejoinProtection.guarded(player),
                    "Real death/clone did not replace the guarded session");
            helper.assertTrue(data.root().entries().stream().anyMatch(e -> e.player().equals(id) && e.placement() == Placement.PENDING),
                    "Old body's readback retired the cloned session");
            phase = 2;
        }
        void completed() throws IOException {
            if (mode.equals("Clone") && player.getPersistentData().contains(RocketRejoinMarker.KEY)) {
                var pending = data.root().entries().stream().filter(e -> e.player().equals(id)).findFirst().orElse(null);
                if (pending != null && pending.attempt() != null) {
                    helper.assertTrue(!pending.attempt().generation().equals(oldGeneration), "Clone reused the dead body's file marker generation");
                    entry = pending; var a = entry.attempt(); target = new Vec3(a.x(), a.y(), a.z());
                }
            }
            if (data.acknowledgedRoot() == null || !data.acknowledgedRoot().entries().isEmpty() || data.guards(server).active(player)) { return; }
            helper.assertTrue(scan().status() == RocketRejoinPlayerFile.Status.MATCH, "P2 lacks its installed exact proof");
            if (mode.equals("Temp") || mode.equals("Replace")) {
                helper.assertTrue(lastSaveAt - session.placedAt >= 100, "Prompt save retried before the global 100-tick cadence");
            }
            if (quit) {
                player = connect(); quit = false;
                helper.assertTrue(!RocketRejoinProtection.guarded(player) && !player.getPersistentData().contains(RocketRejoinMarker.KEY)
                        && player.position().equals(target), "Stale-marker login guarded or relocated a verified player");
            } else { helper.assertTrue(!player.getPersistentData().contains(RocketRejoinMarker.KEY), "P2 retained live marker"); }
            close(); helper.succeed();
        }
        RocketRejoinPlayerFile.Result scan() {
            var a = entry.attempt(); return RocketRejoinPlayerFile.scan(playerDirectory,
                    new RocketRejoinPlayerFile.Expected(id, entry.release(), a.generation(), "minecraft:overworld", a.x(), a.y(), a.z()));
        }
        void unsafe() {
            helper.assertTrue((session.disconnected || player.isAlive()) && player.getHealth() == 1 && RocketRejoinProtection.guarded(player),
                    "Unsafe wait exposed player: health=" + player.getHealth() + " guarded=" + RocketRejoinProtection.guarded(player));
            if (!session.disconnected) { return; }
            helper.assertTrue(session.disconnectedAt == session.deadline && session.reason.equals("unsafe")
                    && player.position().equals(session.position) && data.root().entries().get(0).placement() == Placement.PENDING,
                    "No-candidate deadline did not retain protected obligation at 200 ticks");
            close(); helper.succeed();
        }
        void saved(PlayerEvent.SaveToFile event) {
            if (event.getEntity().getUUID().equals(id)) { saves++; lastSaveAt = server.getTickCount(); }
        }
        void restoreDirectory() throws IOException {
            if (!blockedDirectory) { return; }
            if (!Files.readString(playerDirectory).equals("W159 temporary-write fixture")) { throw new IOException("Fixture directory marker changed"); }
            Files.delete(playerDirectory); Files.move(movedDirectory, playerDirectory); Files.delete(ownedDirectory); blockedDirectory = false;
        }
        void restoreBackup() throws IOException {
            if (blockedBackup) { Files.delete(backup.resolve("w159-owned")); Files.delete(backup); blockedBackup = false; }
        }
        void close() {
            if (closed) { return; } closed = true;
            try { restoreDirectory(); restoreBackup(); } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            if (borderChanged) { var border = server.overworld().getWorldBorder(); border.setCenter(borderX, borderZ); border.setSize(borderSize); }
            if (player != null && server.getPlayerList().getPlayer(id) == player) { server.getPlayerList().remove(player); }
            if (boat != null && !boat.isRemoved()) { boat.discard(); }
            MinecraftForge.EVENT_BUS.unregister(listener);
            if (data != null) { data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original); }
            try { if (sidecar != null) { Files.deleteIfExists(sidecar); } } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
        }
    }
}
