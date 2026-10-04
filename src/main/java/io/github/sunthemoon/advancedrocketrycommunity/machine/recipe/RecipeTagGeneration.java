package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** An invalidation epoch only: no world, recipe catalogue or server references are retained. */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RecipeTagGeneration {
    private static final AtomicLong GENERATION = new AtomicLong();

    private RecipeTagGeneration() { }

    static long current() {
        return GENERATION.get();
    }

    @SubscribeEvent
    public static void tagsUpdated(TagsUpdatedEvent event) {
        if (event.shouldUpdateStaticData()) {
            GENERATION.incrementAndGet();
        }
    }
}
