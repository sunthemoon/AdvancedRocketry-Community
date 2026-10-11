package io.github.sunthemoon.advancedrocketrycommunity.equipment.suit;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationBlock;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.equipment.workstation.SuitWorkstationMenu;
import io.github.sunthemoon.advancedrocketrycommunity.model.api.equipment.ModuleKind;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SuitEquipmentContent {
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ModIdentity.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ModIdentity.MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ModIdentity.MOD_ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, ModIdentity.MOD_ID);
    private static final DeferredRegister<Enchantment> ENCHANTMENTS = DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ModIdentity.MOD_ID);
    public static final RegistryObject<SuitWorkstationBlock> BLOCK = BLOCKS.register("suit_workstation", () ->
            new SuitWorkstationBlock(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3.5F)
                    .sound(SoundType.METAL).pushReaction(PushReaction.BLOCK)));
    public static final RegistryObject<Item> WORKSTATION = ITEMS.register("suit_workstation", () -> new BlockItem(BLOCK.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<SuitWorkstationBlockEntity>> ENTITY = ENTITIES.register("suit_workstation", () ->
            BlockEntityType.Builder.of(SuitWorkstationBlockEntity::new, BLOCK.get()).build(null));
    public static final RegistryObject<MenuType<SuitWorkstationMenu>> MENU = MENUS.register("suit_workstation", () -> IForgeMenuType.create(SuitWorkstationMenu::new));
    public static final RegistryObject<Enchantment> SPACE_BREATHING = ENCHANTMENTS.register("space_breathing", SpaceBreathingEnchantment::new);
    private static final Map<String, RegistryObject<Item>> MODULES = createModules();
    private static Map<String, RegistryObject<Item>> createModules() {
        Map<String, RegistryObject<Item>> modules = new LinkedHashMap<>();
        for (ModuleKind kind : ModuleKind.values()) {
            if (kind == ModuleKind.BEACON_FINDER) { continue; }
            for (String id : kind.itemPaths()) {
                modules.put(id, ITEMS.register(id, () -> new SuitModuleItem(TankData.baseCapacity(ModIdentity.MOD_ID + ":" + id))));
            }
        }
        return Map.copyOf(modules);
    }
    public static Map<String, RegistryObject<Item>> modules() { return MODULES; }
    public static void register(IEventBus bus) { BLOCKS.register(bus); ITEMS.register(bus); ENTITIES.register(bus); MENUS.register(bus); ENCHANTMENTS.register(bus); }
    private SuitEquipmentContent() { }
}
