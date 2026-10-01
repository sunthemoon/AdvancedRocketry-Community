package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * ADR-055 section 3 payment counters, driven through {@link LaserLinkCounters#next} by a port of the C10 reference
 * model ({@code check_examples.py counter_model}): every crash cut settles to exactly one payment per layer.
 */
final class LaserLinkCountersTest {
    private static final long COST = 10_000;
    private static final long BUFFER = 200_000;
    private static final String[] ALPHABET = {"OP", "CHARGE", "SAVE_C", "SAVE_M", "CRASH"};

    @Test
    void stepsFollowTheCounterGap() {
        assertEquals(LaserLinkCounters.Step.PAY_DEBT, LaserLinkCounters.next(1, 2, COST, COST, true));
        assertEquals(LaserLinkCounters.Step.WAIT_FOR_ENERGY, LaserLinkCounters.next(1, 2, COST - 1, COST, true));
        assertEquals(LaserLinkCounters.Step.USE_CREDIT, LaserLinkCounters.next(3, 2, 0, COST, true));
        assertEquals(LaserLinkCounters.Step.CREDIT_WAITS, LaserLinkCounters.next(3, 2, BUFFER, COST, false),
                "a disabled system breaks no block");
        assertEquals(LaserLinkCounters.Step.NORMAL, LaserLinkCounters.next(2, 2, COST, COST, true));
        assertEquals(LaserLinkCounters.Step.NO_ENERGY, LaserLinkCounters.next(2, 2, COST - 1, COST, true));
        assertEquals(LaserLinkCounters.Step.PAY_DEBT, LaserLinkCounters.next(0, 1, COST, COST, false),
                "a debt is settled while the system is disabled");
        assertThrows(IllegalArgumentException.class, () -> LaserLinkCounters.next(-1, 0, 0, COST, true));
    }

    @Test
    void everyCrashCutUpToSevenEventsSettlesToOnePaymentPerLayer() {
        int[] checked = {0};
        walk(new ArrayList<>(), 7, checked);
        assertEquals(97_656, checked[0], "the reference model's counter_sequences_checked");
    }

    @Test
    void namedCutsMatchTheReferenceVectors() {
        assertEquals(List.of(0L, 0L, 0L), model(List.of("OP", "CRASH"), COST, 100_000));
        assertEquals(List.of(1L, 1L, 10_000L), model(List.of("OP", "SAVE_C", "CRASH"), COST, 100_000));
        assertEquals(List.of(1L, 1L, 10_000L), model(List.of("OP", "SAVE_M", "CRASH"), COST, 100_000));
        assertEquals(List.of(3L, 3L, 30_000L), model(List.of("OP", "OP", "SAVE_C", "OP", "SAVE_M", "CRASH"), COST,
                100_000));
        assertEquals(List.of(2L, 2L, 20_000L), model(List.of("OP", "OP", "SAVE_C", "CRASH"), COST, 100_000));
    }

    @Test
    void aDebtLargerThanTheBufferIsPaidAsEnergyArrives() {
        long cost = 100_000;
        assertEquals(List.of(3L, 3L, 300_000L), model(List.of("OP", "CHARGE", "OP", "CHARGE", "OP", "SAVE_M", "CRASH"),
                cost, BUFFER));
    }

    private static void walk(List<String> prefix, int depth, int[] checked) {
        List<Long> result = model(prefix, COST, 3 * COST);
        assertEquals(result.get(0), result.get(1), prefix.toString());
        assertEquals(COST * result.get(1), (long) result.get(2), prefix.toString());
        checked[0]++;
        if (depth > 0) {
            for (String event : ALPHABET) {
                prefix.add(event);
                walk(prefix, depth - 1, checked);
                prefix.remove(prefix.size() - 1);
            }
        }
    }

    /** Returns paid, done and spent after the sequence and a settling tail of charges. */
    private static List<Long> model(List<String> events, long cost, long energy) {
        long[] live = {energy, 0, 0, 0}; // energy, paid, spent, done
        long[] durable = live.clone();
        for (String event : events) {
            switch (event) {
                case "OP" -> {
                    if (contact(live, cost)
                            && LaserLinkCounters.next(live[1], live[3], live[0], cost, true)
                            == LaserLinkCounters.Step.NORMAL) {
                        live[0] -= cost;
                        live[2] += cost;
                        live[1]++;
                        live[3]++;
                    }
                }
                case "CHARGE" -> live[0] = Math.min(BUFFER, live[0] + cost);
                case "SAVE_C" -> {
                    durable[0] = live[0];
                    durable[1] = live[1];
                    durable[2] = live[2];
                }
                case "SAVE_M" -> durable[3] = live[3];
                case "CRASH" -> live = durable.clone();
                default -> throw new IllegalArgumentException(event);
            }
        }
        for (int i = 0; i < 64; i++) {
            if (contact(live, cost)) {
                return List.of(live[1], live[3], live[2]);
            }
            live[0] = Math.min(BUFFER, live[0] + cost);
        }
        throw new AssertionError("A debt never settled");
    }

    /** One contact: pay the debt layer by layer while energy allows, then run paid layers; settled when equal. */
    private static boolean contact(long[] live, long cost) {
        while (true) {
            LaserLinkCounters.Step step = LaserLinkCounters.next(live[1], live[3], live[0], cost, true);
            if (step == LaserLinkCounters.Step.PAY_DEBT) {
                live[0] -= cost;
                live[2] += cost;
                live[1]++;
            } else if (step == LaserLinkCounters.Step.USE_CREDIT) {
                live[3]++;
            } else {
                return live[1] == live[3];
            }
        }
    }
}
