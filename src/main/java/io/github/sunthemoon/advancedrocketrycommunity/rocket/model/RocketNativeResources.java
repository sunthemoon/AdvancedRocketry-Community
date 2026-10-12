package io.github.sunthemoon.advancedrocketrycommunity.rocket.model;

import io.github.sunthemoon.advancedrocketrycommunity.persistence.BoundedNbt;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Preflight before copying native roots; NC1 supplies canonical resource signatures. */
public final class RocketNativeResources {
    private RocketNativeResources() { }
    public static boolean bounded(Tag root, int bytes) {
        return BoundedNbt.fits(root, bytes, 32, 65_536);
    }
    public static CompoundTag preserve(CompoundTag root, int bytes) {
        return bounded(root, bytes) ? root.copy() : root;
    }
    public static String signature(CompoundTag owned) {
        return hash(RocketNativeFrame.encodeOwned(owned));
    }
    public static String hash(String value) { return hash(value.getBytes(StandardCharsets.UTF_8)); }
    private static String hash(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
