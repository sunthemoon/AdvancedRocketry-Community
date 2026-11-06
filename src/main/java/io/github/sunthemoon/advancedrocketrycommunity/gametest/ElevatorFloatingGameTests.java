package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialGravityController;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
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
 * W303 (R345): an elevator ride from a zero-g station terminal does not carry vanilla's floating counter into the
 * Overworld. A connected survival rider, with flight disabled, hovers unsupported over the terminal's platform through
 * the real countdown, so W285's allowance holds the native counter at 80. The ride's own commit then moves the rider to
 * the anchor; the first connection tick before the client acknowledges that move, and the next 100 ticks, must keep the
 * rider connected. The mock connection is ticked by the test, as in {@link LowGravityFlightGameTests}; the ride commits
 * in the installed server END handler. One {@code onEachTick} per test (W28).
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorFloatingGameTests {
    private static final String BATCH = "endgame_elevator_floating";
    /** Feet height above the endpoint: on its platform cell, but beyond vanilla's 0.55-block support probe. */
    private static final double HOVER = 1.75D;
    private static final int CONNECTED_AFTER_ARRIVAL = 100;
    /** A test that fails or times out skips its own clean-up; the batch closes the fixture (C15bR1-L5). */
    private static Runnable cleanup = () -> { };

    private ElevatorFloatingGameTests() {
    }

    @AfterBatch(batch = BATCH)
    public static void cleanUp(ServerLevel level) {
        Runnable pending = cleanup;
        cleanup = () -> { };
        pending.run();
    }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 1200)
    public static void aZeroGravityRiderDoesNotCarryTheFloatingCounterToTheAnchor(GameTestHelper helper) {
        new Ride(helper).start();
    }

    private static final class Ride {
        final GameTestHelper helper;
        final GameTestTickPacer pacer;
        final ElevatorGameTests.Fixture fixture;
        final UUID riderId = UUID.randomUUID();
        final boolean flightAllowed;
        final BlockPos headroom;
        final BlockState headroomState;
        ServerPlayer rider;
        EmbeddedChannel channel;
        int phase, ticks, countdown, held, after;
        boolean closed;

        Ride(GameTestHelper helper) {
            this.helper = helper;
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            fixture = new ElevatorGameTests.Fixture(helper);
            fixture.stations.addMember(fixture.stationId, riderId);
            flightAllowed = fixture.server.isFlightAllowed();
            // The hovering rider's box reaches three cells above the terminal; the fixture clears only two.
            headroom = fixture.terminalPos.above(3);
            headroomState = fixture.space.getBlockState(headroom);
            cleanup = this::close;
        }

        void start() {
            helper.onEachTick(this::tick);
        }

        void tick() {
            if (closed) {
                return;
            }
            try {
                helper.assertTrue(++ticks < 1150, "Elevator floating fixture stalled at phase " + phase + "; "
                        + describe());
                if (phase == 0) {
                    if (ready()) {
                        board();
                        phase = 1;
                    }
                } else if (phase == 1) {
                    countdown();
                } else {
                    connectedAtAnchor();
                }
                if (!closed) {
                    pacer.pace();
                }
            } catch (RuntimeException | Error failure) {
                close();
                throw failure;
            }
        }

        /** The registration check of {@code ElevatorGameTests.Fixture.registered}, without failing while waiting. */
        boolean ready() {
            LaserTargetGameTests.chunkSaved(fixture.level, fixture.anchorPos);
            LaserTargetGameTests.chunkSaved(fixture.space, fixture.terminalPos);
            return fixture.anchor() != null && fixture.anchor().endpointActive()
                    && fixture.anchor().placement() == EndgameCode.OK && fixture.terminal() != null
                    && fixture.terminal().endpointActive() && fixture.terminal().placement() == EndgameCode.OK;
        }

        void board() {
            fixture.bind(helper);
            fixture.terminal().storage().energy().set(200_000);
            fixture.space.setBlock(headroom, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            var server = fixture.server;
            server.setFlightAllowed(false);
            rider = new ServerPlayer(server, fixture.space, new GameProfile(riderId, "elevatorFloat"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            server.getPlayerList().placeNewPlayer(connection, rider);
            rider.setGameMode(GameType.SURVIVAL);
            BlockPos terminal = fixture.terminalPos;
            rider.teleportTo(fixture.space, terminal.getX() + 0.5D, terminal.getY() + HOVER, terminal.getZ() + 0.5D,
                    0.0F, 0.0F);
            acknowledge();
            helper.assertTrue(rider.level() == fixture.space && fixture.terminal().onPlatform(rider)
                            && !rider.isCreative() && !rider.getAbilities().mayfly,
                    "The rider is not a survival player on the terminal's platform: " + describe());
            EndgameCode requested = rides().request(rider, fixture.terminal(), service(), devices()).code();
            helper.assertTrue(requested == EndgameCode.RIDE_COUNTDOWN, "The ride down was refused: " + requested);
        }

        void countdown() {
            if (rider.level() == fixture.level) {
                arrived();
                return;
            }
            helper.assertTrue(rider.level() == fixture.space && rides().pending(riderId).isPresent(),
                    "The ride ended without arriving: " + describe() + " audit=" + audit());
            airborne(true);
            countdown++;
            helper.assertTrue(connected() && rider.isAlive(), "The rider was lost during the countdown at "
                    + countdown + ": " + describe());
            helper.assertTrue(CelestialGravityController.appliedMultiplier(rider) == 0.0D,
                    "The terminal fixture is not in zero gravity: " + describe());
            if (rider.connection.aboveGroundTickCount == 80) {
                held++;
            }
        }

        void arrived() {
            int arrivalCounter = rider.connection.aboveGroundTickCount;
            helper.assertTrue(held >= 10, "The countdown did not hold the native counter at 80: held=" + held
                    + " countdown=" + countdown);
            helper.assertTrue(fixture.anchor().onPlatform(rider) && rides().pending(riderId).isEmpty(),
                    "The rider did not arrive on the anchor's platform: " + describe());
            // Do not acknowledge yet: the client can still be reporting its zero-g floating state.
            rider.connection.tick();
            helper.assertTrue(connected(), "The first connection tick after the ride kicked the rider; arrival counter="
                    + arrivalCounter);
            helper.assertTrue(arrivalCounter == 0, "The committed ride retained counter " + arrivalCounter);
            acknowledge();
            phase = 2;
        }

        void connectedAtAnchor() {
            airborne(true);
            after++;
            helper.assertTrue(connected() && rider.level() == fixture.level,
                    "The rider did not remain connected at the anchor at " + after + ": " + describe());
            if (after >= CONNECTED_AFTER_ARRIVAL) {
                close();
                helper.succeed();
            }
        }

        /** One client tick: acknowledge any teleport, report the same position airborne, then the connection tick. */
        void airborne(boolean acknowledge) {
            if (acknowledge) {
                acknowledge();
            }
            rider.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(rider.getX(), rider.getY(),
                    rider.getZ(), false));
            rider.connection.tick();
            rider.setDeltaMovement(Vec3.ZERO);
            rider.setOldPosAndRot();
        }

        void acknowledge() {
            Object packet;
            while ((packet = channel.readOutbound()) != null) {
                try {
                    if (packet instanceof ClientboundPlayerPositionPacket position) {
                        rider.connection.handleAcceptTeleportPacket(
                                new ServerboundAcceptTeleportationPacket(position.getId()));
                    }
                } finally {
                    io.netty.util.ReferenceCountUtil.release(packet);
                }
            }
        }

        boolean connected() {
            return rider.connection.connection.isConnected();
        }

        String describe() {
            return rider == null ? "no rider" : "level=" + rider.level().dimension().location() + " at="
                    + rider.position() + " counter=" + rider.connection.aboveGroundTickCount + " pending="
                    + rides().pending(riderId).isPresent() + " connected=" + connected();
        }

        List<String> audit() {
            List<String> lines = service().audit().page(EndgameSystem.SPACE_ELEVATOR.id(), 0);
            return lines.subList(Math.max(0, lines.size() - 3), lines.size());
        }

        void close() {
            if (closed) {
                return;
            }
            closed = true;
            cleanup = () -> { };
            var server = fixture.server;
            try {
                if (rider != null && server.getPlayerList().getPlayer(riderId) == rider) {
                    server.getPlayerList().remove(rider);
                }
                fixture.space.setBlock(headroom, headroomState, Block.UPDATE_CLIENTS);
                fixture.close();
                server.setFlightAllowed(flightAllowed);
            } finally {
                if (channel != null) {
                    channel.finishAndReleaseAll();
                }
            }
        }
    }

    private static ElevatorRides rides() {
        return devices().elevatorRides();
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices devices() {
        return EndgameRuntime.devices().orElseThrow();
    }

    private static io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService service() {
        return EndgameRuntime.operational().orElseThrow();
    }
}
