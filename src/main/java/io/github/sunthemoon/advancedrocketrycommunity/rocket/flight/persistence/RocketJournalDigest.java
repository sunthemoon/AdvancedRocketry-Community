package io.github.sunthemoon.advancedrocketrycommunity.rocket.flight.persistence;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/** NC1 sorted-key framing streamed to SHA-256, without allocating a journal-sized canonical buffer. */
final class RocketJournalDigest {
    // Snapshot/flight roots permit depth 32; transfers/list/root add three levels.
    private static final int MAX_DEPTH = 35;

    private RocketJournalDigest() { }

    static String of(CompoundTag payload, int maxBytes) {
        // Every native node occupies at least one byte; the byte quota also bounds visits.
        if (!BoundedNbt.fits(payload, maxBytes, MAX_DEPTH, maxBytes)) {
            throw new IllegalArgumentException("Journal digest exceeds its native envelope");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var output = new DataOutputStream(new DigestOutputStream(OutputStream.nullOutputStream(), digest))) {
                output.writeByte(Tag.TAG_COMPOUND);
                output.writeUTF("");
                write(payload, output);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("In-memory journal digest failed", impossible);
        }
    }

    private static void write(Tag tag, DataOutputStream output) throws IOException {
        if (tag instanceof CompoundTag compound) {
            var keys = compound.getAllKeys().stream().sorted((left, right) -> {
                int order = Arrays.compareUnsigned(left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
                return order != 0 ? order : left.compareTo(right);
            }).toList();
            for (String key : keys) {
                Tag child = compound.get(key);
                output.writeByte(child.getId());
                output.writeUTF(key);
                write(child, output);
            }
            output.writeByte(Tag.TAG_END);
        } else if (tag instanceof ListTag list) {
            output.writeByte(list.getElementType());
            output.writeInt(list.size());
            for (Tag child : list) { write(child, output); }
        } else {
            tag.write(output);
        }
    }
}
