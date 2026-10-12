package io.github.sunthemoon.advancedrocketrycommunity.client;

import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.AtmosphereLimits;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.life.BreathabilityState;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.network.LifeSupportClientCache;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.server.PlayerLifeSupportSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Anchor;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Mode;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Panel;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.PanelSettings;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Placement;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Rect;
import io.github.sunthemoon.advancedrocketrycommunity.client.LifeSupportHudLayout.Size;
import io.github.sunthemoon.advancedrocketrycommunity.client.equipment.SuitHudClient;
import io.github.sunthemoon.advancedrocketrycommunity.config.ClientConfig;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/**
 * Compact high-contrast HUD; all values are display-only server-synchronized data. Environment and oxygen come from
 * the life-support snapshot, hydrogen and modules from the worn suit (ADR-066 section 7.1), and the rocket readout
 * from the ridden rocket's synchronized flight state and fuel. Every visible panel is placed in one layout pass.
 */
public final class LifeSupportHud {
    public static final IGuiOverlay OVERLAY = LifeSupportHud::render;

    private static final int NORMAL_MIN_WIDTH = 136;
    private static final int BAR_HEIGHT = 6;
    private static final int TEXT_BAR_GAP = 2;
    /** The rocket readout has no legacy position key; it requests the top-left corner. */
    private static final PanelSettings ROCKET_SETTINGS = new PanelSettings(6, 6, Anchor.START, Anchor.START);

    private LifeSupportHud() {
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight
    ) {
        var player = gui.getMinecraft().player;
        if (player == null || gui.getMinecraft().options.hideGui) {
            return;
        }
        List<PanelContent> contents = new ArrayList<>(LifeSupportHudLayout.MAX_PANELS);
        PlayerLifeSupportSnapshot snapshot = LifeSupportClientCache.current().orElse(null);
        if (snapshot != null) {
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
            contents.add(new PanelContent(ClientConfig.environmentHudSettings(), status, accent, 0xFFF4FAFF,
                    false, 0, 0));
            contents.add(new PanelContent(ClientConfig.oxygenHudSettings(), oxygen, accent, 0xFFD8E8F0,
                    true, snapshot.oxygenUnits(), AtmosphereLimits.SUIT_OXYGEN_CAPACITY));
        }
        SuitHudClient.Summary suit = SuitHudClient.current();
        if (suit.showsHydrogen()) {
            contents.add(new PanelContent(ClientConfig.hydrogenHudSettings(), Component.translatable(
                    "hud.advancedrocketrycommunity.hydrogen", suit.hydrogen(), suit.hydrogenMaximum()),
                    0xFF71E5EE, 0xFFD8F4F6, true, suit.hydrogen(), suit.hydrogenMaximum()));
        }
        if (suit.showsSuit()) {
            contents.add(new PanelContent(ClientConfig.suitHudSettings(), modules(suit.modules()),
                    0xFFB7D5DD, 0xFFE6EEF2, false, 0, 0));
        }
        if (player.getVehicle() instanceof RocketEntity rocket) {
            var state = rocket.displayedFlightState();
            Component readout = Component.translatable("hud.advancedrocketrycommunity.rocket",
                    Component.translatable("flight.advancedrocketrycommunity.state."
                            + state.name().toLowerCase(Locale.ROOT)),
                    rocket.displayedFuelAmount(), Mth.floor(rocket.getY()));
            contents.add(new PanelContent(ROCKET_SETTINGS, readout, state.inMotion() ? 0xFFFF9F43 : 0xFF9FB3C8,
                    0xFFF4FAFF, false, 0, 0));
        }
        if (contents.isEmpty()) {
            return;
        }

        Font font = gui.getMinecraft().font;
        List<PanelText> texts = new ArrayList<>(contents.size());
        List<Panel> panels = new ArrayList<>(contents.size());
        for (PanelContent content : contents) {
            PanelText text = measure(font, content.text(), screenWidth, content.gauge());
            texts.add(text);
            panels.add(new Panel(content.settings(), text.normalSize(), text.compactSize()));
        }
        Placement placement = LifeSupportHudLayout.placeAll(screenWidth, screenHeight, panels);
        boolean compact = placement.mode() == Mode.COMPACT || placement.mode() == Mode.UNFIT;
        for (int i = 0; i < contents.size(); i++) {
            drawPanel(graphics, font, placement.panels().get(i), texts.get(i), compact, contents.get(i));
        }
    }

    private static Component modules(List<ModuleKind> kinds) {
        MutableComponent labels = Component.empty();
        for (int i = 0; i < kinds.size(); i++) {
            if (i > 0) {
                labels.append("  ");
            }
            labels.append(Component.translatable("hud.advancedrocketrycommunity.suit.module."
                    + kinds.get(i).stableId()));
        }
        return Component.translatable("hud.advancedrocketrycommunity.suit", labels);
    }

    private static PanelText measure(Font font, Component text, int screenWidth, boolean gauge) {
        int lineHeight = font.lineHeight;
        int normalWidth = Math.toIntExact(Math.max(NORMAL_MIN_WIDTH, (long) font.width(text) + 14L));
        int normalHeight = Math.toIntExact((long) lineHeight + (gauge ? 19L : 10L));
        int wrapWidth = Math.toIntExact(Math.max(1L, (long) screenWidth - 12L));
        List<FormattedCharSequence> lines = List.copyOf(font.split(text, wrapWidth));
        int widest = 0;
        for (FormattedCharSequence line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        // The stripe occupies the two-pixel left padding; text is not narrowed or scissored.
        int compactWidth = Math.toIntExact(Math.max(gauge ? 1L : 0L, widest) + 4L);
        int textHeight = Math.toIntExact(Math.multiplyExact((long) lines.size(), lineHeight));
        int compactHeight = Math.toIntExact((long) textHeight + 4L + (gauge ? TEXT_BAR_GAP + BAR_HEIGHT : 0L));
        return new PanelText(text, lines, new Size(normalWidth, normalHeight),
                new Size(compactWidth, compactHeight), textHeight);
    }

    private static void drawPanel(
            GuiGraphics graphics,
            Font font,
            Rect bounds,
            PanelText text,
            boolean compact,
            PanelContent content
    ) {
        int padding = compact ? 2 : 7;
        int textY = compact ? 2 : 5;
        int textHeight = compact ? text.compactTextHeight() : font.lineHeight;
        int barY = Math.toIntExact((long) textY + textHeight + TEXT_BAR_GAP);
        int barBottom = Math.addExact(barY, BAR_HEIGHT);
        int barWidth = Math.toIntExact((long) bounds.width() - 2L * padding);
        int barRight = Math.addExact(padding, barWidth);
        int filled = content.capacity() <= 0 ? 0 : Math.toIntExact(Math.max(0L, Math.min(barWidth,
                (long) content.value() * barWidth / content.capacity())));

        graphics.pose().pushPose();
        try {
            graphics.pose().translate((double) bounds.x(), (double) bounds.y(), 0.0);
            graphics.fill(0, 0, bounds.width(), bounds.height(), 0xC8101820);
            graphics.fill(0, 0, 2, bounds.height(), content.accent());
            if (compact) {
                int y = textY;
                for (FormattedCharSequence line : text.compactLines()) {
                    graphics.drawString(font, line, padding, y, content.color(), false);
                    y = Math.addExact(y, font.lineHeight);
                }
            } else {
                graphics.drawString(font, text.full(), padding, textY, content.color(), false);
            }
            if (content.gauge()) {
                graphics.fill(padding, barY, barRight, barBottom, 0xFF263746);
                graphics.fill(padding, barY, Math.addExact(padding, filled), barBottom, 0xFF5FBFF9);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    /** One visible panel's display data; a gauge draws {@code value / capacity} under the text. */
    private record PanelContent(
            PanelSettings settings,
            Component text,
            int accent,
            int color,
            boolean gauge,
            int value,
            int capacity
    ) {
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
