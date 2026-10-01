package io.github.sunthemoon.advancedrocketrycommunity.endgame.root;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict NBT reads for endgame roots: a missing, mistyped or unknown field is an error, never a default. */
public final class EndgameNbt {
    private EndgameNbt() {
    }

    public static int uncompressedBytes(CompoundTag tag) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                NbtIo.write(tag, output);
            }
            return bytes.size();
        } catch (IOException exception) {
            throw new IllegalStateException("In-memory endgame NBT sizing failed", exception);
        }
    }

    public static void requireKeys(CompoundTag source, Set<String> allowed, String record) {
        for (String key : source.getAllKeys()) {
            if (!allowed.contains(key)) {
                throw new IllegalArgumentException(record + " has an unknown field " + key);
            }
        }
    }

    public static int requireInt(CompoundTag source, String key) {
        require(source, key, Tag.TAG_INT);
        return source.getInt(key);
    }

    public static long requireLong(CompoundTag source, String key) {
        require(source, key, Tag.TAG_LONG);
        return source.getLong(key);
    }

    public static String requireString(CompoundTag source, String key, int maxChars) {
        require(source, key, Tag.TAG_STRING);
        String value = source.getString(key);
        if (value.length() > maxChars) {
            throw new IllegalArgumentException(key + " exceeds " + maxChars + " characters");
        }
        return value;
    }

    public static ResourceLocation requireLocation(CompoundTag source, String key, int maxChars) {
        ResourceLocation location = ResourceLocation.tryParse(requireString(source, key, maxChars));
        if (location == null) {
            throw new IllegalArgumentException(key + " is not a resource location");
        }
        return location;
    }

    public static UUID requireUuid(CompoundTag source, String key) {
        require(source, key, Tag.TAG_INT_ARRAY);
        if (source.getIntArray(key).length != 4) {
            throw new IllegalArgumentException(key + " is not a UUID");
        }
        return NbtUtils.loadUUID(source.get(key));
    }

    public static long[] requireLongArray(CompoundTag source, String key) {
        require(source, key, Tag.TAG_LONG_ARRAY);
        return source.getLongArray(key);
    }

    public static ListTag requireList(CompoundTag source, String key, int elementType, int maxSize) {
        require(source, key, Tag.TAG_LIST);
        ListTag list = (ListTag) source.get(key);
        if (!list.isEmpty() && list.getElementType() != elementType) {
            throw new IllegalArgumentException(key + " holds the wrong element type");
        }
        if (list.size() > maxSize) {
            throw new IllegalArgumentException(key + " exceeds " + maxSize + " entries");
        }
        return list;
    }

    private static void require(CompoundTag source, String key, int type) {
        if (!source.contains(key, type)) {
            throw new IllegalArgumentException("Missing or mistyped " + key);
        }
    }
}
