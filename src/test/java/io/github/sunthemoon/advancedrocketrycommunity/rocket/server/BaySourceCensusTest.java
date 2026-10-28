package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.server.BayConsumptionFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayGroup;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.bay.deployment.BayMember;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.RocketCargoWorkBudget;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketFlightLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.RocketTransferRecord;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * W170-R21 A0: the R2 and D6 census first bounds every loaded identity naming L at both recorded ends, then classifies
 * each one, so an unexpected image is never treated as no copy. The destination-column case is review pass 2's probe;
 * the entity index is the real vanilla one, without a started server.
 */
final class BaySourceCensusTest {
    private static final UUID OTHER = new UUID(999, 1);
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    private static BayGroup group(RocketTransferRecord record) {
        var job = BayConsumedSnapshots.prepare(record.sourceSnapshot(), record.destinationSnapshot(),
                Map.of(bay(0), intent(record, 0))).orElseThrow();
        var budget = new RocketCargoWorkBudget();
        for (long tick = 1; !job.step(budget, tick); tick++) { }
        var result = job.result().orElseThrow();
        var receipt = registered(intent(record, 0), result.source().contentHash(), result.destination().contentHash());
        return BayGroup.resumed(LOGICAL, TRANSFER, List.of(BayMember.restartRegistered(receipt))).orElseThrow();
    }

    /** A loaded rocket image naming L: operational, with T's source flight data, at block {@code (x, y, z)}. */
    private static RocketEntity image(RocketTransferRecord record, UUID uuid, UUID owner, RocketStructureSnapshot snapshot,
                                      double x, double y, double z) {
        var rocket = UnstartedServer.allocate(RocketEntity.class);
        UnstartedServer.set(RocketEntity.class, rocket, "assemblyTransactionId", LOGICAL);
        UnstartedServer.set(RocketEntity.class, rocket, "ownerId", owner);
        UnstartedServer.set(RocketEntity.class, rocket, "snapshot", snapshot);
        UnstartedServer.set(RocketEntity.class, rocket, "flightData", record.sourceFlightData());
        UnstartedServer.set(Entity.class, rocket, "uuid", uuid);
        UnstartedServer.set(Entity.class, rocket, "bb", new AABB(x, y, z, x + 1, y + 1, z + 1));
        return rocket;
    }

    /** Levels by dimension, each with the real loaded-entity index over the given entities. */
    private static final class World {
        final MinecraftServer server = UnstartedServer.owned();
        final Map<ResourceKey<Level>, ServerLevel> levels = new LinkedHashMap<>();

        World put(ResourceLocation dimension, RocketEntity... entities) {
            var level = UnstartedServer.allocate(ProbeLevel.class);
            level.key = ResourceKey.create(Registries.DIMENSION, dimension);
            var sections = new EntitySectionStorage<Entity>(Entity.class, key -> Visibility.TICKING);
            var lookup = new EntityLookup<Entity>();
            for (RocketEntity rocket : entities) {
                var box = rocket.getBoundingBox();
                sections.getOrCreateSection(SectionPos.asLong(SectionPos.blockToSectionCoord(box.minX),
                        SectionPos.blockToSectionCoord(box.minY), SectionPos.blockToSectionCoord(box.minZ))).add(rocket);
                lookup.add(rocket);
            }
            level.getter = new LevelEntityGetterAdapter<>(lookup, sections);
            levels.put(level.key, level);
            UnstartedServer.set(MinecraftServer.class, server, "levels", levels);
            return this;
        }
        String copies(RocketTransferRecord record) { return BaySourceResume.copies(server, record, group(record), SOURCE); }
    }

    private static double altitude(RocketTransferRecord record) {
        return record.sourceSnapshot().sourceOrigin().y() + RocketFlightLimits.FLIGHT_ALTITUDE_BLOCKS;
    }

    @Test void theExactSourceAloneAtItsEndIsNoCopy() {
        var record = record(1);
        var source = image(record, SOURCE, OWNER, record.sourceSnapshot(), EARTH.x(), altitude(record), EARTH.z());
        var world = new World().put(record.sourceSnapshot().sourceDimension(), source)
                .put(record.destinationSnapshot().sourceDimension());
        assertNull(world.copies(record), "control: T's exact source at the source end is excluded");
    }

    /** Review pass 2's probe: a same-L, different-hash copy in the destination's loaded entity index. */
    @Test void anUnexpectedImageInTheDestinationColumnHoldsForRepair() {
        var record = record(1);
        var copy = image(record, new UUID(999, 2), OWNER, record(2).destinationSnapshot(), MOON.x(), MOON.y(), MOON.z());
        var world = new World().put(record.sourceSnapshot().sourceDimension()).put(record.destinationSnapshot().sourceDimension(), copy);
        int[] visible = {0};
        world.levels.values().forEach(level -> level.getEntities().get(new AABB(-1_000, -64, -1_000, 1_000, 320, 1_000), entity -> visible[0]++));
        assertEquals(1, visible[0], "control: the copy is in the destination's loaded entity index");
        assertEquals("UNEXPECTED_COPY", world.copies(record), "an unexpected loaded image of L must not count as no copy");
    }

    @Test void anUnexpectedImageInTheSourceMarginHoldsForRepair() {
        var record = record(1);
        var region = RocketRegion.fromSnapshot(record.sourceSnapshot());
        // Inside the one-block lookup margin west of the region, at ground level: the box spans the whole column.
        var copy = image(record, new UUID(999, 3), OWNER, record(2).sourceSnapshot(), region.minimum().x() - 1.0D,
                record.sourceSnapshot().sourceOrigin().y(), region.minimum().z());
        var world = new World().put(record.sourceSnapshot().sourceDimension(), copy).put(record.destinationSnapshot().sourceDimension());
        assertEquals("UNEXPECTED_COPY", world.copies(record));
    }

    @Test void theSourceUuidAtTheDestinationEndIsNotTheSource() {
        var record = record(1);
        var source = image(record, SOURCE, OWNER, record.sourceSnapshot(), EARTH.x(), altitude(record), EARTH.z());
        var relocated = image(record, SOURCE, OWNER, record.sourceSnapshot(), MOON.x(), MOON.y(), MOON.z());
        var world = new World().put(record.sourceSnapshot().sourceDimension(), source)
                .put(record.destinationSnapshot().sourceDimension(), relocated);
        assertEquals("UNEXPECTED_COPY", world.copies(record), "T's source UUID is excluded only at the source end");
    }

    @Test void aCopyNamingLWithAnotherOwnerHoldsForRepair() {
        var record = record(1);
        var copy = image(record, new UUID(999, 4), OTHER, record.sourceSnapshot(), EARTH.x(), EARTH.y(), EARTH.z());
        var world = new World().put(record.sourceSnapshot().sourceDimension(), copy).put(record.destinationSnapshot().sourceDimension());
        assertEquals("UNEXPECTED_COPY", world.copies(record), "a source image under another owner is not recognized");
    }

    @Test void theCapCountsEveryIdentityNamingLBeforeItClassifies() {
        var record = record(1);
        var copies = new RocketEntity[RocketFlightLimits.MAX_TRANSFER_ENTITY_MATCHES];
        for (int i = 0; i < copies.length; i++) {
            copies[i] = image(record, new UUID(998, i), OWNER, record(2).destinationSnapshot(), MOON.x(), MOON.y() + i, MOON.z());
        }
        var world = new World().put(record.sourceSnapshot().sourceDimension()).put(record.destinationSnapshot().sourceDimension(), copies);
        assertEquals("DUPLICATE_LIMIT", world.copies(record), "reaching the cap is a conflict, never a truncation");
    }

    /**
     * W170-R42, the re-review's probe: a loaded rocket whose entity data did not decode keeps it as preserved blocked data,
     * with null decoded fields. Its preserved {@code assembly_transaction_id} names L, so it counts toward the cap and is
     * unexpected, never no copy.
     */
    @Test void aLoadedBlockedImageWhosePreservedDataNamesLMustHold() {
        var record = record(1);
        var blocked = UnstartedServer.allocate(RocketEntity.class);
        var data = new CompoundTag();
        data.putInt("schema_version", 99);
        data.putUUID("assembly_transaction_id", LOGICAL);
        data.putUUID("owner_id", OWNER);
        UnstartedServer.set(RocketEntity.class, blocked, "preservedBlockedData", data);
        UnstartedServer.set(Entity.class, blocked, "uuid", new UUID(999, 5));
        UnstartedServer.set(Entity.class, blocked, "bb", new AABB(MOON.x(), MOON.y(), MOON.z(), MOON.x() + 1, MOON.y() + 1, MOON.z() + 1));
        assertFalse(blocked.operational(), "control: the image is blocked");
        assertEquals(LOGICAL, blocked.preservedBlockedData().orElseThrow().getUUID("assembly_transaction_id"),
                "control: the preserved data names L");
        var world = new World().put(record.sourceSnapshot().sourceDimension()).put(record.destinationSnapshot().sourceDimension(), blocked);
        int[] visible = {0};
        world.levels.values().forEach(level -> level.getEntities().get(new AABB(-1_000, -64, -1_000, 1_000, 320, 1_000), entity -> visible[0]++));
        assertEquals(1, visible[0], "control: the blocked image is in the destination's loaded entity index");
        assertEquals("UNEXPECTED_COPY", world.copies(record), "a loaded image whose preserved data names L must not count as no copy");
    }

    static final class ProbeLevel extends ServerLevel {
        LevelEntityGetter<Entity> getter;
        ResourceKey<Level> key;
        ProbeLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public ResourceKey<Level> dimension() { return key; }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public LevelEntityGetter<Entity> getEntities() { return getter; }
    }
}
