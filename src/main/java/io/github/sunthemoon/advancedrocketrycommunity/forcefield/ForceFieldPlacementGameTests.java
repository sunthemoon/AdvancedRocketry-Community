package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ForceFieldPlacementGameTests {
    static final class Placement implements AutoCloseable {
        final ServerLevel level;
        final BlockPos pos;
        final FakePlayer player;
        final ItemStack stack = new ItemStack(ModItems.FORCE_FIELD_PROJECTOR.get(), 2);
        Placement(GameTestHelper helper) {
            this(helper, new BlockPos(2_000_008 + helper.absolutePos(BlockPos.ZERO).getX() * 64, 220,
                    2_000_008 + helper.absolutePos(BlockPos.ZERO).getZ() * 64));
        }
        Placement(GameTestHelper helper, BlockPos position) {
            level = helper.getLevel().getServer().getLevel(CelestialIds.MOON_LEVEL);
            pos = position;
            level.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
            level.setBlock(pos.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            player = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "FieldPlacement"));
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        }
        InteractionResult place() {
            return stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                    new BlockHitResult(Vec3.atCenterOf(pos.below()), Direction.UP, pos.below(), false)));
        }
        @Override public void close() {
            if (level.getBlockEntity(pos) instanceof ForceFieldProjectorBlockEntity projector) {
                FieldCleanupSavedData.get(level.getServer()).forget(projector.reservationIdentity().key());
            }
            ForceFieldProtection.physical(level, pos, () -> level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE));
            level.setBlock(pos.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void lastSlotNativeSurvivalPlacementConservesStockAndRefusesTheNext(GameTestHelper helper) {
        try (var f = new Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            java.util.List<FieldIdentity.Key> fillers = new java.util.ArrayList<>();
            try {
                for (int i = data.view().size(); i < FieldCleanupRoot.MAX_ENTRIES - 1; i++) {
                    var id = new FieldIdentity(UUID.randomUUID(), new UUID(41, 42), 1, f.level.dimension(),
                            new BlockPos(25_000_000 + i, 100, 25_000_000), Direction.EAST);
                    helper.assertTrue(data.reserve(id, 0), "Last-slot fixture admission failed");
                    fillers.add(id.key());
                }
                helper.assertTrue(data.view().size() == 4095 && f.place().consumesAction(),
                        "Survival placement was refused at the last free slot");
                helper.assertTrue(f.stack.getCount() == 1 && f.level.getBlockState(f.pos).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())
                        && f.level.getBlockEntity(f.pos) instanceof ForceFieldProjectorBlockEntity projector
                        && projector.hasReservation() && data.view().size() == 4096,
                        "Last-slot placement lost inventory, world stock or its exact reservation");
                BlockPos next = f.pos.east();
                f.level.setBlock(next.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                try {
                    var before = data.save(new net.minecraft.nbt.CompoundTag());
                    var result = f.stack.useOn(new UseOnContext(f.player, InteractionHand.MAIN_HAND,
                            new BlockHitResult(Vec3.atCenterOf(next.below()), Direction.UP, next.below(), false)));
                    helper.assertTrue(!result.consumesAction() && f.stack.getCount() == 1 && f.level.getBlockState(next).is(Blocks.AIR)
                            && data.save(new net.minecraft.nbt.CompoundTag()).equals(before),
                            "Full-root item refusal consumed stock, placed a block or changed reservations");
                } finally { f.level.setBlock(next.below(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE); }
            } finally { fillers.forEach(data::forget); }
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void cancelledNativePlacementRollsBackDespiteFakePlayerBreakRefusal(GameTestHelper helper) {
        try (var f = new Placement(helper)) {
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var before = data.save(new net.minecraft.nbt.CompoundTag());
            int count = f.stack.getCount();
            AtomicInteger placements = new AtomicInteger();
            Consumer<BlockEvent.EntityPlaceEvent> vetoPlace = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)) {
                    helper.assertTrue(f.level.getBlockEntity(f.pos) instanceof ForceFieldProjectorBlockEntity projector
                            && projector.hasReservation() && data.view().size() == before.getList("entries", 10).size() + 1,
                            "Cancelled native placement did not hold its own admission reservation");
                    placements.incrementAndGet(); event.setCanceled(true);
                }
            };
            Consumer<BlockEvent.BreakEvent> vetoBreak = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)
                        && event.getPlayer() instanceof FakePlayer) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, vetoPlace);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, vetoBreak);
            try {
                helper.assertTrue(f.place() == InteractionResult.FAIL && placements.get() == 1,
                        "Native placement did not reach the cancelled Forge transaction");
                helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.AIR) && f.level.getBlockEntity(f.pos) == null
                        && f.stack.getCount() == count && data.save(new net.minecraft.nbt.CompoundTag()).equals(before),
                        "Cancelled ItemStack.useOn left a refunded projector or changed cleanup data");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(vetoPlace); MinecraftForge.EVENT_BUS.unregister(vetoBreak);
            }
        }
        helper.succeed();
    }
    /**
     * A connected creative placer beside the target, so native placement binds the projector to it (W04F review N6).
     * It joins over an embedded channel, as {@code ConnectedTestPlayers} does, because the Moon teleport sends packets;
     * it is in {@code joined} before the teleport, so the caller always removes it.
     */
    private static ServerPlayer connectedPlacer(Placement f, List<ServerPlayer> joined) {
        var server = f.level.getServer();
        ServerPlayer placer = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), "FieldPlacer")) {
            @Override public boolean isSpectator() { return false; }
            @Override public boolean isCreative() { return true; }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        server.getPlayerList().placeNewPlayer(connection, placer);
        joined.add(placer);
        placer.teleportTo(f.level, f.pos.getX() + 0.5, f.pos.getY(), f.pos.getZ() + 6.5, 0.0F, 0.0F);
        placer.setItemInHand(InteractionHand.MAIN_HAND, f.stack);
        return placer;
    }
    private static void leave(List<ServerPlayer> joined) {
        joined.forEach(player -> player.getServer().getPlayerList().remove(player));
    }
    private static InteractionResult place(Placement f, ServerPlayer placer) {
        return f.stack.useOn(new UseOnContext(placer, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(f.pos.below()), Direction.UP, f.pos.below(), false)));
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void cancelledConnectedPlacementRollsBackItsBoundReservationDespiteBreakRefusal(GameTestHelper helper) {
        List<ServerPlayer> joined = new ArrayList<>();
        try (var f = new Placement(helper)) {
            ServerPlayer placer = connectedPlacer(f, joined);
            UUID placerId = placer.getUUID();
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var before = data.save(new net.minecraft.nbt.CompoundTag());
            AtomicInteger placements = new AtomicInteger();
            Consumer<BlockEvent.EntityPlaceEvent> vetoPlace = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)) {
                    helper.assertTrue(f.level.getBlockEntity(f.pos) instanceof ForceFieldProjectorBlockEntity projector
                            && projector.state().owner().filter(placerId::equals).isPresent() && projector.hasReservation(),
                            "Connected placement did not hold a reservation bound to its placer");
                    placements.incrementAndGet(); event.setCanceled(true);
                }
            };
            Consumer<BlockEvent.BreakEvent> vetoBreak = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)
                        && event.getPlayer() != null && placerId.equals(event.getPlayer().getUUID())) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.EntityPlaceEvent.class, vetoPlace);
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, vetoBreak);
            try {
                helper.assertTrue(place(f, placer) == InteractionResult.FAIL && placements.get() == 1,
                        "Connected placement did not reach the cancelled Forge transaction");
                helper.assertTrue(f.level.getBlockState(f.pos).is(Blocks.AIR) && f.level.getBlockEntity(f.pos) == null
                        && data.save(new net.minecraft.nbt.CompoundTag()).equals(before),
                        "Cancelled connected placement left a projector or its bound reservation");
            } finally {
                MinecraftForge.EVENT_BUS.unregister(vetoPlace); MinecraftForge.EVENT_BUS.unregister(vetoBreak);
            }
        } finally {
            leave(joined);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void allowedNativeExplosionDropsTheProjectorExactlyOnce(GameTestHelper helper) {
        List<ServerPlayer> joined = new ArrayList<>();
        try (var f = new Placement(helper)) {
            ServerPlayer placer = connectedPlacer(f, joined);
            helper.assertTrue(place(f, placer).consumesAction()
                    && f.level.getBlockEntity(f.pos) instanceof ForceFieldProjectorBlockEntity placed
                    && placed.state().owner().filter(placer.getUUID()::equals).isPresent() && placed.hasReservation(),
                    "Connected placement did not bind its projector");
            var key = ((ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.pos)).reservationIdentity().key();
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var area = new AABB(f.pos).inflate(6);
            Explosion explosion = new Explosion(f.level, null, f.pos.getX() + 0.5, f.pos.getY() + 0.5, f.pos.getZ() + 0.5,
                    4.0F, false, Explosion.BlockInteraction.DESTROY);
            try {
                explosion.explode();
                helper.assertTrue(explosion.getToBlow().contains(f.pos), "The allowed explosion did not reach the projector");
                explosion.finalizeExplosion(true);
                int dropped = f.level.getEntitiesOfClass(ItemEntity.class, area,
                        item -> item.getItem().is(ModItems.FORCE_FIELD_PROJECTOR.get())).stream()
                        .mapToInt(item -> item.getItem().getCount()).sum();
                var entry = data.view().get(key);
                helper.assertTrue(f.level.getBlockState(f.pos).isAir() && f.level.getBlockEntity(f.pos) == null && dropped == 1
                        && (entry == null || entry.orphan()), "Allowed explosion dropped " + dropped
                        + " projectors or kept a live reservation");
            } finally {
                f.level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);
                data.forget(key);
            }
        } finally {
            leave(joined);
        }
        helper.succeed();
    }
    @GameTest(template = "empty", batch = "force_field", timeoutTicks = 20)
    public static void snapshotRestorationFlagDoesNotBypassCommittedOrQuarantinedProjectors(GameTestHelper helper) {
        try (var f = new Placement(helper)) {
            helper.assertTrue(f.place().consumesAction(), "Committed native fixture placement failed");
            var source = (ForceFieldProjectorBlockEntity) f.level.getBlockEntity(f.pos);
            var data = FieldCleanupSavedData.get(f.level.getServer());
            var previous = source.reservationIdentity();
            var state = source.state().bind(new UUID(410, 1));
            var bound = new FieldIdentity(state.id(), state.owner().orElseThrow(), state.generation(),
                    f.level.dimension(), f.pos, state.direction());
            helper.assertTrue(data.bindReservation(previous, bound), "Committed native fixture binding failed");
            var outer = new net.minecraft.nbt.CompoundTag();
            outer.put(ForceFieldCodecs.PROJECTOR_ROOT, ForceFieldCodecs.projector(state)); source.load(outer);
            var cleanupBefore = data.save(new net.minecraft.nbt.CompoundTag());
            Consumer<BlockEvent.BreakEvent> veto = event -> {
                if (event.getLevel() == f.level && event.getPos().equals(f.pos)) { event.setCanceled(true); }
            };
            MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, BlockEvent.BreakEvent.class, veto);
            boolean restoring = f.level.restoringBlockSnapshots;
            try {
                f.level.restoringBlockSnapshots = true;
                for (boolean quarantine : new boolean[]{false, true}) {
                    if (quarantine) {
                        var future = ForceFieldCodecs.projector(state); future.putInt("schema", 2);
                        outer.put(ForceFieldCodecs.PROJECTOR_ROOT, future); source.load(outer);
                        helper.assertTrue(source.quarantined(), "Future-root fixture did not quarantine");
                    }
                    var rawBefore = source.saveWithFullMetadata();
                    f.level.destroyBlock(f.pos, true);
                    helper.assertTrue(f.level.getBlockEntity(f.pos) == source
                            && f.level.getBlockState(f.pos).is(ModBlocks.FORCE_FIELD_PROJECTOR.get())
                            && source.saveWithFullMetadata().equals(rawBefore)
                            && data.save(new net.minecraft.nbt.CompoundTag()).equals(cleanupBefore),
                            "The snapshot-restoration flag bypassed committed/quarantined source protection");
                }
            } finally {
                f.level.restoringBlockSnapshots = restoring; MinecraftForge.EVENT_BUS.unregister(veto);
            }
        }
        helper.succeed();
    }
    private ForceFieldPlacementGameTests() { }
}
