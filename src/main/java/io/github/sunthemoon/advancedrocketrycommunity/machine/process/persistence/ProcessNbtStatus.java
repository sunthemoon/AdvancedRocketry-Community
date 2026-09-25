package io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence;

/** Outcome of decoding one independent process-owned NBT root. */
public enum ProcessNbtStatus {
    EMPTY,
    SUPPORTED,
    UNSUPPORTED_SCHEMA,
    INVALID_DATA
}
