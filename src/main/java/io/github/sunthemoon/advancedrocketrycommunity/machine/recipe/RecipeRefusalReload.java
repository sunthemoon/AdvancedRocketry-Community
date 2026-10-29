package io.github.sunthemoon.advancedrocketrycommunity.machine.recipe;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * W193: opens a {@link RecipeRefusalLedger} for each server data reload and logs its one summary. Forge appends the
 * listeners of this event after vanilla's, and a reload applies its listeners in order, so the flush applies after
 * RecipeManager has decoded every recipe of the reload.
 */
@Mod.EventBusSubscriber(modid = AdvancedRocketryCommunity.MOD_ID)
public final class RecipeRefusalReload {
    private RecipeRefusalReload() {
    }

    @SubscribeEvent
    public static void reload(AddReloadListenerEvent event) {
        RecipeRefusalLedger.open(event.getConditionContext());
        event.addListener(new Flush(event.getConditionContext()));
    }

    private static final class Flush extends SimplePreparableReloadListener<Void> {
        private final ICondition.IContext context;

        Flush(ICondition.IContext context) {
            this.context = context;
        }

        @Override
        protected Void prepare(ResourceManager manager, ProfilerFiller profiler) {
            return null;
        }

        @Override
        protected void apply(Void nothing, ResourceManager manager, ProfilerFiller profiler) {
            RecipeRefusalLedger.flush(context, AdvancedRocketryCommunity.LOGGER);
        }
    }
}
