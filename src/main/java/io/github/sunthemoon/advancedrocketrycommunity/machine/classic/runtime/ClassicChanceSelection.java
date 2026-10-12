package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicChanceOutput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter.ClassicItemOutput;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.function.IntSupplier;
import java.util.function.ToIntFunction;
import net.minecraft.resources.ResourceLocation;

/** Stable batch randomness. No bank revision, retry count, player or world clock enters the seed. */
public final class ClassicChanceSelection {
    public static long seed(UUID machine, String signature, long ordinal) {
        if (ordinal < 1 || !signature.matches("[0-9a-f]{64}")) { throw new IllegalArgumentException("Chance seed identity"); }
        ByteBuffer bytes = ByteBuffer.allocate(56).putLong(machine.getMostSignificantBits())
                .putLong(machine.getLeastSignificantBits()).put(HexFormat.of().parseHex(signature)).putLong(ordinal);
        try { return ByteBuffer.wrap(MessageDigest.getInstance("SHA-256").digest(bytes.array())).getLong(); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    public static List<ClassicItemOutput> choose(List<ClassicChanceOutput> candidates, long seed,
                                               ToIntFunction<ResourceLocation> stackLimit) {
        Random random = new Random(seed);
        return choose(candidates, () -> random.nextInt(100) + 1, stackLimit);
    }
    static List<ClassicItemOutput> choose(List<ClassicChanceOutput> candidates, IntSupplier roll,
                                         ToIntFunction<ResourceLocation> stackLimit) {
        if (candidates.size() > 16) { throw new IllegalArgumentException("Chance candidates bound"); }
        var chosen = new ArrayList<ClassicItemOutput>(); int successes = 0;
        for (var candidate : candidates) {
            int value = roll.getAsInt();
            if (value < 1 || value > 100) { throw new IllegalArgumentException("Chance roll range"); }
            if (value > candidate.weight()) { continue; }
            int maximum = Math.min(64, stackLimit.applyAsInt(candidate.itemId()));
            if (maximum < candidate.count()) { throw new IllegalArgumentException("Chance stack limit"); }
            int remaining = candidate.count();
            for (int i = 0; i < chosen.size() && remaining > 0; i++) {
                var existing = chosen.get(i);
                if (!existing.itemId().equals(candidate.itemId())) { continue; }
                int count = Math.min(remaining, maximum - existing.count());
                chosen.set(i, new ClassicItemOutput(existing.itemId(), existing.count() + count)); remaining -= count;
            }
            if (remaining > 0) { chosen.add(new ClassicItemOutput(candidate.itemId(), remaining)); }
            if (++successes == 4) { break; }
        }
        return List.copyOf(chosen);
    }
    private ClassicChanceSelection() { }
}
