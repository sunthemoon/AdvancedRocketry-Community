package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import com.google.common.hash.Hashing;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

/**
 * NEW MIT community audio for the ten classic sound events (ADR-066 section 7.1), registered in
 * {@code docs/provenance/v1.8.0-c18d-sounds-preauthoring.md} before authoring. Every waveform is synthesized from
 * the numbers in this file with {@link StrictMath} and a fixed-seed generator: no recording, sample or other input
 * file. Loops are periodic over their length, so they repeat without a seam.
 */
public final class V180SoundArt implements DataProvider {
    public static final int RATE = 22_050;
    private static final double TAU = 2.0 * StrictMath.PI;
    private final PackOutput.PathProvider output;

    public V180SoundArt(PackOutput output) {
        this.output = output.createPathProvider(PackOutput.Target.RESOURCE_PACK, "sounds");
    }

    /** The encoded file, with its identity and licence in the Vorbis comments. */
    public static byte[] ogg(ClassicSound sound) {
        return VorbisEncoder.encode(pcm(sound), RATE, sound.loop(), 0x41524300 + sound.ordinal(), List.of(
                "TITLE=" + sound.id(),
                "ARTIST=Advanced Rocketry: Community Edition contributors",
                "COPYRIGHT=Copyright (c) 2026 Advanced Rocketry: Community Edition contributors",
                "LICENSE=MIT",
                "DESCRIPTION=NEW code-synthesized sound; see docs/provenance/v1.8.0-c18d-sounds.json"));
    }

    /** The synthesized waveform, normalized to its own peak level. */
    public static float[] pcm(ClassicSound sound) {
        return switch (sound) {
            case AIR_HISS_LOOP -> airHiss();
            case BASIC_LASER_GUN -> laserGun();
            case COMBUSTION_ROCKET -> combustionRocket();
            case ELECTRIC_SHOCK_SMALL -> electricShock();
            case GRAVITY_CONTROLLER -> gravityController();
            case LASER_DRILL -> laserDrill();
            case LATHE -> lathe();
            case MACHINE_LARGE -> machineLarge();
            case RAILGUN_BANG -> railgunBang();
            case ROLLING_MACHINE -> rollingMachine();
        };
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        for (ClassicSound sound : ClassicSound.values()) {
            byte[] bytes = ogg(sound);
            writes.add(CompletableFuture.runAsync(() -> {
                try {
                    cache.writeIfNeeded(output.file(sound.file(), "ogg"), bytes, Hashing.sha1().hashBytes(bytes));
                } catch (java.io.IOException failed) {
                    throw new IllegalStateException("Cannot write sound " + sound.id(), failed);
                }
            }));
        }
        return CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new));
    }

    @Override
    public String getName() {
        return "Advanced Rocketry Community v1.8 NEW sounds";
    }

    // Supplied oxygen escaping a vent: band-limited noise with a slow flutter.
    private static float[] airHiss() {
        int length = hops(44);
        Random random = new Random(0x41495248L);
        double[] noise = random.noise(length);
        double[] hiss = Biquad.bandPass(4_200, 0.6).loop(noise);
        double[] air = Biquad.highPass(1_500, 0.7).loop(noise);
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double flutter = 0.82 + 0.12 * StrictMath.sin(cycles(i, length, 3)) + 0.06 * StrictMath.sin(cycles(i, length, 7));
            out[i] = flutter * (hiss[i] + 0.35 * air[i]);
        }
        return normalize(out, 0.45);
    }

    // A falling zap with a short noisy crack.
    private static float[] laserGun() {
        int length = hops(11);
        Random random = new Random(0x4C41534CL);
        double[] crack = Biquad.highPass(2_500, 0.7).once(random.noise(length));
        double[] out = new double[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) RATE;
            double frequency = 300 + 1_500 * StrictMath.exp(-t / 0.07);
            phase += TAU * frequency / RATE;
            double tone = StrictMath.tanh(2.5 * StrictMath.sin(phase)) + 0.25 * StrictMath.sin(2.01 * phase);
            double envelope = attack(t, 0.004) * StrictMath.exp(-t / 0.11);
            out[i] = envelope * tone + crack[i] * StrictMath.exp(-t / 0.012) * 0.8;
        }
        return normalize(fadeOut(out, 0.05), 0.75);
    }

    // A chemical rocket engine: low rumble with a periodic roar.
    private static float[] combustionRocket() {
        int length = hops(65);
        Random random = new Random(0x524F434BL);
        double[] noise = random.noise(length);
        double[] rumble = Biquad.lowPass(180, 0.7).loop(Biquad.lowPass(320, 0.7).loop(noise));
        double[] roar = Biquad.bandPass(650, 0.9).loop(noise);
        double[] crackle = Biquad.highPass(3_000, 0.7).loop(random.sparse(length, 0.004));
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double pulse = 0.85 + 0.15 * StrictMath.sin(cycles(i, length, 9));
            out[i] = pulse * (3.0 * rumble[i] + 0.55 * roar[i]) + 0.35 * crackle[i];
        }
        return normalize(out, 0.8);
    }

    // A small electric arc: a few short crackles.
    private static float[] electricShock() {
        int length = hops(9);
        Random random = new Random(0x53484F4BL);
        double[] noise = Biquad.highPass(1_800, 0.7).once(random.noise(length));
        double[] out = new double[length];
        double[] starts = {0.0, 0.035, 0.06, 0.11, 0.17, 0.205};
        double[] levels = {1.0, 0.7, 0.85, 0.55, 0.6, 0.35};
        for (int i = 0; i < length; i++) {
            double t = i / (double) RATE;
            double envelope = 0;
            for (int burst = 0; burst < starts.length; burst++) {
                double age = t - starts[burst];
                if (age >= 0) {
                    envelope += levels[burst] * attack(age, 0.0008) * StrictMath.exp(-age / 0.009);
                }
            }
            double buzz = 0.25 * StrictMath.sin(TAU * 120 * t) * StrictMath.exp(-t / 0.15);
            out[i] = envelope * noise[i] + buzz * attack(t, 0.002);
        }
        return normalize(fadeOut(out, 0.03), 0.7);
    }

    // A gravity field: a low two-tone hum under a slow "ohh" vowel sweep.
    private static float[] gravityController() {
        int length = hops(65);
        double[] source = new double[length];
        double[] hum = new double[length];
        for (int i = 0; i < length; i++) {
            double saw = 0;
            for (int harmonic = 1; harmonic <= 24; harmonic++) {
                saw += StrictMath.sin(cycles(i, length, 330 * harmonic)) / harmonic;
            }
            source[i] = saw;
            hum[i] = StrictMath.sin(cycles(i, length, 165)) + 0.6 * StrictMath.sin(cycles(i, length, 248))
                    + 0.2 * StrictMath.sin(cycles(i, length, 495));
        }
        double[] vowel = new double[length];
        double[] state = new double[4];
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < length; i++) {
                double sweep = 0.5 - 0.5 * StrictMath.cos(cycles(i, length, 2));
                Biquad formant = Biquad.bandPass(380 + 320 * sweep, 4.0);
                vowel[i] = formant.step(source[i], state);
            }
        }
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double swell = 0.75 + 0.25 * StrictMath.sin(cycles(i, length, 2) - 0.6);
            out[i] = 0.55 * hum[i] + swell * vowel[i] * 0.9;
        }
        return normalize(out, 0.6);
    }

    // A laser drill: a high whine with a periodic pitch wobble and a little noise.
    private static float[] laserDrill() {
        int length = hops(44);
        Random random = new Random(0x44524C4CL);
        double[] fizz = Biquad.bandPass(6_000, 0.8).loop(random.noise(length));
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            // About 1.2 kHz with a +/-53 Hz wobble six times per loop; both terms repeat exactly over the loop.
            double phase = cycles(i, length, 2_450) + 18.0 * StrictMath.sin(cycles(i, length, 6));
            double tone = StrictMath.sin(phase) + 0.35 * StrictMath.sin(2 * phase) + 0.12 * StrictMath.sin(3 * phase);
            out[i] = tone * (0.9 + 0.1 * StrictMath.sin(cycles(i, length, 11))) + 0.25 * fizz[i];
        }
        return normalize(out, 0.45);
    }

    // A lathe: a mid whir with a periodic cutting buzz.
    private static float[] lathe() {
        int length = hops(44);
        Random random = new Random(0x4C415448L);
        double[] chips = Biquad.bandPass(2_200, 1.2).loop(random.noise(length));
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double whir = 0;
            for (int harmonic = 1; harmonic <= 8; harmonic++) {
                whir += StrictMath.sin(cycles(i, length, 367 * harmonic) + harmonic) / (harmonic * harmonic);
            }
            double cut = 0.5 + 0.5 * StrictMath.sin(cycles(i, length, 8));
            out[i] = whir * (0.8 + 0.2 * cut) + 0.8 * cut * cut * chips[i];
        }
        return normalize(out, 0.55);
    }

    // A large machine: a deep mains-like hum with harmonics and a slow pulse.
    private static float[] machineLarge() {
        int length = hops(44);
        Random random = new Random(0x4D41434CL);
        double[] rumble = Biquad.lowPass(140, 0.7).loop(random.noise(length));
        double[] out = new double[length];
        double[] harmonics = {1.0, 0.65, 0.4, 0.3, 0.12, 0.15};
        int[] orders = {1, 2, 3, 4, 5, 6};
        for (int i = 0; i < length; i++) {
            double hum = 0;
            for (int h = 0; h < orders.length; h++) {
                hum += harmonics[h] * StrictMath.sin(cycles(i, length, 102 * orders[h]) + h * 0.7);
            }
            double pulse = 0.8 + 0.2 * StrictMath.sin(cycles(i, length, 2));
            out[i] = pulse * hum + 2.0 * rumble[i];
        }
        return normalize(out, 0.7);
    }

    // A railgun launch: a sharp crack and a decaying low thump.
    private static float[] railgunBang() {
        int length = hops(22);
        Random random = new Random(0x5241494CL);
        double[] noise = random.noise(length);
        double[] crack = Biquad.highPass(1_200, 0.7).once(noise);
        double[] boom = Biquad.lowPass(500, 0.7).once(noise);
        double[] out = new double[length];
        double phase = 0;
        for (int i = 0; i < length; i++) {
            double t = i / (double) RATE;
            phase += TAU * (35 + 45 * StrictMath.exp(-t / 0.08)) / RATE;
            double thump = StrictMath.sin(phase) * StrictMath.exp(-t / 0.28) * attack(t, 0.003);
            out[i] = 1.2 * crack[i] * StrictMath.exp(-t / 0.007) * attack(t, 0.0005)
                    + 3.0 * boom[i] * StrictMath.exp(-t / 0.13) * attack(t, 0.002) + 0.9 * thump;
        }
        return normalize(fadeOut(out, 0.1), 0.9);
    }

    // A rolling machine: a low rolling rumble with periodic metal clanks.
    private static float[] rollingMachine() {
        int length = hops(65);
        Random random = new Random(0x524F4C4CL);
        double[] noise = random.noise(length);
        double[] rumble = Biquad.lowPass(220, 0.8).loop(noise);
        double[] grind = Biquad.bandPass(900, 1.5).loop(noise);
        double[] out = new double[length];
        for (int i = 0; i < length; i++) {
            double roll = 0.8 + 0.2 * StrictMath.sin(cycles(i, length, 12));
            out[i] = 3.0 * roll * rumble[i] + 0.25 * grind[i];
        }
        int clanks = 6;
        double[] partials = {420, 1_130, 2_210, 3_370};
        double[] weights = {1.0, 0.6, 0.35, 0.2};
        for (int clank = 0; clank < clanks; clank++) {
            int start = clank * length / clanks;
            double level = clank % 2 == 0 ? 0.9 : 0.6;
            for (int j = 0; j < RATE / 4; j++) {
                double t = j / (double) RATE;
                double ring = 0;
                for (int p = 0; p < partials.length; p++) {
                    ring += weights[p] * StrictMath.sin(TAU * partials[p] * t) * StrictMath.exp(-t / (0.06 / (p + 1)));
                }
                out[(start + j) % length] += level * ring * attack(t, 0.0007);
            }
        }
        return normalize(out, 0.6);
    }

    private static int hops(int count) {
        return count * VorbisEncoder.HOP;
    }

    /** The phase of a tone with a whole number of cycles over the loop, so it repeats exactly. */
    private static double cycles(int sample, int length, int count) {
        return TAU * ((long) sample * count % length) / length;
    }

    private static double attack(double t, double seconds) {
        return t >= seconds ? 1.0 : t / seconds;
    }

    private static double[] fadeOut(double[] samples, double seconds) {
        int fade = (int) (seconds * RATE);
        for (int i = 0; i < fade; i++) {
            samples[samples.length - 1 - i] *= i / (double) fade;
        }
        return samples;
    }

    private static float[] normalize(double[] samples, double peak) {
        double max = 0;
        for (double sample : samples) {
            max = Math.max(max, Math.abs(sample));
        }
        float[] result = new float[samples.length];
        for (int i = 0; i < samples.length; i++) {
            result[i] = (float) (samples[i] * peak / max);
        }
        return result;
    }

    /** A fixed-seed xorshift64* generator; its sequence is defined here, not by a library. */
    private static final class Random {
        private long state;

        Random(long seed) {
            state = seed;
        }

        double next() {
            state ^= state >>> 12;
            state ^= state << 25;
            state ^= state >>> 27;
            long value = state * 0x2545F4914F6CDD1DL;
            return (value >>> 11) / (double) (1L << 53) * 2.0 - 1.0;
        }

        double[] noise(int length) {
            double[] noise = new double[length];
            for (int i = 0; i < length; i++) {
                noise[i] = next();
            }
            return noise;
        }

        /** Rare random impulses: on average {@code density} per sample. */
        double[] sparse(int length, double density) {
            double[] impulses = new double[length];
            for (int i = 0; i < length; i++) {
                double roll = next() * 0.5 + 0.5;
                double value = next();
                if (roll < density) {
                    impulses[i] = value;
                }
            }
            return impulses;
        }
    }

    /** RBJ biquad filters. {@link #loop} runs twice so the output of a periodic input is itself periodic. */
    private record Biquad(double b0, double b1, double b2, double a1, double a2) {
        static Biquad lowPass(double frequency, double q) {
            double w = TAU * frequency / RATE;
            double alpha = StrictMath.sin(w) / (2 * q);
            double cos = StrictMath.cos(w);
            return normalized((1 - cos) / 2, 1 - cos, (1 - cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad highPass(double frequency, double q) {
            double w = TAU * frequency / RATE;
            double alpha = StrictMath.sin(w) / (2 * q);
            double cos = StrictMath.cos(w);
            return normalized((1 + cos) / 2, -(1 + cos), (1 + cos) / 2, 1 + alpha, -2 * cos, 1 - alpha);
        }

        static Biquad bandPass(double frequency, double q) {
            double w = TAU * frequency / RATE;
            double alpha = StrictMath.sin(w) / (2 * q);
            double cos = StrictMath.cos(w);
            return normalized(alpha, 0, -alpha, 1 + alpha, -2 * cos, 1 - alpha);
        }

        private static Biquad normalized(double b0, double b1, double b2, double a0, double a1, double a2) {
            return new Biquad(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0);
        }

        /** One direct-form-I step; {@code state} holds x[n-1], x[n-2], y[n-1], y[n-2]. */
        double step(double x, double[] state) {
            double y = b0 * x + b1 * state[0] + b2 * state[1] - a1 * state[2] - a2 * state[3];
            state[1] = state[0];
            state[0] = x;
            state[3] = state[2];
            state[2] = y;
            return y;
        }

        double[] once(double[] input) {
            double[] state = new double[4];
            double[] output = new double[input.length];
            for (int i = 0; i < input.length; i++) {
                output[i] = step(input[i], state);
            }
            return output;
        }

        double[] loop(double[] input) {
            double[] state = new double[4];
            double[] output = new double[input.length];
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < input.length; i++) {
                    output[i] = step(input[i], state);
                }
            }
            return output;
        }
    }
}
