package io.github.sunthemoon.advancedrocketrycommunity.celestial.data;

import java.util.ArrayList;
import java.util.List;

/** One diagnostic budget shared by both resource sets. */
final class ReloadDiagnostics {
    static final int MAX_DETAILS = 8;
    static final int MAX_CHARS = 2_048;
    private final List<String> details = new ArrayList<>();

    void add(String message) {
        if (!full()) {
            details.add(bound(message));
        }
    }

    boolean full() {
        return details.size() >= MAX_DETAILS;
    }

    boolean empty() {
        return details.isEmpty();
    }

    String message() {
        return bound(String.join("; ", details));
    }

    static String bound(String message) {
        String safe = message == null ? "definition reload failed" : message.replace('\n', ' ').replace('\r', ' ');
        return safe.length() <= MAX_CHARS ? safe : safe.substring(0, MAX_CHARS - 3) + "...";
    }
}
