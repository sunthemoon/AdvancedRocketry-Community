package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestAutosave;
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
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
    /** The measured timeout of every fixture: its own {@link Fixture#MEASURED_TICKS} assertion and ten ticks of margin. */
    private static final int MEASURED_TIMEOUT_TICKS = 240;
    /**
     * W347: Temp and Replace may first wait, unmeasured, for vanilla's autosave to pass (at most
     * {@link Fixture#AUTOSAVE_WAIT_TICKS}). Only that wait is added; the measured budget and its assertion are unchanged.
     */
    private static final int FAULT_TIMEOUT_TICKS = MEASURED_TIMEOUT_TICKS + Fixture.AUTOSAVE_WAIT_TICKS;
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_quit", timeoutTicks = 240)
    public static void quitBetweenPinsSavesExactPositionAndCompletesOffline(GameTestHelper helper) { new Fixture(helper, "Quit").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_temp_failure", timeoutTicks = FAULT_TIMEOUT_TICKS)
    public static void realTemporaryWriteFailureKeepsObligationUntilNextPrompt(GameTestHelper helper) { new Fixture(helper, "Temp").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_replace_failure", timeoutTicks = FAULT_TIMEOUT_TICKS)
    public static void realReplacementFailureEventCannotProveOldFile(GameTestHelper helper) { new Fixture(helper, "Replace").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_temp_ordinary", timeoutTicks = FAULT_TIMEOUT_TICKS)
    public static void ordinarySaveAfterTemporaryFailureCanCompleteReadback(GameTestHelper helper) { new Fixture(helper, "Temp", true).start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_replace_ordinary", timeoutTicks = FAULT_TIMEOUT_TICKS)
    public static void ordinarySaveAfterReplacementFailureCanCompleteReadback(GameTestHelper helper) { new Fixture(helper, "Replace", true).start(); }
    // W347: a simulated clock puts vanilla's next autosave inside the fixture's window; nothing is installed or logged.
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_autosave_window", timeoutTicks = 40)
    public static void replaceFixtureWaitsWhileTheNextAutosaveFallsInsideItsWindow(GameTestHelper helper) { autosaveWindow(helper, "Replace"); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_autosave_window", timeoutTicks = 40)
    public static void tempFixtureWaitsWhileTheNextAutosaveFallsInsideItsWindow(GameTestHelper helper) { autosaveWindow(helper, "Temp"); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_unsafe", timeoutTicks = 240)
    public static void noSafeCandidateEndsAtProtectedDeadline(GameTestHelper helper) { new Fixture(helper, "Unsafe").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_vehicle", timeoutTicks = 240)
    public static void entityJoinGuardPreventsVanillaRootVehicleRemount(GameTestHelper helper) { new Fixture(helper, "Vehicle").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_vehicle_first", timeoutTicks = 240)
    public static void playerFirstLoginCannotRemountItsSubsequentlyLoadedRootVehicle(GameTestHelper helper) { new Fixture(helper, "VehicleFirst").start(); }
    @GameTest(template = "rocket_test", batch = "rocket_rejoin_clone", timeoutTicks = 240)
    public static void deathAndRealRespawnDiscardTheOldSessionsReadback(GameTestHelper helper) { new Fixture(helper, "Clone").start(); }

    // W304: a test that fails or times out can skip its fixture's close(); its batch then restores the pad it left.
    @AfterBatch(batch = "rocket_rejoin_quit") public static void afterQuit(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_temp_failure") public static void afterTempFailure(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_replace_failure") public static void afterReplaceFailure(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_temp_ordinary") public static void afterTempOrdinary(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_replace_ordinary") public static void afterReplaceOrdinary(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_unsafe") public static void afterUnsafe(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_vehicle") public static void afterVehicle(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_vehicle_first") public static void afterVehicleFirst(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_clone") public static void afterClone(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }
    @AfterBatch(batch = "rocket_rejoin_autosave_window") public static void afterAutosaveWindow(ServerLevel level) { RocketRejoinFixturePad.closeAbandoned(); }

    /**
     * W347 regression: the real fixture's tick, driven with a simulated clock, at the first tick whose window holds the
     * autosave, inside the window, just before it and in the autosave's own tick. The fixture must not run setup, connect
     * its player, block a file or start its measured count; its window opens only once the autosave has passed or lies
     * past the window.
     */
    private static void autosaveWindow(GameTestHelper helper, String mode) {
        var server = helper.getLevel().getServer();
        int autosave = 10 * GameTestAutosave.PERIOD_TICKS;
        int[] simulated = {0};
        var fixture = new Fixture(helper, mode, false, () -> simulated[0]);
        int[] ticks = {autosave - Fixture.MEASURED_TICKS, autosave - 100, autosave - 1, autosave};
        int[] step = {0};
        helper.onEachTick(() -> {
            if (step[0] == ticks.length) { return; }
            try {
                simulated[0] = ticks[step[0]];
                fixture.tick();
                var playerDirectory = server.getWorldPath(LevelResource.PLAYER_DATA_DIR);
                helper.assertTrue(fixture.phase == 0 && fixture.ticks == 0 && fixture.data == null && fixture.player == null
                                && fixture.autosaveWaits == step[0] + 1 && !fixture.blockedBackup && !fixture.blockedDirectory
                                && !Files.exists(playerDirectory.resolve(fixture.id + ".dat_old"))
                                && (!Files.exists(playerDirectory) || Files.isDirectory(playerDirectory))
                                && server.getPlayerList().getPlayer(fixture.id) == null,
                        mode + " fixture opened its window at simulated tick " + simulated[0] + " with the autosave of tick "
                                + autosave + " inside it");
                if (++step[0] == ticks.length) {
                    simulated[0] = autosave + 1; boolean passed = fixture.windowFits();
                    simulated[0] = autosave - Fixture.MEASURED_TICKS - 1; boolean beyond = fixture.windowFits();
                    helper.assertTrue(passed && beyond, mode + " fixture's window stays closed after the autosave=" + passed
                            + " or with the autosave past its window=" + beyond);
                    fixture.close(); helper.succeed();
                }
            } catch (RuntimeException | Error failure) { fixture.close(); throw failure; }
        });
    }

    private static final class Fixture {
        /** The fixture's measured tick budget, checked by its own assertion in {@link #tick}. */
        static final int MEASURED_TICKS = 230;
        /** W347: the longest unmeasured wait for an autosave, from the first tick whose window holds it to the tick after it. */
        static final int AUTOSAVE_WAIT_TICKS = MEASURED_TICKS + 1;
        final GameTestHelper helper;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final IntSupplier clock;
        final String mode;
        final boolean ordinarySave;
        final java.util.ArrayList<Long> prompts = new java.util.ArrayList<>();
        final UUID id = UUID.randomUUID();
        final Consumer<PlayerEvent.SaveToFile> listener = this::saved;
        RocketRejoinSavedData original, data;
        RocketRejoinFixturePad pad;
        ServerPlayer player;
        RocketRejoinGuards.Session session;
        Path sidecar, playerDirectory, movedDirectory, ownedDirectory, backup;
        Entry entry;
        UUID oldGeneration;
        Vec3 oldPosition, target;
        Boat boat;
        boolean closed, blockedDirectory, blockedBackup, borderChanged, quit;
        double borderX, borderZ, borderSize;
        int phase, ticks, saves, autosaveWaits;
        long ordinarySavedAt = -1;
        Fixture(GameTestHelper helper, String mode) {
            this(helper, mode, false);
        }
        Fixture(GameTestHelper helper, String mode, boolean ordinarySave) {
            this(helper, mode, ordinarySave, null);
        }
        /** {@code clock} replaces the server tick count only in the W347 regression. */
        Fixture(GameTestHelper helper, String mode, boolean ordinarySave, IntSupplier clock) {
            this.ordinarySave = ordinarySave;
            this.helper = helper; this.mode = mode; server = helper.getLevel().getServer(); pacer = new GameTestTickPacer(server);
            this.clock = clock != null ? clock : server::getTickCount;
        }
        void start() { helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                if (phase == 0 && !windowClear()) { return; }
                if (data != null) { observePrompt(); }
                helper.assertTrue(++ticks < MEASURED_TICKS, "Rejoin fixture did not finish at phase " + phase + " mode=" + mode);
                if (phase == 0) { setup(); phase = 1; }
                else if (mode.equals("Unsafe")) { unsafe(); }
                else if (phase == 1) { placed(); }
                else if (phase == 3) { cloneBody(); }
                else { completed(); }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        /**
         * W347: vanilla's autosave saves every player. Temp and Replace block this player's file from setup until placement,
         * and the log manifest counts exactly one failed save, so they open their window only when the next autosave falls
         * after the whole measured budget. The ticks waited here come before setup and are not measured.
         */
        boolean windowClear() {
            if (!mode.equals("Temp") && !mode.equals("Replace")) { return true; }
            if (windowFits()) {
                if (autosaveWaits > 0) {
                    io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                            "ARCE_W347_AUTOSAVE_WAIT mode={} ordinary={} waited={} opened_at={}", mode, ordinarySave, autosaveWaits,
                            clock.getAsInt());
                }
                return true;
            }
            helper.assertTrue(++autosaveWaits <= AUTOSAVE_WAIT_TICKS, "Rejoin fixture waited past the autosave bound mode=" + mode);
            return false;
        }
        boolean windowFits() { return !GameTestAutosave.within(clock.getAsInt(), MEASURED_TICKS); }
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
            // W304: the candidates take their height from the surface, above this floor; the pad over the same columns
            // is that surface, so neither the terrain above the structure nor the spawn ring decides the placement.
            pad = RocketRejoinFixturePad.around(server.overworld(), center.getX(), center.getZ(), center.getX(), center.getZ());
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
            if (ordinarySave) {
                helper.assertTrue(data.root().entries().get(0).placement() == Placement.PENDING,
                        "Failed write retired placement before the ordinary save");
                helper.assertTrue(server.getTickCount() - session.placedAt < 100,
                        "Controlled ordinary save missed the interval before the next W80 prompt");
                long prompt = data.guards(server).lastPromptTick();
                ordinarySavedAt = server.getTickCount();
                server.getPlayerList().saveAll();
                helper.assertTrue(data.guards(server).lastPromptTick() == prompt,
                        "An ordinary save was counted as a W80 prompt");
                helper.assertTrue(scan().status() == RocketRejoinPlayerFile.Status.MATCH,
                        "Ordinary save did not install the exact readback");
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
                observePrompt();
                helper.assertTrue(!prompts.isEmpty(), "Failure fixture never observed a W80 prompt");
                if (ordinarySave) {
                    helper.assertTrue(ordinarySavedAt >= prompts.get(0) && ordinarySavedAt - prompts.get(0) < 100,
                            "Ordinary save was not between the first prompt and its next allowed retry");
                }
                io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                        "ARCE_W160F_PROMPTS mode={} ordinary={} ticks={} ordinaryAt={} p2=true", mode, ordinarySave, prompts, ordinarySavedAt);
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
            if (event.getEntity().getUUID().equals(id)) { saves++; }
        }
        void observePrompt() {
            long prompt = data.guards(server).lastPromptTick();
            if (prompt == Long.MIN_VALUE || !prompts.isEmpty() && prompts.get(prompts.size() - 1) == prompt) { return; }
            helper.assertTrue(prompts.isEmpty() || prompt - prompts.get(prompts.size() - 1) >= 100,
                    "Prompt save retried before the global 100-tick cadence");
            prompts.add(prompt);
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
            finally { if (pad != null) { pad.close(); } }
        }
    }
}
