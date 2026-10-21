package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.UnstartedServer;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.FluidFuelLoaderBlockEntity;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderRoot;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.fuel.loader.LoaderTank;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderRelocationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void loadedSameChunkCrossChunkAndCrossLevelHoldersDiagnoseButNeverReplaceTheReference() {
        var endpoint = EscrowFillSelfGuardTest.fill(1, 0).source(); var before = EscrowCodec.endpoint(endpoint);
        var original = EscrowLoaderEndpoint.position(endpoint);
        for (var slot : List.of(new EscrowLoaderRelocation.Slot(endpoint.level(), original.offset(1, 0, 0)),
                new EscrowLoaderRelocation.Slot(endpoint.level(), original.offset(32, 0, 0)),
                new EscrowLoaderRelocation.Slot("minecraft:the_nether", original))) {
            assertEquals(EscrowReason.RELOCATED_ENDPOINT, EscrowLoaderRelocation.missingReason(List.of(1), endpoint, ignored -> slot));
            assertEquals(before, EscrowCodec.endpoint(endpoint));
        }
    }

    @Test void missingUnloadedAndStaleHoldersProvideNoRelocationEvidence() {
        var endpoint = EscrowFillSelfGuardTest.fill(1, 0).source();
        // The adapter supplies no Slot for a removed, foreign, unloaded or replaced index holder.
        assertEquals(EscrowReason.MISSING_ENDPOINT, EscrowLoaderRelocation.missingReason(List.of(), endpoint, ignored -> fail()));
        assertEquals(EscrowReason.MISSING_ENDPOINT, EscrowLoaderRelocation.missingReason(List.of(1, 2, 3), endpoint, ignored -> null));
        assertEquals(EscrowReason.MISSING_ENDPOINT, EscrowLoaderRelocation.missingReason(List.of(1), endpoint,
                ignored -> new EscrowLoaderRelocation.Slot(endpoint.level(), EscrowLoaderEndpoint.position(endpoint))));
    }

    @Test void anotherLiveDuplicateDisprovesAbsenceEvenAfterAStaleHolderOrAnOriginalPlace() {
        var endpoint = EscrowFillSelfGuardTest.fill(1, 0).source(); var calls = new AtomicInteger();
        assertEquals(EscrowReason.RELOCATED_ENDPOINT, EscrowLoaderRelocation.missingReason(List.of(0, 1, 2), endpoint, level -> {
            calls.incrementAndGet();
            return level == 0 ? null : new EscrowLoaderRelocation.Slot(level == 1 ? endpoint.level() : "minecraft:the_end",
                    EscrowLoaderEndpoint.position(endpoint));
        }));
        assertEquals(3, calls.get());
    }

    @Test void sixtyFourLevelsAreAllowedButOverflowIsRetryableAndDoesNotReadTheSixtyFifth() {
        var endpoint = EscrowFillSelfGuardTest.fill(1, 0).source();
        assertEquals(EscrowReason.MISSING_ENDPOINT, EscrowLoaderRelocation.missingReason(IntStream.range(0, 64).boxed().toList(), endpoint, ignored -> null));
        for (boolean foundEarly : List.of(false, true)) {
            var calls = new AtomicInteger();
            var reason = EscrowLoaderRelocation.missingReason(IntStream.range(0, 65).boxed().toList(), endpoint, level -> {
                assertTrue(level < 64); calls.incrementAndGet();
                return foundEarly && level == 0 ? new EscrowLoaderRelocation.Slot("minecraft:the_end", BlockPos.ZERO) : null;
            });
            assertEquals(64, calls.get()); assertEquals(EscrowReason.BARRIER_BOUNDS, reason);
            assertTrue(reason.retryableProofFailure()); assertFalse(reason.manual());
        }
    }

    @Test void liveHolderValidationRejectsRemovedForeignIdentityForeignLevelUnloadedAndReplacedReferences() {
        var level = UnstartedServer.allocate(HolderLevel.class); var device = EscrowTestFixtures.id(12);
        var holder = UnstartedServer.allocate(FluidFuelLoaderBlockEntity.class);
        var block = net.minecraft.world.level.block.entity.BlockEntity.class;
        UnstartedServer.set(block, holder, "level", level); UnstartedServer.set(block, holder, "worldPosition", BlockPos.ZERO);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, holder, "status", LoaderRoot.Status.VALID);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, holder, "root", LoaderRoot.placed(null, device, LoaderTank.EMPTY));
        assertEquals(new EscrowLoaderRelocation.Slot("minecraft:overworld", BlockPos.ZERO),
                EscrowLoaderRelocation.loadedHolder(level, device, holder, pos -> holder));
        assertNull(EscrowLoaderRelocation.loadedHolder(level, device, holder, pos -> null), "Unloaded holder");
        var duplicate = UnstartedServer.allocate(FluidFuelLoaderBlockEntity.class);
        assertNull(EscrowLoaderRelocation.loadedHolder(level, device, holder, pos -> duplicate), "Stale holder replaced in loaded chunk");
        assertNull(EscrowLoaderRelocation.loadedHolder(level, EscrowTestFixtures.id(13), holder, pos -> fail("Foreign identity must not read a chunk")));
        assertNull(EscrowLoaderRelocation.loadedHolder(UnstartedServer.allocate(HolderLevel.class), device, holder, pos -> fail("Foreign Level")));
        UnstartedServer.set(block, holder, "remove", true);
        assertNull(EscrowLoaderRelocation.loadedHolder(level, device, holder, pos -> fail("Removed holder")));
        UnstartedServer.set(block, holder, "remove", false);
        UnstartedServer.set(FluidFuelLoaderBlockEntity.class, holder, "status", LoaderRoot.Status.INVALID);
        assertNull(EscrowLoaderRelocation.loadedHolder(level, device, holder, pos -> fail("Invalid root")));
    }

    static final class HolderLevel extends net.minecraft.server.level.ServerLevel {
        HolderLevel() { super(null, null, null, null, null, null, null, false, 0, List.of(), false, null); }
        @Override public net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension() {
            return net.minecraft.world.level.Level.OVERWORLD;
        }
    }

    @Test void previewAndConfirmationObservationRefuseRelocationEvenWithAnAlreadyPersistedMissingReason() {
        var op = EscrowFillSelfGuardTest.fill(1, 0).withReason(EscrowReason.MISSING_ENDPOINT);
        var root = EscrowTestFixtures.ledger(op); var before = EscrowCodec.encode(root);
        assertTrue(EscrowLoaderEndpoint.observe(null, op.source(), root.id(), () -> EscrowReason.MISSING_ENDPOINT).missing());
        for (int observation = 0; observation < 2; observation++) {
            for (var reason : List.of(EscrowReason.RELOCATED_ENDPOINT, EscrowReason.BARRIER_BOUNDS)) {
                var failure = assertThrows(EscrowReconciliationView.Refusal.class,
                        () -> EscrowLoaderEndpoint.observe(null, op.source(), root.id(), () -> reason));
                assertEquals(reason, failure.reason);
            }
        }
        assertEquals(before, EscrowCodec.encode(root));
    }
}
