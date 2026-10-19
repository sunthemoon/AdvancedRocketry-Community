package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Measurement of the pre-existing synchronous constructor; not a budget-admission PASS. */
class RocketCargoBudgetBoundaryTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    @Test void paletteCompressionDoesNotBoundRepeatedHashInputToOneMiB() {
        var properties = new TreeMap<String, String>();
        for (int i = 0; i < 32; i++) { properties.put(String.format("%02d", i) + "k".repeat(62), "v".repeat(64)); }
        var state = new RocketBlockState(ResourceLocation.tryParse("fixture:palette_shared"), properties);
        var blocks = new ArrayList<RocketBlock>();
        for (int i = 0; i < 2048; i++) { blocks.add(new RocketBlock(new RocketPosition(i % 16, i / 256, i / 16 % 16), state)); }
        var snapshot = CargoTestFixtures.snapshot(blocks);
        var encoded = RocketSnapshotNbtCodec.encode(snapshot);
        long structure = snapshot.structureDigestBytes();
        // No payloads: the content input differs only by the documented prefix and propulsion suffix.
        long content = structure - "arce-rocket-structure-1:".length() - 1 - 4 - snapshot.propulsion().orElseThrow().signature().length();
        int nativeBytes = RocketNbtSize.uncompressedBytes(encoded);
        System.out.println("W106 hash boundary: native=" + nativeBytes + " structure=" + structure + " content=" + content
                + " combined=" + (structure + content) + " budget=" + (16 * 1_048_576));
        assertTrue(nativeBytes < 1_048_576);
        assertTrue(RocketSnapshotNbtCodec.decode(encoded).snapshot().isPresent());
        assertTrue(structure + content > 16L * 1_048_576);
    }

    @Test void sameBoundaryExistsWithACodecValidTankAndAnExactlyRecomputableCargoDelta() {
        var properties = new TreeMap<String, String>();
        for (int i = 0; i < 32; i++) { properties.put(String.format("%02d", i) + "k".repeat(62), "v".repeat(64)); }
        var state = new RocketBlockState(ResourceLocation.tryParse("fixture:palette_shared"), properties);
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(new RocketPosition(0, 0, 0),
                new RocketBlockState(io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.TYPE, Map.of()),
                io.github.sunthemoon.advancedrocketrycommunity.rocket.forge.PressurizedTankRocketAdapter.payload(net.minecraftforge.fluids.FluidStack.EMPTY)));
        for (int i = 1; i < 2048; i++) { blocks.add(new RocketBlock(new RocketPosition(i % 16, i / 256, i / 16 % 16), state)); }
        var before = CargoTestFixtures.snapshot(blocks);
        var delta = CargoTestFixtures.delta(before, 0, new net.minecraftforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1));
        var after = RocketCargoMutation.apply(before, delta, true);
        long structure = after.structureDigestBytes();
        // Ignore the positive payload contribution: this is a lower bound, not an estimate of the total cost.
        long lowerBound = 2 * structure - "arce-rocket-structure-1:".length() - 1 - 4 - after.propulsion().orElseThrow().signature().length();
        int bytes = RocketNbtSize.uncompressedBytes(RocketSnapshotNbtCodec.encode(after));
        System.out.println("W106 codec-valid cargo boundary: native=" + bytes + " two-hash-lower-bound=" + lowerBound);
        assertTrue(bytes < 1_048_576); assertTrue(lowerBound > RocketCargoWorkBudget.MAX_BYTES);
        assertEquals(1, CargoTestFixtures.tank(after, 0).getAmount());
        assertEquals(after.contentHash(), RocketCargoMutation.apply(before, delta, true).contentHash());
    }
}
