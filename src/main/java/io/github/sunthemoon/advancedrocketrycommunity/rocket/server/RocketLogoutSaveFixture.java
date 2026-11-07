package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
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

/** Native connected survival logins; unlike the general fixture, rejoin never teleports over the loaded save. */
final class RocketLogoutSaveFixture implements AutoCloseable {
    static final BlockPos ORIGIN = new BlockPos(3, 40, 3);
    final GameTestHelper helper;
    final UUID playerId = UUID.randomUUID();
    final RocketTransferSavedData journal = new RocketTransferSavedData();
    final HashSet<UUID> live = new HashSet<>(), settled = new HashSet<>();
    final RocketTransferRecoveryService recovery = new RocketTransferRecoveryService(live, settled);
    final Map<BlockPos, BlockState> original = new LinkedHashMap<>();
    final List<EmbeddedChannel> channels = new ArrayList<>();
    final Path directory;
    final RocketTransferRecord record;
    RocketEntity rocket;
    ServerPlayer player;
    Vec3 exit;

    RocketLogoutSaveFixture(GameTestHelper helper, RocketFlightState state) throws IOException {
        this.helper = helper;
        directory = Files.createTempDirectory("arce-w312-");
        var level = helper.getLevel();
        var at = helper.absolutePos(ORIGIN);
        // An elevated, fixed-size platform avoids natural terrain and other batches' fixture blocks.
        for (int x = -1; x <= 3; x++) { for (int z = -1; z <= 1; z++) {
            block(at.offset(x, -1, z), Blocks.STONE.defaultBlockState());
        } }
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
            default -> throw new IllegalArgumentException("Unsupported logout fixture state " + state);
        };
        // stationarySource retains source fuel; explicitly establish an empty ASSEMBLED control.
        if (state == RocketFlightState.ASSEMBLED) {
            flight = io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightData.initial(
                    record.logicalRocketId(), 1_000, 1, record.sourceFlightData().currentBody(), level.dimension().location(),
                    snapshot.sourceOrigin(), level.getGameTime()).withPassengers(old.sourceFlightData().passengers());
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

    CompoundTag disk() {
        var file = helper.getLevel().getServer().getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(playerId + ".dat");
        try { return NbtIo.readCompressed(file.toFile()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    static Vec3 position(CompoundTag tag) {
        var pos = tag.getList("Pos", Tag.TAG_DOUBLE);
        return new Vec3(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
    }

    void logout() {
        var before = rocket.flightData().orElseThrow().passengers();
        player.setShiftKeyDown(true); // Unconsumed sneak is not LEAVE on a disconnect.
        player.connection.onDisconnect(Component.literal("W312 fixture logout"));
        helper.assertTrue(player.hasDisconnected() && !player.isPassenger(), "Native disconnect did not detach");
        helper.assertTrue(rocket.flightData().orElseThrow().passengers().equals(before), "Logout changed the durable seat manifest");
        var exits = RocketRuntime.passengerExits().orElseThrow();
        helper.assertTrue(!exits.state.live.containsKey(player) && !exits.state.refusals.containsKey(player),
                "Disconnect started a bay exit or refusal");
        helper.assertTrue(!disk().contains("RootVehicle"), "Logout embedded another rocket authority in the player save");
        recovery.onPlayerLoggedOut(playerId);
        player = null;
    }

    void reconnect() {
        var committed = record.destinationSpawned(rocket.getUUID()).advance(RocketTransferPhase.PASSENGERS_TRANSFERRED)
                .advance(RocketTransferPhase.SOURCE_REMOVED).advance(RocketTransferPhase.COMMITTED);
        journal.put(committed); settled.add(record.transferId());
        RocketJournalGameTestSupport.confirmDetached(helper, journal, directory.resolve("arce_rocket_transfers.dat"));
        recovery.onPlayerLoggedIn(player, journal);
    }

    @Override public void close() {
        journal.abort(); recovery.clear();
        if (player != null && helper.getLevel().getServer().getPlayerList().getPlayer(playerId) == player) {
            player.connection.onDisconnect(Component.literal("W312 fixture cleanup")); player = null;
        }
        if (rocket != null) { rocket.discard(); }
        for (var entry : original.entrySet()) {
            helper.getLevel().setBlock(entry.getKey(), entry.getValue(), net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        try (var paths = Files.walk(directory)) {
            for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) { Files.delete(path); }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
