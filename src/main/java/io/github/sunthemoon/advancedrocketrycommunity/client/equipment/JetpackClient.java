package io.github.sunthemoon.advancedrocketrycommunity.client.equipment;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitArmorItem;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.JetpackIntentPacket;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.JetpackPhysics;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.motion.MotionNetwork;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * ADR-066 section 5.2 client input. The toggle key sends TOGGLE, or CYCLE_MODE while sneaking. Holding jump in a
 * suit chestplate sends a THRUST heartbeat every three ticks (at most seven per second) and one release. The
 * server decides and pays; this class only adds the increment the server sends back.
 */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class JetpackClient {
    public static final String CATEGORY = "key.categories." + ModIdentity.MOD_ID;
    public static final KeyMapping TOGGLE = new KeyMapping("key." + ModIdentity.MOD_ID + ".toggle_jetpack",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_J, CATEGORY);
    static final int HEARTBEAT_TICKS = 3;
    private static boolean holding;
    private static int sinceHeartbeat;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) { return; }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) { holding = false; return; }
        if (TOGGLE.consumeClick()) {
            MotionNetwork.sendIntent(player.isShiftKeyDown() ? JetpackIntentPacket.cycleMode() : JetpackIntentPacket.toggle());
            while (TOGGLE.consumeClick()) { /* One intent per tick; the rest of a burst is dropped. */ }
        }
        boolean wants = minecraft.screen == null && minecraft.options.keyJump.isDown() && !player.isSpectator()
                && !player.isPassenger() && !player.isFallFlying()
                && player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof SpaceSuitArmorItem;
        if (wants) {
            if (!holding || ++sinceHeartbeat >= HEARTBEAT_TICKS) {
                MotionNetwork.sendIntent(JetpackIntentPacket.thrust(true));
                sinceHeartbeat = 0;
            }
            holding = true;
        } else if (holding) {
            MotionNetwork.sendIntent(JetpackIntentPacket.thrust(false));
            holding = false;
        }
    }

    @SubscribeEvent public static void loggingOut(ClientPlayerNetworkEvent.LoggingOut event) { holding = false; }

    /** Adds one server-paid increment with the shared caps; anything outside the bounds is ignored. */
    public static void applyImpulse(JetpackPhysics.Impulse impulse) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || !impulse.bounded()) { return; }
        var motion = player.getDeltaMovement();
        var next = JetpackPhysics.apply(new JetpackPhysics.Motion(motion.x, motion.y, motion.z), impulse);
        player.setDeltaMovement(next.x(), next.y(), next.z());
    }

    @Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class Setup {
        @SubscribeEvent public static void keys(RegisterKeyMappingsEvent event) { event.register(TOGGLE); }
        private Setup() { }
    }

    private JetpackClient() { }
}
