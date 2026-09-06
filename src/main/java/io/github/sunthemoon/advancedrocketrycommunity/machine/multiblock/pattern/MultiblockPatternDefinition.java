package io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Complete, immutable and bounded multiblock pattern definition. */
public record MultiblockPatternDefinition(
        String id,
        int schemaVersion,
        int encodedSizeBytes,
        PatternSize size,
        PatternPosition controllerAnchor,
        Set<PatternRotation> allowedRotations,
        boolean allowMirrorLocalX,
        Map<PatternPosition, PatternMatcher> cells
) {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_DEFINITION_BYTES = 65_536;
    public static final int MAX_PORTS = 64;

    public MultiblockPatternDefinition {
        id = PatternIds.requireResourceId("id", id);
        if (schemaVersion != SCHEMA_VERSION) {
            throw new IllegalArgumentException("unsupported multiblock pattern schema");
        }
        if (encodedSizeBytes < 1 || encodedSizeBytes > MAX_DEFINITION_BYTES) {
            throw new IllegalArgumentException("encoded pattern exceeds the byte limit");
        }
        Objects.requireNonNull(size, "size");
        Objects.requireNonNull(controllerAnchor, "controllerAnchor");
        if (!size.contains(controllerAnchor)) {
            throw new IllegalArgumentException("controller anchor is outside the pattern");
        }
        Objects.requireNonNull(allowedRotations, "allowedRotations");
        if (allowedRotations.isEmpty()) {
            throw new IllegalArgumentException("pattern must allow at least one rotation");
        }
        allowedRotations = Collections.unmodifiableSet(EnumSet.copyOf(allowedRotations));
        Objects.requireNonNull(cells, "cells");
        TreeMap<PatternPosition, PatternMatcher> copy = new TreeMap<>();
        cells.forEach((position, matcher) -> {
            Objects.requireNonNull(position, "cell position");
            Objects.requireNonNull(matcher, "cell matcher");
            if (!size.contains(position)) {
                throw new IllegalArgumentException("cell is outside the pattern size");
            }
            copy.put(position, matcher);
        });
        if (copy.size() != size.volume()) {
            throw new IllegalArgumentException("pattern cells must completely cover the declared volume");
        }
        if (!(copy.get(controllerAnchor) instanceof PatternMatcher.Controller)) {
            throw new IllegalArgumentException("controller anchor must contain the controller matcher");
        }
        long controllers = copy.values().stream().filter(PatternMatcher.Controller.class::isInstance).count();
        long ports = copy.values().stream().filter(MultiblockPatternDefinition::containsPort).count();
        if (controllers != 1 || ports > MAX_PORTS) {
            throw new IllegalArgumentException("pattern must contain one controller and at most 64 ports");
        }
        cells = Collections.unmodifiableMap(copy);
    }

    public boolean allows(PatternTransform transform) {
        return allowedRotations.contains(transform.rotation()) && (!transform.mirroredLocalX() || allowMirrorLocalX);
    }

    private static boolean containsPort(PatternMatcher matcher) {
        return matcher instanceof PatternMatcher.Port
                || (matcher instanceof PatternMatcher.OptionalCell optional
                && optional.matcher() instanceof PatternMatcher.Port);
    }
}
