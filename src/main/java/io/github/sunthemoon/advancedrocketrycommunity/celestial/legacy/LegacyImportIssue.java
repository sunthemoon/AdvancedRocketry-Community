package io.github.sunthemoon.advancedrocketrycommunity.celestial.legacy;

import java.util.Objects;

/** Stable, source-path-addressed diagnostic emitted by the import-only adapter. */
public record LegacyImportIssue(
        Severity severity,
        String code,
        String path,
        String message
) implements Comparable<LegacyImportIssue> {
    public LegacyImportIssue {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(message, "message");
    }

    @Override
    public int compareTo(LegacyImportIssue other) {
        int pathOrder = path.compareTo(other.path);
        if (pathOrder != 0) {
            return pathOrder;
        }
        int codeOrder = code.compareTo(other.code);
        if (codeOrder != 0) {
            return codeOrder;
        }
        return severity.compareTo(other.severity);
    }

    public enum Severity {
        ERROR,
        WARNING
    }
}
