package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.resourceescrow.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNativeResources;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** W27c2 terminal parity and receipt/registry ordering only; no rocket deployment or source recovery. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteRegistryProofGameTests {
    private SatelliteRegistryProofGameTests() { }

    @GameTest(template = "empty", batch = "satellite_registry_proof", timeoutTicks = 100)
    public static void removedDefinitionReplayExtractsDataPackageWithoutRecreatingInitialMission(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var terminal = terminal(helper, new BlockPos(1, 2, 1));
        var player = player(helper, terminal);
        var identity = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), ModIdentity.id("removed_w119_definition"));
        var data = SatelliteMissionSavedData.get(server);
        UUID initial = SatelliteManager.initialMissionId(identity.satelliteId());
        var definition = new SatelliteDefinition(1, identity.definitionId(), 200, 120, 100, List.of(ModIdentity.id("earth")));
        helper.assertTrue(data.launch(identity.satelliteId(), initial, player.getUUID(), definition,
                ModIdentity.id("earth"), server.overworld().getGameTime(), false).success(), "Could not seed registration");
        helper.assertTrue(data.cancel(initial, player.getUUID(), false, server.overworld().getGameTime()).success(), "Could not cancel initial mission");
        var beforeMission = data.mission(initial).orElseThrow();
        var service = new SatelliteLaunchService(new SatelliteCatalogManager(), new CelestialCatalogManager());
        var validation = service.validate(server, player.getUUID(), identity, ModIdentity.id("removed_target"));
        helper.assertTrue(validation.result().code() == SatelliteOperationCode.IDEMPOTENT,
                "Shared replay consulted a missing definition or target");
        helper.assertTrue(service.register(server, validation.request().orElseThrow(), server.overworld().getGameTime()).code()
                == SatelliteOperationCode.IDEMPOTENT, "Shared register took the new-launch branch");
        insert(helper, terminal, identity);
        press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.IDEMPOTENT);
        helper.assertTrue(terminal.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).isEmpty(),
                "N8: removed-definition replay kept the package");
        helper.assertTrue(!data.isDirty() && data.mission(initial).orElseThrow().equals(beforeMission),
                "Terminal consumed before its barrier or reconstructed a cancelled initial mission");
        var watch = data.watch(identity.satelliteId());
        helper.assertTrue(data.acknowledgement().orElseThrow().proves(watch), "Terminal barrier did not acknowledge identity");
        var loaded = SatelliteMissionSavedData.load(data.save(new CompoundTag()));
        helper.assertTrue(loaded.acknowledgement().orElseThrow().proves(loaded.watch(identity.satelliteId())),
                "Restart observation required retained initial mission history");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "satellite_registry_proof", timeoutTicks = 200)
    public static void terminalLaunchAndDecommissionWaitForExactReceiptPhases(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        var terminal = terminal(helper, new BlockPos(1, 2, 1));
        var player = player(helper, terminal);
        var identity = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), ModIdentity.id("removed_w119_solar"),
                SatelliteKind.SOLAR, List.of(ModIdentity.id("removed_chassis"), ModIdentity.id("removed_primary")));
        var data = SatelliteMissionSavedData.get(server);
        helper.assertTrue(data.launchIdle(time -> SatelliteState.launchIdle(identity.satelliteId(), identity.definitionId(),
                identity.ownerId(), time, ModIdentity.id("earth"), new SatelliteBlueprint(identity.components(), false,
                        new SatelliteStats(4, 10720, 1000, 1, 10)), new SatelliteKindState.Solar(100, Optional.empty())),
                server.overworld().getGameTime()).success(), "Could not seed solar registration");
        data.flush(server);
        insert(helper, terminal, identity);
        String hash = RocketNativeResources.hash("w119-registry-ordering");
        long tick = server.getTickCount();
        var intent = new BayReceipt(UUID.randomUUID(), BayReceiptTestHooks.nextSequence(server), UUID.randomUUID(),
                new RocketPosition(0, 0, 0), identity.satelliteId(), identity.ownerId(), identity.definitionId().toString(),
                identity.kind(), ModIdentity.id("earth").toString(), hash, UUID.randomUUID(), hash, hash, hash, hash,
                BayReceipt.Phase.INTENT, "NONE", true, false, data.saveEpoch(), tick, tick, 0);
        helper.assertTrue(BayReceiptTestHooks.put(server, intent, outcome -> { }).queued(), "Could not queue INTENT");
        ItemStack packageBefore = terminal.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE).copy();
        press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.DEPLOYMENT_PENDING);
        press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.DEPLOYMENT_PENDING);
        helper.assertTrue(ItemStack.matches(packageBefore, terminal.menuInventory().getStackInSlot(SatelliteTerminalBlockEntity.SLOT_PACKAGE)),
                "A deployment refusal changed the package");
        int[] phase = {0};
        helper.onEachTick(() -> {
            var store = BayReceiptTestHooks.store(server).orElseThrow();
            if (store.holdsWork()) { return; }
            if (phase[0] == 0) {
                press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.DEPLOYMENT_PENDING);
                var registered = intent.withPhase(BayReceipt.Phase.REGISTERED, "NONE", server.getTickCount());
                helper.assertTrue(BayReceiptTestHooks.put(server, registered, outcome -> { }).queued(), "Could not queue REGISTERED");
                press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.DEPLOYMENT_PENDING);
                phase[0] = 1;
            } else if (phase[0] == 1) {
                helper.assertTrue(store.acknowledged(intent.op()), "REGISTERED has no exact acknowledgement");
                press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteOperationCode.DEPLOYMENT_PENDING);
                press(helper, terminal, player, SatelliteTerminalMenu.BUTTON_DECOMMISSION, SatelliteOperationCode.SUCCESS);
                helper.assertTrue(data.satellite(identity.satelliteId()).isEmpty(), "Durable REGISTERED still blocked normal removal");
                helper.assertTrue(data.deploymentPending(identity.satelliteId()), "Decommission deleted a deployment fence");
                helper.assertTrue(BayReceiptTestHooks.remove(server, intent.op(), outcome -> { }).queued(), "Could not retire fixture");
                helper.assertTrue(data.deploymentPending(identity.satelliteId()), "Queued removal released launch early");
                phase[0] = 2;
            } else {
                helper.assertTrue(!data.deploymentPending(identity.satelliteId()), "Acknowledged removal retained the fence");
                helper.succeed();
            }
        });
    }

    private static SatelliteTerminalBlockEntity terminal(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get());
        var terminal = (SatelliteTerminalBlockEntity) helper.getBlockEntity(pos);
        terminal.getCapability(ForgeCapabilities.ENERGY).resolve().orElseThrow()
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        return terminal;
    }
    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "W119Replay"));
        player.setPos(terminal.getBlockPos().getX() + 0.5, terminal.getBlockPos().getY() + 1, terminal.getBlockPos().getZ() + 0.5);
        return player;
    }
    private static void insert(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, SatelliteIdentity identity) {
        var chip = new ItemStack(ModItems.SATELLITE_CONTROL_CHIP.get()); SatelliteItemData.write(chip, identity);
        var pkg = new ItemStack(identity.kind() == SatelliteKind.DATA ? ModItems.DATA_SATELLITE_PACKAGE.get() : ModItems.SATELLITE_PACKAGE.get());
        SatelliteItemData.write(pkg, identity);
        helper.assertTrue(terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, chip, false).isEmpty(), "Chip refused");
        helper.assertTrue(terminal.menuInventory().insertItem(SatelliteTerminalBlockEntity.SLOT_PACKAGE, pkg, false).isEmpty(), "Package refused");
    }
    private static void press(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, FakePlayer player, int button,
                              SatelliteOperationCode expected) {
        terminal.handleButton(player, button);
        int result = terminal.saveWithoutMetadata().getCompound("SatelliteTerminal").getInt("last_result");
        helper.assertTrue(result == expected.ordinal(), "Expected " + expected + ", got " + SatelliteOperationCode.values()[result]);
    }
}
