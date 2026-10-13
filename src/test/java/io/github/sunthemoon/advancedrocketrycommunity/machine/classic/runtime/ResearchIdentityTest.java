package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import static org.junit.jupiter.api.Assertions.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.*;
import io.github.sunthemoon.advancedrocketrycommunity.testsupport.MinecraftBootstrap;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ResearchIdentityTest {
    @BeforeAll static void bootstrap() { MinecraftBootstrap.initialize(); }
    private final UUID owner = UUID.randomUUID(), id = UUID.randomUUID();
    private final ResourceLocation earth = ResourceLocation.tryParse("minecraft:overworld"), moon = ResourceLocation.tryParse("advancedrocketrycommunity:moon");
    private ResearchIdentity identity() { return new ResearchIdentity(id, Optional.of(owner), earth, 10, false); }
    @Test void idleCopiesRemainRejectedAcrossLevelsAndUnloadReload() {
        var directory = new ResearchDeviceDirectory(); var identity = identity(); Object original = new Object(), copy = new Object();
        assertEquals(ResearchDeviceDirectory.Admission.ACCEPTED, directory.observe(identity, identity.home(), false, original));
        var other = new Device(id, moon, 10);
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(identity, other, false, copy));
        assertEquals(2, directory.loadedCount()); directory.forget(identity.home(), original); directory.unload(moon);
        assertEquals(0, directory.loadedCount());
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(identity, other, false, copy));
        assertEquals(ResearchDeviceDirectory.Admission.ACCEPTED, directory.observe(identity, identity.home(), false, original));
        assertEquals(identity.quarantined(), ResearchIdentity.decode(identity.quarantined().encode()));
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(identity.quarantined(), identity.home(), false, original));
    }
    @Test void readyAndProcessedJobsAndCreditedClaimsPinTheAuthority() {
        var directory = new ResearchDeviceDirectory(); var identity = identity(); GroundSurveyJob job = job(identity.home());
        for (int tick = 0; tick < 20; tick++) { job = job.advanced(tick + 1); }
        assertEquals(Phase.READY, job.phase()); directory.retained(List.of(job));
        var alteredHome = new ResearchIdentity(id, Optional.of(owner), moon, 30, false);
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(alteredHome, alteredHome.home(), false, new Object()));
        var processor = new Device(UUID.randomUUID(), earth, 50);
        job = job.analyzed(new Analysis(AnalysisState.CREDITED, 1, 20, 120, 1, Optional.of(new Claim(2, processor))), 30);
        assertEquals(Phase.PROCESSED, job.phase()); directory.retained(List.of(job));
        var processorIdentity = new ResearchIdentity(processor.id(), Optional.of(owner), earth, 50, false);
        assertEquals(ResearchDeviceDirectory.Admission.ACCEPTED, directory.observe(processorIdentity, processor, true, new Object()));
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(processorIdentity, new Device(processor.id(), moon, 60), true, new Object()));
        assertEquals(ResearchDeviceDirectory.Admission.COLLISION, directory.observe(identity, identity.home(), true, new Object()));
        assertEquals(ResearchDeviceDirectory.Admission.ACCEPTED, directory.observe(identity, identity.home(), false, new Object()));
    }
    @Test void unknownOrOversizedRootIsNotNormalized() {
        var root = identity().encode(); assertEquals(identity(), ResearchIdentity.decode(root));
        root.putInt("schema_version", 2); assertThrows(IllegalArgumentException.class, () -> ResearchIdentity.decode(root));
        root.putInt("schema_version", 1); root.putString("extra", "value"); assertThrows(IllegalArgumentException.class, () -> ResearchIdentity.decode(root));
        var large = new CompoundTag(); large.putString("data", "x".repeat(4_096)); assertFalse(ResearchIdentity.bounded(large));
        assertThrows(IllegalArgumentException.class, () -> ResearchIdentity.decode(large));
    }
    @Test void directoryCapacityDoesNotEvictAnExistingIdentity() {
        var directory = new ResearchDeviceDirectory();
        for (int i = 0; i < ResearchDeviceDirectory.MAX_LOADED; i++) {
            var identity = new ResearchIdentity(new UUID(0, i), Optional.of(owner), earth, i, false);
            assertEquals(ResearchDeviceDirectory.Admission.ACCEPTED, directory.observe(identity, identity.home(), false, new Object()));
        }
        assertEquals(ResearchDeviceDirectory.Admission.CAPACITY, directory.observe(identity(), identity().home(), false, new Object()));
        assertEquals(ResearchDeviceDirectory.MAX_LOADED, directory.loadedCount()); directory.clear(); assertEquals(0, directory.loadedCount());
    }
    private GroundSurveyJob job(Device source) {
        var snapshot = new Snapshot(earth, earth, "0123456789abcdef", "0123456789abcdef", 1, "0123456789abcdef");
        return new GroundSurveyJob(1, UUID.randomUUID(), owner, UUID.randomUUID(), source, snapshot, 20, 20, 0, 0, 1,
                Phase.ACTIVE, OptionalLong.empty(), OptionalLong.empty(), new Analysis(AnalysisState.WAITING, 1, 20, 120, 0, Optional.empty()), Optional.empty());
    }
}
