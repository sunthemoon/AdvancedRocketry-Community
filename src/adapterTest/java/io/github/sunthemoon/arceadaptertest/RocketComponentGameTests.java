package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.sunthemoon.advancedrocketrycommunity.api.rocket.RocketComponentDefinition;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Black-box component integration using only API, gameplay commands and saved NBT. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RocketComponentGameTests {
    private static final String HOST = "advancedrocketrycommunity";
    private static final ResourceLocation ROCKET = ResourceLocation.tryParse(HOST + ":rocket");

    private RocketComponentGameTests() { }

    @GameTest(templateNamespace = HOST, template = "rocket_test", timeoutTicks = 20)
    public static void externalComponentsReceiveOneClosedEvent(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.componentEvents == 1 && AdapterTestMod.componentEvent != null,
                "Expected exactly one component registration event");
        boolean rejected = false;
        try {
            AdapterTestMod.componentEvent.register(AdapterTestMod.id("late_component"),
                    Set.of(ResourceLocation.tryParse("minecraft:stone")),
                    new RocketComponentDefinition(1, 0, 0, false, false, false));
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "Retained component event remained writable");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST, template = "rocket_test", timeoutTicks = 40)
    public static void externalComponentsDriveActualAssemblyAndZeroFuelDisassembly(GameTestHelper helper) {
        BlockPos origin = new BlockPos(3, 2, 3);
        Map<BlockPos, Block> blocks = Map.of(origin, Blocks.DIAMOND_BLOCK, origin.west(), Blocks.EMERALD_BLOCK,
                origin.above(), Blocks.OAK_PLANKS, origin.above(2), Blocks.GOLD_BLOCK, origin.east(), Blocks.QUARTZ_BLOCK);
        blocks.forEach(helper::setBlock);
        helper.setBlock(origin.below(), ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":rocket_assembler")));
        FakePlayer owner = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ComponentFixture"));
        BlockPos absolute = helper.absolutePos(origin);
        owner.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        BlockPos assembler = helper.absolutePos(origin.below());
        try {
            int queued = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                    "arce rocket assemble " + assembler.getX() + " " + assembler.getY() + " " + assembler.getZ(),
                    owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            helper.assertTrue(queued == 1, "Production component assembly was not queued");
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException(exception);
        }
        AABB region = new AABB(absolute).inflate(3);
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(rockets(helper, region).size() == 1,
                "Production component assembly did not create exactly one rocket"))
                .thenExecute(() -> {
                    Entity rocket = rockets(helper, region).get(0);
                    CompoundTag saved = new CompoundTag();
                    helper.assertTrue(rocket.save(saved), "Component rocket did not serialize");
                    CompoundTag data = saved.getCompound("RocketEntityData");
                    CompoundTag snapshot = data.getCompound("snapshot");
                    CompoundTag stats = snapshot.getCompound("mass_inputs");
                    helper.assertTrue(stats.getInt("block_count") == 5 && stats.getLong("mass") == 254
                                    && stats.getLong("thrust") == 2400 && stats.getLong("fuel_capacity") == 1500
                                    && stats.getInt("engine_count") == 1 && stats.getInt("seat_count") == 1
                                    && stats.getInt("guidance_count") == 1 && stats.getInt("block_entity_count") == 0,
                            "Actual assembled stats differ from registered component definitions");
                    helper.assertTrue(snapshot.getList("passenger_anchors", Tag.TAG_INT_ARRAY).size() == 1
                                    && java.util.Arrays.equals(snapshot.getList("passenger_anchors", Tag.TAG_INT_ARRAY).getIntArray(0),
                                    new int[]{0, 1, 0}), "External seat anchor differs");
                    helper.assertTrue(data.getCompound("flight_data").getCompound("fuel").getLong("capacity") == 1500
                                    && owner.getUUID().equals(data.getUUID("owner_id")), "Flight capacity or owner differs");
                    for (BlockPos pos : blocks.keySet()) { helper.assertTrue(helper.getBlockState(pos).isAir(), "Duplicate world block"); }
                    owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
                    owner.setShiftKeyDown(true);
                    try { rocket.interact(owner, InteractionHand.MAIN_HAND); } finally { owner.setShiftKeyDown(false); }
                    helper.assertTrue(rocket.isRemoved() && rockets(helper, region).isEmpty(), "Zero-fuel disassembly failed");
                    blocks.forEach((pos, block) -> helper.assertTrue(helper.getBlockState(pos).is(block), "Original component block not restored"));
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, region).isEmpty(), "Component operation dropped duplicates");
                }).thenSucceed();
    }

    private static List<Entity> rockets(GameTestHelper helper, AABB region) {
        return helper.getLevel().getEntitiesOfClass(Entity.class, region,
                entity -> ROCKET.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
    }
}
