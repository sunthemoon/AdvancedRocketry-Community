package io.github.sunthemoon.arceadaptertest;

import com.mojang.logging.LogUtils;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketAdaptersEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketComponentsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RegisterRocketFuelsEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterAtmosphereBoundariesEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.AtmosphereBoundary;
import io.github.sunthemoon.advancedrocketrycommunity.api.atmosphere.RegisterSuitEquipmentEvent;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiCompatibility;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersion;
import io.github.sunthemoon.advancedrocketrycommunity.api.version.ApiVersions;
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

/** Dedicated API compatibility fixture; never bundled in the distributed host JAR. */
@Mod(AdapterTestMod.MOD_ID)
public final class AdapterTestMod {
    public static final String MOD_ID = "arce_adapter_test";
    static final ResourceLocation CONTAINER_ID = id("cargo_container");
    static final ResourceLocation ADAPTER_ID = id("cargo_inventory");
    static final ResourceLocation BOUNDARY_ID = id("state_boundary");
    static final int PAYLOAD_VERSION = 1;

    private static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, MOD_ID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MOD_ID);

    static final RegistryObject<Block> CONTAINER =
            BLOCKS.register(CONTAINER_ID.getPath(), FixtureContainerBlock::new);
    static final RegistryObject<Block> BOUNDARY =
            BLOCKS.register(BOUNDARY_ID.getPath(), FixtureBoundaryBlock::new);
    static final RegistryObject<BlockEntityType<FixtureContainerBlockEntity>> CONTAINER_TYPE =
            BLOCK_ENTITIES.register(CONTAINER_ID.getPath(), () -> BlockEntityType.Builder.of(
                    FixtureContainerBlockEntity::new, CONTAINER.get()).build(null));

    private static volatile int registrationEvents;
    private static volatile RegisterRocketAdaptersEvent receivedEvent;
    static volatile int boundaryEvents;
    static volatile RegisterAtmosphereBoundariesEvent boundaryEvent;
    static volatile int suitEvents;
    static volatile RegisterSuitEquipmentEvent suitEvent;
    static volatile int componentEvents;
    static volatile RegisterRocketComponentsEvent componentEvent;
    static volatile int fuelEvents;
    static volatile RegisterRocketFuelsEvent fuelEvent;

    public AdapterTestMod(FMLJavaModLoadingContext context) {
        if (ApiVersions.check(ApiVersions.current(), new ApiVersion(1, 7)) != ApiCompatibility.COMPATIBLE) {
            throw new IllegalStateException("Adapter fixture requires ARCE API 1.7");
        }
        IEventBus modBus = context.getModEventBus();
        BLOCKS.register(modBus);
        BLOCK_ENTITIES.register(modBus);
        modBus.addListener(this::registerRocketAdapters);
        modBus.addListener(this::registerAtmosphereBoundaries);
        modBus.addListener(this::registerSuitEquipment);
        modBus.addListener(this::registerRocketComponents);
        modBus.addListener(this::registerRocketFuels);
        modBus.addListener(SatellitePayloadFixture::register);
        EnvironmentQueryFixture.install();
        if (Boolean.getBoolean("arce_adapter_test.satelliteSmoke")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SatellitePayloadFixture::commands);
        }
        if (Boolean.getBoolean("arce_adapter_test.suitSmoke")) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.addListener(SuitEquipmentFixture::registerCommands);
        }
    }

    private void registerRocketAdapters(RegisterRocketAdaptersEvent event) {
        registrationEvents++;
        // Intentionally retained only to exercise the host's closed registration window.
        receivedEvent = event;
        ApiVersion version = ApiVersions.current();
        // Startup-only fault control; the block, BlockEntity and tags remain installed.
        if (Boolean.getBoolean("arce_adapter_test.skipRocketAdapter")) {
            LogUtils.getLogger().info("Skipped rocket adapter {} (API {}.{}, event {})",
                    ADAPTER_ID, version.major(), version.minor(), registrationEvents);
            return;
        }
        event.register(ADAPTER_ID, Set.of(CONTAINER_ID), PAYLOAD_VERSION, new FixtureInventoryAdapter());
        LogUtils.getLogger().info("Registered rocket adapter {} (payload {}, API {}.{}, event {})",
                ADAPTER_ID, PAYLOAD_VERSION, version.major(), version.minor(), registrationEvents);
    }

    static int registrationEvents() {
        return registrationEvents;
    }

    private void registerAtmosphereBoundaries(RegisterAtmosphereBoundariesEvent event) {
        boundaryEvents++;
        boundaryEvent = event;
        if (Boolean.getBoolean("arce_adapter_test.skipAtmosphereBoundary")) {
            LogUtils.getLogger().info("Skipped atmosphere boundary {} (event {})", BOUNDARY_ID, boundaryEvents);
            return;
        }
        boolean failCompilation = Boolean.getBoolean("arce_adapter_test.failAtmosphereBoundary");
        event.register(BOUNDARY_ID, Set.of(BOUNDARY_ID), state -> {
            if (failCompilation) {
                throw new IllegalStateException("Fixture registration fault");
            }
            return state.getValue(FixtureBoundaryBlock.OPEN) ? AtmosphereBoundary.PERMEABLE : AtmosphereBoundary.SEALED;
        });
        LogUtils.getLogger().info("Registered atmosphere boundary {} (API {}.{}, event {})",
                BOUNDARY_ID, ApiVersions.current().major(), ApiVersions.current().minor(), boundaryEvents);
    }

    static RegisterRocketAdaptersEvent receivedEvent() {
        return receivedEvent;
    }

    private void registerSuitEquipment(RegisterSuitEquipmentEvent event) {
        suitEvents++;
        suitEvent = event;
        if (Boolean.getBoolean("arce_adapter_test.skipSuitEquipment")) {
            LogUtils.getLogger().info("Skipped suit equipment {} (event {})", FixtureSuitOxygen.ID, suitEvents);
            return;
        }
        event.register(FixtureSuitOxygen.ID, FixtureSuitOxygen.ITEMS, 1, new FixtureSuitOxygen());
        LogUtils.getLogger().info("Registered suit equipment {} (API {}.{}, event {})",
                FixtureSuitOxygen.ID, ApiVersions.current().major(), ApiVersions.current().minor(), suitEvents);
    }

    static ResourceLocation id(String path) {
        return ResourceLocation.tryParse(MOD_ID + ":" + path);
    }

    private void registerRocketComponents(RegisterRocketComponentsEvent event) {
        componentEvents++;
        componentEvent = event;
        if (Boolean.getBoolean("arce_adapter_test.skipRocketComponents")) {
            LogUtils.getLogger().info("Skipped rocket components (event {})", componentEvents);
            return;
        }
        FixtureRocketComponents.register(event);
        LogUtils.getLogger().info("Registered rocket components (variant {}, API {}.{}, event {})",
                FixtureRocketComponents.variant(), ApiVersions.current().major(), ApiVersions.current().minor(), componentEvents);
    }

    private void registerRocketFuels(RegisterRocketFuelsEvent event) {
        fuelEvents++;
        fuelEvent = event;
        if (Boolean.getBoolean("arce_adapter_test.skipRocketFuels")) {
            LogUtils.getLogger().info("Skipped rocket fuels (event {})", fuelEvents);
            return;
        }
        FixtureRocketFuels.register(event);
        LogUtils.getLogger().info("Registered rocket fuels (variant {}, API {}.{}, event {})",
                FixtureRocketFuels.variant(), ApiVersions.current().major(), ApiVersions.current().minor(), fuelEvents);
    }
}
