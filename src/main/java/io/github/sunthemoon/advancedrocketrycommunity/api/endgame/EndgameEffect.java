package io.github.sunthemoon.advancedrocketrycommunity.api.endgame;

/** The kind of world effect an endgame device is about to cause (API 1.8). */
public enum EndgameEffect {
    /** Blocks removed by an orbital laser drill's physical mode. */
    BLOCK_BREAK,
    /** Player gravity changed by an area gravity field. */
    ENTITY_GRAVITY,
    /** A player moved by a space-elevator ride. */
    TELEPORT
}
