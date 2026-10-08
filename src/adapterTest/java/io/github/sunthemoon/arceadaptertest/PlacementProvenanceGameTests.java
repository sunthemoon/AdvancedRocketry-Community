package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerPlayerGameMode;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Actual item callback/context observations, not a physical hatch or a provenance credential. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PlacementProvenanceGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private PlacementProvenanceGameTests() { }

    @GameTest(templateNamespace = HOST, template = "empty", batch = "placement_provenance", timeoutTicks = 20)
    public static void ordinarySurvivalRecordsNativePreWriteCallerAndSource(GameTestHelper helper) {
        ordinary(helper, GameType.SURVIVAL, 1);
    }

    @GameTest(templateNamespace = HOST, template = "empty", batch = "placement_provenance", timeoutTicks = 20)
    public static void ordinaryCreativeRecordsNativePreWriteCallerAndSource(GameTestHelper helper) {
        ordinary(helper, GameType.CREATIVE, 2);
    }

    private static void ordinary(GameTestHelper helper, GameType mode, int remaining) {
        Fixture fixture = new Fixture(helper, mode, false);
        try {
            InteractionResult result = fixture.invoke();
            helper.assertTrue(result.consumesAction(), "Ordinary native placement did not complete");
            fixture.source(helper, remaining);
            fixture.owner(helper);
            helper.assertTrue(fixture.observations.size() == 3 && fixture.direct == null,
                    "Ordinary placement callback cardinality changed");
            var first = fixture.observations.get(0);
            var use = fixture.observations.get(1);
            var place = fixture.observations.get(2);
            helper.assertTrue(first.phase.equals("FIRST") && use.phase.equals("USE") && place.phase.equals("PLACE")
                    && first.context == use.context, "Ordinary dispatch did not reuse its first-use context");
            fixture.preWrite(helper, place);
            helper.assertTrue(!hasCaller(use, FixturePlacementProbeItem.class, "onItemUseFirst")
                    && hasCaller(use, ServerPlayerGameMode.class, "useItemOn"),
                    "Ordinary item entry did not retain the native game-mode caller");
            fixture.report("ordinary-" + mode.getName());
            helper.succeed();
        } catch (RuntimeException | Error failure) { fixture.close(failure); throw failure; }
    }

    @GameTest(templateNamespace = HOST, template = "empty", batch = "placement_provenance", timeoutTicks = 20)
    public static void firstUseDirectEntryReusesContextButHasDifferentOrderedCallers(GameTestHelper helper) {
        Fixture fixture = new Fixture(helper, GameType.SURVIVAL, true);
        try {
            InteractionResult result = fixture.invoke();
            fixture.source(helper, 1);
            fixture.owner(helper);
            helper.assertTrue(!result.consumesAction() && fixture.direct != null
                    && fixture.direct.result.consumesAction(), "Direct and subsequent ordinary outcomes changed");
            helper.assertTrue(fixture.observations.size() == 4, "Direct-entry callback cardinality changed");
            var first = fixture.observations.get(0);
            var direct = fixture.observations.get(1);
            var place = fixture.observations.get(2);
            var ordinary = fixture.observations.get(3);
            helper.assertTrue(first.phase.equals("FIRST") && direct.phase.equals("USE")
                    && place.phase.equals("PLACE") && ordinary.phase.equals("USE")
                    && first.context == direct.context && direct.context == ordinary.context
                    && fixture.direct.context == direct.context,
                    "First-use direct entry did not share the actual ordinary native context");
            fixture.preWrite(helper, place);
            helper.assertTrue(hasCaller(direct, ServerPlayerGameMode.class, "useItemOn")
                    && hasCaller(ordinary, ServerPlayerGameMode.class, "useItemOn")
                    && hasCaller(direct, FixturePlacementProbeItem.class, "onItemUseFirst")
                    && !hasCaller(ordinary, FixturePlacementProbeItem.class, "onItemUseFirst")
                    && nativeCallerIndex(direct) > nativeCallerIndex(ordinary),
                    "Bounded ordered callers did not distinguish the two native-context entries");
            fixture.report("direct-first-survival");
            helper.succeed();
        } catch (RuntimeException | Error failure) { fixture.close(failure); throw failure; }
    }

    private static boolean hasCaller(FixturePlacementProbeItem.Observation observation, Class<?> type, String method) {
        return observation.callers.stream().anyMatch(caller -> caller.type() == type && caller.method().equals(method));
    }

    private static int nativeCallerIndex(FixturePlacementProbeItem.Observation observation) {
        for (int i = 0; i < observation.callers.size(); i++) {
            var caller = observation.callers.get(i);
            if (caller.type() == ServerPlayerGameMode.class && caller.method().equals("useItemOn")) { return i; }
        }
        return -1;
    }

    /** Owns its listener, player/channel and two loaded cells; never retains global operation state. */
    public static final class Fixture implements GameTestListener {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private final List<FixturePlacementProbeItem.Observation> observations = new ArrayList<>();
        private final ItemStack source = new ItemStack(FixturePlacementProbeItem.ITEM.get(), 2);
        private net.minecraft.nbt.CompoundTag sourceTag;
        private ServerPlayer player;
        private EmbeddedChannel channel;
        private FixturePlacementProbeItem.DirectResult direct;
        private boolean listening;
        private boolean active;
        private boolean closed;

        private Fixture(GameTestHelper helper, GameType mode, boolean directFirst) {
            this.helper = helper; level = helper.getLevel();
            BlockPos allocation = helper.absolutePos(new BlockPos(1, 1, 1));
            target = new BlockPos(allocation.getX(), 180, allocation.getZ());
            source.getOrCreateTag().putBoolean("DirectFirst", directFirst);
            sourceTag = source.getTag().copy();
            try {
                Field field = GameTestHelper.class.getDeclaredField("testInfo");
                field.setAccessible(true);
                ((GameTestInfo) field.get(helper)).addListener(this);
                for (BlockPos cell : new BlockPos[] { target, target.below() }) {
                    helper.assertTrue(!level.isOutsideBuildHeight(cell) && level.hasChunkAt(cell)
                            && level.getBlockEntity(cell) == null, "Fixture needs loaded BE-free cells");
                    before.put(cell, level.getBlockState(cell));
                }
                level.setBlock(target, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                level.setBlock(target.below(), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                helper.assertTrue(level.getBlockState(target).isAir()
                        && level.getBlockState(target.below()).is(Blocks.STONE), "Fixture cells were not installed");
                player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "entryProbe"));
                Connection connection = new Connection(PacketFlow.SERVERBOUND);
                channel = new EmbeddedChannel(connection);
                level.getServer().getPlayerList().placeNewPlayer(connection, player);
                player.teleportTo(level, target.getX() + 0.5D, target.getY(), target.getZ() + 2.5D, 0, 0);
                player.gameMode.changeGameModeForPlayer(mode);
                helper.assertTrue(player.isCreative() == (mode == GameType.CREATIVE)
                        && player.getAbilities().instabuild == (mode == GameType.CREATIVE), "Native mode is inconsistent");
                player.setItemInHand(InteractionHand.MAIN_HAND, source);
                MinecraftForge.EVENT_BUS.register(this); listening = true;
            } catch (ReflectiveOperationException failure) {
                close(failure); throw new IllegalStateException("Fixture terminal listener unavailable", failure);
            } catch (RuntimeException | Error failure) { close(failure); throw failure; }
        }

        private InteractionResult invoke() {
            active = true;
            try {
                BlockPos support = target.below();
                return player.gameMode.useItemOn(player, level, source, InteractionHand.MAIN_HAND,
                        new BlockHitResult(Vec3.atCenterOf(support).add(0, 0.5D, 0), Direction.UP, support, false));
            } finally { active = false; }
        }

        @SubscribeEvent public void observed(FixturePlacementProbeItem.Observation event) {
            if (event.context.getPlayer() != player) { return; }
            helper.assertTrue(active && !closed && event.context.getLevel() == level
                    && event.context.getHand() == InteractionHand.MAIN_HAND && event.context.getItemInHand() == source
                    && observations.size() < 4 && !event.callers.isEmpty() && event.callers.size() <= 16,
                    "Observation identity, phase or size changed");
            if (event.phase.equals("PLACE")) {
                helper.assertTrue(event.context.getClickedPos().equals(target)
                        && level.getBlockState(target).isAir() && level.getBlockEntity(target) == null,
                        "Selected pre-write cell was not actually empty");
            }
            observations.add(event);
        }

        @SubscribeEvent public void directResult(FixturePlacementProbeItem.DirectResult event) {
            if (event.context.getPlayer() != player) { return; }
            helper.assertTrue(active && direct == null, "Duplicate or unrelated direct result");
            direct = event;
        }

        private void preWrite(GameTestHelper helper, FixturePlacementProbeItem.Observation place) {
            helper.assertTrue(place.context instanceof net.minecraft.world.item.context.BlockPlaceContext
                    && place.context.getClickedPos().equals(target)
                    && hasCaller(place, net.minecraft.world.item.BlockItem.class, "place")
                    && hasCaller(place, net.minecraft.world.item.BlockItem.class, "useOn"),
                    "Actual pre-write target or native BlockItem callers changed");
        }

        private void source(GameTestHelper helper, int remaining) {
            helper.assertTrue(player.getItemInHand(InteractionHand.MAIN_HAND) == source
                    && source.is(FixturePlacementProbeItem.ITEM.get()) && source.getCount() == remaining
                    && sourceTag.equals(source.getTag()), "Native source identity/count/tag changed");
        }

        private void owner(GameTestHelper helper) {
            helper.assertTrue(level.getBlockEntity(target) instanceof ChestBlockEntity owner
                    && owner.getLevel() == level && !owner.isRemoved(), "No registered native chest owner");
        }

        private void report(String subject) {
            for (var observation : observations) {
                System.out.println("ARCE_PLACEMENT_ENTRY subject=" + subject + " phase=" + observation.phase
                        + " frames=" + observation.callers);
            }
        }

        private void close(Throwable primary) {
            if (closed) { return; }
            closed = true; active = false;
            Throwable first = primary;
            if (listening) {
                try { MinecraftForge.EVENT_BUS.unregister(this); }
                catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (player != null && level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                try { level.getServer().getPlayerList().remove(player); }
                catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (channel != null) {
                try { channel.finishAndReleaseAll(); }
                catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            for (var entry : before.entrySet()) {
                try {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
                    helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue()
                            && level.getBlockEntity(entry.getKey()) == null, "Owned cell was not restored");
                } catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (primary == null) {
                if (first instanceof RuntimeException failure) { throw failure; }
                if (first instanceof Error failure) { throw failure; }
            }
        }

        private static Throwable retain(Throwable first, Throwable failure) {
            if (first == null) { return failure; }
            if (first != failure) { first.addSuppressed(failure); }
            return first;
        }
        @Override public void testStructureLoaded(GameTestInfo info) { }
        @Override public void testPassed(GameTestInfo info) { close(null); }
        @Override public void testFailed(GameTestInfo info) { close(info.getError()); }
    }
}
