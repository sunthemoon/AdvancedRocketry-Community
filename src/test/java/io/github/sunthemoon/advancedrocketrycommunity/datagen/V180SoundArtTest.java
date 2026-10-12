package io.github.sunthemoon.advancedrocketrycommunity.datagen;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The automated part of ADR-066 section 7.1's audible waveform check for the NEW sounds: every file decodes with
 * Minecraft's stb_vorbis to an audible, correctly sized waveform close to its synthesis; loops have no seam and
 * one-shots end in silence. A manual listen is still required and is not replaced by this test.
 */
class V180SoundArtTest {
    private static final Map<ClassicSound, float[]> PCM = new EnumMap<>(ClassicSound.class);
    private static final Map<ClassicSound, byte[]> OGG = new EnumMap<>(ClassicSound.class);

    @BeforeAll
    static void synthesize() {
        for (ClassicSound sound : ClassicSound.values()) {
            PCM.put(sound, V180SoundArt.pcm(sound));
            OGG.put(sound, V180SoundArt.ogg(sound));
        }
    }

    @Test
    void everySoundIsAWholeNumberOfBlocksBetweenAThirdOfASecondAndFourSeconds() {
        for (ClassicSound sound : ClassicSound.values()) {
            float[] pcm = PCM.get(sound);
            assertEquals(0, pcm.length % VorbisEncoder.HOP, sound.id());
            double seconds = pcm.length / (double) V180SoundArt.RATE;
            assertTrue(seconds >= 0.3 && seconds <= 4.0, sound.id() + " lasts " + seconds + " s");
        }
    }

    @Test
    void everyWaveformIsAudibleFiniteAndBelowFullScale() {
        for (ClassicSound sound : ClassicSound.values()) {
            float[] pcm = PCM.get(sound);
            double peak = 0;
            double energy = 0;
            for (float sample : pcm) {
                assertTrue(Float.isFinite(sample), sound.id());
                peak = Math.max(peak, Math.abs(sample));
                energy += sample * sample;
            }
            double rms = Math.sqrt(energy / pcm.length);
            assertTrue(peak >= 0.4 && peak <= 0.9 + 1e-6, sound.id() + " peak " + peak);
            // -40 dBFS RMS is a floor for "not silent", far below any of these sounds.
            assertTrue(rms > 0.01, sound.id() + " RMS " + rms);
        }
    }

    @Test
    void loopsRepeatWithoutASeamAndOneShotsEndInSilence() {
        for (ClassicSound sound : ClassicSound.values()) {
            float[] pcm = PCM.get(sound);
            if (sound.loop()) {
                double largestStep = 0;
                for (int i = 1; i < pcm.length; i++) {
                    largestStep = Math.max(largestStep, Math.abs(pcm[i] - pcm[i - 1]));
                }
                double seam = Math.abs(pcm[0] - pcm[pcm.length - 1]);
                assertTrue(seam <= largestStep, sound.id() + " seam " + seam + " > largest step " + largestStep);
            } else {
                assertEquals(0.0f, pcm[pcm.length - 1], 1e-6f, sound.id());
            }
        }
    }

    @Test
    void everyFileDecodesWithMinecraftsDecoderCloseToItsSynthesis() {
        for (ClassicSound sound : ClassicSound.values()) {
            float[] pcm = PCM.get(sound);
            float[] decoded = VorbisEncoderTest.decodePushData(OGG.get(sound));
            assertEquals(pcm.length, decoded.length, sound.id());
            double snr = VorbisEncoderTest.snr(pcm, decoded);
            assertTrue(snr > 25, sound.id() + " signal-to-noise " + snr + " dB");
            double peak = 0;
            for (float sample : decoded) {
                peak = Math.max(peak, Math.abs(sample));
            }
            assertTrue(peak > 0.35, sound.id() + " decoded peak " + peak);
        }
    }

    @Test
    void synthesisAndEncodingAreDeterministicAndFilesCarryTheirIdentity() {
        for (ClassicSound sound : ClassicSound.values()) {
            assertArrayEquals(PCM.get(sound), V180SoundArt.pcm(sound), sound.id());
            assertArrayEquals(OGG.get(sound), V180SoundArt.ogg(sound), sound.id());
            String text = new String(OGG.get(sound), StandardCharsets.UTF_8);
            assertTrue(text.contains("TITLE=" + sound.id()), sound.id());
            assertTrue(text.contains("LICENSE=MIT"), sound.id());
            assertTrue(OGG.get(sound).length < 256 * 1024, sound.id() + " is " + OGG.get(sound).length + " bytes");
        }
    }
}
