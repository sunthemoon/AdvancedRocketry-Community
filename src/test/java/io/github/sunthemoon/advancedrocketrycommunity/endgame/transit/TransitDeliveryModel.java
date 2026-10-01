package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * A Java port of the C10 reference model {@code check_examples.py Delivery}: the destination half with a third store,
 * a player who withdraws. {@code gated} is the incoming gate of ADR-054 section 11; without it the model reproduces
 * finding F02. Protocol decisions come from {@link TransitRules}.
 */
final class TransitDeliveryModel {
    private TransitDeliveryModel() {
    }

    /** A record: 'A' or 'C', acknowledged, the acknowledgement epoch (-1 none); null once pruned. */
    record Rec(char st, boolean acked, int ae) {
    }

    /**
     * D live: incoming "0", "U" (unpersisted), "P" (persisted), "A" (aged); receipt null or "U"/"P"/"A"; absence
     * "F" or "seen".
     */
    record D(String incoming, int buffer, String receipt, boolean present, String absence) {
    }

    record DDur(int incoming, int buffer, boolean receipt, boolean present) {
    }

    record State(Rec rec, Rec recDur, int e, int eDur, D d, DDur dDur, int p, int pDur, boolean vanilla) {
    }

    record Step(String label, State state, int crashes, int faults) {
    }

    static State initial() {
        Rec record = new Rec('A', false, -1);
        return new State(record, record, 1, 1, new D("0", 0, null, true, "F"), new DDur(0, 0, false, true), 0, 0,
                false);
    }

    static State normalize(State state) {
        TreeSet<Integer> epochs = new TreeSet<>(List.of(state.e(), state.eDur()));
        for (Rec rec : new Rec[] {state.rec(), state.recDur()}) {
            if (rec != null && rec.ae() >= 0) {
                epochs.add(rec.ae());
            }
        }
        Map<Integer, Integer> rank = new HashMap<>();
        for (int epoch : epochs) {
            rank.put(epoch, rank.size());
        }
        return new State(fix(state.rec(), rank), fix(state.recDur(), rank), rank.get(state.e()),
                rank.get(state.eDur()), state.d(), state.dDur(), state.p(), state.pDur(), state.vanilla());
    }

    private static Rec fix(Rec rec, Map<Integer, Integer> rank) {
        return rec == null ? null : new Rec(rec.st(), rec.acked(), rec.ae() < 0 ? -1 : rank.get(rec.ae()));
    }

    /** One destination: a claimed record is paid at D; the record is durable (registered long ago). */
    private static TransitRules.RecordFacts facts(Rec rec, int e) {
        if (rec == null) {
            return null;
        }
        return new TransitRules.RecordFacts(rec.st() == 'A' ? TransitRecord.State.ARRIVED : TransitRecord.State.CLAIMED,
                true, rec.st() == 'C', true, rec.acked(), rec.acked() && e > rec.ae());
    }

    private static boolean hasIncoming(String incoming) {
        return !incoming.equals("0");
    }

    static List<Step> successors(State state, boolean gated, int crashesLeft, int faultsLeft) {
        List<Step> out = new ArrayList<>();
        Rec rec = state.rec();
        int e = state.e();
        D d = state.d();
        int c = crashesLeft;
        int f = faultsLeft;
        TransitRules.RecordFacts facts = facts(rec, e);
        boolean receiptHere = d.receipt() != null;
        D land = gated ? new D("U", d.buffer(), "U", d.present(), d.absence())
                : new D(d.incoming(), d.buffer() + 1, "U", d.present(), d.absence());
        boolean claimed = facts != null && facts.claimedHere();

        if (d.present() && facts != null && TransitRules.claims(facts, receiptHere, true)) {
            out.add(step("CLAIM", state, new Rec('C', false, -1), null, null, null, land, null, null, null, null, c, f));
        }
        if (d.present() && gated && TransitRules.moves(d.incoming().equals("A"), facts)) {
            out.add(step("MOVE", state, claimed ? new Rec('C', true, e) : rec, null, null, null,
                    new D("0", d.buffer() + 1, d.receipt(), d.present(), d.absence()), null, null, null, null, c, f));
        }
        if (d.present() && d.buffer() > 0 && state.p() < 3) {
            out.add(step("WITHDRAW", state, null, null, null, null, new D(d.incoming(), d.buffer() - 1, d.receipt(),
                    d.present(), d.absence()), null, state.p() + 1, null, null, c, f));
        }
        DDur saved = new DDur(hasIncoming(d.incoming()) ? 1 : 0, d.buffer(), d.receipt() != null, d.present());
        String seen = !d.absence().equals("F") ? d.absence() : (d.present() ? "F" : "seen");
        if (!saved.equals(state.dDur()) || "U".equals(d.receipt()) || d.incoming().equals("U")
                || !seen.equals(d.absence())) {
            D observed = new D(observe(d.incoming()), d.buffer(), observeReceipt(d.receipt()), d.present(), seen);
            out.add(step("SAVE_D", state, null, null, null, null, observed, saved, null, null, null, c, f));
            if (f > 0) {
                out.add(step("SAVE_D_LOST", state, null, null, null, null, observed, null, null, null, null, c, f - 1));
            }
        }
        if (d.incoming().equals("P") || "P".equals(d.receipt())) {
            out.add(step("AGE_D", state, null, null, null, null, new D(age(d.incoming()), d.buffer(),
                    d.receipt() == null ? null : age(d.receipt()), d.present(), d.absence()), null, null, null, null,
                    c, f));
        }
        if (state.p() != state.pDur()) {
            out.add(step("SAVE_P", state, null, null, null, null, null, null, null, state.p(), null, c, f));
        }
        if (!Objects.equals(rec, state.recDur())) {
            out.add(new Step("FLUSH_L", normalize(new State(rec, rec, e + 1, e + 1, d, state.dDur(), state.p(),
                    state.pDur(), state.vanilla())), c, f));
        }
        if (d.present() && facts != null && facts.state() == TransitRecord.State.ARRIVED
                && TransitRules.recovers(facts, receiptHere)) {
            out.add(step("D_RECOVER", state, new Rec('C', false, -1), null, null, null, null, null, null, null, null,
                    c, f));
        }
        if (d.present() && facts != null && TransitRules.acknowledges(facts, "A".equals(d.receipt()),
                hasIncoming(d.incoming()))) {
            out.add(step("D_ACK", state, new Rec('C', true, e), null, null, null, null, null, null, null, null, c, f));
        }
        if (d.present() && facts != null && TransitRules.rematerializes(facts, receiptHere)) {
            out.add(step("D_REMAT", state, null, null, null, null, land, null, null, null, null, c, f));
        }
        if (receiptHere && TransitRules.dropsReceipt(facts)) {
            out.add(step("D_DROP", state, null, null, null, null, new D(d.incoming(), d.buffer(), null, d.present(),
                    d.absence()), null, null, null, null, c, f));
        }
        if (facts != null && TransitRules.prunes(facts, false)) {
            out.add(new Step("PRUNE", normalize(new State(null, state.recDur(), e, state.eDur(), d, state.dDur(),
                    state.p(), state.pDur(), state.vanilla())), c, f));
        }
        boolean dueMove = (d.incoming().equals("P") || d.incoming().equals("A")) && (rec == null || rec.acked());
        if (d.present() && d.buffer() == 0 && !dueMove) {
            Rec settled = rec;
            if (rec != null) {
                switch (TransitRules.settleRemoval(facts, receiptHere, hasIncoming(d.incoming()))) {
                    case MOVED -> settled = new Rec('C', true, e);
                    case INCOMING -> settled = new Rec('A', false, -1);
                    default -> {
                    }
                }
            }
            out.add(new Step("REMOVE_D", normalize(new State(settled, settled, e + 1, e + 1,
                    new D("0", 0, null, false, "F"), state.dDur(), state.p(), state.pDur(), state.vanilla())), c, f));
        }
        if (!d.present() && d.absence().equals("seen") && rec != null && rec.st() == 'A') {
            out.add(step("REDIRECT", state, null, null, null, null, new D("0", 0, null, true, "F"),
                    new DDur(0, 0, false, true), null, null, null, c, f));
        }
        if (crashesLeft > 0) {
            DDur dd = state.dDur();
            boolean torn = state.pDur() >= 1 && dd.incoming() + dd.buffer() >= 1 || state.p() > state.pDur();
            out.add(new Step("CRASH", normalize(new State(state.recDur(), state.recDur(), state.eDur(), state.eDur(),
                    new D(dd.incoming() != 0 ? "P" : "0", dd.buffer(), dd.receipt() ? "P" : null, dd.present(),
                            dd.present() ? "F" : "seen"), dd, state.pDur(), state.pDur(), state.vanilla() || torn)),
                    c - 1, f));
        }
        return out;
    }

    private static String observe(String flag) {
        return flag.equals("0") || flag.equals("A") ? flag : "P";
    }

    private static String observeReceipt(String flag) {
        return flag == null || flag.equals("A") ? flag : "P";
    }

    private static String age(String flag) {
        return flag.equals("P") ? "A" : flag;
    }

    /** A successor; null keeps a part. {@code rec} may be set explicitly only through the arguments given. */
    private static Step step(String label, State state, Rec rec, Rec recDur, Integer e, Integer eDur, D d, DDur dDur,
                             Integer p, Integer pDur, Boolean vanilla, int c, int f) {
        return new Step(label, normalize(new State(rec != null ? rec : state.rec(),
                recDur != null ? recDur : state.recDur(), e != null ? e : state.e(), eDur != null ? eDur : state.eDur(),
                d != null ? d : state.d(), dDur != null ? dDur : state.dDur(), p != null ? p : state.p(),
                pDur != null ? pDur : state.pDur(), vanilla != null ? vanilla : state.vanilla())), c, f);
    }

    static final List<String> ORDER = List.of("D_RECOVER", "D_ACK", "D_DROP", "D_REMAT", "PRUNE", "CLAIM", "MOVE",
            "AGE_D", "FLUSH_L", "SAVE_D", "SAVE_P", "REDIRECT");

    record Outcome(int total, boolean vanilla) {
    }

    static Outcome outcome(State state, boolean gated) {
        for (int i = 0; i < 300; i++) {
            Map<String, State> options = new HashMap<>();
            for (Step step : successors(state, gated, 0, 0)) {
                options.put(step.label(), step.state());
            }
            String chosen = null;
            for (String label : ORDER) {
                if (options.containsKey(label)) {
                    chosen = label;
                    break;
                }
            }
            if (chosen == null) {
                if (state.rec() != null || state.d().receipt() != null || hasIncoming(state.d().incoming())) {
                    throw new AssertionError("Delivery did not settle: " + state);
                }
                return new Outcome(state.d().buffer() + state.p(), state.vanilla());
            }
            state = options.get(chosen);
        }
        throw new AssertionError("delivery drain did not terminate");
    }

    record Node(State state, int crashes, int faults) {
    }

    record Exploration(int states, Map<List<Object>, Integer> outcomes) {
    }

    static Exploration explore(boolean gated, int crashes, int faults) {
        Set<Node> seen = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(normalize(initial()), crashes, faults));
        Map<List<Object>, Integer> outcomes = new HashMap<>();
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (!seen.add(node)) {
                continue;
            }
            Outcome outcome = outcome(node.state(), gated);
            outcomes.merge(List.of(outcome.total(), outcome.vanilla(), node.faults() < faults), 1, Integer::sum);
            for (Step step : successors(node.state(), gated, node.crashes(), node.faults())) {
                stack.push(new Node(step.state(), step.crashes(), step.faults()));
            }
        }
        return new Exploration(seen.size(), outcomes);
    }

    /** Follows named events with one crash available and no fault, keeping the last state per label. */
    static State follow(List<String> events, boolean gated) {
        State state = normalize(initial());
        for (String label : events) {
            State next = null;
            for (Step step : successors(state, gated, 1, 0)) {
                if (step.label().equals(label)) {
                    next = step.state();
                }
            }
            if (next == null) {
                throw new AssertionError("Event " + label + " not enabled in " + state);
            }
            state = next;
        }
        return state;
    }

    static Set<String> labels(State state, boolean gated) {
        Set<String> labels = new HashSet<>();
        successors(state, gated, 1, 0).forEach(step -> labels.add(step.label()));
        return labels;
    }
}
