package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class CargoBenchmarkBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void completeFramingAndO1FindAdjacentByteDepthNodeRefusalsForAllDevices() {
        var rows = CargoBenchmarkBoundaries.measure(); assertEquals(12, rows.size());
        for (var row : rows) {
            var yes = (Map<?, ?>) row.get("admitted"); var no = (Map<?, ?>) row.get("rejected");
            assertEquals("ELIGIBLE", yes.get("status")); assertEquals("CARGO_TOO_LARGE", no.get("status"));
            assertEquals((int) yes.get("parameter") + 1, no.get("parameter"));
            assertEquals(true, yes.get("unmutated")); assertEquals(true, no.get("unmutated"));
        }
    }
    @Test void nativeMaximumIsAnExactWholeCodecBoundaryForEveryDevice() {
        for (boolean item : new boolean[] {false, true}) { for (boolean load : new boolean[] {true, false}) {
            var base = CargoBenchmarkFixtures.small(item, load, -1).before();
            if (item) {
                var block = new io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketBlock(
                        base.blocks().get(0).position(), base.blocks().get(0).state(),
                        io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.container(null, null,
                            io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.entry(0,
                                ItemCargoReconciliationFixtures.stack(load ? 60 : 10))));
                base = io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.CargoTestFixtures.snapshot(java.util.List.of(block));
            }
            var max = CargoNativeFixtures.largest(base, item, load);
            assertEquals(2048, max.blocks().size());
            assertEquals(128, max.blocks().stream().filter(b -> b.blockEntityPayload().isPresent()).count());
            assertEquals(1048576, Math.max(CargoNativeFixtures.encoded(max), CargoNativeFixtures.encoded(CargoNativeFixtures.after(max, item, load))));
            assertFalse(CargoNativeFixtures.fits(base, CargoNativeFixtures.aggregate(max) + 1, item, load));
            assertFalse(CargoNativeFixtures.fits(base, 1048576, item, load));
        } }
    }
}
