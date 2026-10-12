package io.github.sunthemoon.advancedrocketrycommunity.equipment.motion;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.ArmorEquipment;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.SuitEquipmentContent;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.suit.TankData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;

/** A connected survival player with its own motion service and impulse recorder, removed again at close. */
final class MotionFixture implements AutoCloseable {
    final GameTestHelper helper;
    final ServerLevel level;
    final List<JetpackPhysics.Impulse> sent = new ArrayList<>();
    final SuitMotionService service;
    ServerPlayer player;
    private EmbeddedChannel channel;

    MotionFixture(GameTestHelper helper) {
        this.helper = helper;
        level = helper.getLevel();
        service = new SuitMotionService((target, impulse) -> { if (target == player) { sent.add(impulse); } });
        try {
            BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
            player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "motionTest"));
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            // The GameTest world defaults to creative, which would make the player immune to falls.
            player.setGameMode(GameType.SURVIVAL);
            player.teleportTo(level, at.getX() + .5, at.getY(), at.getZ() + .5, 0, 0);
            rest();
            helper.assertTrue(SuitMotionService.live(player), "Fixture must be a live connected player");
        } catch (RuntimeException | Error failure) {
            try { close(); } catch (RuntimeException | Error cleanup) { failure.addSuppressed(cleanup); }
            throw failure;
        }
    }

    static ItemStack module(String id) { return SuitEquipmentContent.modules().get(id).get().getDefaultInstance(); }

    /** A built-in chestplate with an optional jetpack and an optional HYDROGEN tank holding {@code hydrogen}. */
    static ItemStack chest(boolean jetpack, int hydrogen) {
        ItemStack chest = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
        if (jetpack) { chest = ArmorEquipment.install(chest, module("jetpack")).armor(); }
        if (hydrogen >= 0) {
            ItemStack tank = module("pressure_tank");
            tank.getOrCreateTag().put(TankData.KEY, new TankData(TankData.Gas.HYDROGEN, hydrogen, 2_000).encode());
            chest = ArmorEquipment.install(chest, tank).armor();
        }
        return chest;
    }

    static ItemStack piece(EquipmentSlot slot, String... modules) {
        ItemStack piece = new ItemStack(switch (slot) {
            case HEAD -> ModItems.SPACE_SUIT_HELMET.get(); case CHEST -> ModItems.SPACE_SUIT_CHESTPLATE.get();
            case LEGS -> ModItems.SPACE_SUIT_LEGGINGS.get(); default -> ModItems.SPACE_SUIT_BOOTS.get();
        });
        for (String id : modules) { piece = ArmorEquipment.install(piece, module(id)).armor(); }
        return piece;
    }

    void wear(EquipmentSlot slot, ItemStack stack) { player.setItemSlot(slot, stack); }

    int hydrogen() { return ArmorEquipment.hydrogen(player.getItemBySlot(EquipmentSlot.CHEST)).stored(); }

    /** Zero server motion and zero accepted displacement, so each plan starts from rest. */
    void rest() {
        player.setDeltaMovement(Vec3.ZERO);
        player.setOldPosAndRot();
    }

    SuitMotionService.Reply send(JetpackIntentPacket intent, long now) { return service.receive(player, intent, now); }

    /** Acknowledge native teleport packets before exercising vanilla movement admission. */
    void acknowledgeTeleport() {
        Object packet;
        while ((packet = channel.readOutbound()) != null) {
            try {
                if (packet instanceof ClientboundPlayerPositionPacket position) {
                    player.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                }
            } finally { io.netty.util.ReferenceCountUtil.release(packet); }
        }
    }

    /** A stationary airborne client packet, followed by an actual native floating-check tick. */
    void floatTick() {
        player.connection.handleMovePlayer(new ServerboundMovePlayerPacket.Pos(player.getX(), player.getY(), player.getZ(), false));
        player.connection.tick();
        rest();
    }

    /** Read the actual registered S2C authorization packets, including those sent by the login event. */
    List<Boolean> equipmentFlags() {
        channel.runPendingTasks();
        List<Boolean> flags = new ArrayList<>();
        for (int count = 0; count < 512; count++) {
            Object packet = channel.readOutbound();
            if (packet == null) { return flags; }
            try {
                if (packet instanceof ClientboundCustomPayloadPacket payload
                        && payload.getIdentifier().equals(ModIdentity.id("suit_motion"))) {
                    var data = payload.getData();
                    try {
                        if (data.readUnsignedByte() == 2) { flags.add(EquipmentEnabledPacket.decode(data).enabled()); }
                    } finally { data.release(); }
                }
            } finally { io.netty.util.ReferenceCountUtil.release(packet); }
        }
        throw new IllegalStateException("Owned motion packet drain reached its bound");
    }

    @Override public void close() {
        try {
            service.clear();
            if (player != null) {
                player.stopRiding();
                if (level.getServer().getPlayerList().getPlayer(player.getUUID()) == player) {
                    level.getServer().getPlayerList().remove(player);
                }
            }
        } finally {
            if (channel != null) { channel.finishAndReleaseAll(); }
        }
    }
}
