package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketNbtSize;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CargoPressureWorkloadTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void sixtyFourRecordsKeepPayloadAndEncodedBytesWithTwoFourMemberChains() {
        var records = new ArrayList<EscrowOperation>(); var historical = new ArrayList<EscrowOperation>();
        for (int i = 0; i < 64; i++) {
            int component = CargoNativePressureWorkload.component(i);
            var op = CargoNativePressureWorkload.operation(EscrowTestFixtures.operation(i + 1, component + 1, 0), i);
            records.add(op);
            var old = EscrowTestFixtures.operation(i + 1, i < 8 ? 1 : i + 1, 0);
            historical.add(i < 8 ? CargoNativePressureWorkload.chained(old, i) : old);
            assertEquals(RocketNbtSize.uncompressedBytes(EscrowCodec.operation(historical.get(i))),
                    RocketNbtSize.uncompressedBytes(EscrowCodec.operation(op)));
            assertEquals(1000, op.payload().getInt("Amount"));
            assertEquals(i < 8 ? i % 4 : 0, op.destination().beforeEpoch());
        }
        var ledger = EscrowTestFixtures.ledger(records.toArray(EscrowOperation[]::new));
        assertEquals(64, ledger.records().size());
        assertEquals(58, records.stream().map(o -> o.destination().key()).distinct().count());
        assertEquals(RocketNbtSize.uncompressedBytes(EscrowCodec.encode(EscrowTestFixtures.ledger(historical.toArray(EscrowOperation[]::new)))),
                RocketNbtSize.uncompressedBytes(EscrowCodec.encode(ledger)));
        for (var op : records) {
            var component = EscrowSettlementIntent.component(records, op.id());
            assertEquals(op.sequence() <= 8 ? 4 : 1, component.size());
            assertDoesNotThrow(() -> EscrowSettlementCapacity.requireAdmission(ledger, op, null));
            // Component admission sizing, not a claim that the global root reservation admits all 64.
            assertDoesNotThrow(() -> EscrowSettlementCapacity.preflight(
                    EscrowTestFixtures.ledger(component.toArray(EscrowOperation[]::new)), op.id()));
        }
    }
    @Test void indicesOutsideTheFixedWorkloadAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> CargoNativePressureWorkload.component(-1));
        assertThrows(IllegalArgumentException.class, () -> CargoNativePressureWorkload.component(64));
        assertEquals(List.of(0, 0, 0, 0, 4, 4, 4, 4, 8),
                java.util.stream.IntStream.range(0, 9).map(CargoNativePressureWorkload::component).boxed().toList());
    }
}
