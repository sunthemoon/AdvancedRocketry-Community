package io.github.sunthemoon.advancedrocketrycommunity.machine.rolling;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.persistence.MultiblockNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailure;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessFailureCode;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessJournalStore;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineLogic;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessMachineState;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessProgress;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessResourceAvailability;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessSimulationResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTickInput;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTickResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionExecutor;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionJournal;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.ProcessTransactionStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Process state owner that keeps the controller BlockEntity as a lifecycle adapter. */
final class RollingMachineProcessController implements ProcessJournalStore {
    private final Runnable changed;
    private final RollingMachineRecipeResolver recipes = new RollingMachineRecipeResolver();

    private ProcessMachineState state = ProcessMachineState.IDLE;
    private ProcessFailure failure = ProcessFailure.NONE;
    private long resourceRevision;
    private Optional<UUID> lastAppliedTransactionId = Optional.empty();
    @Nullable
    private ProcessProgress progress;
    @Nullable
    private String recipeSignature;
    @Nullable
    private ProcessTransactionJournal journal;
    @Nullable
    private UUID machineId;
    private MultiblockNbtStatus processPersistenceStatus = MultiblockNbtStatus.SUPPORTED;
    private MultiblockNbtStatus journalPersistenceStatus = MultiblockNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedProcessRoot;
    @Nullable
    private Tag preservedJournalRoot;

    RollingMachineProcessController(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    void initialize(UUID initializedMachineId) {
        if (machineId != null) {
            throw new IllegalStateException("Rolling Machine process owner is already initialized");
        }
        machineId = Objects.requireNonNull(initializedMachineId, "initializedMachineId");
    }

    void load(CompoundTag parent, UUID loadedMachineId) {
        reset(loadedMachineId);
        RollingMachineNbtResult<RollingMachineProcessPersistence.ProcessData> processResult =
                RollingMachineProcessPersistence.decode(parent);
        processPersistenceStatus = normalizeEmpty(processResult.status());
        if (processResult.status() == MultiblockNbtStatus.SUPPORTED) {
            applyLoaded(processResult.value().orElseThrow());
        } else if (processResult.status() != MultiblockNbtStatus.EMPTY) {
            preservedProcessRoot = processResult.preservedRoot().orElseThrow();
            state = processResult.status() == MultiblockNbtStatus.UNSUPPORTED_SCHEMA
                    ? ProcessMachineState.UNSUPPORTED_DATA
                    : ProcessMachineState.RECOVERY_REQUIRED;
        }

        RollingMachineNbtResult<ProcessTransactionJournal> journalResult =
                RollingMachineJournalPersistence.decode(parent, loadedMachineId);
        journalPersistenceStatus = normalizeEmpty(journalResult.status());
        if (journalResult.status() == MultiblockNbtStatus.SUPPORTED) {
            journal = journalResult.value().orElseThrow();
            state = ProcessMachineState.RECOVERY_REQUIRED;
        } else if (journalResult.status() != MultiblockNbtStatus.EMPTY) {
            preservedJournalRoot = journalResult.preservedRoot().orElseThrow();
            state = journalResult.status() == MultiblockNbtStatus.UNSUPPORTED_SCHEMA
                    ? ProcessMachineState.UNSUPPORTED_DATA
                    : ProcessMachineState.RECOVERY_REQUIRED;
        }
    }

    void save(CompoundTag parent) {
        if (preservedProcessRoot != null) {
            parent.put(RollingMachineProcessPersistence.ROOT, preservedProcessRoot.copy());
        } else {
            parent.put(
                    RollingMachineProcessPersistence.ROOT,
                    RollingMachineProcessPersistence.encode(data())
            );
        }
        if (preservedJournalRoot != null) {
            parent.put(RollingMachineJournalPersistence.ROOT, preservedJournalRoot.copy());
        } else if (journal != null) {
            parent.put(
                    RollingMachineJournalPersistence.ROOT,
                    RollingMachineJournalPersistence.encode(journal)
            );
        } else {
            parent.remove(RollingMachineJournalPersistence.ROOT);
        }
    }

    boolean tick(ServerLevel level, RollingMachineBlockEntity controller) {
        if (!acceptsResourceAccess()
                || controller.formationState() != MultiblockFormationState.FORMED) {
            return false;
        }
        Optional<RollingMachinePortSet> resolvedPorts = RollingMachinePortSet.resolve(level, controller);
        if (resolvedPorts.isEmpty()) {
            return false;
        }
        RollingMachinePortSet ports = resolvedPorts.orElseThrow();
        if (journal != null) {
            return recover(level, controller, ports);
        }

        RollingMachineRecipeResolver.Resolution resolution = recipes.resolve(
                level,
                controller.getBlockPos(),
                ports.itemInput().storedItemCopy(),
                progress,
                recipeSignature
        );
        if (resolution.recipe().isEmpty()) {
            updateState(resolution.missingState(), resolution.failure());
            return false;
        }
        RollingMachineRecipe recipe = resolution.recipe().orElseThrow();
        ProcessProgress active = progress == null
                ? ProcessProgress.notStarted(recipe.processDefinition())
                : progress;
        if (!validProgress(recipe, active)) {
            updateState(ProcessMachineState.INVALID_RECIPE, failure(
                    ProcessFailureCode.INVALID_RECIPE,
                    active.definitionId()
            ));
            return false;
        }
        if (progress != null
                && active.progressTicks() == recipe.processDefinition().durationTicks()) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, active.definitionId())
            );
            return false;
        }

        RollingMachineResourceStore resources = new RollingMachineResourceStore(
                level,
                controller,
                this,
                recipe,
                ports
        );
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                resources.snapshot()
        );
        ProcessResourceAvailability availability = availability(simulation);
        if (availability == null) {
            updateState(ProcessMachineState.INVALID_RECIPE, simulation.failure());
            return false;
        }
        ProcessTickResult tick = ProcessMachineLogic.tick(
                recipe.processDefinition(),
                active,
                new ProcessTickInput(
                        true,
                        !level.hasNeighborSignal(controller.getBlockPos()),
                        availability,
                        ports.energyInput().storedEnergy()
                )
        );
        if (tick.energyConsumed() > 0
                && !ports.energyInput().consumeEnergyInternal(Math.toIntExact(tick.energyConsumed()))) {
            updateState(
                    ProcessMachineState.WAITING_ENERGY,
                    failure(ProcessFailureCode.INSUFFICIENT_ENERGY, recipe.getId().toString())
            );
            return false;
        }
        applyTick(recipe, tick);
        if (tick.completionDue()) {
            return complete(controller, recipe, resources);
        }
        return tick.state() == ProcessMachineState.RUNNING;
    }

    boolean locked() {
        return !acceptsResourceAccess() || progress != null || journal != null;
    }

    boolean acceptsResourceAccess() {
        return processPersistenceStatus == MultiblockNbtStatus.SUPPORTED
                && journalPersistenceStatus == MultiblockNbtStatus.SUPPORTED;
    }

    boolean permitsExternalResourceOperations() {
        return acceptsResourceAccess()
                && journal == null
                && state != ProcessMachineState.RECOVERY_REQUIRED
                && state != ProcessMachineState.UNSUPPORTED_DATA;
    }

    void recordExternalMutation(ServerLevel level, RollingMachineBlockEntity controller) {
        if (!acceptsResourceAccess()) {
            return;
        }
        resourceRevision = Math.addExact(resourceRevision, 1);
        recipes.resourceChanged();
        changed.run();
        RollingMachineRuntime.markProcessReady(level, controller.getBlockPos());
    }

    void requireRecoveryAfterUnexpectedTickFailure() {
        updateState(
                ProcessMachineState.RECOVERY_REQUIRED,
                failure(ProcessFailureCode.RECOVERY_DIVERGED, "process_tick")
        );
    }

    ProcessMachineState state() {
        return state;
    }

    ProcessFailure failure() {
        return failure;
    }

    Optional<ProcessProgress> progress() {
        return Optional.ofNullable(progress);
    }

    long resourceRevision() {
        return resourceRevision;
    }

    Optional<UUID> lastAppliedTransactionId() {
        return lastAppliedTransactionId;
    }

    MultiblockNbtStatus processPersistenceStatus() {
        return processPersistenceStatus;
    }

    MultiblockNbtStatus journalPersistenceStatus() {
        return journalPersistenceStatus;
    }

    void replaceResourceRevision(long expected, long replacement) {
        if (resourceRevision != expected || replacement != Math.addExact(expected, 1)) {
            throw new IllegalStateException("Rolling Machine resource revision changed during commit");
        }
        resourceRevision = replacement;
        changed.run();
    }

    void restoreResourceRevision(long revision) {
        if (revision < 0) {
            throw new IllegalArgumentException("resource revision cannot be negative");
        }
        resourceRevision = revision;
        changed.run();
    }

    void markApplied(UUID transactionId) {
        lastAppliedTransactionId = Optional.of(Objects.requireNonNull(transactionId, "transactionId"));
        changed.run();
    }

    @Override
    public Optional<ProcessTransactionJournal> load() {
        return Optional.ofNullable(journal);
    }

    @Override
    public void save(ProcessTransactionJournal replacement) {
        ProcessTransactionJournal checked = Objects.requireNonNull(replacement, "replacement");
        if (!acceptsResourceAccess() || !checked.machineId().equals(requireMachineId())) {
            throw new IllegalStateException("Rolling Machine rejected a journal for another process owner");
        }
        journal = checked;
        changed.run();
    }

    @Override
    public void clear(UUID transactionId) {
        if (journal == null || !journal.transactionId().equals(transactionId)) {
            throw new IllegalStateException("Rolling Machine cannot clear a different journal");
        }
        journal = null;
        changed.run();
    }

    private boolean recover(
            ServerLevel level,
            RollingMachineBlockEntity controller,
            RollingMachinePortSet ports
    ) {
        ProcessTransactionJournal activeJournal = journal;
        ResourceLocation recipeId = ResourceLocation.tryParse(activeJournal.definitionId());
        Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded = recipeId == null
                ? Optional.empty()
                : level.getRecipeManager().byKey(recipeId);
        if (loaded.isEmpty() || !(loaded.orElseThrow() instanceof RollingMachineRecipe recipe)) {
            updateState(
                    ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, activeJournal.definitionId())
            );
            return false;
        }
        ProcessSimulationResult expected = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                activeJournal.before()
        );
        if (expected.plan().isEmpty()
                || !expected.plan().orElseThrow().after().equals(activeJournal.after())
                || (progress != null && (!progress.definitionId().equals(recipe.getId().toString())
                || !recipe.signature().equals(recipeSignature)))) {
            updateState(
                    ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, activeJournal.definitionId())
            );
            return false;
        }
        RollingMachineResourceStore resources = new RollingMachineResourceStore(
                level,
                controller,
                this,
                recipe,
                ports
        );
        ProcessTransactionResult recovered = ProcessTransactionExecutor.recover(resources, this);
        return handleTransactionResult(recovered);
    }

    private boolean complete(
            RollingMachineBlockEntity controller,
            RollingMachineRecipe recipe,
            RollingMachineResourceStore resources
    ) {
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(),
                resources.snapshot()
        );
        if (simulation.plan().isEmpty()) {
            updateState(stateFor(simulation.failure()), simulation.failure());
            return false;
        }
        ProcessTransactionResult result = ProcessTransactionExecutor.commit(
                UUID.randomUUID(),
                controller.controllerState().machineInstanceId(),
                simulation.plan().orElseThrow(),
                resources,
                this
        );
        return handleTransactionResult(result);
    }

    private boolean handleTransactionResult(ProcessTransactionResult result) {
        if (result.failure().code() == ProcessFailureCode.NONE) {
            progress = null;
            recipeSignature = null;
            state = ProcessMachineState.IDLE;
            failure = ProcessFailure.NONE;
            changed.run();
            return true;
        }
        updateState(
                result.status() == ProcessTransactionStatus.STALE_TRANSACTION
                        ? ProcessMachineState.RUNNING
                        : ProcessMachineState.RECOVERY_REQUIRED,
                result.failure()
        );
        return result.status() == ProcessTransactionStatus.STALE_TRANSACTION;
    }

    private void applyTick(RollingMachineRecipe recipe, ProcessTickResult tick) {
        boolean changedState = !Objects.equals(progress, tick.progress())
                || !Objects.equals(recipeSignature, recipe.signature())
                || state != tick.state()
                || !failure.equals(tick.failure());
        progress = tick.progress().progressTicks() == 0 ? null : tick.progress();
        recipeSignature = progress == null ? null : recipe.signature();
        state = tick.state();
        failure = tick.failure();
        if (changedState) {
            changed.run();
        }
    }

    private boolean validProgress(RollingMachineRecipe recipe, ProcessProgress active) {
        if (!active.definitionId().equals(recipe.getId().toString())
                || active.progressTicks() > recipe.processDefinition().durationTicks()) {
            return false;
        }
        long expectedEnergy = Math.multiplyExact(
                (long) active.progressTicks(),
                recipe.processDefinition().energyPerTick()
        );
        return active.consumedEnergy() == expectedEnergy;
    }

    @Nullable
    private static ProcessResourceAvailability availability(ProcessSimulationResult simulation) {
        if (simulation.successful()) {
            return ProcessResourceAvailability.READY;
        }
        return switch (simulation.failure().code()) {
            case MISSING_ITEM_INPUT -> ProcessResourceAvailability.MISSING_ITEM_INPUT;
            case MISSING_FLUID_INPUT -> ProcessResourceAvailability.MISSING_FLUID_INPUT;
            case OUTPUT_BLOCKED -> ProcessResourceAvailability.OUTPUT_BLOCKED;
            default -> null;
        };
    }

    private static ProcessMachineState stateFor(ProcessFailure failure) {
        return switch (failure.code()) {
            case MISSING_ITEM_INPUT, MISSING_FLUID_INPUT -> ProcessMachineState.WAITING_INPUT;
            case OUTPUT_BLOCKED -> ProcessMachineState.WAITING_OUTPUT;
            default -> ProcessMachineState.RECOVERY_REQUIRED;
        };
    }

    private void updateState(ProcessMachineState replacement, ProcessFailure replacementFailure) {
        if (state != replacement || !failure.equals(replacementFailure)) {
            state = replacement;
            failure = replacementFailure;
            changed.run();
        }
    }

    private RollingMachineProcessPersistence.ProcessData data() {
        return new RollingMachineProcessPersistence.ProcessData(
                state,
                resourceRevision,
                Optional.ofNullable(progress),
                Optional.ofNullable(recipeSignature),
                lastAppliedTransactionId,
                failure
        );
    }

    private void applyLoaded(RollingMachineProcessPersistence.ProcessData data) {
        state = data.state();
        resourceRevision = data.resourceRevision();
        progress = data.progress().orElse(null);
        recipeSignature = data.recipeSignature().orElse(null);
        lastAppliedTransactionId = data.lastAppliedTransactionId();
        failure = data.failure();
    }

    private void reset(UUID replacementMachineId) {
        machineId = Objects.requireNonNull(replacementMachineId, "replacementMachineId");
        state = ProcessMachineState.IDLE;
        failure = ProcessFailure.NONE;
        resourceRevision = 0;
        lastAppliedTransactionId = Optional.empty();
        progress = null;
        recipeSignature = null;
        journal = null;
        processPersistenceStatus = MultiblockNbtStatus.SUPPORTED;
        journalPersistenceStatus = MultiblockNbtStatus.SUPPORTED;
        preservedProcessRoot = null;
        preservedJournalRoot = null;
        recipes.reset();
    }

    private UUID requireMachineId() {
        return Objects.requireNonNull(machineId, "Rolling Machine process owner is not initialized");
    }

    private static ProcessFailure failure(ProcessFailureCode code, String subject) {
        return new ProcessFailure(code, subject);
    }

    private static MultiblockNbtStatus normalizeEmpty(MultiblockNbtStatus status) {
        return status == MultiblockNbtStatus.EMPTY ? MultiblockNbtStatus.SUPPORTED : status;
    }
}
