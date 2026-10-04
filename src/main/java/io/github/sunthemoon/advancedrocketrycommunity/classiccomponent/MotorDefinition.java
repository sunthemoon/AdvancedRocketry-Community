package io.github.sunthemoon.advancedrocketrycommunity.classiccomponent;

import java.util.List;

/** Accepted C16 ingredient multisets and stable tier identities, independent of Forge. */
public enum MotorDefinition {
    MOTOR("motor", "copper", "Motor", "电动机", 0xFFD59663),
    ADVANCED("advanced_motor", "gold", "Advanced Motor", "高级电动机", 0xFFE2C473),
    ENHANCED("enhanced_motor", "titanium", "Enhanced Motor", "增强电动机", 0xFFADA6D1),
    ELITE("elite_motor", "iridium", "Elite Motor", "精英电动机", 0xFFC7C3B4);

    public static final String NAMESPACE = "advancedrocketrycommunity";
    public static final String TAG_ID = NAMESPACE + ":motors";
    public static final String ADVANCED_CASING_ID = NAMESPACE + ":endgame_casing";
    private final String id;
    private final String metal;
    private final String english;
    private final String chinese;
    private final int accent;

    MotorDefinition(String id, String metal, String english, String chinese, int accent) {
        this.id = id;
        this.metal = metal;
        this.english = english;
        this.chinese = chinese;
        this.accent = accent;
    }

    public String id() { return id; }
    public String fullId() { return NAMESPACE + ":" + id; }
    public String metal() { return metal; }
    public String english() { return english; }
    public String chinese() { return chinese; }
    public int accent() { return accent; }
    public MotorDefinition previous() { return this == MOTOR ? null : values()[ordinal() - 1]; }

    public List<Ingredient> ingredients() {
        Ingredient coil = new Ingredient(true, NAMESPACE + ":coils/" + metal, 1);
        if (this == MOTOR) {
            return List.of(coil, new Ingredient(true, "forge:plates/steel", 2),
                    new Ingredient(true, "forge:rods/iron", 2), new Ingredient(true, "forge:ingots/steel", 1));
        }
        return List.of(new Ingredient(false, previous().fullId(), 1), coil,
                new Ingredient(true, "forge:plates/" + metal, 2));
    }

    public record Ingredient(boolean tag, String id, int count) {
        public Ingredient {
            if (id == null || !id.matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || count < 1 || count > 9) {
                throw new IllegalArgumentException("Invalid motor crafting ingredient");
            }
        }
    }
}
