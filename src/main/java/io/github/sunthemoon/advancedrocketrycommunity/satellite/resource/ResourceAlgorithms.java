package io.github.sunthemoon.advancedrocketrycommunity.satellite.resource;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.RewardEntry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import net.minecraft.resources.ResourceLocation;

/**
 * ADR-052 sections 4–6, versions {@code survey-v1}, {@code asteroid-v1} and {@code gas-v1}: pure functions of
 * persisted inputs, in signed 64-bit arithmetic. A new rule needs a new version name.
 */
public final class ResourceAlgorithms {
    public static final String SURVEY_V1 = "survey-v1";
    public static final String ASTEROID_V1 = "asteroid-v1";
    public static final String GAS_V1 = "gas-v1";
    public static final int ITEMS_PER_CARGO_UNIT = 64;
    public static final int MIN_DURATION = 200;
    public static final int MAX_DURATION = 72_000;

    private ResourceAlgorithms() {
    }

    /** The types in a system, sorted by the full ID string in UTF-16 order (not {@code ResourceLocation} order). */
    public static List<AsteroidType> candidates(Collection<AsteroidType> types, ResourceLocation system) {
        Objects.requireNonNull(system, "system");
        return types.stream()
                .filter(type -> type.inSystem(system))
                .sorted(Comparator.comparing(type -> type.id().toString()))
                .toList();
    }

    /** First 16 hex characters of the SHA-256 of {@code <id>\t<weight>\t<version>\n} per sorted candidate. */
    public static String fingerprint(List<AsteroidType> candidates) {
        StringBuilder lines = new StringBuilder();
        for (AsteroidType type : candidates) {
            lines.append(type.id()).append('\t').append(type.weight()).append('\t').append(type.tableVersion()).append('\n');
        }
        return sha256Hex16(lines.toString().getBytes(StandardCharsets.UTF_8));
    }

    /** First 16 hex characters of the SHA-256 of raw bytes: a table or type version. */
    public static String sha256Hex16(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return HexFormat.of().formatHex(digest).substring(0, 16);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /** {@code survey-v1}: {@code count} instances from the mission seed over the sorted candidates. */
    public static List<GeneratedInstance> survey(long missionSeed, List<AsteroidType> candidates, int count) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("A survey needs at least one candidate type");
        }
        if (count < 1 || count > 4) {
            throw new IllegalArgumentException("A survey creates 1..4 instances");
        }
        long totalWeight = 0L;
        for (AsteroidType type : candidates) {
            totalWeight += type.weight();
        }
        SplitMix64 stream = new SplitMix64(missionSeed ^ SplitMix64.SURVEY01);
        List<GeneratedInstance> instances = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            long seed = stream.next();
            long pick = new SplitMix64(seed ^ SplitMix64.ASTTYPE1).bounded(totalWeight);
            AsteroidType chosen = null;
            long cumulative = 0L;
            for (AsteroidType type : candidates) {
                cumulative += type.weight();
                if (cumulative > pick) {
                    chosen = type;
                    break;
                }
            }
            instances.add(new GeneratedInstance(Objects.requireNonNull(chosen), seed, asteroidYield(chosen, seed)));
        }
        return List.copyOf(instances);
    }

    /** {@code asteroid-v1}: the yield of one instance, ores in file order then the base item. */
    public static List<RewardEntry> asteroidYield(AsteroidType type, long seed) {
        SplitMix64 stream = new SplitMix64(seed ^ SplitMix64.ASTYIELD);
        long dm = stream.bounded(type.massVariabilityPct() + 1L) - type.massVariabilityPct() / 2;
        long mass = clamp(type.mass() * (100L + dm) / 100L, 1L, AsteroidType.MAX_MASS);
        long dr = stream.bounded(type.richnessVariabilityPct() + 1L) - type.richnessVariabilityPct() / 2;
        long richness = clamp(type.richnessPct() + dr, 0L, 100L);
        long ores = mass * richness / 100L;
        long oreWeight = type.oreWeight();
        long[] counts = new long[type.ores().size()];
        for (long draw = 0L; draw < ores; draw++) {
            long pick = stream.bounded(oreWeight);
            long cumulative = 0L;
            for (int index = 0; index < counts.length; index++) {
                cumulative += type.ores().get(index).weight();
                if (cumulative > pick) {
                    counts[index]++;
                    break;
                }
            }
        }
        List<RewardEntry> yield = new ArrayList<>(counts.length + 1);
        for (int index = 0; index < counts.length; index++) {
            if (counts[index] > 0) {
                yield.add(new RewardEntry(type.ores().get(index).item(), (int) counts[index]));
            }
        }
        if (mass - ores > 0) {
            yield.add(new RewardEntry(type.baseItem(), (int) (mass - ores)));
        }
        return List.copyOf(yield);
    }

    /** Takes entries in yield order up to {@code cargo × 64} items; the last may be partial. */
    public static List<RewardEntry> truncate(List<RewardEntry> yield, int cargo) {
        if (cargo < 0) {
            throw new IllegalArgumentException("Cargo must not be negative");
        }
        long remaining = (long) cargo * ITEMS_PER_CARGO_UNIT;
        List<RewardEntry> delivered = new ArrayList<>();
        for (RewardEntry entry : yield) {
            if (remaining <= 0L) {
                break;
            }
            long take = Math.min(entry.count(), remaining);
            delivered.add(new RewardEntry(entry.item(), (int) take));
            remaining -= take;
        }
        return List.copyOf(delivered);
    }

    /** {@code ceil(12,000 × time_multiplier × config × 10 / (100 × 100 × rating))}, clamped to 200..72,000. */
    public static int asteroidDuration(int timeMultiplierPct, int configPct, int rating) {
        if (rating < 1) {
            throw new IllegalArgumentException("Rating must be positive");
        }
        long numerator = 12_000L * timeMultiplierPct * configPct * 10L;
        long denominator = 100L * 100L * rating;
        return (int) clamp(ceilDiv(numerator, denominator), MIN_DURATION, MAX_DURATION);
    }

    /** {@code gas-v1}: rate, base duration, amount and configured duration; no random draw. */
    public static GasResult gas(int amountPer1000Ticks, int rating, int cargo, int configPct) {
        if (rating < 1 || cargo < 1) {
            throw new IllegalArgumentException("Gas harvesting needs a positive rating and cargo");
        }
        long capacity = (long) cargo * ITEMS_PER_CARGO_UNIT;
        long rate = Math.max(1L, (long) amountPer1000Ticks * rating / 10L);
        long base = clamp(ceilDiv(capacity * 1_000L, rate), MIN_DURATION, MAX_DURATION);
        long amount = Math.min(capacity, rate * base / 1_000L);
        long duration = clamp(ceilDiv(base * configPct, 100L), MIN_DURATION, MAX_DURATION);
        return new GasResult((int) rate, (int) base, (int) amount, (int) duration);
    }

    private static long clamp(long value, long minimum, long maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static long ceilDiv(long numerator, long denominator) {
        return Math.floorDiv(numerator + denominator - 1L, denominator);
    }

    /** One {@code survey-v1} instance: its type, seed and full {@code asteroid-v1} yield. */
    public record GeneratedInstance(AsteroidType type, long seed, List<RewardEntry> yield) {
        public GeneratedInstance {
            Objects.requireNonNull(type, "type");
            yield = List.copyOf(yield);
        }
    }

    /** The {@code gas-v1} outputs. */
    public record GasResult(int rate, int base, int amount, int duration) {
    }
}
