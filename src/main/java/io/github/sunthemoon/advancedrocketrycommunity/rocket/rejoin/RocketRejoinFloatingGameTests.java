package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedRootSupplier;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.server.RocketRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W303 (R345): a guarded rejoin session that waited unsupported at zero gravity on its new connection does not carry
 * vanilla's floating counter into its Overworld placement. A survival player, with flight disabled, logs back in adrift
 * in the Space Level while only an admission names it, so the guard holds it there without a placement; W285's
 * allowance holds the native counter at 80. The durable entry is then submitted, and the real placement moves the
 * player to a pad on a contained platform above the test, not to terrain-dependent spawn columns (W304). The first
 * connection tick before the client acknowledges that move, and the next 100 ticks, must keep it connected. The mock
 * connection is ticked by the test; placement runs in the installed server END handlers. One {@code onEachTick} (W28).
 */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketRejoinFloatingGameTests {
    private static final String BATCH = "rocket_rejoin_floating";
    /** Above every natural surface, below the Overworld's build limit with the pad's four-cell envelope. */
    private static final int PLATFORM_Y = 300;
    private static final int CONNECTED_AFTER_ARRIVAL = 100;
    /** A test that fails or times out skips its own clean-up; the batch closes the fixture. */
    private static Fixture open;

    private RocketRejoinFloatingGameTests() { }

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        Fixture pending = open; open = null;
        if (pending != null) { pending.close(); }
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 600)
    public static void aZeroGravityWaitDoesNotCarryTheFloatingCounterIntoThePlacement(GameTestHelper helper) {
        new Fixture(helper).start();
    }

    private static final class Fixture {
        final GameTestHelper helper;
        final MinecraftServer server;
        final GameTestTickPacer pacer;
        final UUID id = UUID.randomUUID();
        final AtomicReference<List<Entry>> admissions = new AtomicReference<>(List.of());
        final Map<BlockPos, BlockState> platform = new LinkedHashMap<>();
        boolean flightAllowed, flightChanged, closed;
        RocketRejoinSavedData original, data;
        Path sidecar;
        RocketRejoinRoot root;
        RocketRejoinGuards.Session session;
        ServerPlayer player;
        EmbeddedChannel channel;
        int phase, ticks, adrift, held, after;

        Fixture(GameTestHelper helper) {
            this.helper = helper; server = helper.getLevel().getServer(); pacer = new GameTestTickPacer(server);
        }
        void start() { open = this; helper.onEachTick(this::tick); }
        void tick() {
            if (closed) { return; }
            try {
                helper.assertTrue(++ticks < 560, "Rejoin floating fixture stalled at phase " + phase + "; " + describe());
                if (phase == 0) { setup(); phase = 1; }
                else if (phase == 1) { adrift(); }
                else if (phase == 2) { placement(); }
                else { connectedAtPad(); }
                if (!closed) { pacer.pace(); }
            } catch (IOException failure) { close(); throw new java.io.UncheckedIOException(failure); }
            catch (RuntimeException | Error failure) { close(); throw failure; }
        }
        void setup() throws IOException {
            original = RocketRejoinEvents.get(server); RocketRuntime.rejoinAdmissions(server);
            sidecar = ManagedRootSupplier.dataFile(server, RocketRejoinSavedData.TYPE);
            helper.assertTrue(original.root().equals(RocketRejoinRoot.empty()) && !Files.exists(sidecar), "Fixture needs an unused sidecar");
            data = RocketRejoinSavedData.open(sidecar); data.guardsForGameTest(server, admissions::get);
            server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), data);
            flightAllowed = server.isFlightAllowed(); flightChanged = true; server.setFlightAllowed(false);
            // A contained stone pad: the candidates' heightmap finds it, and no fluid or terrain is involved.
            var overworld = server.overworld(); var column = helper.absolutePos(new BlockPos(1, 0, 1));
            var center = new BlockPos(column.getX(), PLATFORM_Y, column.getZ());
            for (int x = -5; x <= 5; x++) { for (int z = -5; z <= 5; z++) { for (int y = 0; y <= 3; y++) {
                var at = center.offset(x, y, z); platform.put(at, overworld.getBlockState(at));
                overworld.setBlock(at, (y == 0 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), Block.UPDATE_CLIENTS);
            } } }
            var b = RocketRejoinGuardGameTests.root(id).batches().get(0);
            var p = new Position(center.getX(), center.getY() + 1, center.getZ());
            b = new Batch(b.release(), 0, b.transfer(), b.rocket(), b.authority(), b.hold(), "minecraft:overworld", p, p, p,
                    b.resources(), b.before(), b.scrubbed(), b.released(), List.of(new Slot("minecraft:overworld", p.x() >> 4, p.z() >> 4)));
            root = new RocketRejoinRoot(1, List.of(b), List.of(new Entry(b.release(), id, 0, Placement.PENDING, Seat.VERIFIED, null)));
            // The first login saves the player adrift in the Space Level; nothing names it yet, so it is not guarded.
            ServerLevel space = server.getLevel(CelestialIds.SPACE_LEVEL);
            var adriftAt = helper.absolutePos(new BlockPos(1, 2, 1));
            space.getChunkAt(adriftAt);
            connect();
            helper.assertTrue(!RocketRejoinProtection.guarded(player), "An unnamed first login was guarded");
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(space, adriftAt.getX() + 0.5D, 220.0D, adriftAt.getZ() + 0.5D, 0.0F, 0.0F);
            server.getPlayerList().remove(player); channel.finishAndReleaseAll();
            // The next login is named by an admission only: guarded, but with no durable entry to place.
            admissions.set(RocketRejoinGuardGameTests.root(id).entries());
            space.getChunkAt(adriftAt); connect(); acknowledge();
            session = data.guards(server).session(id);
            helper.assertTrue(session != null && RocketRejoinProtection.guarded(player) && player.level() == space
                    && player.getY() == 220.0D && !player.isCreative() && !player.getAbilities().mayfly,
                    "The second login is not a guarded survival player adrift in space: " + describe());
        }
        void connect() {
            player = new ServerPlayer(server, server.overworld(), new GameProfile(id, "W303Rejoin"));
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
        }
        void adrift() {
            airborne(true); adrift++;
            helper.assertTrue(connected() && RocketRejoinProtection.guarded(player) && player.level().dimension().equals(CelestialIds.SPACE_LEVEL),
                    "The guarded zero-g wait ended early at " + adrift + ": " + describe());
            helper.assertTrue(CelestialGravityController.appliedMultiplier(player) == 0.0D, "The adrift fixture is not in zero gravity: " + describe());
            if (player.connection.aboveGroundTickCount == 80) { held++; }
            if (held >= 5) {
                // The obligation becomes durable now; the guard's real placement follows.
                helper.assertTrue(data.submit(root), "Fixture S1 refused");
                admissions.set(List.of()); phase = 2;
            }
        }
        void placement() {
            if (player.level() == server.overworld()) { arrived(); return; }
            airborne(true);
            helper.assertTrue(connected() && RocketRejoinProtection.guarded(player), "The guarded session ended before its placement: " + describe());
        }
        void arrived() {
            int arrivalCounter = player.connection.aboveGroundTickCount;
            helper.assertTrue(player.getPersistentData().contains(RocketRejoinMarker.KEY) && player.getY() == PLATFORM_Y + 1,
                    "The guard did not place the player on the fixture pad: " + describe());
            // Do not acknowledge yet: the client can still be reporting its zero-g floating state.
            player.connection.tick();
            helper.assertTrue(connected(), "The first connection tick after the placement kicked the player; arrival counter=" + arrivalCounter);
            helper.assertTrue(arrivalCounter == 0, "The confirmed placement retained counter " + arrivalCounter);
            acknowledge(); phase = 3;
        }
        void connectedAtPad() {
            airborne(true); after++;
            helper.assertTrue(connected() && player.level() == server.overworld(),
                    "The placed player did not remain connected in the Overworld at " + after + ": " + describe());
            if (after >= CONNECTED_AFTER_ARRIVAL) { close(); helper.succeed(); }
        }
        /** One client tick: acknowledge any teleport, report the same position airborne, then the connection tick. */
        void airborne(boolean acknowledge) {
            if (acknowledge) { acknowledge(); }
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX(), player.getY(), player.getZ(), false));
            player.connection.tick();
            player.setDeltaMovement(Vec3.ZERO); player.setOldPosAndRot();
        }
        void acknowledge() {
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                try {
                    if (packet instanceof ClientboundPlayerPositionPacket position) {
                        player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                    }
                } finally { io.netty.util.ReferenceCountUtil.release(packet); }
            }
        }
        boolean connected() { return player.connection.connection.isConnected(); }
        String describe() {
            return player == null ? "no player" : "level=" + player.level().dimension().location() + " at=" + player.position()
                    + " counter=" + player.connection.aboveGroundTickCount + " held=" + held + " connected=" + connected()
                    + (session == null ? "" : " reason=" + session.reason + " disconnected=" + session.disconnected);
        }
        void close() {
            if (closed) { return; } closed = true; if (open == this) { open = null; }
            try {
                if (player != null && server.getPlayerList().getPlayer(id) == player) { server.getPlayerList().remove(player); }
                if (data != null) { data.close(); server.overworld().getDataStorage().set(RocketRejoinSavedData.TYPE.dataName(), original); }
                // Setup's flags: no neighbour is notified, so restoring the cells changes nothing else.
                platform.forEach((at, state) -> server.overworld().setBlock(at, state, Block.UPDATE_CLIENTS));
                if (flightChanged) { server.setFlightAllowed(flightAllowed); }
                try { if (sidecar != null) { Files.deleteIfExists(sidecar); } }
                catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
            } finally { if (channel != null) { channel.finishAndReleaseAll(); } }
        }
    }
}
