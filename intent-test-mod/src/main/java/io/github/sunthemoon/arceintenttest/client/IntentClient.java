package io.github.sunthemoon.arceintenttest.client;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import io.github.sunthemoon.arceintenttest.CaseBuilder;
import io.github.sunthemoon.arceintenttest.CaseGuard;
import io.github.sunthemoon.arceintenttest.IntentCase;
import io.github.sunthemoon.arceintenttest.IntentTestMod;
import io.github.sunthemoon.arceintenttest.ProbeSession;
import io.netty.buffer.Unpooled;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;

/** Physical-client only. Connection-scoped capture and budget are discarded at logout. */
@Mod.EventBusSubscriber(modid = IntentTestMod.ID, value = Dist.CLIENT)
public final class IntentClient {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String PREFIX = "advancedrocketrycommunity:";
    private static final List<String> KINDS = List.of("warp_controller", "station_gravity_controller",
            "station_altitude_controller", "station_orientation_controller", "rocket_monitoring_station");
    private static final KeyMapping CAPTURE = new KeyMapping("ARCE intent: capture", GLFW.GLFW_KEY_F8, "ARCE campaign fixture");
    private static final KeyMapping SEND = new KeyMapping("ARCE intent: send once", GLFW.GLFW_KEY_F9, "ARCE campaign fixture");
    private static ProbeSession session = new ProbeSession();
    private static IntentCase selected;
    private static int entityId = -1;
    private static String body;
    private static Capture capture;

    private record Capture(IntentCase which, int container, int kind, String menu, int entity, String body,
                           String dimension, Vec3 position, long time) { }

    @Mod.EventBusSubscriber(modid = IntentTestMod.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Keys {
        @SubscribeEvent public static void register(RegisterKeyMappingsEvent event) {
            event.register(CAPTURE);
            event.register(SEND);
        }
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("arce_intent")
                .then(Commands.literal("enable").then(Commands.literal("test-world").executes(context -> {
                    session.enable();
                    say("Enabled for this disposable-world connection; 32 sends maximum. Select a case, F8 capture, F9 send.");
                    return 1;
                })))
                .then(Commands.literal("disable").executes(context -> {
                    session.disable(); capture = null; say("Disabled; connection budget retained."); return 1;
                }))
                .then(Commands.literal("select").then(Commands.argument("case", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(IntentCase.values()).map(IntentCase::id), builder))
                        .executes(context -> attempt(() -> {
                            selected = IntentCase.parse(StringArgumentType.getString(context, "case"));
                            capture = null;
                            say("Selected " + selected.id() + ". Flight cases also need /arce_intent rocket <id> <body>.");
                        }))))
                .then(Commands.literal("rocket").then(Commands.argument("id", IntegerArgumentType.integer(0))
                        .then(Commands.argument("body", StringArgumentType.word()).executes(context -> attempt(() -> {
                            entityId = IntegerArgumentType.getInteger(context, "id");
                            body = StringArgumentType.getString(context, "body");
                            capture = null;
                            // Validate before a menu is opened, without accessing the host implementation.
                            CaseBuilder.build(selected != null && selected.flight() ? selected : IntentCase.FLIGHT_LATE,
                                    new CaseBuilder.Input(0, 0, entityId, body, UUID.randomUUID()));
                            say("Rocket " + entityId + ", body " + body + ". Open that rocket's menu, then F8.");
                        }))))));
    }

    @SubscribeEvent public static void key(InputEvent.Key event) {
        // InputEvent also fires while a screen is open; use press, not repeat, for a finite trigger.
        if (event.getAction() != GLFW.GLFW_PRESS) { return; }
        if (CAPTURE.matches(event.getKey(), event.getScanCode())) { attempt(IntentClient::capture); }
        else if (SEND.matches(event.getKey(), event.getScanCode())) { attempt(IntentClient::send); }
    }

    private static void capture() {
        Minecraft mc = connected();
        if (selected == null) { throw new IllegalStateException("Select a case first"); }
        AbstractContainerMenu menu = mc.player.containerMenu;
        String menuId = menuId(mc);
        int kind = -1;
        Vec3 position = mc.player.position();
        if (selected.classic()) {
            kind = KINDS.indexOf(menuId.replace(PREFIX, ""));
            if (kind < 0 || !(mc.hitResult instanceof BlockHitResult hit)) {
                throw new IllegalStateException("Open one of the five classic control menus while looking at its block");
            }
            String block = BuiltInRegistries.BLOCK.getKey(mc.level.getBlockState(hit.getBlockPos()).getBlock()).toString();
            if (!block.equals(menuId)) { throw new IllegalStateException("Looked-at block must match the open menu"); }
            position = Vec3.atCenterOf(hit.getBlockPos());
        } else if (selected.flight()) {
            if (!menuId.equals(PREFIX + "rocket_flight") || !(mc.hitResult instanceof EntityHitResult hit)
                    || hit.getEntity().getId() != entityId || body == null) {
                throw new IllegalStateException("Configure the rocket ID/body, look at that rocket and open its flight menu");
            }
            position = hit.getEntity().position();
        }
        if ((selected.flight() || selected.classic()) && mc.player.position().distanceToSqr(position) > 64) {
            throw new IllegalStateException("Capture must start within 8 blocks of the target");
        }
        capture = new Capture(selected, menu.containerId, kind, menuId, entityId, body,
                mc.level.dimension().location().toString(), position, System.nanoTime());
        LOG.info("ARCE_INTENT_FIXTURE stage=captured case={} utc={} player={} dimension={} container={} menu={} kind={} entity={} target={} origin={}",
                selected.id(), Instant.now(), mc.player.getUUID(), capture.dimension(), capture.container(), menuId,
                kind, entityId, body, position);
        say("Captured " + selected.id() + "; perform its negative precondition, then F9 within 120 seconds.");
    }

    private static void send() {
        Minecraft mc = connected();
        Capture old = capture;
        if (old == null) { throw new IllegalStateException("F8 capture required; every capture is single-use"); }
        if (!old.dimension().equals(mc.level.dimension().location().toString())) {
            capture = null;
            throw new IllegalStateException("Dimension changed: recapture; cross-world replays are NOT_COVERED");
        }
        Entity entity = old.which().flight() ? mc.level.getEntity(old.entity()) : null;
        Vec3 position = entity != null ? entity.position() : old.position();
        boolean sameMenu = old.container() == mc.player.containerMenu.containerId && old.menu().equals(menuId(mc));
        CaseGuard.check(old.which(), old.kind(), new CaseGuard.Observation(sameMenu,
                mc.player.containerMenu == mc.player.inventoryMenu, entity != null,
                mc.player.position().distanceToSqr(position), mc.player.isSpectator(), mc.player.isCreative()));
        CaseBuilder.Packet packet = CaseBuilder.build(old.which(), new CaseBuilder.Input(
                old.container(), old.kind(), old.entity(), old.body(), UUID.randomUUID()));
        session.claim(System.nanoTime(), old.time());
        capture = null; // Even a transport exception must not retry or restore the send budget.
        FriendlyByteBuf payload = new FriendlyByteBuf(Unpooled.wrappedBuffer(packet.payload()));
        mc.getConnection().send(new ServerboundCustomPayloadPacket(ResourceLocation.tryParse(old.which().channel()), payload));
        LOG.info("ARCE_INTENT_FIXTURE stage=queued case={} channel={} protocol={} index=0 utc={} monotonic={} ordinal={} player={} dimension={} captured_container={} current_container={} current_menu={} target_position={} player_position={} fields=[{}] payload_hex={}",
                old.which().id(), old.which().channel(), old.which().protocol(), Instant.now(), System.nanoTime(), session.sent(),
                mc.player.getUUID(), old.dimension(), old.container(), mc.player.containerMenu.containerId, menuId(mc),
                position, mc.player.position(), packet.values(), HexFormat.of().formatHex(packet.payload()));
        say("Queued one " + old.which().id() + " intent. This is NOT a server acknowledgement; read server state back.");
    }

    private static Minecraft connected() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || !mc.player.isAlive()) {
            throw new IllegalStateException("A connected, living test-world player is required");
        }
        return mc;
    }

    private static String menuId(Minecraft mc) {
        if (mc.player.containerMenu == mc.player.inventoryMenu) { return "inventory"; }
        return BuiltInRegistries.MENU.getKey(mc.player.containerMenu.getType()).toString();
    }

    private static int attempt(Runnable action) {
        try { action.run(); return 1; }
        catch (IllegalArgumentException | IllegalStateException refusal) {
            LOG.info("ARCE_INTENT_FIXTURE stage=local_refusal utc={} reason={}", Instant.now(), refusal.getMessage());
            say(refusal.getMessage()); return 0;
        }
    }

    private static void say(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) { mc.player.displayClientMessage(Component.literal("[ARCE intent fixture] " + text), false); }
    }

    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        capture = null; selected = null; entityId = -1; body = null; session = new ProbeSession();
    }

    private IntentClient() { }
}
