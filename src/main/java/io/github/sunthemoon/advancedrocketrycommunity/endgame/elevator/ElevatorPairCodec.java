package io.github.sunthemoon.advancedrocketrycommunity.endgame.elevator;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.model.EndgameNbt;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Strict encoding of one {@link ElevatorPair} in the {@code elevator_pairs} section (ADR-059 section 1). */
public final class ElevatorPairCodec {
    private static final Set<String> KEYS = Set.of("pair_id", "station_id", "terminal_id", "anchor_id", "body_id",
            "level_key", "x", "z", "anchor_y", "bound_at", "bound_by");

    private ElevatorPairCodec() {
    }

    public static CompoundTag encode(ElevatorPair pair) {
        CompoundTag tag = new CompoundTag();
        tag.put("pair_id", NbtUtils.createUUID(pair.pairId()));
        tag.put("station_id", NbtUtils.createUUID(pair.stationId()));
        tag.put("terminal_id", NbtUtils.createUUID(pair.terminalId()));
        tag.put("anchor_id", NbtUtils.createUUID(pair.anchorId()));
        tag.putString("body_id", pair.bodyId().toString());
        tag.putString("level_key", pair.levelKey().toString());
        tag.putInt("x", pair.x());
        tag.putInt("z", pair.z());
        tag.putInt("anchor_y", pair.anchorY());
        tag.putLong("bound_at", pair.boundAt());
        tag.put("bound_by", NbtUtils.createUUID(pair.boundBy()));
        return tag;
    }

    public static ElevatorPair decode(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, KEYS, "Elevator pair");
        return new ElevatorPair(EndgameNbt.requireUuid(tag, "pair_id"), EndgameNbt.requireUuid(tag, "station_id"),
                EndgameNbt.requireUuid(tag, "terminal_id"), EndgameNbt.requireUuid(tag, "anchor_id"),
                EndgameNbt.requireLocation(tag, "body_id", ElevatorPair.MAX_ID_LENGTH),
                EndgameNbt.requireLocation(tag, "level_key", ElevatorPair.MAX_ID_LENGTH),
                EndgameNbt.requireInt(tag, "x"), EndgameNbt.requireInt(tag, "z"),
                EndgameNbt.requireInt(tag, "anchor_y"), EndgameNbt.requireLong(tag, "bound_at"),
                EndgameNbt.requireUuid(tag, "bound_by"));
    }
}
