package io.github.sunthemoon.advancedrocketrycommunity.endgame.protection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffect;
import io.github.sunthemoon.advancedrocketrycommunity.api.endgame.EndgameEffectEvent;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameCode;
import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameSystem;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

/** ADR-054 section 5: the chain's order, its first-failure rule and the event it posts. */
class EndgameProtectionTest {
    static {
        MinecraftBootstrap.initialize();
    }

    private static final ResourceKey<Level> OVERWORLD = ResourceKey.create(Registries.DIMENSION,
            ResourceLocation.tryBuild("minecraft", "overworld"));
    private static final UUID OWNER = new UUID(1L, 1L);
    private static final BlockPos MIN = new BlockPos(10, 60, 10);
    private static final BlockPos MAX = new BlockPos(12, 60, 12);

    @Test
    void anUntouchedBatchPassesAndPostsTheEventOnce() {
        FakeView view = new FakeView();
        assertEquals(EndgameCode.OK, EndgameProtection.check(batch(true), view));
        assertEquals(1, view.posted.size());
        EndgameEffectEvent event = view.posted.get(0);
        assertEquals(EndgameSystem.LASER_DRILL.key(), event.systemId());
        assertEquals(EndgameEffect.BLOCK_BREAK, event.effect());
        assertEquals(OWNER, event.ownerId());
        assertTrue(event.actorId().isEmpty());
        assertEquals(OVERWORLD, event.level());
        assertEquals(MIN, event.min());
        assertEquals(MAX, event.max());
    }

    @Test
    void eachStepFailsInOrderAndStopsBeforeTheLaterOnes() {
        FakeView unloaded = new FakeView();
        unloaded.loaded = false;
        unloaded.inside = false;
        assertRefused(unloaded, EndgameCode.TARGET_UNLOADED);

        FakeView outside = new FakeView();
        outside.inside = false;
        outside.zones.add(zone(List.of()));
        assertRefused(outside, EndgameCode.TARGET_OUT_OF_BOUNDS);

        FakeView zoned = new FakeView();
        zoned.zones.add(zone(List.of()));
        zoned.space = true;
        zoned.stations = false;
        assertRefused(zoned, EndgameCode.TARGET_PROTECTED);

        FakeView station = new FakeView();
        station.space = true;
        station.stations = false;
        station.spawn = Optional.of(new EndgameProtection.SpawnSquare(11, 11, 16));
        assertRefused(station, EndgameCode.TARGET_PROTECTED);

        FakeView spawn = new FakeView();
        spawn.spawn = Optional.of(new EndgameProtection.SpawnSquare(0, 0, 10));
        assertRefused(spawn, EndgameCode.TARGET_PROTECTED);

        FakeView vetoed = new FakeView();
        vetoed.cancel = true;
        assertEquals(EndgameCode.TARGET_PROTECTED, EndgameProtection.check(batch(true), vetoed));
        assertEquals(1, vetoed.posted.size(), "the event is the last step");
    }

    @Test
    void anAllowListedOwnerPassesTheZoneAndOtherLevelsAreIgnored() {
        FakeView allowed = new FakeView();
        allowed.zones.add(zone(List.of(OWNER)));
        assertEquals(EndgameCode.OK, EndgameProtection.check(batch(true), allowed));
        FakeView elsewhere = new FakeView();
        elsewhere.zones.add(ProtectedZone.of("nether", ResourceLocation.tryBuild("minecraft", "the_nether"), 0, 0, 100,
                100, List.of()));
        assertEquals(EndgameCode.OK, EndgameProtection.check(batch(true), elsewhere));
        FakeView space = new FakeView();
        space.space = true;
        assertEquals(EndgameCode.OK, EndgameProtection.check(batch(true), space), "the owner may build there");
    }

    @Test
    void effectsThatModifyNoChunkSkipTheLoadedStep() {
        FakeView unloaded = new FakeView();
        unloaded.loaded = false;
        assertEquals(EndgameCode.OK, EndgameProtection.check(batch(false), unloaded), "ADR-058 section 3 step 1");
    }

    @Test
    void theSpawnSquareIsInclusiveOnEverySide() {
        EndgameProtection.SpawnSquare square = new EndgameProtection.SpawnSquare(0, 0, 16);
        assertTrue(square.intersects(new BlockPos(16, 0, 16), new BlockPos(20, 0, 20)));
        assertTrue(square.intersects(new BlockPos(-20, 0, -20), new BlockPos(-16, 0, -16)));
        assertFalse(square.intersects(new BlockPos(17, 0, 0), new BlockPos(20, 0, 0)));
        assertFalse(square.intersects(new BlockPos(0, 0, -30), new BlockPos(0, 0, -17)));
        assertTrue(square.intersects(new BlockPos(-100, 0, -100), new BlockPos(100, 0, 100)), "a box around it");
    }

    private static void assertRefused(FakeView view, EndgameCode code) {
        assertEquals(code, EndgameProtection.check(batch(true), view));
        assertTrue(view.posted.isEmpty(), "no event after an earlier failure");
    }

    private static ProtectedZone zone(List<UUID> allow) {
        return ProtectedZone.of("claim", OVERWORLD.location(), 0, 0, 10, 10, allow);
    }

    private static EndgameProtection.Batch batch(boolean requireLoaded) {
        return new EndgameProtection.Batch(EndgameSystem.LASER_DRILL, EndgameEffect.BLOCK_BREAK, OWNER, Optional.empty(),
                OVERWORLD, MIN, MAX, requireLoaded);
    }

    private static final class FakeView implements EndgameProtection.View {
        boolean loaded = true;
        boolean inside = true;
        boolean space;
        boolean stations = true;
        boolean cancel;
        Optional<EndgameProtection.SpawnSquare> spawn = Optional.empty();
        final List<ProtectedZone> zones = new ArrayList<>();
        final List<EndgameEffectEvent> posted = new ArrayList<>();

        @Override
        public boolean chunksFull(BlockPos min, BlockPos max) {
            return loaded;
        }

        @Override
        public boolean insideWorld(BlockPos min, BlockPos max) {
            return inside;
        }

        @Override
        public Collection<ProtectedZone> zones() {
            return zones;
        }

        @Override
        public boolean spaceLevel() {
            return space;
        }

        @Override
        public boolean stationsAllow(UUID owner, BlockPos min, BlockPos max) {
            return stations;
        }

        @Override
        public Optional<EndgameProtection.SpawnSquare> spawnSquare() {
            return spawn;
        }

        @Override
        public boolean cancelled(EndgameEffectEvent event) {
            posted.add(event);
            if (cancel) {
                event.setCanceled(true);
            }
            return event.isCanceled();
        }
    }

    @Test
    void theEventRejectsAnInvertedBoxAndCopiesItsCorners() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(1, 2, 3);
        EndgameEffectEvent event = new EndgameEffectEvent(EndgameSystem.GRAVITY_FIELD.key(), EndgameEffect.ENTITY_GRAVITY,
                OWNER, Optional.of(OWNER), OVERWORLD, mutable, mutable);
        mutable.set(9, 9, 9);
        assertEquals(new BlockPos(1, 2, 3), event.min(), "the event keeps an immutable copy");
        assertEquals(Optional.of(OWNER), event.actorId());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> new EndgameEffectEvent(
                EndgameSystem.GRAVITY_FIELD.key(), EndgameEffect.ENTITY_GRAVITY, OWNER, Optional.empty(), OVERWORLD,
                new BlockPos(1, 1, 1), new BlockPos(0, 1, 1)));
    }
}
