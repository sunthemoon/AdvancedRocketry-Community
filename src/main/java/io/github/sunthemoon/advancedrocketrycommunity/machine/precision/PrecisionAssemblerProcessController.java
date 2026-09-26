package io.github.sunthemoon.advancedrocketrycommunity.machine.precision;

import io.github.sunthemoon.advancedrocketrycommunity.machine.multiblock.lifecycle.MultiblockFormationState;
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
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessJournalPersistence;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtResult;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessNbtStatus;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStateData;
import io.github.sunthemoon.advancedrocketrycommunity.machine.process.persistence.ProcessStatePersistence;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;

/** Server-only process and journal owner; Item resources share its controller chunk. */
final class PrecisionAssemblerProcessController implements ProcessJournalStore {
    private final Runnable changed;
    private final PrecisionAssemblerRecipeResolver recipes = new PrecisionAssemblerRecipeResolver();

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
    private ProcessNbtStatus processStatus = ProcessNbtStatus.SUPPORTED;
    private ProcessNbtStatus journalStatus = ProcessNbtStatus.SUPPORTED;
    @Nullable
    private Tag preservedProcessRoot;
    @Nullable
    private Tag preservedJournalRoot;

    PrecisionAssemblerProcessController(Runnable changed) {
        this.changed = Objects.requireNonNull(changed, "changed");
    }

    void initialize(UUID initializedMachineId) {
        if (machineId != null) {
            throw new IllegalStateException("Precision Assembler process owner is already initialized");
        }
        machineId = Objects.requireNonNull(initializedMachineId, "initializedMachineId");
    }

    void load(CompoundTag parent, UUID loadedMachineId) {
        reset(loadedMachineId);
        ProcessNbtResult<ProcessStateData> processResult = ProcessStatePersistence.decode(parent);
        processStatus = normalizeEmpty(processResult.status());
        if (processResult.status() == ProcessNbtStatus.SUPPORTED) {
            ProcessStateData data = processResult.value().orElseThrow();
            state = data.state();
            failure = data.failure();
            resourceRevision = data.resourceRevision();
            progress = data.progress().orElse(null);
            recipeSignature = data.recipeSignature().orElse(null);
            lastAppliedTransactionId = data.lastAppliedTransactionId();
        } else if (processResult.status() != ProcessNbtStatus.EMPTY) {
            preservedProcessRoot = processResult.preservedRoot().orElseThrow();
            state = blockedState(processStatus);
        }

        ProcessNbtResult<ProcessTransactionJournal> journalResult = ProcessJournalPersistence.decode(parent);
        journalStatus = normalizeEmpty(journalResult.status());
        if (journalResult.status() == ProcessNbtStatus.SUPPORTED) {
            ProcessTransactionJournal decoded = journalResult.value().orElseThrow();
            if (!decoded.machineId().equals(loadedMachineId)) {
                journalStatus = ProcessNbtStatus.INVALID_DATA;
                preservedJournalRoot = parent.get(ProcessJournalPersistence.ROOT).copy();
                state = ProcessMachineState.RECOVERY_REQUIRED;
            } else {
                journal = decoded;
                if (processStatus == ProcessNbtStatus.SUPPORTED) {
                    state = ProcessMachineState.RECOVERY_REQUIRED;
                }
            }
        } else if (journalResult.status() != ProcessNbtStatus.EMPTY) {
            preservedJournalRoot = journalResult.preservedRoot().orElseThrow();
            if (processStatus == ProcessNbtStatus.SUPPORTED) {
                state = blockedState(journalStatus);
            }
        }
    }

    void save(CompoundTag parent) {
        parent.put(ProcessStatePersistence.ROOT,
                preservedProcessRoot != null ? preservedProcessRoot.copy() : ProcessStatePersistence.encode(
                        new ProcessStateData(state, resourceRevision, Optional.ofNullable(progress),
                                Optional.ofNullable(recipeSignature), lastAppliedTransactionId, failure)
                ));
        if (preservedJournalRoot != null) {
            parent.put(ProcessJournalPersistence.ROOT, preservedJournalRoot.copy());
        } else if (journal != null) {
            parent.put(ProcessJournalPersistence.ROOT, ProcessJournalPersistence.encode(journal));
        } else {
            parent.remove(ProcessJournalPersistence.ROOT);
        }
    }

    boolean tick(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        if (!acceptsResourceAccess()
                || controller.formationState() != MultiblockFormationState.FORMED) {
            return false;
        }
        Optional<PrecisionAssemblerPortSet> resolved = PrecisionAssemblerPortSet.resolve(level, controller);
        if (resolved.isEmpty()) {
            return false;
        }
        PrecisionAssemblerPortSet ports = resolved.orElseThrow();
        if (journal != null) {
            return recover(level, controller, ports);
        }
        if (state == ProcessMachineState.RECOVERY_REQUIRED) {
            return false;
        }

        PrecisionAssemblerRecipeResolver.Resolution resolution = recipes.resolve(
                level, controller.getBlockPos(),
                ports.inputs().stream().map(PrecisionAssemblerPortBlockEntity::storedItemCopy).toList(),
                progress, recipeSignature
        );
        if (resolution.recipe().isEmpty()) {
            updateState(resolution.missingState(), resolution.failure());
            return false;
        }
        PrecisionAssemblerRecipe recipe = resolution.recipe().orElseThrow();
        ProcessProgress active = progress == null
                ? ProcessProgress.notStarted(recipe.processDefinition()) : progress;
        if (!validProgress(recipe, active)) {
            updateState(ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, active.definitionId()));
            return false;
        }
        if (progress != null
                && active.progressTicks() == recipe.processDefinition().durationTicks()) {
            updateState(ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, active.definitionId()));
            return false;
        }
        PrecisionAssemblerResourceStore resources = new PrecisionAssemblerResourceStore(
                level, controller, this, recipe, ports
        );
        if (!resources.inputsMatchRecipe()) {
            updateState(ProcessMachineState.WAITING_INPUT,
                    failure(ProcessFailureCode.MISSING_ITEM_INPUT, recipe.getId().toString()));
            return false;
        }
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(), resources.snapshot()
        );
        ProcessResourceAvailability availability = availability(simulation);
        if (availability == null) {
            updateState(ProcessMachineState.INVALID_RECIPE, simulation.failure());
            return false;
        }
        ProcessTickResult tick = ProcessMachineLogic.tick(
                recipe.processDefinition(), active,
                new ProcessTickInput(true, !level.hasNeighborSignal(controller.getBlockPos()),
                        availability, ports.energy().storedEnergy())
        );
        if (tick.energyConsumed() > 0
                && !ports.energy().consumeEnergyInternal(Math.toIntExact(tick.energyConsumed()))) {
            updateState(ProcessMachineState.WAITING_ENERGY,
                    failure(ProcessFailureCode.INSUFFICIENT_ENERGY, recipe.getId().toString()));
            return false;
        }
        applyTick(recipe, tick);
        if (tick.completionDue()) {
            return complete(controller, recipe, resources);
        }
        return tick.state() == ProcessMachineState.RUNNING;
    }

    boolean locked() {
        return !permitsExternalResourceOperations() || progress != null;
    }

    boolean acceptsResourceAccess() {
        return processStatus == ProcessNbtStatus.SUPPORTED
                && journalStatus == ProcessNbtStatus.SUPPORTED;
    }

    boolean permitsExternalResourceOperations() {
        return acceptsResourceAccess() && journal == null
                && state != ProcessMachineState.RECOVERY_REQUIRED
                && state != ProcessMachineState.UNSUPPORTED_DATA;
    }

    void recordExternalMutation(ServerLevel level, PrecisionAssemblerBlockEntity controller) {
        if (!acceptsResourceAccess()) {
            return;
        }
        resourceRevision = Math.addExact(resourceRevision, 1);
        changed.run();
        PrecisionAssemblerRuntime.markProcessReady(level, controller.getBlockPos());
    }

    void requireRecoveryAfterUnexpectedTickFailure() {
        updateState(ProcessMachineState.RECOVERY_REQUIRED,
                failure(ProcessFailureCode.RECOVERY_DIVERGED, "process_tick"));
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

    int totalProcessingTicks(ServerLevel level) {
        if (progress == null || recipeSignature == null) {
            return 0;
        }
        ResourceLocation id = ResourceLocation.tryParse(progress.definitionId());
        if (id == null) {
            return 0;
        }
        Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded =
                level.getRecipeManager().byKey(id);
        if (loaded.isEmpty() || !(loaded.orElseThrow() instanceof PrecisionAssemblerRecipe recipe)
                || !recipe.signature().equals(recipeSignature)) {
            return 0;
        }
        return recipe.processDefinition().durationTicks();
    }

    long resourceRevision() {
        return resourceRevision;
    }

    Optional<UUID> lastAppliedTransactionId() {
        return lastAppliedTransactionId;
    }

    ProcessNbtStatus processPersistenceStatus() {
        return processStatus;
    }

    ProcessNbtStatus journalPersistenceStatus() {
        return journalStatus;
    }

    void replaceResourceRevision(long expected, long replacement) {
        if (resourceRevision != expected || replacement != Math.addExact(expected, 1)) {
            throw new IllegalStateException("Precision Assembler resource revision changed during commit");
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
        if (!acceptsResourceAccess() || !checked.machineId().equals(machineId)) {
            throw new IllegalStateException("Precision Assembler rejected another process owner's journal");
        }
        journal = checked;
        changed.run();
    }

    @Override
    public void clear(UUID transactionId) {
        if (journal == null || !journal.transactionId().equals(transactionId)) {
            throw new IllegalStateException("Precision Assembler cannot clear a different journal");
        }
        journal = null;
        changed.run();
    }

    private boolean recover(
            ServerLevel level,
            PrecisionAssemblerBlockEntity controller,
            PrecisionAssemblerPortSet ports
    ) {
        ProcessTransactionJournal pending = Objects.requireNonNull(journal);
        ResourceLocation id = ResourceLocation.tryParse(pending.definitionId());
        Optional<? extends net.minecraft.world.item.crafting.Recipe<?>> loaded = id == null
                ? Optional.empty() : level.getRecipeManager().byKey(id);
        if (loaded.isEmpty() || !(loaded.orElseThrow() instanceof PrecisionAssemblerRecipe recipe)) {
            updateState(ProcessMachineState.INVALID_RECIPE,
                    failure(ProcessFailureCode.INVALID_RECIPE, pending.definitionId()));
            return false;
        }
        ProcessSimulationResult expected = ProcessMachineLogic.simulate(
                recipe.processDefinition(), pending.before()
        );
        if (expected.plan().isEmpty()
                || !expected.plan().orElseThrow().after().equals(pending.after())
                || (progress != null && (!progress.definitionId().equals(recipe.getId().toString())
                || !recipe.signature().equals(recipeSignature)))) {
            updateState(ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, pending.definitionId()));
            return false;
        }
        PrecisionAssemblerResourceStore resources = new PrecisionAssemblerResourceStore(
                level, controller, this, recipe, ports
        );
        if (!resources.reconcileJournal(pending)) {
            updateState(ProcessMachineState.RECOVERY_REQUIRED,
                    failure(ProcessFailureCode.RECOVERY_DIVERGED, pending.definitionId()));
            return false;
        }
        return handleTransactionResult(ProcessTransactionExecutor.recover(resources, this));
    }

    private boolean complete(
            PrecisionAssemblerBlockEntity controller,
            PrecisionAssemblerRecipe recipe,
            PrecisionAssemblerResourceStore resources
    ) {
        ProcessSimulationResult simulation = ProcessMachineLogic.simulate(
                recipe.processDefinition(), resources.snapshot()
        );
        if (simulation.plan().isEmpty()) {
            updateState(stateFor(simulation.failure()), simulation.failure());
            return false;
        }
        ProcessTransactionResult result = ProcessTransactionExecutor.commit(
                UUID.randomUUID(), controller.controllerState().machineInstanceId(),
                simulation.plan().orElseThrow(), resources, this
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
        updateState(result.status() == ProcessTransactionStatus.STALE_TRANSACTION
                        ? ProcessMachineState.RUNNING : ProcessMachineState.RECOVERY_REQUIRED,
                result.failure());
        return result.status() == ProcessTransactionStatus.STALE_TRANSACTION;
    }

    private void applyTick(PrecisionAssemblerRecipe recipe, ProcessTickResult tick) {
        ProcessProgress next = tick.progress().progressTicks() == 0 ? null : tick.progress();
        String nextSignature = next == null ? null : recipe.signature();
        boolean changedState = !Objects.equals(progress, next)
                || !Objects.equals(recipeSignature, nextSignature)
                || state != tick.state() || !failure.equals(tick.failure());
        progress = next;
        recipeSignature = nextSignature;
        state = tick.state();
        failure = tick.failure();
        if (changedState) {
            changed.run();
        }
    }

    private static boolean validProgress(PrecisionAssemblerRecipe recipe, ProcessProgress active) {
        return active.definitionId().equals(recipe.getId().toString())
                && active.progressTicks() <= recipe.processDefinition().durationTicks()
                && active.consumedEnergy() == Math.multiplyExact(
                        (long) active.progressTicks(), recipe.processDefinition().energyPerTick());
    }

    @Nullable
    private static ProcessResourceAvailability availability(ProcessSimulationResult simulation) {
        if (simulation.successful()) {
            return ProcessResourceAvailability.READY;
        }
        return switch (simulation.failure().code()) {
            case MISSING_ITEM_INPUT -> ProcessResourceAvailability.MISSING_ITEM_INPUT;
            case OUTPUT_BLOCKED -> ProcessResourceAvailability.OUTPUT_BLOCKED;
            default -> null;
        };
    }

    private static ProcessMachineState stateFor(ProcessFailure failure) {
        return switch (failure.code()) {
            case MISSING_ITEM_INPUT -> ProcessMachineState.WAITING_INPUT;
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

    private void reset(UUID replacementMachineId) {
        machineId = Objects.requireNonNull(replacementMachineId, "replacementMachineId");
        state = ProcessMachineState.IDLE;
        failure = ProcessFailure.NONE;
        resourceRevision = 0;
        lastAppliedTransactionId = Optional.empty();
        progress = null;
        recipeSignature = null;
        journal = null;
        processStatus = ProcessNbtStatus.SUPPORTED;
        journalStatus = ProcessNbtStatus.SUPPORTED;
        preservedProcessRoot = null;
        preservedJournalRoot = null;
        recipes.reset();
    }

    private static ProcessNbtStatus normalizeEmpty(ProcessNbtStatus status) {
        return status == ProcessNbtStatus.EMPTY ? ProcessNbtStatus.SUPPORTED : status;
    }

    private static ProcessMachineState blockedState(ProcessNbtStatus status) {
        return status == ProcessNbtStatus.UNSUPPORTED_SCHEMA
                ? ProcessMachineState.UNSUPPORTED_DATA : ProcessMachineState.RECOVERY_REQUIRED;
    }

    private static ProcessFailure failure(ProcessFailureCode code, String subject) {
        return new ProcessFailure(code, subject);
    }
}
