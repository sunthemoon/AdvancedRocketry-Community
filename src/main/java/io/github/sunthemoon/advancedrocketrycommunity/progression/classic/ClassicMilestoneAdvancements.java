package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ADR-066 §6.2: the eleven classic advancements beside the six inventory tutorials of
 * {@link ClassicInventoryAdvancements}. Parents control display, not eligibility; none grants a reward or locks a
 * recipe. A PENDING criterion is vanilla {@code minecraft:impossible}: its content or durable authority is not in
 * this tree yet (see the W12 record), so no event can earn it.
 */
public final class ClassicMilestoneAdvancements {
    public static final String INVENTORY_CRITERION = ClassicInventoryAdvancements.CRITERION;
    public static final String EVENT_CRITERION = "event";
    public static final String ARRIVAL_CRITERION = "arrived";
    public static final String PENDING_CRITERION = "pending";

    private ClassicMilestoneAdvancements() { }

    public enum Frame {
        TASK, GOAL, CHALLENGE;

        public String serializedName() { return name().toLowerCase(java.util.Locale.ROOT); }
    }

    public enum Kind {
        /** Vanilla {@code inventory_changed} on one exact item. */
        INVENTORY,
        /** {@link ClassicEventTrigger} for one {@link ClassicEvent}. */
        EVENT,
        /** Vanilla {@code changed_dimension} into one Level. */
        ARRIVAL,
        /** Vanilla {@code impossible}: the trigger cannot fire in this tree yet. */
        PENDING
    }

    public enum Milestone {
        ARC_FURNACE("feeltheheat", "arc_furnace", "block_press", Frame.TASK,
                "advancedrocketrycommunity:arc_furnace", Kind.INVENTORY, "advancedrocketrycommunity:arc_furnace"),
        LATHE("spindoctor", "lathe", "block_press", Frame.TASK,
                "advancedrocketrycommunity:lathe", Kind.INVENTORY, "advancedrocketrycommunity:lathe"),
        /** TODO(W10): inventory_changed on the crystallizer item once the C16c crystallizer is merged. */
        CRYSTALLIZER("crystalline", "crystallizer", "arc_furnace", Frame.TASK,
                "advancedrocketrycommunity:silicon_boule", Kind.PENDING, null),
        DILITHIUM("dilithium", "dilithium", "crystallizer", Frame.GOAL,
                "advancedrocketrycommunity:dilithium_crystal", Kind.INVENTORY,
                "advancedrocketrycommunity:dilithium_crystal"),
        FORMED_MULTIBLOCK("holographic", "formed_multiblock", "rolling", Frame.TASK,
                "advancedrocketrycommunity:machine_casing", Kind.EVENT, "formed_multiblock"),
        SEAT_ON_TNT("beer", "seat_on_tnt", "root", Frame.TASK,
                "advancedrocketrycommunity:rocket_seat", Kind.EVENT, "seat_on_tnt"),
        MOON_LANDING("moonlanding", "moon_landing", "moon_surface_visit", Frame.GOAL,
                "advancedrocketrycommunity:rocket_motor", Kind.EVENT, "moon_landing"),
        /** TODO(D3 source outbox): the world-first Moon landing receipt; blocked, see the W12 record. */
        ONE_SMALL_STEP("onesmallstep", "one_small_step", "moon_landing", Frame.CHALLENGE,
                "advancedrocketrycommunity:space_suit_boots", Kind.PENDING, null),
        MOON_SURFACE_VISIT("wenttothemoon", "moon_surface_visit", "suited_up", Frame.TASK,
                "advancedrocketrycommunity:moon_turf", Kind.ARRIVAL, "advancedrocketrycommunity:moon"),
        STATION_WARP("givingitallshesgot", "station_warp", "warp_core", Frame.GOAL,
                "advancedrocketrycommunity:station_deployment_kit", Kind.EVENT, "station_warp"),
        /** TODO(D3 source outbox): the world-first station warp receipt; blocked, see the W12 record. */
        FIRST_STATION_WARP("flightofpheonix", "first_station_warp", "station_warp", Frame.CHALLENGE,
                "minecraft:fire_charge", Kind.PENDING, null);

        private final String legacyName;
        private final String suffix;
        private final String parent;
        private final Frame frame;
        private final String icon;
        private final Kind kind;
        private final String target;

        Milestone(String legacyName, String suffix, String parent, Frame frame, String icon, Kind kind, String target) {
            this.legacyName = legacyName;
            this.suffix = suffix;
            this.parent = parent;
            this.frame = frame;
            this.icon = icon;
            this.kind = kind;
            this.target = target;
        }

        public String legacyName() { return legacyName; }
        public String suffix() { return suffix; }
        public String id() { return ModIdentity.MOD_ID + ":classic/" + suffix; }
        public String parentId() { return ModIdentity.MOD_ID + ":classic/" + parent; }
        public Frame frame() { return frame; }
        public String icon() { return icon; }
        public Kind kind() { return kind; }
        /** The exact item, event name or Level of the criterion; null for PENDING. */
        public String target() { return target; }
        public String criterion() {
            return switch (kind) {
                case INVENTORY -> INVENTORY_CRITERION;
                case EVENT -> EVENT_CRITERION;
                case ARRIVAL -> ARRIVAL_CRITERION;
                case PENDING -> PENDING_CRITERION;
            };
        }
        public String titleKey() { return "advancement." + ModIdentity.MOD_ID + ".classic." + suffix + ".title"; }
        public String descriptionKey() {
            return "advancement." + ModIdentity.MOD_ID + ".classic." + suffix + ".description";
        }
        public String file() { return "data/" + ModIdentity.MOD_ID + "/advancements/classic/" + suffix + ".json"; }
    }

    /** Fresh JSON each call: callers cannot mutate a retained generation template. */
    public static Map<String, JsonObject> files() {
        Map<String, JsonObject> files = new LinkedHashMap<>();
        for (Milestone milestone : Milestone.values()) {
            JsonObject json = new JsonObject();
            json.addProperty("parent", milestone.parentId());
            JsonObject display = new JsonObject();
            JsonObject icon = new JsonObject();
            icon.addProperty("item", milestone.icon());
            display.add("icon", icon);
            display.add("title", translated(milestone.titleKey()));
            display.add("description", translated(milestone.descriptionKey()));
            display.addProperty("frame", milestone.frame().serializedName());
            display.addProperty("show_toast", true);
            display.addProperty("announce_to_chat", false);
            display.addProperty("hidden", false);
            json.add("display", display);

            JsonObject criteria = new JsonObject();
            criteria.add(milestone.criterion(), criterion(milestone));
            json.add("criteria", criteria);
            JsonArray group = new JsonArray();
            group.add(milestone.criterion());
            JsonArray requirements = new JsonArray();
            requirements.add(group);
            json.add("requirements", requirements);
            json.add("rewards", new JsonObject());
            files.put(milestone.file(), json);
        }
        return Collections.unmodifiableMap(files);
    }

    private static JsonObject criterion(Milestone milestone) {
        JsonObject criterion = new JsonObject();
        JsonObject conditions = new JsonObject();
        switch (milestone.kind()) {
            case INVENTORY -> {
                criterion.addProperty("trigger", "minecraft:inventory_changed");
                JsonArray identities = new JsonArray();
                identities.add(milestone.target());
                JsonObject predicate = new JsonObject();
                predicate.add("items", identities);
                JsonArray items = new JsonArray();
                items.add(predicate);
                conditions.add("items", items);
            }
            case EVENT -> {
                criterion.addProperty("trigger", ClassicEventTrigger.ID.toString());
                conditions.addProperty(ClassicEventTrigger.EVENT, milestone.target());
            }
            case ARRIVAL -> {
                criterion.addProperty("trigger", "minecraft:changed_dimension");
                conditions.addProperty("to", milestone.target());
            }
            case PENDING -> {
                criterion.addProperty("trigger", "minecraft:impossible");
                return criterion;
            }
        }
        criterion.add("conditions", conditions);
        return criterion;
    }

    private static JsonObject translated(String key) {
        JsonObject component = new JsonObject();
        component.addProperty("translate", key);
        return component;
    }
}
