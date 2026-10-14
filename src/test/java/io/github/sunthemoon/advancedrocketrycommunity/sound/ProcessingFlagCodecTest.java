package io.github.sunthemoon.advancedrocketrycommunity.sound;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Set;
import net.minecraft.nbt.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ProcessingFlagCodecTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void canonicalBooleansRoundTripWithoutSavedRoots() {
        for (boolean active : List.of(false, true)) {
            CompoundTag tag = ProcessingFlagCodec.encode(active);
            assertEquals(Set.of("active"), tag.getAllKeys());
            assertEquals(Tag.TAG_BYTE, tag.get("active").getId());
            assertEquals(active, ProcessingFlagCodec.decode(tag));
        }
    }

    @Test void missingFlagIsInactive() {
        assertFalse(ProcessingFlagCodec.decode(null));
        assertFalse(ProcessingFlagCodec.decode(new CompoundTag()));
    }

    @Test void onlyByteOneIsActive() {
        for (int value = Byte.MIN_VALUE; value <= Byte.MAX_VALUE; value++) {
            CompoundTag tag = new CompoundTag();
            tag.putByte("active", (byte) value);
            assertEquals(value == 1, ProcessingFlagCodec.decode(tag), "byte " + value);
        }
    }

    @Test void wrongTagTypesAreInactiveEvenWhenNumericallyOne() {
        for (Tag value : List.of(ShortTag.valueOf((short) 1), IntTag.valueOf(1), LongTag.valueOf(1),
                FloatTag.valueOf(1), DoubleTag.valueOf(1), StringTag.valueOf("true"), new CompoundTag(),
                new ListTag(), new ByteArrayTag(new byte[] {1}), new IntArrayTag(new int[] {1}),
                new LongArrayTag(new long[] {1}))) {
            CompoundTag tag = new CompoundTag();
            tag.put("active", value);
            assertFalse(ProcessingFlagCodec.decode(tag), value.getClass().getSimpleName());
        }
    }

    @Test void invalidUpdateReplacesPreviouslyActiveInputWithoutMutatingTag() {
        CompoundTag tag = ProcessingFlagCodec.encode(true);
        assertTrue(ProcessingFlagCodec.decode(tag));
        tag.putString("active", "true");
        CompoundTag before = tag.copy();
        assertFalse(ProcessingFlagCodec.decode(tag));
        assertEquals(before, tag);
    }
}
