package io.github.sunthemoon.advancedrocketrycommunity.travel.route.model;

public final class RouteLimits {
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_RESOURCE_LOCATION_CHARS = 128;
    public static final int MAX_DISTANCE_UNITS = 1_000_000;
    public static final int MAX_ROUTES = 512;
    public static final int MAX_ANCHORS = 256;
    public static final int MAX_OUTGOING_EDGES = 64;
    public static final int MAX_EXPANDED_NODES = 1_024;
    public static final int MAX_CACHED_PLANS = 256;
    public static final int MAX_JSON_CHARS_PER_ROUTE = 4_096;
    public static final int MAX_REPORTED_ERRORS = 8;
    public static final int MAX_STATUS_MESSAGE_CHARS = 2_048;

    private RouteLimits() {
    }
}
