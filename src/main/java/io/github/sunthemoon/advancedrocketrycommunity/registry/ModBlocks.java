package io.github.sunthemoon.advancedrocketrycommunity.registry;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlock;
import io.github.sunthemoon.advancedrocketrycommunity.content.MachineCasingBlock;
import io.github.sunthemoon.advancedrocketrycommunity.machine.electrolyzer.ElectrolyzerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS,
            AdvancedRocketryCommunity.MOD_ID
    );

    public static final RegistryObject<Block> MACHINE_CASING = BLOCKS.register(
            "machine_casing",
            () -> new MachineCasingBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> ELECTROLYZER = BLOCKS.register(
            "electrolyzer",
            () -> new ElectrolyzerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(5.0F, 6.0F)
                    .lightLevel(state -> state.getValue(ElectrolyzerBlock.LIT) ? 8 : 0)
                    .sound(SoundType.METAL))
    );
    public static final RegistryObject<Block> OXYGEN_VENT = BLOCKS.register(
            "oxygen_vent",
            () -> new OxygenVentBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 6.0F)
                    .lightLevel(state -> state.getValue(OxygenVentBlock.LIT) ? 8 : 0)
                    .sound(SoundType.METAL))
    );

    private ModBlocks() {
    }

    public static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
    }
}
