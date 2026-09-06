package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

import java.util.Objects;

/** Bounded failure detail safe to persist or synchronize without localized text. */
public record ProcessFailure(ProcessFailureCode code, String subject) {
    public static final int MAX_SUBJECT_CHARS = 128;
    public static final ProcessFailure NONE = new ProcessFailure(ProcessFailureCode.NONE, "");

    public ProcessFailure {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(subject, "subject");
        if (subject.length() > MAX_SUBJECT_CHARS) {
            throw new IllegalArgumentException("failure subject exceeds the character limit");
        }
        if (code == ProcessFailureCode.NONE && !subject.isEmpty()) {
            throw new IllegalArgumentException("a successful result cannot have a failure subject");
        }
    }
}
