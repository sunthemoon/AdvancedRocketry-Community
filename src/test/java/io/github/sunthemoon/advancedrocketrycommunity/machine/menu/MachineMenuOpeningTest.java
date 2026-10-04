package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.SimpleContainerData;
import org.junit.jupiter.api.Test;

class MachineMenuOpeningTest {
    @Test
    void eachCountUsesExactTenBytesAndPreservesPosition() {
        BlockPos[] positions = {BlockPos.ZERO, new BlockPos(-30_000_000, -64, 30_000_000),
                new BlockPos(30_000_000, 319, -30_000_000)};
        for (int count : new int[] {25, 23, 8}) {
            for (BlockPos position : positions) {
                FriendlyByteBuf buffer = buffer();
                try {
                    MachineMenuOpening.write(buffer, position, count);
                    assertEquals(10, buffer.readableBytes());
                    assertEquals(2, buffer.getUnsignedByte(8));
                    assertEquals(count, buffer.getUnsignedByte(9));
                    assertEquals(position, MachineMenuOpening.read(buffer, count));
                    assertEquals(0, buffer.readableBytes());
                } finally { buffer.release(); }
            }
        }
    }

    @Test
    void missingTruncatedOrTrailingPayloadIsRejectedBeforeConsuming() {
        assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.read(null, 25));
        for (int length = 0; length <= 12; length++) {
            if (length == 10) { continue; }
            FriendlyByteBuf buffer = buffer();
            try {
                buffer.writeZero(length);
                assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.read(buffer, 25));
                assertEquals(0, buffer.readerIndex());
            } finally { buffer.release(); }
        }
    }

    @Test
    void unsignedSchemaAndCountMustMatchExactlyWithoutConsumptionOnRefusal() {
        for (int count : new int[] {25, 23, 8}) {
            for (int value = 0; value <= 255; value++) {
                if (value != 2) { rejectTrailer(value, count, count); }
                if (value != count) { rejectTrailer(2, value, count); }
            }
        }
    }

    @Test
    void nativeMinimumIsNotEnoughBothUnderAndOverCountAreRejected() {
        for (int expected : new int[] {25, 23, 8}) {
            MachineMenuOpening.requireExactDataCount(new SimpleContainerData(expected), expected);
            for (int count : new int[] {0, expected - 1, expected + 1, 256}) {
                assertThrows(IllegalArgumentException.class, () ->
                        MachineMenuOpening.requireExactDataCount(new SimpleContainerData(count), expected));
            }
            assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.requireExactDataCount(null, expected));
        }
    }

    @Test
    void writerRefusesUnsupportedCountAndExistingPayloadBeforeWriting() {
        FriendlyByteBuf buffer = buffer();
        try {
            assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.write(buffer, BlockPos.ZERO, 24));
            assertEquals(0, buffer.writerIndex());
            buffer.writeByte(99);
            assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.write(buffer, BlockPos.ZERO, 25));
            assertEquals(1, buffer.writerIndex());
            assertEquals(99, buffer.getUnsignedByte(0));
        } finally { buffer.release(); }
    }

    private static void rejectTrailer(int schema, int count, int expected) {
        FriendlyByteBuf buffer = buffer();
        try {
            buffer.writeBlockPos(BlockPos.ZERO); buffer.writeByte(schema); buffer.writeByte(count);
            assertThrows(IllegalArgumentException.class, () -> MachineMenuOpening.read(buffer, expected));
            assertEquals(0, buffer.readerIndex());
        } finally { buffer.release(); }
    }

    private static FriendlyByteBuf buffer() { return new FriendlyByteBuf(Unpooled.buffer()); }
}
