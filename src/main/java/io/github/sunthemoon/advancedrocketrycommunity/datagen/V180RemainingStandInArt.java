package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import javax.imageio.ImageIO;

/** W277 NEW MIT art, registered before authoring. Integer geometry only; no bitmap inputs. */
public final class V180RemainingStandInArt {
    private static final int DARK = 0xFF162F38;
    private static final int STEEL = 0xFF95A6A0;
    private static final int SHADE = 0xFF657E83;
    private static final int LIGHT = 0xFFC6D4CE;
    private static final int CYAN = 0xFF4DB5C6;
    private static final int AMBER = 0xFFD3A65E;
    private static final int BLUE = 0xFF7296D6;
    private static final List<String> TEXTURES = List.of(
            "block/oxygen_vent_grille_active", "block/guidance_computer_top", "block/rocket_assembler_top",
            "block/fuel_loader_top", "block/rocket_seat_cushion", "block/rocket_seat_frame",
            "block/satellite_terminal_top", "block/warp_core_top", "block/microwave_receiver_top",
            "block/satellite_builder_top", "block/black_hole_generator_front", "block/black_hole_generator_top",
            "block/elevator_anchor_bottom", "block/elevator_terminal_top", "block/endgame_casing_end",
            "block/gravity_field_controller_top", "block/laser_target_bottom", "block/orbital_laser_drill_top",
            "block/railgun_top", "item/station_deployment_kit", "item/nitrogen_canister_cylinder");

    private V180RemainingStandInArt() { }

    public static List<String> textures() { return TEXTURES; }

    public static int[] pixels(String name) {
        if (!TEXTURES.contains(name)) { throw new IllegalArgumentException("Unknown W277 texture: " + name); }
        if (name.equals("item/nitrogen_canister_cylinder")) {
            // Reuse the original community drawing, not an encoded bitmap. W275 outputs remain unchanged.
            int[] p = V180StandInArt.pixels("item/oxygen_canister_cylinder");
            rect(p, 5, 6, 10, 6, BLUE);
            return p;
        }
        int[] p = name.startsWith("item/") ? new int[256] : V180StandInArt.pixels("block/machine_panel_side");
        if (name.startsWith("block/")) { rect(p, 3, 4, 12, 12, DARK); }
        switch (name) {
            case "block/oxygen_vent_grille_active" -> {
                for (int x : new int[] {4, 7, 10}) {
                    rect(p, x, 5, x + 1, 10, SHADE);
                    rect(p, x, 5, x, 9, CYAN);
                }
                rect(p, 5, 12, 10, 12, CYAN);
            }
            case "block/guidance_computer_top" -> {
                screen(p);
                rect(p, 6, 6, 6, 9, CYAN); rect(p, 7, 7, 10, 7, CYAN);
                p[5 * 16 + 10] = LIGHT; p[8 * 16 + 9] = AMBER;
            }
            case "block/rocket_assembler_top" -> {
                // Two opposed grippers holding a small rocket-shaped workpiece.
                rect(p, 4, 5, 5, 10, SHADE); rect(p, 10, 5, 11, 10, SHADE);
                rect(p, 5, 9, 10, 10, LIGHT); rect(p, 7, 5, 8, 9, STEEL);
                rect(p, 7, 4, 8, 4, AMBER); rect(p, 6, 10, 9, 11, CYAN);
                p[6 * 16 + 4] = AMBER; p[6 * 16 + 11] = AMBER;
            }
            case "block/fuel_loader_top" -> {
                rect(p, 4, 7, 11, 9, SHADE); rect(p, 4, 7, 10, 7, LIGHT);
                rect(p, 7, 4, 9, 11, SHADE); rect(p, 7, 4, 7, 11, AMBER);
                rect(p, 5, 5, 11, 5, AMBER); rect(p, 8, 6, 8, 8, CYAN);
                rect(p, 10, 8, 12, 10, CYAN); p[9 * 16 + 11] = LIGHT;
            }
            case "block/rocket_seat_cushion" -> {
                rect(p, 4, 4, 11, 11, 0xFF354B60);
                rect(p, 5, 5, 10, 6, 0xFF587789); rect(p, 5, 8, 10, 10, 0xFF49677B);
                rect(p, 6, 4, 6, 11, AMBER); rect(p, 9, 4, 9, 11, AMBER);
                rect(p, 6, 7, 9, 8, SHADE); rect(p, 7, 7, 8, 8, LIGHT);
            }
            case "block/rocket_seat_frame" -> {
                rect(p, 4, 5, 6, 11, SHADE); rect(p, 5, 5, 5, 10, LIGHT);
                rect(p, 7, 8, 11, 10, 0xFF49677B); rect(p, 7, 11, 12, 12, STEEL);
                rect(p, 9, 5, 10, 7, AMBER); p[9 * 16 + 9] = AMBER;
            }
            case "block/satellite_terminal_top" -> {
                screen(p);
                // Dish and an offset tracked signal, not the old daylight-sensor grid.
                rect(p, 6, 6, 6, 8, LIGHT); rect(p, 7, 8, 9, 8, LIGHT);
                p[7 * 16 + 8] = CYAN; p[6 * 16 + 9] = CYAN; p[5 * 16 + 10] = AMBER;
            }
            case "block/warp_core_top" -> field(p, CYAN, false);
            case "block/microwave_receiver_top" -> {
                rect(p, 5, 5, 5, 8, LIGHT); rect(p, 6, 8, 8, 10, STEEL);
                rect(p, 9, 5, 10, 6, CYAN); rect(p, 10, 7, 11, 8, CYAN);
                p[5 * 16 + 7] = AMBER; rect(p, 7, 11, 10, 12, SHADE);
            }
            case "block/satellite_builder_top" -> {
                rect(p, 7, 6, 9, 9, LIGHT); rect(p, 8, 4, 8, 5, AMBER);
                rect(p, 4, 6, 5, 8, BLUE); rect(p, 11, 7, 12, 9, BLUE);
                rect(p, 6, 7, 6, 7, AMBER); rect(p, 10, 8, 10, 8, AMBER);
                rect(p, 5, 11, 10, 12, SHADE);
            }
            case "block/black_hole_generator_front" -> field(p, AMBER, true);
            case "block/black_hole_generator_top" -> {
                rect(p, 4, 5, 6, 10, SHADE); rect(p, 8, 6, 11, 11, SHADE);
                rect(p, 5, 5, 5, 9, AMBER); rect(p, 9, 7, 9, 11, AMBER);
                rect(p, 6, 7, 8, 8, LIGHT); p[11 * 16 + 4] = CYAN;
            }
            case "block/elevator_anchor_bottom" -> {
                rect(p, 4, 5, 5, 11, LIGHT); rect(p, 10, 5, 11, 11, LIGHT);
                rect(p, 6, 6, 9, 9, SHADE); rect(p, 7, 4, 8, 12, AMBER);
                p[7 * 16 + 7] = DARK; p[10 * 16 + 11] = CYAN;
            }
            case "block/elevator_terminal_top" -> {
                screen(p); rect(p, 6, 5, 6, 9, SHADE);
                rect(p, 9, 5, 9, 9, SHADE); rect(p, 6, 7, 9, 8, CYAN);
                p[6 * 16 + 7] = LIGHT;
            }
            case "block/endgame_casing_end" -> {
                rect(p, 4, 5, 6, 10, SHADE); rect(p, 8, 4, 11, 7, STEEL);
                rect(p, 8, 9, 11, 11, SHADE); rect(p, 5, 5, 5, 9, LIGHT);
                p[5 * 16 + 10] = AMBER; p[10 * 16 + 9] = LIGHT;
            }
            case "block/gravity_field_controller_top" -> {
                rect(p, 7, 5, 8, 10, BLUE); rect(p, 5, 8, 10, 8, BLUE);
                rect(p, 6, 6, 9, 6, LIGHT); rect(p, 5, 10, 10, 10, CYAN);
                rect(p, 4, 11, 11, 11, SHADE); p[4 * 16 + 8] = AMBER;
            }
            case "block/laser_target_bottom" -> {
                rect(p, 5, 5, 10, 10, SHADE); rect(p, 6, 6, 9, 9, DARK);
                rect(p, 4, 7, 11, 8, AMBER); rect(p, 7, 4, 8, 11, AMBER);
                rect(p, 7, 7, 8, 8, CYAN); p[11 * 16 + 11] = LIGHT;
            }
            case "block/orbital_laser_drill_top" -> {
                rect(p, 4, 5, 6, 9, SHADE); rect(p, 9, 7, 11, 11, SHADE);
                rect(p, 6, 6, 9, 9, CYAN); rect(p, 7, 7, 8, 8, LIGHT);
                rect(p, 5, 11, 6, 12, AMBER); p[5 * 16 + 10] = CYAN;
            }
            case "block/railgun_top" -> {
                rect(p, 5, 4, 6, 12, LIGHT); rect(p, 9, 4, 10, 12, SHADE);
                rect(p, 7, 5, 8, 10, DARK); rect(p, 7, 7, 8, 8, AMBER);
                for (int y : new int[] {5, 9, 11}) { p[y * 16 + 10] = CYAN; }
            }
            case "item/station_deployment_kit" -> {
                // Folded modular habitat with side solar wings and a docking collar.
                rect(p, 5, 4, 10, 11, DARK); rect(p, 6, 3, 9, 12, SHADE);
                rect(p, 6, 4, 8, 10, LIGHT); rect(p, 7, 5, 9, 6, CYAN);
                rect(p, 2, 6, 4, 9, BLUE); rect(p, 11, 7, 13, 10, BLUE);
                rect(p, 3, 6, 3, 9, CYAN); rect(p, 12, 7, 12, 10, CYAN);
                rect(p, 7, 1, 8, 3, AMBER); rect(p, 7, 12, 8, 13, AMBER);
            }
            default -> throw new IllegalStateException("Missing W277 drawing: " + name);
        }
        return p;
    }

    private static void screen(int[] p) {
        rect(p, 4, 4, 11, 10, SHADE); rect(p, 5, 5, 10, 9, DARK);
        rect(p, 4, 12, 9, 12, STEEL); p[12 * 16 + 11] = CYAN;
    }

    private static void field(int[] p, int accent, boolean singularity) {
        // Offset segmented containment arms around a field aperture.
        rect(p, 4, 5, 5, 9, SHADE); rect(p, 6, 4, 10, 5, accent);
        rect(p, 10, 6, 11, 10, SHADE); rect(p, 5, 10, 9, 11, accent);
        rect(p, 7, 6, 8, 9, singularity ? 0xFF091D29 : BLUE);
        if (!singularity) { p[7 * 16 + 7] = LIGHT; }
        p[11 * 16 + 11] = LIGHT;
    }

    private static void rect(int[] p, int x1, int y1, int x2, int y2, int color) {
        for (int y = y1; y <= y2; y++) {
            for (int x = x1; x <= x2; x++) { p[y * 16 + x] = color; }
        }
    }

    public static byte[] png(String name) {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 16, 16, pixels(name), 0, 16);
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "PNG", bytes)) { throw new IllegalStateException("No PNG writer"); }
            return bytes.toByteArray();
        } catch (IOException failure) { throw new IllegalStateException("W277 texture encoding failed", failure); }
    }
}
