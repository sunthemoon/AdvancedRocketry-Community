package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import com.mojang.authlib.GameProfile;
import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.content.PlanetaryContent;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.persistence.CelestialSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataFile;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.CheckedSavedDataPolicy;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.migration.ManagedSavedDataType;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModBlocks;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.content.SatelliteItemData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.MissionStatus;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.SatelliteOperationCode;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteKind;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.TerminalActionTable;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalMenu;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.SatelliteTerminalTargets;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.terminal.TerminalPendingEffects;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.AfterBatch;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/** W129bc review regressions through real terminal buttons, inventory, services and checked IO. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SatelliteTerminalBarrierGameTests {
    private static final String BATCH = "satellite_terminal_barrier_review";
    private static final CheckedSavedDataPolicy ATOMIC = CheckedSavedDataPolicy.atomic(ManagedSavedDataType.SATELLITE_MISSIONS);
    private static SatelliteMissionSavedData original;
    private static CelestialSavedData originalCelestial;
    private static final java.util.List<HeldWrite> holds = new java.util.ArrayList<>();
    private static final java.util.List<TerminalActionTable.Key> keys = new java.util.ArrayList<>();

    private SatelliteTerminalBarrierGameTests() { }

    @BeforeBatch(batch = BATCH)
    public static void install(ServerLevel level) {
        var server = level.getServer();
        original = SatelliteMissionSavedData.get(server);
        original.awaitForTests(server);
        originalCelestial = CelestialSavedData.get(server);
        var storage = server.overworld().getDataStorage();
        storage.set(SatelliteMissionSavedData.DATA_NAME, SatelliteMissionSavedData.create(server.overworld().getGameTime()));
        storage.set(CelestialSavedData.DATA_NAME, CelestialSavedData.create());
    }

    @AfterBatch(batch = BATCH)
    public static void restore(ServerLevel level) {
        var server = level.getServer();
        var fixture = SatelliteMissionSavedData.get(server);
        holds.forEach(held -> held.release.countDown());
        fixture.writePolicy(ATOMIC);
        fixture.request(server);
        fixture.awaitForTests(server);
        fixture.close();
        var table = SatelliteRuntime.terminalActions().orElseThrow();
        keys.forEach(key -> table.at(key).ifPresent(table::completed));
        holds.clear();
        keys.clear();
        var storage = server.overworld().getDataStorage();
        storage.set(SatelliteMissionSavedData.DATA_NAME, original);
        storage.set(CelestialSavedData.DATA_NAME, originalCelestial);
        original.setDirty();
        originalCelestial.setDirty();
        storage.save();
        original = null;
        originalCelestial = null;
    }

    @BeforeBatch(batch = "satellite_terminal_survey_review")
    public static void installSurvey(ServerLevel level) { install(level); }

    @AfterBatch(batch = "satellite_terminal_survey_review")
    public static void restoreSurvey(ServerLevel level) { restore(level); }

    @GameTest(template = "empty", batch = BATCH, timeoutTicks = 300)
    public static void firstDiscoveryClaimRetainsItsTerminalEntryThroughHeldAndFailedWrites(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = SatelliteMissionSavedData.get(server);
        var celestial = CelestialSavedData.get(server);
        var terminal = place(helper, 1);
        var player = player(helper, terminal);
        var definition = PlanetaryContent.surveySatellite();
        var chip = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), definition.id());
        UUID mission = UUID.randomUUID();
        long now = server.overworld().getGameTime();
        data.launch(chip.satelliteId(), mission, player.getUUID(), definition, PlanetaryContent.MARS, now, true);
        data.completeDue(now + 200);
        data.flush(server);
        insert(helper, terminal, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), chip));
        var menu = new SatelliteTerminalMenu(1, player.getInventory(), terminal, SatelliteTerminalTargets.current());
        var table = SatelliteRuntime.terminalActions().orElseThrow();
        var key = new TerminalActionTable.Key(helper.getLevel().dimension(), terminal.getBlockPos());
        keys.add(key);
        var failed = new HeldWrite(true);
        holds.add(failed);
        data.writePolicy(failed.policy);
        terminal.handleButton(player, SatelliteTerminalMenu.BUTTON_CLAIM);
        var paid = data.token();
        helper.assertTrue(data.account(player.getUUID()).balance() == 20
                && data.mission(mission).orElseThrow().status() == MissionStatus.CLAIM_PENDING_DISCOVERY,
                "The first claim did not pay and retain its receipt");
        helper.assertTrue(menu.status() == SatelliteOperationCode.REGISTRY_PENDING && table.at(key).isPresent(),
                "The paying claim lost its pending terminal DATA entry");
        var entry = table.at(key).orElseThrow();
        helper.assertTrue(entry.kind() == TerminalActionTable.Kind.DATA
                && entry.result() == SatelliteOperationCode.PENDING_DISCOVERY, "The original RAM result was lost");
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(failed.entered.getCount() == 0,
                    "Writer has not reached the held committer"))
                .thenExecute(() -> {
                    helper.assertTrue(!entry.covered(data) && celestial.get(PlanetaryContent.MARS).isEmpty(),
                            "Discovery or the terminal completed before the paid receipt was covered");
                    long before = data.mutationSequence();
                    helper.assertTrue(!terminal.handleButton(player, SatelliteTerminalMenu.BUTTON_CANCEL)
                            && data.mutationSequence() == before, "A conflicting button mutated the pending claim");
                    failed.release.countDown();
                })
                .thenWaitUntil(() -> helper.assertTrue(data.writerForTests().lastOutcome()
                        .filter(outcome -> outcome == SatelliteRegistryProof.Outcome.FAILED).isPresent(),
                        "The held attempt has not failed"))
                .thenExecute(() -> {
                    TerminalPendingEffects.complete(server);
                    helper.assertTrue(table.at(key).orElseThrow() == entry && !data.covers(paid)
                            && celestial.get(PlanetaryContent.MARS).isEmpty(), "Failure lost the stable pending entry or paid-receipt gate");
                    data.completeDue(now + 401); // A later clock mutation must not replace the entry's original token.
                    var later = data.token();
                    helper.assertTrue(later.sequence() > paid.sequence(), "The fixture did not advance the registry sequence");
                    helper.assertTrue(table.at(key).orElseThrow() == entry, "A later mutation replaced the original entry");
                    data.writePolicy(ATOMIC);
                    data.request(server);
                })
                .thenWaitUntil(() -> helper.assertTrue(table.at(key).isEmpty(), "The pending result has not been delivered"))
                .thenExecute(() -> {
                    helper.assertTrue(data.covers(paid) && entry.covered(data), "Delivery ran before the original claim was covered");
                    helper.assertTrue(table.at(key).isEmpty() && menu.status() == SatelliteOperationCode.PENDING_DISCOVERY,
                            "Coverage did not deliver the stored RAM result");
                    SatelliteRuntime.replayDiscoveriesForTests(server);
                    helper.assertTrue(celestial.get(PlanetaryContent.MARS).isPresent() && data.account(player.getUUID()).balance() == 20,
                            "Covered discovery did not recover once without another payment");
                }).thenSucceed();
    }

    @GameTest(template = "empty", batch = "satellite_terminal_survey_review", timeoutTicks = 300)
    public static void movedSurveyChipCannotStartElsewhereUntilCancellationIsAcknowledged(GameTestHelper helper) {
        var server = helper.getLevel().getServer();
        var data = SatelliteMissionSavedData.get(server);
        var first = place(helper, 1);
        var second = place(helper, 3);
        var player = player(helper, first);
        var chip = new SatelliteIdentity(UUID.randomUUID(), player.getUUID(), ModIdentity.id("survey_satellite"),
                SatelliteKind.SURVEY, List.of(id(ModItems.SATELLITE_CHASSIS.get()), id(ModItems.SURVEY_SCANNER_MODULE.get()),
                id(ModItems.SATELLITE_SOLAR_MODULE.get()), id(ModItems.DATA_STORAGE_UNIT.get()), id(ModItems.SATELLITE_BATTERY.get())));
        insert(helper, first, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, bound(ModItems.SATELLITE_CONTROL_CHIP.get(), chip));
        insert(helper, first, SatelliteTerminalBlockEntity.SLOT_PACKAGE, bound(ModItems.SATELLITE_PACKAGE.get(), chip));
        var firstMenu = new SatelliteTerminalMenu(1, player.getInventory(), first, SatelliteTerminalTargets.current());
        var secondMenu = new SatelliteTerminalMenu(2, player.getInventory(), second, SatelliteTerminalTargets.current());
        var table = SatelliteRuntime.terminalActions().orElseThrow();
        var key = new TerminalActionTable.Key(helper.getLevel().dimension(), first.getBlockPos());
        keys.add(key);
        var held = new HeldWrite(false);
        holds.add(held);
        first.handleButton(player, SatelliteTerminalMenu.BUTTON_LAUNCH);
        data.awaitForTests(server);
        TerminalPendingEffects.complete(server);
        helper.assertTrue(firstMenu.status() == SatelliteOperationCode.SUCCESS, "Survey fixture launch failed");
        first.handleButton(player, SatelliteTerminalMenu.BUTTON_LAUNCH);
        helper.assertTrue(firstMenu.status() == SatelliteOperationCode.SUCCESS, "Survey fixture start failed");
        UUID mission = data.satellite(chip.satelliteId()).orElseThrow().currentMissionId().orElseThrow();
        data.flush(server);
        data.writePolicy(held.policy);
        first.handleButton(player, SatelliteTerminalMenu.BUTTON_CANCEL);
        var cancelled = data.token();
        helper.startSequence().thenWaitUntil(() -> helper.assertTrue(held.entered.getCount() == 0,
                    "Cancellation write has not reached the held committer"))
                .thenExecute(() -> {
                    helper.assertTrue(firstMenu.status() == SatelliteOperationCode.REGISTRY_PENDING
                            && data.mission(mission).orElseThrow().status() == MissionStatus.CANCELLED, "Cancel did not enter its DATA barrier");
                    var moved = first.menuInventory().extractItem(SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, 1, false);
                    helper.assertTrue(!moved.isEmpty(), "A DATA barrier incorrectly locked the chip");
                    insert(helper, second, SatelliteTerminalBlockEntity.SLOT_CONTROL_CHIP, moved);
                    long before = data.mutationSequence();
                    for (int button : new int[] {SatelliteTerminalMenu.BUTTON_LAUNCH, SatelliteTerminalMenu.BUTTON_CLAIM,
                            SatelliteTerminalMenu.BUTTON_CANCEL, SatelliteTerminalMenu.BUTTON_UNLINK}) {
                        second.handleButton(player, button);
                        helper.assertTrue(secondMenu.status() == SatelliteOperationCode.REGISTRY_BUSY
                                && data.mutationSequence() == before, "A second terminal bypassed pending admission for button " + button);
                    }
                    helper.assertTrue(!data.covers(cancelled) && data.satellite(chip.satelliteId()).orElseThrow().currentMissionId().isEmpty(),
                            "A held cancellation admitted a successor survey");
                    held.release.countDown();
                })
                .thenWaitUntil(() -> helper.assertTrue(table.at(key).isEmpty(), "Cancellation result has not been delivered"))
                .thenExecute(() -> {
                    helper.assertTrue(data.covers(cancelled) && table.at(key).isEmpty(), "Cancellation did not acknowledge and deliver");
                    second.handleButton(player, SatelliteTerminalMenu.BUTTON_LAUNCH);
                    helper.assertTrue(secondMenu.status() == SatelliteOperationCode.SUCCESS
                            && data.satellite(chip.satelliteId()).orElseThrow().currentMissionId().isPresent(),
                            "The second terminal could not start after cancellation coverage");
                }).thenSucceed();
    }

    private static final class HeldWrite {
        final CountDownLatch entered = new CountDownLatch(1), release = new CountDownLatch(1);
        final CheckedSavedDataPolicy policy;
        HeldWrite(boolean fail) {
            policy = ATOMIC.withCommitter((staged, target) -> {
                entered.countDown();
                try { release.await(); } catch (InterruptedException stop) {
                    Thread.currentThread().interrupt(); throw new java.io.IOException(stop);
                }
                if (fail) { throw new SatelliteRegistryTestAwait.InjectedFailure(); }
                CheckedSavedDataFile.atomicMove(staged, target);
            });
        }
    }

    private static SatelliteTerminalBlockEntity place(GameTestHelper helper, int x) {
        var pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, ModBlocks.SATELLITE_TERMINAL.get());
        var terminal = (SatelliteTerminalBlockEntity) helper.getBlockEntity(pos);
        terminal.getCapability(ForgeCapabilities.ENERGY).orElseThrow(IllegalStateException::new)
                .receiveEnergy(SatelliteTerminalBlockEntity.ENERGY_CAPACITY, false);
        return terminal;
    }
    private static FakePlayer player(GameTestHelper helper, SatelliteTerminalBlockEntity terminal) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "BarrierReview"));
        var pos = terminal.getBlockPos();
        player.setPos(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        return player;
    }
    private static net.minecraft.resources.ResourceLocation id(Item item) { return ForgeRegistries.ITEMS.getKey(item); }
    private static ItemStack bound(Item item, SatelliteIdentity identity) {
        var stack = new ItemStack(item); SatelliteItemData.write(stack, identity); return stack;
    }
    private static void insert(GameTestHelper helper, SatelliteTerminalBlockEntity terminal, int slot, ItemStack stack) {
        helper.assertTrue(terminal.menuInventory().insertItem(slot, stack, false).isEmpty(), "Could not insert fixture slot " + slot);
    }
}
