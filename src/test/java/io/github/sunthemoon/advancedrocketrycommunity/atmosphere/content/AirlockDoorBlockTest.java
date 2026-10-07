package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.content;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan.CellObservation;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class AirlockDoorBlockTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void disabledPlacementStopsBeforeEvenReadingTheContext() {
        AtomicInteger checks = new AtomicInteger();
        var door = new AirlockDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR), () -> {
            checks.incrementAndGet(); return false;
        });
        assertNull(door.getStateForPlacement(null));
        assertEquals(1, checks.get());
        assertThrows(NullPointerException.class, () -> new AirlockDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR), null));
        assertFalse(door.defaultBlockState().hasBlockEntity());
    }

    @Test void consistentClosedAndOpenPairsHaveSymmetricBoundedClassification() {
        var door = door();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (DoorHingeSide hinge : DoorHingeSide.values()) {
                for (boolean powered : new boolean[] {false, true}) {
                    for (boolean open : new boolean[] {false, true}) {
                        for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                            BlockPos local = new BlockPos(3, 20, 4);
                            BlockPos other = half == DoubleBlockHalf.LOWER ? local.above() : local.below();
                            BlockState state = door.defaultBlockState().setValue(DoorBlock.HALF, half)
                                    .setValue(DoorBlock.FACING, facing).setValue(DoorBlock.HINGE, hinge)
                                    .setValue(DoorBlock.POWERED, powered).setValue(DoorBlock.OPEN, open);
                            BlockState counterpart = state.setValue(DoorBlock.HALF,
                                    half == DoubleBlockHalf.LOWER ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
                            List<BlockPos> reads = new ArrayList<>();
                            assertEquals(open ? CellObservation.TRAVERSABLE : CellObservation.SEALED,
                                    door.observeBoundary(world(counterpart, null, null, reads), local, state));
                            assertEquals(List.of(other), reads);
                        }
                    }
                }
            }
        }
    }

    @Test void malformedPairsNeverSealOrNeedMoreThanOneCounterpartRead() {
        var door = door(); BlockPos pos = new BlockPos(2, 25, 2);
        BlockState lower = door.defaultBlockState(), upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        var different = door();
        for (BlockState invalid : List.of(Blocks.AIR.defaultBlockState(), Blocks.IRON_DOOR.defaultBlockState(),
                different.defaultBlockState().setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), lower,
                upper.cycle(DoorBlock.FACING), upper.cycle(DoorBlock.HINGE), upper.cycle(DoorBlock.POWERED), upper.cycle(DoorBlock.OPEN))) {
            List<BlockPos> reads = new ArrayList<>();
            assertEquals(CellObservation.OPEN, door.observeBoundary(world(invalid, null, null, reads), pos, lower));
            assertEquals(List.of(pos.above()), reads);
        }
        List<BlockPos> reads = new ArrayList<>();
        assertEquals(CellObservation.OPEN, door.observeBoundary(world(upper, null, null, reads), pos, Blocks.AIR.defaultBlockState()));
        assertTrue(reads.isEmpty());
    }

    @Test void heightAndLoadedGuardsPrecedeEveryCounterpartRead() {
        var door = door(); BlockPos pos = new BlockPos(1, 30, 1); BlockState lower = door.defaultBlockState();
        for (BlockPos outside : List.of(pos, pos.above())) {
            List<BlockPos> reads = new ArrayList<>();
            assertEquals(CellObservation.OPEN, door.observeBoundary(world(null, outside, null, reads), pos, lower));
            assertTrue(reads.isEmpty());
        }
        for (BlockPos missing : List.of(pos, pos.above())) {
            List<BlockPos> reads = new ArrayList<>();
            assertEquals(CellObservation.UNLOADED, door.observeBoundary(world(null, null, missing, reads), pos, lower));
            assertTrue(reads.isEmpty());
        }
        BlockState upper = lower.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER);
        List<BlockPos> reads = new ArrayList<>();
        assertEquals(CellObservation.OPEN, door.observeBoundary(world(null, pos.below(), null, reads), pos, upper));
        assertTrue(reads.isEmpty());
    }

    private static AirlockDoorBlock door() {
        return new AirlockDoorBlock(BlockBehaviour.Properties.copy(Blocks.IRON_DOOR), () -> true);
    }

    /** Interface-only query fixture: any loading, repair, collision or unlisted call is an error. */
    private static LevelReader world(BlockState other, BlockPos outside, BlockPos unloaded, List<BlockPos> reads) {
        return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "isOutsideBuildHeight" -> args[0].equals(outside);
                    case "hasChunkAt" -> !args[0].equals(unloaded);
                    case "getBlockState" -> { reads.add(((BlockPos) args[0]).immutable()); yield java.util.Objects.requireNonNull(other); }
                    default -> throw new AssertionError("Unexpected world query: " + method);
                });
    }
}
