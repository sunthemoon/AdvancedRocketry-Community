package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.AuditLogCapture;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickDriver;
import io.github.sunthemoon.advancedrocketrycommunity.gametest.GameTestTickPacer;
import io.github.sunthemoon.advancedrocketrycommunity.registry.ModEntities;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.entity.RocketEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlockState;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketStructureSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.station.forge.StationPlatformGenerator;
import io.github.sunthemoon.advancedrocketrycommunity.station.model.StationState;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationCreationService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * W311F: the landing selector's two occupancy steps ({@code RocketLandingPadSelector}), each against one parked occupant
 * in a fresh station's Space column. The quick check refuses a wide occupant through its structure-sized entity box (W311)
 * and names it. A tall occupant whose base section lies below vanilla's entity-section margin escapes the quick check, and
 * the structure discovery refuses it.
 */
@GameTestHolder(AdvancedRocketryCommunity.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LandingPadQuickCheckGameTests {
    private static final TicketType<UUID> FIXTURE_TICKET = TicketType.create(
            "arce_gametest_landing_quick_check", Comparator.<UUID>naturalOrder(), 180);
    /** Vanilla's {@code EntitySectionStorage} visits sections from this many blocks below a searched box upward. */
    private static final int SECTION_MARGIN_BELOW = 4;
    /** The wide occupant's base: eight blocks into section 4, well below the station platform at y = 127. */
    private static final int WIDE_BASE_Y = 72;
    /** The tall occupant's base: twelve blocks into section 4, as in W311's worst case for the seated pick. */
    private static final int TALL_BASE_Y = 76;

    private LandingPadQuickCheckGameTests() { }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void quickCheckRefusesAWideOccupantThroughItsStructureBoxAndNamesIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.awaitReady(() -> {
            // R1's wide occupant, X=0..15, with the arrival at X=9..11. The origin is on the section's west edge, so
            // vanilla's search for the arrival visits the occupant's section, and only the entity box decides.
            BlockPos origin = f.corner.atY(WIDE_BASE_Y);
            var parked = f.park(wide(f.space, origin));
            var arrival = arrival(f.space, origin.offset(10, 0, 0), List.of(new RocketPosition(-1, 0, 0),
                    new RocketPosition(0, 0, 0), new RocketPosition(1, 0, 0), new RocketPosition(0, 1, 0), new RocketPosition(0, 2, 0)));
            var region = RocketRegion.fromSnapshot(arrival);
            helper.assertTrue(RocketRegion.fromSnapshot(parked.snapshot().orElseThrow()).overlaps(region),
                    "Precondition: the wide occupant's structure does not cover the arrival");
            helper.assertTrue(!LandingOccupancyGameTestSupport.baseBox(parked).intersects(LandingOccupancyGameTestSupport.arrivalBox(region)),
                    "Precondition: the wide occupant's base box reaches the arrival, so the quick check would not need its"
                            + " structure-sized box");
            var found = LandingOccupancyGameTestSupport.quickCheck(f.space, region);
            helper.assertTrue(found.size() == 1 && found.get(0) == parked, "The selector's quick check did not find the wide"
                    + " occupant through its structure-sized entity box (W311); it found " + found.size() + " rocket(s)");
            List<String> diagnostics;
            String reason;
            try (var log = new AuditLogCapture("ARCE_PAD_OCCUPANCY_REFUSED")) {
                reason = new RocketLandingPadSelector().unavailable(f.space, arrival, null, false);
                diagnostics = log.lines();
            }
            helper.assertTrue(reason != null && reason.endsWith("occupied"),
                    "The quick check did not refuse the arrival over the wide occupant as occupied: " + reason);
            // W311's commit 373e3e274: the quick check returns before the discovery runs, so the only line is its own.
            helper.assertTrue(diagnostics.size() == 1 && names(diagnostics.get(0), parked, region), "The quick check's refusal"
                    + " did not log one ARCE_PAD_OCCUPANCY_REFUSED line naming the wide occupant: " + diagnostics);
            helper.succeed();
        });
    }

    @GameTest(template = "rocket_test", batch = "legacy_pad_occupancy", timeoutTicks = 180)
    public static void tallOccupantBelowTheSectionMarginEscapesTheQuickCheckAndTheDiscoveryRefusesIt(GameTestHelper helper) {
        Fixture f = new Fixture(helper);
        f.awaitReady(() -> {
            // An eleven-block column with a two-block arm at its top; the arrival is one block under the arm, eight blocks
            // above the base. The occupant is indexed in its base's section, section 4; vanilla starts the arrival's search
            // in section 5.
            BlockPos origin = f.corner.atY(TALL_BASE_Y);
            var parked = f.park(tall(f.space, origin));
            var arrival = arrival(f.space, origin.offset(2, 8, 0), List.of(new RocketPosition(0, 0, 0)));
            var region = RocketRegion.fromSnapshot(arrival);
            var box = LandingOccupancyGameTestSupport.arrivalBox(region);
            helper.assertTrue(RocketRegion.fromSnapshot(parked.snapshot().orElseThrow()).overlaps(region),
                    "Precondition: the tall occupant's structure does not cover the arrival");
            helper.assertTrue(parked.getBoundingBox().intersects(box), "Precondition: the tall occupant's structure-sized"
                    + " entity box (W311) does not reach the arrival, so a quick-check miss would not be the section margin's");
            int baseSection = SectionPos.blockToSectionCoord(parked.blockPosition().getY());
            int firstSearched = SectionPos.posToSectionCoord(box.minY - SECTION_MARGIN_BELOW);
            helper.assertTrue(baseSection < firstSearched, "Precondition: the tall occupant's base section " + baseSection
                    + " is not below the first section " + firstSearched + " that vanilla searches for the arrival");
            var found = LandingOccupancyGameTestSupport.quickCheck(f.space, region);
            helper.assertTrue(found.isEmpty(), "The selector's quick check found the tall occupant although its base section"
                    + " lies below vanilla's entity-section margin; this test no longer shows the discovery as the backstop");
            // The occupant is indexed: a search that starts at its base finds it, so the miss comes from where vanilla starts.
            var fromBase = f.space.getEntitiesOfClass(RocketEntity.class,
                    new AABB(box.minX, parked.getY(), box.minZ, box.maxX, box.maxY, box.maxZ));
            helper.assertTrue(fromBase.contains(parked), "A search from the tall occupant's base did not find it, so the quick"
                    + " check's miss does not show the section margin");
            List<String> diagnostics;
            String reason;
            try (var log = new AuditLogCapture("ARCE_PAD_OCCUPANCY_REFUSED")) {
                reason = new RocketLandingPadSelector().unavailable(f.space, arrival, null, false);
                diagnostics = log.lines();
            }
            helper.assertTrue(reason != null && reason.endsWith("occupied"), "The structure discovery did not refuse the"
                    + " arrival that the tall occupant's structure covers: " + reason);
            helper.assertTrue(diagnostics.size() == 1 && names(diagnostics.get(0), parked, region), "The structure discovery's"
                    + " refusal did not log one ARCE_PAD_OCCUPANCY_REFUSED line naming the tall occupant: " + diagnostics);
            helper.succeed();
        });
    }

    /** True when an operator line states an occupied refusal of {@code arrival} by {@code rocket}, with a readable snapshot. */
    private static boolean names(String line, RocketEntity rocket, RocketRegion arrival) {
        var minimum = arrival.minimum();
        return line.contains("reason=occupied")
                && line.contains("arrival=" + minimum.x() + "," + minimum.y() + "," + minimum.z())
                && line.contains("rocket=" + rocket.getUUID())
                && line.contains("rocket_pos=" + rocket.blockPosition().toShortString())
                && line.contains("snapshot=readable");
    }

    /** R1's sixteen-block occupant: X=0..15 on one row and two blocks up at X=0; its origin is its west end. */
    private static RocketStructureSnapshot wide(ServerLevel level, BlockPos origin) {
        List<RocketPosition> blocks = new ArrayList<>();
        for (int x = 0; x <= 15; x++) { blocks.add(new RocketPosition(x, 0, 0)); }
        blocks.add(new RocketPosition(0, 1, 0));
        blocks.add(new RocketPosition(0, 2, 0));
        return snapshot(level, origin, blocks);
    }

    /** An eleven-block column at X=0 with an arm at X=1..2 on its top block; its origin is the column's base. */
    private static RocketStructureSnapshot tall(ServerLevel level, BlockPos origin) {
        List<RocketPosition> blocks = new ArrayList<>();
        for (int y = 0; y <= 10; y++) { blocks.add(new RocketPosition(0, y, 0)); }
        blocks.add(new RocketPosition(1, 10, 0));
        blocks.add(new RocketPosition(2, 10, 0));
        return snapshot(level, origin, blocks);
    }

    /** A recorded arrival, as a selection would produce it: only its region and blocks are read. */
    private static RocketStructureSnapshot arrival(ServerLevel level, BlockPos origin, List<RocketPosition> blocks) {
        return snapshot(level, origin, blocks);
    }

    private static RocketStructureSnapshot snapshot(ServerLevel level, BlockPos origin, List<RocketPosition> positions) {
        var iron = new RocketBlockState(ResourceLocation.tryParse("minecraft:iron_block"), Map.of());
        List<RocketBlock> blocks = positions.stream().map(position -> new RocketBlock(position, iron)).toList();
        return RocketStructureSnapshot.create(UUID.randomUUID(), level.dimension().location(),
                new RocketPosition(origin.getX(), origin.getY(), origin.getZ()), blocks, List.of(),
                new RocketStats(blocks.size(), blocks.size(), 0, 0, 0, 0, 0, 0), level.getGameTime());
    }

    /** A fresh station, its pad chunk held by a ticket; the occupants are entities only and place no blocks. */
    private static final class Fixture {
        private final GameTestHelper helper;
        private final GameTestTickDriver driver;
        private final GameTestTickPacer pacer;
        private final ServerLevel space;
        private final StationState station;
        /** The north-west corner of the station pad's chunk; the station pad sits on it. */
        private final BlockPos corner;
        private final ChunkPos column;
        private final UUID ticket = UUID.randomUUID();
        private final List<RocketEntity> rockets = new ArrayList<>();
        private boolean closed;

        private Fixture(GameTestHelper helper) {
            this.helper = helper;
            driver = new GameTestTickDriver(helper);
            pacer = new GameTestTickPacer(helper.getLevel().getServer());
            space = helper.getLevel().getServer().getLevel(CelestialIds.SPACE_LEVEL);
            helper.assertTrue(space != null, "Space Level unavailable");
            station = new StationCreationService(new StationPlatformGenerator(), id -> true)
                    .create(space.getServer(), UUID.randomUUID(), "Quick Check Test", CelestialIds.EARTH_ID, false)
                    .station().orElseThrow();
            column = new ChunkPos(new BlockPos(station.landingPad().x(), 0, station.landingPad().z()));
            corner = new BlockPos(column.getMinBlockX(), 0, column.getMinBlockZ());
            // Test setup, not admission: the occupants' chunk stays entity-ticking while the test reads its sections.
            space.getChunkSource().addRegionTicket(FIXTURE_TICKET, column, 2, ticket);
        }

        private void awaitReady(Runnable action) {
            boolean ready = space.areEntitiesLoaded(column.toLong()) && space.isPositionEntityTicking(corner.atY(WIDE_BASE_Y));
            if (ready || helper.getTick() >= 80) {
                try {
                    helper.assertTrue(ready, "Quick-check fixture entity chunk did not become ready");
                    action.run();
                } finally { close(); }
            } else {
                pacer.pace(); driver.after(1, () -> awaitReady(action));
            }
        }

        private RocketEntity park(RocketStructureSnapshot snapshot) {
            var rocket = ModEntities.ROCKET.get().create(space);
            helper.assertTrue(rocket != null, "Rocket entity creation failed");
            rocket.initialize(snapshot, UUID.randomUUID(), station.ownerId());
            helper.assertTrue(space.addFreshEntity(rocket), "Fixture occupant spawn failed");
            rockets.add(rocket);
            helper.assertTrue(rocket.snapshot().isPresent() && new ChunkPos(rocket.blockPosition()).equals(column),
                    "Fixture occupant is not an operational rocket in the fixture's chunk");
            return rocket;
        }

        private void close() {
            if (closed) { return; }
            closed = true;
            rockets.forEach(RocketEntity::discard);
            space.getChunkSource().removeRegionTicket(FIXTURE_TICKET, column, 2, ticket);
            StationRegistrySavedData.get(space.getServer()).delete(station.stationId());
            new StationPlatformGenerator().removeTemplate(space, station.cell());
        }
    }
}
