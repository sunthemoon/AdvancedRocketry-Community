package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Compact high-contrast HUD; all values are display-only S2C snapshots. */
public final class LifeSupportHud {
    public static final IGuiOverlay OVERLAY = LifeSupportHud::render;

    private static final int PANEL_WIDTH = 136;
    private static final int PANEL_HEIGHT = 38;

    private LifeSupportHud() {
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight
    ) {
        if (gui.getMinecraft().player == null || gui.getMinecraft().options.hideGui) {
            return;
        }
        PlayerLifeSupportSnapshot snapshot = LifeSupportClientCache.current().orElse(null);
        if (snapshot == null) {
            return;
        }

        Font font = gui.getMinecraft().font;
        int width = Math.min(PANEL_WIDTH, Math.max(96, screenWidth - 8));
        int x = Math.max(4, screenWidth - width - 6);
        int y = 6;
        int accent = snapshot.status().protectedFromVacuum() ? 0xFF58E1D1 : 0xFFFF665E;
        if (snapshot.breathability() == io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState.PENDING) {
            accent = 0xFFFFC857;
        }

        graphics.fill(x, y, x + width, y + PANEL_HEIGHT, 0xC8101820);
        graphics.fill(x, y, x + 2, y + PANEL_HEIGHT, accent);
        Component status = Component.translatable(
                "hud.advancedrocketrycommunity.life_support.status",
                Component.translatable(
                        "hud.advancedrocketrycommunity.life_support.state."
                                + snapshot.status().diagnosticKey()
                )
        ).withStyle(ChatFormatting.WHITE);
        graphics.drawString(font, status, x + 7, y + 5, 0xFFF4FAFF, false);

        int barX = x + 7;
        int barY = y + 20;
        int barWidth = width - 14;
        int filled = Mth.clamp(
                snapshot.oxygenUnits() * barWidth / AtmosphereLimits.SUIT_OXYGEN_CAPACITY,
                0,
                barWidth
        );
        graphics.fill(barX, barY, barX + barWidth, barY + 6, 0xFF263746);
        graphics.fill(barX, barY, barX + filled, barY + 6, 0xFF5FBFF9);
        Component oxygen = Component.translatable(
                "hud.advancedrocketrycommunity.life_support.oxygen",
                snapshot.oxygenUnits(),
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY,
                snapshot.equippedSuitPieces()
        );
        graphics.drawString(font, oxygen, barX, y + 28, 0xFFD8E8F0, false);
    }
}
