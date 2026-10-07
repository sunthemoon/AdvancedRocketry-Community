package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class ClassicGuardAdmissionTest {
    @Test void ticketCannotBeConstructedOrPublishedByPublicStructuralValues() {
        assertFalse(Modifier.isPublic(GuardTicket.class.getModifiers()));
        for (var constructor : GuardTicket.class.getDeclaredConstructors()) { assertTrue(Modifier.isPrivate(constructor.getModifiers())); }
        for (var method : GuardTicket.class.getDeclaredMethods()) {
            if (!method.getName().equals("close")) { assertFalse(Modifier.isPublic(method.getModifiers())); }
        }
        assertFalse(Modifier.isPublic(ClassicAccessCoordinator.class.getModifiers()));
        assertFalse(Modifier.isPublic(ClassicRawPermit.class.getModifiers()));
    }

    @Test void coordinatorAndLoadedWorldRejectAbsentActualDependencies() {
        assertThrows(NullPointerException.class, () -> new ClassicLoadedWorld(null));
        assertThrows(NullPointerException.class, () -> new ClassicAccessCoordinator(null));
        assertThrows(NullPointerException.class, () -> new ClassicFamilyService(null, null));
        assertThrows(NullPointerException.class, () -> GuardTicket.acquire(null, null, java.util.List.of(), ClassicTicketPurpose.LOAD));
    }

    @Test void rawAndGameplayPurposeInventoriesRemainDistinct() {
        assertEquals(java.util.List.of("CAPTURE", "EMIT"), java.util.Arrays.stream(ClassicRawPurpose.values()).map(Enum::name).toList());
        assertEquals(java.util.List.of("CAPABILITY", "LIFECYCLE", "LOAD", "COMPLETION", "RECOVERY"),
                java.util.Arrays.stream(ClassicTicketPurpose.values()).map(Enum::name).toList());
    }
}
