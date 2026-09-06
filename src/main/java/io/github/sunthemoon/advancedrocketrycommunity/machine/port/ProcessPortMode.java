package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

public enum ProcessPortMode {
    INPUT(true, false),
    OUTPUT(false, true),
    BIDIRECTIONAL(true, true);

    private final boolean insertionAllowed;
    private final boolean extractionAllowed;

    ProcessPortMode(boolean insertionAllowed, boolean extractionAllowed) {
        this.insertionAllowed = insertionAllowed;
        this.extractionAllowed = extractionAllowed;
    }

    public boolean insertionAllowed() {
        return insertionAllowed;
    }

    public boolean extractionAllowed() {
        return extractionAllowed;
    }
}
