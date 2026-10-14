package io.github.sunthemoon.advancedrocketrycommunity.client.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicLoopSelection.Candidate;
import io.github.sunthemoon.advancedrocketrycommunity.client.sound.ClassicLoopSelection.Key;
import io.github.sunthemoon.advancedrocketrycommunity.sound.ClassicSound;
import io.github.sunthemoon.advancedrocketrycommunity.sound.MachineSoundProfile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** ADR-066 section 7.1 loop limits: 32 loops per client, nearest first, only within 32 blocks. */
class ClassicLoopSelectionTest {
    private static Candidate at(long key, double distance, boolean playing) {
        return new Candidate(Key.block(key), ClassicSound.AIR_HISS_LOOP, distance * distance, playing);
    }

    @Test
    void atMostThirtyTwoLoopsAndTheNearestWin() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            candidates.add(at(i, 29.0 - i * 0.25, false));
        }
        List<Candidate> selected = ClassicLoopSelection.select(candidates);
        assertEquals(ClassicLoopSelection.MAX_LOOPS, selected.size());
        // All 100 are within 30 blocks; keys 99 down to 68 are the nearest and the rest are past the cap.
        for (int i = 0; i < selected.size(); i++) {
            assertEquals(Key.block(99 - i), selected.get(i).key());
        }
    }

    @Test
    void sourcesStartWithinThirtyBlocksAndStopBeyondThirtyTwo() {
        assertEquals(1, ClassicLoopSelection.select(List.of(at(1, 30.0, false))).size());
        assertEquals(0, ClassicLoopSelection.select(List.of(at(1, 30.001, false))).size());
        assertEquals(1, ClassicLoopSelection.select(List.of(at(1, 31.0, true))).size());
        assertEquals(1, ClassicLoopSelection.select(List.of(at(1, 32.0, true))).size());
        assertEquals(0, ClassicLoopSelection.select(List.of(at(1, 32.001, true))).size());
        assertEquals(1, ClassicLoopSelection.select(List.of(at(1, 0.0, false))).size());
    }

    @Test
    void selectionIsDeterministicForAnyInputOrderAndTies() {
        List<Candidate> candidates = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            ClassicSound sound = i % 2 == 0 ? ClassicSound.LASER_DRILL : ClassicSound.GRAVITY_CONTROLLER;
            candidates.add(new Candidate(i % 3 == 0 ? Key.entity(i) : Key.block(i), sound, 100.0, i % 5 == 0));
        }
        List<Candidate> expected = ClassicLoopSelection.select(candidates);
        Random random = new Random(7);
        for (int round = 0; round < 20; round++) {
            Collections.shuffle(candidates, random);
            assertEquals(expected, ClassicLoopSelection.select(candidates));
        }
        // Equal distances order by sound, then blocks before entities, then key value.
        assertEquals(ClassicSound.GRAVITY_CONTROLLER, expected.get(0).sound());
        assertTrue(expected.stream().filter(candidate -> candidate.sound() == ClassicSound.GRAVITY_CONTROLLER)
                .count() == 32);
        assertEquals(Key.block(1), expected.get(0).key());
    }

    @Test
    void eachSourcePlaysAtMostOneLoop() {
        Key key = Key.block(5);
        List<Candidate> selected = ClassicLoopSelection.select(List.of(
                new Candidate(key, ClassicSound.LASER_DRILL, 25.0, false),
                new Candidate(key, ClassicSound.LATHE, 4.0, false)));
        assertEquals(List.of(new Candidate(key, ClassicSound.LATHE, 4.0, false)), selected);
    }

    @Test
    void invalidCandidatesAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Candidate(Key.block(1), ClassicSound.RAILGUN_BANG, 1.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Candidate(Key.block(1), ClassicSound.LATHE, -1.0, false));
        assertThrows(IllegalArgumentException.class,
                () -> new Candidate(Key.block(1), ClassicSound.LATHE, Double.NaN, false));
        assertThrows(NullPointerException.class, () -> new Candidate(null, ClassicSound.LATHE, 1.0, false));
        assertThrows(NullPointerException.class, () -> new Candidate(Key.block(1), null, 1.0, false));
        List<Candidate> tooMany = Collections.nCopies(ClassicLoopSelection.MAX_CANDIDATES + 1, at(1, 1.0, false));
        assertThrows(IllegalArgumentException.class, () -> ClassicLoopSelection.select(tooMany));
    }

    @Test
    void everyLoopSoundIsSelectableAndOneShotsAreNot() {
        for (ClassicSound sound : ClassicSound.values()) {
            if (sound.loop()) {
                assertEquals(1, ClassicLoopSelection.select(
                        List.of(new Candidate(Key.entity(1), sound, 1.0, false))).size());
            } else {
                assertThrows(IllegalArgumentException.class, () -> new Candidate(Key.entity(1), sound, 1.0, false));
            }
        }
    }

    @Test
    void machineLoopsFollowPinnedMappingsAndInactiveMachinesOfferNothing() {
        for (String machine : List.of("lathe", "precision_laser_etcher", "rolling_machine", "chemical_reactor")) {
            ClassicSound expected = machine.equals("lathe") || machine.equals("precision_laser_etcher")
                    ? ClassicSound.LATHE : ClassicSound.ROLLING_MACHINE;
            assertEquals(expected, MachineSoundProfile.loop(machine, true));
            assertEquals(null, MachineSoundProfile.loop(machine, false));
            assertEquals(expected, ClassicLoopSelection.select(List.of(
                    new Candidate(Key.block(1), MachineSoundProfile.loop(machine, true), 1, false))).get(0).sound());
        }
        for (String silent : List.of("arc_furnace", "cutting_machine", "crystallizer", "electrolyzer",
                "centrifuge", "precision_assembler", "observatory", "astrobody_data_processor", "unknown")) {
            assertEquals(null, MachineSoundProfile.loop(silent, true));
        }
    }

    @Test
    void processingMachinesShareExistingCapNearestOrderingAndHysteresis() {
        List<Candidate> candidates = new ArrayList<>();
        List<String> machines = List.of("lathe", "rolling_machine", "precision_laser_etcher", "chemical_reactor");
        for (int i = 0; i < 64; i++) {
            candidates.add(new Candidate(Key.block(i), MachineSoundProfile.loop(machines.get(i % 4), true), i, false));
        }
        candidates.add(new Candidate(Key.block(100), ClassicSound.GRAVITY_CONTROLLER, 0, false));
        List<Candidate> selected = ClassicLoopSelection.select(candidates);
        assertEquals(32, selected.size());
        assertEquals(Key.block(100), selected.get(0).key());
        assertEquals(Key.block(30), selected.get(31).key());
        for (String machine : machines) {
            ClassicSound sound = MachineSoundProfile.loop(machine, true);
            assertEquals(0, ClassicLoopSelection.select(List.of(new Candidate(Key.block(1), sound, 31 * 31, false))).size());
            assertEquals(1, ClassicLoopSelection.select(List.of(new Candidate(Key.block(1), sound, 32 * 32, true))).size());
            assertEquals(0, ClassicLoopSelection.select(List.of(new Candidate(Key.block(1), sound, 32 * 32 + 1, true))).size());
        }
    }
}
