package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketEscrowSectionTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void neutralEncodingIsExactAndFirstBindingDoesNotInventAnIdentity() {
        var neutral = RocketEscrowSection.neutral();
        var tag = neutral.encode();
        assertEquals(java.util.Set.of("schema_version", "resource_epoch", "markers"), tag.getAllKeys());
        assertEquals(neutral, RocketEscrowSection.decode(tag));
        UUID ledger = UUID.randomUUID();
        var bound = neutral.bind(ledger);
        assertEquals(ledger, bound.ledger());
        assertEquals(bound, RocketEscrowSection.decode(bound.encode()));
        assertEquals(bound, bound.bind(ledger));
        assertThrows(IllegalArgumentException.class, () -> bound.bind(UUID.randomUUID()));
    }
    @Test void neutralCannotCarryCreditOrMarkers() {
        assertThrows(IllegalArgumentException.class, () -> new RocketEscrowSection(null, 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> new RocketEscrowSection(null, 1,
                List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, "DESTINATION", 1))));
    }
    @Test void nativeResourceMutationPreservesNeutralityAndAdvancesOnlyAnUnmarkedBoundEpoch() {
        var neutral = RocketEscrowSection.neutral(); assertSame(neutral, neutral.afterNativeResourceMutation());
        var bound = new RocketEscrowSection(UUID.randomUUID(), 7, List.of());
        assertEquals(new RocketEscrowSection(bound.ledger(), 8, List.of()), bound.afterNativeResourceMutation());
        var marked = new RocketEscrowSection(bound.ledger(), 7, List.of(new EscrowPrefix.Marker(UUID.randomUUID(), 1, "DESTINATION", 7)));
        assertThrows(IllegalArgumentException.class, marked::afterNativeResourceMutation);
        var exhausted = new RocketEscrowSection(bound.ledger(), Long.MAX_VALUE, List.of());
        assertThrows(ArithmeticException.class, exhausted::afterNativeResourceMutation);
        assertEquals(Long.MAX_VALUE, exhausted.epoch());
    }
    @Test void eightMarkerLimitAndContiguousEpochsAreEnforced() {
        var markers = IntStream.rangeClosed(1, 8).mapToObj(index ->
                new EscrowPrefix.Marker(UUID.randomUUID(), index * 3L, "DESTINATION", index)).toList();
        var section = new RocketEscrowSection(UUID.randomUUID(), 8, markers);
        assertEquals(section, RocketEscrowSection.decode(section.encode()));
        var nine = new java.util.ArrayList<>(markers);
        nine.add(new EscrowPrefix.Marker(UUID.randomUUID(), 30, "DESTINATION", 9));
        assertThrows(IllegalArgumentException.class, () -> new RocketEscrowSection(section.ledger(), 9, nine));
        assertThrows(IllegalArgumentException.class, () -> new RocketEscrowSection(section.ledger(), 9, markers));
        assertThrows(IllegalArgumentException.class, () -> new RocketEscrowSection(section.ledger(), -1, List.of()));
    }
    @Test void wrongTypesUnknownAndFutureFieldsRemainInvalid() {
        var future = RocketEscrowSection.neutral().encode(); future.putInt("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(future));
        var unknown = RocketEscrowSection.neutral().encode(); unknown.putInt("other", 1);
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(unknown));
        var wrong = RocketEscrowSection.neutral().encode(); wrong.putInt("resource_epoch", 0);
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(wrong));
        var missing = RocketEscrowSection.neutral().encode(); missing.remove("markers");
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(missing));
        var oversized = RocketEscrowSection.neutral().encode();
        oversized.putByteArray("ledger_id", new byte[EscrowLimits.ENTITY_MARKER_BYTES]);
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(oversized));
        var badUuid = RocketEscrowSection.neutral().encode(); badUuid.putIntArray("ledger_id", new int[3]);
        assertThrows(IllegalArgumentException.class, () -> RocketEscrowSection.decode(badUuid));
    }
}
