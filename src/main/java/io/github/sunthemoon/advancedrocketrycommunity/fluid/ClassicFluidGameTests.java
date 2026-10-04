package io.github.sunthemoon.advancedrocketrycommunity.fluid;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content.SpaceSuitOxygen;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.vent.OxygenVentBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.compat.atmosphere.SuitEquipmentService;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.IFluidHandlerItem;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.ItemStackHandler;

@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ClassicFluidGameTests {
    private ClassicFluidGameTests() { }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void everyGasCanisterSimulatesAndSwapsExactlyOneUnit(GameTestHelper helper) {
        GasCanisterCatalog catalog = GasCanisterCatalog.registered();
        for (var entry : catalog.gases()) {
            ItemStack empty = new ItemStack(catalog.empty());
            var handler = capability(empty);
            FluidStack gas = new FluidStack(entry.fluid(), 1_000);
            helper.assertTrue(handler.fill(gas, FluidAction.SIMULATE) == 1_000
                    && handler.getContainer().is(catalog.empty()), "Fill simulation mutated empty canister");
            helper.assertTrue(handler.fill(gas, FluidAction.EXECUTE) == 1_000
                    && handler.getContainer().is(entry.item()), "Gas did not swap to its exact canister ID");
            var filled = capability(handler.getContainer());
            helper.assertTrue(filled.drain(1_000, FluidAction.SIMULATE).getAmount() == 1_000
                    && filled.getContainer().is(entry.item()), "Drain simulation changed filled canister");
            helper.assertTrue(filled.drain(1_000, FluidAction.EXECUTE).getAmount() == 1_000
                    && filled.getContainer().is(catalog.empty()), "Drain did not return an empty canister");
            helper.assertTrue(empty.getCount() == 1 && empty.is(catalog.empty()),
                    "Bucket-style result mutated the caller's original stack");
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void sixteenUnitCapabilitiesRejectSimulationAndExecution(GameTestHelper helper) {
        GasCanisterCatalog catalog = GasCanisterCatalog.registered();
        for (var entry : catalog.gases()) {
            for (FluidAction action : FluidAction.values()) {
                ItemStack empty = new ItemStack(catalog.empty(), 16);
                ItemStack full = new ItemStack(entry.item(), 16);
                helper.assertTrue(capability(empty).fill(new FluidStack(entry.fluid(), 1_000), action) == 0,
                        "Stacked direct fill was allowed");
                helper.assertTrue(capability(full).drain(1_000, action).isEmpty(), "Stacked direct drain was allowed");
                helper.assertTrue(empty.getCount() == 16 && full.getCount() == 16,
                        "Stacked canister count changed");
                helper.assertTrue(empty.getMaxStackSize() == 16 && full.getMaxStackSize() == 16,
                        "Legacy stack limit changed");
            }
        }
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void partialAndTaggedFluidTransfersRefuseWithoutLoss(GameTestHelper helper) {
        var empty = capability(new ItemStack(ModItems.EMPTY_CANISTER.get()));
        var oxygen = capability(new ItemStack(ModItems.OXYGEN_CANISTER.get()));
        for (FluidAction action : FluidAction.values()) {
            helper.assertTrue(empty.fill(new FluidStack(ClassicFluids.OXYGEN.get(), 999), action) == 0,
                    "Partial canister fill succeeded");
            helper.assertTrue(oxygen.drain(999, action).isEmpty(), "Partial canister drain succeeded");
            FluidStack tagged = new FluidStack(ClassicFluids.OXYGEN.get(), 1_000);
            tagged.setTag(new CompoundTag());
            tagged.getTag().putInt("marker", 42);
            helper.assertTrue(empty.fill(tagged, action) == 0 && oxygen.drain(tagged, action).isEmpty(),
                    "Item ID cannot preserve a tagged fluid payload");
        }
        helper.assertTrue(empty.getContainer().is(ModItems.EMPTY_CANISTER.get())
                && oxygen.getContainer().is(ModItems.OXYGEN_CANISTER.get()), "Refusal changed a canister ID");
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void forgeStackedInteractionStowsOneAndRejectsAFullInventory(GameTestHelper helper) {
        ItemStack held = new ItemStack(ModItems.EMPTY_CANISTER.get(), 16);
        FluidTank tank = new FluidTank(1_000);
        tank.fill(new FluidStack(ClassicFluids.NITROGEN.get(), 1_000), FluidAction.EXECUTE);
        ItemStackHandler inventory = new ItemStackHandler(2);
        inventory.setStackInSlot(0, new ItemStack(Items.STONE, 64));
        inventory.setStackInSlot(1, new ItemStack(Items.STONE, 64));
        helper.assertTrue(!FluidUtil.tryFillContainerAndStow(held, tank, inventory, 1_000, null, false).isSuccess()
                && !FluidUtil.tryFillContainerAndStow(held, tank, inventory, 1_000, null, true).isSuccess(),
                "Full destination inventory accepted a stacked swap");
        helper.assertTrue(held.getCount() == 16 && tank.getFluidAmount() == 1_000,
                "Full-inventory refusal consumed a canister or gas");
        inventory.setStackInSlot(1, ItemStack.EMPTY);
        var simulated = FluidUtil.tryFillContainerAndStow(held, tank, inventory, 1_000, null, false);
        helper.assertTrue(simulated.isSuccess() && simulated.getResult().getCount() == 15
                && tank.getFluidAmount() == 1_000 && inventory.getStackInSlot(1).isEmpty(),
                "Whole-stack simulation changed resources");
        var filled = FluidUtil.tryFillContainerAndStow(held, tank, inventory, 1_000, null, true);
        helper.assertTrue(filled.isSuccess() && filled.getResult().getCount() == 15
                && inventory.getStackInSlot(1).is(ClassicFluids.NITROGEN_CANISTER.get())
                && inventory.getStackInSlot(1).getCount() == 1 && tank.getFluidAmount() == 0,
                "Forge stowing did not exchange exactly one unit");
        helper.assertTrue(held.getCount() == 16, "Forge interaction mutated its input rather than returning the remainder");
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void canisterMetadataAndMeaningSurviveNativeItemRoundTrip(GameTestHelper helper) {
        ItemStack empty = new ItemStack(ModItems.EMPTY_CANISTER.get());
        empty.getOrCreateTag().putString("marker", "retained");
        var handler = capability(empty);
        helper.assertTrue(handler.fill(new FluidStack(ClassicFluids.HYDROGEN.get(), 1_000), FluidAction.EXECUTE) == 1_000,
                "Hydrogen fill failed");
        ItemStack decoded = ItemStack.of(handler.getContainer().save(new CompoundTag()));
        helper.assertTrue(decoded.is(ModItems.HYDROGEN_CANISTER.get())
                && "retained".equals(decoded.getTag().getString("marker"))
                && capability(decoded).getFluidInTank(0).getFluid() == ClassicFluids.HYDROGEN.get(),
                "Stateless canister ID or metadata changed on native item save/reload");
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void oxygenCanistersStillRefillTheSuitAndVent(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "CanisterSuitTest"));
        ItemStack suit = new ItemStack(ModItems.SPACE_SUIT_CHESTPLATE.get());
        player.setItemSlot(EquipmentSlot.CHEST, suit);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.OXYGEN_CANISTER.get(), 16));
        SuitEquipmentService service = new SuitEquipmentService(SuitEquipmentCatalog.empty());
        helper.assertTrue(service.fillOneCanister(player, InteractionHand.MAIN_HAND).accepted()
                && SpaceSuitOxygen.read(suit).oxygenUnits() == 1_000
                && player.getItemInHand(InteractionHand.MAIN_HAND).getCount() == 15,
                "The existing ID-based suit refill changed");
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ModBlocks.OXYGEN_VENT.get());
        var vent = (OxygenVentBlockEntity) helper.getBlockEntity(relative);
        var items = vent.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(IllegalStateException::new);
        helper.assertTrue(items.insertItem(0, new ItemStack(ModItems.OXYGEN_CANISTER.get(), 16), false).isEmpty(),
                "Vent no longer accepts a sixteen-unit oxygen stack");
        OxygenVentBlockEntity.serverTick(helper.getLevel(), vent.getBlockPos(), vent.getBlockState(), vent);
        helper.assertTrue(vent.oxygenUnits() == 1_000 && items.getStackInSlot(0).getCount() == 15
                && items.getStackInSlot(1).is(ModItems.EMPTY_CANISTER.get())
                && items.getStackInSlot(1).getCount() == 1, "Vent changed existing whole-canister consumption");
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void gasesCannotPlaceAndLiquidsKeepWorldSourceAndBucketIdentities(GameTestHelper helper) {
        for (var gas : GasCanisterCatalog.registered().gases()) {
            helper.assertTrue(gas.fluid().getBucket() == Items.AIR
                    && gas.fluid().defaultFluidState().createLegacyBlock().isAir()
                    && !gas.fluid().getFluidType().canBePlacedInLevel(helper.getLevel(), helper.absolutePos(BlockPos.ZERO),
                            new FluidStack(gas.fluid(), 1_000)), "Container gas acquired a placeable world form");
        }
        helper.setBlock(new BlockPos(1, 1, 1), ClassicFluids.ROCKET_FUEL_BLOCK.get());
        helper.setBlock(new BlockPos(3, 1, 1), ClassicFluids.ENRICHED_LAVA_BLOCK.get());
        helper.assertTrue(helper.getBlockState(new BlockPos(1, 1, 1)).getFluidState().getType() == ClassicFluids.ROCKET_FUEL.get()
                && helper.getBlockState(new BlockPos(3, 1, 1)).getFluidState().getType() == ClassicFluids.ENRICHED_LAVA.get(),
                "Liquid world blocks do not expose their exact source identities");
        helper.assertTrue(ClassicFluids.ROCKET_FUEL.get().isSame(ClassicFluids.FLOWING_ROCKET_FUEL.get())
                && ClassicFluids.ENRICHED_LAVA.get().isSame(ClassicFluids.FLOWING_ENRICHED_LAVA.get()),
                "Liquid source/flowing pair mismatch");
        helper.assertTrue(ClassicFluids.ROCKET_FUEL.get().getBucket() == ClassicFluids.ROCKET_FUEL_BUCKET.get()
                && ClassicFluids.ENRICHED_LAVA.get().getBucket() == ClassicFluids.ENRICHED_LAVA_BUCKET.get()
                && !ClassicFluids.ROCKET_FUEL.get().isSame(Fluids.WATER), "Liquid bucket or fluid identity mismatch");
        helper.succeed();
    }

    @GameTest(template = "atmosphere_test", timeoutTicks = 20)
    public static void enrichedLavaAppliesTheVanillaEntityHeatAction(GameTestHelper helper) {
        BlockPos relative = new BlockPos(2, 1, 2);
        helper.setBlock(relative, ClassicFluids.ENRICHED_LAVA_BLOCK.get());
        BlockPos absolute = helper.absolutePos(relative);
        ArmorStand entity = new ArmorStand(helper.getLevel(), absolute.getX() + 0.5D,
                absolute.getY(), absolute.getZ() + 0.5D);
        ClassicFluids.ENRICHED_LAVA_BLOCK.get().entityInside(helper.getBlockState(relative),
                helper.getLevel(), absolute, entity);
        helper.assertTrue(entity.isOnFire(), "Enriched lava did not ignite a non-fire-immune entity");
        helper.succeed();
    }

    private static IFluidHandlerItem capability(ItemStack stack) {
        return stack.getCapability(ForgeCapabilities.FLUID_HANDLER_ITEM).orElseThrow(IllegalStateException::new);
    }
}
