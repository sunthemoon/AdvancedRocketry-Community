package io.github.sunthemoon.advancedrocketrycommunity.endgame.transit;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * A Java port of the C10 reference model {@code check_examples.py Transit} (one source S, one destination D, live and
 * durable copies of each store, crashes and lost writes). Every protocol decision the model makes is taken from
 * {@link TransitRules}; only the model's own mechanics (saves, crashes, the adversary) are written here. The test pins
 * the explored state counts and outcomes to the accepted {@code examples.json}, so a production rule that differs from
 * the reference model changes them.
 */
final class TransitSourceModel {
    static final int MAX_SEQ = 4;
    static boolean tombstoneRemoval = true;
    static boolean capEviction = false;

    private TransitSourceModel() {
    }

    record Entry(int seq, boolean persisted, boolean aged) {
    }

    /** {@code dispatched_through}: a plain value ('I'), dropped ('N'), retired ('R') or evicted ('X'). */
    record Hw(char kind, int value) {
        static final Hw NONE = new Hw('N', 0);

        static Hw of(int value) {
            return new Hw('I', value);
        }

        int plain() {
            return kind == 'I' ? value : 0;
        }

        boolean tuple() {
            return kind == 'R' || kind == 'X';
        }
    }

    /** absence: "F" (none), "seen", "aged", "saved", "saved_aged". */
    record SLive(int input, Set<Entry> outbox, int nextSeq, boolean present, String absence) {
    }

    record SDur(int input, Set<Integer> outbox, int nextSeq, boolean present) {
    }

    /** st: 'T' in transit, 'A' arrived, 'C' claimed at D; ae -1 while unacknowledged. */
    record Rec(int seq, char st, int de, boolean acked, int ae) {
    }

    record Ledger(Set<Rec> records, Hw hw, int e) {
    }

    record Receipt(int seq, boolean persisted) {
    }

    record DLive(int buffer, Set<Receipt> receipts) {
    }

    record DDur(int buffer, Set<Integer> receipts) {
    }

    record State(SLive s, SDur sd, Ledger l, Ledger ld, DLive d, DDur dd, boolean rollback, int destroyedLive,
                 int destroyedDur, int returned) {
    }

    record Step(String label, State state, int crashes, int faults) {
    }

    record Outcome(int delivered, int destroyed, boolean rollback) {
    }

    static State initial(int payloads) {
        return new State(new SLive(payloads, Set.of(), 1, true, "F"), new SDur(payloads, Set.of(), 1, true),
                new Ledger(Set.of(), Hw.of(0), 1), new Ledger(Set.of(), Hw.of(0), 1), new DLive(0, Set.of()),
                new DDur(0, Set.of()), false, 0, 0, 0);
    }

    static State normalize(State state) {
        TreeSet<Integer> epochs = new TreeSet<>();
        epochs.add(state.l().e());
        epochs.add(state.ld().e());
        for (Set<Rec> records : List.of(state.l().records(), state.ld().records())) {
            for (Rec rec : records) {
                epochs.add(rec.de());
                if (rec.ae() >= 0) {
                    epochs.add(rec.ae());
                }
            }
        }
        Map<Integer, Integer> rank = new HashMap<>();
        for (int epoch : epochs) {
            rank.put(epoch, rank.size());
        }
        return new State(state.s(), state.sd(), remap(state.l(), rank), remap(state.ld(), rank), state.d(),
                state.dd(), state.rollback(), state.destroyedLive(), state.destroyedDur(), state.returned());
    }

    private static Ledger remap(Ledger ledger, Map<Integer, Integer> rank) {
        Set<Rec> records = new HashSet<>();
        for (Rec rec : ledger.records()) {
            records.add(new Rec(rec.seq(), rec.st(), rank.get(rec.de()), rec.acked(),
                    rec.ae() < 0 ? -1 : rank.get(rec.ae())));
        }
        return new Ledger(Set.copyOf(records), ledger.hw(), rank.get(ledger.e()));
    }

    private static Rec record(Set<Rec> records, int seq) {
        for (Rec rec : records) {
            if (rec.seq() == seq) {
                return rec;
            }
        }
        return null;
    }

    /** The record as D sees it: D is the only destination, so claimed means paid at D. */
    private static TransitRules.RecordFacts facts(Rec rec, int e) {
        TransitRecord.State state = switch (rec.st()) {
            case 'T' -> TransitRecord.State.IN_TRANSIT;
            case 'A' -> TransitRecord.State.ARRIVED;
            default -> TransitRecord.State.CLAIMED;
        };
        return new TransitRules.RecordFacts(state, true, rec.st() == 'C', e > rec.de(), rec.acked(),
                rec.acked() && e > rec.ae());
    }

    private static Set<Entry> with(Set<Entry> set, Entry added) {
        Set<Entry> next = new HashSet<>(set);
        next.add(added);
        return Set.copyOf(next);
    }

    private static Set<Entry> without(Set<Entry> set, Entry removed) {
        Set<Entry> next = new HashSet<>(set);
        next.remove(removed);
        return Set.copyOf(next);
    }

    private static <T> Set<T> replace(Set<T> set, T removed, T added) {
        Set<T> next = new HashSet<>(set);
        next.remove(removed);
        if (added != null) {
            next.add(added);
        }
        return Set.copyOf(next);
    }

    static List<Step> successors(State state, int crashesLeft, int faultsLeft) {
        List<Step> out = new ArrayList<>();
        SLive s = state.s();
        Ledger l = state.l();
        DLive d = state.d();
        Set<Rec> records = l.records();
        Hw hw = l.hw();
        int e = l.e();
        int hwValue = hw.plain();
        Set<Integer> outSeqs = new HashSet<>();
        s.outbox().forEach(entry -> outSeqs.add(entry.seq()));
        Set<Integer> receiptSeqs = new HashSet<>();
        d.receipts().forEach(receipt -> receiptSeqs.add(receipt.seq()));
        int c = crashesLeft;
        int f = faultsLeft;

        boolean rollbackPending = s.present() && TransitRules.rollback(s.nextSeq(), hwValue);
        if (rollbackPending) {
            out.add(new Step("FIX_ROLLBACK", make(state, new SLive(s.input(), s.outbox(), hwValue + 1, s.present(),
                    s.absence()), null, null, null, null, null, true, null, null), c, f));
        }
        if (s.present() && s.input() > 0 && s.nextSeq() <= MAX_SEQ
                && TransitRules.mayEscrow(rollbackPending, s.outbox().size() < 4, !state.ld().hw().tuple())) {
            out.add(new Step("ESCROW", make(state, new SLive(s.input() - 1, with(s.outbox(),
                    new Entry(s.nextSeq(), false, false)), s.nextSeq() + 1, s.present(), s.absence()), null, null,
                    null, null, null, null, null, null), c, f));
        }
        if (s.present() && s.input() == 0) {
            int unregistered = 0;
            boolean pending = false;
            for (int seq : outSeqs) {
                if (seq > hwValue) {
                    unregistered++;
                } else {
                    Rec rec = record(records, seq);
                    pending |= rec != null && !(e > rec.de());
                }
            }
            if (hw.kind() != 'I') {
                throw new AssertionError("A present source with a non-plain dispatched_through: " + state);
            }
            Hw tomb = new Hw('R', hw.value());
            SLive removed = new SLive(0, Set.of(), s.nextSeq(), false, "F");
            State next = pending
                    ? make(state, removed, null, new Ledger(records, tomb, e + 1), new Ledger(records, tomb, e + 1),
                    null, null, null, state.destroyedLive() + unregistered, null)
                    : make(state, removed, null, new Ledger(records, tomb, e), null, null, null, null,
                    state.destroyedLive() + unregistered, null);
            out.add(new Step("REMOVE_S", next, c, f));
        }
        Set<Entry> newOutbox = new HashSet<>();
        s.outbox().forEach(entry -> newOutbox.add(new Entry(entry.seq(), true, entry.aged())));
        SDur saved = new SDur(s.input(), Set.copyOf(outSeqs), s.nextSeq(), s.present());
        String observed = capEviction && !s.present() && s.absence().equals("F") ? "saved" : s.absence();
        if (!saved.equals(state.sd()) || !newOutbox.equals(s.outbox()) || !observed.equals(s.absence())
                || state.destroyedLive() != state.destroyedDur()) {
            SLive live = new SLive(s.input(), Set.copyOf(newOutbox), s.nextSeq(), s.present(), observed);
            out.add(new Step("SAVE_S", make(state, live, saved, null, null, null, null, null, null,
                    state.destroyedLive()), c, f));
            if (f > 0) {
                out.add(new Step("SAVE_S_LOST", make(state, live, null, null, null, null, null, null, null, null), c,
                        f - 1));
            }
        }
        boolean unaged = s.outbox().stream().anyMatch(entry -> entry.persisted() && !entry.aged());
        if (unaged || s.absence().equals("seen") || s.absence().equals("saved")) {
            String agedAbsence = switch (s.absence()) {
                case "seen" -> "aged";
                case "saved" -> "saved_aged";
                default -> s.absence();
            };
            Set<Entry> aged = new HashSet<>();
            s.outbox().forEach(entry -> aged.add(new Entry(entry.seq(), entry.persisted(),
                    entry.persisted() || entry.aged())));
            out.add(new Step("AGE", make(state, new SLive(s.input(), Set.copyOf(aged), s.nextSeq(), s.present(),
                    agedAbsence), null, null, null, null, null, null, null, null), c, f));
        }
        if (tombstoneRemoval && hw.kind() != 'N' && !s.present() && s.absence().equals("aged")) {
            out.add(new Step("DROP_HW", make(state, null, null, new Ledger(records, Hw.NONE, e), null, null, null,
                    null, null, null), c, f));
        }
        if (capEviction && hw.kind() == 'R' && !s.present()
                && (s.absence().equals("saved_aged") || s.absence().equals("aged")) && records.isEmpty()) {
            out.add(new Step("EVICT_HW", make(state, null, null, new Ledger(records, new Hw('X', hw.value()), e),
                    null, null, null, null, null, null), c, f));
        }
        int lowest = outSeqs.isEmpty() ? -1 : outSeqs.stream().min(Integer::compare).orElseThrow();
        List<Entry> sortedOutbox = new ArrayList<>(s.outbox());
        sortedOutbox.sort(Comparator.comparingInt(Entry::seq).thenComparing(Entry::persisted)
                .thenComparing(Entry::aged));
        for (Entry entry : sortedOutbox) {
            Rec rec = record(records, entry.seq());
            TransitRules.SourceStep step = TransitRules.sourceStep(entry.seq(), lowest,
                    entry.persisted() && entry.aged(), hwValue, rec != null, rec != null && e > rec.de());
            switch (step) {
                case STALE_DROP -> out.add(new Step("STALE_DROP", make(state, new SLive(s.input(),
                        without(s.outbox(), entry), s.nextSeq(), s.present(), s.absence()), null, null, null, null,
                        null, null, null, null), c, f));
                case REGISTER -> out.add(new Step("REGISTER", make(state, null, null, new Ledger(replace(records, null,
                        new Rec(entry.seq(), 'T', e, false, -1)), Hw.of(entry.seq()), e), null, null, null, null,
                        null, null), c, f));
                case RELEASE -> out.add(new Step("RELEASE", make(state, new SLive(s.input(),
                        without(s.outbox(), entry), s.nextSeq(), s.present(), s.absence()), null, null, null, null,
                        null, null, null, null), c, f));
                default -> {
                }
            }
        }
        List<Rec> sortedRecords = new ArrayList<>(records);
        sortedRecords.sort(Comparator.comparingInt(Rec::seq));
        for (Rec rec : sortedRecords) {
            TransitRules.RecordFacts facts = facts(rec, e);
            boolean receiptHere = receiptSeqs.contains(rec.seq());
            if (rec.st() == 'T') {
                out.add(new Step("ARRIVE", make(state, null, null, new Ledger(replace(records, rec,
                        new Rec(rec.seq(), 'A', rec.de(), rec.acked(), rec.ae())), hw, e), null, null, null, null,
                        null, null), c, f));
            }
            if (TransitRules.claims(facts, receiptHere, true)) {
                out.add(new Step("CLAIM", make(state, null, null, new Ledger(replace(records, rec,
                        new Rec(rec.seq(), 'C', rec.de(), false, -1)), hw, e), null,
                        new DLive(d.buffer() + 1, Set.copyOf(addReceipt(d.receipts(), new Receipt(rec.seq(), false)))),
                        null, null, null, null), c, f));
            }
            if (TransitRules.recovers(facts, receiptHere)) {
                out.add(new Step("D_RECOVER", make(state, null, null, new Ledger(replace(records, rec,
                        new Rec(rec.seq(), 'C', rec.de(), false, -1)), hw, e), null, null, null, null, null, null),
                        c, f));
            }
            if (TransitRules.acknowledges(facts, d.receipts().contains(new Receipt(rec.seq(), true)), false)) {
                out.add(new Step("D_ACK", make(state, null, null, new Ledger(replace(records, rec,
                        new Rec(rec.seq(), 'C', rec.de(), true, e)), hw, e), null, null, null, null, null, null), c,
                        f));
            }
            if (TransitRules.rematerializes(facts, receiptHere)) {
                out.add(new Step("D_REMAT", make(state, null, null, null, null,
                        new DLive(d.buffer() + 1, Set.copyOf(addReceipt(d.receipts(), new Receipt(rec.seq(), false)))),
                        null, null, null, null), c, f));
            }
            if (TransitRules.prunes(facts, false)) {
                out.add(new Step("PRUNE", make(state, null, null, new Ledger(replace(records, rec, null), hw, e), null,
                        null, null, null, null, null), c, f));
            }
        }
        List<Receipt> sortedReceipts = new ArrayList<>(d.receipts());
        sortedReceipts.sort(Comparator.comparingInt(Receipt::seq).thenComparing(Receipt::persisted));
        for (Receipt receipt : sortedReceipts) {
            Rec rec = record(records, receipt.seq());
            if (TransitRules.dropsReceipt(rec == null ? null : facts(rec, e))) {
                out.add(new Step("D_DROP", make(state, null, null, null, null,
                        new DLive(d.buffer(), replace(d.receipts(), receipt, null)), null, null, null, null), c, f));
            }
        }
        if (!records.equals(state.ld().records()) || !hw.equals(state.ld().hw())) {
            Ledger flushed = new Ledger(records, hw, e + 1);
            out.add(new Step("FLUSH_L", make(state, null, null, flushed, flushed, null, null, null, null, null), c, f));
        }
        Set<Integer> savedReceipts = new HashSet<>(receiptSeqs);
        DDur dSaved = new DDur(d.buffer(), Set.copyOf(savedReceipts));
        Set<Receipt> newReceipts = new HashSet<>();
        d.receipts().forEach(receipt -> newReceipts.add(new Receipt(receipt.seq(), true)));
        if (!dSaved.equals(state.dd()) || !newReceipts.equals(d.receipts())) {
            out.add(new Step("SAVE_D", make(state, null, null, null, null, new DLive(d.buffer(),
                    Set.copyOf(newReceipts)), dSaved, null, null, null), c, f));
        }
        if (crashesLeft > 0) {
            out.add(new Step("CRASH", crash(state), c - 1, f));
        }
        return out;
    }

    private static Set<Receipt> addReceipt(Set<Receipt> receipts, Receipt receipt) {
        Set<Receipt> next = new HashSet<>(receipts);
        next.add(receipt);
        return next;
    }

    /** Builds a successor; null keeps a part. The destroyed pair is (live, durable). */
    private static State make(State state, SLive s, SDur sd, Ledger l, Ledger ld, DLive d, DDur dd, Boolean rollback,
                              Integer destroyedLive, Integer destroyedDur) {
        return normalize(new State(s != null ? s : state.s(), sd != null ? sd : state.sd(), l != null ? l : state.l(),
                ld != null ? ld : state.ld(), d != null ? d : state.d(), dd != null ? dd : state.dd(),
                rollback != null ? rollback : state.rollback(),
                destroyedLive != null ? destroyedLive : state.destroyedLive(),
                destroyedDur != null ? destroyedDur : state.destroyedDur(), state.returned()));
    }

    static State crash(State state) {
        SDur sd = state.sd();
        int input = sd.input();
        Set<Integer> outSeqs = sd.outbox();
        int nextSeq = sd.nextSeq();
        boolean present = sd.present();
        Set<Entry> restored = new HashSet<>();
        outSeqs.forEach(seq -> restored.add(new Entry(seq, true, false)));
        SLive s = new SLive(input, Set.copyOf(restored), nextSeq, present, present ? "F" : "seen");
        Ledger l = state.ld();
        int destroyedLive = state.destroyedDur();
        int destroyedDur = state.destroyedDur();
        int returned = state.returned();
        boolean rollback = state.rollback();
        Hw hw = state.ld().hw();
        if (present && hw.tuple()) {
            if (hw.kind() == 'R' || !outSeqs.isEmpty()) {
                int unregistered = 0;
                for (int seq : outSeqs) {
                    unregistered += seq > hw.value() ? 1 : 0;
                }
                if (hw.kind() == 'R') {
                    returned += input + unregistered;
                    rollback = rollback || nextSeq <= hw.value();
                } else {
                    int lost = destroyedDur + unregistered;
                    destroyedLive = lost;
                    destroyedDur = lost;
                    returned += input;
                }
                sd = new SDur(0, Set.of(), nextSeq, false);
                s = new SLive(0, Set.of(), nextSeq, false, "aged");
            } else {
                l = new Ledger(state.ld().records(), Hw.of(0), state.ld().e());
            }
        }
        Set<Receipt> receipts = new HashSet<>();
        state.dd().receipts().forEach(seq -> receipts.add(new Receipt(seq, true)));
        return normalize(new State(s, sd, l, state.ld(), new DLive(state.dd().buffer(), Set.copyOf(receipts)),
                state.dd(), rollback, destroyedLive, destroyedDur, returned));
    }

    static final List<String> DRAIN_ORDER = List.of("FIX_ROLLBACK", "STALE_DROP", "D_RECOVER", "D_ACK", "D_DROP",
            "D_REMAT", "PRUNE", "RELEASE", "REGISTER", "CLAIM", "ARRIVE", "ESCROW", "AGE", "DROP_HW", "FLUSH_L",
            "SAVE_D", "SAVE_S");

    static State drain(State state) {
        for (int i = 0; i < 400; i++) {
            Map<String, State> options = new HashMap<>();
            for (Step step : successors(state, 0, 0)) {
                options.putIfAbsent(step.label(), step.state());
            }
            String chosen = null;
            for (String label : DRAIN_ORDER) {
                if (options.containsKey(label)) {
                    chosen = label;
                    break;
                }
            }
            if (chosen == null) {
                return state;
            }
            state = options.get(chosen);
        }
        throw new AssertionError("drain did not terminate");
    }

    static Outcome delivered(State state) {
        if (!state.s().outbox().isEmpty() || !state.l().records().isEmpty() || !state.d().receipts().isEmpty()
                || state.s().input() != 0) {
            throw new AssertionError("Not quiescent: " + state);
        }
        return new Outcome(state.d().buffer() + state.returned(), state.destroyedDur(), state.rollback());
    }

    record Exploration(int states, Map<List<Object>, Integer> outcomes) {
    }

    record Node(State state, int crashes, int faults) {
    }

    static Exploration explore(int crashes, int faults, int payloads) {
        State start = normalize(initial(payloads));
        Set<Node> seen = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(start, crashes, faults));
        Map<List<Object>, Integer> outcomes = new HashMap<>();
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (!seen.add(node)) {
                continue;
            }
            Outcome outcome = delivered(drain(node.state()));
            outcomes.merge(List.of(outcome.delivered(), outcome.destroyed(), outcome.rollback(), node.faults() < faults),
                    1, Integer::sum);
            for (Step step : successors(node.state(), node.crashes(), node.faults())) {
                stack.push(new Node(step.state(), step.crashes(), step.faults()));
            }
        }
        return new Exploration(seen.size(), outcomes);
    }

    /** Follows named events with one crash and one fault available, as the reference test does. */
    static State follow(List<String> events) {
        State state = normalize(initial(1));
        for (String label : events) {
            State next = null;
            for (Step step : successors(state, 1, 1)) {
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
}
