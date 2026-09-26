package io.github.sunthemoon.arceadaptertest;

import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketAdaptersEvent;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Dedicated GameTest fixture; this mod is not part of the distributed host JAR. */
@Mod(AdapterTestMod.MOD_ID)
public final class AdapterTestMod {
    public static final String MOD_ID = "arce_adapter_test";
    static final ResourceLocation CONTAINER_ID = id("cargo_container");
    static final ResourceLocation ADAPTER_ID = id("cargo_inventory");
    static final int PAYLOAD_VERSION = 1;

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

    static final RegistryObject<Block> CONTAINER =
            BLOCKS.register(CONTAINER_ID.getPath(), FixtureContainerBlock::new);
    static final RegistryObject<BlockEntityType<FixtureContainerBlockEntity>> CONTAINER_TYPE =
            BLOCK_ENTITIES.register(CONTAINER_ID.getPath(), () -> BlockEntityType.Builder.of(
                    FixtureContainerBlockEntity::new, CONTAINER.get()).build(null));

    private static volatile int registrationEvents;
    private static volatile RegisterRocketAdaptersEvent receivedEvent;

    public AdapterTestMod(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::registerRocketAdapters);
    }

    private void registerRocketAdapters(RegisterRocketAdaptersEvent event) {
        event.register(ADAPTER_ID, Set.of(CONTAINER_ID), PAYLOAD_VERSION, new FixtureInventoryAdapter());
        registrationEvents++;
        // Intentionally retained only to exercise the host's closed registration window.
        receivedEvent = event;
    }

    static int registrationEvents() {
        return registrationEvents;
    }

    static RegisterRocketAdaptersEvent receivedEvent() {
        return receivedEvent;
    }

    static ResourceLocation id(String path) {
        return new ResourceLocation(MOD_ID, path);
    }
}
