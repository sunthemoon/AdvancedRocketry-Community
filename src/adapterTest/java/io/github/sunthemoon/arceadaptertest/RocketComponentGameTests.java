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
import net.minecraft.world.entity.Entity;
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
    @net.minecraft.gametest.framework.GameTestGenerator
    public static java.util.Collection<net.minecraft.gametest.framework.TestFunction> asynchronousTransitions() {
        return java.util.List.of(
                AdapterAsyncFixture.test("w164_adapter_success", "RocketComponentGameTests.externalComponentsDriveActualAssemblyAndZeroFuelDisassembly",
                        5, 1, false, RocketComponentGameTests::externalComponentsDriveActualAssemblyAndZeroFuelDisassembly),
                AdapterAsyncFixture.test("w272_adapter_pending_rejection", "RocketComponentGameTests.externalComponentsRetryRejectedPendingDisassembly",
                        5, 1, false, helper -> componentAssemblyAndDisassembly(helper, true)),
                AdapterAsyncFixture.test("w299_adapter_start_button", "RocketComponentGameTests.flowingWaterCannotDropComponentStartButton",
                        5, 1, false, helper -> AdapterDropFixture.withFlowingStartControlWater(helper,
                                () -> componentAssemblyAndDisassembly(helper, true))));
    }
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

    public static void externalComponentsDriveActualAssemblyAndZeroFuelDisassembly(GameTestHelper helper) {
        componentAssemblyAndDisassembly(helper, false);
    }

    private static void componentAssemblyAndDisassembly(GameTestHelper helper, boolean rejectFirstPending) {
        AdapterDropFixture.retireStartControl(helper);
        var rejection = rejectFirstPending ? new AdapterPendingRejectionFixture(helper) : null;
        BlockPos origin = new BlockPos(3, 2, 3);
        Map<BlockPos, Block> blocks = Map.of(origin, Blocks.DIAMOND_BLOCK, origin.west(), Blocks.EMERALD_BLOCK,
                origin.above(), Blocks.OAK_PLANKS, origin.above(2), Blocks.GOLD_BLOCK, origin.east(), Blocks.QUARTZ_BLOCK);
        blocks.forEach(helper::setBlock);
        helper.setBlock(origin.below(), ForgeRegistries.BLOCKS.getValue(ResourceLocation.tryParse(HOST + ":rocket_assembler")));
        FakePlayer owner = new AdapterAsyncFixture.Player(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ComponentFixture"));
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
        Entity[] authority = new Entity[1];
        AdapterAsyncFixture.Submission[] disassembly = new AdapterAsyncFixture.Submission[1];
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(rockets(helper, region).size() == 1,
                "Production component assembly did not create exactly one rocket"))
                .thenExecute(() -> {
                    Entity rocket = rockets(helper, region).get(0);
                    authority[0] = rocket;
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
                    disassembly[0] = new AdapterAsyncFixture.Submission(owner, rocket);
                })
                .thenWaitUntil(() -> helper.assertTrue(disassembly[0].advance(helper), "Component disassembly admission is busy"))
                .thenExecute(() -> { if (rejection != null) { rejection.arm(disassembly[0]); } })
                // DISASSEMBLY_PENDING -> BLOCKS_PUBLISHED, including every non-container member.
                .thenWaitUntil(() -> {
                    // Observe terminal rejection before a fresh request; never refresh accepted pending work.
                    if (!authority[0].isRemoved()) { disassembly[0].advance(helper); }
                    blocks.forEach((pos, block) -> helper.assertTrue(helper.getBlockState(pos).is(block),
                            "Original component block not yet published"));
                })
                .thenExecute(() -> {
                    if (rejection != null) { rejection.assertRejected(); }
                    Entity rocket = authority[0];
                    helper.assertTrue(rocket.isRemoved() && rockets(helper, region).isEmpty(), "Zero-fuel disassembly failed");
                    blocks.forEach((pos, block) -> helper.assertTrue(helper.getBlockState(pos).is(block), "Original component block not restored"));
                    AdapterDropFixture.assertNoDrops(helper, region, "Component operation dropped duplicates");
                }).thenSucceed();
    }

    private static List<Entity> rockets(GameTestHelper helper, AABB region) {
        return helper.getLevel().getEntitiesOfClass(Entity.class, region,
                entity -> ROCKET.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
    }
}
