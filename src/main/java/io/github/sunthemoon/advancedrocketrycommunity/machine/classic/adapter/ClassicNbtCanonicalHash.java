package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;

/** Pure digest of K2 bytes; the caller exclusively owns and keeps the input unchanged. */
final class ClassicNbtCanonicalHash {
    private ClassicNbtCanonicalHash() { }

    static String sha256(CompoundTag root, ClassicNbtLimits limits) {
        byte[] bytes = ClassicNbtCanonicalBytes.encode(root, limits);
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
