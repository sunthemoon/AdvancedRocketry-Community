package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.BeaconStatus;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.PlanetaryBeaconBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.finder.BeaconFinderService;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.model.BeaconRecord;
import io.github.sunthemoon.advancedrocketrycommunity.beacon.persistence.BeaconStateCodec;
import io.github.sunthemoon.advancedrocketrycommunity.classiccontrol.network.BeaconMarkerSnapshotPacket;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModItems;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Native chunk lifecycle: unloading and the budget-delayed first validation keep the last active record. */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BeaconReloadGameTests {
    private BeaconReloadGameTests() {
    }

    @GameTest(template = "empty", batch = "beacon_native_reload", timeoutTicks = 600)
    public static void unloadAndPendingReloadKeepTheActiveRecordAndFinderMarker(GameTestHelper helper) {
        BeaconGameTestFixture fixture = new BeaconGameTestFixture(helper);
        try {
            ServerPlayer owner = fixture.join("beaconReload", fixture.standingBefore(fixture.controllerAt(210)));
            BlockPos far = fixture.unloaded(0);
            // Keep the entire pattern inside one isolated chunk, away from every player's view tickets.
            BlockPos at = new BlockPos((far.getX() & ~15) + 8, 210, (far.getZ() & ~15) + 8);
            helper.assertTrue(fixture.level.getWorldBorder().isWithinBounds(at), "The isolated fixture is outside the world border");
            fixture.forceChunk(at, true);
            awaitTickReady(fixture, at, () -> lifecycle(fixture, owner, at));
        } catch (RuntimeException | Error failure) {
            fixture.close();
            throw failure;
        }
    }

    private static void lifecycle(BeaconGameTestFixture fixture, ServerPlayer owner, BlockPos at) {
        GameTestHelper helper = fixture.helper;
        fixture.body(at);
        fixture.buildStructure(at);
        PlanetaryBeaconBlockEntity original = fixture.placeStack(null, at, new ItemStack(ModItems.PLANETARY_BEACON.get()));
        UUID id = original.beaconId().orElseThrow();
        CompoundTag saved = new CompoundTag();
        saved.put(BeaconStateCodec.KEY, BeaconStateCodec.encode(new BeaconStateCodec.State(id,
                Optional.of(owner.getUUID()), "Reload", true, 0)));
        original.load(saved);
        BeaconFinderService finder = new BeaconFinderService((player, snapshot) -> { }, fixture.services());
        int[] attempts = {40};
        fixture.await(40, "the isolated beacon to form", () -> {
            if (--attempts[0] == 0 && !original.live()) {
                helper.assertTrue(false, "The isolated beacon did not form: status=" + original.status()
                        + ", currentInstance=" + (fixture.level.getBlockEntity(at) == original)
                        + ", tickReady=" + tickReady(fixture, at));
            }
            return original.live();
        }, () -> {
            BeaconRecord before = fixture.index().get(id).orElseThrow();
            fixture.index().setDirty(false);
            fixture.forceChunk(at, false);
            fixture.await(300, "the isolated chunk to unload", () -> !fixture.level.hasChunkAt(at), () -> {
                helper.assertTrue(original.isRemoved() && fixture.index().get(id).equals(Optional.of(before))
                        && !fixture.index().isDirty(), "Unloading changed or rewrote the active index record");
                marker(helper, finder.snapshot(owner), id, false);
                helper.assertTrue(!fixture.level.hasChunkAt(at), "The finder loaded the beacon chunk");
                fixture.forceChunk(at, true);
                PlanetaryBeaconBlockEntity reloaded = (PlanetaryBeaconBlockEntity) fixture.level.getBlockEntity(at);
                helper.assertTrue(reloaded != original && reloaded.beaconId().equals(Optional.of(id))
                        && reloaded.status() == BeaconStatus.PENDING, "The native reload did not restore a pending beacon");
                // No grant survives the unloaded interval: the first ticker only requests validation.
                PlanetaryBeaconBlockEntity.serverTick(fixture.level, at, reloaded.getBlockState(), reloaded);
                helper.assertTrue(reloaded.status() == BeaconStatus.PENDING
                        && fixture.index().get(id).equals(Optional.of(before)) && !fixture.index().isDirty(),
                        "The first pending ticker rewrote the active record");
                marker(helper, finder.snapshot(owner), id, false);
                awaitTickReady(fixture, at, () -> fixture.await(40, "the reloaded beacon to validate", reloaded::live, () -> {
                    BeaconRecord after = fixture.index().get(id).orElseThrow();
                    helper.assertTrue(after.active() && after.generation() == before.generation() + 1
                            && fixture.index().isDirty(), "Validation did not write the new active generation once");
                    marker(helper, finder.snapshot(owner), id, true);
                    fixture.index().setDirty(false);
                    helper.runAfterDelay(5, () -> {
                        try {
                            helper.assertTrue(fixture.index().get(id).equals(Optional.of(after))
                                    && !fixture.index().isDirty(), "The reload caused another index rewrite");
                            fixture.succeed();
                        } catch (RuntimeException | Error failure) {
                            fixture.close();
                            throw failure;
                        }
                    });
                }));
            });
        });
    }

    /** FULL alone is not ticker readiness: the native ticker also requires loaded entity storage. */
    private static boolean tickReady(BeaconGameTestFixture fixture, BlockPos at) {
        return fixture.level.shouldTickBlocksAt(ChunkPos.asLong(at))
                && fixture.level.getChunkAt(at).getFullStatus().isOrAfter(FullChunkStatus.BLOCK_TICKING)
                && fixture.level.areEntitiesLoaded(ChunkPos.asLong(at));
    }

    private static void awaitTickReady(BeaconGameTestFixture fixture, BlockPos at, Runnable next) {
        int[] attempts = {40};
        GameTestTickPacer pacer = new GameTestTickPacer(fixture.server);
        fixture.await(40, "the fixture chunk's native ticker readiness", () -> {
            boolean ready = tickReady(fixture, at);
            if (!ready) {
                // Entity sections load asynchronously; give each unchanged tick the normal server's 50 ms.
                pacer.pace();
            }
            if (--attempts[0] == 0 && !ready) {
                fixture.helper.assertTrue(false, "Fixture chunk is not ticker-ready: status="
                        + fixture.level.getChunkAt(at).getFullStatus() + ", entities="
                        + fixture.level.areEntitiesLoaded(ChunkPos.asLong(at)) + ", blockTick="
                        + fixture.level.shouldTickBlocksAt(ChunkPos.asLong(at)));
            }
            return ready;
        }, next);
    }

    private static void marker(GameTestHelper helper, BeaconMarkerSnapshotPacket snapshot, UUID id, boolean live) {
        helper.assertTrue(snapshot.markers().size() == 1 && snapshot.markers().get(0).beaconId().equals(id)
                && snapshot.markers().get(0).live() == live, "The finder lost the beacon or reported wrong liveness: " + snapshot);
    }
}
