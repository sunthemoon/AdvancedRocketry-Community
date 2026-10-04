package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fluids.FluidType;
import net.minecraftforge.fluids.ForgeFlowingFluid;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Small registration module; the central bootstrap explicitly installs it once. */
public final class ClassicFluids {
    private static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(
            ForgeRegistries.Keys.FLUID_TYPES, ModIdentity.MOD_ID);
    private static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(
            ForgeRegistries.FLUIDS, ModIdentity.MOD_ID);
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(
            ForgeRegistries.BLOCKS, ModIdentity.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(
            ForgeRegistries.ITEMS, ModIdentity.MOD_ID);

    public static final RegistryObject<FluidType> OXYGEN_TYPE = type(ClassicFluidDefinition.OXYGEN);
    public static final RegistryObject<FluidType> HYDROGEN_TYPE = type(ClassicFluidDefinition.HYDROGEN);
    public static final RegistryObject<FluidType> NITROGEN_TYPE = type(ClassicFluidDefinition.NITROGEN);
    public static final RegistryObject<FluidType> ROCKET_FUEL_TYPE = type(ClassicFluidDefinition.ROCKET_FUEL);
    public static final RegistryObject<FluidType> ENRICHED_LAVA_TYPE = type(ClassicFluidDefinition.ENRICHED_LAVA);

    public static final RegistryObject<Fluid> OXYGEN = gas("oxygen", OXYGEN_TYPE);
    public static final RegistryObject<Fluid> HYDROGEN = gas("hydrogen", HYDROGEN_TYPE);
    public static final RegistryObject<Fluid> NITROGEN = gas("nitrogen", NITROGEN_TYPE);
    public static final RegistryObject<FlowingFluid> ROCKET_FUEL = FLUIDS.register(
            "rocket_fuel", () -> new ForgeFlowingFluid.Source(rocketFuelProperties()));
    public static final RegistryObject<FlowingFluid> FLOWING_ROCKET_FUEL = FLUIDS.register(
            "flowing_rocket_fuel", () -> new ForgeFlowingFluid.Flowing(rocketFuelProperties()));
    public static final RegistryObject<FlowingFluid> ENRICHED_LAVA = FLUIDS.register(
            "enriched_lava", () -> new ForgeFlowingFluid.Source(enrichedLavaProperties()));
    public static final RegistryObject<FlowingFluid> FLOWING_ENRICHED_LAVA = FLUIDS.register(
            "flowing_enriched_lava", () -> new ForgeFlowingFluid.Flowing(enrichedLavaProperties()));

    public static final RegistryObject<LiquidBlock> ROCKET_FUEL_BLOCK = BLOCKS.register(
            "rocket_fuel", () -> new LiquidBlock(ROCKET_FUEL, liquid(MapColor.COLOR_YELLOW, 2)));
    public static final RegistryObject<LiquidBlock> ENRICHED_LAVA_BLOCK = BLOCKS.register(
            "enriched_lava", () -> new EnrichedLavaBlock(ENRICHED_LAVA, liquid(MapColor.FIRE, 15)));
    public static final RegistryObject<Item> ROCKET_FUEL_BUCKET = ITEMS.register(
            "rocket_fuel_bucket", () -> new BucketItem(ROCKET_FUEL, bucketProperties()));
    public static final RegistryObject<Item> ENRICHED_LAVA_BUCKET = ITEMS.register(
            "enriched_lava_bucket", () -> new BucketItem(ENRICHED_LAVA, bucketProperties()));
    public static final RegistryObject<Item> NITROGEN_CANISTER = ITEMS.register(
            "nitrogen_canister", () -> new GasCanisterItem(new Item.Properties().stacksTo(16)));

    private ClassicFluids() { }

    public static void register(IEventBus bus) {
        TYPES.register(bus);
        FLUIDS.register(bus);
        BLOCKS.register(bus);
        ITEMS.register(bus);
    }

    private static RegistryObject<FluidType> type(ClassicFluidDefinition definition) {
        return TYPES.register(definition.id(), () -> new ClassicFluidType(definition));
    }

    private static RegistryObject<Fluid> gas(String id, RegistryObject<FluidType> type) {
        return FLUIDS.register(id, () -> new ContainerGasFluid(type));
    }

    private static BlockBehaviour.Properties liquid(MapColor color, int light) {
        return BlockBehaviour.Properties.of().mapColor(color).replaceable().noCollission()
                .strength(100.0F).noLootTable().liquid().lightLevel(state -> light);
    }

    private static Item.Properties bucketProperties() {
        return new Item.Properties().craftRemainder(Items.BUCKET).stacksTo(1);
    }

    private static ForgeFlowingFluid.Properties rocketFuelProperties() {
        return new ForgeFlowingFluid.Properties(ROCKET_FUEL_TYPE, ROCKET_FUEL, FLOWING_ROCKET_FUEL)
                .block(ROCKET_FUEL_BLOCK).bucket(ROCKET_FUEL_BUCKET).tickRate(5);
    }

    private static ForgeFlowingFluid.Properties enrichedLavaProperties() {
        return new ForgeFlowingFluid.Properties(ENRICHED_LAVA_TYPE, ENRICHED_LAVA, FLOWING_ENRICHED_LAVA)
                .block(ENRICHED_LAVA_BLOCK).bucket(ENRICHED_LAVA_BUCKET).tickRate(30)
                .slopeFindDistance(2).levelDecreasePerBlock(2).explosionResistance(100.0F);
    }
}
