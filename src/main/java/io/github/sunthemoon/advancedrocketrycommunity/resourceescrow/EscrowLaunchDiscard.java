package io.github.sunthemoon.advancedrocketrycommunity.resourceescrow;

import io.github.sunthemoon.advancedrocketrycommunity.AdvancedRocketryCommunity;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/** Stateless observations at launch discard sites; never evaluates an admission predicate. */
public final class EscrowLaunchDiscard {
    private EscrowLaunchDiscard() { }

    public static void note(UUID logical, UUID transfer, String site, String reason) {
        AdvancedRocketryCommunity.LOGGER.info("ARCE_LAUNCH_DISCARD logical={} transfer={} site={} reason={}",
                logical, transfer, site, reason);
    }

    /** Only field names from the bounded envelope, never player NBT contents or a second live observation. */
    static String differences(CompoundTag expected, CompoundTag observed) {
        if (expected == null || observed == null) { return "unavailable"; }
        return java.util.stream.Stream.concat(expected.getAllKeys().stream(), observed.getAllKeys().stream())
                .distinct().sorted().filter(key -> !java.util.Objects.equals(expected.get(key), observed.get(key)))
                .collect(java.util.stream.Collectors.joining(","));
    }
}
