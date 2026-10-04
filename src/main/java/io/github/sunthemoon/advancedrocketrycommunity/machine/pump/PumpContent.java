package io.github.sunthemoon.advancedrocketrycommunity.machine.pump;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Root integration installs these three registries once, with its COMMON enabled supplier. */
public final class PumpContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModIdentity.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModIdentity.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModIdentity.MOD_ID);
    private static BooleanSupplier enabled = () -> false;
    public static final RegistryObject<PumpBlock> BLOCK = BLOCKS.register("pump", () -> new PumpBlock(
            BlockBehaviour.Properties.of().mapColor(MapColor.METAL).requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F).sound(SoundType.METAL)));
    public static final RegistryObject<Item> ITEM = ITEMS.register("pump", () -> new PumpBlockItem(BLOCK.get(), new Item.Properties().stacksTo(1)));
    public static final RegistryObject<BlockEntityType<PumpBlockEntity>> ENTITY = ENTITIES.register("pump",
            () -> BlockEntityType.Builder.of(PumpBlockEntity::new, BLOCK.get()).build(null));
    public static void register(IEventBus bus, BooleanSupplier configuredEnabled) {
        enabled = Objects.requireNonNull(configuredEnabled);
        BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus);
    }
    public static boolean enabled() { return enabled.getAsBoolean(); }
    private PumpContent() { }
}
