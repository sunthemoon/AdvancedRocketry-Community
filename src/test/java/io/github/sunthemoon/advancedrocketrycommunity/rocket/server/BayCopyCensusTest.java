package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.google.common.collect.ImmutableList;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayCopyCensusTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void exactSourceIsNotACopyAndAbsentExactSourceNeverElectsADestination() {
        var f = new Fixture();
        var source = f.add(f.before.sourceSnapshot(), SOURCE);
        assertEquals(List.of(), f.scan().copies()); assertNull(f.scan().refusal());
        f.source.lookup.remove(source); f.source.sections.getOrCreateSection(SectionPos.asLong(0, 8, 0)).remove(source);
        // A fresh fixture has no exact source at all; only the destination is a non-survivor.
        var absent = new Fixture(); var destination = absent.add(absent.before.destinationSnapshot(), new UUID(185, 1));
        var scan = absent.scan(); assertNull(scan.refusal());
        assertEquals(List.of(new BayCopyCensus.Copy(destination, true)), scan.copies());
    }

    @Test void beforeAndAfterAtBothEndsUseTheirOwnHashPairs() {
        var f = new Fixture(); f.add(f.before.sourceSnapshot(), SOURCE);
        f.add(f.before.sourceSnapshot(), new UUID(185, 1)); f.add(f.after.source(), new UUID(185, 2));
        f.add(f.before.destinationSnapshot(), new UUID(185, 3)); f.add(f.after.destination(), new UUID(185, 4));
        var scan = f.scan(); assertNull(scan.refusal()); assertEquals(4, scan.copies().size());
        assertEquals(List.of(false, false, true, true), scan.copies().stream().map(BayCopyCensus.Copy::destination).toList());
    }

    @Test void wrongOwnerHashPositionAndPhysicalEndAreNeverRecognized() {
        for (int mutation = 0; mutation < 4; mutation++) {
            var f = new Fixture(); var copy = f.add(f.before.sourceSnapshot(), new UUID(185, mutation));
            switch (mutation) {
                case 0 -> UnstartedServer.set(RocketEntity.class, copy, "ownerId", new UUID(999, 1));
                case 1 -> UnstartedServer.set(RocketEntity.class, copy, "snapshot", record(2).sourceSnapshot());
                case 2 -> UnstartedServer.set(Entity.class, copy, "position", new Vec3(EARTH.x() + .75, EARTH.y(), EARTH.z() + .5));
                default -> UnstartedServer.set(RocketEntity.class, copy, "snapshot", f.before.destinationSnapshot());
            }
            assertEquals("UNEXPECTED_COPY", f.scan().refusal(), "mutation " + mutation);
        }
    }

    @Test void aPreviouslyRecognizedCopyMustBeRecheckedAfterCallbackMutations() {
        var f = new Fixture(); var rocket = f.add(f.before.sourceSnapshot(), new UUID(185, 9));
        var copy = f.scan().copies().get(0);
        assertTrue(BayCopyCensus.recognizes(f.server, f.before, f.group, copy));
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", new UUID(185, 10));
        assertFalse(BayCopyCensus.recognizes(f.server, f.before, f.group, copy));
    }

    @Test void transitAuthorityIncludesTheDurableManifestNotJustItsTransferAndPosition() {
        var f = new Fixture(); var source = f.add(f.before.sourceSnapshot(), SOURCE);
        assertTrue(BayConsumptionSteps.inTransit(source, f.before));
        var flight = source.flightData().orElseThrow();
        UnstartedServer.set(RocketEntity.class, source, "flightData",
                flight.withPassengers(flight.passengers().assign(new UUID(185, 11)).orElseThrow()));
        assertFalse(BayConsumptionSteps.inTransit(source, f.before));
    }

    @Test void anExactUuidOutsideItsRecordedBoxIsNotRebuiltOrIgnored() {
        var f = new Fixture(); var source = f.add(f.before.sourceSnapshot(), SOURCE);
        UnstartedServer.set(Entity.class, source, "position", new Vec3(1000, 128, 1000));
        UnstartedServer.set(Entity.class, source, "bb", new AABB(999, 128, 999, 1001, 130, 1001));
        assertFalse(BayCopyCensus.within(f.server, f.before, source));
        assertEquals("SOURCE_AUTHORITY_CONFLICT", f.scan().refusal());
    }

    @Test void joinNotificationRefusesAnEndWhoseCheckedMarginOverflows() {
        var f = new Fixture(BayAbsenceCensusTest.relocate(Integer.MAX_VALUE - 2, 8, 72, 8));
        var source = f.add(f.before.sourceSnapshot(), SOURCE);
        assertFalse(assertDoesNotThrow(() -> BayCopyCensus.within(f.server, f.before, source)));
    }

    @Test void aRecordedOriginOutsideTheRectangleIsQueriedWithoutWideningThatRectangle() {
        var f = new Fixture(); var original = f.before.sourceSnapshot();
        var blocks = original.blocks().stream().map(block -> new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock(
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(block.position().x() + 16,
                        block.position().y(), block.position().z()), block.state(), block.blockEntityPayload().orElse(null))).toList();
        var anchors = original.passengerAnchors().stream().map(pos ->
                new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition(pos.x() + 16, pos.y(), pos.z())).toList();
        var source = RocketStructureSnapshot.create(original.snapshotId(), original.sourceDimension(), original.sourceOrigin(), blocks,
                anchors, original.stats(), 0);
        var destination = source.relocated(f.before.destinationSnapshot().snapshotId(), f.before.destinationSnapshot().sourceDimension(), MOON, 160);
        var record = RocketTransferRecord.create(TRANSFER, LOGICAL, OWNER, SOURCE, source, destination,
                f.before.sourceFlightData(), f.before.destinationFlightData(), f.before.requiredFuel(), 0);
        var group = new BayGroup(LOGICAL, TRANSFER, f.group.members(), source.contentHash(), destination.contentHash(),
                f.group.srcAfter(), f.group.dstAfter());
        assertFalse(io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketDiscoveryColumns.of(source).containsOrigin());
        f.add(source, SOURCE); var copy = f.add(destination, new UUID(185, 15));
        var scan = BayCopyCensus.inspect(f.server, record, group, rocket -> true);
        assertNull(scan.refusal()); assertEquals(List.of(new BayCopyCensus.Copy(copy, true)), scan.copies());
    }

    @Test void sourceUuidAtDestinationAndBlockedImagesHoldBeforeNeutralityChecks() {
        var f = new Fixture(); f.add(f.before.destinationSnapshot(), SOURCE);
        assertEquals("UNEXPECTED_COPY", f.scan().refusal());
        var blocked = new Fixture(); var copy = blocked.add(blocked.before.destinationSnapshot(), new UUID(185, 7));
        UnstartedServer.set(RocketEntity.class, copy, "snapshot", null);
        UnstartedServer.set(RocketEntity.class, copy, "assemblyTransactionId", null);
        UnstartedServer.set(RocketEntity.class, copy, "flightData", null);
        var data = new CompoundTag(); data.putUUID("assembly_transaction_id", LOGICAL);
        UnstartedServer.set(RocketEntity.class, copy, "preservedBlockedData", data);
        assertEquals("UNEXPECTED_COPY", blocked.scan(rocket -> fail("An unexpected image reached escrow")).refusal());
    }

    @Test void protectedCopiesHoldAndTheCapCountsBothEndsIncludingTheExactSource() {
        var protectedCopy = new Fixture(); protectedCopy.add(protectedCopy.before.sourceSnapshot(), new UUID(185, 1));
        assertEquals("PROTECTED_DUPLICATE", protectedCopy.scan(rocket -> false).refusal());
        var f = new Fixture(); f.add(f.before.sourceSnapshot(), SOURCE);
        for (int i = 0; i < 62; i++) { f.add(i % 2 == 0 ? f.before.sourceSnapshot() : f.before.destinationSnapshot(), new UUID(185, i)); }
        assertNull(f.scan().refusal()); assertEquals(62, f.scan().copies().size());
        f.add(f.before.destinationSnapshot(), new UUID(185, 63));
        assertEquals("DUPLICATE_LIMIT", f.scan().refusal());
    }

    @Test void copyConsumptionSharesTheExistingSnapshotBudgetAndDefersBehindOtherWork() {
        var budget = new RocketCargoWorkBudget();
        assertTrue(budget.claim(1, new Object()));
        assertFalse(BayCopySteps.reserveCopyWork(budget, 1));
        assertTrue(BayCopySteps.reserveCopyWork(budget, 2));
        assertEquals(RocketCargoWorkBudget.MAX_BYTES, budget.chargedBytes());
        assertEquals(RocketCargoWorkBudget.MAX_VISITS, budget.chargedVisits());
        assertEquals(1, budget.reconstructions());
        assertFalse(BayCopySteps.reserveCopyWork(budget, 2));
        assertFalse(budget.claim(2, new Object()));
        assertTrue(BayCopySteps.reserveCopyWork(budget, 3));
    }

    static final class Fixture {
        final MinecraftServer server = UnstartedServer.owned();
        final RocketTransferRecord before;
        final BayConsumedSnapshots.Result after;
        final BayGroup group;
        final ProbeLevel source, destination;
        Fixture() { this(record(1)); }
        Fixture(RocketTransferRecord before) {
            this.before = before;
            var job = BayConsumedSnapshots.prepare(before.sourceSnapshot(), before.destinationSnapshot(), Map.of(bay(0), intent(before, 0))).orElseThrow();
            var budget = new RocketCargoWorkBudget(); for (long tick = 1; !job.step(budget, tick); tick++) { }
            after = job.result().orElseThrow();
            group = BayGroup.resumed(LOGICAL, TRANSFER, List.of(BayMember.restartRegistered(
                    registered(intent(before, 0), after.source().contentHash(), after.destination().contentHash())))).orElseThrow();
            source = level(before.sourceSnapshot()); destination = level(before.destinationSnapshot());
            UnstartedServer.set(MinecraftServer.class, server, "levels", Map.of(source.key, source, destination.key, destination));
        }
        BayCopyCensus.Scan scan() { return scan(rocket -> true); }
        BayCopyCensus.Scan scan(Predicate<RocketEntity> neutral) { return BayCopyCensus.inspect(server, before, group, neutral); }
        private ProbeLevel level(RocketStructureSnapshot snapshot) {
            var level = UnstartedServer.allocate(ProbeLevel.class);
            level.key = ResourceKey.create(Registries.DIMENSION, snapshot.sourceDimension());
            level.lookup = new EntityLookup<>(); level.sections = new EntitySectionStorage<>(Entity.class, chunk -> Visibility.TICKING);
            level.getter = new LevelEntityGetterAdapter<>(level.lookup, level.sections);
            return level;
        }
        RocketEntity add(RocketStructureSnapshot snapshot, UUID id) {
            var level = snapshot.sourceDimension().equals(source.key.location()) ? source : destination;
            var origin = snapshot.sourceOrigin(); var rocket = UnstartedServer.allocate(RocketEntity.class);
            UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", LOGICAL);
            UnstartedServer.set(RocketEntity.class, rocket, "ownerId", OWNER);
            UnstartedServer.set(RocketEntity.class, rocket, "snapshot", snapshot);
            UnstartedServer.set(RocketEntity.class, rocket, "flightData", before.sourceFlightData());
            UnstartedServer.set(Entity.class, rocket, "uuid", id); UnstartedServer.set(Entity.class, rocket, "level", level);
            UnstartedServer.set(Entity.class, rocket, "passengers", ImmutableList.of());
            UnstartedServer.set(Entity.class, rocket, "position", new Vec3(origin.x() + .5, origin.y() + 48, origin.z() + .5));
            UnstartedServer.set(Entity.class, rocket, "bb", new AABB(origin.x() - .25, origin.y() + 48, origin.z() - .25,
                    origin.x() + 1.25, origin.y() + 49.5, origin.z() + 1.25));
            level.lookup.add(rocket);
            level.sections.getOrCreateSection(SectionPos.asLong(origin.x() >> 4, (origin.y() + 48) >> 4, origin.z() >> 4)).add(rocket);
            return rocket;
        }
    }

    static final class ProbeLevel extends ServerLevel {
        ResourceKey<Level> key;
        EntityLookup<Entity> lookup;
        EntitySectionStorage<Entity> sections;
        LevelEntityGetter<Entity> getter;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public LevelEntityGetter<Entity> getEntities() { return getter; }
        @Override public Entity getEntity(UUID id) { return lookup.getEntity(id); }
    }
}
