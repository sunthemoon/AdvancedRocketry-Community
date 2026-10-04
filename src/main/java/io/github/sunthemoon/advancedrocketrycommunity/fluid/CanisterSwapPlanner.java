package io.github.sunthemoon.advancedrocketrycommunity.fluid;

/** Pure whole-unit inventory-capacity decision, before any fluid execution. */
public final class CanisterSwapPlanner {
    public static final int STACK_LIMIT = 16;

    private CanisterSwapPlanner() { }

    public static Decision plan(int heldCount, int destinationRoom) {
        if (heldCount < 1 || heldCount > STACK_LIMIT || destinationRoom < 0) {
            return new Decision(false, heldCount, 0, false);
        }
        if (heldCount == 1) { return new Decision(true, 1, 0, true); }
        if (destinationRoom == 0) { return new Decision(false, heldCount, 0, false); }
        return new Decision(true, heldCount - 1, 1, false);
    }

    public record Decision(boolean accepted, int heldCountAfter, int stowCount, boolean replaceHeld) { }
}
