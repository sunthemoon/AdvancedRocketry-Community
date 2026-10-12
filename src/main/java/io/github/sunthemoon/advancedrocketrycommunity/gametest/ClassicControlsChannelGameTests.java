package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlKind;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.ClassicControlViewPacket;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerKind;
import io.github.sunthemoon.advancedrocketrycommunity.station.controller.StationControllerMenu;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationManagementCode;
import io.netty.buffer.Unpooled;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.network.NetworkRegistry;

/**
 * ADR-065 section 11.1 on the real server, through the installed {@code classic_controls} channel: a peer with any
 * other protocol is rejected; a frame decoded by the registered codec reaches only the sender's own open menu of
 * the same container and kind; a frame on an unknown index is dropped; a server-bound view closes the sender's
 * connection; and an intent from a member, an unrelated player, another container or a distant holder changes
 * nothing.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicControlsChannelGameTests {
    private static final ResourceLocation CHANNEL = ModIdentity.id("classic_controls");

    private ClassicControlsChannelGameTests() {
    }

    @GameTest(template = "empty", batch = "classic_controls_version", timeoutTicks = 20)
    public static void theInstalledChannelRejectsEveryOtherProtocol(GameTestHelper helper) {
        try {
            Map<ResourceLocation, String> advertised = advertised();
            helper.assertTrue("1".equals(advertised.get(CHANNEL)), "The classic_controls channel is not installed at 1");
            for (String direction : new String[] {"validateClientChannels", "validateServerChannels"}) {
                helper.assertTrue(rejected(direction, advertised).isEmpty(),
                        "The installed versions must be accepted by " + direction);
                Map<ResourceLocation, String> missing = new HashMap<>(advertised);
                missing.remove(CHANNEL);
                Map<ResourceLocation, String> refusedMissing = rejected(direction, missing);
                helper.assertTrue(refusedMissing.size() == 1 && refusedMissing.containsKey(CHANNEL),
                        "A peer without classic_controls must be rejected by " + direction);
                for (String version : new String[] {"", "0", "2", "01", "1 ",
                        NetworkRegistry.ABSENT.version(), NetworkRegistry.ACCEPTVANILLA}) {
                    Map<ResourceLocation, String> other = new HashMap<>(advertised);
                    other.put(CHANNEL, version);
                    Map<ResourceLocation, String> refused = rejected(direction, other);
                    helper.assertTrue(refused.size() == 1 && version.equals(refused.get(CHANNEL)),
                            "Version '" + version + "' must be rejected by " + direction);
                }
            }
            helper.assertTrue(advertised.equals(advertised()), "Validation changed the advertised channels");
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Pinned Forge channel observation is unavailable", exception);
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "classic_controls_channel", timeoutTicks = 100)
    public static void onlyTheValidatedHoldersIntentReachesItsMenu(GameTestHelper helper) {
        StationWarpGameTests.Fixture fixture = new StationWarpGameTests.Fixture(helper, "Control channel",
                CelestialIds.EARTH_ID, 0);
        List<BlockPos> placed = new ArrayList<>();
        UUID memberId = UUID.randomUUID();
        UUID outsiderId = UUID.randomUUID();
        UUID wrongWayId = UUID.randomUUID();
        Runnable cleanup = () -> {
            StationControlsGameTests.clear(fixture, placed);
            fixture.close();
        };
        boolean scheduled = false;
        try {
            ServerPlayer owner = StationControlsGameTests.owner(fixture, "channelOwner");
            fixture.data.invite(fixture.id(), memberId);
            fixture.data.acceptInvitation(fixture.id(), memberId);
            fixture.join(memberId, "channelMember");
            fixture.join(outsiderId, "channelOutsider");
            fixture.join(wrongWayId, "channelWrongWay");
            ServerPlayer member = fixture.server.getPlayerList().getPlayer(memberId);
            ServerPlayer outsider = fixture.server.getPlayerList().getPlayer(outsiderId);
            ServerPlayer wrongWay = fixture.server.getPlayerList().getPlayer(wrongWayId);
            BlockPos at = StationControlsGameTests.place(fixture, placed, StationControllerKind.GRAVITY,
                    fixture.pad.offset(2, 0, 2));

            StationControllerMenu ownerMenu = open(helper, owner, at);
            StationControllerMenu memberMenu = open(helper, member, at);
            helper.assertTrue(ownerMenu.lastView().filter(view -> view.value(0) == 0).isPresent()
                    && memberMenu.lastView().isPresent(), "Opening did not send each holder its view");
            use(outsider, at);
            helper.assertTrue(outsider.containerMenu == outsider.inventoryMenu, "A stranger opened the controller");

            // A member's intent is decoded and reaches the member's menu, where the station service refuses it.
            deliver(member, 0, intent(memberMenu, 3));
            gravity(helper, fixture, 0, "after the member's intent");
            helper.assertTrue(memberMenu.status().equals(Optional.of(StationManagementCode.UNAUTHORIZED)),
                    "The member's intent was not refused by the service: " + memberMenu.status());
            // A stranger's frame naming the owner's container has no menu to reach.
            deliver(outsider, 0, intent(ownerMenu, 3));
            // An unknown index is dropped before decoding.
            deliver(owner, 3, intent(ownerMenu, 3));
            // Another container, or another kind for this container, is not this menu's intent.
            deliver(owner, 0, buffer -> ClassicControlIntentPacket.encode(new ClassicControlIntentPacket(
                    ownerMenu.containerId % 255 + 1, ClassicControlKind.STATION_GRAVITY_CONTROLLER, 3, 0, 0L), buffer));
            deliver(owner, 0, buffer -> ClassicControlIntentPacket.encode(new ClassicControlIntentPacket(
                    ownerMenu.containerId, ClassicControlKind.STATION_ALTITUDE_CONTROLLER, 3, 0, 0L), buffer));
            gravity(helper, fixture, 0, "after the foreign frames");
            helper.assertTrue(ownerMenu.status().isEmpty(), "A foreign frame reached the owner's menu: "
                    + ownerMenu.status());
            // The holder's own frame through the registered codec and handler.
            deliver(owner, 0, intent(ownerMenu, 3));
            gravity(helper, fixture, 100, "after the owner's +10");
            helper.assertTrue(ownerMenu.lastView().flatMap(ClassicControlViewPacket::status)
                    .equals(Optional.of(StationManagementCode.GRAVITY_SET)), "The owner's view differs: "
                    + ownerMenu.lastView());

            // A view sent towards the server is the wrong direction: Forge closes that sender's connection.
            boolean refused = false;
            try {
                deliver(wrongWay, 1, buffer -> ClassicControlViewPacket.encode(new ClassicControlViewPacket(1,
                        ClassicControlKind.STATION_GRAVITY_CONTROLLER, Optional.empty(), 0L, List.of(50)), buffer));
            } catch (IllegalStateException expected) {
                refused = true;
            }
            helper.assertTrue(refused && !wrongWay.connection.connection.isConnected(),
                    "A server-bound view was not refused with a disconnect");

            // A holder 13 blocks away: the intent is ignored, and the next player tick closes the menu.
            owner.teleportTo(fixture.space, fixture.pad.getX() + 15.5D, fixture.pad.getY(), fixture.pad.getZ() + 0.5D,
                    0.0F, 0.0F);
            Optional<ClassicControlViewPacket> lastView = ownerMenu.lastView();
            deliver(owner, 0, intent(ownerMenu, 0));
            helper.assertTrue(ownerMenu.status().equals(Optional.of(StationManagementCode.GRAVITY_SET)),
                    "A distant holder's intent was handled: " + ownerMenu.status());
            scheduled = true;
            helper.runAfterDelay(2, () -> {
                try {
                    helper.assertTrue(owner.containerMenu == owner.inventoryMenu, "The distant holder's menu stayed open");
                    helper.assertTrue(ownerMenu.lastView().equals(lastView), "A distant holder was sent a view");
                    gravity(helper, fixture, 100, "after the distant intent");
                } finally {
                    cleanup.run();
                }
                helper.succeed();
            });
        } finally {
            if (!scheduled) {
                cleanup.run();
            }
        }
    }

    /** Opens the controller as its block does for {@code player}, and returns the server menu. */
    static StationControllerMenu open(GameTestHelper helper, ServerPlayer player, BlockPos at) {
        use(player, at);
        helper.assertTrue(player.containerMenu instanceof StationControllerMenu,
                player.getGameProfile().getName() + " could not open the controller");
        return (StationControllerMenu) player.containerMenu;
    }

    private static void use(ServerPlayer player, BlockPos at) {
        player.serverLevel().getBlockState(at).use(player.serverLevel(), player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(at), Direction.UP, at, false));
    }

    /** One {@code classic_controls} frame from {@code sender}, as its connection hands it to the server thread. */
    static void deliver(ServerPlayer sender, int index, Consumer<FriendlyByteBuf> body) {
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.buffer());
        payload.writeByte(index);
        body.accept(payload);
        sender.connection.handleCustomPayload(new ServerboundCustomPayloadPacket(CHANNEL, payload));
    }

    static Consumer<FriendlyByteBuf> intent(StationControllerMenu menu, int action) {
        return buffer -> ClassicControlIntentPacket.encode(new ClassicControlIntentPacket(menu.containerId,
                menu.classicControlKind(), action, 0, 0L), buffer);
    }

    private static void gravity(GameTestHelper helper, StationWarpGameTests.Fixture fixture, int milli, String label) {
        int actual = fixture.station().environment().gravityMilli();
        helper.assertTrue(actual == milli, label + ": gravity " + actual + " milli, expected " + milli);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, String> advertised() throws ReflectiveOperationException {
        Method method = NetworkRegistry.class.getDeclaredMethod("buildChannelVersions");
        method.setAccessible(true);
        return (Map<ResourceLocation, String>) method.invoke(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<ResourceLocation, String> rejected(String direction, Map<ResourceLocation, String> incoming)
            throws ReflectiveOperationException {
        Method method = NetworkRegistry.class.getDeclaredMethod(direction, Map.class);
        method.setAccessible(true);
        return (Map<ResourceLocation, String>) method.invoke(null, incoming);
    }
}
