package io.github.sunthemoon.advancedrocketrycommunity.atmosphere.torch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * The reviewed schema-1 {@code torch_pairs} catalog (ADR-066 section 3.2): the permanent vanilla pair plus data pairs,
 * at most 64 in all, with no block mapped twice. Immutable; decoding is pure and bounded before parsing.
 */
public final class TorchPairCatalog {
    public static final String DIRECTORY = "torch_pairs";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_PAIRS = 64;
    public static final int MAX_FILE_BYTES = 4_096;
    public static final int MAX_ID_BYTES = 128;
    private static final int MAX_ERRORS = 32;
    private static final Set<String> FIELDS = Set.of("schema_version", "standing_block", "wall_block", "item");
    public static final TorchPair VANILLA = new TorchPair(minecraft("torch"), minecraft("torch"),
            minecraft("wall_torch"), minecraft("torch"));
    public static final TorchPairCatalog BUILT_IN = new TorchPairCatalog(List.of(VANILLA));

    private final Map<ResourceLocation, TorchPair> pairs;
    private final Map<ResourceLocation, TorchPair> byBlock;

    private TorchPairCatalog(List<TorchPair> values) {
        if (values.size() > MAX_PAIRS || !values.contains(VANILLA)) {
            throw new IllegalArgumentException("A torch catalog holds the vanilla pair and at most " + MAX_PAIRS + " pairs");
        }
        Map<ResourceLocation, TorchPair> ordered = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
        Map<ResourceLocation, TorchPair> blocks = new LinkedHashMap<>();
        for (TorchPair pair : values) {
            if (ordered.putIfAbsent(pair.id(), pair) != null || blocks.putIfAbsent(pair.standingBlock(), pair) != null
                    || blocks.putIfAbsent(pair.wallBlock(), pair) != null) {
                throw new IllegalArgumentException("Duplicate torch pair or block mapping: " + pair.id());
            }
        }
        pairs = Collections.unmodifiableMap(ordered);
        byBlock = Collections.unmodifiableMap(blocks);
    }

    public Optional<TorchPair> get(ResourceLocation id) {
        return Optional.ofNullable(pairs.get(id));
    }

    /** The pair that maps this lit standing or wall block. */
    public Optional<TorchPair> forBlock(ResourceLocation block) {
        return Optional.ofNullable(byBlock.get(block));
    }

    public List<TorchPair> pairs() {
        return List.copyOf(pairs.values());
    }

    public int size() {
        return pairs.size();
    }

    /** The catalog's pairs plus extra ones, for disposable GameTest fixtures only. */
    TorchPairCatalog with(List<TorchPair> extra) {
        List<TorchPair> all = new ArrayList<>(pairs.values());
        all.addAll(extra);
        return new TorchPairCatalog(all);
    }

    /** Decodes one file; the message of a thrown {@link IllegalArgumentException} names the first defect. */
    public static TorchPair decode(ResourceLocation fileId, byte[] raw) {
        Objects.requireNonNull(raw, "raw");
        if (fileId == null || bytes(fileId.toString()) > MAX_ID_BYTES) {
            throw new IllegalArgumentException("The pair ID must have at most " + MAX_ID_BYTES + " bytes");
        }
        if (raw.length > MAX_FILE_BYTES) {
            throw new IllegalArgumentException("The file exceeds " + MAX_FILE_BYTES + " bytes");
        }
        if (!(JsonParser.parseString(new String(raw, StandardCharsets.UTF_8)) instanceof JsonObject object)) {
            throw new IllegalArgumentException("The pair must be an object");
        }
        if (!object.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("The pair must have exactly the fields " + FIELDS);
        }
        if (!(object.get("schema_version") instanceof JsonPrimitive version) || !version.isNumber()
                || version.getAsBigDecimal().compareTo(BigDecimal.valueOf(SCHEMA_VERSION)) != 0) {
            throw new IllegalArgumentException("Unsupported torch pair schema");
        }
        return new TorchPair(fileId, id(object.get("standing_block"), "standing_block"),
                id(object.get("wall_block"), "wall_block"), id(object.get("item"), "item"));
    }

    /**
     * Decodes every data file and checks the set with the built-in pair. Any read, decode, registry or mapping error
     * rejects the whole set, and the result carries at most 32 errors.
     */
    public static Result build(Map<ResourceLocation, byte[]> files, List<String> readErrors,
                               Predicate<TorchPair> registered) {
        List<String> errors = new ArrayList<>(readErrors);
        List<TorchPair> decoded = new ArrayList<>(List.of(VANILLA));
        if (files.size() > MAX_PAIRS - 1) {
            errors.add(DIRECTORY + " holds more than " + (MAX_PAIRS - 1) + " data pairs");
        } else {
            Map<ResourceLocation, byte[]> ordered = new TreeMap<>(Comparator.comparing(ResourceLocation::toString));
            ordered.putAll(files);
            Map<ResourceLocation, ResourceLocation> owners = new LinkedHashMap<>();
            owners.put(VANILLA.standingBlock(), VANILLA.id());
            owners.put(VANILLA.wallBlock(), VANILLA.id());
            for (Map.Entry<ResourceLocation, byte[]> file : ordered.entrySet()) {
                TorchPair pair;
                try {
                    pair = decode(file.getKey(), file.getValue());
                } catch (RuntimeException invalid) {
                    errors.add(file.getKey() + ": " + invalid.getMessage());
                    continue;
                }
                if (pair.id().equals(VANILLA.id())) {
                    errors.add(pair.id() + ": the built-in vanilla pair cannot be redefined");
                } else if (owners.containsKey(pair.standingBlock()) || owners.containsKey(pair.wallBlock())) {
                    errors.add(pair.id() + ": a block is already mapped by another pair");
                } else if (!registered.test(pair)) {
                    errors.add(pair.id() + ": not a registered torch pair with supported shapes and a shared item");
                } else {
                    owners.put(pair.standingBlock(), pair.id());
                    owners.put(pair.wallBlock(), pair.id());
                    decoded.add(pair);
                }
            }
        }
        if (!errors.isEmpty()) {
            return new Result(Optional.empty(), List.copyOf(errors.subList(0, Math.min(MAX_ERRORS, errors.size()))));
        }
        return new Result(Optional.of(new TorchPairCatalog(decoded)), List.of());
    }

    public record Result(Optional<TorchPairCatalog> catalog, List<String> errors) {
    }

    /** A canonical, namespaced resource location of at most 128 UTF-8 bytes, or null. */
    static ResourceLocation canonical(String value) {
        if (value == null || bytes(value) > MAX_ID_BYTES || value.indexOf(':') < 0) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(value);
        return id != null && !id.getPath().isEmpty() && id.toString().equals(value) ? id : null;
    }

    private static ResourceLocation id(JsonElement element, String name) {
        ResourceLocation id = element instanceof JsonPrimitive primitive && primitive.isString()
                ? canonical(primitive.getAsString()) : null;
        if (id == null) {
            throw new IllegalArgumentException(name + " must be a namespaced ID of at most " + MAX_ID_BYTES + " bytes");
        }
        return id;
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private static ResourceLocation minecraft(String path) {
        return new ResourceLocation("minecraft", path);
    }
}
