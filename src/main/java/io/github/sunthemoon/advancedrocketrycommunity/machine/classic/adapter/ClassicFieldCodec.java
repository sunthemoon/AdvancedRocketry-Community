package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Strict fields after whole-root structural preflight; never a native owner resolver. */
final class ClassicFieldCodec {
    static void fields(CompoundTag root, String[] required, String... optional) {
        Set<String> allowed = new HashSet<>(Arrays.asList(required));
        allowed.addAll(Arrays.asList(optional));
        ClassicValueChecks.require(root.getAllKeys().containsAll(Arrays.asList(required))
                && allowed.containsAll(root.getAllKeys()), "Missing or unknown fields");
    }

    static Tag tag(CompoundTag root, String key, int type) {
        Tag value = root.get(key);
        ClassicValueChecks.require(value != null && value.getId() == type, "Wrong field type: " + key);
        return value;
    }

    static CompoundTag compound(CompoundTag root, String key) {
        return (CompoundTag) tag(root, key, Tag.TAG_COMPOUND);
    }

    static String string(CompoundTag root, String key, int maximum) {
        tag(root, key, Tag.TAG_STRING);
        String value = root.getString(key);
        ClassicValueChecks.require(value.length() <= maximum, "String limit: " + key);
        return value;
    }

    static ResourceLocation id(CompoundTag root, String key) {
        return ClassicValueChecks.id(string(root, key, 128));
    }

    static int integer(CompoundTag root, String key) {
        tag(root, key, Tag.TAG_INT);
        return root.getInt(key);
    }

    static long number(CompoundTag root, String key) {
        tag(root, key, Tag.TAG_LONG);
        return root.getLong(key);
    }

    static UUID uuid(CompoundTag root, String key) {
        tag(root, key, Tag.TAG_INT_ARRAY);
        ClassicValueChecks.require(root.getIntArray(key).length == 4, "UUID array length");
        return root.getUUID(key);
    }

    static ListTag list(CompoundTag root, String key, int maximum) {
        ListTag list = (ListTag) tag(root, key, Tag.TAG_LIST);
        ClassicValueChecks.require(list.size() <= maximum
                && list.getElementType() == (list.isEmpty() ? Tag.TAG_END : Tag.TAG_COMPOUND), "List type/limit");
        return list;
    }

    static BlockPos position(CompoundTag root) {
        fields(root, new String[] {"x", "y", "z"});
        return new BlockPos(integer(root, "x"), integer(root, "y"), integer(root, "z"));
    }

    static CompoundTag position(BlockPos position) {
        CompoundTag result = new CompoundTag();
        result.putInt("x", position.getX()); result.putInt("y", position.getY()); result.putInt("z", position.getZ());
        return result;
    }

    static <E extends Enum<E>> E enumeration(Class<E> type, String value, boolean lower) {
        for (E option : type.getEnumConstants()) {
            if ((lower ? option.name().toLowerCase(Locale.ROOT) : option.name()).equals(value)) { return option; }
        }
        throw new IllegalArgumentException("Unknown enum value");
    }

    static void schema(CompoundTag root) {
        ClassicValueChecks.require(integer(root, "schema_version") == 1, "Unsupported schema");
    }

    static void bounded(Tag root, ClassicNbtLimits limits) {
        ClassicValueChecks.require(ClassicNbtShape.fits(root, limits), "Unbounded root");
    }

    private ClassicFieldCodec() { }
}
