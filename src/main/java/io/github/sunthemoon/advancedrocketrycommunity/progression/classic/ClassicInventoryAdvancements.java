package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Additive inventory tutorials; parents control display, not acquisition eligibility. */
public final class ClassicInventoryAdvancements {
    public static final String CRITERION = "has_inventory";
    public static final String BACKGROUND = "minecraft:textures/gui/advancements/backgrounds/stone.png";

    private ClassicInventoryAdvancements() { }

    public enum Goal {
        ROOT("root", null, "minecraft:crafting_table"),
        BLOCK_PRESS("block_press", "root", "advancedrocketrycommunity:small_plate_press"),
        ROLLING("rolling", "block_press", "advancedrocketrycommunity:rolling_machine"),
        ELECTROLYSIS("electrolysis", "rolling", "advancedrocketrycommunity:electrolyzer"),
        SUITED_UP("suited_up", "root", "advancedrocketrycommunity:space_suit_helmet",
                "advancedrocketrycommunity:space_suit_chestplate",
                "advancedrocketrycommunity:space_suit_leggings", "advancedrocketrycommunity:space_suit_boots"),
        WARP_CORE("warp_core", "electrolysis", "advancedrocketrycommunity:warp_core");

        private final String suffix;
        private final String parent;
        private final List<String> items;

        Goal(String suffix, String parent, String... items) {
            this.suffix = suffix;
            this.parent = parent;
            this.items = List.of(items);
        }

        public String suffix() { return suffix; }
        public String id() { return ModIdentity.MOD_ID + ":classic/" + suffix; }
        public String parentId() { return parent == null ? null : ModIdentity.MOD_ID + ":classic/" + parent; }
        public List<String> items() { return items; }
        public String titleKey() { return "advancement." + ModIdentity.MOD_ID + ".classic." + suffix + ".title"; }
        public String descriptionKey() {
            return "advancement." + ModIdentity.MOD_ID + ".classic." + suffix + ".description";
        }
        public String file() { return "data/" + ModIdentity.MOD_ID + "/advancements/classic/" + suffix + ".json"; }
    }

    /** Fresh JSON each call: callers cannot mutate a retained generation template. */
    public static Map<String, JsonObject> files() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (Goal goal : Goal.values()) {
            JsonObject json = new JsonObject();
            if (goal.parentId() != null) {
                json.addProperty("parent", goal.parentId());
            }
            JsonObject display = new JsonObject();
            JsonObject icon = new JsonObject();
            icon.addProperty("item", goal.items().get(0));
            display.add("icon", icon);
            display.add("title", translated(goal.titleKey()));
            display.add("description", translated(goal.descriptionKey()));
            display.addProperty("frame", "task");
            display.addProperty("show_toast", true);
            display.addProperty("announce_to_chat", false);
            display.addProperty("hidden", false);
            if (goal == Goal.ROOT) {
                display.addProperty("background", BACKGROUND);
            }
            json.add("display", display);

            JsonArray items = new JsonArray();
            for (String id : goal.items()) {
                JsonObject predicate = new JsonObject();
                JsonArray identities = new JsonArray();
                identities.add(id);
                predicate.add("items", identities);
                items.add(predicate);
            }
            JsonObject conditions = new JsonObject();
            conditions.add("items", items);
            JsonObject criterion = new JsonObject();
            criterion.addProperty("trigger", "minecraft:inventory_changed");
            criterion.add("conditions", conditions);
            JsonObject criteria = new JsonObject();
            criteria.add(CRITERION, criterion);
            json.add("criteria", criteria);

            // One trigger checks all suit predicates against the same current inventory.
            JsonArray group = new JsonArray();
            group.add(CRITERION);
            JsonArray requirements = new JsonArray();
            requirements.add(group);
            json.add("requirements", requirements);
            json.add("rewards", new JsonObject());
            files.put(goal.file(), json);
        }
        return Collections.unmodifiableMap(files);
    }

    private static JsonObject translated(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }
}
