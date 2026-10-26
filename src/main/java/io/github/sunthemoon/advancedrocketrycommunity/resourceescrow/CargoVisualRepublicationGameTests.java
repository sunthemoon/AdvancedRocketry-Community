package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.device.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualChunkPacket;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.network.RocketVisualSynchronizer;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundHandlerAdapter;
import io.netty.channel.ChannelPromise;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.game.ServerboundKeepAlivePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Observe the real tracking transport through native admission, reconciliation and C8 pruning. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CargoVisualRepublicationGameTests {
    private static Scenario running;

    @GameTest(template = "rocket_test", batch = "cargo_visual_fluid", timeoutTicks = 1200)
    public static void fluidLoadUnloadAndRefusalPublishOnlyChangedSnapshots(GameTestHelper helper) {
        running = new Scenario(helper, false); helper.onEachTick(running::tick);
    }

    @GameTest(template = "rocket_test", batch = "cargo_visual_item", timeoutTicks = 1200)
    public static void itemLoadUnloadAndRefusalPublishOnlyChangedSnapshots(GameTestHelper helper) {
        running = new Scenario(helper, true); helper.onEachTick(running::tick);
    }

    @AfterBatch(batch = "cargo_visual_fluid")
    public static void cleanupFluid(ServerLevel level) { cleanup(); }
    @AfterBatch(batch = "cargo_visual_item")
    public static void cleanupItem(ServerLevel level) { cleanup(); }
    private static void cleanup() { if (running != null) { running.close(); running = null; } }

    private static final class Scenario implements AutoCloseable {
        final GameTestHelper helper;
        final boolean item;
        // Both endpoints and nearby trackers stay inside the fixture's entity-ticking ticket center.
        final BlockPos at = new BlockPos(28008, 160, 28008);
        final EscrowCargoGameTestWorld world;
        final GameTestTickPacer pacer;
        final List<Observer> observers = new ArrayList<>();
        RocketEntity rocket;
        RocketFluidCargoBlockEntity fluidLoad, fluidUnload;
        RocketItemCargoBlockEntity itemLoad, itemUnload;
        String initial, loaded;
        int phase, since;
        boolean done;

        Scenario(GameTestHelper helper, boolean item) {
            this.helper = helper; this.item = item;
            world = new EscrowCargoGameTestWorld(helper, List.of(at, at.offset(512, 0, 0)));
            pacer = new GameTestTickPacer(world.server);
        }

        void tick() {
            if (done) { return; }
            pacer.pace(); observers.forEach(Observer::pump);
            observers.forEach(observer -> check(observer.failure == null, "Transport observer failed: " + observer.failure));
            if (phase == 0) {
                if (!world.ready()) { return; }
                check(world.level.isPositionEntityTicking(at.offset(2, 0, 0))
                        && world.level.isPositionEntityTicking(at.offset(-2, 0, 0)), "Both cargo endpoints must be entity-ticking");
                world.isolate();
                observe(at.offset(0, 2, 4), "CargoWatcher");
                observe(at.offset(512, 2, 0), "CargoDistant");
                rocket = item ? world.itemRocket(at, 0) : world.rocket(at, FluidStack.EMPTY);
                if (item) {
                    itemLoad = world.itemDevice(at.offset(2, 0, 0), rocket, RocketCargoRole.LOAD, 1);
                    itemUnload = world.itemDevice(at.offset(-2, 0, 0), rocket, RocketCargoRole.UNLOAD, 0);
                } else {
                    fluidLoad = world.device(at.offset(2, 0, 0), rocket, RocketCargoRole.LOAD, 1000);
                    fluidUnload = world.device(at.offset(-2, 0, 0), rocket, RocketCargoRole.UNLOAD, 0);
                }
                initial = hash(); phase = 1;
            }
            if (phase == 1) {
                if (observers.get(0).packets.isEmpty()) { return; }
                expect(observers.get(0), List.of(initial)); expect(observers.get(1), List.of());
                refuse();
                enable(true, true); phase = 2;
            }
            if (phase == 2 || phase == 4) {
                boolean load = phase == 2;
                var op = member(load);
                if (op != null) {
                    enable(load, false);
                    check(!op.reason().manual(), "Cargo operation entered manual repair");
                }
                if (rocket.escrowSection().epoch() != (load ? 1 : 2) || op != null || world.data() == null
                        || !world.data().bind(world.server).writerIdle()) { return; }
                check(rocket.escrowSection().markers().isEmpty(), "C8 did not prune the cargo marker");
                if (load) {
                    loaded = hash(); check(!initial.equals(loaded), "Load did not change the snapshot hash");
                    expect(observers.get(0), List.of(initial, loaded));
                    // A late tracker gets the current snapshot; existing trackers must not be rebroadcast to.
                    observe(at.offset(0, 2, -4), "CargoLate");
                    phase = 3;
                } else {
                    check(initial.equals(hash()), "Unload did not restore the original cargo snapshot");
                    expect(observers.get(0), List.of(initial, loaded, initial));
                    expect(observers.get(2), List.of(loaded, initial));
                    refuse(); since = world.server.getTickCount(); phase = 5;
                }
            }
            if (phase == 3) {
                if (observers.get(2).packets.isEmpty()) { return; }
                expect(observers.get(0), List.of(initial, loaded)); expect(observers.get(2), List.of(loaded));
                enable(false, true); phase = 4;
            }
            if (phase == 5) {
                expect(observers.get(0), List.of(initial, loaded, initial));
                expect(observers.get(1), List.of()); expect(observers.get(2), List.of(loaded, initial));
                if (world.server.getTickCount() - since < 20) { return; }
                close(); helper.succeed();
            }
        }

        void refuse() {
            String before = hash();
            var status = item ? itemUnload.attempt() : fluidUnload.attempt();
            check(status == RocketCargoStatus.ADMISSION_DISABLED, "Expected a refused operation with admission closed");
            check(before.equals(hash()), "Refusal changed the rocket snapshot");
            RocketVisualSynchronizer.snapshotChanged(rocket, rocket.snapshot().orElseThrow());
        }
        String hash() { return rocket.snapshot().orElseThrow().contentHash(); }
        void enable(boolean load, boolean enabled) {
            if (item) { world.enable(load ? itemLoad : itemUnload, enabled); }
            else { world.enable(load ? fluidLoad : fluidUnload, enabled); }
        }
        EscrowOperation member(boolean load) {
            return item ? world.member(load ? itemLoad : itemUnload) : world.member(load ? fluidLoad : fluidUnload);
        }
        void observe(BlockPos position, String name) {
            // Install the passive observer before entering tracking range, including the late-join case.
            var player = new ServerPlayer(world.server, world.level, new GameProfile(UUID.randomUUID(), name));
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            world.server.getPlayerList().placeNewPlayer(connection, player);
            world.players.add(player); observers.add(new Observer(player, channel));
            player.setGameMode(GameType.CREATIVE);
            player.teleportTo(world.level, position.getX() + 0.5, position.getY(), position.getZ() + 0.5, 0, 0);
        }
        void expect(Observer observer, List<String> hashes) {
            check(observer.packets.size() == hashes.size(), "Expected exactly " + hashes.size() + " visual packets for "
                    + observer.player.getGameProfile().getName() + ", got " + observer.packets.size());
            for (int i = 0; i < hashes.size(); i++) {
                var packet = observer.packets.get(i);
                check(packet.entityId().equals(rocket.getUUID()) && packet.structureContentHash().equals(hashes.get(i))
                        && packet.chunkIndex() == 0 && packet.chunkCount() == 1
                        && packet.totalBytes() <= RocketLimits.MAX_VISUAL_SNAPSHOT_BYTES
                        && packet.chunk().length <= RocketLimits.MAX_VISUAL_CHUNK_BYTES, "Wrong or unbounded cargo visual packet");
            }
        }
        void check(boolean condition, String message) { helper.assertTrue(condition, message); }
        @Override public void close() {
            if (done) { return; } done = true;
            io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity.LOGGER.info(
                    "ARCE_CARGO_VISUAL_TEST item={} phase={} epoch={} packets={} unload_status={} unload_entity_ticking={}",
                    item, phase, rocket == null ? -1 : rocket.escrowSection().epoch(),
                    observers.stream().map(observer -> observer.packets.size()).toList(),
                    item ? itemUnload == null ? null : itemUnload.displayStatus()
                            : fluidUnload == null ? null : fluidUnload.displayStatus(),
                    world.level.isPositionEntityTicking(at.offset(-2, 0, 0)));
            try { world.close(); } finally { observers.forEach(Observer::close); }
        }
    }

    private static final class Observer implements AutoCloseable {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final List<RocketVisualChunkPacket> packets = new ArrayList<>();
        String failure;
        Observer(ServerPlayer player, EmbeddedChannel channel) {
            this.player = player; this.channel = channel;
            channel.pipeline().addFirst("cargo-visual-observer", new ChannelOutboundHandlerAdapter() {
                @Override public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) throws Exception {
                    try {
                        if (message instanceof ClientboundCustomPayloadPacket packet
                                && packet.getIdentifier().equals(ModIdentity.id("rocket_visual"))) {
                            var data = new FriendlyByteBuf(packet.getData().duplicate());
                            if (data.readVarInt() != 0 || packets.size() >= 16) { throw new IllegalStateException("Unexpected visual packet"); }
                            packets.add(RocketVisualChunkPacket.decode(data));
                        }
                    } catch (RuntimeException problem) { failure = problem.toString(); }
                    super.write(context, message, promise);
                }
            });
        }
        void pump() {
            player.connection.tick();
            Object message;
            while ((message = channel.readOutbound()) != null) {
                if (message instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
                io.netty.util.ReferenceCountUtil.release(message);
            }
        }
        @Override public void close() {
            channel.pipeline().remove("cargo-visual-observer"); channel.finishAndReleaseAll();
        }
    }
}
