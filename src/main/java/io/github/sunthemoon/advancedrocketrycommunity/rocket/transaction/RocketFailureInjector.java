package io.github.sunthemoon.advancedrocketrycommunity.rocket.transaction;

@FunctionalInterface
public interface RocketFailureInjector {
    RocketFailureInjector NONE = (point, progress) -> {
    };

    void check(RocketFailurePoint point, int progress);
}
