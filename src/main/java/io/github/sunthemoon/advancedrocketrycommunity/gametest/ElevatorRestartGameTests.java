package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.ModIdentity;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.device.EndgameDevices;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorEndpointBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator.ElevatorRules;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.service.EndgameService;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitKey;
import java.util.ArrayList;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Joint current-format snapshots: both BEs, endpoint/pair/transit tables, and the memory-only ride lifecycle. */
@GameTestHolder(ModIdentity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ElevatorRestartGameTests {
    private ElevatorRestartGameTests() { }

    // F12: anchor outbox + terminal receive checkpoint reopened with the same pair/ledger, before and after arrival.
    @GameTest(template = "empty", batch = "w416_f12_cargo_up_reload", timeoutTicks = 2400)
    public static void f12BothEndpointAndRegistryReloadsDeliverAnchorCargoAtMostOnce(GameTestHelper helper) {
        cargo(helper, true);
    }

    // F12: the reverse direction exercises terminal outbox and anchor receive recovery with the same assertions.
    @GameTest(template = "empty", batch = "w416_f12_cargo_down_reload", timeoutTicks = 2400)
    public static void f12BothEndpointAndRegistryReloadsDeliverTerminalCargoAtMostOnce(GameTestHelper helper) {
        cargo(helper, false);
    }

    private static void cargo(GameTestHelper helper, boolean up) {
        ElevatorGameTests.Fixture f = new ElevatorGameTests.Fixture(helper);
        RestartTestSupport.cleanup(helper, f::close);
        TransitKey[] key = {null};
        ItemStack payload = new ItemStack(Items.IRON_INGOT, 3);
        payload.getOrCreateTag().putString("restart_fixture", "W416 cargo " + up);
        helper.startSequence().thenWaitUntil(() -> f.registered(helper)).thenExecute(() -> {
            f.bind(helper);
            f.anchor().storage().energy().set(200_000);
            f.terminal().storage().energy().set(200_000);
            // An unrelated tagged receive stack at each end must survive alongside the delivered payload.
            for (var endpoint : new ElevatorEndpointBlockEntity[] {f.anchor(), f.terminal()}) {
                ItemStack sentinel = new ItemStack(Items.GOLD_INGOT, 5);
                sentinel.getOrCreateTag().putString("restart_fixture", "W416 sentinel");
                endpoint.storage().receive().setStackInSlot(8, sentinel);
            }
            source(f, up).storage().input().setStackInSlot(0, payload.copy());
            source(f, up).launchForTest(f.owner);
            key[0] = new TransitKey(source(f, up).endpointId(), 1L);
            helper.assertTrue(source(f, up).source().outbox().size() == 1
                    && source(f, up).storage().energy().energy() == 180_000, "Cargo cut lacks the exact paid escrow");
        }).thenWaitUntil(() -> {
            saved(f);
            helper.assertTrue(service().root().orElseThrow().transits().record(key[0]).isPresent(),
                    "Cargo was not registered before the restart");
        }).thenExecute(() -> {
            service().barrier(root -> null);
            var record = service().root().orElseThrow().transits().record(key[0]).orElseThrow();
            helper.assertTrue(record.state() == io.github.sunthemoon.advancedrocketrycommunity.endgame.transit.TransitRecord.State.IN_TRANSIT,
                    "Restart cut is not in transit");
            reopen(helper, f);
            helper.assertTrue(service().root().orElseThrow().transits().record(key[0]).orElseThrow().equals(record),
                    "Registry reload changed the transit payload, debit, destination or arrival tick");
        }).thenWaitUntil(() -> {
            saved(f);
            helper.assertTrue(ItemStack.matches(destination(f, up).storage().receive().getStackInSlot(0), payload),
                    "Restored transit did not deliver the exact tagged payload");
        }).thenExecute(() -> {
            service().barrier(root -> null);
            // Reopen again with an already materialized receive buffer and its durable claim/receipt history.
            reopen(helper, f);
            inventory(helper, f, up, payload);
        }).thenExecuteAfter(ElevatorRules.CARGO_TRAVEL_TICKS + 45, () -> {
            saved(f);
            service().barrier(root -> null);
            inventory(helper, f, up, payload);
            helper.assertTrue(source(f, up).source().outbox().isEmpty()
                    && destination(f, up).destination().incoming().isEmpty(), "Reload left cargo available to replay");
        }).thenSucceed();
    }

    // F12: ADR-059 has no passenger journal; a pending ride is forgotten, a newly consented ride arrives once.
    @GameTest(template = "empty", batch = "w416_f12_ride_reload", timeoutTicks = 2400)
    public static void f12EndpointPairAndRideRestartDiscardsPendingAndDoesNotReplayCommittedArrival(GameTestHelper helper) {
        ElevatorGameTests.Fixture f = new ElevatorGameTests.Fixture(helper);
        ServerPlayer[] rider = {null}; int[] teleports = {0};
        Consumer<EndgameEffectEvent> effects = event -> {
            if (event.effect() == EndgameEffect.TELEPORT && event.actorId().filter(f.owner::equals).isPresent()) {
                teleports[0]++;
            }
        };
        MinecraftForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, EndgameEffectEvent.class, effects);
        RestartTestSupport.cleanup(helper, () -> {
            MinecraftForge.EVENT_BUS.unregister(effects);
            devices().elevatorRides().clear(f.server);
            if (rider[0] != null) { f.server.getPlayerList().remove(rider[0]); }
            f.close();
        });
        helper.startSequence().thenWaitUntil(() -> f.registered(helper)).thenExecute(() -> {
            f.bind(helper);
            f.anchor().storage().energy().set(200_000);
            f.terminal().storage().energy().set(210_000);
            rider[0] = ConnectedTestPlayers.join(f.server, f.owner, "elevatorRestart", f.level,
                    f.anchorPos.above(), new ArrayList<>());
            helper.assertTrue(devices().elevatorRides().request(rider[0], f.anchor(), service(), devices()).code()
                    == EndgameCode.RIDE_COUNTDOWN, "No pending ride at the restart cut");
            helper.assertTrue(devices().elevatorRides().size() == 1 && tickets(f) == 1,
                    "Pending ride did not hold exactly one arrival ticket");
            reopen(helper, f);
            helper.assertTrue(devices().elevatorRides().pending(f.owner).isEmpty() && tickets(f) == 0,
                    "A memory-only ride/ticket survived the stop");
        }).thenExecuteAfter(ElevatorRules.RIDE_COUNTDOWN_TICKS + 5, () -> {
            helper.assertTrue(rider[0].level() == f.level && f.anchor().onPlatform(rider[0]) && teleports[0] == 0
                    && f.anchor().storage().energy().energy() == 200_000,
                    "Discarded ride arrived or debited after its original due tick");
            helper.assertTrue(devices().elevatorRides().request(rider[0], f.anchor(), service(), devices()).code()
                    == EndgameCode.RIDE_COUNTDOWN, "Restored pair refused a fresh ride");
        }).thenWaitUntil(() -> helper.assertTrue(rider[0].level() == f.space && f.terminal().onPlatform(rider[0]),
                "Fresh ride did not reach the restored terminal"))
                .thenExecute(() -> {
                    helper.assertTrue(teleports[0] == 1 && f.anchor().storage().energy().energy() == 150_000
                            && f.terminal().storage().energy().energy() == 210_000 && tickets(f) == 0,
                            "Ride did not arrive/debit exactly once");
                    reopen(helper, f);
                }).thenExecuteAfter(ElevatorRules.RIDE_COUNTDOWN_TICKS + 5, () -> {
                    helper.assertTrue(teleports[0] == 1 && rider[0].level() == f.space && f.terminal().onPlatform(rider[0])
                            && f.anchor().storage().energy().energy() == 150_000
                            && f.terminal().storage().energy().energy() == 210_000
                            && devices().elevatorRides().size() == 0 && tickets(f) == 0,
                            "Committed ride replayed after coordinated BE/registry restore");
                }).thenSucceed();
    }

    /** Saves current stores, drops runtime references, then installs decoded objects as the real service authority. */
    private static void reopen(GameTestHelper helper, ElevatorGameTests.Fixture f) {
        var oldAnchor = f.anchor(); var oldTerminal = f.terminal();
        CompoundTag anchor = oldAnchor.saveWithFullMetadata(), terminal = oldTerminal.saveWithFullMetadata();
        var pair = service().root().orElseThrow().pairs().forStation(f.stationId).orElseThrow();
        service().barrier(root -> null);
        CompoundTag registry = EndgameSavedData.get(f.server).save(new CompoundTag());
        devices().elevatorRides().clear(f.server); // Actual stop semantics: there is deliberately no ride NBT.
        f.level.removeBlockEntity(f.anchorPos);
        f.space.removeBlockEntity(f.terminalPos);
        service().clear();
        devices().clear();
        EndgameSavedData restored = EndgameSavedData.load(registry);
        helper.assertTrue(restored.operational(), "Endgame registry did not reopen");
        f.server.overworld().getDataStorage().set(EndgameSavedData.DATA_NAME, restored);
        service().onServerStarted(new ServerStartedEvent(f.server));
        RestartTestSupport.replace(f.level, f.anchorPos, anchor);
        RestartTestSupport.replace(f.space, f.terminalPos, terminal);
        helper.assertTrue(oldAnchor.isRemoved() && oldTerminal.isRemoved()
                && f.anchor() != oldAnchor && f.terminal() != oldTerminal,
                "Both endpoint block entities were not deserialized anew");
        helper.assertTrue(anchor.equals(f.anchor().saveWithFullMetadata())
                && terminal.equals(f.terminal().saveWithFullMetadata()),
                "Endpoint reload changed identity, owner, inventory, energy or transit checkpoint");
        helper.assertTrue(service().root().orElseThrow().pairs().forStation(f.stationId).orElseThrow().equals(pair),
                "Reopened pair differs from its snapshot");
    }

    private static void inventory(GameTestHelper helper, ElevatorGameTests.Fixture f, boolean up, ItemStack payload) {
        for (var endpoint : new ElevatorEndpointBlockEntity[] {f.anchor(), f.terminal()}) {
            for (int slot = 0; slot < endpoint.storage().input().getSlots(); slot++) {
                helper.assertTrue(endpoint.storage().input().getStackInSlot(slot).isEmpty(), "Cargo input replayed");
            }
            for (int slot = 0; slot < endpoint.storage().receive().getSlots(); slot++) {
                ItemStack actual = endpoint.storage().receive().getStackInSlot(slot);
                if (slot == 8) {
                    helper.assertTrue(actual.is(Items.GOLD_INGOT) && actual.getCount() == 5
                            && actual.getTag() != null && actual.getTag().getString("restart_fixture").equals("W416 sentinel"),
                            "Reload changed unrelated inventory");
                } else if (slot == 0 && endpoint == destination(f, up)) {
                    helper.assertTrue(ItemStack.matches(actual, payload), "Cargo duplicated or lost count/tag");
                } else { helper.assertTrue(actual.isEmpty(), "Unexpected extra cargo output"); }
            }
        }
        helper.assertTrue(source(f, up).storage().energy().energy() == 180_000
                && destination(f, up).storage().energy().energy() == 200_000,
                "Restored cargo did not preserve exactly one 20000 FE debit");
    }

    private static int tickets(ElevatorGameTests.Fixture f) {
        String name = "advancedrocketrycommunity:elevator_arrival";
        return TicketCounts.near(f.level, new ChunkPos(f.anchorPos), 2).getOrDefault(name, 0)
                + TicketCounts.near(f.space, new ChunkPos(f.terminalPos), 2).getOrDefault(name, 0);
    }
    private static void saved(ElevatorGameTests.Fixture f) {
        LaserTargetGameTests.chunkSaved(f.level, f.anchorPos);
        LaserTargetGameTests.chunkSaved(f.space, f.terminalPos);
    }
    private static ElevatorEndpointBlockEntity source(ElevatorGameTests.Fixture f, boolean up) {
        return up ? f.anchor() : f.terminal();
    }
    private static ElevatorEndpointBlockEntity destination(ElevatorGameTests.Fixture f, boolean up) {
        return up ? f.terminal() : f.anchor();
    }
    private static EndgameService service() { return EndgameRuntime.service().orElseThrow(); }
    private static EndgameDevices devices() { return EndgameRuntime.devices().orElseThrow(); }
}
