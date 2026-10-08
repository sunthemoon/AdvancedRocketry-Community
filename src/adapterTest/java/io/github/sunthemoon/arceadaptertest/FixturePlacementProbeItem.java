package io.github.sunthemoon.arceadaptertest;

import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Development-only native caller observation; it grants no installed-owner authority. */
public final class FixturePlacementProbeItem extends BlockItem {
    private static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, AdapterTestMod.MOD_ID);
    static final RegistryObject<Item> ITEM = ITEMS.register("placement_probe", FixturePlacementProbeItem::new);
    private static final StackWalker WALKER = StackWalker.getInstance(Set.of(
            StackWalker.Option.RETAIN_CLASS_REFERENCE, StackWalker.Option.SHOW_REFLECT_FRAMES,
            StackWalker.Option.SHOW_HIDDEN_FRAMES));

    private FixturePlacementProbeItem() { super(Blocks.CHEST, new Item.Properties()); }
    static void register(IEventBus bus) { ITEMS.register(bus); }

    // The probe has its own Item registry identity, never the native chest's block-to-item entry.
    @Override public void registerBlocks(Map<Block, Item> map, Item item) { }
    @Override public void removeFromBlockToItemMap(Map<Block, Item> map, Item item) { }

    @Override public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack, UseOnContext context) {
        observe("FIRST", context);
        if (stack.hasTag() && stack.getTag().getBoolean("DirectFirst")) {
            InteractionResult direct = stack.useOn(context);
            MinecraftForge.EVENT_BUS.post(new DirectResult(context, direct));
        }
        return InteractionResult.PASS;
    }

    @Override public InteractionResult useOn(UseOnContext context) {
        observe("USE", context);
        return super.useOn(context);
    }

    @Override protected boolean placeBlock(BlockPlaceContext context, BlockState state) {
        observe("PLACE", context);
        return super.placeBlock(context, state);
    }

    private static void observe(String phase, UseOnContext context) {
        List<Caller> callers = WALKER.walk(frames -> frames.limit(16)
                .map(frame -> new Caller(frame.getDeclaringClass(), frame.getMethodName(),
                        frame.getDescriptor(), frame.getByteCodeIndex())).toList());
        MinecraftForge.EVENT_BUS.post(new Observation(phase, context, callers));
    }

    record Caller(Class<?> type, String method, String descriptor, int bci) { }
    public static final class Observation extends Event {
        final String phase;
        final UseOnContext context;
        final List<Caller> callers;
        Observation(String phase, UseOnContext context, List<Caller> callers) {
            this.phase = phase; this.context = context; this.callers = callers;
        }
    }
    public static final class DirectResult extends Event {
        final UseOnContext context;
        final InteractionResult result;
        DirectResult(UseOnContext context, InteractionResult result) {
            this.context = context; this.result = result;
        }
    }
}
