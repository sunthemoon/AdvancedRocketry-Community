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
        stationCase(helper, false, false);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void loggingBackIntoZeroGravityDriftStillReturnsBeforeAKick(GameTestHelper helper) {
        stationCase(helper, true, false);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void aSupportedZeroGravityPlayerIsNotReturned(GameTestHelper helper) {
        stationCase(helper, false, true);
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void anOverworldSafetyReturnDoesNotCarryTheFloatingCounter(GameTestHelper helper) {
        try (Fixture f = new Fixture(helper, CelestialIds.SPACE_LEVEL, "gravityReturn")) {
            var server = f.player.server;
            var data = StationRegistrySavedData.get(server);
            int halfGrid = io.github.sunthemoon.advancedrocketrycommunity.station.model.StationLimits.GRID_SPACING / 2;
            BlockPos gap = new BlockPos(halfGrid * 75, 220, halfGrid * 75);
            helper.assertTrue(data.findAt(gap.getX(), gap.getZ()).isEmpty(), "The fixture is inside a station");
            f.level.getChunkAt(gap);
            f.player.teleportTo(f.level, gap.getX() + 0.5D, gap.getY(), gap.getZ() + 0.5D, 0, 0);
            f.acknowledge();
            var service = io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnRuntime
                    .current().orElseThrow();
            int ticks = 0;
            while (f.player.level() == f.level && ticks++ < 200) {
                f.tick(0.0D);
                helper.assertTrue(f.connected(), "Disconnected before the safety return at " + ticks);
                helper.assertTrue(CelestialGravityController.appliedMultiplier(f.player) == 0.0D,
                        "The outside-region fixture did not receive zero gravity");
                service.tickServer(server);
            }
            helper.assertTrue(ticks >= 100 && f.player.level() == server.overworld(), "No Overworld safety return");
            int arrivalCounter = f.player.connection.aboveGroundTickCount;
            // Do not acknowledge yet: the client can still be reporting the old zero-g floating state.
            f.player.connection.tick();
            helper.assertTrue(f.connected(), "The first connection tick after the return kicked the player; arrival counter="
                    + arrivalCounter);
            helper.assertTrue(arrivalCounter == 0, "The successful return retained counter " + arrivalCounter);
            helper.assertTrue(f.tokens.contains(SpaceSafetyReturnService.ARRIVED_OVERWORLD),
                    "The return was not announced: " + f.tokens);
            f.acknowledge();
            for (int tick = 1; tick <= 100; tick++) {
                f.tick(0.0D);
                helper.assertTrue(f.connected() && f.player.level() == server.overworld(),
                        "The returned player did not remain connected in the Overworld at " + tick);
            }
        }
        helper.succeed();
    }

    /**
     * W294 (finding 6-4): at 0 % a player standing on the platform reports {@code onGround} false. Vanilla would then
     * divide dig speed by 5 as if airborne; supported, they dig at ground speed. Off the platform, and standing at 1 g
     * with the airborne flag, vanilla's penalty stays.
     */
    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 100)
    public static void aPlayerStandingOnAZeroGravityPlatformDigsAtGroundSpeed(GameTestHelper helper) {
        var stone = Blocks.STONE.defaultBlockState();
        try (Fixture f = new Fixture(helper, CelestialIds.SPACE_LEVEL, "gravityDig")) {
            StationRegistrySavedData data = StationRegistrySavedData.get(f.player.server);
            UUID id = UUID.randomUUID();
            data.reserve(id, f.player.getUUID(), "Gravity dig", CelestialIds.EARTH_ID, f.level.getGameTime());
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
                f.player.teleportTo(f.level, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 0.5D, 0, 0);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) { f.level.getChunkAt(f.player.blockPosition().offset(x * 16, 0, z * 16)); }
                }
                f.acknowledge();
                for (int tick = 0; tick < 3; tick++) { f.tick(0.0D); }
                helper.assertTrue(CelestialGravityController.appliedMultiplier(f.player) == 0.0D && !f.player.onGround(),
                        "The fixture is not a 0 % player reporting onGround false");
                float floating = f.player.getDigSpeed(stone, pad.below());
                float ground = groundDigSpeed(f.player, stone, pad.below());
                helper.assertTrue(floating == ground, "A player standing on a 0 % platform digs at " + floating
                        + " instead of the ground speed " + ground);
                // Off the platform edge (inside the region, unsupported), the airborne penalty stays.
                f.player.teleportTo(f.level, pad.getX() + 0.5D, pad.getY(), pad.getZ() + 10.5D, 0, 0);
                f.acknowledge();
                f.tick(0.0D);
                float adrift = f.player.getDigSpeed(stone, pad.below());
                helper.assertTrue(!f.player.onGround() && adrift == groundDigSpeed(f.player, stone, pad.below()) / 5.0F,
                        "An unsupported 0 % player lost vanilla's airborne dig penalty: " + adrift);
            } finally {
                f.level.setBlockAndUpdate(pad.below(), floor);
                f.level.setBlockAndUpdate(pad, feet);
                f.level.setBlockAndUpdate(pad.above(), head);
                data.delete(id);
            }
        }
        try (Fixture f = new Fixture(helper, net.minecraft.world.level.Level.OVERWORLD, "gravityDigNormal")) {
            BlockPos below = f.player.blockPosition().below();
            var original = f.level.getBlockState(below);
            try {
                f.level.setBlockAndUpdate(below, Blocks.SMOOTH_STONE.defaultBlockState());
                f.tick(0.0D);
                f.player.setOnGround(false); // As the client reports a jump just above the block.
                float airborne = f.player.getDigSpeed(stone, below);
                helper.assertTrue(!f.player.onGround() && airborne == groundDigSpeed(f.player, stone, below) / 5.0F,
                        "A 1 g player reporting airborne over a block lost vanilla's dig penalty: " + airborne);
            } finally { f.level.setBlockAndUpdate(below, original); }
        }
        helper.succeed();
    }

    /** The same player's dig speed with the ground flag set, then the flag restored. */
    private static float groundDigSpeed(ServerPlayer player, net.minecraft.world.level.block.state.BlockState state,
                                        BlockPos pos) {
        boolean onGround = player.onGround();
        player.setOnGround(true);
        try {
            return player.getDigSpeed(state, pos);
        } finally { player.setOnGround(onGround); }
    }

    private static void stationCase(GameTestHelper helper, boolean reconnect, boolean supported) {
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
                f.player.teleportTo(f.level, pad.getX() + 0.5D, pad.getY(), pad.getZ() + (supported ? 0.5D : 10.5D), 0, 0);
                for (int x = -1; x <= 1; x++) {
                    for (int z = -1; z <= 1; z++) { f.level.getChunkAt(f.player.blockPosition().offset(x * 16, 0, z * 16)); }
                }
                f.acknowledge();
                if (reconnect) {
                    Vec3 saved = f.player.position();
                    f.rejoin();
                    f.player.setGameMode(GameType.ADVENTURE);
                    helper.assertTrue(f.player.position().distanceToSqr(saved) < 0.01D,
                            "Login did not restore the saved adrift position");
                }
                helper.assertTrue(data.findAt(f.player.getBlockX(), f.player.getBlockZ()).isPresent(),
                        "The drift fixture must be inside the station region");
                for (int tick = 1; tick < 80 && f.tokens.isEmpty(); tick++) {
                    f.tick(0.0D);
                    // The mock connection drives player ticks; drive the normal server END service separately.
                    io.github.sunthemoon.advancedrocketrycommunity.station.safety.SpaceSafetyReturnRuntime.current()
                            .orElseThrow().tickServer(f.player.server);
                    helper.assertTrue(f.connected(), "Station drift disconnected before recovery at " + tick);
                }
                if (supported) {
                    helper.assertTrue(f.tokens.isEmpty() && f.connected(), "A supported player was returned: " + f.tokens);
                } else {
                    helper.assertTrue(f.tokens.contains(SpaceSafetyReturnService.ARRIVED_STATION + "(Gravity rescue)"),
                            "No station safety-return message: " + f.tokens);
                }
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
        ServerPlayer player;
        final List<String> tokens = new ArrayList<>();
        EmbeddedChannel channel;
        final boolean allowed;
        final double startY;

        Fixture(GameTestHelper helper, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> key,
                String name) {
            var server = helper.getLevel().getServer();
            allowed = server.isFlightAllowed();
            server.setFlightAllowed(false);
            level = server.getLevel(key);
            helper.assertTrue(level != null, "Missing fixture Level");
            join(UUID.randomUUID(), name);
            player.setGameMode(GameType.SURVIVAL);
            BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
            startY = 220;
            level.getChunkAt(at);
            player.teleportTo(level, at.getX() + 0.5D, startY, at.getZ() + 0.5D, 0, 0);
            acknowledge();
            tokens.clear();
        }

        void join(UUID id, String name) {
            var server = level.getServer();
            player = new ServerPlayer(server, level, new GameProfile(id, name)) {
                @Override public void sendSystemMessage(Component message) { tokens.add(ConnectedTestPlayers.token(message)); }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, player);
        }

        void rejoin() {
            UUID id = player.getUUID();
            String name = player.getGameProfile().getName();
            player.server.getPlayerList().remove(player);
            channel.finishAndReleaseAll();
            join(id, name);
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
