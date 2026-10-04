package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.resource;

/** Persisted Item/Fluid roles. Power plugs deliberately have no resource bank. */
public enum ClassicBankKind {
    ITEM_INPUT("item_input", true, true),
    ITEM_OUTPUT("item_output", true, false),
    FLUID_INPUT("fluid_input", false, true),
    FLUID_OUTPUT("fluid_output", false, false);

    private final String id;
    private final boolean item;
    private final boolean input;

    ClassicBankKind(String id, boolean item, boolean input) {
        this.id = id;
        this.item = item;
        this.input = input;
    }

    public String id() { return id; }
    public boolean isItem() { return item; }
    public boolean isInput() { return input; }

    public static ClassicBankKind parse(String id) {
        for (ClassicBankKind kind : values()) {
            if (kind.id.equals(id)) { return kind; }
        }
        throw new IllegalArgumentException("Unknown classic bank kind");
    }
}
