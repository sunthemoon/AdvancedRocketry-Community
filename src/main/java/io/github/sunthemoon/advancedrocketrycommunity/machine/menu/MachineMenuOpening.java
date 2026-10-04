package io.github.sunthemoon.advancedrocketrycommunity.machine.menu;

import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.inventory.ContainerData;

/** Exact local opening frame, independent of required-channel peer admission. */
public final class MachineMenuOpening {
    public static final int SCHEMA = 2;
    public static final int PAYLOAD_BYTES = 10;

    private MachineMenuOpening() { }

    public static BlockPos read(FriendlyByteBuf buffer, int expectedCount) {
        requireSupportedCount(expectedCount);
        if (buffer == null || buffer.readableBytes() != PAYLOAD_BYTES) {
            throw new IllegalArgumentException("Machine menu opening must contain exactly ten bytes");
        }
        // Inspect both unsigned trailer bytes before lookup or menu construction.
        int start = buffer.readerIndex();
        if (buffer.getUnsignedByte(start + 8) != SCHEMA
                || buffer.getUnsignedByte(start + 9) != expectedCount) {
            throw new IllegalArgumentException("Unsupported machine menu schema or data count");
        }
        BlockPos position = buffer.readBlockPos();
        buffer.skipBytes(2);
        return position;
    }

    public static void write(FriendlyByteBuf buffer, BlockPos position, int count) {
        requireSupportedCount(count);
        Objects.requireNonNull(buffer, "buffer");
        Objects.requireNonNull(position, "position");
        if (buffer.writerIndex() != 0) {
            throw new IllegalArgumentException("Machine menu opening requires an empty buffer");
        }
        buffer.writeBlockPos(position);
        buffer.writeByte(SCHEMA);
        buffer.writeByte(count);
    }

    public static void requireExactDataCount(ContainerData data, int expectedCount) {
        requireSupportedCount(expectedCount);
        if (data == null || data.getCount() != expectedCount) {
            throw new IllegalArgumentException("Machine menu data count does not match its schema");
        }
    }

    private static void requireSupportedCount(int count) {
        if (count != 25 && count != 23 && count != 8) {
            throw new IllegalArgumentException("Unsupported machine menu data count");
        }
    }
}
