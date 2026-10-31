package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import static org.junit.jupiter.api.Assertions.*;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.stats.RocketStats;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EscrowLoaderS6SnapshotTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }

    @Test void independentlyDecodedEqualSnapshotsCompareByValue() {
        var encoded = RocketSnapshotNbtCodec.encode(snapshot("baseline"));
        var entity = RocketSnapshotNbtCodec.decode(encoded.copy()).snapshot().orElseThrow();
        var journal = RocketSnapshotNbtCodec.decode(encoded.copy()).snapshot().orElseThrow();
        assertNotSame(entity, journal);
        assertTrue(EscrowLoaderS6R2Probe.sameSnapshot(entity, journal));
    }

    @Test void rejectsEveryPersistedSnapshotComponentDifference() {
        var expected = snapshot("baseline");
        for (String change : List.of("id", "dimension", "origin", "block-position", "block-id", "state",
                "payload-adapter", "payload-data", "anchor", "stats", "created", "propulsion")) {
            var different = RocketSnapshotNbtCodec.decode(RocketSnapshotNbtCodec.encode(snapshot(change)))
                    .snapshot().orElseThrow();
            assertFalse(EscrowLoaderS6R2Probe.sameSnapshot(different, expected), change);
        }
    }

    private static RocketStructureSnapshot snapshot(String change) {
        var payload = new CompoundTag();
        payload.putIntArray("items", new int[]{1, 2, change.equals("payload-data") ? 4 : 3});
        var first = new RocketPosition(0, 0, 0);
        var second = new RocketPosition(0, 1, 0);
        var blocks = new ArrayList<RocketBlock>();
        blocks.add(new RocketBlock(first, new RocketBlockState(id(change.equals("block-id")
                ? "minecraft:barrel" : "minecraft:chest"), Map.of("facing", change.equals("state") ? "south" : "north")),
                new RocketBlockEntityPayload(id(change.equals("payload-adapter") ? "test:other" : "test:inventory"), payload)));
        blocks.add(new RocketBlock(second, new RocketBlockState(id("minecraft:iron_block"), Map.of())));
        blocks.add(new RocketBlock(new RocketPosition(change.equals("block-position") ? 2 : 1, 0, 0),
                new RocketBlockState(id("minecraft:iron_block"), Map.of())));
        return RocketStructureSnapshot.create(new UUID(1, change.equals("id") ? 3 : 2),
                id(change.equals("dimension") ? "minecraft:the_end" : "minecraft:overworld"),
                new RocketPosition(change.equals("origin") ? 73 : 72, 100, 64), blocks,
                List.of(change.equals("anchor") ? first : second),
                new RocketStats(3, change.equals("stats") ? 101 : 100, 1_000, 2_000, 1, 1, 1, 1),
                change.equals("created") ? 11 : 10,
                change.equals("propulsion") ? null : FluidFuelLoaderEscrowFixtures.mono(2_000));
    }

    private static ResourceLocation id(String value) { return new ResourceLocation(value); }

    /** Read-only replay entry point for retained native snapshot pairs, not another server start. */
    public static void main(String[] args) throws Exception {
        bootstrap();
        if (args.length == 0 || args.length % 2 != 0) { throw new IllegalArgumentException("Expected snapshot pairs"); }
        for (int index = 0; index < args.length; index += 2) {
            var actual = RocketSnapshotNbtCodec.decode(TagParser.parseTag(Files.readString(Path.of(args[index]))))
                    .snapshot().orElseThrow();
            var expected = RocketSnapshotNbtCodec.decode(TagParser.parseTag(Files.readString(Path.of(args[index + 1]))))
                    .snapshot().orElseThrow();
            if (!EscrowLoaderS6R2Probe.sameSnapshot(actual, expected)) {
                throw new AssertionError("Retained snapshot mismatch: " + args[index]);
            }
            System.out.println("SNAPSHOT_REPLAY_PASS " + args[index]);
        }
    }
}
