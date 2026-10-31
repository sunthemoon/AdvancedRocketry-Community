package io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.lang.management.ManagementFactory;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StringTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

final class SatelliteNbtSizeTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void countMatchesTheNativeNamedRootIncludingModifiedUtfAndNestedTags() throws Exception {
        CompoundTag root = new CompoundTag();
        root.putString("\u0000\u0800", "x\u0000\u0080\u0800\ud83d\ude80");
        root.putByteArray("bytes", new byte[8193]);
        root.putIntArray("ints", new int[]{Integer.MIN_VALUE, 0, Integer.MAX_VALUE});
        root.putLongArray("longs", new long[]{Long.MIN_VALUE, Long.MAX_VALUE});
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("\u0000"));
        list.add(StringTag.valueOf("\u0800"));
        root.put("strings", list);
        CompoundTag nested = new CompoundTag();
        nested.putBoolean("flag", true);
        nested.putLong("epoch", Long.MAX_VALUE);
        root.put("nested", nested);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(root, output); }
        assertEquals(bytes.size(), SatelliteNbtSize.uncompressedBytes(root));
        assertEquals(4, SatelliteNbtSize.uncompressedBytes(new CompoundTag()));
    }

    @Test void countingANearBoundPayloadDoesNotAllocateAnotherPayloadBuffer() {
        var bean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        assertTrue(bean.isThreadAllocatedMemorySupported());
        if (!bean.isThreadAllocatedMemoryEnabled()) { bean.setThreadAllocatedMemoryEnabled(true); }
        CompoundTag root = new CompoundTag();
        root.putByteArray("payload", new byte[16 * 1024 * 1024]);
        for (int i = 0; i < 3; i++) { SatelliteNbtSize.uncompressedBytes(new CompoundTag()); }
        long before = bean.getThreadAllocatedBytes(Thread.currentThread().getId());
        int size = SatelliteNbtSize.uncompressedBytes(root);
        long allocated = bean.getThreadAllocatedBytes(Thread.currentThread().getId()) - before;
        assertEquals(16 * 1024 * 1024 + 18, size);
        assertTrue(allocated < 256 * 1024, "Count-only transient allocation: " + allocated);
    }
}
