package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Black-box installed-catalog checks: platform and existing fixture, no host implementation imports. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SealDetectorRuntimeGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private static final String PREFIX = "message." + HOST + ".seal_detector.";

    private SealDetectorRuntimeGameTests() { }

    @GameTest(templateNamespace = HOST, template = "empty", batch = "seal_detector_runtime", timeoutTicks = 20)
    public static void registeredMainHandReadsTheInstalledExternalStateRule(GameTestHelper helper) {
        measure(helper, InteractionHand.MAIN_HAND);
    }

    @GameTest(templateNamespace = HOST, template = "empty", batch = "seal_detector_runtime", timeoutTicks = 20)
    public static void registeredOffHandReadsTheInstalledExternalStateRule(GameTestHelper helper) {
        measure(helper, InteractionHand.OFF_HAND);
    }

    private static void measure(GameTestHelper helper, InteractionHand hand) {
        try (Fixture fixture = new Fixture(helper)) {
            fixture.state(false);
            fixture.read(hand, "sealed");
            fixture.state(true);
            fixture.cooldown(hand);
            fixture.read(hand, "open");
            fixture.state(false);
            fixture.cooldown(hand);
            fixture.read(hand, "sealed");
            fixture.unchanged();
            System.out.println("ARCE_SEAL_INSTALLED_RULE hand=" + hand
                    + " states=closed,open,closed replies=" + fixture.actor.replies.size()
                    + " peerReplies=" + fixture.peer.replies.size() + " payloads=" + fixture.actor.replies);
        }
        helper.succeed();
    }

    /** Owns two loaded BE-free cells, two players and their channels; setup is not measurement work. */
    private static final class Fixture implements AutoCloseable {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final BlockPos target;
        private final Map<BlockPos, BlockState> before = new LinkedHashMap<>();
        private final Item item;
        private final ItemStack main;
        private final ItemStack off;
        private final CompoundTag mainData;
        private final CompoundTag offData;
        private JoinedPlayer actor;
        private JoinedPlayer peer;
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            level = helper.getLevel();
            BlockPos allocation = helper.absolutePos(new BlockPos(2, 2, 2));
            target = new BlockPos(allocation.getX(), 180, allocation.getZ());
            item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(HOST + ":seal_detector"));
            helper.assertTrue(item != null && item != net.minecraft.world.item.Items.AIR,
                    "Registered detector is missing");
            main = new ItemStack(item);
            off = new ItemStack(item);
            main.getOrCreateTag().putString("fixture_note", "native main hand");
            off.getOrCreateTag().putLong("fixture_note", Long.MIN_VALUE);
            mainData = main.save(new CompoundTag());
            offData = off.save(new CompoundTag());
            try {
                helper.assertTrue(AdapterTestMod.boundaryEvents == 1 && AdapterTestMod.boundaryEvent != null,
                        "Fixture did not receive its startup boundary registration");
                for (BlockPos cell : new BlockPos[] { target, target.west() }) {
                    helper.assertTrue(!level.isOutsideBuildHeight(cell) && level.hasChunkAt(cell)
                            && level.getBlockEntity(cell) == null, "Fixture needs loaded BE-free cells");
                    before.put(cell, level.getBlockState(cell));
                }
                level.setBlock(target.west(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                actor = new JoinedPlayer(level, target, "sealRuntime");
                peer = new JoinedPlayer(level, target, "sealObserver");
                actor.player.setItemInHand(InteractionHand.MAIN_HAND, main);
                actor.player.setItemInHand(InteractionHand.OFF_HAND, off);
                actor.replies.clear();
                peer.replies.clear();
            } catch (RuntimeException | Error failure) {
                closeAfter(failure);
                throw failure;
            }
        }

        private void state(boolean open) {
            BlockState state = AdapterTestMod.BOUNDARY.get().defaultBlockState()
                    .setValue(FixtureBoundaryBlock.OPEN, open);
            level.setBlock(target, state, Block.UPDATE_ALL);
            helper.assertTrue(level.getBlockState(target) == state
                    && Block.isShapeFullBlock(state.getCollisionShape(level, target)),
                    "External state boundary must retain full collision in both states");
        }

        private InteractionResult invoke(InteractionHand hand) {
            return actor.player.gameMode.useItemOn(actor.player, level, actor.player.getItemInHand(hand), hand,
                    new BlockHitResult(Vec3.atCenterOf(target).add(-0.5D, 0, 0), Direction.WEST, target, false));
        }

        private void read(InteractionHand hand, String boundary) {
            int count = actor.replies.size();
            BlockState state = level.getBlockState(target);
            helper.assertTrue(invoke(hand).consumesAction(), "Native detector use did not respond");
            Component expected = Component.translatable(PREFIX + "reading",
                    Component.translatable(PREFIX + "boundary." + boundary),
                    Component.translatable(PREFIX + "supply.not_known_supplied"));
            helper.assertTrue(actor.replies.size() == count + 1
                    && actor.replies.get(count).equals(new Reply(Component.Serializer.toJson(expected), false))
                    && peer.replies.isEmpty(),
                    "Installed rule, ambient supply or actor-only system-chat response differs");
            helper.assertTrue(level.getBlockState(target) == state && level.getBlockState(target.west()).isAir(),
                    "Measurement mutated the selected cells");
            unchanged();
        }

        private void cooldown(InteractionHand hand) {
            InteractionHand other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            int count = actor.replies.size();
            helper.assertTrue(actor.player.getCooldowns().isOnCooldown(item), "Successful measurement lacked cooldown");
            invoke(other);
            helper.assertTrue(actor.replies.size() == count, "Opposite hand bypassed the shared cooldown");
            actor.player.getCooldowns().tick();
            invoke(hand);
            helper.assertTrue(actor.replies.size() == count && actor.player.getCooldowns().isOnCooldown(item),
                    "One tick bypassed the cooldown");
            actor.player.getCooldowns().tick();
            helper.assertTrue(!actor.player.getCooldowns().isOnCooldown(item), "Two ticks did not release the cooldown");
        }

        private void unchanged() {
            helper.assertTrue(actor.player.getItemInHand(InteractionHand.MAIN_HAND) == main
                    && actor.player.getItemInHand(InteractionHand.OFF_HAND) == off
                    && mainData.equals(main.save(new CompoundTag())) && offData.equals(off.save(new CompoundTag())),
                    "Measurement changed actual held identities, counts or custom NBT");
            helper.assertTrue(peer.replies.isEmpty(), "A bystander received detector feedback");
        }

        private void closeAfter(Throwable primary) {
            try { close(); } catch (RuntimeException | Error failure) { primary.addSuppressed(failure); }
        }

        @Override public void close() {
            if (closed) { return; }
            closed = true;
            Throwable first = null;
            for (JoinedPlayer owned : new JoinedPlayer[] { actor, peer }) {
                if (owned == null) { continue; }
                try { owned.close(); } catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            for (var entry : before.entrySet()) {
                try {
                    level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
                    helper.assertTrue(level.getBlockState(entry.getKey()) == entry.getValue()
                            && level.getBlockEntity(entry.getKey()) == null, "Owned fixture cell was not restored");
                } catch (RuntimeException | Error failure) { first = retain(first, failure); }
            }
            if (first instanceof RuntimeException failure) { throw failure; }
            if (first instanceof Error failure) { throw failure; }
        }
    }

    private record Reply(String json, boolean overlay) { }

    private static final class JoinedPlayer implements AutoCloseable {
        private final ServerLevel level;
        private final List<Reply> replies = new ArrayList<>();
        private final ServerPlayer player;
        private final EmbeddedChannel channel;

        private JoinedPlayer(ServerLevel level, BlockPos target, String name) {
            this.level = level;
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name)) {
                @Override public void sendSystemMessage(Component message) {
                    replies.add(new Reply(Component.Serializer.toJson(message), false));
                }
                @Override public void sendSystemMessage(Component message, boolean overlay) {
                    replies.add(new Reply(Component.Serializer.toJson(message), overlay));
                }
            };
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            try {
                level.getServer().getPlayerList().placeNewPlayer(connection, player);
                player.teleportTo(level, target.getX() - 1.5D, target.getY(), target.getZ() + 0.5D, 0, 0);
                player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);
            } catch (RuntimeException | Error failure) {
                try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
                throw failure;
            }
        }

        @Override public void close() {
            try {
                if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                    level.getServer().getPlayerList().remove(player);
                }
            } finally { channel.finishAndReleaseAll(); }
        }
    }

    private static Throwable retain(Throwable first, Throwable failure) {
        if (first == null) { return failure; }
        if (first != failure) { first.addSuppressed(failure); }
        return first;
    }
}
