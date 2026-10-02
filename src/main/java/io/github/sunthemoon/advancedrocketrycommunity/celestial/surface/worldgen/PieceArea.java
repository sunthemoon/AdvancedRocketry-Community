package io.github.sunthemoon.advancedrocketrycommunity.celestial.surface.worldgen;

import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/** The part of a structure piece that lies in the chunk being generated: the only blocks the piece may write. */
final class PieceArea {
    private PieceArea() {
    }

    static Optional<BoundingBox> overlap(BoundingBox piece, BoundingBox chunk) {
        if (!piece.intersects(chunk)) {
            return Optional.empty();
        }
        return Optional.of(new BoundingBox(
                Math.max(piece.minX(), chunk.minX()), Math.max(piece.minY(), chunk.minY()),
                Math.max(piece.minZ(), chunk.minZ()), Math.min(piece.maxX(), chunk.maxX()),
                Math.min(piece.maxY(), chunk.maxY()), Math.min(piece.maxZ(), chunk.maxZ())));
    }
}
