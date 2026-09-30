package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;

/** ADR-049 section 5 (revision 4): the Satellite Builder's refusal order, assembly, automation and root carry. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteBuilderGameTests {
    private static final String KEY = "SatelliteBuilder";

    private SatelliteBuilderGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 80)
    public static void builderRefusesInOrderThenAssemblesASurveySatellite(GameTestHelper helper) {
        SatelliteBuilderBlockEntity builder = place(helper);
        FakePlayer owner = player(helper, builder);
        IItemHandler slots = builder.menuInventory();

        expect(helper, builder, owner, SatelliteOperationCode.INVALID_COMPONENTS, "empty builder");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHASSIS, ModItems.SATELLITE_CHASSIS.get());
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_PRIMARY, ModItems.SURVEY_SCANNER_MODULE.get());
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST, ModItems.SATELLITE_SOLAR_MODULE.get());
        expect(helper, builder, owner, SatelliteOperationCode.INVALID_COMPONENTS, "missing blank chip");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHIP, ModItems.SATELLITE_CONTROL_CHIP.get());
        expect(helper, builder, owner, SatelliteOperationCode.REQUIREMENT_UNMET, "survey without data storage");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST + 1, ModItems.DATA_STORAGE_UNIT.get());
        // Battery 720 is below the survey definition's scan energy of 1,000.
        expect(helper, builder, owner, SatelliteOperationCode.REQUIREMENT_UNMET, "survey without a battery");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST + 2, ModItems.SATELLITE_BATTERY.get());
        expect(helper, builder, owner, SatelliteOperationCode.NO_POWER, "unpowered builder");
        helper.assertTrue(!slots.insertItem(SatelliteBuilderBlockEntity.SLOT_OUTPUT,
                new ItemStack(ModItems.SATELLITE_PACKAGE.get()), false).isEmpty(), "The output slot accepted an item");
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHARGE, Items.REDSTONE);

        helper.runAfterDelay(2, () -> {
            IEnergyStorage energy = builder.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
            helper.assertTrue(energy.getEnergyStored() == SatelliteBuilderBlockEntity.REDSTONE_ENERGY,
                    "Redstone did not charge the builder");
            helper.assertTrue(builder.assemble(owner), "A valid survey blueprint was refused");
            SatelliteIdentity identity = SatelliteItemData.read(slots.getStackInSlot(SatelliteBuilderBlockEntity.SLOT_CHIP))
                    .identity().orElseThrow();
            helper.assertTrue(identity.kind() == SatelliteKind.SURVEY
                            && identity.ownerId().equals(owner.getUUID())
                            && identity.definitionId().equals(ModIdentity.id("survey_satellite"))
                            && identity.components().equals(List.of(id(ModItems.SATELLITE_CHASSIS.get()),
                                    id(ModItems.SURVEY_SCANNER_MODULE.get()), id(ModItems.SATELLITE_SOLAR_MODULE.get()),
                                    id(ModItems.DATA_STORAGE_UNIT.get()), id(ModItems.SATELLITE_BATTERY.get()))),
                    "The chip identity does not describe the assembled blueprint");
            ItemStack output = slots.getStackInSlot(SatelliteBuilderBlockEntity.SLOT_OUTPUT);
            helper.assertTrue(output.is(ModItems.SATELLITE_PACKAGE.get())
                            && SatelliteItemData.read(output).identity().orElseThrow().equals(identity),
                    "The package does not carry the chip identity");
            for (int slot = SatelliteBuilderBlockEntity.SLOT_CHASSIS; slot <= SatelliteBuilderBlockEntity.SLOT_MODULE_LAST; slot++) {
                helper.assertTrue(slots.getStackInSlot(slot).isEmpty(), "Assembly left a component in slot " + slot);
            }
            helper.assertTrue(energy.getEnergyStored() == SatelliteBuilderBlockEntity.REDSTONE_ENERGY
                    - SatelliteBuilderBlockEntity.ASSEMBLY_ENERGY, "Assembly did not consume its energy exactly");
            expect(helper, builder, owner, SatelliteOperationCode.RATE_LIMITED, "second assembly in one second");

            IItemHandler automation = builder.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP)
                    .resolve().orElseThrow();
            helper.assertTrue(automation.extractItem(SatelliteBuilderBlockEntity.SLOT_CHIP, 1, false).isEmpty(),
                    "Automation extracted the bound chip");
            helper.assertTrue(automation.extractItem(SatelliteBuilderBlockEntity.SLOT_OUTPUT, 1, false)
                    .is(ModItems.SATELLITE_PACKAGE.get()), "Automation could not extract the package");
            helper.assertTrue(automation.insertItem(SatelliteBuilderBlockEntity.SLOT_CHASSIS,
                    new ItemStack(ModItems.SURVEY_SCANNER_MODULE.get()), false).getCount() == 1,
                    "Automation inserted a primary module into the chassis slot");
            helper.assertTrue(automation.insertItem(SatelliteBuilderBlockEntity.SLOT_CHARGE,
                    new ItemStack(Items.REDSTONE), false).isEmpty(), "Automation could not insert redstone");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void builderStatLimitAndResearchGatesComeBeforePower(GameTestHelper helper) {
        SatelliteBuilderBlockEntity builder = place(helper);
        FakePlayer owner = player(helper, builder);
        IItemHandler slots = builder.menuInventory();
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHASSIS, ModItems.SATELLITE_CHASSIS.get());
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_PRIMARY, ModItems.ASTEROID_DRILL_MODULE.get());
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_CHIP, ModItems.SATELLITE_CONTROL_CHIP.get());
        for (int slot = SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST; slot <= SatelliteBuilderBlockEntity.SLOT_MODULE_LAST; slot++) {
            insert(helper, slots, slot, ModItems.SATELLITE_CARGO_HOLD.get());
        }
        // Six cargo holds are 54 units, above the cap of 27; checked before the missing power module.
        expect(helper, builder, owner, SatelliteOperationCode.STAT_LIMIT, "cargo over its cap");

        for (int slot = SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST; slot <= SatelliteBuilderBlockEntity.SLOT_MODULE_LAST; slot++) {
            slots.extractItem(slot, 1, false);
        }
        slots.extractItem(SatelliteBuilderBlockEntity.SLOT_PRIMARY, 1, false);
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_PRIMARY, ModItems.SOLAR_TRANSMITTER_MODULE.get());
        insert(helper, slots, SatelliteBuilderBlockEntity.SLOT_MODULE_FIRST, ModItems.SATELLITE_SOLAR_MODULE.get());
        // The solar definition needs 120 lifetime research; a new player has none. Energy is also missing.
        expect(helper, builder, owner, SatelliteOperationCode.RESEARCH_LOCKED, "solar without lifetime research");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void builderFutureRootIsPreservedBlockedAndCarried(GameTestHelper helper) {
        SatelliteBuilderBlockEntity builder = place(helper);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "preserve-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put(KEY, future.copy());
        builder.load(parent);
        builder.setChanged();

        IEnergyStorage energy = builder.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 0, "A future builder root accepted energy");
        helper.assertTrue(!builder.menuInventory().insertItem(SatelliteBuilderBlockEntity.SLOT_CHASSIS,
                new ItemStack(ModItems.SATELLITE_CHASSIS.get()), false).isEmpty(), "A future builder root accepted items");
        helper.assertTrue(!builder.assemble(player(helper, builder)), "A future builder root assembled");
        helper.assertTrue(builder.saveWithoutMetadata().getCompound(KEY).equals(future),
                "A future builder root was not preserved exactly");

        BlockPos position = builder.getBlockPos();
        helper.assertTrue(helper.getLevel().destroyBlock(position, true), "Builder removal failed");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1.5));
        helper.assertTrue(drops.size() == 1, "Removal duplicated items or omitted the builder");
        ItemStack carried = drops.get(0).getItem();
        helper.assertTrue(carried.is(ModItems.SATELLITE_BUILDER.get())
                && BlockItem.getBlockEntityData(carried).getCompound(KEY).equals(future),
                "The builder item did not carry its raw root");
        drops.get(0).discard();
        helper.succeed();
    }

    private static SatelliteBuilderBlockEntity place(GameTestHelper helper) {
        BlockPos position = new BlockPos(1, 2, 1);
        helper.setBlock(position, ModBlocks.SATELLITE_BUILDER.get().defaultBlockState()
                .setValue(SatelliteBuilderBlock.FACING, Direction.NORTH));
        return (SatelliteBuilderBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper, SatelliteBuilderBlockEntity builder) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SatelliteBuilder"));
        BlockPos position = builder.getBlockPos();
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        return player;
    }

    private static void insert(GameTestHelper helper, IItemHandler slots, int slot, Item item) {
        helper.assertTrue(slots.insertItem(slot, new ItemStack(item), false).isEmpty(),
                "Could not insert " + id(item) + " into builder slot " + slot);
    }

    private static void expect(GameTestHelper helper, SatelliteBuilderBlockEntity builder, FakePlayer player,
                               SatelliteOperationCode expected, String what) {
        helper.assertTrue(!builder.assemble(player), "Assembly succeeded for " + what);
        int actual = builder.saveWithoutMetadata().getCompound(KEY).getInt("last_result");
        helper.assertTrue(actual == expected.ordinal(), "Expected " + expected + " for " + what + " but got "
                + SatelliteOperationCode.values()[actual]);
    }

    private static ResourceLocation id(Item item) {
        return ForgeRegistries.ITEMS.getKey(item);
    }
}
