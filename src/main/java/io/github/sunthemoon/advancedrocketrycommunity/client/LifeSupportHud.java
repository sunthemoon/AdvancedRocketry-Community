package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Layout;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** Compact high-contrast HUD; all values are display-only S2C snapshots. */
public final class LifeSupportHud {
    public static final IGuiOverlay OVERLAY = LifeSupportHud::render;

    private static final int NORMAL_MIN_WIDTH = 136;
    private static final int BAR_HEIGHT = 6;
    private static final int TEXT_BAR_GAP = 2;

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
        PanelSettings environmentSettings = ClientConfig.environmentHudSettings();
        PanelSettings oxygenSettings = ClientConfig.oxygenHudSettings();
        int accent = snapshot.status().protectedFromVacuum() ? 0xFF58E1D1 : 0xFFFF665E;
        if (snapshot.breathability() == BreathabilityState.PENDING) {
            accent = 0xFFFFC857;
        }

        Component status = Component.translatable(
                "hud.advancedrocketrycommunity.life_support.status",
                Component.translatable(
                        "hud.advancedrocketrycommunity.life_support.state."
                                + snapshot.status().diagnosticKey()
                )
        ).withStyle(ChatFormatting.WHITE);
        Component oxygen = Component.translatable(
                "hud.advancedrocketrycommunity.life_support.oxygen",
                snapshot.oxygenUnits(),
                AtmosphereLimits.SUIT_OXYGEN_CAPACITY,
                snapshot.equippedSuitPieces()
        );

        PanelText environmentText = measure(font, status, screenWidth, false);
        PanelText oxygenText = measure(font, oxygen, screenWidth, true);
        Layout layout = LifeSupportHudLayout.place(screenWidth, screenHeight,
                environmentSettings, oxygenSettings, environmentText.normalSize(), oxygenText.normalSize(),
                environmentText.compactSize(), oxygenText.compactSize());
        boolean compact = layout.mode() == Mode.COMPACT || layout.mode() == Mode.UNFIT;
        drawPanel(graphics, font, layout.environment(), environmentText, compact, accent, 0xFFF4FAFF, -1);
        drawPanel(graphics, font, layout.oxygen(), oxygenText, compact, accent, 0xFFD8E8F0, snapshot.oxygenUnits());
    }

    private static PanelText measure(Font font, Component text, int screenWidth, boolean oxygen) {
        int lineHeight = font.lineHeight;
        int normalWidth = Math.toIntExact(Math.max(NORMAL_MIN_WIDTH, (long) font.width(text) + 14L));
        int normalHeight = Math.toIntExact((long) lineHeight + (oxygen ? 19L : 10L));
        int wrapWidth = Math.toIntExact(Math.max(1L, (long) screenWidth - 12L));
        List<FormattedCharSequence> lines = List.copyOf(font.split(text, wrapWidth));
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        // The stripe occupies the two-pixel left padding; text is not narrowed or scissored.
        int compactWidth = Math.toIntExact(Math.max(oxygen ? 1L : 0L, widest) + 4L);
        int textHeight = Math.toIntExact(Math.multiplyExact((long) lines.size(), lineHeight));
        int compactHeight = Math.toIntExact((long) textHeight + 4L + (oxygen ? TEXT_BAR_GAP + BAR_HEIGHT : 0L));
        return new PanelText(text, lines, new Size(normalWidth, normalHeight),
                new Size(compactWidth, compactHeight), textHeight);
    }

    private static void drawPanel(
            GuiGraphics graphics,
            Font font,
            Rect bounds,
            PanelText text,
            boolean compact,
            int accent,
            int color,
            int oxygenUnits
    ) {
        int padding = compact ? 2 : 7;
        int textY = compact ? 2 : 5;
        int textHeight = compact ? text.compactTextHeight() : font.lineHeight;
        int barY = Math.toIntExact((long) textY + textHeight + TEXT_BAR_GAP);
        int barBottom = Math.addExact(barY, BAR_HEIGHT);
        int barWidth = Math.toIntExact((long) bounds.width() - 2L * padding);
        int barRight = Math.addExact(padding, barWidth);
        int filled = Math.toIntExact(Math.max(0L, Math.min(barWidth,
                (long) oxygenUnits * barWidth / AtmosphereLimits.SUIT_OXYGEN_CAPACITY)));

        graphics.pose().pushPose();
        try {
            graphics.pose().translate((double) bounds.x(), (double) bounds.y(), 0.0);
            graphics.fill(0, 0, bounds.width(), bounds.height(), 0xC8101820);
            graphics.fill(0, 0, 2, bounds.height(), accent);
            if (compact) {
                int y = textY;
                for (FormattedCharSequence line : text.compactLines()) {
                    graphics.drawString(font, line, padding, y, color, false);
                    y = Math.addExact(y, font.lineHeight);
                }
            } else {
                graphics.drawString(font, text.full(), padding, textY, color, false);
            }
            if (oxygenUnits >= 0) {
                graphics.fill(padding, barY, barRight, barBottom, 0xFF263746);
                graphics.fill(padding, barY, Math.addExact(padding, filled), barBottom, 0xFF5FBFF9);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    private record PanelText(
            Component full,
            List<FormattedCharSequence> compactLines,
            Size normalSize,
            Size compactSize,
            int compactTextHeight
    ) {
    }
}
