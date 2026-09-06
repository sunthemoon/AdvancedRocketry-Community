package io.github.sunthemoon.advancedrocketrycommunity.celestial.context;

@FunctionalInterface
public interface InstanceBodyContextResolver {
    BodyContextResolution resolve(WorldLocation location);
}
