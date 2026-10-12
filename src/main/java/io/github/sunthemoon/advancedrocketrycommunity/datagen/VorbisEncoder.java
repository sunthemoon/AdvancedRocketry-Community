package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * A small NEW Ogg Vorbis I encoder for code-synthesized mono sounds (ADR-066 section 7.1; pre-authoring record
 * {@code docs/provenance/v1.8.0-c18d-sounds-preauthoring.md}). Written for this project from the public Xiph.Org
 * Vorbis I specification and RFC 3533; no codec source is copied. It favours simplicity over size: one 2,048-sample
 * block size, one mode, floor type 1 with only its two end posts (a flat per-frame gain), and residue type 1 with a
 * one-bit partition class and a uniform 8-bit lattice codebook. Output is deterministic: all arithmetic is strict
 * IEEE and every table comes from {@link StrictMath}.
 */
public final class VorbisEncoder {
    public static final int BLOCK = 2048;
    /** Samples produced by each audio packet after the first; sound lengths are whole multiples of it. */
    public static final int HOP = BLOCK / 2;
    public static final int MAX_SAMPLES = 1 << 20;

    private static final int PARTITION = 32;
    private static final int RANGE_BITS = 10;
    /** The quantizer step of the value codebook, 2^-7, and its minimum, -1. */
    private static final double STEP = 1.0 / 128.0;
    private static final double[] WINDOW = new double[BLOCK];
    private static final double[] COSINE = new double[4 * BLOCK];
    /** Floor 1 amplitudes for the two-post flat floor; a geometric fit of the specification's dB table. */
    private static final double[] FLOOR = new double[256];
    private static final int[] CRC = new int[256];

    static {
        for (int i = 0; i < BLOCK; i++) {
            double s = StrictMath.sin((i + 0.5) / BLOCK * StrictMath.PI);
            WINDOW[i] = StrictMath.sin(StrictMath.PI / 2.0 * s * s);
        }
        for (int i = 0; i < COSINE.length; i++) {
            COSINE[i] = StrictMath.cos(StrictMath.PI * i / (2.0 * BLOCK));
        }
        double lowest = StrictMath.log(1.0649863e-07);
        for (int y = 0; y < FLOOR.length; y++) {
            FLOOR[y] = StrictMath.exp(lowest * (255 - y) / 255.0);
        }
        for (int i = 0; i < CRC.length; i++) {
            int r = i << 24;
            for (int bit = 0; bit < 8; bit++) {
                r = (r & 0x80000000) != 0 ? (r << 1) ^ 0x04C11DB7 : r << 1;
            }
            CRC[i] = r;
        }
    }

    private VorbisEncoder() {
    }

    /**
     * Encodes {@code samples} (each in [-1, 1], length a positive multiple of {@link #HOP}) as one logical Ogg
     * Vorbis stream that decodes to exactly that many samples. A {@code loop} is analysed as periodic, so its first
     * and last blocks see the wrapped waveform instead of a step to silence and the seam carries no extra coding
     * noise; the decoded samples still depend only on the samples themselves. {@code comments} are {@code KEY=value}
     * Vorbis comment fields, written in list order.
     */
    public static byte[] encode(float[] samples, int sampleRate, boolean loop, int serial, List<String> comments) {
        if (samples.length == 0 || samples.length % HOP != 0 || samples.length > MAX_SAMPLES) {
            throw new IllegalArgumentException("Sample count must be a positive multiple of " + HOP);
        }
        if (sampleRate < 8_000 || sampleRate > 48_000) {
            throw new IllegalArgumentException("Unsupported sample rate " + sampleRate);
        }
        for (float sample : samples) {
            if (!(sample >= -1.0f && sample <= 1.0f)) {
                throw new IllegalArgumentException("Samples must be finite and within [-1, 1]");
            }
        }
        OggWriter ogg = new OggWriter(serial);
        ogg.page(List.of(identification(sampleRate)), 0, 0x02);
        ogg.page(List.of(comment(comments), setup()), 0, 0);
        int frames = samples.length / HOP + 1;
        List<byte[]> pending = new ArrayList<>();
        int segments = 0;
        int bytes = 0;
        for (int frame = 0; frame < frames; frame++) {
            byte[] packet = audio(samples, loop, frame);
            int laces = packet.length / 255 + 1;
            if (!pending.isEmpty() && (segments + laces > 255 || bytes + packet.length > 4096)) {
                ogg.page(pending, (long) (frame - 1) * HOP, 0);
                pending.clear();
                segments = 0;
                bytes = 0;
            }
            pending.add(packet);
            segments += laces;
            bytes += packet.length;
        }
        ogg.page(pending, (long) (frames - 1) * HOP, 0x04);
        return ogg.bytes();
    }

    /** The forward MDCT of one windowed block, scaled so the decoder's unscaled inverse reconstructs it. */
    static double[] spectrum(float[] samples, boolean loop, int frame) {
        double[] windowed = new double[BLOCK];
        int start = (frame - 1) * HOP;
        for (int i = 0; i < BLOCK; i++) {
            int index = loop ? Math.floorMod(start + i, samples.length) : start + i;
            windowed[i] = index >= 0 && index < samples.length ? samples[index] * WINDOW[i] : 0.0;
        }
        double[] spectrum = new double[HOP];
        int period = COSINE.length;
        for (int k = 0; k < HOP; k++) {
            double sum = 0.0;
            int step = 2 * k + 1;
            for (int i = 0; i < BLOCK; i++) {
                if (windowed[i] != 0.0) {
                    sum += windowed[i] * COSINE[(int) ((long) (2 * i + 1 + HOP) * step % period)];
                }
            }
            spectrum[k] = sum * 4.0 / BLOCK;
        }
        return spectrum;
    }

    private static byte[] audio(float[] samples, boolean loop, int frame) {
        double[] spectrum = spectrum(samples, loop, frame);
        double peak = 0.0;
        for (double value : spectrum) {
            peak = Math.max(peak, Math.abs(value));
        }
        BitWriter bits = new BitWriter();
        bits.write(0, 1); // audio packet; one mode, so no mode bits
        int floor = 0;
        while (floor < 255 && FLOOR[floor] * (127.0 / 128.0) < peak) {
            floor++;
        }
        int[] quantized = new int[HOP];
        boolean audible = false;
        for (int k = 0; k < HOP; k++) {
            long q = Math.round(spectrum[k] / FLOOR[floor] / STEP);
            quantized[k] = (int) Math.max(-128, Math.min(127, q));
            audible |= quantized[k] != 0;
        }
        if (!audible) {
            bits.write(0, 1); // floor unused: a silent frame with no residue
            return bits.bytes();
        }
        bits.write(1, 1);
        bits.write(floor, 8);
        bits.write(floor, 8);
        for (int partition = 0; partition < HOP / PARTITION; partition++) {
            boolean coded = false;
            for (int k = partition * PARTITION; k < (partition + 1) * PARTITION; k++) {
                coded |= quantized[k] != 0;
            }
            bits.write(coded ? 1 : 0, 1); // class codeword: entry 0 is "0", entry 1 is "1"
            if (coded) {
                for (int k = partition * PARTITION; k < (partition + 1) * PARTITION; k++) {
                    bits.writeCodeword(quantized[k] + 128, 8);
                }
            }
        }
        return bits.bytes();
    }

    private static byte[] identification(int sampleRate) {
        BitWriter bits = new BitWriter();
        header(bits, 1);
        bits.write(0, 32); // version
        bits.write(1, 8); // channels
        bits.write(sampleRate, 32);
        bits.write(0, 32); // maximum bitrate: unset
        bits.write(0, 32); // nominal bitrate: unset
        bits.write(0, 32); // minimum bitrate: unset
        int exponent = Integer.numberOfTrailingZeros(BLOCK);
        bits.write(exponent, 4);
        bits.write(exponent, 4);
        bits.write(1, 1); // framing
        return bits.bytes();
    }

    private static byte[] comment(List<String> comments) {
        BitWriter bits = new BitWriter();
        header(bits, 3);
        byte[] vendor = "Advanced Rocketry: Community Edition NEW sound generator".getBytes(StandardCharsets.UTF_8);
        bits.write(vendor.length, 32);
        bits.writeBytes(vendor);
        bits.write(comments.size(), 32);
        for (String comment : comments) {
            if (comment.indexOf('=') <= 0) {
                throw new IllegalArgumentException("Vorbis comments are KEY=value fields");
            }
            byte[] field = comment.getBytes(StandardCharsets.UTF_8);
            bits.write(field.length, 32);
            bits.writeBytes(field);
        }
        bits.write(1, 1); // framing
        return bits.bytes();
    }

    private static byte[] setup() {
        BitWriter bits = new BitWriter();
        header(bits, 5);
        bits.write(2 - 1, 8); // codebooks
        // Codebook 0, the partition classbook: two one-bit entries, no lookup.
        codebookHeader(bits, 1, 2, 1);
        bits.write(0, 4);
        // Codebook 1, the value book: 256 eight-bit entries, lattice values (entry - 128) / 128.
        codebookHeader(bits, 1, 256, 8);
        bits.write(1, 4); // lookup type 1
        bits.write(vorbisFloat(1, 788, true), 32); // minimum -1
        bits.write(vorbisFloat(1, 788 - 7, false), 32); // delta 2^-7
        bits.write(8 - 1, 4); // value bits
        bits.write(0, 1); // sequence_p
        for (int multiplicand = 0; multiplicand < 256; multiplicand++) {
            bits.write(multiplicand, 8);
        }
        bits.write(1 - 1, 6); // time domain transforms
        bits.write(0, 16);
        bits.write(1 - 1, 6); // floors
        bits.write(1, 16); // floor type 1
        bits.write(0, 5); // no partitions: only the posts at x = 0 and x = 2^rangebits
        bits.write(1 - 1, 2); // multiplier 1
        bits.write(RANGE_BITS, 4);
        bits.write(1 - 1, 6); // residues
        bits.write(1, 16); // residue type 1
        bits.write(0, 24); // begin
        bits.write(HOP, 24); // end
        bits.write(PARTITION - 1, 24);
        bits.write(2 - 1, 6); // classifications
        bits.write(0, 8); // classbook
        bits.write(0, 3); // class 0 cascade: no passes
        bits.write(0, 1);
        bits.write(1, 3); // class 1 cascade: pass 0 only
        bits.write(0, 1);
        bits.write(1, 8); // class 1, pass 0: the value book
        bits.write(1 - 1, 6); // mappings
        bits.write(0, 16); // mapping type 0
        bits.write(0, 1); // one submap
        bits.write(0, 1); // no coupling
        bits.write(0, 2); // reserved
        bits.write(0, 8); // unused time configuration
        bits.write(0, 8); // floor 0
        bits.write(0, 8); // residue 0
        bits.write(1 - 1, 6); // modes
        bits.write(0, 1); // block flag
        bits.write(0, 16); // window type
        bits.write(0, 16); // transform type
        bits.write(0, 8); // mapping 0
        bits.write(1, 1); // framing
        return bits.bytes();
    }

    private static void header(BitWriter bits, int type) {
        bits.write(type, 8);
        bits.writeBytes("vorbis".getBytes(StandardCharsets.US_ASCII));
    }

    /** A complete tree of {@code entries} codewords that all have {@code length} bits; entry i is codeword i. */
    private static void codebookHeader(BitWriter bits, int dimensions, int entries, int length) {
        if (entries != 1 << length) {
            throw new IllegalArgumentException("Uniform codebooks must be complete");
        }
        bits.write(0x564342, 24);
        bits.write(dimensions, 16);
        bits.write(entries, 24);
        bits.write(0, 1); // not ordered
        bits.write(0, 1); // not sparse
        for (int entry = 0; entry < entries; entry++) {
            bits.write(length - 1, 5);
        }
    }

    private static long vorbisFloat(int mantissa, int exponent, boolean negative) {
        return (negative ? 0x80000000L : 0L) | ((long) exponent << 21) | mantissa;
    }

    /** Vorbis packs bits least significant first; Huffman codewords are read most significant bit first. */
    private static final class BitWriter {
        private byte[] buffer = new byte[1024];
        private long position;

        void write(long value, int count) {
            for (int i = 0; i < count; i++) {
                bit((int) (value >>> i) & 1);
            }
        }

        void writeCodeword(int codeword, int length) {
            for (int i = length - 1; i >= 0; i--) {
                bit((codeword >>> i) & 1);
            }
        }

        void writeBytes(byte[] bytes) {
            for (byte b : bytes) {
                write(b & 0xFF, 8);
            }
        }

        private void bit(int value) {
            int index = (int) (position >>> 3);
            if (index == buffer.length) {
                buffer = Arrays.copyOf(buffer, buffer.length * 2);
            }
            if (value != 0) {
                buffer[index] |= (byte) (1 << (position & 7));
            }
            position++;
        }

        byte[] bytes() {
            return Arrays.copyOf(buffer, (int) ((position + 7) >>> 3));
        }
    }

    /** RFC 3533 pages: whole packets only, so every page ends a packet and carries a real granule position. */
    private static final class OggWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();
        private final int serial;
        private int sequence;

        OggWriter(int serial) {
            this.serial = serial;
        }

        void page(List<byte[]> packets, long granule, int flags) {
            ByteArrayOutputStream lacing = new ByteArrayOutputStream();
            ByteArrayOutputStream body = new ByteArrayOutputStream();
            for (byte[] packet : packets) {
                int remaining = packet.length;
                while (remaining >= 255) {
                    lacing.write(255);
                    remaining -= 255;
                }
                lacing.write(remaining);
                body.writeBytes(packet);
            }
            if (lacing.size() > 255) {
                throw new IllegalStateException("Ogg page needs more than 255 segments");
            }
            ByteArrayOutputStream page = new ByteArrayOutputStream();
            page.writeBytes("OggS".getBytes(StandardCharsets.US_ASCII));
            page.write(0);
            page.write(flags);
            little(page, granule, 8);
            little(page, serial, 4);
            little(page, sequence++, 4);
            little(page, 0, 4); // CRC placeholder
            page.write(lacing.size());
            page.writeBytes(lacing.toByteArray());
            page.writeBytes(body.toByteArray());
            byte[] bytes = page.toByteArray();
            int crc = 0;
            for (byte b : bytes) {
                crc = (crc << 8) ^ CRC[((crc >>> 24) ^ b) & 0xFF];
            }
            for (int i = 0; i < 4; i++) {
                bytes[22 + i] = (byte) (crc >>> (8 * i));
            }
            out.writeBytes(bytes);
        }

        byte[] bytes() {
            return out.toByteArray();
        }

        private static void little(ByteArrayOutputStream out, long value, int count) {
            for (int i = 0; i < count; i++) {
                out.write((int) (value >>> (8 * i)) & 0xFF);
            }
        }
    }
}
