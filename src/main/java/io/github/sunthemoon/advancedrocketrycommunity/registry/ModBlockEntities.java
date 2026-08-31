package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(
            ForgeRegistries.BLOCK_ENTITY_TYPES,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<BlockEntityType<ElectrolyzerBlockEntity>> ELECTROLYZER =
            BLOCK_ENTITIES.register(
                    "electrolyzer",
                    () -> BlockEntityType.Builder.of(
                            ElectrolyzerBlockEntity::new,
                            ModBlocks.ELECTROLYZER.get()
                    ).build(null)
            );

    private ModBlockEntities() {
    }

    public static void register(IEventBus modBus) {
        BLOCK_ENTITIES.register(modBus);
    }
}
