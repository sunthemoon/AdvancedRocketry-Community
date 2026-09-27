package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketFuelDefinition;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Independent API/platform-only tests exercise registered fuel via actual player use and server ticks. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketFuelGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private RocketFuelGameTests() { }

    @GameTest(templateNamespace = HOST, template = "rocket_test", timeoutTicks = 20)
    public static void fuelRegistrationEventExpiresAfterOneDelivery(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.fuelEvents == 1 && AdapterTestMod.fuelEvent != null, "Fuel event count differs");
        boolean rejected = false;
        try {
            AdapterTestMod.fuelEvent.register(AdapterTestMod.id("late_fuel"), Set.of(ResourceLocation.tryParse("minecraft:bread")),
                    new RocketFuelDefinition(1, Optional.empty()));
        } catch (IllegalStateException expected) { rejected = true; }
        helper.assertTrue(rejected, "Retained fuel event was writable");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", timeoutTicks = 40)
    public static void registeredFuelActuallyConsumesOneItemAndReturnsAReburnableOutput(GameTestHelper helper) {
        exercise(helper, Items.CHARCOAL, Items.STICK, 73);
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", timeoutTicks = 40)
    public static void registeredNoRemainderFuelActuallyFinishesEmpty(GameTestHelper helper) {
        exercise(helper, Items.COAL, null, 127);
    }

    private static void exercise(GameTestHelper helper, Item input, Item remainder, long units) {
        var level = helper.getLevel(); BlockPos origin = new BlockPos(3, 2, 3); BlockPos loaderRelative = origin.east(4);
        helper.setBlock(loaderRelative, ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":fuel_loader")));
        var loader = helper.getBlockEntity(loaderRelative); BlockPos loaderPos = loader.getBlockPos();
        var owner = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "ItemFuelFixture"));
        owner.setPos(loaderPos.getX() + 0.5, loaderPos.getY(), loaderPos.getZ() + 0.5);
        var held = new ItemStack(input, 2); held.getOrCreateTag().putString("fixture", "queued native data");
        owner.setItemInHand(InteractionHand.MAIN_HAND, held);
        var hit = new BlockHitResult(Vec3.atCenterOf(loaderPos), Direction.UP, loaderPos, false);
        helper.assertTrue(loader.getBlockState().use(level, owner, InteractionHand.MAIN_HAND, hit).consumesAction()
                && held.getCount() == 1, "Registered player input did not consume one item");
        var handler = loader.getCapability(ForgeCapabilities.ITEM_HANDLER).orElseThrow(() -> new AssertionError("Missing inventory"));
        helper.assertTrue(handler.getStackInSlot(0).getTag().getString("fixture").equals("queued native data"), "Queued metadata changed");
        helper.setBlock(origin, Blocks.DIAMOND_BLOCK); helper.setBlock(origin.west(), Blocks.EMERALD_BLOCK);
        helper.setBlock(origin.above(), Blocks.OAK_PLANKS); helper.setBlock(origin.above(2), Blocks.GOLD_BLOCK);
        helper.setBlock(origin.east(), Blocks.QUARTZ_BLOCK);
        helper.setBlock(origin.below(), ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":rocket_assembler")));
        BlockPos assembler = helper.absolutePos(origin.below());
        try {
            helper.assertTrue(level.getServer().getCommands().getDispatcher().execute(
                    "arce rocket assemble " + assembler.getX() + " " + assembler.getY() + " " + assembler.getZ(),
                    owner.createCommandSourceStack().withPermission(2).withSuppressedOutput()) == 1, "Fuel fixture assembly not queued");
        } catch (CommandSyntaxException exception) { throw new IllegalStateException(exception); }
        AABB region = new AABB(helper.absolutePos(origin)).inflate(3);
        helper.startSequence().thenWaitUntil(() -> {
            var rockets = level.getEntitiesOfClass(Entity.class, region, entity ->
                    ResourceLocation.tryParse(HOST + ":rocket").equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
            helper.assertTrue(rockets.size() == 1, "Actual assembly did not produce one rocket");
            CompoundTag saved = new CompoundTag(); rockets.get(0).save(saved);
            helper.assertTrue(saved.getCompound("RocketEntityData").getCompound("flight_data").getCompound("fuel").getLong("amount") == units,
                    "Registered fuel value was not actually transferred");
            helper.assertTrue(loader.saveWithoutMetadata().getCompound("arce_fuel_loader").getLong("buffered_units") == 0,
                    "Batch did not finish");
        }).thenExecuteAfter(2, () -> {
            ItemStack output = handler.extractItem(0, 1, false);
            helper.assertTrue(remainder == null ? output.isEmpty() : output.is(remainder) && output.getCount() == 1,
                    "Configured remainder differs or was automatically burned");
            helper.assertTrue(handler.extractItem(0, 1, false).isEmpty() && held.getCount() == 1, "Duplicate item/remainder");
        }).thenSucceed();
    }
}
