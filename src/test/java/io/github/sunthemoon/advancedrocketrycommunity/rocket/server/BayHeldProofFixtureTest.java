package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class BayHeldProofFixtureTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void elevenBlockSeparationCanFinishWithoutAPartialCaptureCallback() {
        var origin = new BlockPos(2, 80, 3);
        assertEquals(1, slots(origin, origin.offset(11, 0, 0)), "Both bounded ends occupy one deduplicated slot");
    }

    @Test void fixtureHasAPartialCaptureBoundaryAtEveryChunkAlignment() {
        for (int base : new int[] {-32, 0, 32}) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int claim = 0; claim < 2; claim++) {
                        var origin = new BlockPos(base + x, 80, base + z + claim * 3);
                        int count = slots(origin, BayHeldProofGameTests.targetOrigin(origin));
                        assertTrue(count > 1, "No partial capture at " + origin);
                        assertTrue(count <= RocketOrdinaryRetirement.MAX_SLOTS, "Fixture exceeds the existing slot bound");
                    }
                }
            }
        }
    }

    private static int slots(BlockPos origin, BlockPos target) {
        var dimension = new ResourceLocation("minecraft", "overworld");
        // The GameTest's base block and vertical bay share this one-column horizontal footprint.
        var source = RocketMarginTicketsTest.snapshot(dimension, origin.getX(), origin.getZ(), 0);
        var destination = RocketMarginTicketsTest.snapshot(dimension, target.getX(), target.getZ(), 0);
        var slots = BayAbsenceCensus.slots(source, destination);
        assertNotNull(slots);
        assertEquals(RocketRetirementProof.Slot.origin(source), slots.get(0));
        return slots.size();
    }
}
