package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.scan;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Set;

/** Deterministic content identity independent of scan order or seed. */
public final class VolumeIdentity {
    private static final Comparator<VolumePosition> POSITION_ORDER = Comparator
            .comparingInt(VolumePosition::x)
            .thenComparingInt(VolumePosition::y)
            .thenComparingInt(VolumePosition::z);

    private VolumeIdentity() {
    }

    public static VolumeId fromCells(Set<VolumePosition> cells) {
        Objects.requireNonNull(cells, "cells");
        if (cells.isEmpty()) {
            throw new IllegalArgumentException("A volume identity needs at least one cell");
        }
        ArrayList<VolumePosition> ordered = new ArrayList<>(cells);
        ordered.sort(POSITION_ORDER);
        MessageDigest digest = sha256();
        updateInt(digest, ordered.size());
        for (VolumePosition position : ordered) {
            updateInt(digest, position.x());
            updateInt(digest, position.y());
            updateInt(digest, position.z());
        }
        return new VolumeId(HexFormat.of().formatHex(digest.digest()));
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime has no SHA-256 provider", exception);
        }
    }

    private static void updateInt(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
    }
}
