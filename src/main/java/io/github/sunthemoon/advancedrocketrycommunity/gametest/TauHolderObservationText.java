package io.github.sunthemoon.advancedrocketrycommunity.gametest;

import java.util.UUID;

/** Bounded scalar text for the failure-only, post-lookup Tau holder observation. */
final class TauHolderObservationText {
    static final int MAX_BYTES = 512;
    static final int MAX_HOLDER = 160;
    private static final int MAX_LEVEL = 128;
    private static final int MAX_DIAGNOSTIC = 24;

    private TauHolderObservationText() {
    }

    static String format(UUID logical, UUID transfer, String level, Integer chunkX, Integer chunkZ,
            String diagnostic, String holder) {
        String text = new StringBuilder(MAX_BYTES).append("ARCE_TAU_HOLDER sample=POST_LOOKUP")
                .append(" logical=").append(logical == null ? "NA" : logical.toString())
                .append(" transfer=").append(transfer == null ? "NA" : transfer.toString())
                .append(" level=").append(token(level, MAX_LEVEL))
                .append(" chunk_x=").append(chunkX == null ? "NA" : chunkX.toString())
                .append(" chunk_z=").append(chunkZ == null ? "NA" : chunkZ.toString())
                .append(" diagnostic=").append(token(diagnostic, MAX_DIAGNOSTIC))
                .append(" holder=").append(token(holder, MAX_HOLDER)).toString();
        return text.length() <= MAX_BYTES ? text : "ARCE_TAU_HOLDER diagnostic=FORMAT_FAILED holder=NA";
    }

    /** Visits at most the field limit, never the unbounded remainder of the input. */
    private static String token(String value, int maximum) {
        if (value == null) {
            return "NA";
        }
        if (value.isEmpty()) {
            return "EMPTY";
        }
        int length = Math.min(value.length(), maximum);
        StringBuilder text = new StringBuilder(length);
        for (int index = 0; index < length; index++) {
            char character = value.charAt(index);
            text.append(character <= 32 || character > 126 ? '_' : character);
        }
        if (value.length() > maximum) {
            text.setCharAt(length - 1, '~');
        }
        return text.toString();
    }
}
