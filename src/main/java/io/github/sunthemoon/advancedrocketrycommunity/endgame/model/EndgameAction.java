package io.github.sunthemoon.advancedrocketrycommunity.endgame.model;

/** ADR-054 section 3 actions a player may take on an endgame device. */
public enum EndgameAction {
    /** Device status; the public part never includes a target, a coordinate, an owner name or a device ID. */
    VIEW,
    /** Settings, targets, links and binds. */
    CONFIGURE,
    /** Start, stop, launch and ride. */
    OPERATE,
    /** Taking items out of a device's local buffers. */
    WITHDRAW
}
