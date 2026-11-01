package io.github.sunthemoon.advancedrocketrycommunity.client.model;

import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Fixed idle poses. Only checked parser input changes; source resources remain byte copies. */
final class ObjModelIdleGeometry {
    private ObjModelIdleGeometry() { }

    static Set<String> hiddenParts(ObjModelFixture fixture) {
        return switch (fixture.id()) {
            case "lathe" -> Set.of("Rod");
            case "crystallizer" -> Set.of("Liquid");
            case "observatory" -> Set.of("Scope", "Axis");
            case "elevator_anchor" -> Set.of("Door", "Tether", "Capsule");
            default -> Set.of();
        };
    }

    /** Called before mirroring, after the full source has passed geometry, reference and budget checks. */
    static byte[] prepare(ObjModelFixture fixture, byte[] checked) {
        boolean lathe = fixture.id().equals("lathe"), crystal = fixture.id().equals("crystallizer");
        boolean beacon = fixture.id().equals("planetary_beacon"), observatory = fixture.id().equals("observatory");
        boolean rolling = fixture.id().equals("rolling_machine"), elevator = fixture.id().equals("elevator_anchor");
        if (!lathe && !crystal && !beacon && !observatory && !rolling && !elevator) { return checked; }
        var output = new StringBuilder(checked.length);
        String part = "";
        for (String line : new String(checked, StandardCharsets.UTF_8).split("\n")) {
            String[] tokens = line.strip().split("\\s+");
            if (tokens.length > 1 && (tokens[0].equals("o") || tokens[0].equals("g"))) { part = tokens[1]; }
            // The source's roller_3 is byte-equivalent to roller_2 after index rebasing (pinned by tests).
            // Reuse it as the legacy renderer's second Roller_2 instance, without growing the mesh or budgets.
            if (rolling && part.equals("roller_3") && (tokens[0].equals("o") || tokens[0].equals("g"))) {
                line = tokens[0] + " roller_2_upper";
            }
            if (rolling && part.startsWith("roller_") && tokens[0].equals("v")) {
                double dx = part.equals("roller_1") ? 1.375 : part.equals("roller_2") ? 1.9375 : 1.65625;
                double dy = part.equals("roller_3") ? 1.125 : 0.6875;
                tokens[1] = Double.toString(Double.parseDouble(tokens[1]) + dx);
                tokens[2] = Double.toString(Double.parseDouble(tokens[2]) + dy);
                line = String.join(" ", tokens);
            }
            // Parts without an activity/recipe projection are absent in this idle pose.
            if (tokens[0].equals("f") && hiddenParts(fixture).contains(part)) { continue; }
            if (lathe && part.equals("Shaft") && tokens[0].equals("v")) {
                tokens[1] = Double.toString(Double.parseDouble(tokens[1]) + 0.375);
                tokens[2] = Double.toString(Double.parseDouble(tokens[2]) + 0.9375);
                line = String.join(" ", tokens);
            }
            if (beacon && (part.equals("InnerSpin") || part.equals("OuterSpin")) && tokens[0].equals("v")) {
                tokens[1] = Double.toString(Double.parseDouble(tokens[1]) + 1);
                line = String.join(" ", tokens);
            }
            output.append(line).append('\n');
        }
        return output.toString().getBytes(StandardCharsets.UTF_8);
    }
}
