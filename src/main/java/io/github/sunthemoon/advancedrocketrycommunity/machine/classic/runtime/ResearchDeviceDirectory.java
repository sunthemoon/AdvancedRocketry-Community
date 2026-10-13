package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.Device;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

/** Server-owned loaded identities, independent of ACTIVE/RUNNING indexes. No eviction admits a copy. */
public final class ResearchDeviceDirectory {
    public static final int MAX_LOADED = 16_384;
    public enum Admission { ACCEPTED, COLLISION, CAPACITY }
    private record Home(Device device, UUID owner, boolean processor) { }
    private record Loaded(Home home, Object token, boolean canonical) { }
    private final Map<Device, Loaded> loaded = new HashMap<>();
    private final Map<UUID, Home> homes = new HashMap<>(), retained = new HashMap<>();
    private final Set<UUID> ambiguous = new HashSet<>();
    private final Map<UUID, Integer> canonicalCounts = new HashMap<>();
    private final Set<Device> working = new HashSet<>();
    private Object retainedSection;
    private long retainedVersion;

    /**
     * Rebuilds the retained authorities and the work index only when the ground section changed: another section
     * object (a reloaded or blocked registry) or another change count. Returns whether it rebuilt.
     */
    public boolean refresh(Object section, long version, Supplier<List<GroundSurveyJob>> jobs) {
        if (section == retainedSection && version == retainedVersion) { return false; }
        retained(jobs.get()); retainedSection = section; retainedVersion = version; return true;
    }

    /** Every retained phase and credited claim participates, not only live-work indexes. */
    public void retained(List<GroundSurveyJob> jobs) {
        if (jobs.size() > io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits.MAX_GROUND_SURVEYS) {
            throw new IllegalArgumentException("Retained research directory bound");
        }
        retained.clear(); ambiguous.clear(); working.clear(); retainedSection = null;
        for (var job : jobs) {
            remember(new Home(job.source(), job.ownerId(), false));
            if (job.phase() == GroundSurveyJob.Phase.ACTIVE) { working.add(job.source()); }
            job.analysis().claim().ifPresent(claim -> {
                remember(new Home(claim.processor(), job.ownerId(), true));
                if (job.phase() == GroundSurveyJob.Phase.READY && job.analysis().state() == GroundSurveyJob.AnalysisState.RUNNING) {
                    working.add(claim.processor());
                }
            });
        }
    }
    /** Whether the device can hold ACTIVE or RUNNING work. A false answer needs no registry query. */
    public boolean working(Device device) { return working.contains(device); }
    private void remember(Home home) {
        Home previous = retained.putIfAbsent(home.device().id(), home);
        if (previous != null && !previous.equals(home)) { ambiguous.add(home.device().id()); }
    }
    public Admission observe(ResearchIdentity identity, Device actual, boolean processor, Object token) {
        if (!loaded.containsKey(actual) && loaded.size() >= MAX_LOADED) { return Admission.CAPACITY; }
        var home = new Home(identity.home(), identity.owner().orElse(null), processor);
        Home authority = retained.getOrDefault(actual.id(), homes.get(actual.id()));
        boolean canonical = !identity.collision() && identity.home().equals(actual)
                && !ambiguous.contains(actual.id()) && (authority == null || authority.equals(home));
        Loaded previous = loaded.put(actual, new Loaded(home, token, canonical));
        if (previous != null && previous.canonical()) { removeCanonical(actual.id()); }
        if (canonical) { canonicalCounts.merge(actual.id(), 1, Integer::sum); }
        if (canonical) { homes.putIfAbsent(actual.id(), home); }
        return canonical ? Admission.ACCEPTED : Admission.COLLISION;
    }
    public void forget(Device actual, Object token) {
        Loaded entry = loaded.get(actual);
        if (entry == null || entry.token() != token) { return; }
        loaded.remove(actual);
        if (entry.canonical()) { removeCanonical(actual.id()); }
    }
    private void removeCanonical(UUID id) {
        int count = canonicalCounts.getOrDefault(id, 0) - 1;
        if (count <= 0) { canonicalCounts.remove(id); homes.remove(id); } else { canonicalCounts.put(id, count); }
    }
    public void unload(net.minecraft.resources.ResourceLocation level) {
        var remove = loaded.entrySet().stream().filter(entry -> entry.getKey().level().equals(level)).toList();
        for (var entry : remove) { forget(entry.getKey(), entry.getValue().token()); }
    }
    /** Whether this exact device is loaded and canonical; an unloaded or broken device is not. */
    public boolean loaded(Device device) { var entry = loaded.get(device); return entry != null && entry.canonical(); }
    public int loadedCount() { return loaded.size(); }
    public void clear() {
        loaded.clear(); homes.clear(); retained.clear(); ambiguous.clear(); canonicalCounts.clear(); working.clear(); retainedSection = null;
    }
}
