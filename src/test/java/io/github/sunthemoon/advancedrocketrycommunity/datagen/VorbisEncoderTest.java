package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.lwjgl.PointerBuffer;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;

/**
 * The project-written Vorbis encoder against LWJGL's stb_vorbis, the decoder Minecraft 1.20.1 uses for every OGG
 * sound: whole-file decoding and the push-data frame decoding of Minecraft's OggAudioStream.
 */
class VorbisEncoderTest {
    private static final int RATE = 22_050;

    @Test
    void aLoopedSineDecodesToTheSameWaveformThroughBothStbVorbisPathsIncludingTheSeam() {
        float[] pcm = new float[VorbisEncoder.HOP * 24];
        for (int i = 0; i < pcm.length; i++) {
            // 490 whole cycles over the file, about 440 Hz: periodic, so the loop has no step at its seam.
            pcm[i] = (float) (0.5 * StrictMath.sin(2 * StrictMath.PI * 490 * i / pcm.length));
        }
        byte[] ogg = VorbisEncoder.encode(pcm, RATE, true, 7, List.of("TITLE=sine"));
        float[] whole = decodeMemory(ogg);
        float[] pushed = decodePushData(ogg);
        assertEquals(pcm.length, whole.length);
        assertEquals(pcm.length, pushed.length);
        assertTrue(maxError(pcm, whole) < 0.01, "whole-file error " + maxError(pcm, whole));
        assertTrue(maxError(pcm, pushed) < 0.01, "push-data error " + maxError(pcm, pushed));
    }

    @Test
    void aFadedOneShotDecodesWithoutEdgeNoise() {
        float[] pcm = new float[VorbisEncoder.HOP * 12];
        for (int i = 0; i < pcm.length; i++) {
            double fade = Math.min(1.0, Math.min(i, pcm.length - 1 - i) / 2048.0);
            pcm[i] = (float) (0.7 * fade * StrictMath.sin(2 * StrictMath.PI * 300 * i / RATE));
        }
        float[] decoded = decodePushData(VorbisEncoder.encode(pcm, RATE, false, 3, List.of()));
        assertEquals(pcm.length, decoded.length);
        // The 8-bit per-frame quantizer leaves about -42 dB of coding noise (measured 42.1 dB, 0.0118 peak).
        assertTrue(snr(pcm, decoded) > 38, "one-shot signal-to-noise " + snr(pcm, decoded) + " dB");
        assertTrue(maxError(pcm, decoded) < 0.02, "one-shot error " + maxError(pcm, decoded));
    }

    @Test
    void broadbandNoiseKeepsAtLeastThirtyDecibelsOfSignalToNoise() {
        float[] pcm = new float[VorbisEncoder.HOP * 16];
        long state = 0x9E3779B97F4A7C15L;
        for (int i = 0; i < pcm.length; i++) {
            state ^= state << 13;
            state ^= state >>> 7;
            state ^= state << 17;
            pcm[i] = (float) (0.6 * ((state >>> 11) / (double) (1L << 53) * 2.0 - 1.0));
        }
        float[] decoded = decodePushData(VorbisEncoder.encode(pcm, RATE, false, 9, List.of()));
        assertEquals(pcm.length, decoded.length);
        assertTrue(snr(pcm, decoded) > 30, "signal-to-noise " + snr(pcm, decoded) + " dB");
    }

    @Test
    void silenceAndAQuietTailStayValidStreams() {
        float[] silent = new float[VorbisEncoder.HOP * 3];
        float[] decoded = decodePushData(VorbisEncoder.encode(silent, RATE, true, 1, List.of()));
        assertEquals(silent.length, decoded.length);
        for (float sample : decoded) {
            assertEquals(0.0f, sample, 0.0f);
        }
        float[] burst = new float[VorbisEncoder.HOP * 4];
        for (int i = 0; i < 200; i++) {
            burst[i] = (float) StrictMath.sin(i * 0.3) * 0.8f;
        }
        float[] tail = decodeMemory(VorbisEncoder.encode(burst, RATE, false, 2, List.of()));
        assertEquals(burst.length, tail.length);
        // An abrupt 200-sample burst spreads pre-echo over its 2,048-sample blocks (measured 34.9 dB).
        assertTrue(snr(burst, tail) > 30, "burst signal-to-noise " + snr(burst, tail) + " dB");
    }

    @Test
    void encodingIsDeterministicAndCarriesTheIdentificationAndComments() {
        float[] pcm = new float[VorbisEncoder.HOP * 2];
        for (int i = 0; i < pcm.length; i++) {
            pcm[i] = (float) StrictMath.sin(i * 0.01) * 0.25f;
        }
        byte[] first = VorbisEncoder.encode(pcm, RATE, true, 42, List.of("TITLE=one", "LICENSE=MIT"));
        assertArrayEquals(first, VorbisEncoder.encode(pcm, RATE, true, 42, List.of("TITLE=one", "LICENSE=MIT")));
        String text = new String(first, StandardCharsets.ISO_8859_1);
        assertTrue(text.startsWith("OggS"));
        assertTrue(text.contains("\u0001vorbis") && text.contains("\u0003vorbis") && text.contains("\u0005vorbis"));
        assertTrue(text.contains("TITLE=one") && text.contains("LICENSE=MIT"));
        // The identification packet is exactly 30 bytes alone on the first page, as stb_vorbis requires.
        assertEquals(1, first[26]);
        assertEquals(30, first[27]);
    }

    @Test
    void everyPageCarriesTheOggChecksumAndPagesEndWithTheLastSample() {
        float[] pcm = new float[VorbisEncoder.HOP * 40];
        long state = 12345L;
        for (int i = 0; i < pcm.length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            pcm[i] = (float) ((state >>> 40) / (double) (1L << 24) - 0.5);
        }
        byte[] ogg = VorbisEncoder.encode(pcm, RATE, false, 5, List.of());
        int offset = 0;
        int pages = 0;
        long granule = -1;
        int flags = 0;
        while (offset < ogg.length) {
            assertEquals("OggS", new String(ogg, offset, 4, StandardCharsets.US_ASCII));
            flags = ogg[offset + 5];
            granule = littleEndian(ogg, offset + 6, 8);
            assertEquals(pages, (int) littleEndian(ogg, offset + 18, 4), "page sequence");
            int segments = ogg[offset + 26] & 0xFF;
            int body = 0;
            for (int i = 0; i < segments; i++) {
                body += ogg[offset + 27 + i] & 0xFF;
            }
            int length = 27 + segments + body;
            byte[] page = java.util.Arrays.copyOfRange(ogg, offset, offset + length);
            long stored = littleEndian(page, 22, 4);
            for (int i = 22; i < 26; i++) {
                page[i] = 0;
            }
            assertEquals(stored, oggCrc(page) & 0xFFFFFFFFL, "page " + pages + " checksum");
            assertEquals(pages == 0 ? 0x02 : 0, flags & 0x02);
            offset += length;
            pages++;
        }
        assertEquals(0x04, flags & 0x04, "the last page ends the stream");
        assertEquals(pcm.length, granule);
        assertTrue(pages > 4, "broadband audio spans several pages: " + pages);
    }

    @Test
    void invalidInputsAreRejectedBeforeEncoding() {
        int hop = VorbisEncoder.HOP;
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[0], RATE, false, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[hop + 1], RATE, false, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[VorbisEncoder.MAX_SAMPLES + hop], RATE, false, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[hop], 7_999, false, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[hop], 48_001, false, 0, List.of()));
        float[] loud = new float[hop];
        loud[3] = 1.001f;
        assertThrows(IllegalArgumentException.class, () -> VorbisEncoder.encode(loud, RATE, false, 0, List.of()));
        float[] invalid = new float[hop];
        invalid[5] = Float.NaN;
        assertThrows(IllegalArgumentException.class, () -> VorbisEncoder.encode(invalid, RATE, true, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> VorbisEncoder.encode(new float[hop], RATE, false, 0, List.of("no separator")));
    }

    /** Whole-file decoding (stb_vorbis_decode_memory), converted from 16-bit PCM. */
    static float[] decodeMemory(byte[] ogg) {
        ByteBuffer input = MemoryUtil.memAlloc(ogg.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(ogg).flip();
            IntBuffer channels = stack.mallocInt(1);
            IntBuffer rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(input, channels, rate);
            assertNotNull(pcm, "stb_vorbis rejected the stream");
            try {
                assertEquals(1, channels.get(0));
                assertEquals(RATE, rate.get(0));
                float[] samples = new float[pcm.remaining()];
                for (int i = 0; i < samples.length; i++) {
                    samples[i] = pcm.get(i) / 32768.0f;
                }
                return samples;
            } finally {
                LibCStdlib.free(pcm); // stb_vorbis allocates the result with the C runtime
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    /** Push-data frame decoding, as Minecraft's OggAudioStream reads sound files. */
    static float[] decodePushData(byte[] ogg) {
        ByteBuffer input = MemoryUtil.memAlloc(ogg.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            input.put(ogg).flip();
            IntBuffer consumed = stack.mallocInt(1);
            IntBuffer error = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_pushdata(input, consumed, error, null);
            assertTrue(decoder != 0, "stb_vorbis push-data open failed with error " + error.get(0));
            try {
                List<Float> samples = new ArrayList<>();
                int offset = consumed.get(0);
                IntBuffer channels = stack.mallocInt(1);
                IntBuffer count = stack.mallocInt(1);
                PointerBuffer output = stack.mallocPointer(1);
                while (offset < ogg.length) {
                    input.position(offset);
                    int used = STBVorbis.stb_vorbis_decode_frame_pushdata(decoder, input.slice(), channels, output,
                            count);
                    assertEquals(STBVorbis.VORBIS__no_error, STBVorbis.stb_vorbis_get_error(decoder));
                    assertTrue(used > 0, "stb_vorbis stalled at byte " + offset);
                    offset += used;
                    if (count.get(0) > 0) {
                        assertEquals(1, channels.get(0));
                        FloatBuffer channel = MemoryUtil.memFloatBuffer(MemoryUtil.memGetAddress(output.get(0)),
                                count.get(0));
                        for (int i = 0; i < count.get(0); i++) {
                            samples.add(channel.get(i));
                        }
                    }
                }
                float[] result = new float[samples.size()];
                for (int i = 0; i < result.length; i++) {
                    result[i] = samples.get(i);
                }
                return result;
            } finally {
                STBVorbis.stb_vorbis_close(decoder);
            }
        } finally {
            MemoryUtil.memFree(input);
        }
    }

    static double snr(float[] expected, float[] actual) {
        double signal = 0;
        double noise = 0;
        for (int i = 0; i < expected.length; i++) {
            signal += expected[i] * expected[i];
            noise += (expected[i] - actual[i]) * (expected[i] - actual[i]);
        }
        return 10 * Math.log10(signal / noise);
    }

    static double maxError(float[] expected, float[] actual) {
        double error = 0;
        for (int i = 0; i < expected.length; i++) {
            error = Math.max(error, Math.abs(expected[i] - actual[i]));
        }
        return error;
    }

    private static long littleEndian(byte[] bytes, int offset, int count) {
        long value = 0;
        for (int i = count - 1; i >= 0; i--) {
            value = (value << 8) | (bytes[offset + i] & 0xFF);
        }
        return value;
    }

    /** An independent bitwise restatement of the RFC 3533 checksum. */
    private static int oggCrc(byte[] page) {
        int crc = 0;
        for (byte b : page) {
            crc ^= (b & 0xFF) << 24;
            for (int bit = 0; bit < 8; bit++) {
                crc = (crc & 0x80000000) != 0 ? (crc << 1) ^ 0x04C11DB7 : crc << 1;
            }
        }
        return crc;
    }
}
