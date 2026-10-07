package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.chunk.LevelChunk;
import org.junit.jupiter.api.Test;

/** Declaration/data boundaries only, not a synthetic installed-owner admission test. */
class ClassicEmptyHatchRemovalAdmissionTest {
    @Test void emptyRemovalEntriesRemainPackagePrivateAndHaveNoCallerSuccessProfile() throws Exception {
        var acquire = GuardTicket.class.getDeclaredMethod("acquireEmptyHatchRemoval",
                ClassicFamilyService.class, ClassicHatchBlockEntity.class);
        assertTrue(Modifier.isStatic(acquire.getModifiers())); assertPackageOnly(acquire.getModifiers());
        assertEquals(Optional.class, acquire.getReturnType());
        var entry = ClassicAccessCoordinator.class.getDeclaredMethod("enterEmptyHatchRemoval", ClassicHatchBlockEntity.class);
        assertFalse(Modifier.isStatic(entry.getModifiers())); assertPackageOnly(entry.getModifiers());
        assertEquals(Optional.class, entry.getReturnType());
        for (var constructor : GuardTicket.class.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        assertEquals(java.util.List.of("CAPTURE", "EMIT"), Arrays.stream(ClassicRawPurpose.values()).map(Enum::name).toList());
        assertEquals(java.util.List.of("CAPABILITY", "LIFECYCLE", "LOAD", "COMPLETION", "RECOVERY"),
                Arrays.stream(ClassicTicketPurpose.values()).map(Enum::name).toList());
    }

    @Test void absentNativeDependenciesCannotProduceAnAcquisitionOrObserverSelection() {
        assertTrue(GuardTicket.acquireEmptyHatchRemoval(null, null).isEmpty());
        assertNull(ClassicSaveProtection.selectEmptyHatchRemoval(null, null, null));
        assertFalse(ClassicSaveProtection.emptyRemovalSelectionMatches(null, null));
    }

    @Test void acquisitionVerifierAndOpaqueWitnessAccessDoNotExposeJoinedGuardAccess() throws Exception {
        var verifier = GuardTicket.class.getDeclaredMethod("verifyEmptyRemovalAcquisition",
                ClassicFamilyService.class, ClassicHatchBlockEntity.class);
        assertEquals(boolean.class, verifier.getReturnType()); assertPackageOnly(verifier.getModifiers());
        var identity = GuardTicket.class.getDeclaredMethod("emptyRemovalWitnessIdentity",
                ClassicFamilyService.class, ClassicHatchBlockEntity.class, LevelChunk.class);
        assertEquals(Object.class, identity.getReturnType()); assertPackageOnly(identity.getModifiers());
        var attachment = GuardTicket.class.getDeclaredMethod("attachEmptyRemovalSelection", ClassicChunkObservation.EmptyRemovalSelection.class);
        assertEquals(boolean.class, attachment.getReturnType()); assertPackageOnly(attachment.getModifiers());
        var profile = Arrays.stream(GuardTicket.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("EmptyRemovalWitness")).findFirst().orElseThrow();
        assertTrue(profile.isRecord()); assertTrue(Modifier.isPrivate(profile.getModifiers()));
        for (var field : profile.getDeclaredFields()) { assertTrue(Modifier.isFinal(field.getModifiers())); }
    }

    @Test void localSnapshotHasPrivateIdentityFieldsAndNoTypedTagOrTicketFields() {
        Class<?> snapshot = ClassicHatchBlockEntity.EmptyRemovalState.class;
        assertTrue(Modifier.isFinal(snapshot.getModifiers())); assertPackageOnly(snapshot.getModifiers());
        for (var constructor : snapshot.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        for (var field : snapshot.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(Tag.class.isAssignableFrom(field.getType())); assertNotEquals(GuardTicket.class, field.getType());
        }
        var token = Arrays.stream(ClassicHatchBlockEntity.class.getDeclaredClasses())
                .filter(type -> type.getSimpleName().equals("CompletedLoadJoin")).findFirst().orElseThrow();
        assertTrue(token.isRecord()); assertTrue(Modifier.isPrivate(token.getModifiers()));
        assertEquals(java.util.List.of("service", "lifetime", "storageEpoch"),
                Arrays.stream(token.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    @Test void observerSelectionIsImmutablePrivatelyConstructedAndHasNoOutcomeOrTypedTagField() {
        Class<?> selection = ClassicChunkObservation.EmptyRemovalSelection.class;
        assertTrue(Modifier.isFinal(selection.getModifiers())); assertPackageOnly(selection.getModifiers());
        for (var constructor : selection.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        for (var field : selection.getDeclaredFields()) {
            assertTrue(Modifier.isPrivate(field.getModifiers())); assertTrue(Modifier.isFinal(field.getModifiers()));
            assertFalse(Tag.class.isAssignableFrom(field.getType())); assertNotEquals(boolean.class, field.getType());
        }
        assertEquals(java.util.Set.of("observation", "ticket", "service", "owner", "chunk", "capture", "retained", "heldWitness"),
                Arrays.stream(selection.getDeclaredFields()).map(java.lang.reflect.Field::getName).collect(java.util.stream.Collectors.toSet()));
    }

    @Test void unboundNonPowerCheckpointDataHasNeitherEnergyBankBindingNorHandoff() {
        for (ClassicHatchKind kind : ClassicHatchKind.values()) {
            if (kind == ClassicHatchKind.POWER_INPUT) { continue; }
            var state = new ClassicHatchState(kind, Optional.empty(), Optional.empty(), OptionalInt.empty());
            var checkpoint = new ClassicHatchCheckpoint(state, Optional.empty());
            assertEquals(kind, checkpoint.view().kind()); assertTrue(state.binding().isEmpty());
            assertTrue(state.bankKey().isEmpty()); assertTrue(state.energy().isEmpty()); assertTrue(checkpoint.handoff().isEmpty());
            assertThrows(IllegalArgumentException.class,
                    () -> new ClassicHatchState(kind, Optional.empty(), Optional.empty(), OptionalInt.of(0)));
        }
    }

    private static void assertPackageOnly(int modifiers) {
        assertFalse(Modifier.isPublic(modifiers)); assertFalse(Modifier.isProtected(modifiers)); assertFalse(Modifier.isPrivate(modifiers));
    }
}
