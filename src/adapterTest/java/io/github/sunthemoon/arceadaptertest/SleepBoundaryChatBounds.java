package io.github.sunthemoon.arceadaptertest;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Finite native-chat receiver admission before native JSON allocation or virtual receiver behavior. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SleepBoundaryChatBounds {
    private SleepBoundaryChatBounds() { }

    static long preflight(Component component) {
        int[] budget = new int[2];
        chatPreflight(component, 0, budget);
        return 4096L + budget[0] * 1024L + budget[1];
    }

    private static void chatText(String text, int[] budget) {
        if (text != null) {
            if (text.length() > 1024 || budget[1] + text.length() * 6L > 8192) { throw new IllegalStateException("chat string preflight cap"); }
            budget[1] += text.length() * 6;
        }
    }
    private static void chatPreflight(Component component, int depth, int[] budget) {
        if (component == null || component.getClass() != MutableComponent.class || depth > 8 || ++budget[0] > 32) {
            throw new IllegalStateException("unsupported or oversized native chat component tree");
        }
        var content = component.getContents();
        if (content instanceof LiteralContents literal) { chatText(literal.text(), budget); }
        else if (content.getClass() == TranslatableContents.class) {
            var translated = (TranslatableContents) content;
            chatText(translated.getKey(), budget); chatText(translated.getFallback(), budget);
            Object[] args = translated.getArgs();
            if (args.length > 4) { throw new IllegalStateException("chat argument preflight cap"); }
            for (Object arg : args) {
                if (arg instanceof Component nested) { chatPreflight(nested, depth + 1, budget); }
                else if (arg instanceof String text) { chatText(text, budget); }
                else if (arg != null && !(arg instanceof Boolean || arg instanceof Integer || arg instanceof Long
                        || arg instanceof Short || arg instanceof Byte || arg instanceof Float || arg instanceof Double)) {
                    throw new IllegalStateException("unsupported native chat argument");
                }
            }
        } else if (content != ComponentContents.EMPTY) { throw new IllegalStateException("unsupported native chat contents"); }
        var style = component.getStyle();
        if (style.getClass() != Style.class) { throw new IllegalStateException("unsupported native chat style"); }
        chatText(style.getInsertion(), budget);
        chatText(style.getFont().getNamespace(), budget); chatText(style.getFont().getPath(), budget);
        ClickEvent click = style.getClickEvent();
        if (click != null) {
            if (click.getClass() != ClickEvent.class) { throw new IllegalStateException("unsupported click receiver"); }
            chatText(click.getValue(), budget);
        }
        HoverEvent hover = style.getHoverEvent();
        if (hover != null) {
            if (hover.getClass() != HoverEvent.class) { throw new IllegalStateException("unsupported hover receiver"); }
            if (hover.getAction() == HoverEvent.Action.SHOW_TEXT) { chatPreflight(hover.getValue(HoverEvent.Action.SHOW_TEXT), depth + 1, budget); }
            else if (hover.getAction() == HoverEvent.Action.SHOW_ENTITY) {
                var entity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
                if (entity == null || entity.getClass() != HoverEvent.EntityTooltipInfo.class) {
                    throw new IllegalStateException("unsupported hover entity receiver");
                }
                var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.type);
                if (type == null || entity.id == null) { throw new IllegalStateException("hover entity metadata unavailable"); }
                chatText(type.getNamespace(), budget); chatText(type.getPath(), budget);
                if (entity.name != null) { chatPreflight(entity.name, depth + 1, budget); }
            } else { throw new IllegalStateException("unsupported hover contents; no item/NBT serialization"); }
        }
        List<Component> siblings = component.getSiblings();
        if (siblings.size() > 32) { throw new IllegalStateException("chat sibling preflight cap"); }
        for (Component sibling : siblings) { chatPreflight(sibling, depth + 1, budget); }
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void ordinaryNestedNativeReceiversRemainSerializable(GameTestHelper helper) {
        var trace = new SleepBoundaryTrace(List.of("ordinary-nested-receivers"));
        UUID id = UUID.fromString("5c499464-a93c-4a09-af52-86de593d18a6");
        try {
            for (ClickEvent.Action action : ClickEvent.Action.values()) {
                Component component = Component.literal("click").withStyle(style ->
                        style.withClickEvent(new ClickEvent(action, "native-value")));
                JsonObject click = trace.boundedChat(component).getAsJsonObject().getAsJsonObject("clickEvent");
                helper.assertTrue(click.get("action").getAsString().equals(action.getName())
                        && click.get("value").getAsString().equals("native-value"), "Ordinary native click serialization differs");
            }
            for (Component name : new Component[] {null, Component.literal("name").withStyle(style ->
                    style.withClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, "nested-value")))}) {
                Component component = Component.literal("entity").withStyle(style -> style.withHoverEvent(new HoverEvent(
                        HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(EntityType.PIG, id, name))));
                JsonObject entity = trace.boundedChat(component).getAsJsonObject().getAsJsonObject("hoverEvent").getAsJsonObject("contents");
                helper.assertTrue(entity.get("type").getAsString().equals("minecraft:pig")
                        && entity.get("id").getAsString().equals(id.toString()) && entity.has("name") == (name != null),
                        "Ordinary native hover entity serialization differs");
                if (name != null) {
                    helper.assertTrue(entity.getAsJsonObject("name").getAsJsonObject("clickEvent").get("value").getAsString()
                            .equals("nested-value"), "Ordinary nested native click was not retained");
                }
            }
        } finally { trace.clear(); }
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void unsupportedClickReceiversAreRefusedBeforeGetters(GameTestHelper helper) {
        var click = new RecordingClickEvent();
        Component component = Component.literal("click").withStyle(style -> style.withClickEvent(click));
        assertRefusedInComponentRoutes(helper, component, "unsupported click receiver");
        helper.assertTrue(click.actionCalls == 0 && click.valueCalls == 0, "Unsupported click getter behavior ran");
        helper.succeed();
    }

    @GameTest(templateNamespace = "advancedrocketrycommunity", template = "empty", batch = "sleep_trace", timeoutTicks = 20)
    public static void unsupportedEntityTooltipReceiversAreRefusedBeforeSerialization(GameTestHelper helper) {
        var entity = new RecordingEntityTooltipInfo();
        Component component = Component.literal("entity").withStyle(style ->
                style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ENTITY, entity)));
        assertRefusedInComponentRoutes(helper, component, "unsupported hover entity receiver");
        helper.assertTrue(entity.serializeCalls == 0 && entity.tooltipCalls == 0, "Unsupported entity tooltip behavior ran");
        helper.succeed();
    }

    private static void assertRefusedInComponentRoutes(GameTestHelper helper, Component nested, String reason) {
        UUID id = UUID.fromString("5c499464-a93c-4a09-af52-86de593d18a6");
        var trace = new SleepBoundaryTrace(List.of("unsupported-nested-receivers"));
        try {
            for (Component root : List.of(nested, Component.literal("sibling").append(nested),
                    Component.translatable("argument", nested), Component.literal("hover-text").withStyle(style ->
                            style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, nested))),
                    Component.literal("hover-name").withStyle(style -> style.withHoverEvent(new HoverEvent(
                            HoverEvent.Action.SHOW_ENTITY, new HoverEvent.EntityTooltipInfo(EntityType.PIG, id, nested)))))) {
                boolean refused = false;
                try { trace.boundedChat(root); }
                catch (IllegalStateException failure) { refused = reason.equals(failure.getMessage()); }
                helper.assertTrue(refused, "Unsupported nested receiver was not refused by its admission boundary");
            }
        } finally { trace.clear(); }
    }

    private static final class RecordingClickEvent extends ClickEvent {
        int actionCalls, valueCalls;
        RecordingClickEvent() { super(Action.COPY_TO_CLIPBOARD, "native-value"); }
        @Override public Action getAction() { actionCalls++; throw new AssertionError("Unsupported click action getter invoked"); }
        @Override public String getValue() { valueCalls++; throw new AssertionError("Unsupported click value getter invoked"); }
    }

    private static final class RecordingEntityTooltipInfo extends HoverEvent.EntityTooltipInfo {
        int serializeCalls, tooltipCalls;
        RecordingEntityTooltipInfo() { super(EntityType.PIG, UUID.fromString("5c499464-a93c-4a09-af52-86de593d18a6"), null); }
        @Override public JsonElement serialize() { serializeCalls++; throw new AssertionError("Unsupported entity serializer invoked"); }
        @Override public List<Component> getTooltipLines() { tooltipCalls++; throw new AssertionError("Unsupported tooltip getter invoked"); }
    }
}
