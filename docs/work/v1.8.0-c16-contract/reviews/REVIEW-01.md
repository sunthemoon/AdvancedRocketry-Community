# ADR-064 revision 1 independent contract review

Reviewed source: `cd63c5ff53e3daa0e6c3be92f17f16c061ddc5a6` on `codex/v1.8.0-classic-content`.
ADR-064 SHA-256: `e69e423d0cdeb8a573c0502d9a47b2c63b724ff43cc292b4d635ffceaa552fab`.
Scope: C16a-d contract only; repository read-only; no production implementation, no heavy Gradle/GameTest, no reads of the untracked user bundle.

Recommendation: **CHANGES_REQUESTED**. Three High and seven Medium contract findings remain. These are proposed-contract defects/ambiguities, not assertions that C16 runtime already exists.

## High

### H1 - Physical cross-chunk hatches do not provide the promised resource transaction

ADR-064 lines 108-119 and 293-295 store resources in separate hatches while promising lossless transactional completion/restart. ADR-019 lines 16-26 identifies precisely this failure: a hatch chunk may save an after-state while the controller still has no journal; `setChanged()` and a chunk-save event are not durable ordering. The existing `RollingMachineResourceStore.replaceIfMatches` mutates separate ports; `ProcessTransactionExecutor.commit` only calls the journal adapter, whose Rolling implementation marks its controller changed. Consequently an allowed persisted cut is old controller/no journal + old input hatch + new output hatch. The next run can consume that retained input again. A kernel transaction is not a durable multi-chunk transaction.

Preferred safe option: controller-owned Item/Fluid bank and journal, with generation-scoped hatch facades, following ADR-019 lines 30-49. Freeze bind/unbind/removal behavior and preserve exactly one resource owner. An alternative is requiring all participating resources in the controller chunk and rejecting cross-chunk formation. Both change the proposed physical ownership or building behavior and require the owner's explicit decision. Retaining physical cross-chunk ownership needs a verified durable barrier protocol with retained journals and performance evidence, not merely extra `setChanged()` calls. Per-tick external energy can retain ADR-019's explicitly limited guarantee.

### H2 - Pump omits the already-required protection chain

ADR-064 lines 219-221 mentions only a standard block-break event. Accepted ADR-061 lines 467-468 explicitly requires pumps to use ADR-054 section 5. That chain also checks FULL chunks, bounds, protected zones, station BUILD access, dedicated-server spawn protection and the cancellable public effect event before the owner-bound FakePlayer break event. A BreakEvent alone need not enforce those host-owned controls.

Correction: explicitly apply the complete ADR-054 section 5 chain per drain batch, with owner-bound FakePlayer, fail-closed unowned/unsupported ownership, stable refusal codes and protection tests. This clarifies an accepted requirement; it is not a new product restriction.

### H3 - Tau Ceti f nitrogen table cannot load or launch as a data-only change

ADR-064 lines 198-200 says a Tau Ceti f gas-harvest table is only a data revision. The actual generated body has `gas_giant:false` and `landable:true`. `ResourceTables.java:68-71` rejects a gas table on a non-gas-giant, and `ResourceMissionService.java:173-174` independently refuses that gas mission. ADR-052 section 2 and ADR-051's gas-mission eligibility freeze this same boundary. A built-in table would fail initial resource-table loading; merely relaxing the table loader would still leave nitrogen unobtainable.

Options: add nitrogen to a currently eligible gas giant's table (data-only, but owner confirms the source/balance change), or explicitly amend and test the gas-table AND mission-eligibility contracts with a separate capability for atmospheric harvesting. Do not mark a landable body `gas_giant:true` just to bypass both checks.

## Medium

### M1 - New JSON signatures have no old-process/journal migration

ADR-064 lines 147-154 and 293-297 changes all three kernel recipe signatures, and Rolling built-ins change from item JSON to tag JSON. Existing `RollingMachineRecipe.java:217-230` hashes resolved alternative IDs, whereas `RollingMachineRecipeResolver.java:37-42` rejects an active process if that signature changes. Precision and Electrolyzer also validate persisted signatures. Stable recipe IDs alone do not preserve running v1.7 machines. Existing recovery also re-simulates the recipe against a saved journal, so the old journal path must be covered, not only ordinary progress.

Freeze a signature-version/migration contract: canonical semantic JSON, tag membership changes do not change identity, recipe JSON changes under the same ID still invalidate rather than inherit progress, and recognized unchanged legacy definitions/signatures upgrade without resetting progress/resources. Preserve/quarantine unmatched data. Add v1.7 in-progress AND retained-journal fixtures for the three machines, then verify reload/restart twice.

### M2 - Centrifuge output limit and random semantics are inconsistent/unfrozen

ADR-064 lines 126-129 allows four Item outputs, but lines 264-267 names five potential nuggets and calls their values both weights and chances while promising up to four. Independent percentage rolls can produce all five (0.75% with the listed values); weighted selection means different yields. The kernel `ProcessOutput` is concrete and `ProcessMachineLogic` is deterministic; current recovery validates a concrete before/after plan. The contract never fixes when a random result is chosen, persisted or reused if completion waits for capacity/reloads/restarts.

Freeze one precise distribution and bound (e.g. a separately bounded candidate list and four explicit draws, or five independent chance outputs within the existing kernel's eight-output ceiling), duplicate aggregation, and persisted concrete outcome/seed identity. Output-full retries and journal recovery must not re-roll. Add deterministic vectors plus interruption/retry tests; a 10,000-run statistical check alone does not prove conservation.

### M3 - Zero-energy recipes are rejected by the accepted kernel

ADR-064 line 128 allows 0 FE/t. `ProcessDefinition.java:37-38` and accepted ADR-016 require at least 1 FE/t. An independent Java 17 compile/run of the actual domain sources rejects energy 0 and accepts 1 and 10,000.

Minimal correction: range 1-10,000, since all listed built-ins use positive energy. If zero-energy powered processes are intended, explicitly amend the kernel contract and exercise progress/persistence compatibility.

### M4 - Crystallizer's position gravity is not the existing player-field contract

ADR-064 lines 245-249 says gravity at the controller resolves through ADR-058 area gravity rules. `CelestialGravityController.java:41-66` separates a position override from a player-only field layer. `GravityFieldIndex.at` needs a player UUID and trust set; accepted ADR-058 applies fields to players, and ADR-062 defers non-player fields. A controller has no defined player/trust identity or ownership in this machine contract, so different implementations can make the same machine behave differently.

Minimal clarification: use the body's gravity or committed station position override, excluding player area fields. If fields should affect machines, separately freeze that gameplay/authority rule instead of fabricating a player identity.

### M5 - Change-triggered tank pulls can recursively traverse a column

ADR-064 lines 208-210 claims one-neighbor/no-column-scan transfer, but every pull changes both tanks. Executing that same rule synchronously on each change can cascade through a tall column or re-enter neighbor transfer. The single-edge wording does not bound work for one originating update.

Freeze deferred, deduplicated transfer scheduling, at most one loaded neighbor edge/amount per tank per tick and an explicit aggregate budget; never perform transfers inside capability-change callbacks. Define initial empty-tank behavior, chunk unload and cancellation/re-entrancy. Test a tall column and many changes in one tick.

### M6 - Persistence identifies only a schema number, not the new stable bounded data

ADR-064 lines 293-297 says schema 1 but does not freeze roots/fields or exact ownership/migration of binding, generator remaining burn time/container, pump owner, resources and random outcome. Section 5's canister/tank contents add item-carried NBT too. `docs/14` section 3/6 requires persistent IDs/NBT fields/lifecycle frozen before downstream implementation, and `docs/17` section 7 requires bounded strings/collections/NBT and preservation of invalid/future data.

After H1's decision, supply the C16 root/field/limit table, reuse the existing process/journal/binding roots where appropriate, include generator burn and pump owner fields, cap carried tank/FluidStack/item NBT, and specify invalid/future-root preservation/refusal. Add round-trip, corrupt/future and config-capacity-reduction tests. Do not infer resource deletion from a lower capacity setting.

### M7 - First matching recipe has no deterministic ordering or lookup budget

ADR-064 lines 115-116 chooses the first matching recipe without defining order or cap. Existing Rolling/Precision resolvers cap candidates at 1,024 and reject ambiguity; the proposal does not say whether it changes those machines' policy or only the new family. Recipe-manager iteration is not a frozen priority contract.

Freeze the new family's candidate ordering by full ID or explicit bounded priority, per-type candidate cap and lookup/scheduling budget; leave existing ambiguity behavior unchanged unless separately amended. Test overlapping recipes across reload and both insertion orders.

## Informational observations

- The retained etcher lens can be represented without a large new kernel by a same-channel input debit plus matching output restoration. The actual domain probe leaves the lens amount at 1; an ordinary consumable input leaves 0. Freeze/test that adapter convention if used.
- The existing bounded Item/Fluid process types and pattern dimensions can accommodate these machine sizes. No need to introduce LibVulpes or a generic new framework.
- No source asset was read/copied/imported; the user bundle was never read. No version Gate is approved by this review.

## Executed checks and evidence

- `git status --short`, `git branch --show-current`, `git rev-parse HEAD`, `git show --stat --oneline HEAD`: read-only source identity checks, exit 0. Initially only the excluded user bundle was untracked.
- Mandatory governance/version/ADR and kernel reads/searches: executed. A few exploratory `rg`/`Get-Content` calls used nonexistent guessed paths/PowerShell wildcard arguments and emitted non-blocking lookup errors; subsequent exact-path reads succeeded. `javac` was initially absent from PATH; explicit JDK 17.0.7 was used instead.
- `C:/Program Files/Java/jdk-17.0.7/bin/javac.exe -d <out>/classes <all direct machine/process/*.java> <out>/KernelContractProbe.java`: exit 0; `<out>/javac.log`.
- `C:/Program Files/Java/jdk-17.0.7/bin/java.exe -cp <out>/classes KernelContractProbe`: exit 0; `<out>/kernel-probe.log`.
- Python read-only static data/chance/cut model: exit 0; `<out>/static-probe.json`. Independent percentage rolls are explicitly a conditional interpretation, not a claim of the legacy RNG implementation. The cross-chunk cut is an abstract store-order witness backed by ADR-019, not a native-server crash run.
- `git diff --check`: exit 0.
- `git diff --exit-code`: exit 1 because root concurrently changed ADR-063's revision header and the implementation log; no reviewer repository writes.
- Gradle, Forge GameTest, packaged/native server, S2 and visual validation: NOT RUN under delegated scope and coordination restriction.

Output directory: `C:/Users/Administrator/AppData/Local/Temp/arce-c16-contract-4bb2b067ea484b0980c779fdfd8cb079`.
Only this fresh Temp directory was written by the reviewer. Production/Gate acceptance remains outside this review.
