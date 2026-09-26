package io.github.sunthemoon.arceadaptertest;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** Black-box host integration: registry IDs, server commands, entity interaction and saved NBT. */
@GameTestHolder(AdapterTestMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class AdapterRegistrationGameTests {
    private static final String HOST_ID = "advancedrocketrycommunity";
    private static final ResourceLocation ROCKET_TYPE = ResourceLocation.tryParse(HOST_ID + ":rocket");

    private AdapterRegistrationGameTests() {
    }

    @GameTest(templateNamespace = HOST_ID, template = "rocket_test", timeoutTicks = 20)
    public static void externalAdapterReceivesOneClosedRegistrationEvent(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.registrationEvents() == 1,
                "External mod did not receive exactly one rocket registration event");
        helper.assertTrue(AdapterTestMod.receivedEvent() != null,
                "External mod registration event was not delivered on its mod bus");
        boolean rejected = false;
        try {
            AdapterTestMod.receivedEvent().register(
                    AdapterTestMod.id("late_inventory"),
                    Set.of(ResourceLocation.tryParse("minecraft:furnace")),
                    AdapterTestMod.PAYLOAD_VERSION,
                    new FixtureInventoryAdapter());
        } catch (IllegalStateException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "A retained event registered a provider after startup freeze");
        helper.succeed();
    }

    @GameTest(templateNamespace = HOST_ID, template = "rocket_test", timeoutTicks = 40)
    public static void externalAdapterProductionAssemblyPreservesInventory(GameTestHelper helper) {
        helper.assertTrue(AdapterTestMod.registrationEvents() == 1,
                "Production test started without exactly one external registration event");
        BlockPos origin = new BlockPos(3, 2, 3);
        BlockPos cargoPosition = origin.east();
        helper.setBlock(origin.below(), hostBlock("rocket_assembler"));
        helper.setBlock(origin, hostBlock("rocket_motor"));
        helper.setBlock(origin.above(), hostBlock("rocket_seat"));
        helper.setBlock(origin.above(2), hostBlock("guidance_computer"));
        helper.setBlock(cargoPosition, AdapterTestMod.CONTAINER.get());
        FixtureContainerBlockEntity original = container(helper, cargoPosition);
        ItemStack diamonds = new ItemStack(Items.DIAMOND, 17);
        diamonds.setHoverName(Component.literal("Public adapter cargo"));
        original.setItem(0, diamonds);
        original.setItem(1, new ItemStack(Items.IRON_INGOT, 64));
        CompoundTag expectedInventory = original.captureInventory();

        // A per-test owner avoids the global fake-player cache and needs no login or test hook.
        FakePlayer owner = new FakePlayer(helper.getLevel(),
                new GameProfile(UUID.randomUUID(), "AdapterFixture"));
        BlockPos absoluteOrigin = helper.absolutePos(origin);
        owner.setPos(absoluteOrigin.getX() + 0.5D, absoluteOrigin.getY(), absoluteOrigin.getZ() + 0.5D);
        BlockPos assembler = helper.absolutePos(origin.below());
        String assemble = "arce rocket assemble " + assembler.getX() + " "
                + assembler.getY() + " " + assembler.getZ();
        try {
            int queued = helper.getLevel().getServer().getCommands().getDispatcher().execute(
                    assemble, owner.createCommandSourceStack().withPermission(2).withSuppressedOutput());
            helper.assertTrue(queued == 1, "Host command did not queue the fixture rocket assembly");
        } catch (CommandSyntaxException exception) {
            throw new IllegalStateException("Host assembly command is unavailable", exception);
        }

        AABB region = new AABB(absoluteOrigin).inflate(3.0D);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rockets(helper, region).size() == 1,
                        "Production tick did not assemble one fixture rocket; assembler="
                                + helper.getBlockEntity(origin.below()).saveWithoutMetadata()))
                .thenExecute(() -> {
                    Entity rocket = rockets(helper, region).get(0);
                    for (BlockPos position : List.of(origin, origin.above(), origin.above(2), cargoPosition)) {
                        helper.assertTrue(helper.getBlockState(position).isAir(),
                                "Assembly left a duplicate world block at " + position);
                    }
                    helper.assertTrue(helper.getLevel().getBlockEntity(helper.absolutePos(cargoPosition)) == null,
                            "Assembly left the source inventory BlockEntity behind");
                    assertSavedPayload(helper, rocket, owner.getUUID(), expectedInventory);
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, region).isEmpty(),
                            "Assembly dropped duplicate inventory items");

                    // Normal player interaction dispatches into the installed production RocketRuntime.
                    owner.setPos(rocket.getX(), rocket.getY(), rocket.getZ());
                    owner.setShiftKeyDown(true);
                    try {
                        rocket.interact(owner, InteractionHand.MAIN_HAND);
                    } finally {
                        owner.setShiftKeyDown(false);
                    }
                    helper.assertTrue(rocket.isRemoved() && rockets(helper, region).isEmpty(),
                            "Owner interaction did not remove the assembled rocket exactly once");
                    helper.assertTrue(helper.getBlockState(origin).is(hostBlock("rocket_motor"))
                                    && helper.getBlockState(origin.above()).is(hostBlock("rocket_seat"))
                                    && helper.getBlockState(origin.above(2)).is(hostBlock("guidance_computer"))
                                    && helper.getBlockState(cargoPosition).is(AdapterTestMod.CONTAINER.get()),
                            "Production disassembly did not restore all four original blocks");
                    FixtureContainerBlockEntity restored = container(helper, cargoPosition);
                    helper.assertTrue(restored != original, "Disassembly reused the detached source inventory");
                    helper.assertTrue(expectedInventory.equals(restored.captureInventory()),
                            "Production disassembly changed the inventory items or item metadata");
                    CompoundTag savedContainer = restored.saveWithoutMetadata().getCompound("FixtureInventory");
                    helper.assertTrue(savedContainer.getInt("schema_version") == 1,
                            "Restored fixture inventory omitted its own persistence schema");
                    savedContainer.remove("schema_version");
                    helper.assertTrue(expectedInventory.equals(savedContainer),
                            "Restored BlockEntity persistence changed the recovered items");
                    helper.assertTrue(helper.getLevel().getEntitiesOfClass(ItemEntity.class, region).isEmpty(),
                            "Disassembly dropped duplicate inventory items");
                })
                .thenSucceed();
    }

    private static void assertSavedPayload(
            GameTestHelper helper, Entity rocket, UUID ownerId, CompoundTag expectedInventory
    ) {
        CompoundTag saved = new CompoundTag();
        helper.assertTrue(rocket.save(saved), "Assembled rocket refused entity serialization");
        CompoundTag rocketData = saved.getCompound("RocketEntityData");
        helper.assertTrue(ownerId.equals(rocketData.getUUID("owner_id")),
                "Host assembly command did not preserve its requesting owner");
        ListTag blocks = rocketData.getCompound("snapshot").getList("relative_blocks", Tag.TAG_COMPOUND);
        helper.assertTrue(blocks.size() == 4, "Host persisted something other than a four-block rocket");
        int externalPayloads = 0;
        for (int index = 0; index < blocks.size(); index++) {
            CompoundTag block = blocks.getCompound(index);
            if (!block.contains("block_entity", Tag.TAG_COMPOUND)) {
                continue;
            }
            externalPayloads++;
            CompoundTag payload = block.getCompound("block_entity");
            helper.assertTrue(AdapterTestMod.ADAPTER_ID.toString().equals(payload.getString("adapter")),
                    "Production snapshot did not select the registered external adapter");
            CompoundTag envelope = payload.getCompound("data");
            helper.assertTrue(envelope.getAllKeys().equals(Set.of("payload_version", "data")),
                    "External payload did not use the frozen versioned envelope");
            helper.assertTrue(envelope.getInt("payload_version") == AdapterTestMod.PAYLOAD_VERSION,
                    "Production snapshot omitted or changed the registered payload version");
            helper.assertTrue(expectedInventory.equals(envelope.getCompound("data")),
                    "Production snapshot changed the captured items or item metadata");
        }
        helper.assertTrue(externalPayloads == 1, "Snapshot did not contain exactly one external inventory");
    }

    private static FixtureContainerBlockEntity container(GameTestHelper helper, BlockPos position) {
        helper.assertTrue(helper.getBlockEntity(position) instanceof FixtureContainerBlockEntity,
                "Fixture inventory BlockEntity is missing at " + position);
        return (FixtureContainerBlockEntity) helper.getBlockEntity(position);
    }

    private static List<Entity> rockets(GameTestHelper helper, AABB region) {
        return helper.getLevel().getEntitiesOfClass(Entity.class, region,
                entity -> ROCKET_TYPE.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType())));
    }

    private static Block hostBlock(String path) {
        ResourceLocation id = ResourceLocation.tryParse(HOST_ID + ":" + path);
        if (!ForgeRegistries.BLOCKS.containsKey(id)) {
            throw new IllegalStateException("Missing required host block " + id);
        }
        return ForgeRegistries.BLOCKS.getValue(id);
    }
}
