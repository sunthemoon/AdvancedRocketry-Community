package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.Verdict;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferPhase;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketJournalGameTestSupport;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.RocketBlockStateAdapter;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Native connected survival logins; unlike the general fixture, rejoin never teleports over the loaded save. */
final class RocketLogoutSaveFixture implements AutoCloseable {
    static final BlockPos ORIGIN = new BlockPos(3, 40, 3);
    /**
     * TALL is W312's eleven-block column. WIDE adds one block beside the origin, so that the rocket's W311 box covers the
     * exit. ON_ASSEMBLER is finding 7-1's field shape, assembled by the installed manager on a real rocket_assembler.
     */
    enum Shape { TALL, WIDE, ON_ASSEMBLER }
    private static final java.util.Set<RocketLogoutSaveFixture> ACTIVE = new HashSet<>();
    final GameTestHelper helper;
    final UUID playerId = UUID.randomUUID();
    final RocketTransferSavedData journal = new RocketTransferSavedData();
    final HashSet<UUID> live = new HashSet<>(), settled = new HashSet<>();
    final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(live, settled);
    final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final List<ServerPlayer> others = new ArrayList<>();
    final List<net.minecraft.world.entity.Entity> extras = new ArrayList<>();
    final Path directory;
    final Shape shape;
    RocketTransferRecord record;
    private boolean closed;
    RocketEntity rocket;
    ServerPlayer player;
    Vec3 exit;
    boolean mountedWhenSaved;

    RocketLogoutSaveFixture(GameTestHelper helper, RocketFlightState state) throws IOException { this(helper, state, Shape.TALL); }

    /** ON_ASSEMBLER ignores {@code state}: it requests assembly, and {@link #boardAssembled()} later boards the rocket. */
    RocketLogoutSaveFixture(GameTestHelper helper, RocketFlightState state, Shape shape) throws IOException {
        this.helper = helper; this.shape = shape;
        directory = Files.createTempDirectory("arce-w312-");
        ACTIVE.add(this);
        try {
            if (shape == Shape.ON_ASSEMBLER) { requestAssembly(); } else { initialize(state); }
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (Throwable cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    private void chamber(BlockPos at) {
        // This fixed chamber is outside other batches' fixture height. Restore every changed terrain cell on close.
        for (int x = -1; x <= 3; x++) { for (int z = -1; z <= 1; z++) {
            for (int y = -2; y <= 11; y++) { block(at.offset(x, y, z), Blocks.AIR.defaultBlockState()); }
            block(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        } }
    }

    private void initialize(RocketFlightState state) {
        var level = helper.getLevel();
        var at = helper.absolutePos(ORIGIN);
        chamber(at);
        var sourceAt = state == RocketFlightState.LANDED ? at.west(6) : at;
        var targetAt = state == RocketFlightState.LANDED ? at : at.east(6);
        var base = RocketSourceRetirementFixtures.countdown(helper, sourceAt, targetAt, List.of(playerId), 0);
        var old = base.record();
        var blocks = new ArrayList<RocketBlock>();
        var seat = new RocketPosition(0, 9, 0);
        for (int y = 0; y <= 10; y++) {
            var pos = new RocketPosition(0, y, 0);
            blocks.add(new RocketBlock(pos, RocketBlockStateAdapter.capture(y == 9
                    ? ModBlocks.ROCKET_SEAT.get().defaultBlockState() : Blocks.IRON_BLOCK.defaultBlockState())));
        }
        if (shape == Shape.WIDE) {
            blocks.add(new RocketBlock(new RocketPosition(1, 0, 0), RocketBlockStateAdapter.capture(Blocks.IRON_BLOCK.defaultBlockState())));
        }
        var snapshot = RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                RocketSourceRetirementFixtures.position(sourceAt), blocks, List.of(seat),
                new RocketStats(blocks.size(), 100, 1_000, 1_000, 1, 1, 1, 0), level.getGameTime(),
                old.sourceSnapshot().propulsion().orElseThrow());
        var arrival = snapshot.relocated(UUID.randomUUID(), level.dimension().location(), old.destinationSnapshot().sourceOrigin(), level.getGameTime());
        record = RocketTransferRecord.create(old.transferId(), old.logicalRocketId(), old.ownerId(), old.sourceEntityId(),
                snapshot, arrival, old.sourceFlightData(), old.destinationFlightData(), old.requiredFuel(), old.createdAtGameTime());
        var flight = switch (state) {
            case ASSEMBLED -> RocketTransferEntities.stationarySource(record, level.getGameTime());
            case FUELED -> RocketTransferEntities.stationarySource(record, level.getGameTime())
                    .withFuel(old.sourceFlightData().fuel(), level.getGameTime());
            case LANDED -> record.destinationFlightData().land(level.getGameTime());
            case ASCENT -> base.countdown().completeCountdown(level.getGameTime());
            case TRANSIT -> record.sourceFlightData();
            default -> throw new IllegalArgumentException("Unsupported logout fixture state " + state);
        };
        // stationarySource retains source fuel; explicitly establish an empty ASSEMBLED control.
        if (state == RocketFlightState.ASSEMBLED) {
            flight = flight.withFuel(io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFuelState.empty(
                    snapshot.propulsion().orElseThrow()), level.getGameTime());
        }
        rocket = ModEntities.ROCKET.get().create(level);
        helper.assertTrue(rocket != null, "Logout rocket type unavailable");
        rocket.initializeTransferred(state == RocketFlightState.LANDED ? arrival : snapshot,
                record.logicalRocketId(), record.ownerId(), flight);
        helper.assertTrue(level.addFreshEntity(rocket), "Logout rocket was not admitted");
        helper.assertTrue(rocket.flightData().orElseThrow().state() == state, "Wrong logout control state");
        player = join();
        player.teleportTo(level, rocket.getX(), rocket.getY(), rocket.getZ(), 0, 0);
        helper.assertTrue(player.startRiding(rocket, true), "Survival player did not mount");
        rocket.positionRider(player);
        player.setHealth(player.getMaxHealth()); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(!player.isCreative() && !player.isInvulnerable() && player.getY() > rocket.getY() + 9,
                "Logout control is not an unprotected high-seat survival rider");
        exit = rocket.getDismountLocationForPassenger(player);
        if (shape == Shape.WIDE) {
            // W311's box covers the exit; only the logout's own-rocket rule (R375) separates the two proofs here.
            helper.assertTrue(rocket.getBoundingBox().intersects(BayPassengerExitWorld.aabb(exitBox())), "The wide rocket's box misses the exit");
            assertProofs(Verdict.UNSAFE, Verdict.SAFE, 0);
        } else {
            assertSafeExit();
        }
    }

    /** W312F: finding 7-1's shape, a real rocket on its rocket_assembler, with the platform one block below the assembler's top. */
    private void requestAssembly() {
        var level = helper.getLevel();
        var at = helper.absolutePos(ORIGIN);
        chamber(at);
        for (int x = -1; x <= 3; x++) { for (int z = -1; z <= 1; z++) {
            block(at.offset(x, -1, z), Blocks.AIR.defaultBlockState());
            for (int y = -6; y <= -2; y++) { block(at.offset(x, y, z), Blocks.STONE.defaultBlockState()); }
        } }
        // The installed manager scans from assemblerPosition.above(); the chest beside the origin widens the W311 box.
        block(at.below(), ModBlocks.ROCKET_ASSEMBLER.get().defaultBlockState());
        block(at, ModBlocks.ROCKET_MOTOR.get().defaultBlockState());
        block(at.above(), ModBlocks.ROCKET_SEAT.get().defaultBlockState());
        block(at.above(2), ModBlocks.GUIDANCE_COMPUTER.get().defaultBlockState());
        block(at.east(), Blocks.CHEST.defaultBlockState());
        player = join();
        player.teleportTo(level, at.getX() + 2.5, at.getY() - 1, at.getZ() + 0.5, 0, 0);
        RocketRuntime.requestAssembler(player, at.below(), true);
    }

    /** Once the installed manager has assembled the rocket, board it through the console, as a player does. */
    boolean boardAssembled() {
        var level = helper.getLevel();
        var at = helper.absolutePos(ORIGIN);
        var found = level.getEntitiesOfClass(RocketEntity.class, new net.minecraft.world.phys.AABB(at).inflate(2));
        if (found.isEmpty()) { return false; }
        helper.assertTrue(found.size() == 1, "Assembly published more than one rocket");
        rocket = found.get(0);
        helper.assertTrue(rocket.blockPosition().equals(at) && rocket.flightData().orElseThrow().state() == RocketFlightState.ASSEMBLED
                && level.getBlockState(at.below()).is(ModBlocks.ROCKET_ASSEMBLER.get()) && level.getBlockState(at.east()).isAir()
                && level.getBlockState(at.east().below()).isAir() && level.getBlockState(at.east().below(2)).is(Blocks.STONE),
                "The rocket is not the assembled 7-1 shape: " + rocket.position());
        RocketRuntime.openFlightMenu(player, rocket);
        RocketRuntime.requestFlightIntent(player, rocket.getId(), io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightAction.BOARD,
                io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketDestination.EARTH, UUID.randomUUID());
        helper.assertTrue(player.getVehicle() == rocket, "Normal boarding did not mount the rider");
        rocket.positionRider(player);
        player.setHealth(player.getMaxHealth()); player.fallDistance = 0; player.setDeltaMovement(Vec3.ZERO);
        helper.assertTrue(!player.isCreative() && !player.isInvulnerable() && player.getY() > rocket.getY() + 1,
                "Logout control is not an unprotected seated survival rider");
        exit = rocket.getDismountLocationForPassenger(player);
        helper.assertTrue(exit.equals(rocket.position().add(1.5, 0, 0))
                && rocket.getBoundingBox().intersects(BayPassengerExitWorld.aabb(exitBox())), "Wrong 7-1 exit: " + exit);
        // The shared proof refuses (assembler, no support, own rocket); the logout proof settles one block.
        assertProofs(Verdict.UNSAFE, Verdict.SAFE, 1);
        return true;
    }

    io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box exitBox() {
        var size = player.getDimensions(net.minecraft.world.entity.Pose.STANDING);
        return new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Box(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitCandidates.Position(
                        player.serverLevel().dimension().location().toString(), exit.x, exit.y, exit.z), size.width, size.height);
    }

    private void assertSafeExit() {
        var world = new BayPassengerExitWorld(player);
        var verdict = sharedProof(world);
        helper.assertTrue(verdict == Verdict.SAFE, "Initial logout exit is not proved safe: " + verdict + " firstContact=" + world.firstContact);
    }

    /** An independent proof verifies the fixture without spending or resetting the installed shared budget. */
    private Verdict sharedProof(BayPassengerExitWorld world) {
        var budget = new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget(player.getServer().getTickCount());
        try (var probe = budget.openProbe().orElseThrow()) {
            return io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitSafety.loaded(world)
                    .inspect(exitBox(), probe, (candidate, work) -> true);
        }
    }

    /** The logout's own proof (W312F), also with a fresh budget. */
    io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety.Proof logoutProof() {
        var budget = new io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.BayExitBudget(player.getServer().getTickCount());
        try (var probe = budget.openProbe().orElseThrow()) {
            return io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.exit.LogoutExitSafety.inspect(
                    new RocketLogoutExitWorld(player, rocket, probe), exitBox(), probe);
        }
    }

    void assertProofs(Verdict shared, Verdict logout, int drop) {
        var world = new BayPassengerExitWorld(player);
        var sharedVerdict = sharedProof(world);
        var proof = logoutProof();
        helper.assertTrue(sharedVerdict == shared && proof.verdict() == logout && proof.drop() == drop,
                "Fixture proofs: shared " + sharedVerdict + " (expected " + shared + ", firstContact=" + world.firstContact
                        + "), logout " + proof + " (expected " + logout + " drop " + drop + ")");
    }

    /** The exit's support row and the rows below it: {@code depth} rows of air, then stone below the proof's last row. */
    void settle(int depth) {
        var at = helper.absolutePos(ORIGIN);
        for (int x = -1; x <= 3; x++) { for (int z = -1; z <= 1; z++) {
            for (int y = -1; y >= -6; y--) { block(at.offset(x, y, z), (y >= -depth ? Blocks.AIR : Blocks.STONE).defaultBlockState()); }
        } }
    }

    void row(int y, BlockState state) {
        var at = helper.absolutePos(ORIGIN);
        for (int x = -1; x <= 3; x++) { for (int z = -1; z <= 1; z++) { block(at.offset(x, y, z), state); } }
    }

    void block(BlockPos at, BlockState state) {
        original.putIfAbsent(at, helper.getLevel().getBlockState(at));
        helper.getLevel().setBlock(at, state, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
    }

    ServerPlayer join() {
        var server = helper.getLevel().getServer();
        var joined = new ServerPlayer(server, helper.getLevel(), new GameProfile(playerId, "W312Rider"));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, joined);
        joined.setGameMode(GameType.SURVIVAL);
        return joined;
    }

    /** Another connected survival player, disconnected on close. */
    ServerPlayer joinOther(String name) {
        var server = helper.getLevel().getServer();
        var joined = new ServerPlayer(server, helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        var connection = new Connection(PacketFlow.SERVERBOUND);
        channels.add(new EmbeddedChannel(connection));
        server.getPlayerList().placeNewPlayer(connection, joined);
        joined.setGameMode(GameType.SURVIVAL);
        others.add(joined);
        return joined;
    }

    CompoundTag disk() {
        var file = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(playerId + ".dat");
        try { return NbtIo.readCompressed(file.toFile()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static Vec3 position(CompoundTag tag) {
        var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
        return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    void logout() { logout(false); }

    void logout(boolean cancelled) {
        var before = rocket.flightData().orElseThrow().passengers();
        var exits = RocketRuntime.passengerExits().orElseThrow();
        int histories = exits.state.histories();
        var observation = new LogoutObservation(player);
        player.setShiftKeyDown(true); // Unconsumed sneak is not LEAVE on a disconnect.
        MinecraftForge.EVENT_BUS.register(observation);
        try { player.connection.onDisconnect(Component.literal("W312 fixture logout")); }
        finally { MinecraftForge.EVENT_BUS.unregister(observation); }
        helper.assertTrue(observation.saves == 1 && observation.detachAttempts > 0 && observation.disconnecting,
                "Logout did not use native disconnecting detach/save events");
        mountedWhenSaved = observation.mountedWhenSaved;
        helper.assertTrue(player.hasDisconnected() && (cancelled || !player.isPassenger()), "Native disconnect did not detach");
        helper.assertTrue(rocket.flightData().orElseThrow().passengers().equals(before), "Logout changed the durable seat manifest");
        helper.assertTrue(!exits.state.live.containsKey(player) && !exits.state.refusals.containsKey(player)
                && exits.state.histories() == histories,
                "Disconnect started a bay exit or refusal");
        helper.assertTrue(!disk().contains("RootVehicle"), "Logout embedded another rocket authority in the player save");
        recovery.onPlayerLoggedOut(playerId);
        player = null;
    }

    public static final class LogoutObservation {
        private final ServerPlayer player;
        int saves, detachAttempts;
        boolean disconnecting = true, mountedWhenSaved;
        LogoutObservation(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
        public void mount(EntityMountEvent event) {
            if (event.isDismounting() && event.getEntityMounting() == player) {
                detachAttempts++; disconnecting &= player.hasDisconnected();
            }
        }
        @SubscribeEvent public void saved(PlayerEvent.SaveToFile event) {
            if (event.getEntity() == player) { saves++; mountedWhenSaved = player.isPassenger(); }
        }
    }

    void reconnect() {
        var committed = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        journal.put(committed); settled.add(record.transferId());
        RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve(
                io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType.ROCKET_TRANSFERS.fileName()));
        recovery.onPlayerLoggedIn(player, journal);
    }

    void reloadRocket() {
        var passengers = rocket.flightData().orElseThrow().passengers();
        var saved = rocket.saveWithoutId(new CompoundTag());
        rocket.discard();
        rocket = ModEntities.ROCKET.get().create(helper.getLevel());
        helper.assertTrue(rocket != null, "Reloaded logout rocket type unavailable");
        rocket.load(saved);
        helper.assertTrue(helper.getLevel().addFreshEntity(rocket) && rocket.operational()
                && rocket.flightData().orElseThrow().passengers().equals(passengers), "Reload lost the retained seat manifest");
    }

    @Override public void close() {
        if (closed) { return; } closed = true;
        ACTIVE.remove(this);
        journal.abort(); recovery.clear();
        if (player != null && helper.getLevel().getServer().getPlayerList().getPlayer(playerId) == player) {
            player.connection.onDisconnect(Component.literal("W312 fixture cleanup")); player = null;
        }
        for (var other : others) {
            if (helper.getLevel().getServer().getPlayerList().getPlayer(other.getUUID()) == other) {
                other.connection.onDisconnect(Component.literal("W312F fixture cleanup"));
            }
        }
        extras.forEach(net.minecraft.world.entity.Entity::discard);
        if (rocket != null) { rocket.discard(); }
        for (var entry : original.entrySet()) {
            helper.getLevel().setBlock(entry.getKey(), entry.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static void closeAbandoned() { for (var fixture : List.copyOf(ACTIVE)) { fixture.close(); } }
}
