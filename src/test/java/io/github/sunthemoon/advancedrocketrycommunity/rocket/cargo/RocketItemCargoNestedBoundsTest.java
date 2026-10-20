package io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo;

import static io.github.sunthemoon.advancedrocketrycommunity.rocket.cargo.ItemCargoTestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.RocketLimits;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.*;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.persistence.RocketSnapshotNbtCodec;
import io.github.sunthemoon.advancedrocketrycommunity.rocket.validation.RocketValidationCode;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** A stack within its own limits can still fail the container, aggregate or whole-snapshot bounds; nothing is truncated. */
class RocketItemCargoNestedBoundsTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private static final ResourceLocation HOST = ResourceLocation.tryParse("fixture:host");

    @Test void aLocallyValidStackCanExceedTheContainerPayloadBound() {
        var moved = blobStack(3_900);
        assertTrue(BoundedNbt.fits(moved, 4_096, 16, 512));
        var base = snapshot(CHEST, entry(1, blobStack(0)));
        int baseBytes = payload(base, 0).uncompressedBytes();
        int grow = payload(RocketItemCargoMutation.apply(base, delta(base, 0, moved, 0, 0), true), 0).uncompressedBytes() - baseBytes;
        // The before container sits exactly grow - 1 bytes below the per-payload bound.
        var before = snapshot(CHEST, entry(1, blobStack(RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - baseBytes - grow + 1)));
        assertEquals(RocketLimits.MAX_BLOCK_ENTITY_NBT_BYTES - grow + 1, payload(before, 0).uncompressedBytes());
        var error = assertThrows(RocketSnapshotException.class,
                () -> RocketItemCargoMutation.apply(before, delta(before, 0, moved, 0, 0), true));
        assertEquals(RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE, error.code());
        assertDoesNotThrow(() -> RocketItemCargoMutation.apply(before, delta(before, 0, stack(Items.STONE, 1), 0, 0), true));
    }

    @Test void aLocallyValidStackCanExceedThePayloadAggregate() {
        var moved = blobStack(3_900);
        var base = snapshot(CHEST);
        int grow = aggregate(RocketItemCargoMutation.apply(base, delta(base, 0, moved, 0, 0), true)) - aggregate(base);
        var before = filled(RocketLimits.MAX_TOTAL_NBT_BYTES - grow + 1);
        assertTrue(aggregate(before) <= RocketLimits.MAX_TOTAL_NBT_BYTES);
        var error = assertThrows(RocketSnapshotException.class,
                () -> RocketItemCargoMutation.apply(before, delta(before, 0, moved, 0, 0), true));
        assertEquals(RocketValidationCode.BLOCK_ENTITY_DATA_TOO_LARGE, error.code());
    }

    @Test void theWholeEncodedSnapshotIsNotThePayloadAggregate() {
        var moved = stack(Items.STONE, 1);
        var base = snapshot(CHEST);
        int grow = aggregate(RocketItemCargoMutation.apply(base, delta(base, 0, moved, 0, 0), true)) - aggregate(base);
        var before = filled(RocketLimits.MAX_TOTAL_NBT_BYTES - grow - 8);
        var after = RocketCargoMutation.replace(before, delta(before, 0, moved, 0, 0).cursor(),
                block -> RocketItemCargoMutation.payload(block, delta(before, 0, moved, 0, 0), true));
        assertTrue(aggregate(after) <= RocketLimits.MAX_TOTAL_NBT_BYTES, "The payload aggregate still fits");
        var error = assertThrows(RocketSnapshotException.class,
                () -> RocketItemCargoMutation.apply(before, delta(before, 0, moved, 0, 0), true));
        assertEquals(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, error.code());
    }

    @Test void individuallyBoundedPayloadsCanExceedWholeSnapshotDepthAndNodes() {
        for (boolean depth : new boolean[] {true, false}) {
            var data = new CompoundTag();
            if (depth) {
                var cursor = data;
                for (int i = 0; i < 31; i++) { var child = new CompoundTag(); cursor.put("c", child); cursor = child; }
            } else {
                var nodes = new net.minecraft.nbt.ListTag();
                for (int i = 0; i < 40_000; i++) { nodes.add(net.minecraft.nbt.ByteTag.valueOf((byte) 1)); }
                data.put("nodes", nodes);
            }
            assertTrue(BoundedNbt.fits(data, 262_144, 32, 65_536));
            var blocks = new ArrayList<RocketBlock>(List.of(block(0, state(BARREL), container(null, null))));
            for (int i = 1; i <= 2; i++) {
                blocks.add(new RocketBlock(new RocketPosition(i, 0, 0), new RocketBlockState(HOST, Map.of()), new RocketBlockEntityPayload(HOST, data)));
            }
            var before = snapshot(blocks.toArray(RocketBlock[]::new));
            assertFalse(BoundedNbt.fits(RocketSnapshotNbtCodec.encode(before), 1_048_576, 32, 65_536));
            var error = assertThrows(RocketSnapshotException.class,
                    () -> RocketItemCargoMutation.apply(before, delta(before, 0, stack(Items.STONE, 1), 0, 0), true));
            assertEquals(RocketValidationCode.SNAPSHOT_DATA_TOO_LARGE, error.code());
        }
    }

    /** An empty chest at x = 0 plus four host payloads, making the payload aggregate exactly {@code total} bytes. */
    private static RocketStructureSnapshot filled(int total) {
        var base = snapshot(CHEST);
        int remaining = total - aggregate(base);
        var blocks = new ArrayList<RocketBlock>(List.of(block(0, state(CHEST), container(null, null))));
        var empty = new CompoundTag(); empty.putByteArray("blob", new byte[0]);
        int overhead = new RocketBlockEntityPayload(HOST, empty).uncompressedBytes();
        for (int i = 1; i <= 4; i++) {
            int bytes = i < 4 ? remaining / 4 : remaining - 3 * (remaining / 4);
            var data = new CompoundTag(); data.putByteArray("blob", new byte[bytes - overhead]);
            var payload = new RocketBlockEntityPayload(HOST, data);
            assertEquals(bytes, payload.uncompressedBytes());
            blocks.add(new RocketBlock(new RocketPosition(i, 0, 0), new RocketBlockState(HOST, Map.of()), payload));
        }
        var result = snapshot(blocks.toArray(RocketBlock[]::new));
        assertEquals(total, aggregate(result));
        return result;
    }
    private static int aggregate(RocketStructureSnapshot value) {
        return value.blocks().stream().flatMap(b -> b.blockEntityPayload().stream()).mapToInt(RocketBlockEntityPayload::uncompressedBytes).sum();
    }
    private static RocketBlockEntityPayload payload(RocketStructureSnapshot value, int x) {
        return value.blocks().stream().filter(b -> b.position().equals(new RocketPosition(x, 0, 0))).findFirst().orElseThrow()
                .blockEntityPayload().orElseThrow();
    }
    private static CompoundTag blobStack(int blob) {
        var saved = stack(Items.STONE, 1); var tag = new CompoundTag(); tag.putByteArray("blob", new byte[blob]); saved.put("tag", tag);
        return saved;
    }
}
