package io.github.sunthemoon.advancedrocketrycommunity.endgame.laser;

import io.github.sunthemoon.advancedrocketrycommunity.endgame.root.EndgameNbt;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/**
 * ADR-055 section 3: the controller's side of its one physical link, {@code {marker_id, ops_paid}}, saved in the
 * controller's chunk. {@code link_id} is new for every link and stored by the marker with the controller's ID, so a
 * marker that served an earlier link of the same controller does not count that link's layers as a debt of the new
 * one: it answers {@code LINK_LOST} until its owner resets it. {@code operator_link} records that an operator chose a
 * marker the drill's owner does not own (link rule 1).
 */
final class LaserLink {
    private static final Set<String> KEYS = Set.of("marker_id", "link_id", "ops_paid", "operator_link");

    private final UUID marker;
    private final UUID linkId;
    private final boolean operatorLink;
    private long opsPaid;

    LaserLink(UUID marker, UUID linkId, long opsPaid, boolean operatorLink) {
        this.marker = Objects.requireNonNull(marker, "marker");
        this.linkId = Objects.requireNonNull(linkId, "linkId");
        if (opsPaid < 0) {
            throw new IllegalArgumentException("ops_paid is not negative");
        }
        this.opsPaid = opsPaid;
        this.operatorLink = operatorLink;
    }

    static LaserLink read(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, KEYS, "Laser drill link");
        return new LaserLink(EndgameNbt.requireUuid(tag, "marker_id"), EndgameNbt.requireUuid(tag, "link_id"),
                EndgameNbt.requireLong(tag, "ops_paid"), EndgameNbt.requireBoolean(tag, "operator_link"));
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.put("marker_id", NbtUtils.createUUID(marker));
        tag.put("link_id", NbtUtils.createUUID(linkId));
        tag.putLong("ops_paid", opsPaid);
        tag.putBoolean("operator_link", operatorLink);
        return tag;
    }

    UUID marker() {
        return marker;
    }

    UUID linkId() {
        return linkId;
    }

    long opsPaid() {
        return opsPaid;
    }

    boolean operatorLink() {
        return operatorLink;
    }

    void paid() {
        opsPaid = Math.addExact(opsPaid, 1L);
    }
}
