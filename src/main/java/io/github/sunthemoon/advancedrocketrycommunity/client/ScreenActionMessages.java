package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.client.station.StationControllerScreen;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Mirrors the unchanged server action-bar message above an open mod screen, not behind its dimmed background. */
@Mod.EventBusSubscriber(modid = ModIdentity.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ScreenActionMessages {
    private static final ScreenMessageState<Component> NOTICE = new ScreenMessageState<>();
    private static final ScreenText TEXT = new ScreenText();

    private ScreenActionMessages() { }

    /** W311: a screen that keeps its refusals until its next action or plan change, instead of the timed lifetime. */
    public interface RefusalOwner {
        boolean refusal(Component message);
    }

    /** W311: called by a {@link RefusalOwner} on its next action or plan change. */
    public static void release(Screen screen) { NOTICE.release(screen); }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void received(ClientChatReceivedEvent.System event) {
        Screen screen = Minecraft.getInstance().screen;
        boolean ours = screen != null && screen.getClass().getName()
                .startsWith("io.github.sunthemoon.advancedrocketrycommunity.");
        receive(screen, ours, event.isOverlay(), event.getMessage(), Util.getMillis());
    }

    static void receive(Object screen, boolean ours, boolean overlay, Component message, long now) {
        boolean mirror = overlay || WarpScreenNotice.replacesCountdown(NOTICE.current(screen, now).orElse(null), message);
        boolean pin = ours && overlay && screen instanceof RefusalOwner owner && owner.refusal(message);
        NOTICE.accept(screen, ours, mirror, message.copy(), now, pin);
    }

    @SubscribeEvent
    public static void render(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        Component message = NOTICE.current(screen, Util.getMillis()).orElse(null);
        if (message == null || screen.width < 32 || screen.height < 32) { return; }
        var graphics = event.getGuiGraphics();
        var font = Minecraft.getInstance().font;
        ScreenNoticeLayout.Bounds bounds = screen instanceof StationControllerScreen controller
                && WarpScreenNotice.isNotice(message) ? controller.noticeBounds().orElse(null)
                : ScreenNoticeLayout.bottom(screen.width, screen.height);
        if (bounds == null) { return; }
        int x = bounds.x(), y = bounds.y();
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(0, 0, 400);
            graphics.fill(x, y, x + bounds.width(), y + bounds.height(), 0xF0101820);
            graphics.renderOutline(x, y, bounds.width(), bounds.height(), 0xFFFFC857);
            TEXT.clear();
            TEXT.centered(graphics, font, message, x + 4, y + 4, bounds.width() - 8, 0xFFFFE5AE);
            TEXT.renderTooltip(graphics, font, 0, 0, event.getMouseX(), event.getMouseY());
        } finally { graphics.pose().popPose(); }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            NOTICE.current(Minecraft.getInstance().screen, Util.getMillis());
        }
    }

    @SubscribeEvent
    public static void closed(ScreenEvent.Closing event) { clear(); }

    @SubscribeEvent
    public static void loggedOut(ClientPlayerNetworkEvent.LoggingOut event) { clear(); }

    private static void clear() {
        NOTICE.clear();
        TEXT.clear();
    }
}
