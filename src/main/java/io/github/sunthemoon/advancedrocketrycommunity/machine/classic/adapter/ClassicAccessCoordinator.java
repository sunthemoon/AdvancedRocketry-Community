package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Private entry points that derive authority afresh from the actual Level-owned service. */
final class ClassicAccessCoordinator {
    private final ClassicFamilyService service;

    ClassicAccessCoordinator(ClassicFamilyService service) { this.service = Objects.requireNonNull(service, "service"); }

    Optional<GuardTicket> enterCapability(ClassicHatchBlockEntity hatch) {
        if (!service.running() || hatch.view() == null || hatch.view().binding().isEmpty()) { return Optional.empty(); }
        var binding = hatch.view().binding().orElseThrow();
        if (binding.controllerLevel() != service.level().dimension()) { return Optional.empty(); }
        var owner = service.world().controller(binding.controllerPosition()).orElse(null);
        if (owner == null || !owner.bindingMatches(hatch)) { return Optional.empty(); }
        var targets = assignedHatches(owner);
        if (targets.isEmpty() || !targets.orElseThrow().contains(hatch)) { return Optional.empty(); }
        return GuardTicket.acquire(service, owner, targets.orElseThrow(), ClassicTicketPurpose.CAPABILITY);
    }

    Optional<GuardTicket> enterLifecycle(ClassicControllerBlockEntity owner, List<ClassicHatchBlockEntity> targets) {
        return GuardTicket.acquire(service, owner, List.copyOf(targets), ClassicTicketPurpose.LIFECYCLE);
    }

    Optional<GuardTicket> enterLoad(ClassicControllerBlockEntity owner) {
        return GuardTicket.acquire(service, owner, List.of(), ClassicTicketPurpose.LOAD);
    }

    Optional<GuardTicket> enterLoad(ClassicHatchBlockEntity hatch) {
        return GuardTicket.acquire(service, null, List.of(hatch), ClassicTicketPurpose.LOAD);
    }

    Optional<GuardTicket> enterCompletion(ClassicControllerBlockEntity owner) {
        return transaction(owner, ClassicTicketPurpose.COMPLETION);
    }

    Optional<GuardTicket> enterRecovery(ClassicControllerBlockEntity owner) {
        return transaction(owner, ClassicTicketPurpose.RECOVERY);
    }

    private Optional<GuardTicket> transaction(ClassicControllerBlockEntity owner, ClassicTicketPurpose purpose) {
        if (!service.running() || owner.frame() == null) { return Optional.empty(); }
        var machine = owner.frame().machine();
        if (machine.dropQuarantine().isPresent() || !(machine.process() instanceof ClassicProcessFrame.Work work)
                || work.progressTicks() != work.durationTicks()) { return Optional.empty(); }
        if (purpose == ClassicTicketPurpose.COMPLETION && (machine.nativePlan().isPresent() || owner.frame().journal().isPresent())) {
            return Optional.empty();
        }
        if (purpose == ClassicTicketPurpose.RECOVERY && (machine.nativePlan().isEmpty() || owner.frame().journal().isEmpty())) {
            return Optional.empty();
        }
        var targets = assignedHatches(owner);
        if (targets.isEmpty()) { return Optional.empty(); }
        var acquired = GuardTicket.acquire(service, owner, targets.orElseThrow(), purpose);
        if (acquired.isPresent()) {
            GuardTicket ticket = acquired.orElseThrow();
            try { ClassicCheckpointChecks.validate(owner.frame(), ticket); ticket.requireValid(); }
            catch (RuntimeException | Error failure) { ticket.close(); throw failure; }
        }
        return acquired;
    }

    private Optional<List<ClassicHatchBlockEntity>> assignedHatches(ClassicControllerBlockEntity owner) {
        ClassicControllerFrame frame = owner.frame();
        if (frame == null) { return Optional.empty(); }
        var targets = new ArrayList<ClassicHatchBlockEntity>();
        for (ClassicAssignment assignment : frame.machine().assignments()) {
            var hatch = service.world().hatch(assignment.position()).orElse(null);
            if (hatch == null || !owner.bindingMatches(hatch)) { return Optional.empty(); }
            targets.add(hatch);
        }
        return owner.frame() == frame ? Optional.of(List.copyOf(targets)) : Optional.empty();
    }

    Optional<ClassicRawPermit> enterCapture(ClassicControllerBlockEntity owner) {
        return ClassicRawPermit.acquire(owner.ownerState(), ClassicRawPurpose.CAPTURE);
    }

    Optional<ClassicRawPermit> enterCapture(ClassicHatchBlockEntity hatch) {
        return ClassicRawPermit.acquire(hatch.ownerState(), ClassicRawPurpose.CAPTURE);
    }

    ClassicRawPermit enterEmission(ClassicControllerBlockEntity owner) { return ClassicRawPermit.emission(owner.ownerState()); }
    ClassicRawPermit enterEmission(ClassicHatchBlockEntity hatch) { return ClassicRawPermit.emission(hatch.ownerState()); }

    void retire(ClassicControllerBlockEntity owner) { owner.retireAccess(); }
    void retire(ClassicHatchBlockEntity hatch) { hatch.retireAccess(); }
}
