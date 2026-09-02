package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteGameTests {
    private static final UUID OWNER_ID = UUID.fromString("731e21fd-e33c-42be-b281-52f02804f81a");
    private static final UUID INTRUDER_ID = UUID.fromString("a82e7b79-a01d-4611-99b8-b98ca6d9967f");

    private SatelliteGameTests() {
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 280)
    public static void terminalMissionIsServerAuthoritativeAndClaimsExactlyOnce(GameTestHelper helper) {
        BlockPos position = BlockPos.ZERO;
        SatelliteTerminalBlockEntity terminal = placeTerminal(helper, position);
        TestPlayer owner = new TestPlayer(helper.getLevel(), OWNER_ID, "SatelliteOwner");
        TestPlayer intruder = new TestPlayer(helper.getLevel(), INTRUDER_ID, "SatelliteIntruder");
        BlockPos absolute = helper.absolutePos(position);
        owner.setPos(absolute.getX() + 0.5D, absolute.getY() + 1.0D, absolute.getZ() + 0.5D);
        intruder.setPos(absolute.getX() + 0.5D, absolute.getY() + 1.0D, absolute.getZ() + 0.5D);
        terminal.setOwner(owner.getUUID());
        helper.assertTrue(!terminal.canAccess(intruder),
                "A second player could open another player's terminal inventory");

        IItemHandler inventory = terminal.menuInventory();
        insert(helper, inventory, SatelliteTerminalBlockEntity.SLOT_CHASSIS,
                new ItemStack(ModItems.SATELLITE_CHASSIS.get()));
        insert(helper, inventory, SatelliteTerminalBlockEntity.SLOT_SOLAR_MODULE,
                new ItemStack(ModItems.SATELLITE_SOLAR_MODULE.get()));
        insert(helper, inventory, SatelliteTerminalBlockEntity.SLOT_DATA_STORAGE,
                new ItemStack(ModItems.DATA_STORAGE_UNIT.get()));
        insert(helper, inventory, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP,
                new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()));
        IEnergyStorage energy = terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
        helper.assertTrue(energy.receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false)
                        == SatelliteTerminalBlockEntity.ENERGY_CAPACITY,
                "Satellite Terminal did not accept its bounded energy capacity");

        helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_ASSEMBLE),
                "Server rejected a nearby owner's assembly intent");
        SatelliteIdentity identity = SatelliteItemData.read(
                inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP)
        ).identity().orElseThrow();
        helper.assertTrue(inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE)
                        .is(ModItems.DATA_SATELLITE_PACKAGE.get()),
                "Assembly did not create the bound satellite package");

        helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_LAUNCH),
                "Server rejected a nearby owner's launch intent");
        helper.assertTrue(inventory.getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "Successful launch did not consume the bound package");
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(helper.getLevel().getServer());
        UUID missionId = data.satellite(identity.satelliteId()).orElseThrow()
                .currentMissionId().orElseThrow();
        MissionState active = data.mission(missionId).orElseThrow();
        int expectedResearch = active.netResearchCredit();
        int researchBefore = data.account(owner.getUUID()).balance();

        helper.assertTrue(
                SatelliteRuntime.claim(intruder, identity).code() == SatelliteOperationCode.UNAUTHORIZED,
                "A second player could claim another player's mission"
        );
        helper.assertTrue(data.account(owner.getUUID()).balance() == researchBefore,
                "Unauthorized intent changed the owner's research balance");

        helper.runAtTickTime(240, () -> {
            MissionState ready = data.mission(missionId).orElseThrow();
            helper.assertTrue(ready.status() == MissionStatus.READY,
                    "Deadline scheduler did not finish an unloaded logical mission");
            helper.assertTrue(terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_CLAIM),
                    "Server rejected the owner's ready claim");
            helper.assertTrue(data.mission(missionId).orElseThrow().status() == MissionStatus.CLAIMED,
                    "Claim did not reach its durable terminal phase");
            helper.assertTrue(data.account(owner.getUUID()).balance() == researchBefore + expectedResearch,
                    "Claim credited an unexpected research amount");
            helper.assertTrue(CelestialSavedData.get(helper.getLevel().getServer())
                            .get(active.targetBodyId()).isPresent(),
                    "Legal mission result did not update celestial discovery");

            int balance = data.account(owner.getUUID()).balance();
            terminal.handleButton(owner, SatelliteTerminalMenu.BUTTON_CLAIM);
            helper.assertTrue(data.account(owner.getUUID()).balance() == balance,
                    "Repeated claim duplicated research");
            helper.succeed();
        });
    }

    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 30)
    public static void terminalFutureSchemaIsPreservedAndBlocked(GameTestHelper helper) {
        SatelliteTerminalBlockEntity terminal = placeTerminal(helper, BlockPos.ZERO);
        CompoundTag future = new CompoundTag();
        future.putInt("schema_version", 2);
        future.putString("future_payload", "preserve-exactly");
        CompoundTag parent = new CompoundTag();
        parent.put("SatelliteTerminal", future.copy());

        terminal.load(parent);

        IEnergyStorage energy = terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow();
        helper.assertTrue(energy.receiveEnergy(1_000, false) == 0,
                "Future terminal schema accepted an energy mutation");
        helper.assertTrue(!terminal.menuInventory().insertItem(
                SatelliteTerminalBlockEntity.SLOT_CHASSIS,
                new ItemStack(ModItems.SATELLITE_CHASSIS.get()),
                false
        ).isEmpty(), "Future terminal schema accepted an inventory mutation");
        helper.assertTrue(terminal.saveWithoutMetadata().getCompound("SatelliteTerminal").equals(future),
                "Future terminal schema was not preserved exactly");
        helper.succeed();
    }

    private static SatelliteTerminalBlockEntity placeTerminal(GameTestHelper helper, BlockPos position) {
        helper.setBlock(
                position,
                ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                        .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH)
                        .setValue(SatelliteTerminalBlock.LIT, false)
        );
        return (SatelliteTerminalBlockEntity) helper.getBlockEntity(position);
    }

    private static void insert(
            GameTestHelper helper,
            IItemHandler inventory,
            int slot,
            ItemStack stack
    ) {
        helper.assertTrue(inventory.insertItem(slot, stack, false).isEmpty(),
                "Could not insert Satellite Terminal test component into slot " + slot);
    }

    /** Network-free server actor for ownership and replay validation. */
    private static final class TestPlayer extends FakePlayer {
        private TestPlayer(ServerLevel level, UUID id, String name) {
            super(level, new GameProfile(id, name));
        }
    }
}
