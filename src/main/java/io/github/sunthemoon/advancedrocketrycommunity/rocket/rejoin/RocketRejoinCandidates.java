package io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin;

import java.util.ArrayList;
import java.util.List;
import static io.github.sunthemoon.advancedrocketrycommunity.rocket.rejoin.RocketRejoinRoot.*;

/** Fixed clockwise ring order, pad first; no world search or random fallback. */
public final class RocketRejoinCandidates {
    private RocketRejoinCandidates() { }
    public record Column(Target target, int x, int z) { }
    public static List<Column> columns(Position min, Position max, int spawnX, int spawnZ) {
        var result = new ArrayList<Column>(64);
        ring(result, Target.PAD, min.x(), max.x(), min.z(), max.z());
        ring(result, Target.OVERWORLD_SPAWN, spawnX, spawnX, spawnZ, spawnZ);
        return List.copyOf(result);
    }
    private static void ring(List<Column> out, Target target, int minX, int maxX, int minZ, int maxZ) {
        int x = minX + (maxX - minX) / 2, z = minZ + (maxZ - minZ) / 2;
        for (int r = 1; r <= 4; r++) {
            out.add(new Column(target, x, minZ - r)); out.add(new Column(target, maxX + r, minZ - r));
            out.add(new Column(target, maxX + r, z)); out.add(new Column(target, maxX + r, maxZ + r));
            out.add(new Column(target, x, maxZ + r)); out.add(new Column(target, minX - r, maxZ + r));
            out.add(new Column(target, minX - r, z)); out.add(new Column(target, minX - r, minZ - r));
        }
    }
}
