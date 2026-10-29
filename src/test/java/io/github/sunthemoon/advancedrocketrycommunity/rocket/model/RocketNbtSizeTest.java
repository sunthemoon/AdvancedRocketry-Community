package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.management.ThreadMXBean;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UTFDataFormatException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class RocketNbtSizeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void countingDoesNotAllocateASecondPayloadBuffer() {
        var bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported(), "Allocation regression requires the supported Java 17 JVM");
        bean.setThreadAllocatedMemoryEnabled(true);
        var root = new CompoundTag();
        root.putByteArray("payload", new byte[2 * 1024 * 1024]);
        for (int i = 0; i < 20; i++) { RocketNbtSize.uncompressedBytes(root); }
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        int bytes = RocketNbtSize.uncompressedBytes(root);
        long allocated = bean.getThreadAllocatedBytes(thread) - before;
        assertEquals(2 * 1024 * 1024 + 18, bytes);
        assertTrue(allocated < 64 * 1024, "Sizing allocated " + allocated + " bytes for an unused payload buffer");
    }

    @Test void exactLimitAndOneByteOverAgreeWithTheMaterializingWriter() throws IOException {
        for (int excess : new int[] {0, 1}) {
            var root = new CompoundTag();
            root.putString("modified\u0000utf", "\u0000\ud83d\ude80\u0800");
            root.putByteArray("padding", new byte[0]);
            int size = RocketLimits.MAX_TOTAL_NBT_BYTES + excess;
            root.putByteArray("padding", new byte[size - legacySize(root)]);
            assertEquals(size, legacySize(root));
            assertEquals(size, RocketNbtSize.uncompressedBytes(root));
            assertEquals(excess > 0, RocketNbtSize.uncompressedBytes(root) > RocketLimits.MAX_TOTAL_NBT_BYTES);
        }
    }

    @Test void allTagWidthsNestedListsAndModifiedUtfUseTheActualWriter() throws IOException {
        var root = new CompoundTag();
        root.putByte("byte", (byte) -1); root.putShort("short", (short) -2);
        root.putInt("int", -3); root.putLong("long", Long.MAX_VALUE);
        root.putFloat("float", Float.NaN); root.putDouble("double", Double.NEGATIVE_INFINITY);
        root.putByteArray("bytes", new byte[] {0, -1, 2});
        root.putIntArray("ints", new int[] {0, -1, Integer.MAX_VALUE});
        root.putLongArray("longs", new long[] {0, -1, Long.MAX_VALUE});
        root.put("empty", new CompoundTag()); root.put("empty_list", new ListTag());
        var nested = new ListTag(); nested.add(root.copy()); nested.add(root.copy());
        root.put("nested", nested);
        for (String value : new String[] {"", "ascii", "\u0000", "\ud83d\ude80", "\ud800", "\udc00",
                "\u007f\u0080\u07ff\u0800\uffff", "\u0000\ud83d\ude80".repeat(1000)}) {
            root.putString(value, value);
            var strings = new ListTag(); strings.add(StringTag.valueOf(value)); root.put("strings", strings);
            assertEquals(legacySize(root), RocketNbtSize.uncompressedBytes(root));
        }
        assertEquals(1, "\u0000".getBytes(StandardCharsets.UTF_8).length);
        assertEquals(4, "\ud83d\ude80".getBytes(StandardCharsets.UTF_8).length);
        var empty = new CompoundTag(); empty.putString("s", "");
        int framing = legacySize(empty);
        empty.putString("s", "\u0000\ud83d\ude80");
        assertEquals(framing + 8, RocketNbtSize.uncompressedBytes(empty));
    }

    @Test void modifiedUtfKeyBoundaryAndWriterFailureRemainIdentical() throws IOException {
        var root = new CompoundTag();
        root.putInt("\u0000".repeat(32767) + "a", 7); // 65,535 modified-UTF bytes, not UTF-8 bytes.
        assertEquals(legacySize(root), RocketNbtSize.uncompressedBytes(root));
        root.putInt("\u0000".repeat(32768), 7);
        var oldFailure = assertThrows(UTFDataFormatException.class, () -> legacySize(root));
        var failure = assertThrows(IllegalStateException.class, () -> RocketNbtSize.uncompressedBytes(root));
        assertEquals("In-memory NBT sizing failed", failure.getMessage());
        assertEquals(oldFailure.getClass(), failure.getCause().getClass());
        assertEquals(oldFailure.getMessage(), failure.getCause().getMessage());
    }

    private static int legacySize(CompoundTag root) throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(root, output); }
        return bytes.size();
    }
}
