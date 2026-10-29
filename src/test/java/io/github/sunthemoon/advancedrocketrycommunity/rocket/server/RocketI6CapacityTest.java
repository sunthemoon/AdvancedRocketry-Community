package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;

class RocketI6CapacityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void pendingWriteBackAndSealedHandoffShareTheSameSixtyFourSlots() throws Exception {
        var ordinary = new RocketOrdinaryRetirement(new HashSet<>(), new HashSet<>(), Set::of, ignored -> null);
        var pending = new HashSet<UUID>(); var sealed = new HashSet<UUID>();
        ordinary.shareSealedCapacity(sealed::size, () -> 0);
        var bind = RocketOrdinaryRetirement.class.getDeclaredMethod("shareLandedCapacity", Supplier.class, Predicate.class);
        bind.invoke(ordinary, (Supplier<Set<UUID>>) () -> Set.copyOf(pending), (Predicate<UUID>) sealed::contains);
        for (int i = 0; i < 64; i++) { pending.add(UUID.randomUUID()); }
        assertFalse(ordinary.pendingRoom(UUID.randomUUID()), "A write-back P still consumes pending capacity");
        var logical = pending.iterator().next();
        assertTrue(ordinary.pendingRoom(logical), "The P to B2 handoff must not count the same owner twice");
        sealed.add(logical);
        assertFalse(ordinary.pendingRoom(UUID.randomUUID()));
        pending.remove(logical);
        assertFalse(ordinary.pendingRoom(UUID.randomUUID()), "B2 retains the handed-off slot");
        sealed.clear(); assertTrue(ordinary.pendingRoom(UUID.randomUUID()));
    }
}
