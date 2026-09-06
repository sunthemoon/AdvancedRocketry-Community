package io.github.sunthemoon.advancedrocketrycommunity.machine.process;

/** Current amount and maximum capacity for one resolved resource channel. */
public record ProcessResourceBalance(long amount, long capacity) {
    public ProcessResourceBalance {
        if (amount < 0 || capacity < 0 || amount > capacity) {
            throw new IllegalArgumentException("resource balance must satisfy 0 <= amount <= capacity");
        }
    }

    public ProcessResourceBalance withAmount(long nextAmount) {
        return new ProcessResourceBalance(nextAmount, capacity);
    }
}
