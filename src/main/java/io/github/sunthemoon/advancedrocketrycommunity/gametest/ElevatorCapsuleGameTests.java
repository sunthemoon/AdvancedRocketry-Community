package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorAnchorBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorCapsuleCodec;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorPairs;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRides;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Real ride requests and vanilla ChunkHolder packet delivery, with an observer left at the departure. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorCapsuleGameTests {
    private static final String CANCEL = "elevator_capsule_cancel";
    private static final String COMMIT = "elevator_capsule_commit";
    private static final String EXPIRY = "elevator_capsule_expiry";
    private static final String UNBIND = "elevator_capsule_unbind";
    private static Runnable cleanup = () -> { };

    private ElevatorCapsuleGameTests() {
    }

    @AfterBatch(batch = CANCEL)
    public static void afterCancel(ServerLevel level) { clean(); }

    @AfterBatch(batch = COMMIT)
    public static void afterCommit(ServerLevel level) { clean(); }

    @AfterBatch(batch = EXPIRY)
    public static void afterExpiry(ServerLevel level) { clean(); }

    @AfterBatch(batch = UNBIND)
    public static void afterUnbind(ServerLevel level) { clean(); }

    private static void clean() {
        Runnable action = cleanup;
        cleanup = () -> { };
        action.run();
    }

    private static final class ProjectionFixture {
        final GameTestHelper helper;
        final ElevatorGameTests.Fixture fixture;
        final List<CompoundTag> sent = new ArrayList<>();
        final List<ServerPlayer> joined = new ArrayList<>();
        ServerPlayer rider;
        long requestedAt;
        int pendingPackets;

        ProjectionFixture(GameTestHelper helper) {
            this.helper = helper;
            fixture = new ElevatorGameTests.Fixture(helper);
            cleanup = () -> {
                rides().cancelPair(fixture.server, EndgameRuntime.operational().orElseThrow().root().orElseThrow()
                        .pairs().forStation(fixture.stationId).map(pair -> pair.pairId()).orElse(new UUID(0, 0)));
                joined.forEach(player -> fixture.server.getPlayerList().remove(player));
                fixture.close();
            };
        }

        void bind() {
            fixture.bind(helper);
            fixture.anchor().storage().energy().set(200_000);
        }

        void request() {
            ServerPlayer observer = ConnectedTestPlayers.join(fixture.server, UUID.randomUUID(), "capsuleObserver",
                    fixture.level, fixture.anchorPos.above().north(), new ArrayList<>());
            joined.add(observer);
            observer.connection = new ServerGamePacketListenerImpl(fixture.server, observer.connection.connection,
                    observer) {
                @Override
                public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundBlockEntityDataPacket update
                            && update.getPos().equals(fixture.anchorPos) && update.getTag() != null) {
                        sent.add(update.getTag().copy());
                    }
                    super.send(packet);
                }
            };
            rider = ConnectedTestPlayers.join(fixture.server, fixture.owner, "capsuleRider", fixture.level,
                    fixture.anchorPos.above(), new ArrayList<>());
            joined.add(rider);
            flush();
            sent.clear();
            EndgameCode requested = rides().request(rider, fixture.anchor(), EndgameRuntime.operational().orElseThrow(),
                    EndgameRuntime.devices().orElseThrow()).code();
            helper.assertTrue(requested == EndgameCode.RIDE_COUNTDOWN, "Ride request failed: " + requested);
            requestedAt = fixture.server.overworld().getGameTime();
            flush();
            helper.assertTrue(sent.size() == 1, "The add did not send exactly one vanilla departure packet: " + sent);
            CompoundTag update = sent.get(0);
            helper.assertTrue(update.getBoolean("tether") && update.size() == 2, "Tether changed or private data leaked");
            helper.assertTrue(ElevatorCapsuleCodec.decode(update).equals(List.of(
                    new ElevatorCapsuleCodec.Entry(rider.getId(), ElevatorRules.RIDE_COUNTDOWN_TICKS))),
                    "The pending rider/countdown was not projected: " + update);
            helper.assertTrue(ElevatorCapsuleCodec.decode(fixture.terminal().getUpdateTag()).isEmpty(),
                    "The arrival endpoint projected a departing rider");
            // Vanilla packet application must not invoke persistent load or erase endpoint identity/energy.
            ElevatorAnchorBlockEntity replica = new ElevatorAnchorBlockEntity(fixture.anchorPos,
                    fixture.anchor().getBlockState());
            replica.load(fixture.anchor().saveWithoutMetadata());
            replica.onDataPacket(observer.connection.connection, fixture.anchor().getUpdatePacket());
            helper.assertTrue(replica.deviceId().equals(fixture.anchor().deviceId())
                            && replica.storage().energy().energy() == 200_000 && replica.tetherForRender()
                            && replica.capsulesForRender().size() == 1,
                    "The packet changed persistent state or failed to reach a replica");
            ElevatorAnchorBlockEntity.clientTick(fixture.level, fixture.anchorPos, replica.getBlockState(), replica);
            helper.assertTrue(replica.capsulesForRender().equals(List.of(
                    new ElevatorCapsuleCodec.Entry(rider.getId(), ElevatorRules.RIDE_COUNTDOWN_TICKS - 1))),
                    "The client ticker did not advance its local countdown");
            replica.onChunkUnloaded();
            helper.assertTrue(replica.capsulesForRender().isEmpty(), "Unload kept the capsule lease");
            replica.handleUpdateTag(update);
            replica.setRemoved();
            helper.assertTrue(replica.capsulesForRender().isEmpty(), "Removal kept the capsule lease");
            pendingPackets = sent.size();
        }

        void ordinaryTicks() {
            flush();
            helper.assertTrue(rides().pending(rider.getUUID()).isPresent(), "The real ride ended prematurely");
            helper.assertTrue(sent.size() == pendingPackets, "Ordinary countdown/wait ticks sent updates: " + sent);
            int expected = (int) Math.max(0, ElevatorRules.RIDE_COUNTDOWN_TICKS
                    - (fixture.server.overworld().getGameTime() - requestedAt));
            helper.assertTrue(ElevatorCapsuleCodec.decode(fixture.anchor().getUpdateTag())
                            .equals(List.of(new ElevatorCapsuleCodec.Entry(rider.getId(), expected))),
                    "A late watcher would receive a stale countdown");
        }

        void ended(boolean committed) {
            flush();
            helper.assertTrue(rides().pending(rider.getUUID()).isEmpty(), "The ride remains pending");
            helper.assertTrue(sent.size() == pendingPackets + 1, "Ride removal sent the wrong number of updates: " + sent);
            CompoundTag update = sent.get(sent.size() - 1);
            helper.assertTrue(ElevatorCapsuleCodec.decode(update).isEmpty(), "The end packet retained the rider");
            helper.assertTrue(fixture.anchor().storage().energy().energy() == (committed ? 150_000 : 200_000),
                    "The projection changed ride energy");
            ElevatorAnchorBlockEntity replica = new ElevatorAnchorBlockEntity(fixture.anchorPos,
                    fixture.anchor().getBlockState());
            replica.handleUpdateTag(sent.get(0));
            helper.assertTrue(replica.capsulesForRender().size() == 1, "The replica never saw the pending rider");
            replica.handleUpdateTag(update);
            helper.assertTrue(replica.capsulesForRender().isEmpty(), "The end packet did not clear the client clock");
        }

        void flush() {
            try {
                Method method = ChunkMap.class.getDeclaredMethod("getVisibleChunkIfPresent", long.class);
                method.setAccessible(true);
                ChunkPos position = new ChunkPos(fixture.anchorPos);
                ChunkHolder holder = (ChunkHolder) method.invoke(fixture.level.getChunkSource().chunkMap,
                        position.toLong());
                helper.assertTrue(holder != null, "The loaded departure has no ChunkHolder");
                holder.broadcastChanges(fixture.level.getChunkAt(fixture.anchorPos));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("Could not flush the real departure's vanilla update queue", exception);
            }
        }
    }

    @GameTest(template = "empty", batch = CANCEL, timeoutTicks = 1200)
    public static void pendingRideIsProjectedWithoutTickSpamAndCancelClearsIt(GameTestHelper helper) {
        ProjectionFixture test = new ProjectionFixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> test.fixture.registered(helper))
                .thenExecute(test::bind)
                .thenWaitUntil(() -> helper.assertTrue(test.fixture.anchor().tetherForRender(), "Tether not synchronized"))
                .thenExecute(test::request)
                .thenExecuteAfter(30, test::ordinaryTicks)
                .thenExecute(() -> test.rider.teleportTo(test.fixture.anchorPos.getX() + 4.5D,
                        test.fixture.anchorPos.getY() + 1.0D, test.fixture.anchorPos.getZ() + 0.5D))
                .thenWaitUntil(() -> helper.assertTrue(rides().pending(test.rider.getUUID()).isEmpty(), "Cancel pending"))
                .thenExecute(() -> test.ended(false))
                .thenExecute(ElevatorCapsuleGameTests::clean)
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = COMMIT, timeoutTicks = 1200)
    public static void committedRideSendsAnEmptyDepartureProjection(GameTestHelper helper) {
        ProjectionFixture test = new ProjectionFixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> test.fixture.registered(helper))
                .thenExecute(test::bind)
                .thenWaitUntil(() -> helper.assertTrue(test.fixture.anchor().tetherForRender(), "Tether not synchronized"))
                .thenExecute(test::request)
                .thenExecuteAfter(30, test::ordinaryTicks)
                .thenWaitUntil(() -> helper.assertTrue(rides().pending(test.rider.getUUID()).isEmpty(), "Commit pending"))
                .thenExecute(() -> {
                    test.ended(true);
                    helper.assertTrue(test.rider.level() == test.fixture.space
                            && test.fixture.terminal().onPlatform(test.rider), "The real rider did not teleport");
                })
                .thenExecute(ElevatorCapsuleGameTests::clean)
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = EXPIRY, timeoutTicks = 1200)
    public static void expiredArrivalWaitSendsAnEmptyDepartureProjection(GameTestHelper helper) {
        ProjectionFixture test = new ProjectionFixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> test.fixture.registered(helper))
                .thenExecute(test::bind)
                .thenWaitUntil(() -> helper.assertTrue(test.fixture.anchor().tetherForRender(), "Tether not synchronized"))
                .thenExecute(test::request)
                .thenExecute(() -> {
                    // Fault injection only: remove the fixture hold and the ride's pre-load ticket so the real
                    // arrival chunk unloads. Authority, dueTick and the production expiry logic are untouched.
                    var fixture = test.fixture;
                    fixture.space.setChunkForced(fixture.terminalChunk.x, fixture.terminalChunk.z, false);
                    fixture.space.getChunkSource().removeRegionTicket(ElevatorGameTests.FIXTURE_TICKET,
                            fixture.terminalChunk, 2, fixture.stationId);
                    fixture.space.getChunkSource().removeRegionTicket(ElevatorRides.ARRIVAL, fixture.terminalChunk,
                            0, test.rider.getUUID());
                })
                .thenExecuteAfter(130, () -> {
                    helper.assertTrue(test.fixture.space.getChunkSource().getChunkNow(test.fixture.terminalChunk.x,
                            test.fixture.terminalChunk.z) == null, "The injected arrival did not unload");
                    test.ordinaryTicks();
                })
                .thenWaitUntil(() -> helper.assertTrue(rides().pending(test.rider.getUUID()).isEmpty(), "Expiry pending"))
                .thenExecute(() -> {
                    test.ended(false);
                    helper.assertTrue(test.fixture.server.overworld().getGameTime() >= test.requestedAt
                                    + ElevatorRules.RIDE_COUNTDOWN_TICKS + ElevatorRules.ARRIVAL_WAIT_TICKS,
                            "The ride was cancelled before its arrival-wait expiry");
                    helper.assertTrue(test.rider.level() == test.fixture.level, "An expired ride teleported");
                    helper.assertTrue(EndgameRuntime.operational().orElseThrow().audit()
                            .page("space_elevator", 0).stream().anyMatch(line -> line.contains("ARRIVAL_UNLOADED")
                                    && line.contains(test.rider.getUUID().toString())), "Not an arrival-wait expiry");
                })
                .thenExecute(ElevatorCapsuleGameTests::clean)
                .thenSucceed();
    }

    @GameTest(template = "empty", batch = UNBIND, timeoutTicks = 1200)
    public static void unbindingThePairClearsThePendingProjection(GameTestHelper helper) {
        ProjectionFixture test = new ProjectionFixture(helper);
        helper.startSequence()
                .thenWaitUntil(() -> test.fixture.registered(helper))
                .thenExecute(test::bind)
                .thenWaitUntil(() -> helper.assertTrue(test.fixture.anchor().tetherForRender(), "Tether not synchronized"))
                .thenExecute(test::request)
                .thenExecute(() -> {
                    var service = EndgameRuntime.operational().orElseThrow();
                    var pair = service.root().orElseThrow().pairs().forStation(test.fixture.stationId).orElseThrow();
                    helper.assertTrue(ElevatorPairs.unbind(test.fixture.server, service,
                            EndgameRuntime.devices().orElseThrow(), pair, test.fixture.owner, false,
                            test.fixture.level.getGameTime()).ok(), "Unbind failed");
                    test.ended(false);
                })
                .thenExecute(ElevatorCapsuleGameTests::clean)
                .thenSucceed();
    }

    private static ElevatorRides rides() {
        return EndgameRuntime.devices().orElseThrow().elevatorRides();
    }
}
