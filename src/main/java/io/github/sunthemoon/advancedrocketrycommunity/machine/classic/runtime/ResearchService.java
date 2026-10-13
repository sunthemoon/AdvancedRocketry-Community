package io.github.sunthemoon.advancedrocketrycommunity.machine.classic.runtime;

import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentQueries;
import io.github.sunthemoon.advancedrocketrycommunity.machine.research.ResearchPatterns;
import io.github.sunthemoon.advancedrocketrycommunity.api.environment.EnvironmentSnapshot;
import io.github.sunthemoon.advancedrocketrycommunity.celestial.CelestialIds;
import io.github.sunthemoon.advancedrocketrycommunity.config.CommonConfig;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.mission.GroundSurveyJob.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.model.SatelliteLimits;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.persistence.SatelliteMissionSavedData;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.resource.*;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.ResourceMissionRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.satellite.service.SatelliteRuntime;
import io.github.sunthemoon.advancedrocketrycommunity.station.persistence.StationRegistrySavedData;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessAction;
import io.github.sunthemoon.advancedrocketrycommunity.station.service.StationAccessService;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;

/** Overworld-capability-owned authority adapter and shared server work budget. */
public final class ResearchService {
    public static final int SURVEY_TICKS = 1_000, ANALYSIS_TICKS = 200, ENERGY_PER_TICK = 20, CREDIT = 120;
    private final MinecraftServer server;
    private final ResearchDeviceDirectory directory = new ResearchDeviceDirectory();
    private final ResearchWorkQueue<ResourceLocation, ResearchControllerBlockEntity> queue = new ResearchWorkQueue<>();
    private final MissionSeeds seeds = new MissionSeeds();
    private final java.util.Map<UUID, Integer> intents = new java.util.HashMap<>();
    private EnvironmentQueries environments;
    private boolean closed;
    private final boolean verificationPaused = Boolean.getBoolean("advancedrocketrycommunity.releaseTestHooks")
            && Boolean.getBoolean("advancedrocketrycommunity.researchVerificationPaused");
    ResearchService(ServerLevel level) { server = level.getServer(); }
    void environments(EnvironmentQueries queries) { environments = queries; }
    boolean allowIntent(UUID player) {
        int tick = server.getTickCount();
        if (intents.containsKey(player) && tick - intents.get(player) < 2
                || !intents.containsKey(player) && intents.size() >= 4_096) { return false; }
        intents.put(player, tick); return true;
    }
    void logout(UUID player) { intents.remove(player); }
    private SatelliteMissionSavedData data() { return SatelliteMissionSavedData.get(server); }
    private long now() { return server.overworld().getGameTime(); }
    /** O(1) unless the ground section changed since the last rebuild. A blocked registry retains nothing. */
    private void refreshDirectory() {
        var data = data();
        if (!data.operational()) { directory.refresh(data, -1, List::of); return; }
        data.groundSurveyQuery(jobs -> directory.refresh(jobs, jobs.version(), jobs::all));
    }
    void observe(ResearchControllerBlockEntity controller) {
        if (closed || !server.isSameThread() || controller.identity().isEmpty()) { return; }
        refreshDirectory(); admit(controller);
    }
    private boolean admit(ResearchControllerBlockEntity controller) {
        var result = directory.observe(controller.identity().orElseThrow(), controller.device(), controller.processor(), controller);
        if (result == ResearchDeviceDirectory.Admission.COLLISION) { controller.quarantineCopy(); return false; }
        if (result == ResearchDeviceDirectory.Admission.CAPACITY) { controller.status(ResearchStatus.CAPACITY_REACHED); return false; }
        return true;
    }
    void forget(ResearchControllerBlockEntity controller) {
        if (controller.identity().isPresent() && controller.getLevel() != null) {
            directory.forget(controller.device(), controller); queue.remove(controller.device().level(), controller);
        }
    }
    void unload(ServerLevel level) { queue.unload(level.dimension().location()); directory.unload(level.dimension().location()); }
    void enqueue(ResearchControllerBlockEntity controller) {
        if (verificationPaused) { return; }
        if (closed || controller.identity().isEmpty() || !server.isSameThread()) { return; }
        if (controller.identity().orElseThrow().collision()) { controller.status(ResearchStatus.DEVICE_COLLISION); return; }
        if (controller.repairRequired() || controller.debitShortfall()) { controller.status(ResearchStatus.OFFLINE_REPAIR_REQUIRED); return; }
        if (!data().operational()) { controller.status(ResearchStatus.REGISTRY_BLOCKED); return; }
        if (!CommonConfig.groundSurveysEnabled()) { controller.status(ResearchStatus.DISABLED); return; }
        if (!controller.formed()) { controller.status(controller.reason() == ClassicWait.UNLOADED ? ResearchStatus.UNLOADED : ResearchStatus.UNFORMED); return; }
        // Every loaded controller runs this each tick: an idle one costs one lookup, and its menu computes the rest.
        refreshDirectory();
        if (!directory.working(controller.device())) { controller.idle(server.getTickCount()); return; }
        if (queue.enqueue(controller.device().level(), controller)) { controller.status(ResearchStatus.QUEUED); }
        else { controller.status(ResearchStatus.CAPACITY_REACHED); }
    }
    void tick() {
        if (verificationPaused) { return; }
        if (closed || !server.isSameThread()) { return; }
        refreshDirectory(); queue.tick(this::advance);
    }
    public List<GroundSurveyJob> choices(ResearchControllerBlockEntity controller) {
        if (!data().operational() || controller.researchOwner().isEmpty()) { return List.of(); }
        return data().groundSurveyQuery(jobs -> jobs.owned(controller.researchOwner().orElseThrow())).stream()
                .filter(job -> controller.processor() ? job.phase() == Phase.READY
                        && job.analysis().state() != AnalysisState.CREDITED : job.phase() == Phase.ACTIVE)
                .sorted(Comparator.comparing(job -> job.jobId().toString())).toList();
    }
    public Optional<GroundSurveyJob> work(ResearchControllerBlockEntity controller) {
        if (!data().operational() || controller.identity().isEmpty() || controller.researchOwner().isEmpty()) { return Optional.empty(); }
        return data().groundSurveyQuery(jobs -> jobs.owned(controller.researchOwner().orElseThrow())).stream()
                .filter(job -> controller.processor() ? job.phase() == Phase.READY && job.analysis().state() == AnalysisState.RUNNING
                        && job.analysis().claim().orElseThrow().processor().equals(controller.device())
                        : job.phase() == Phase.ACTIVE && job.source().equals(controller.device())).findFirst();
    }
    public Optional<GroundSurveyJob> selected(ResearchControllerBlockEntity controller) {
        var choices = choices(controller);
        var selected = choices.stream().filter(job -> job.jobId().equals(controller.selection())).findFirst();
        if (selected.isEmpty() && !choices.isEmpty()) { selected = Optional.of(choices.get(0)); controller.selection(choices.get(0).jobId()); }
        return selected;
    }
    public Optional<GroundSurveyJob> displayed(ResearchControllerBlockEntity controller) {
        var work = work(controller); if (work.isPresent()) { return work; }
        var selected = selected(controller); if (selected.isPresent()) { return selected; }
        if (!data().operational() || controller.researchOwner().isEmpty() || controller.identity().isEmpty()) { return Optional.empty(); }
        return data().groundSurveyQuery(jobs -> jobs.owned(controller.researchOwner().orElseThrow())).stream()
                .filter(job -> controller.processor() ? job.analysis().claim().map(claim -> claim.processor().equals(controller.device())).orElse(false)
                        : job.source().equals(controller.device())).max(Comparator.comparingLong(GroundSurveyJob::createdAt)
                                .thenComparing(job -> job.jobId().toString()));
    }
    /** The menu's status: an idle controller's status is computed here, for an open menu only, never per tick. */
    ResearchStatus shownStatus(ResearchControllerBlockEntity controller, GroundSurveyJob job) {
        ResearchStatus status = controller.researchStatus();
        if (status != ResearchStatus.IDLE || job == null) { return status; }
        if (job.phase() == Phase.QUARANTINED) { return quarantined(job); }
        if (job.phase() == Phase.PROCESSED || !controller.processor() && job.phase() == Phase.READY) { return ResearchStatus.COMPLETE; }
        // W21a design section 4: a job whose device was broken stays and pauses until its owner rebinds it.
        // An unloaded device looks the same from here, and rebinding is the owner's remedy for both.
        Device device = job.phase() == Phase.ACTIVE ? job.source() : job.analysis().state() == AnalysisState.RUNNING
                ? job.analysis().claim().orElseThrow().processor() : null;
        return device != null && !device.equals(controller.device()) && !directory.loaded(device)
                ? ResearchStatus.SOURCE_MISSING : ResearchStatus.IDLE;
    }
    private ResearchStatus basic(ResearchControllerBlockEntity controller, boolean enabled) {
        if (!controller.physical()) { return ResearchStatus.UNLOADED; }
        if (!data().operational()) { return ResearchStatus.REGISTRY_BLOCKED; }
        if (controller.identity().isEmpty()) { return ResearchStatus.OFFLINE_REPAIR_REQUIRED; }
        if (!admit(controller)) { return controller.researchStatus(); }
        if (controller.repairRequired() || controller.debitShortfall()) { return ResearchStatus.OFFLINE_REPAIR_REQUIRED; }
        if (enabled && !CommonConfig.groundSurveysEnabled()) { return ResearchStatus.DISABLED; }
        if (!controller.formed()) { return controller.reason() == ClassicWait.UNLOADED ? ResearchStatus.UNLOADED : ResearchStatus.UNFORMED; }
        var level = (ServerLevel) controller.getLevel();
        // Pattern footprint includes ordinary blocks and clearance, not only bound plugs.
        for (BlockPos cell : controller.footprint(ResearchPatterns.definition(controller.processor()))) {
            if (level.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4) == null) { return ResearchStatus.UNLOADED; }
        }
        return null;
    }
    public Optional<ResourceLocation> system(ResearchControllerBlockEntity controller) {
        if (controller.getLevel() == null || controller.researchOwner().isEmpty()) { return Optional.empty(); }
        ResourceLocation body;
        var level = (ServerLevel) controller.getLevel();
        if (level.dimension().equals(CelestialIds.SPACE_LEVEL)) {
            var stations = StationRegistrySavedData.get(server);
            var station = stations.findAt(controller.getBlockPos().getX(), controller.getBlockPos().getZ());
            if (station.isEmpty() || !new StationAccessService().allowed(station.orElseThrow(), controller.researchOwner().orElseThrow(),
                    false, StationAccessAction.VISIT)) { return Optional.empty(); }
            body = station.orElseThrow().orbitBody();
        } else {
            var environment = environments == null ? Optional.<EnvironmentSnapshot>empty() : environments.at(level.dimension(), controller.getBlockPos());
            if (environment.isEmpty() || environment.orElseThrow().locus() != EnvironmentSnapshot.Locus.SURFACE) { return Optional.empty(); }
            body = environment.orElseThrow().bodyId();
        }
        return SatelliteRuntime.celestialCatalog().flatMap(catalog -> catalog.systemOf(body));
    }
    ResearchStatus observing(ResearchControllerBlockEntity controller, GroundSurveyJob job) {
        var system = system(controller);
        if (system.isEmpty()) { return ResearchStatus.BODY_UNAVAILABLE; }
        if (!system.orElseThrow().equals(job.snapshot().system())) { return ResearchStatus.SYSTEM_CHANGED; }
        var level = (ServerLevel) controller.getLevel();
        if (level.dimension().equals(CelestialIds.SPACE_LEVEL)) { return null; }
        var local = controller.controllerState().selectedTransform().applyOffset(
                new io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.pattern.PatternPosition(0, 3, 1));
        BlockPos lens = controller.getBlockPos().offset(local.x(), local.y(), local.z());
        if (!level.canSeeSky(lens)) { return ResearchStatus.NO_SKY; }
        if (level.getSkyDarken() < 4) { return ResearchStatus.DAYTIME; }
        if (level.isRaining() && level.getBiome(lens).value().getPrecipitationAt(lens) != Biome.Precipitation.NONE) { return ResearchStatus.WEATHER; }
        return null;
    }
    boolean advance(ResearchControllerBlockEntity controller) {
        ResearchStatus refusal = basic(controller, true);
        if (refusal != null) { controller.status(refusal); return false; }
        if (!((ServerLevel) controller.getLevel()).isPositionEntityTicking(controller.getBlockPos())) {
            controller.status(ResearchStatus.UNLOADED); return false;
        }
        var job = work(controller).orElse(null);
        if (job == null) { controller.idle(server.getTickCount()); return false; }
        if (!controller.processor()) {
            refusal = observing(controller, job);
            if (refusal != null) { controller.status(refusal); return false; }
        }
        if (data().saveEpoch() <= epoch(controller, job)) { controller.status(ResearchStatus.WAITING_SAVE); return false; }
        int cost = controller.processor() ? job.analysis().energy() : job.surveyEnergy();
        if (controller.storedEnergy() < cost) { controller.status(ResearchStatus.NO_POWER); return false; }
        return controller.paidResearchAdvance(cost, () -> {
            var result = data().groundSurveys(jobs -> controller.processor()
                    ? jobs.advanceAnalysis(job.jobId(), controller.device(), now())
                    : jobs.advanceSurvey(job.jobId(), controller.device(), CommonConfig.asteroidInstanceTtlTicks(), now()));
            controller.status(status(controller, result));
            return result.changed();
        });
    }
    boolean intent(ResearchControllerBlockEntity controller, UUID owner, int button) {
        if (!server.isSameThread() || controller.researchOwner().filter(owner::equals).isEmpty()) { answer(controller, ResearchStatus.UNAUTHORIZED); return false; }
        refreshDirectory();
        if (button == 2 || button == 3) {
            var choices = choices(controller); if (choices.isEmpty()) { return false; }
            int index = choices.indexOf(selected(controller).orElse(null));
            controller.selection(choices.get(Math.floorMod(index + (button == 2 ? -1 : 1), choices.size())).jobId()); return true;
        }
        if (button == 1) {
            if (!data().operational() || controller.identity().isEmpty() || !admit(controller)) { return false; }
            var job = selected(controller).orElse(null);
            if (job == null) { answer(controller, ResearchStatus.JOB_NOT_FOUND); return false; }
            return result(controller, data().groundSurveys(jobs -> jobs.cancel(job.jobId(), owner, false, now())), ResearchStatus.CANCELLED);
        }
        var refusal = basic(controller, true);
        if (refusal != null) { answer(controller, refusal); return false; }
        if (button == 0 && !controller.processor()) { return start(controller, owner); }
        var job = selected(controller).orElse(null);
        if (job == null) { answer(controller, ResearchStatus.JOB_NOT_FOUND); return false; }
        if (button == 4 && controller.processor()) {
            return result(controller, data().groundSurveys(jobs -> jobs.claim(job.jobId(), owner, controller.device(), now())), ResearchStatus.WAITING_SAVE);
        }
        if (button == 5) {
            return result(controller, data().groundSurveys(jobs -> controller.processor()
                    ? jobs.rebindAnalysis(job.jobId(), owner, controller.device(), now())
                    : jobs.rebindSurvey(job.jobId(), owner, controller.device(), now())), ResearchStatus.WAITING_SAVE);
        }
        return false;
    }
    private boolean start(ResearchControllerBlockEntity controller, UUID owner) {
        ResourceLocation system = system(controller).orElse(null);
        if (system == null) { answer(controller, ResearchStatus.BODY_UNAVAILABLE); return false; }
        var tables = ResourceMissionRuntime.service().flatMap(service -> service.resourceTables()).orElse(null);
        var candidates = tables == null ? List.<AsteroidType>of() : ResourceAlgorithms.candidates(tables.asteroidTypes(), system);
        if (candidates.isEmpty()) { answer(controller, ResearchStatus.CATALOG_UNAVAILABLE); return false; }
        UUID id = UUID.randomUUID(); var generated = ResourceAlgorithms.survey(seeds.next(), candidates, 1).get(0);
        long createdAt = data().groundSurveyQuery(jobs -> jobs.previewTime(now()));
        var instance = new AsteroidInstance(SatelliteLimits.INSTANCE_SCHEMA_VERSION, UUID.randomUUID(), owner, system,
                generated.type().id(), generated.type().tableVersion(), ResourceAlgorithms.fingerprint(candidates), generated.seed(),
                generated.yield(), createdAt, OptionalLong.empty(), InstanceState.PENDING, InstanceOrigin.ground(id), Optional.empty());
        var request = new GroundSurveys.Start(id, owner, controller.device(), instance, SURVEY_TICKS, ENERGY_PER_TICK,
                ANALYSIS_TICKS, ENERGY_PER_TICK, CREDIT);
        return result(controller, data().groundSurveys(jobs -> jobs.start(request, now())), ResearchStatus.WAITING_SAVE);
    }
    private void answer(ResearchControllerBlockEntity controller, ResearchStatus status) { controller.answer(status, server.getTickCount()); }
    private boolean result(ResearchControllerBlockEntity controller, GroundSurveys.Result result, ResearchStatus success) {
        answer(controller, result.code() == GroundSurveys.Code.SUCCESS ? success : status(controller, result));
        result.job().ifPresent(job -> controller.selection(job.jobId())); return result.changed() || result.code() == GroundSurveys.Code.IDEMPOTENT;
    }
    private static long epoch(ResearchControllerBlockEntity controller, GroundSurveyJob job) {
        return controller.processor() ? job.analysis().claim().map(Claim::epoch).orElse(Long.MAX_VALUE) : job.startEpoch();
    }
    private ResearchStatus quarantined(GroundSurveyJob job) {
        String hint = data().groundSurveyQuery(jobs -> jobs.recoveryHint(job.jobId())); return quarantined(hint);
    }
    /** R4: only the two offline-repair cases read as offline repair; an operator can release any other quarantine. */
    static ResearchStatus quarantined(String recoveryHint) {
        return recoveryHint == null ? ResearchStatus.QUARANTINED : ResearchStatus.OFFLINE_REPAIR_REQUIRED;
    }
    /** A registry answer about one job: a quarantine by its recovery boundary, a repeated intent by the job's state. */
    private ResearchStatus status(ResearchControllerBlockEntity controller, GroundSurveys.Result result) {
        var job = result.job().orElse(null);
        if (job != null && result.code() == GroundSurveys.Code.QUARANTINED) { return quarantined(job); }
        if (job != null && result.code() == GroundSurveys.Code.IDEMPOTENT) {
            return data().saveEpoch() <= epoch(controller, job) ? ResearchStatus.WAITING_SAVE : ResearchStatus.QUEUED;
        }
        return status(result.code());
    }
    /** Exhaustive, so a new registry code cannot fall through to "Job not ready". */
    static ResearchStatus status(GroundSurveys.Code code) {
        return switch (code) {
            case SUCCESS -> ResearchStatus.RUNNING;
            case IDEMPOTENT -> ResearchStatus.QUEUED;
            case JOB_NOT_FOUND -> ResearchStatus.JOB_NOT_FOUND;
            case UNAUTHORIZED -> ResearchStatus.UNAUTHORIZED;
            case IDENTITY_CONFLICT -> ResearchStatus.IDENTITY_CONFLICT;
            case DEVICE_BUSY -> ResearchStatus.DEVICE_BUSY;
            case PROCESSOR_BUSY -> ResearchStatus.PROCESSOR_BUSY;
            case OWNER_LIMIT -> ResearchStatus.OWNER_LIMIT;
            case CAPACITY_REACHED -> ResearchStatus.CAPACITY_REACHED;
            case STORAGE_BUDGET -> ResearchStatus.STORAGE_BUDGET;
            case WAITING_SAVE -> ResearchStatus.WAITING_SAVE;
            case NOT_READY -> ResearchStatus.NOT_READY;
            case ALREADY_CLAIMED -> ResearchStatus.ALREADY_CLAIMED;
            case CANCELLED -> ResearchStatus.CANCELLED;
            case QUARANTINED -> ResearchStatus.QUARANTINED;
            case SOURCE_MISMATCH -> ResearchStatus.SOURCE_MISMATCH;
            case ACCOUNT_FULL -> ResearchStatus.ACCOUNT_FULL;
            // The touched job or one of its peers breaks the load plan's rules, which quarantine it at the next load.
            case OFFLINE_REPAIR_REQUIRED, INVARIANT_FAILED -> ResearchStatus.OFFLINE_REPAIR_REQUIRED;
        };
    }
    void close() { closed = true; environments = null; queue.clear(); directory.clear(); intents.clear(); }
}
