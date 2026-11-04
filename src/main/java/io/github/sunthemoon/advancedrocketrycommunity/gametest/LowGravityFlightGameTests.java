package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnService;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real vanilla connection ticks, including movement admission and the default allow-flight=false kick. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LowGravityFlightGameTests {
    private static final String BATCH = "low_gravity_flight";

    private LowGravityFlightGameTests() { }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void aMoonPlayerCanFallPastEightyFloatingTicks(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, CelestialIds.MOON_LEVEL, "gravityMoon")) {
            for (int tick = 1; tick <= 120; tick++) {
                f.tick(-0.02D);
                helper.assertTrue(f.connected(), "Moon descent disconnected at tick " + tick);
            }
            helper.assertTrue(f.player.getY() < f.startY - 2.0D, "The Moon player did not descend");
            helper.assertTrue(Math.abs(CelestialGravityController.appliedMultiplier(f.player) - 0.165D) < 1.0E-9D,
                    "The fixture did not receive Moon gravity");
            helper.assertTrue(f.player.connection.aboveGroundTickCount > 0, "Gravity must not reset the counter");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void normalGravityStillKicksAfterEightyFloatingTicks(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, net.minecraft.world.level.Level.OVERWORLD, "gravityNormal")) {
            for (int tick = 1; tick <= 80; tick++) {
                f.tick(0.0D);
                helper.assertTrue(f.connected(), "Normal gravity kicked early at " + tick);
            }
            helper.assertTrue(f.player.connection.aboveGroundTickCount == 80, "Vanilla counter changed");
            f.tick(0.0D);
            helper.assertTrue(!f.connected(), "Normal gravity no longer enforces vanilla's limit");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void hoveringOnTheMoonStillHasAFiniteAllowance(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, CelestialIds.MOON_LEVEL, "gravityHover")) {
            // ceil(80 / 0.165), not an unlimited flight exemption.
            for (int tick = 1; tick <= 485; tick++) {
                f.tick(0.0D);
                helper.assertTrue(f.connected(), "Moon allowance ended early at " + tick);
            }
            f.tick(0.0D);
            helper.assertTrue(!f.connected(), "Moon hovering exceeded its finite allowance");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void zeroGravityStationDriftReturnsBeforeVanillaCanKick(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, CelestialIds.SPACE_LEVEL, "gravityStation")) {
            StationRegistrySavedData data = StationRegistrySavedData.get(f.player.server);
            UUID id = UUID.randomUUID();
            data.reserve(id, f.player.getUUID(), "Gravity rescue", CelestialIds.EARTH_ID, f.level.getGameTime());
            data.commit(id);
            StationState station = data.find(id).orElseThrow();
            BlockPos pad = new BlockPos(station.landingPad().x(), station.landingPad().y(), station.landingPad().z());
            f.level.getChunkAt(pad);
            var floor = f.level.getBlockState(pad.below());
            var feet = f.level.getBlockState(pad);
            var head = f.level.getBlockState(pad.above());
            try {
                f.level.setBlockAndUpdate(pad.below(), Blocks.SMOOTH_STONE.defaultBlockState());
                f.level.setBlockAndUpdate(pad, Blocks.AIR.defaultBlockState());
                f.level.setBlockAndUpdate(pad.above(), Blocks.AIR.defaultBlockState());
                // The initial platform ends at z+8. This is inside the region, but unsupported at z+10.
                f.player.teleportTo(f.level, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 10.5D, 0, 0);
                f.level.getChunkAt(f.player.blockPosition());
                f.acknowledge();
                helper.assertTrue(data.findAt(f.player.getBlockX(), f.player.getBlockZ()).isPresent(),
                        "The drift fixture must be inside the station region");
                for (int tick = 1; tick < 80 && f.tokens.isEmpty(); tick++) {
                    f.tick(0.0D);
                    // The mock connection drives player ticks; drive the normal server END service separately.
                    io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnRuntime.current()
                            .orElseThrow().tickServer(f.player.server);
                    helper.assertTrue(f.connected(), "Station drift disconnected before recovery at " + tick);
                }
                helper.assertTrue(f.tokens.contains(SpaceSafetyReturnService.ARRIVED_STATION + "(Gravity rescue)"),
                        "No station safety-return message: " + f.tokens);
                helper.assertTrue(f.player.position().distanceToSqr(pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D) < 1,
                        "The player was not returned to the station's safe point");
            } finally {
                f.level.setBlockAndUpdate(pad.below(), floor);
                f.level.setBlockAndUpdate(pad, feet);
                f.level.setBlockAndUpdate(pad.above(), head);
                data.delete(id);
            }
        }
        helper.succeed();
    }

    /** Each tick is a real admitted airborne packet followed by ServerGamePacketListenerImpl.tick(). */
    private static final class Fixture implements AutoCloseable {
        final ServerLevel level;
        final ServerPlayer player;
        final List<String> tokens = new ArrayList<>();
        final EmbeddedChannel channel;
        final boolean allowed;
        final double startY;

        Fixture(GameTestHelper helper, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key,
                String name) {
            var server = helper.getLevel().getServer();
            allowed = server.isFlightAllowed();
            server.setFlightAllowed(false);
            level = server.getLevel(key);
            helper.assertTrue(level != null, "Missing fixture Level");
            player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name)) {
                @Override public void sendSystemMessage(Component message) { tokens.add(ConnectedTestPlayers.token(message)); }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
            startY = 220;
            level.getChunkAt(at);
            player.teleportTo(level, at.getX() + 0.5D, startY, at.getZ() + 0.5D, 0, 0);
            acknowledge();
            tokens.clear();
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

        void tick(double dy) {
            player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(
                    player.getX(), player.getY() + dy, player.getZ(), false));
            player.connection.tick();
            player.setDeltaMovement(Vec3.ZERO);
            player.setOldPosAndRot();
        }

        boolean connected() { return player.connection.connection.isConnected(); }

        @Override public void close() {
            try {
                if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    player.server.getPlayerList().remove(player);
                }
                player.server.setFlightAllowed(allowed);
            } finally { channel.finishAndReleaseAll(); }
        }
    }
}
