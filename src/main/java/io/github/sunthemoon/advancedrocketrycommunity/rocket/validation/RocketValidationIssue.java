package io.github.sunthemoon.advancedrocketrycommunity.rocket.validation;

import io.github.sunthemoon.advancedrocketrycommunity.rocket.model.RocketPosition;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

public final class RocketValidationIssue {
    private final RocketValidationCode code;
    private final RocketPosition position;
    private final Map<String, String> parameters;

    public RocketValidationIssue(
            RocketValidationCode code,
            RocketPosition position,
            Map<String, String> parameters
    ) {
        this.code = Objects.requireNonNull(code, "code");
        if (code == RocketValidationCode.SUCCESS) {
            throw new IllegalArgumentException("A validation issue cannot use SUCCESS");
        }
        this.position = position;
        Objects.requireNonNull(parameters, "parameters");
        TreeMap<String, String> sorted = new TreeMap<>();
        parameters.forEach((key, value) -> sorted.put(
                Objects.requireNonNull(key, "parameter key"),
                Objects.requireNonNull(value, "parameter value")
        ));
        this.parameters = Collections.unmodifiableMap(sorted);
    }

    public RocketValidationIssue(RocketValidationCode code, Map<String, String> parameters) {
        this(code, null, parameters);
    }

    public RocketValidationCode code() {
        return code;
    }

    public Optional<RocketPosition> position() {
        return Optional.ofNullable(position);
    }

    public Map<String, String> parameters() {
        return parameters;
    }

    public String translationKey() {
        return code.translationKey();
    }
}
