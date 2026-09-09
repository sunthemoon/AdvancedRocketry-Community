package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** All-or-nothing decoder for file-derived pattern IDs and their original bytes. */
public final class MultiblockPatternCatalogDecoder {
    private MultiblockPatternCatalogDecoder() {
    }

    public static MultiblockPatternCatalog decode(Map<String, byte[]> encodedDefinitions) {
        Objects.requireNonNull(encodedDefinitions, "encodedDefinitions");
        if (encodedDefinitions.isEmpty()) {
            throw new PatternDefinitionException("pattern catalog: no definitions were found");
        }
        if (encodedDefinitions.size() > MultiblockPatternCatalog.MAX_DEFINITIONS) {
            throw new PatternDefinitionException("pattern catalog: exceeds 256 definitions");
        }

        TreeMap<String, byte[]> sorted = new TreeMap<>();
        encodedDefinitions.forEach((id, encoded) -> {
            String boundedId = PatternIds.requireResourceId("file-derived pattern id", id);
            if (encoded == null) {
                throw new PatternDefinitionException("pattern catalog: missing bytes for " + boundedId);
            }
            if (sorted.put(boundedId, encoded) != null) {
                throw new PatternDefinitionException("pattern catalog: duplicate file-derived id " + boundedId);
            }
        });

        ArrayList<MultiblockPatternDefinition> decoded = new ArrayList<>(sorted.size());
        sorted.forEach((fileId, encoded) -> {
            MultiblockPatternDefinition definition;
            try {
                definition = MultiblockPatternJsonCodec.decode(encoded);
            } catch (PatternDefinitionException exception) {
                throw new PatternDefinitionException(
                        "pattern catalog " + fileId + ": " + boundedMessage(exception),
                        exception
                );
            }
            if (!fileId.equals(definition.id())) {
                throw new PatternDefinitionException(
                        "pattern catalog " + fileId + ": embedded id must match the file-derived id"
                );
            }
            decoded.add(definition);
        });
        return MultiblockPatternCatalog.create(decoded);
    }

    private static String boundedMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return "invalid definition";
        }
        return message.length() <= 256 ? message : message.substring(0, 256);
    }
}
