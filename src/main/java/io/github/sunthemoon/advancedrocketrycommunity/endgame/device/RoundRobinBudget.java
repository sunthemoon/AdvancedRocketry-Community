package io.github.sunthemoon.advancedrocketrycommunity.endgame.device;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * ADR-054 section 7: a per-tick work cap served in device ID order (canonical lowercase UUID strings), round-robin
 * from the last served device. Devices that are due request a slot during a tick; at the end of the tick at most
 * {@code cap} of them are granted one for the next tick, the rest wait and ask again. A grant that is not taken in
 * the next tick lapses.
 */
public final class RoundRobinBudget {
    private final TreeSet<String> requests = new TreeSet<>();
    private final Set<UUID> granted = new HashSet<>();
    private String lastServed;
    private int servedLastTick;
    private int waitingLastTick;

    public void request(UUID device) {
        requests.add(Objects.requireNonNull(device, "device").toString());
    }

    /** Consumes this device's grant for the current tick. */
    public boolean take(UUID device) {
        if (granted.remove(device)) {
            servedLastTick++;
            return true;
        }
        return false;
    }

    /** Grants at most {@code cap} of this tick's requests for the next tick and forgets the rest. */
    public List<UUID> endTick(int cap) {
        if (cap < 0) {
            throw new IllegalArgumentException("A cap is not negative");
        }
        granted.clear();
        List<String> order = new ArrayList<>(requests.size());
        if (lastServed == null) {
            order.addAll(requests);
        } else {
            order.addAll(requests.tailSet(lastServed, false));
            order.addAll(requests.headSet(lastServed, true));
        }
        List<UUID> chosen = new ArrayList<>(Math.min(cap, order.size()));
        for (String id : order) {
            if (chosen.size() == cap) {
                break;
            }
            chosen.add(UUID.fromString(id));
        }
        if (!chosen.isEmpty()) {
            lastServed = chosen.get(chosen.size() - 1).toString();
        }
        granted.addAll(chosen);
        waitingLastTick = order.size() - chosen.size();
        requests.clear();
        return chosen;
    }

    /** Grants taken in the tick that just ended; reset by {@link #resetCounters()}. */
    public int servedLastTick() {
        return servedLastTick;
    }

    public int waitingLastTick() {
        return waitingLastTick;
    }

    public void resetCounters() {
        servedLastTick = 0;
    }

    public void clear() {
        requests.clear();
        granted.clear();
        lastServed = null;
        servedLastTick = 0;
        waitingLastTick = 0;
    }
}
