package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKey;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceKind;
import java.util.Set;

/** Closed exact-ID filter; no callback, class name or script can enter a port definition. */
public record ProcessPortFilter(boolean allowAny, Set<String> resourceIds) {
    public static final int MAX_RESOURCE_IDS = 32;

    public ProcessPortFilter {
        resourceIds = Set.copyOf(resourceIds);
        if (resourceIds.size() > MAX_RESOURCE_IDS || (allowAny && !resourceIds.isEmpty())) {
            throw new IllegalArgumentException("port filter is outside its bounded form");
        }
        for (String resourceId : resourceIds) {
            new ProcessResourceKey(ProcessResourceKind.ITEM, "filter", resourceId);
        }
    }

    public static ProcessPortFilter any() {
        return new ProcessPortFilter(true, Set.of());
    }

    public static ProcessPortFilter exact(Set<String> resourceIds) {
        if (resourceIds.isEmpty()) {
            throw new IllegalArgumentException("an exact filter requires at least one resource ID");
        }
        return new ProcessPortFilter(false, resourceIds);
    }

    public boolean allows(String resourceId) {
        return allowAny || resourceIds.contains(resourceId);
    }
}
