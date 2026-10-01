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
 * A Java port of the C10 reference model {@code check_examples.py Redirect}: one payload claimed at D; D removed, lost
 * without {@code onRemove} (later {@code MISSING}) or carried by a block mover; the record redirected to E; D coming
 * back through a crash or the mover; an adversary evicting D's tombstone; re-registration and an operator resolve.
 * Rules {@code (retire, freeze, evict)} switch the protocol's protections; decisions come from {@link TransitRules}.
 */
final class TransitRedirectModel {
    private TransitRedirectModel() {
    }

    /** st 'A' or 'C', destination 'D' or 'E', acknowledged, acknowledgement epoch (-1 none); null once pruned. */
    record Rec(char st, char dest, boolean acked, int ae) {
    }

    /** D live: where "here", "gone" or "carried"; incoming "0", "U", "P"; receipt null, "U", "P". */
    record DState(String where, String inc, int buf, String rc) {
    }

    record DDur(boolean present, int inc, int buf, boolean rc) {
    }

    /** A block mover's copy of D: incoming, receipt and receive buffer. */
    record Carried(String inc, String rc, int buf) {
    }

    record EState(String inc, int buf, String rc) {
    }

    record EDur(int inc, int buf, boolean rc) {
    }

    record State(Rec rec, Rec recDur, int e, int eDur, boolean retired, boolean retiredDur, boolean registered,
                 boolean registeredDur, DState d, DDur dDur, Carried carried, EState ee, EDur eeDur,
                 Carried carriedDur) {
    }

    record Rules(boolean retire, boolean freeze, boolean evict) {
    }

    record Step(String label, State state, int crashes) {
    }

    static State initial() {
        Rec rec = new Rec('A', 'D', false, -1);
        return new State(rec, rec, 1, 1, false, false, true, true, new DState("here", "0", 0, null),
                new DDur(true, 0, 0, false), null, new EState("0", 0, null), new EDur(0, 0, false), null);
    }

    static State normalize(State s) {
        TreeSet<Integer> epochs = new TreeSet<>(List.of(s.e(), s.eDur()));
        for (Rec rec : new Rec[] {s.rec(), s.recDur()}) {
            if (rec != null && rec.ae() >= 0) {
                epochs.add(rec.ae());
            }
        }
        Map<Integer, Integer> rank = new HashMap<>();
        for (int epoch : epochs) {
            rank.put(epoch, rank.size());
        }
        return new State(fix(s.rec(), rank), fix(s.recDur(), rank), rank.get(s.e()), rank.get(s.eDur()),
                s.retired(), s.retiredDur(), s.registered(), s.registeredDur(), s.d(), s.dDur(), s.carried(), s.ee(),
                s.eeDur(), s.carriedDur());
    }

    private static Rec fix(Rec rec, Map<Integer, Integer> rank) {
        return rec == null ? null : new Rec(rec.st(), rec.dest(), rec.acked(), rec.ae() < 0 ? -1 : rank.get(rec.ae()));
    }

    /** The record from one endpoint: destination and paid endpoint coincide in this model. */
    private static TransitRules.RecordFacts facts(Rec rec, char name, int e) {
        if (rec == null) {
            return null;
        }
        boolean here = rec.dest() == name;
        return new TransitRules.RecordFacts(rec.st() == 'A' ? TransitRecord.State.ARRIVED
                : TransitRecord.State.CLAIMED, here, here && rec.st() == 'C', true, rec.acked(),
                rec.acked() && e > rec.ae());
    }

    /** A builder over a state, so each successor names only what it changes. */
    private static final class B {
        Rec rec;
        Rec recDur;
        int e;
        int eDur;
        boolean retired;
        boolean retiredDur;
        boolean registered;
        boolean registeredDur;
        DState d;
        DDur dDur;
        Carried carried;
        EState ee;
        EDur eeDur;
        Carried carriedDur;

        B(State s) {
            rec = s.rec();
            recDur = s.recDur();
            e = s.e();
            eDur = s.eDur();
            retired = s.retired();
            retiredDur = s.retiredDur();
            registered = s.registered();
            registeredDur = s.registeredDur();
            d = s.d();
            dDur = s.dDur();
            carried = s.carried();
            ee = s.ee();
            eeDur = s.eeDur();
            carriedDur = s.carriedDur();
        }

        State build() {
            return normalize(new State(rec, recDur, e, eDur, retired, retiredDur, registered, registeredDur, d, dDur,
                    carried, ee, eeDur, carriedDur));
        }
    }

    private static boolean has(String flag) {
        return flag != null && !flag.equals("0");
    }

    private static void endpoint(List<Step> out, State s, char name, String inc, int buf, String rc, boolean active,
                                 boolean durableRegistration, int c) {
        if (!active) {
            return;
        }
        Rec rec = s.rec();
        int e = s.e();
        TransitRules.RecordFacts facts = facts(rec, name, e);
        boolean claimed = facts != null && facts.claimedHere();
        if (facts != null && TransitRules.claims(facts, rc != null, durableRegistration)) {
            B b = new B(s);
            b.rec = new Rec('C', name, false, -1);
            set(b, name, "U", buf, "U");
            out.add(new Step("CLAIM_" + name, b.build(), c));
        }
        if (TransitRules.moves("P".equals(inc), facts)) {
            B b = new B(s);
            b.rec = claimed ? new Rec('C', name, true, e) : rec;
            set(b, name, "0", buf + 1, rc);
            out.add(new Step("MOVE_" + name, b.build(), c));
        }
        if (facts != null && facts.state() == TransitRecord.State.ARRIVED && TransitRules.recovers(facts, rc != null)) {
            B b = new B(s);
            b.rec = new Rec('C', name, false, -1);
            out.add(new Step("RECOVER_" + name, b.build(), c));
        }
        if (facts != null && TransitRules.acknowledges(facts, "P".equals(rc), has(inc))) {
            B b = new B(s);
            b.rec = new Rec('C', name, true, e);
            out.add(new Step("ACK_" + name, b.build(), c));
        }
        if (facts != null && TransitRules.rematerializes(facts, rc != null)) {
            B b = new B(s);
            set(b, name, "U", buf, "U");
            out.add(new Step("REMAT_" + name, b.build(), c));
        }
        if (rc != null && TransitRules.dropsReceipt(facts)) {
            B b = new B(s);
            set(b, name, inc, buf, null);
            out.add(new Step("DROP_" + name, b.build(), c));
        }
    }

    private static void set(B b, char name, String inc, int buf, String rc) {
        if (name == 'D') {
            b.d = new DState(b.d.where(), inc, buf, rc);
        } else {
            b.ee = new EState(inc, buf, rc);
        }
    }

    private static String observe(String flag) {
        return flag == null || flag.equals("0") ? flag : "P";
    }

    static List<Step> successors(State s, Rules rules, int crashesLeft) {
        List<Step> out = new ArrayList<>();
        Rec rec = s.rec();
        int e = s.e();
        DState d = s.d();
        String where = d.where();
        EState ee = s.ee();
        int c = crashesLeft;
        boolean holds = has(d.inc()) || d.rc() != null;
        boolean frozen = where.equals("here") && (rules.retire() && s.retired()
                || rules.freeze() && !s.registered() && holds);
        boolean dActive = where.equals("here") && s.registered() && !(rules.retire() && s.retired());
        endpoint(out, s, 'D', d.inc(), d.buf(), d.rc(), dActive, s.registeredDur(), c);
        endpoint(out, s, 'E', ee.inc(), ee.buf(), ee.rc(), true, true, c);
        if (where.equals("here")) {
            DDur saved = new DDur(true, has(d.inc()) ? 1 : 0, d.buf(), d.rc() != null);
            if (!saved.equals(s.dDur()) || "U".equals(d.inc()) || "U".equals(d.rc()) || s.carriedDur() != null) {
                B b = new B(s);
                b.d = new DState(where, observe(d.inc()), d.buf(), observe(d.rc()));
                b.dDur = saved;
                b.carriedDur = null;
                out.add(new Step("SAVE_D", b.build(), c));
            }
        } else if (!s.dDur().equals(new DDur(false, 0, d.buf(), false)) || !Objects.equals(s.carried(),
                s.carriedDur())) {
            B b = new B(s);
            b.dDur = new DDur(false, 0, d.buf(), false);
            b.carriedDur = s.carried();
            out.add(new Step("SAVE_D", b.build(), c));
        }
        EDur savedE = new EDur(has(ee.inc()) ? 1 : 0, ee.buf(), ee.rc() != null);
        if (!savedE.equals(s.eeDur()) || "U".equals(ee.inc()) || "U".equals(ee.rc())) {
            B b = new B(s);
            b.ee = new EState(observe(ee.inc()), ee.buf(), observe(ee.rc()));
            b.eeDur = savedE;
            out.add(new Step("SAVE_E", b.build(), c));
        }
        if (!Objects.equals(rec, s.recDur()) || s.retired() != s.retiredDur() || s.registered() != s.registeredDur()) {
            B b = new B(s);
            b.recDur = rec;
            b.retiredDur = s.retired();
            b.registeredDur = s.registered();
            b.e = e + 1;
            b.eDur = e + 1;
            out.add(new Step("FLUSH_L", b.build(), c));
        }
        if (rec != null) {
            boolean holdsIncoming = rec.dest() == 'D' ? s.dDur().inc() != 0 : s.eeDur().inc() != 0;
            if (TransitRules.prunes(facts(rec, rec.dest(), e), holdsIncoming)) {
                B b = new B(s);
                b.rec = null;
                out.add(new Step("PRUNE", b.build(), c));
            }
        }
        TransitRules.RecordFacts atD = facts(rec, 'D', e);
        boolean dueMove = "P".equals(d.inc()) && (rec == null || rec.acked());
        if (dActive) {
            Rec settled = rec;
            if (atD != null) {
                switch (TransitRules.settleRemoval(atD, d.rc() != null, has(d.inc()))) {
                    case MOVED -> settled = new Rec('C', 'D', true, e);
                    case INCOMING -> settled = new Rec('A', 'D', false, -1);
                    default -> {
                    }
                }
            }
            int own = d.buf() + (dueMove ? 1 : 0);
            B removed = barrier(s, settled, rules, e);
            removed.d = new DState("gone", "0", own, null);
            out.add(new Step("REMOVE_D", removed.build(), c));
            B picked = barrier(s, settled, rules, e);
            picked.d = new DState("carried", "0", 0, null);
            picked.carried = new Carried(dueMove ? "0" : d.inc(), d.rc(), own);
            out.add(new Step("PICKUP_D", picked.build(), c));
            if (!dueMove && d.buf() == 0) {
                B vanished = new B(s);
                vanished.d = new DState("gone", "0", 0, null);
                out.add(new Step("VANISH_D", vanished.build(), c));
                B carriedAway = new B(s);
                carriedAway.d = new DState("carried", "0", 0, null);
                carriedAway.carried = new Carried(d.inc(), d.rc(), 0);
                out.add(new Step("VANISH_CARRIED_D", carriedAway.build(), c));
            }
        }
        if (!where.equals("here") && !s.dDur().present() && s.registered() && !s.retired()) {
            Rec settled = atD != null && TransitRules.returnsOnRetirement(atD) ? new Rec('A', 'D', false, -1) : rec;
            out.add(new Step("MISSING_D", barrier(s, settled, rules, e).build(), c));
        }
        if (rules.evict() && s.retired() && !where.equals("here")) {
            B b = new B(s);
            b.retired = false;
            out.add(new Step("EVICT_D", b.build(), c));
        }
        if (where.equals("carried")) {
            Carried carried = s.carried();
            B b = new B(s);
            b.d = new DState("here", has(carried.inc()) ? "U" : "0", carried.buf(), carried.rc() != null ? "U" : null);
            b.carried = null;
            out.add(new Step("PLACE_D", b.build(), c));
        }
        boolean cleanOnDisk = s.dDur().present() && s.dDur().inc() == 0 && !s.dDur().rc();
        if (where.equals("here") && !s.registered() && TransitRules.registers(rules.retire() && s.retired(),
                rules.freeze() && (holds || !cleanOnDisk))) {
            B b = new B(s);
            b.registered = true;
            out.add(new Step("REREGISTER_D", b.build(), c));
        }
        if (frozen && holds) {
            B b = new B(s);
            if (has(d.inc()) && TransitRules.resolveIncoming(atD) == TransitRules.Resolve.TO_RECEIVE_BUFFER) {
                b.d = new DState(where, "0", d.buf() + 1, null);
                b.rec = rec.acked() ? rec : new Rec('C', 'D', true, e);
            } else {
                b.d = new DState(where, "0", d.buf(), null);
            }
            out.add(new Step("RESOLVE_D", b.build(), c));
        }
        if (atD != null && rec.dest() == 'D' && TransitRules.redirectable(atD,
                rules.retire() ? !s.registeredDur() : !where.equals("here"))) {
            Rec moved = new Rec('A', 'E', false, -1);
            B b = new B(s);
            b.rec = moved;
            b.recDur = moved;
            b.retiredDur = s.retired();
            b.registeredDur = s.registered();
            b.e = e + 1;
            b.eDur = e + 1;
            out.add(new Step("REDIRECT", b.build(), c));
        }
        if (crashesLeft > 0) {
            DDur dd = s.dDur();
            DState restored = dd.present() ? new DState("here", dd.inc() != 0 ? "P" : "0", dd.buf(),
                    dd.rc() ? "P" : null) : s.carriedDur() != null ? new DState("carried", "0", 0, null)
                    : new DState("gone", "0", dd.buf(), null);
            EDur ed = s.eeDur();
            out.add(new Step("CRASH", normalize(new State(s.recDur(), s.recDur(), s.eDur(), s.eDur(), s.retiredDur(),
                    s.retiredDur(), s.registeredDur(), s.registeredDur(), restored, dd, s.carriedDur(),
                    new EState(ed.inc() != 0 ? "P" : "0", ed.buf(), ed.rc() ? "P" : null), ed, s.carriedDur())),
                    c - 1));
        }
        return out;
    }

    /** Live settlement, retirement and index removal written in one barrier flush. */
    private static B barrier(State s, Rec settled, Rules rules, int e) {
        B b = new B(s);
        b.rec = settled;
        b.recDur = settled;
        b.retired = rules.retire();
        b.retiredDur = rules.retire();
        b.registered = false;
        b.registeredDur = false;
        b.e = e + 1;
        b.eDur = e + 1;
        return b;
    }

    static final List<String> ORDER = List.of("RECOVER_D", "ACK_D", "DROP_D", "REMAT_D", "RECOVER_E", "ACK_E",
            "DROP_E", "REMAT_E", "PRUNE", "CLAIM_D", "MOVE_D", "CLAIM_E", "MOVE_E", "FLUSH_L", "SAVE_D", "SAVE_E",
            "MISSING_D", "PLACE_D", "RESOLVE_D", "REREGISTER_D", "REDIRECT");

    /** The settled count of payloads (D's and E's receive buffers), or -1 when the drain gets stuck ("stuck"). */
    static int outcome(State s, Rules rules) {
        for (int i = 0; i < 400; i++) {
            Map<String, State> options = new HashMap<>();
            for (Step step : successors(s, rules, 0)) {
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
                if (s.carried() != null || s.rec() != null || has(s.ee().inc()) || s.ee().rc() != null
                        || has(s.d().inc())) {
                    return -1;
                }
                return s.d().buf() + s.ee().buf();
            }
            s = options.get(chosen);
        }
        return -1; // A drain that does not terminate is stuck, as the reference model counts it.
    }

    record Node(State state, int crashes) {
    }

    record Exploration(int states, Map<Integer, Integer> outcomes) {
    }

    static Exploration explore(Rules rules, int crashes) {
        Set<Node> seen = new HashSet<>();
        Deque<Node> stack = new ArrayDeque<>();
        stack.push(new Node(normalize(initial()), crashes));
        Map<Integer, Integer> outcomes = new HashMap<>();
        while (!stack.isEmpty()) {
            Node node = stack.pop();
            if (!seen.add(node)) {
                continue;
            }
            if (seen.size() > 400_000) {
                throw new AssertionError("redirect exploration exceeded its state limit");
            }
            outcomes.merge(outcome(node.state(), rules), 1, Integer::sum);
            for (Step step : successors(node.state(), rules, node.crashes())) {
                stack.push(new Node(step.state(), step.crashes()));
            }
        }
        return new Exploration(seen.size(), outcomes);
    }

    static State follow(List<String> events, Rules rules, int crashes) {
        State s = normalize(initial());
        for (String label : events) {
            State next = null;
            for (Step step : successors(s, rules, crashes)) {
                if (step.label().equals(label)) {
                    next = step.state();
                }
            }
            if (next == null) {
                throw new AssertionError("Event " + label + " not enabled in " + s);
            }
            s = next;
        }
        return s;
    }
}
