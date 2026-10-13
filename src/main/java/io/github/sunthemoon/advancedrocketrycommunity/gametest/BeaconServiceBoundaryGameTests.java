package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconServices;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStructure;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconIndexSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconStateCodec;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.service.CelestialCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.MultiblockPatternCatalog;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.service.MultiblockPatternCatalogManager;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.OnDatapackSyncEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native preservation, unavailable services, exact validation budgets, and the installed Forge event listeners. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeaconServiceBoundaryGameTests {
    private BeaconServiceBoundaryGameTests() {
    }

    @GameTest(template = "empty", batch = "beacon_retained_native", timeoutTicks = 40)
    public static void refusedBlockEntityRootsSurviveNativeLoadAndSave(GameTestHelper helper) {
        try (BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper)) {
            BlockPos at = fixture.controllerAt(210);
            PlanetaryBeaconBlockEntity beacon = fixture.placeStack(null, at, new ItemStack(ModItems.PLANETARY_BEACON.get()));
            CompoundTag future = BeaconStateCodec.encode(BeaconStateCodec.State.fresh().withOwner(UUID.randomUUID()));
            future.putInt("schema_version", 99);
            CompoundTag extra = BeaconStateCodec.encode(BeaconStateCodec.State.fresh());
            extra.putString("unknown", "preserve");
            for (Tag raw : List.of(future, extra, StringTag.valueOf("not a compound"))) {
                CompoundTag root = new CompoundTag();
                root.put(BeaconStateCodec.KEY, raw.copy());
                beacon.load(root);
                PlanetaryBeaconBlockEntity.serverTick(fixture.level, at, beacon.getBlockState(), beacon);
                helper.assertTrue(beacon.status() == BeaconStatus.INVALID_DATA && beacon.beaconId().isEmpty()
                        && beacon.owner().isEmpty() && !beacon.live(), "A refused root became operational");
                CompoundTag nativeSave = beacon.saveWithFullMetadata();
                helper.assertTrue(raw.equals(nativeSave.get(BeaconStateCodec.KEY))
                        && raw.equals(beacon.saveWithoutMetadata().get(BeaconStateCodec.KEY)), "saveAdditional changed a refused root");
                BlockEntity loaded = BlockEntity.loadStatic(at, beacon.getBlockState(), nativeSave);
                helper.assertTrue(loaded instanceof PlanetaryBeaconBlockEntity restored
                        && restored.status() == BeaconStatus.INVALID_DATA && restored.beaconId().isEmpty()
                        && raw.equals(restored.saveWithFullMetadata().get(BeaconStateCodec.KEY)), "Native load/save normalized the retained root");
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "beacon_missing_body", timeoutTicks = 100)
    public static void missingBodyRefusesNewRegistrationAndKeepsTheRegisteredBeaconsLastBody(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            ServerPlayer owner = fixture.join("bodyBoundaryOwner", fixture.standingBefore(at));
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity registered = fixture.place(owner, at, Optional.of("Last body"));
            fixture.await(40, "the beacon's initial body registration", registered::live, () -> {
                BeaconRecord before = fixture.index().get(registered.beaconId().orElseThrow()).orElseThrow();
                BlockPos newAt = at.east(5);
                fixture.buildStructure(newAt);
                PlanetaryBeaconBlockEntity fresh = fixture.place(owner, newAt, Optional.empty());
                BeaconServices missingBody = isolatedServices(fixture);
                try {
                    usingServices(missingBody, () -> {
                        helper.assertTrue(missingBody.body(fixture.server, fixture.level.dimension(), at).isEmpty(), "The missing-body fixture resolves a body");
                        PlanetaryBeaconBlockEntity.serverTick(fixture.level, newAt, fresh.getBlockState(), fresh);
                        endTick(fixture, missingBody);
                        PlanetaryBeaconBlockEntity.serverTick(fixture.level, newAt, fresh.getBlockState(), fresh);
                        helper.assertTrue(fresh.formed() && fresh.status() == BeaconStatus.BODY_UNAVAILABLE
                                && fixture.index().get(fresh.beaconId().orElseThrow()).isEmpty(), "A missing body admitted a new beacon");
                        fixture.use(owner, at, ItemStack.EMPTY);
                        BeaconRecord disabled = fixture.index().get(before.beaconId()).orElseThrow();
                        helper.assertTrue(registered.status() == BeaconStatus.DISABLED && !disabled.active()
                                && disabled.body().equals(before.body()), "A missing body lost the registered beacon's last body");
                        fixture.use(owner, at, ItemStack.EMPTY);
                        helper.assertTrue(registered.live() && fixture.index().get(before.beaconId()).orElseThrow().body().equals(before.body())
                                && missingBody.finder().snapshot(owner).markers().isEmpty(), "The missing-body finder resolved markers or lost the record");
                    });
                } finally {
                    missingBody.clear();
                }
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_index_unavailable", timeoutTicks = 100)
    public static void anUnavailableIndexBlocksBeaconUpdatesAndClearsTheFinderWithoutChangingTheRoot(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            BlockPos at = fixture.controllerAt(210);
            ServerPlayer owner = fixture.join("indexBoundaryOwner", fixture.standingBefore(at));
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity beacon = fixture.place(owner, at, Optional.of("Unavailable index"));
            fixture.await(40, "the beacon to register", beacon::live, () -> {
                BeaconIndexSavedData original = fixture.index();
                BeaconRecord before = original.get(beacon.beaconId().orElseThrow()).orElseThrow();
                BeaconFinderService finder = new BeaconFinderService((player, snapshot) -> { }, fixture.services());
                owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BEACON_FINDER.get()));
                helper.assertTrue(finder.evaluate(owner, 1000).filter(snapshot -> snapshot.markers().size() == 1).isPresent(), "The finder has no baseline marker");
                CompoundTag refusedRoot = new CompoundTag();
                refusedRoot.putInt("schema_version", 99);
                BeaconIndexSavedData unavailable = BeaconIndexSavedData.load(refusedRoot);
                fixture.server.overworld().getDataStorage().set(BeaconIndexSavedData.NAME, unavailable);
                try {
                    fixture.use(owner, at, ItemStack.EMPTY);
                    helper.assertTrue(beacon.status() == BeaconStatus.INDEX_UNAVAILABLE && !beacon.live()
                            && original.get(before.beaconId()).equals(Optional.of(before)), "An unavailable index admitted a beacon update");
                    helper.assertTrue(finder.snapshot(owner).markers().isEmpty()
                            && finder.evaluate(owner, 1020).equals(Optional.of(BeaconMarkerSnapshotPacket.EMPTY))
                            && finder.evaluate(owner, 1040).isEmpty(), "The unavailable-index finder did not clear exactly once");
                    helper.assertTrue(!unavailable.operational() && !unavailable.isDirty() && unavailable.size() == 0
                            && unavailable.save(new CompoundTag()) == refusedRoot, "The unavailable index changed its retained root");
                } finally {
                    fixture.server.overworld().getDataStorage().set(BeaconIndexSavedData.NAME, original);
                }
                fixture.succeed();
            });
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    @GameTest(template = "empty", batch = "beacon_validation_budget", timeoutTicks = 40)
    public static void nineStructuresShareEightSlotsAndThePeriodicRecheckIsDueAtTwoHundredTicks(GameTestHelper helper) {
        try (BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper)) {
            BlockPos at = fixture.controllerAt(210);
            fixture.buildStructure(at);
            PlanetaryBeaconBlockEntity beacon = fixture.placeStack(null, at, new ItemStack(ModItems.PLANETARY_BEACON.get()));
            BeaconServices services = isolatedServices(fixture);
            try {
                List<BeaconStructure> structures = new ArrayList<>();
                List<UUID> ids = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    BeaconStructure structure = new BeaconStructure(beacon.getBlockState().getBlock());
                    UUID id = new UUID(0, i + 1);
                    structures.add(structure);
                    ids.add(id);
                    structure.tick(fixture.level, at, Direction.NORTH, id, services, 100);
                    helper.assertTrue(!structure.consumeValidated(), "Validation ran without a next-tick grant");
                }
                endTick(fixture, services);
                int first = 0;
                for (int i = 0; i < 9; i++) {
                    structures.get(i).tick(fixture.level, at, Direction.NORTH, ids.get(i), services, 101);
                    if (structures.get(i).consumeValidated()) {
                        first++;
                    }
                }
                helper.assertTrue(first == 8 && services.validations().servedLastTick() == 8,
                        "The beacon services did not enforce eight validations per tick: " + first);
                endTick(fixture, services);
                int second = 0;
                for (int i = 0; i < 9; i++) {
                    structures.get(i).tick(fixture.level, at, Direction.NORTH, ids.get(i), services, 102);
                    if (structures.get(i).consumeValidated()) {
                        second++;
                    }
                }
                helper.assertTrue(second == 1 && structures.stream().allMatch(s -> s.status() == BeaconStructure.Status.FORMED),
                        "The ninth beacon did not receive the next tick's slot");
                // These isolated services are not event-bus listeners: this change has no dirty notification there.
                fixture.set(BeaconGameTestFixture.capOf(at), Blocks.AIR.defaultBlockState());
                BeaconStructure firstStructure = structures.get(0);
                endTick(fixture, services);
                firstStructure.tick(fixture.level, at, Direction.NORTH, ids.get(0), services, 300);
                helper.assertTrue(!firstStructure.consumeValidated() && firstStructure.status() == BeaconStructure.Status.FORMED,
                        "The periodic check ran before 200 elapsed ticks");
                firstStructure.tick(fixture.level, at, Direction.NORTH, ids.get(0), services, 301);
                helper.assertTrue(!firstStructure.consumeValidated(), "The due recheck bypassed its budget grant");
                endTick(fixture, services);
                firstStructure.tick(fixture.level, at, Direction.NORTH, ids.get(0), services, 302);
                helper.assertTrue(firstStructure.consumeValidated() && firstStructure.status() == BeaconStructure.Status.UNFORMED
                        && services.validations().servedLastTick() == 1, "The 200-tick recheck did not detect an unnotified change");
            } finally {
                services.clear();
            }
            helper.succeed();
        }
    }

    @GameTest(template = "empty", batch = "beacon_event_wiring", timeoutTicks = 40)
    public static void installedLogoutDimensionRespawnAndDatapackListenersResetTheFinderStream(GameTestHelper helper) {
        try (BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper)) {
            BlockPos standing = fixture.standingBefore(fixture.controllerAt(210));
            ServerPlayer owner = fixture.join("beaconEventOwner", standing);
            fixture.record(owner, fixture.unloaded(0), fixture.body(standing), true, "Events");
            owner.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.BEACON_FINDER.get()));
            BeaconFinderService finder = fixture.services().finder();
            long now = 2_000_000;
            helper.assertTrue(finder.evaluate(owner, now).isPresent() && finder.evaluate(owner, now + 20).isEmpty(), "The baseline finder stream is not stable");
            List<Runnable> events = List.of(
                    () -> MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(owner, Level.OVERWORLD, Level.NETHER)),
                    () -> MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerRespawnEvent(owner, false)),
                    () -> MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(fixture.server.getPlayerList(), owner)),
                    () -> MinecraftForge.EVENT_BUS.post(new OnDatapackSyncEvent(fixture.server.getPlayerList(), null)));
            now += 20;
            for (Runnable event : events) {
                event.run();
                helper.assertTrue(finder.evaluate(owner, now + 1).isEmpty(), "An event bypassed the 20-tick evaluation interval");
                now += 20;
                helper.assertTrue(finder.evaluate(owner, now).filter(snapshot -> snapshot.markers().size() == 1).isPresent(), "An installed event listener did not resend the unchanged marker");
                now += 20;
                helper.assertTrue(finder.evaluate(owner, now).isEmpty(), "An event caused duplicate resends");
            }
            int tracked = finder.tracked();
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(owner));
            helper.assertTrue(finder.tracked() == tracked - 1 && finder.evaluate(owner, now + 1).isPresent(), "The installed logout listener did not forget the player's interval and snapshot");
            helper.succeed();
        }
    }

    private static BeaconServices isolatedServices(BeaconGameTestFixture fixture) {
        MultiblockPatternCatalogManager patterns = new MultiblockPatternCatalogManager();
        patterns.accept(MultiblockPatternCatalog.create(List.of(fixture.services().pattern().orElseThrow())));
        return new BeaconServices(patterns, new CelestialCatalogManager(), (player, snapshot) -> { });
    }

    private static void endTick(BeaconGameTestFixture fixture, BeaconServices services) {
        services.onServerTick(new TickEvent.ServerTickEvent(TickEvent.Phase.END, () -> true, fixture.server));
    }

    /** Synchronous fixture-only service substitution; restored before returning or yielding to a server tick. */
    private static void usingServices(BeaconServices services, Runnable action) {
        try {
            Field field = BeaconRuntime.class.getDeclaredField("services");
            field.setAccessible(true);
            Object original = field.get(null);
            field.set(null, services);
            try {
                action.run();
            } finally {
                field.set(null, original);
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot substitute the beacon services for the synchronous fixture", exception);
        }
    }
}
