package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Item normalization retries accept only the frozen old wrapper or its prescribed bound form. */
class EscrowItemSettlementNormalizationTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void oldAndPrescribedWrappersRetainTypedCargoButOnlyThePrescribedFormProvesO2() {
        for (boolean load : List.of(true, false)) {
            var f = EscrowItemReconciliationFixture.create(load);
            var old = EscrowItemSettlementObservationTest.rocket(f, f.before());
            var chosen = old.checkpointed();
            var normalized = f.before().copy(); normalized.put("wrapper", old.normalizedWrapper().encode());
            assertFalse(old.bound()); assertTrue(chosen.bound());
            assertEquals(chosen, EscrowItemSettlementObservationTest.rocket(f, normalized));
            assertEquals(old, EscrowSettlementIntent.decodeObservation(EscrowSettlementIntent.encodeObservation(old)));
            assertEquals(chosen, chosen.checkpointed());
            assertEquals(f.before().getCompound("snapshot"), normalized.getCompound("snapshot"));
            var validator = EscrowSettlementDisk.validator(chosen, f.ledger().id());
            assertFalse(validator.test(EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), f.before())));
            assertTrue(validator.test(EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), normalized)));

            var foreign = normalized.copy(); foreign.put("wrapper", new RocketEscrowSection(EscrowTestFixtures.id(991), 0, List.of()).encode());
            var epoch = normalized.copy(); epoch.put("wrapper", new RocketEscrowSection(f.ledger().id(), 1, List.of()).encode());
            var marker = normalized.copy(); marker.put("wrapper", new RocketEscrowSection(f.ledger().id(), 1,
                    List.of(new EscrowPrefix.Link(f.op(), f.op().rocket(), load ? "DESTINATION" : "SOURCE").marker())).encode());
            var owner = normalized.copy(); owner.putUUID("owner", EscrowTestFixtures.id(992));
            var physical = normalized.copy(); physical.putUUID("physical", EscrowTestFixtures.id(993));
            var logical = normalized.copy(); logical.putUUID("logical", EscrowTestFixtures.id(994));
            var content = f.after().copy(); content.put("wrapper", old.normalizedWrapper().encode());
            for (var changed : List.of(foreign, epoch, marker, owner, physical, logical, content)) {
                assertFalse(validator.test(EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), changed)),
                        "A changed identity, wrapper or cargo is not the prescribed normalization");
            }
            var moved = EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), normalized);
            moved.getList("Entities", 10).getCompound(0).getList("Pos", 6).set(0, net.minecraft.nbt.DoubleTag.valueOf(0.75));
            assertFalse(validator.test(moved));
            var occupied = EscrowReconciliationCheckpointTest.rocketSlot(f.op().rocket(), normalized);
            var passengers = new net.minecraft.nbt.ListTag(); passengers.add(new CompoundTag());
            occupied.getList("Entities", 10).getCompound(0).put("Passengers", passengers);
            assertFalse(validator.test(occupied));
        }
    }
}
