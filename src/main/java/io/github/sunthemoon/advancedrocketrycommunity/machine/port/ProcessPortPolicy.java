package io.github.sunthemoon.advancedrocketrycommunity.machine.port;

import java.util.List;

public record ProcessPortPolicy(List<ProcessPortDefinition> ports) {
    public static final int MAX_PORTS = 64;

    public ProcessPortPolicy {
        ports = List.copyOf(ports);
        if (ports.isEmpty() || ports.size() > MAX_PORTS) {
            throw new IllegalArgumentException("port policy must contain 1..64 ports");
        }
        for (int first = 0; first < ports.size(); first++) {
            ProcessPortDefinition left = ports.get(first);
            for (int second = first + 1; second < ports.size(); second++) {
                ProcessPortDefinition right = ports.get(second);
                if (left.kind() == right.kind()
                        && left.channel().equals(right.channel())
                        && left.localSides().stream().anyMatch(right.localSides()::contains)
                        && rangesOverlap(left.range(), right.range())) {
                    throw new IllegalArgumentException("overlapping kind/channel/side port ranges");
                }
            }
        }
    }

    private static boolean rangesOverlap(ProcessPortRange left, ProcessPortRange right) {
        return left.first() < right.endExclusive() && right.first() < left.endExclusive();
    }
}
