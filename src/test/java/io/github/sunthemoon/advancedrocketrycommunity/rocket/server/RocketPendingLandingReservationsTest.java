package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence.RocketTransferSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction.RocketRegion;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.transfer.RocketLandingPadSelector;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.GovernedTransferFixture;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.EntitySectionStorage;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.entity.LevelEntityGetterAdapter;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.levelgen.Heightmap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Runs prepareLaunch's reservation inputs and real surface selector without world loading or F2 publication. */
class RocketPendingLandingReservationsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void eightPreF2SelectionsAreDistinctExhaustThePadsAndDiscardReusesTheExactPad() {
        var level = UnstartedServer.allocate(EmptyLandingLevel.class);
        var fixture = GovernedTransferFixture.create(); var source = fixture.record().sourceSnapshot();
        var journal = new RocketTransferSavedData();
        var pending = new ArrayList<RocketRegion>();
        var selector = new RocketLandingPadSelector();
        // Model the pending input only. GameTests cover real F1 lifecycle and the player-visible refusal code.
        for (int i = 0; i < 8; i++) {
            var selected = selector.select(level, source, UUID.randomUUID(),
                    RocketTransferService.landingReservations(journal, pending), 0);
            assertTrue(selected.success(), "Pending launch " + i + " could not select its pad");
            var region = RocketRegion.fromSnapshot(selected.snapshot().orElseThrow());
            assertTrue(pending.stream().noneMatch(region::overlaps));
            pending.add(region);
            assertTrue(journal.entries().isEmpty(), "The fixture must not publish F2 between selections");
        }
        assertEquals(8, pending.stream().distinct().count());
        assertFalse(selector.select(level, source, UUID.randomUUID(),
                RocketTransferService.landingReservations(journal, pending), 0).success());
        var released = pending.remove(3);
        var next = selector.select(level, source, UUID.randomUUID(),
                RocketTransferService.landingReservations(journal, pending), 0);
        assertTrue(next.success());
        assertEquals(released, RocketRegion.fromSnapshot(next.snapshot().orElseThrow()));
    }

    @Test void journalReservationsRemainWhenThePendingViewReleasesAfterPublication() {
        var record = GovernedTransferFixture.create().record();
        var journal = new RocketTransferSavedData();
        var region = RocketRegion.fromSnapshot(record.destinationSnapshot());
        var pending = new ArrayList<>(List.of(region));
        journal.put(record);
        assertTrue(RocketTransferService.landingReservations(journal, pending).contains(region));
        pending.clear();
        assertEquals(List.of(region), RocketTransferService.landingReservations(journal, pending));
        assertThrows(UnsupportedOperationException.class,
                () -> RocketTransferService.landingReservations(journal, pending).clear());
    }

    /** Only terrain/entity reads are stubbed; candidate order, regions, overlap and bounds are production code. */
    private static final class EmptyLandingLevel extends ServerLevel {
        private EmptyLandingLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public net.minecraft.util.profiling.ProfilerFiller getProfiler() { return net.minecraft.util.profiling.InactiveProfiler.INSTANCE; }
        @Override public net.minecraft.resources.ResourceKey<Level> dimension() { return Level.OVERWORLD; }
        @Override public BlockPos getSharedSpawnPos() { return new BlockPos(8, 80, 8); }
        @Override public int getMinBuildHeight() { return -64; }
        @Override public int getHeight() { return 384; }
        @Override public int getHeight(Heightmap.Types type, int x, int z) { return 80; }
        @Override public LevelChunk getChunk(int x, int z) { return null; }
        @Override public WorldBorder getWorldBorder() { return new WorldBorder(); }
        @Override public BlockState getBlockState(BlockPos pos) { return Blocks.AIR.defaultBlockState(); }
        @Override public BlockEntity getBlockEntity(BlockPos pos) { return null; }
        @Override public java.util.Collection<net.minecraftforge.entity.PartEntity<?>> getPartEntities() { return List.of(); }
        @Override public LevelEntityGetter<Entity> getEntities() {
            return new LevelEntityGetterAdapter<>(new EntityLookup<>(), new EntitySectionStorage<>(Entity.class, key -> Visibility.HIDDEN));
        }
    }
}
