package io.github.sunthemoon.advancedrocketrycommunity.rocket.server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

/** Server-lifetime bounded transition diagnostics; never stores a payload, position list or exception. */
final class SealedDiagnostics {
    private record State(String phase, String reason, boolean complete, boolean pending) {
        State emitted() { return new State(phase, reason, complete, false); }
    }
    private final Thread owner = Thread.currentThread();
    private final Map<String, State> states = new LinkedHashMap<>();
    private final BiConsumer<Boolean, String> output;
    private long nextLine = Long.MIN_VALUE, overflow;

    SealedDiagnostics(BiConsumer<Boolean, String> output) { this.output = Objects.requireNonNull(output); }
    void hold(String identity, String phase, String reason) { change(identity, phase, reason, false); }
    void complete(String identity, String result) { change(identity, "DONE", result, true); }
    private void change(String identity, String phase, String reason, boolean complete) {
        owner();
        if (!identity.matches("[a-z0-9_:/.-]{1,192}") || !phase.matches("[A-Z_]{1,64}") || !reason.matches("[A-Z_]{1,64}")) {
            throw new IllegalArgumentException("Unbounded diagnostic identity or reason");
        }
        var old = states.get(identity);
        if (old == null && states.size() == 64) { if (overflow < Long.MAX_VALUE) { overflow++; } return; }
        if (old == null || !old.phase.equals(phase) || !old.reason.equals(reason) || old.complete != complete) {
            states.put(identity, new State(phase, reason, complete, true));
        }
    }
    int size() { owner(); return states.size(); }
    void flush(long now) {
        owner(); if (now < nextLine) { return; }
        var pending = states.entrySet().stream().filter(entry -> entry.getValue().pending).toList();
        if (pending.isEmpty() && overflow == 0) { return; }
        boolean warning = overflow != 0 || pending.stream().anyMatch(entry -> !entry.getValue().complete);
        var details = new java.util.ArrayList<String>();
        for (var entry : pending) {
            var value = entry.getValue();
            details.add("transaction=" + entry.getKey() + " phase=" + value.phase + " reason=" + value.reason);
        }
        output.accept(warning, "ARCE_DISASSEMBLY_DURABILITY_" + (warning ? "HELD" : "COMPLETE") + " "
                + (details.isEmpty() ? "transaction=overflow phase=HELD reason=CAPACITY" : details.get(0))
                + " additional=" + details.stream().skip(1).toList() + " overflow=" + overflow);
        for (var entry : pending) {
            if (entry.getValue().complete) { states.remove(entry.getKey()); }
            else { states.put(entry.getKey(), entry.getValue().emitted()); }
        }
        overflow = 0; nextLine = now + 20;
    }
    void clear() { owner(); states.clear(); overflow = 0; nextLine = Long.MIN_VALUE; }
    private void owner() { if (Thread.currentThread() != owner) { throw new IllegalStateException("Diagnostics require their owner thread"); } }
}
