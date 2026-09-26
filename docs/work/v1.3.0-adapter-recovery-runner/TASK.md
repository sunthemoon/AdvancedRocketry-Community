# V130-ROCKET-03B — bounded recovery runner

- Status: READY_FOR_REVIEW; implementation and Python verification only.
- Worktree: `D:/GitHub/arce-v130-adapter-recovery`.
- Branch: `codex/v1.3.0-adapter-recovery`.
- Base: `b42cbdb401265c2159702a2b0045c79338955c6a`.
- Write scope: one runner, its conventionally located Python tests, this record.
- Dependencies: approved ADR-022; independently built API-only fixture; fixture
  startup property `arce_adapter_test.skipRocketAdapter=true` supplied by integrator.
- Outcome: six clean processes exercise external cargo assembly, entity restart,
  provider-skipped refusal, actual fixture removal, reinstall recovery and restored
  BlockEntity restart. Compare saved entity/payload/journal/inventory authority.
- Non-goals: real crash/power loss, cross-dimension flight, real clients, arbitrary
  absent item mods, new production hooks/API, network downloads or long-load tests.
- Ownership: do not modify host Java/build, fixture Java, shared Python helpers or
  central documentation. Independent execution uses fresh disposable directories.
- Validation: focused pure Python unittest, syntax/help and whitespace checks;
  delegated implementation does not run Gradle, Java or Minecraft.

The skipped-provider phase stages a synthetic EXTRACTING journal only after its
ordinary disassembly refusal. Recovery is suppressed until shutdown by the existing
host command. It does not test a pending-journal skipped-provider restart. The
actual-uninstall phase exercises the persisted journal retry with missing block
and adapter registrations; reinstall then retries the same journal.
