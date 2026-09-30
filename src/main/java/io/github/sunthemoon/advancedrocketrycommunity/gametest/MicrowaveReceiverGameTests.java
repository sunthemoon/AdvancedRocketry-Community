package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.receiver.MicrowaveReceiverBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SolarLinks;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-049 section 9: link claim, holding against a duplicate chip, release, output, unlink and carry. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MicrowaveReceiverGameTests {
    private static final ResourceLocation SOLAR = ModIdentity.id("solar_satellite");
    private static final String KEY = "MicrowaveReceiver";

    private MicrowaveReceiverGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 140)
    public static void theHolderKeepsTheLinkUntilItsChipLeaves(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper);
        SatelliteIdentity identity = launch(server, owner.getUUID(), Optional.empty());
        MicrowaveReceiverBlockEntity first = place(helper, new BlockPos(1, 2, 1));
        MicrowaveReceiverBlockEntity second = place(helper, new BlockPos(1, 2, 3));
        first.menuInventory().insertItem(0, chip(identity), false);
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);

        // Every link change waits for the holder's own 20-tick check.
        helper.runAfterDelay(22, () -> {
            helper.assertTrue(link(data, identity).equals(Optional.ofNullable(first.receiverId())),
                    "The first receiver did not claim the free link");
            helper.assertTrue(first.status(0) == SolarLinks.LinkStatus.HOLDING && first.output() == 4,
                    "A power-4 satellite over Earth should give 4 FE/t, got " + first.output());
            second.menuInventory().insertItem(0, chip(identity), false);
            helper.runAfterDelay(22, () -> {
                helper.assertTrue(second.status(0) == SolarLinks.LinkStatus.ELSEWHERE && second.output() == 0
                                && link(data, identity).equals(Optional.of(first.receiverId())),
                        "A duplicated chip took the link or produced power");
                helper.assertTrue(first.energyStored() > 0, "The holder stored no energy");
                helper.assertTrue(SatelliteRuntime.decommission(owner, identity).code() == SatelliteOperationCode.MISSION_BUSY,
                        "A satellite linked to a present receiver was decommissioned");
                helper.assertTrue(SatelliteRuntime.unlink(owner, identity).code() == SatelliteOperationCode.MISSION_BUSY,
                        "A link to a present receiver was cleared by its owner");
                IEnergyStorage energy = first.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
                helper.assertTrue(energy.receiveEnergy(1_000, false) == 0 && energy.extractEnergy(1, false) == 1,
                        "The receiver buffer is not output-only");

                first.menuInventory().extractItem(0, 1, false);
                // The holder releases at its next check, then the other receiver claims at its own.
                helper.runAfterDelay(42, () -> {
                    helper.assertTrue(link(data, identity).equals(Optional.of(second.receiverId()))
                                    && second.status(0) == SolarLinks.LinkStatus.HOLDING && second.output() == 4
                                    && first.output() == 0,
                            "The link did not pass to the remaining chip holder");
                    BlockPos removed = second.getBlockPos();
                    helper.getLevel().destroyBlock(removed, false);
                    helper.assertTrue(link(data, identity).isEmpty(), "Breaking the holder kept the link");
                    helper.assertTrue(!helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(removed).inflate(1.5))
                            .isEmpty(), "Breaking the receiver did not drop its chip");
                    helper.succeed();
                });
            });
        });
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void onlyAConfirmedMissingReceiverCanBeUnlinkedByItsOwner(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        FakePlayer owner = player(helper);
        UUID vanished = UUID.randomUUID();
        SatelliteIdentity identity = launch(server, owner.getUUID(), Optional.of(vanished));
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);

        // Never seen since the start: unknown, so neither the owner's unlink nor decommission is allowed.
        helper.assertTrue(SatelliteRuntime.unlink(owner, identity).code() == SatelliteOperationCode.MISSION_BUSY,
                "An unknown receiver was treated as missing");
        helper.assertTrue(SatelliteRuntime.decommission(owner, identity).code() == SatelliteOperationCode.MISSION_BUSY,
                "A satellite linked to an unknown receiver was decommissioned");
        // Last seen in a loaded chunk that now has no receiver with that ID: missing.
        SatelliteRuntime.registerReceiver(vanished, helper.getLevel().dimension(), helper.absolutePos(new BlockPos(0, 2, 0)));
        helper.assertTrue(SatelliteRuntime.unlink(owner, identity).code() == SatelliteOperationCode.SUCCESS
                && link(data, identity).isEmpty(), "The owner could not unlink a missing receiver");
        helper.assertTrue(SatelliteRuntime.unlink(owner, identity).code() == SatelliteOperationCode.IDEMPOTENT,
                "Unlinking twice was not idempotent");
        data.updateKindState(identity.satelliteId(), new SatelliteKindState.Solar(100, Optional.of(vanished)));
        helper.assertTrue(SatelliteRuntime.decommission(owner, identity).code() == SatelliteOperationCode.SUCCESS
                && data.satellite(identity.satelliteId()).isEmpty(), "A satellite of a missing receiver was not decommissioned");

        SatelliteIdentity operatorCase = launch(server, owner.getUUID(), Optional.of(UUID.randomUUID()));
        var source = owner.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        int result = server.getCommands().performPrefixedCommand(source,
                "arce satellite admin unlink " + operatorCase.satelliteId());
        helper.assertTrue(result == 1 && link(data, operatorCase).isEmpty(), "The operator could not unlink by ID");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void receiverFutureRootIsPreservedBlockedAndCarried(GameTestHelper helper) {
        MicrowaveReceiverBlockEntity receiver = place(helper, new BlockPos(1, 2, 1));
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "preserve-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put(KEY, future.copy());
        receiver.load(parent);
        receiver.setChanged();

        helper.assertTrue(receiver.menuInventory().insertItem(0,
                chip(new SatelliteIdentity(UUID.randomUUID(), UUID.randomUUID(), SOLAR, SatelliteKind.SOLAR,
                        components())), false).getCount() == 1, "A future receiver root accepted a chip");
        helper.assertTrue(receiver.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .extractEnergy(1, false) == 0, "A future receiver root gave energy");
        helper.assertTrue(receiver.saveWithoutMetadata().getCompound(KEY).equals(future),
                "A future receiver root was not preserved exactly");
        BlockPos position = receiver.getBlockPos();
        helper.assertTrue(helper.getLevel().destroyBlock(position, true), "Receiver removal failed");
        var drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(position).inflate(1.5));
        helper.assertTrue(drops.size() == 1, "Removal duplicated items or omitted the receiver");
        ItemStack carried = drops.get(0).getItem();
        helper.assertTrue(carried.is(ModItems.MICROWAVE_RECEIVER.get())
                && BlockItem.getBlockEntityData(carried).getCompound(KEY).equals(future),
                "The receiver item did not carry its raw root");
        drops.get(0).discard();
        helper.succeed();
    }

    private static Optional<UUID> link(SatelliteMissionSavedData data, SatelliteIdentity identity) {
        return data.satellite(identity.satelliteId())
                .map(SatelliteState::kindState)
                .filter(SatelliteKindState.Solar.class::isInstance)
                .flatMap(state -> ((SatelliteKindState.Solar) state).receiver());
    }

    private static SatelliteIdentity launch(MinecraftServer server, UUID ownerId, Optional<UUID> receiver) {
        UUID satelliteId = UUID.randomUUID();
        SatelliteMissionSavedData.get(server).launchIdle(time -> SatelliteState.launchIdle(satelliteId, SOLAR, ownerId,
                time, CelestialIds.EARTH_ID, new SatelliteBlueprint(components(), false,
                        new SatelliteStats(4, 720, 0, 0, 10)), new SatelliteKindState.Solar(100, receiver)),
                server.overworld().getGameTime());
        return new SatelliteIdentity(satelliteId, ownerId, SOLAR, SatelliteKind.SOLAR, components());
    }

    private static List<ResourceLocation> components() {
        return List.of(ModIdentity.id("satellite_chassis"), ModIdentity.id("solar_transmitter_module"),
                ModIdentity.id("satellite_solar_module"));
    }

    private static ItemStack chip(SatelliteIdentity identity) {
        ItemStack chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get());
        SatelliteItemData.write(chip, identity);
        return chip;
    }

    private static MicrowaveReceiverBlockEntity place(GameTestHelper helper, BlockPos position) {
        helper.setBlock(position, ModBlocks.MICROWAVE_RECEIVER.get());
        return (MicrowaveReceiverBlockEntity) helper.getBlockEntity(position);
    }

    private static FakePlayer player(GameTestHelper helper) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "SolarOwner"));
        BlockPos position = helper.absolutePos(new BlockPos(1, 2, 1));
        player.setPos(position.getX() + 0.5D, position.getY(), position.getZ() + 0.5D);
        return player;
    }
}
