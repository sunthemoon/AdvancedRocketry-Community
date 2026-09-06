package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Strict schema-1 decoder for bounded datapack multiblock patterns. */
public final class MultiblockPatternJsonCodec {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "schema_version",
            "id",
            "size",
            "controller_anchor",
            "allowed_rotations",
            "allow_mirror_local_x",
            "cells"
    );
    private static final Set<String> POSITION_FIELDS = Set.of("x", "y", "z");
    private static final Set<String> CELL_FIELDS = Set.of("x", "y", "z", "matcher");
    private static final Set<String> MATCHER_FIELDS = Set.of(
            "block",
            "tag",
            "air",
            "controller",
            "port",
            "optional"
    );

    private MultiblockPatternJsonCodec() {
    }

    public static MultiblockPatternDefinition decode(byte[] encoded) {
        if (encoded == null || encoded.length == 0) {
            throw failure("pattern", "definition is empty");
        }
        if (encoded.length > MultiblockPatternDefinition.MAX_DEFINITION_BYTES) {
            throw failure("pattern", "definition exceeds 65536 bytes");
        }

        String json = decodeUtf8(encoded);
        try {
            JsonElement root = JsonParser.parseString(json);
            JsonObject object = requireObject(root, "pattern");
            requireFields(object, ROOT_FIELDS, ROOT_FIELDS, "pattern");

            PatternSize size = readSize(object.get("size"), "pattern.size");
            PatternPosition anchor = readPosition(object.get("controller_anchor"), "pattern.controller_anchor");
            EnumSet<PatternRotation> rotations = readRotations(
                    object.get("allowed_rotations"),
                    "pattern.allowed_rotations"
            );
            Map<PatternPosition, PatternMatcher> cells = readCells(object.get("cells"), size);

            return new MultiblockPatternDefinition(
                    requireString(object.get("id"), "pattern.id"),
                    requireInt(object.get("schema_version"), "pattern.schema_version"),
                    encoded.length,
                    size,
                    anchor,
                    rotations,
                    requireBoolean(object.get("allow_mirror_local_x"), "pattern.allow_mirror_local_x"),
                    cells
            );
        } catch (PatternDefinitionException exception) {
            throw exception;
        } catch (JsonSyntaxException | IllegalStateException | ArithmeticException exception) {
            throw new PatternDefinitionException("pattern: invalid JSON value", exception);
        } catch (IllegalArgumentException exception) {
            throw new PatternDefinitionException("pattern: " + boundedMessage(exception), exception);
        }
    }

    private static String decodeUtf8(byte[] encoded) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(encoded))
                    .toString();
        } catch (CharacterCodingException exception) {
            throw new PatternDefinitionException("pattern: definition is not valid UTF-8", exception);
        }
    }

    private static PatternSize readSize(JsonElement element, String path) {
        JsonObject object = requireObject(element, path);
        requireFields(object, POSITION_FIELDS, POSITION_FIELDS, path);
        return new PatternSize(
                requireInt(object.get("x"), path + ".x"),
                requireInt(object.get("y"), path + ".y"),
                requireInt(object.get("z"), path + ".z")
        );
    }

    private static PatternPosition readPosition(JsonElement element, String path) {
        JsonObject object = requireObject(element, path);
        requireFields(object, POSITION_FIELDS, POSITION_FIELDS, path);
        return new PatternPosition(
                requireInt(object.get("x"), path + ".x"),
                requireInt(object.get("y"), path + ".y"),
                requireInt(object.get("z"), path + ".z")
        );
    }

    private static EnumSet<PatternRotation> readRotations(JsonElement element, String path) {
        if (element == null || !element.isJsonArray()) {
            throw failure(path, "must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() < 1 || array.size() > PatternRotation.values().length) {
            throw failure(path, "must contain between 1 and 4 rotations");
        }
        EnumSet<PatternRotation> rotations = EnumSet.noneOf(PatternRotation.class);
        for (int index = 0; index < array.size(); index++) {
            int degrees = requireInt(array.get(index), path + "[" + index + "]");
            PatternRotation rotation = switch (degrees) {
                case 0 -> PatternRotation.ZERO;
                case 90 -> PatternRotation.CLOCKWISE_90;
                case 180 -> PatternRotation.CLOCKWISE_180;
                case 270 -> PatternRotation.CLOCKWISE_270;
                default -> throw failure(path + "[" + index + "]", "unsupported rotation");
            };
            if (!rotations.add(rotation)) {
                throw failure(path + "[" + index + "]", "duplicate rotation");
            }
        }
        return rotations;
    }

    private static Map<PatternPosition, PatternMatcher> readCells(JsonElement element, PatternSize size) {
        String path = "pattern.cells";
        if (element == null || !element.isJsonArray()) {
            throw failure(path, "must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != size.volume()) {
            throw failure(path, "must completely cover the declared volume");
        }
        Map<PatternPosition, PatternMatcher> cells = new HashMap<>();
        for (int index = 0; index < array.size(); index++) {
            String cellPath = path + "[" + index + "]";
            JsonObject object = requireObject(array.get(index), cellPath);
            requireFields(object, CELL_FIELDS, CELL_FIELDS, cellPath);
            PatternPosition position = new PatternPosition(
                    requireInt(object.get("x"), cellPath + ".x"),
                    requireInt(object.get("y"), cellPath + ".y"),
                    requireInt(object.get("z"), cellPath + ".z")
            );
            if (!size.contains(position)) {
                throw failure(cellPath, "position is outside the declared size");
            }
            PatternMatcher previous = cells.put(position, readMatcher(object.get("matcher"), cellPath + ".matcher"));
            if (previous != null) {
                throw failure(cellPath, "duplicates a cell position");
            }
        }
        return cells;
    }

    private static PatternMatcher readMatcher(JsonElement element, String path) {
        JsonObject object = requireObject(element, path);
        requireFields(object, MATCHER_FIELDS, Set.of(), path);
        if (object.size() != 1) {
            throw failure(path, "must contain exactly one matcher kind");
        }
        Map.Entry<String, JsonElement> entry = object.entrySet().iterator().next();
        return switch (entry.getKey()) {
            case "block" -> new PatternMatcher.ExactBlock(requireString(entry.getValue(), path + ".block"));
            case "tag" -> new PatternMatcher.BlockTag(requireString(entry.getValue(), path + ".tag"));
            case "air" -> {
                requireTrue(entry.getValue(), path + ".air");
                yield new PatternMatcher.Air();
            }
            case "controller" -> {
                requireTrue(entry.getValue(), path + ".controller");
                yield new PatternMatcher.Controller();
            }
            case "port" -> new PatternMatcher.Port(requireString(entry.getValue(), path + ".port"));
            case "optional" -> new PatternMatcher.OptionalCell(readMatcher(entry.getValue(), path + ".optional"));
            default -> throw failure(path, "contains an unsupported matcher kind");
        };
    }

    private static JsonObject requireObject(JsonElement element, String path) {
        if (element == null || !element.isJsonObject()) {
            throw failure(path, "must be an object");
        }
        return element.getAsJsonObject();
    }

    private static void requireFields(
            JsonObject object,
            Set<String> allowed,
            Set<String> required,
            String path
    ) {
        for (String field : object.keySet()) {
            if (!allowed.contains(field)) {
                throw failure(path, "contains unknown field " + field);
            }
        }
        for (String field : required) {
            if (!object.has(field)) {
                throw failure(path, "is missing field " + field);
            }
        }
    }

    private static int requireInt(JsonElement element, String path) {
        JsonPrimitive primitive = requirePrimitive(element, path);
        if (!primitive.isNumber()) {
            throw failure(path, "must be an integer");
        }
        try {
            return new BigDecimal(primitive.getAsString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw failure(path, "must be a 32-bit integer");
        }
    }

    private static String requireString(JsonElement element, String path) {
        JsonPrimitive primitive = requirePrimitive(element, path);
        if (!primitive.isString()) {
            throw failure(path, "must be a string");
        }
        return primitive.getAsString();
    }

    private static boolean requireBoolean(JsonElement element, String path) {
        JsonPrimitive primitive = requirePrimitive(element, path);
        if (!primitive.isBoolean()) {
            throw failure(path, "must be a boolean");
        }
        return primitive.getAsBoolean();
    }

    private static void requireTrue(JsonElement element, String path) {
        if (!requireBoolean(element, path)) {
            throw failure(path, "must be true");
        }
    }

    private static JsonPrimitive requirePrimitive(JsonElement element, String path) {
        if (element == null || !element.isJsonPrimitive()) {
            throw failure(path, "must be a primitive value");
        }
        return element.getAsJsonPrimitive();
    }

    private static PatternDefinitionException failure(String path, String message) {
        return new PatternDefinitionException(path + ": " + message);
    }

    private static String boundedMessage(IllegalArgumentException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "invalid definition";
        }
        return message.length() <= 160 ? message : message.substring(0, 160);
    }
}
