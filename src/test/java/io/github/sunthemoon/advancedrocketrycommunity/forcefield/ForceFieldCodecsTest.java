package io.github.sunthemoon.advancedrocketrycommunity.forcefield;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class ForceFieldCodecsTest {
    private static final UUID ID = new UUID(4, 1);
    private static final UUID OWNER = new UUID(4, 2);
    private static FieldIdentity identity(long generation) {
        return new FieldIdentity(ID, OWNER, generation, Level.OVERWORLD, new BlockPos(15, 100, 15), Direction.EAST);
    }
    @Test void allSixDirectionsAndThirtyTwoClaimsRoundTrip() {
        for (Direction direction : Direction.values()) {
            for (int mask : new int[]{0, 1, Integer.MIN_VALUE, -1}) {
                var state = new ProjectorState(ID, Optional.of(OWNER), 73, direction, mask);
                assertEquals(state, ForceFieldCodecs.projector(ForceFieldCodecs.projector(state)));
                var field = new FieldIdentity(ID, OWNER, 73, Level.OVERWORLD, new BlockPos(10, 64, -5), direction);
                assertEquals(field, ForceFieldCodecs.field(ForceFieldCodecs.field(field)));
                for (int offset = 1; offset <= 32; offset++) { assertEquals(offset, field.offset(field.target(offset))); }
                assertEquals(0, field.offset(field.source()));
                assertEquals(0, field.offset(field.source().relative(direction, 33)));
            }
        }
        var unowned = new ProjectorState(ID, Optional.empty(), 1, Direction.DOWN, 0);
        assertEquals(unowned, ForceFieldCodecs.projector(ForceFieldCodecs.projector(unowned)));
        assertThrows(IllegalArgumentException.class, () -> new ProjectorState(ID, Optional.empty(), 1, Direction.UP, 1));
    }
    @Test void exactClaimsDoNotAliasOtherGenerationsOwnersSourcesOrDirections() {
        var field = identity(1);
        assertNotEquals(field, identity(2));
        assertNotEquals(field, new FieldIdentity(UUID.randomUUID(), OWNER, 1, field.level(), field.source(), field.direction()));
        assertNotEquals(field, new FieldIdentity(ID, UUID.randomUUID(), 1, field.level(), field.source(), field.direction()));
        assertNotEquals(field, new FieldIdentity(ID, OWNER, 1, Level.NETHER, field.source(), field.direction()));
        assertEquals(0, field.offset(field.target(1).above()));
        assertThrows(IllegalArgumentException.class, () -> FieldIdentity.bit(0));
        assertThrows(IllegalArgumentException.class, () -> FieldIdentity.bit(33));
        int mask = 0;
        for (int i = 1; i <= 32; i++) { mask |= FieldIdentity.bit(i); }
        assertEquals(-1, mask);
        assertEquals(31, Integer.bitCount(mask & ~FieldIdentity.bit(32)));
    }
    @Test void nativeCodecsRejectMissingFutureMistypedAndUnknownFieldsWithoutMutation() {
        var projector = ForceFieldCodecs.projector(new ProjectorState(ID, Optional.of(OWNER), 1, Direction.EAST, -1));
        var field = ForceFieldCodecs.field(identity(1));
        for (CompoundTag original : new CompoundTag[]{projector, field}) {
            for (String key : original.getAllKeys()) {
                var missing = original.copy(); missing.remove(key);
                var wrong = original.copy(); wrong.put(key, StringTag.valueOf("retain"));
                reject(original == projector, missing); reject(original == projector, wrong);
            }
            var future = original.copy(); future.putInt("schema", 2); reject(original == projector, future);
            var unknown = original.copy(); unknown.putInt("extra", 1); reject(original == projector, unknown);
        }
    }
    @Test void byteDepthAndNodeLimitsArePreflightedBeforeDecode() {
        var huge = new CompoundTag(); huge.putByteArray("huge", new byte[4096]);
        assertFalse(ForceFieldCodecs.projectorBounded(huge));
        assertFalse(ForceFieldCodecs.fieldBounded(huge));
        var deep = new CompoundTag(); var child = deep;
        for (int i = 0; i < 8; i++) { var next = new CompoundTag(); child.put("next", next); child = next; }
        assertFalse(ForceFieldCodecs.projectorBounded(deep));
        var nodes = new CompoundTag(); for (int i = 0; i < 128; i++) { nodes.putInt("n" + i, 0); }
        assertFalse(ForceFieldCodecs.projectorBounded(nodes));
        reject(true, huge); reject(false, deep);
    }
    private static void reject(boolean projector, CompoundTag raw) {
        var before = raw.copy();
        assertThrows(IllegalArgumentException.class, () -> { if (projector) { ForceFieldCodecs.projector(raw); } else { ForceFieldCodecs.field(raw); } });
        assertEquals(before, raw);
    }
}
