package io.github.sunthemoon.advancedrocketrycommunity.progression.classic;

import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import java.util.Set;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SerializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.GsonHelper;

/** One server trigger for the classic events; conditions name exactly one {@link ClassicEvent}. */
public final class ClassicEventTrigger extends SimpleCriterionTrigger<ClassicEventTrigger.Instance> {
    public static final ResourceLocation ID = ModIdentity.id("classic_event");
    public static final String EVENT = "event";
    private static final Set<String> CONDITION_KEYS = Set.of(EVENT, "player");

    @Override
    public ResourceLocation getId() {
        return ID;
    }

    @Override
    protected Instance createInstance(JsonObject json, ContextAwarePredicate player, DeserializationContext context) {
        for (String key : json.keySet()) {
            if (!CONDITION_KEYS.contains(key)) {
                throw new JsonSyntaxException("Unknown classic event condition " + key);
            }
        }
        String name = GsonHelper.getAsString(json, EVENT);
        ClassicEvent event = ClassicEvent.byName(name)
                .orElseThrow(() -> new JsonSyntaxException("Unknown classic event " + name));
        return new Instance(player, event);
    }

    /** Server thread only; the caller has already established the real completion point. */
    public void trigger(ServerPlayer player, ClassicEvent event) {
        Objects.requireNonNull(event, "event");
        trigger(player, instance -> instance.event() == event);
    }

    public static final class Instance extends AbstractCriterionTriggerInstance {
        private final ClassicEvent event;

        public Instance(ContextAwarePredicate player, ClassicEvent event) {
            super(ID, player);
            this.event = Objects.requireNonNull(event, "event");
        }

        public ClassicEvent event() {
            return event;
        }

        @Override
        public JsonObject serializeToJson(SerializationContext context) {
            JsonObject json = super.serializeToJson(context);
            json.addProperty(EVENT, event.serializedName());
            return json;
        }
    }
}
