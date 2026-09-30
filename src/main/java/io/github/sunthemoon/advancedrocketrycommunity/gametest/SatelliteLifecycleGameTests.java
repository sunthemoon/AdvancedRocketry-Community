package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.builder.SatelliteBuilderMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteBlueprint;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKindState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteState;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStats;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlock;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** ADR-050 C8a-1: intent limits at the menus, the coalesced flush, and the operator lifecycle commands. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteLifecycleGameTests {
    private SatelliteLifecycleGameTests() {
    }

    /** ADR-049 section 10: a second intent in the same tick is refused at the menu, per player. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 40)
    public static void menuIntentsAreSpacedPerPlayer(GameTestHelper helper) {
        helper.setBlock(new BlockPos(1, 2, 1), ModBlocks.SATELLITE_TERMINAL.get().defaultBlockState()
                .setValue(SatelliteTerminalBlock.FACING, Direction.NORTH).setValue(SatelliteTerminalBlock.LIT, false));
        SatelliteTerminalBlockEntity terminal = (SatelliteTerminalBlockEntity) helper.getBlockEntity(new BlockPos(1, 2, 1));
        FakePlayer player = player(helper, "IntentSpacing");
        var menu = new SatelliteTerminalMenu(1, player.getInventory(), terminal, SatelliteTerminalTargets.current());
        helper.assertTrue(menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_NEXT), "The first selection was refused");
        helper.assertTrue(!menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_NEXT)
                        && lastResult(terminal) == SatelliteOperationCode.RATE_LIMITED,
                "A second selection in the same tick was accepted");
        menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_CLAIM);
        helper.assertTrue(!menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_CANCEL)
                        && lastResult(terminal) == SatelliteOperationCode.RATE_LIMITED,
                "A second state-changing intent in the same tick was accepted");
        helper.setBlock(new BlockPos(3, 2, 1), ModBlocks.SATELLITE_BUILDER.get().defaultBlockState()
                .setValue(SatelliteBuilderBlock.FACING, Direction.NORTH));
        SatelliteBuilderBlockEntity builder = (SatelliteBuilderBlockEntity) helper.getBlockEntity(new BlockPos(3, 2, 1));
        var builderMenu = new SatelliteBuilderMenu(2, player.getInventory(), builder, SatelliteRuntime.catalogGeneration());
        helper.assertTrue(!builderMenu.clickMenuButton(player, SatelliteBuilderMenu.BUTTON_ASSEMBLE)
                        && builder.saveWithoutMetadata().getCompound("SatelliteBuilder").getInt("last_result")
                        == SatelliteOperationCode.RATE_LIMITED.ordinal(),
                "The builder accepted an assembly in the same tick as a terminal intent");
        helper.runAfterDelay(11, () -> {
            helper.assertTrue(menu.clickMenuButton(player, SatelliteTerminalMenu.BUTTON_CLAIM)
                            || lastResult(terminal) != SatelliteOperationCode.RATE_LIMITED,
                    "An intent after the interval was still limited");
            helper.succeed();
        });
    }

    /** ADR-050 section 2: a change waits for the coalesced flush, which the production tick runs within 120 ticks. */
    @GameTest(template = "empty", batch = "satellite", timeoutTicks = 200)
    public static void aPendingChangeIsFlushedByTheServerTick(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        UUID solar = UUID.randomUUID();
        data.launchIdle(time -> SatelliteState.launchIdle(solar, ModIdentity.id("solar_satellite"), UUID.randomUUID(),
                time, CelestialIds.EARTH_ID, new SatelliteBlueprint(List.of(ModIdentity.id("satellite_chassis"),
                        ModIdentity.id("solar_transmitter_module")), false, new SatelliteStats(4, 720, 0, 0, 10)),
                new SatelliteKindState.Solar(100, Optional.empty())), server.overworld().getGameTime());
        data.updateKindState(solar, new SatelliteKindState.Solar(100, Optional.of(UUID.randomUUID())));
        long before = SatelliteRuntime.coalescedFlushes();
        helper.assertTrue(data.flushPending(), "A link change was not marked flush pending");
        helper.succeedWhen(() -> helper.assertTrue(SatelliteRuntime.coalescedFlushes() > before,
                "The server tick has not run a coalesced flush yet"));
    }

    /** ADR-050 sections 8 and 9: operators inspect, release, cancel and recover through audited commands. */
    @GameTest(template = "empty", batch = SatelliteRegistryFixture.LIFECYCLE_BATCH, timeoutTicks = 30)
    public static void operatorsResolveQuarantinesAndRecoveries(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SatelliteMissionSavedData data = SatelliteMissionSavedData.get(server);
        UUID mission = SatelliteRegistryFixture.orphanMission;
        UUID satellite = SatelliteRegistryFixture.strandedSatellite;
        helper.assertTrue(data.mission(mission).orElseThrow().status() == MissionStatus.QUARANTINED
                        && data.satellite(satellite).orElseThrow().status() == SatelliteStatus.RECOVERY_REQUIRED,
                "The broken root did not load as a quarantine and a recovery");
        FakePlayer operator = player(helper, "SatelliteOperator");
        var source = operator.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        var commands = server.getCommands();
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin mission inspect " + mission) == 1,
                "mission inspect failed");
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin mission release " + mission) == 0
                        && data.mission(mission).orElseThrow().status() == MissionStatus.QUARANTINED,
                "A mission without its satellite was released");
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin mission cancel " + mission) == 1
                        && data.mission(mission).orElseThrow().status() == MissionStatus.CANCELLED,
                "The operator could not cancel the quarantined mission");
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin satellite recover " + satellite) == 1
                        && data.satellite(satellite).orElseThrow().status() == SatelliteStatus.OPERATIONAL,
                "The operator could not recover the satellite");
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin instance inspect "
                + UUID.randomUUID()) == 0, "An unknown instance was inspected");
        helper.assertTrue(commands.performPrefixedCommand(source, "arce satellite admin diagnostics") == 1,
                "diagnostics failed");
        var player = operator.createCommandSourceStack().withPermission(0).withSuppressedOutput();
        helper.assertTrue(commands.performPrefixedCommand(player, "arce satellite admin satellite recover " + satellite) == 0,
                "A player without permission reached an operator command");
        helper.succeed();
    }

    private static SatelliteOperationCode lastResult(SatelliteTerminalBlockEntity terminal) {
        return SatelliteOperationCode.values()[terminal.saveWithoutMetadata().getCompound("SatelliteTerminal")
                .getInt("last_result")];
    }

    private static FakePlayer player(GameTestHelper helper, String name) {
        FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
        BlockPos position = helper.absolutePos(new BlockPos(2, 2, 1));
        player.setPos(position.getX() + 0.5D, position.getY() + 1.0D, position.getZ() + 0.5D);
        return player;
    }
}
