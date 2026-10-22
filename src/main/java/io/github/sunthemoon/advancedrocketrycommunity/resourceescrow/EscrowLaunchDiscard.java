package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;

/** Stateless observations at launch discard sites; never evaluates an admission predicate. */
public final class EscrowLaunchDiscard {
    private EscrowLaunchDiscard() { }

    public static void note(String site, Supplier<String> details) {
        log(message -> AdvancedRocketryCommunity.LOGGER.info(message), "ARCE_LAUNCH_DISCARD site=" + site, details);
    }

    /** Only diagnostic work belongs in these suppliers; lifecycle actions must stay outside the guard. */
    public static String detail(Supplier<String> details) {
        try { return java.util.Objects.requireNonNull(details.get()); }
        catch (Throwable unavailable) { return "unavailable"; }
    }

    /** A broken formatter or logging sink must never suppress a launch completion or warm-up transition. */
    public static void log(Consumer<String> sink, String event, Supplier<String> details) {
        try { sink.accept(event + " " + detail(details)); }
        catch (Throwable unavailable) {
            // Deliberately contain diagnostic failures, including sink failures when writing fallback text.
        }
    }

    /** Only field names from the bounded envelope, never player NBT contents or a second live observation. */
    static String differences(CompoundTag expected, CompoundTag observed) {
        return detail(() -> {
            if (expected == null || observed == null) { return "unavailable"; }
            return java.util.stream.Stream.concat(expected.getAllKeys().stream(), observed.getAllKeys().stream())
                    .distinct().sorted().filter(key -> !java.util.Objects.equals(expected.get(key), observed.get(key)))
                    .collect(java.util.stream.Collectors.joining(","));
        });
    }
}
