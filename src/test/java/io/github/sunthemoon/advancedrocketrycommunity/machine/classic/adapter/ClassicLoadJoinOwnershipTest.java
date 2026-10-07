package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Storage/declaration checks only: no fake installed owner, service or gameplay ticket. */
class ClassicLoadJoinOwnershipTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void withholdingAvailabilityPreservesTheHeldStorageAndLifetime() {
        var state = ClassicRawPermitTest.state();
        state.installed(); assertTrue(state.available());
        Object life = state.lifetime(), epoch = state.storageEpoch(), facade = state.facadeEpoch();
        Object guard = state.acquire();
        state.withholdGameplay();
        assertFalse(state.available()); assertSame(life, state.lifetime());
        assertSame(epoch, state.storageEpoch()); assertSame(facade, state.facadeEpoch());
        assertTrue(state.heldBy(guard)); assertTrue(state.pending().isEmpty());
        state.release(guard); assertFalse(state.busy());
    }

    @Test void protectedPendingRemainsOwnedAndVerbatimWhileAvailabilityIsWithheld() {
        var state = ClassicRawPermitTest.state();
        CompoundTag input = new CompoundTag(), root = new CompoundTag();
        root.putInt("schema_version", 99); input.put(ClassicRootBundle.HATCH, root);
        input.putString("arce_machine", "legacy");
        ClassicPendingLoad pending;
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            pending = ClassicPendingLoad.capture(input, ClassicRootBundle.OwnerType.HATCH, capture);
            state.captured(pending, capture); state.withholdGameplay();
            assertTrue(capture.storageStillCurrent()); assertTrue(pending.ownedBy(state));
            assertFalse(pending.requiresSaveRefusal()); assertSame(pending, state.pending().orElseThrow());
        }
        CompoundTag outgoing = new CompoundTag();
        try (var emit = ClassicRawPermit.emission(state)) { pending.emit(outgoing, emit); }
        assertEquals(input, outgoing); assertFalse(state.available()); assertFalse(state.busy());
        // This does not establish native LOAD, retained recording or disk save admission.
    }

    @Test void withholdingAvailabilityDoesNotManufactureAMissingRawCheckpoint() {
        var state = ClassicRawPermitTest.state();
        state.withholdGameplay();
        assertFalse(state.available()); assertFalse(state.loaded()); assertTrue(state.pending().isEmpty());
        try (var emit = ClassicRawPermit.emission(state)) {
            Object epoch = state.storageEpoch(); state.withholdGameplay();
            assertTrue(emit.storageStillCurrent()); assertSame(epoch, state.storageEpoch());
        }
        assertFalse(state.busy());
    }

    @Test void realRetirementStillInvalidatesCaptureAndPreservesPendingData() {
        var state = ClassicRawPermitTest.state();
        CompoundTag input = new CompoundTag(); input.put(ClassicRootBundle.HATCH, new CompoundTag());
        try (var capture = ClassicRawPermit.acquire(state, ClassicRawPurpose.CAPTURE).orElseThrow()) {
            var pending = ClassicPendingLoad.capture(input, ClassicRootBundle.OwnerType.HATCH, capture);
            state.captured(pending, capture); Object life = state.lifetime();
            state.withholdGameplay(); state.retire();
            assertNotSame(life, state.lifetime()); assertFalse(capture.storageStillCurrent());
            assertSame(pending, state.pending().orElseThrow()); assertFalse(state.available());
        }
        assertFalse(state.busy());
    }

    @Test void ownerJoinAndOutgoingComparisonRemainPackagePrivateBooleans() throws Exception {
        for (Class<?> owner : new Class<?>[] {ClassicControllerBlockEntity.class, ClassicHatchBlockEntity.class}) {
            for (String name : new String[] {"loadJoinReady", "loadJoinStillCurrent"}) {
                var method = owner.getDeclaredMethod(name, ClassicFamilyService.class, GuardTicket.class);
                assertEquals(boolean.class, method.getReturnType());
                assertFalse(Modifier.isPublic(method.getModifiers())); assertFalse(Modifier.isProtected(method.getModifiers()));
                if (owner == ClassicControllerBlockEntity.class) { assertTrue(Modifier.isFinal(method.getModifiers())); }
            }
            var comparison = owner.getDeclaredMethod("matchesOutgoingCheckpoint", ClassicFamilyService.class, CompoundTag.class);
            assertEquals(boolean.class, comparison.getReturnType());
            assertFalse(Modifier.isPublic(comparison.getModifiers())); assertFalse(Modifier.isProtected(comparison.getModifiers()));
            if (owner == ClassicControllerBlockEntity.class) { assertTrue(Modifier.isFinal(comparison.getModifiers())); }
        }
    }

    @Test void completedCandidatesAndJoinStampsDoNotRetainOrPublishTickets() throws Exception {
        for (Class<?> owner : new Class<?>[] {ClassicControllerBlockEntity.class, ClassicHatchBlockEntity.class}) {
            Class<?> candidate = Arrays.stream(owner.getDeclaredClasses())
                    .filter(type -> type.getSimpleName().equals("LoadJoinCandidate")).findFirst().orElseThrow();
            assertTrue(candidate.isRecord()); assertTrue(Modifier.isPrivate(candidate.getModifiers()));
            for (var field : candidate.getDeclaredFields()) {
                assertTrue(Modifier.isFinal(field.getModifiers())); assertNotEquals(GuardTicket.class, field.getType());
            }
            for (String name : new String[] {"loadJoinCandidate", "preparingLoad", "joinedService", "joinedLifetime", "joinedStorageEpoch"}) {
                assertTrue(Modifier.isPrivate(owner.getDeclaredField(name).getModifiers()));
            }
            for (var field : owner.getDeclaredFields()) { assertNotEquals(GuardTicket.class, field.getType()); }
        }
    }

    @Test void singleOwnerLoadQualifiersDoNotAddAnAuthorityPurposeOrPublicFactory() throws Exception {
        for (String name : new String[] {"isSingleOwnerLoad", "singleOwnerLoadStillHeld"}) {
            var method = GuardTicket.class.getDeclaredMethod(name, ClassicFamilyService.class, ClassicOwnerState.class);
            assertEquals(boolean.class, method.getReturnType()); assertFalse(Modifier.isPublic(method.getModifiers()));
            assertFalse(Modifier.isStatic(method.getModifiers()));
        }
        for (var constructor : GuardTicket.class.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        assertEquals(java.util.List.of("CAPTURE", "EMIT"), Arrays.stream(ClassicRawPurpose.values()).map(Enum::name).toList());
        assertEquals(java.util.List.of("CAPABILITY", "LIFECYCLE", "LOAD", "COMPLETION", "RECOVERY"),
                Arrays.stream(ClassicTicketPurpose.values()).map(Enum::name).toList());
    }
}
