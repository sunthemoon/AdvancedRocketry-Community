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
 * one: it answers {@code LINK_LOST} until its owner resets it. {@code generation} is the marker generation of this
 * link's first contact ({@code -1} before it); an unlinked marker of another generation was reset since and never
 * adopts the link again (review C11R-M1). {@code operator_link} records that an operator chose a marker the drill's
 * owner does not own (link rule 1).
 */
final class LaserLink {
    private static final Set<String> KEYS = Set.of("marker_id", "link_id", "ops_paid", "operator_link", "generation");

    private final UUID marker;
    private final UUID linkId;
    private final boolean operatorLink;
    private long opsPaid;
    private long generation;

    LaserLink(UUID marker, UUID linkId, long opsPaid, boolean operatorLink, long generation) {
        this.marker = Objects.requireNonNull(marker, "marker");
        this.linkId = Objects.requireNonNull(linkId, "linkId");
        if (opsPaid < 0) {
            throw new IllegalArgumentException("ops_paid is not negative");
        }
        if (generation < -1) {
            throw new IllegalArgumentException("A link generation is -1 or more");
        }
        this.opsPaid = opsPaid;
        this.operatorLink = operatorLink;
        this.generation = generation;
    }

    /** A new link, with a new {@code link_id}, that has not touched its marker yet. */
    static LaserLink create(UUID marker, boolean operatorLink) {
        return new LaserLink(marker, UUID.randomUUID(), 0L, operatorLink, -1L);
    }

    static LaserLink read(CompoundTag tag) {
        EndgameNbt.requireKeys(tag, KEYS, "Laser drill link");
        return new LaserLink(EndgameNbt.requireUuid(tag, "marker_id"), EndgameNbt.requireUuid(tag, "link_id"),
                EndgameNbt.requireLong(tag, "ops_paid"), EndgameNbt.requireBoolean(tag, "operator_link"),
                EndgameNbt.requireLong(tag, "generation"));
    }

    CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.put("marker_id", NbtUtils.createUUID(marker));
        tag.put("link_id", NbtUtils.createUUID(linkId));
        tag.putLong("ops_paid", opsPaid);
        tag.putBoolean("operator_link", operatorLink);
        tag.putLong("generation", generation);
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

    long generation() {
        return generation;
    }

    void paid() {
        opsPaid = Math.addExact(opsPaid, 1L);
    }

    /** Records the generation of the marker this link touched; true when it changed (the first contact). */
    boolean touched(long markerGeneration) {
        if (markerGeneration < 0) {
            throw new IllegalArgumentException("A marker generation is not negative");
        }
        boolean changed = generation != markerGeneration;
        generation = markerGeneration;
        return changed;
    }
}
