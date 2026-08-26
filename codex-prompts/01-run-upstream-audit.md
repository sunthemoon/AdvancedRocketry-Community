# Codex Task 01 — Run the upstream code and asset audit

Read:

- `AGENTS.md`
- `UPSTREAM.md`
- `docs/02-UPSTREAM-TREE-AND-ASSET-AUDIT.md`
- `docs/08-ASSET-LICENSE-AND-PROVENANCE.md`
- `docs/PORTING_MATRIX.md`
- `docs/versions/V0.1.0-ASSET-REGISTRY-BASELINE.md`

Use an exact local checkout of `Advanced-Rocketry/AdvancedRocketry` branch `1.12`.

Tasks:

1. Verify and record the exact upstream commit.
2. Build deterministic audit scripts under `tools/audit/`.
3. Generate the complete `legacy-manifest/` outputs required by the audit document.
4. Identify LibVulpes imports, ASM/coremod points, mutable global world state, integer dimension IDs, large classes, network packets, NBT and client/common coupling.
5. Audit assets, references, case collisions, duplicate hashes, model/texture/sound chains, and possible third-party origins.
6. Update `docs/PORTING_MATRIX.md` with exact class/file paths and target versions.
7. Do not copy source or assets into the new mod in this task.
8. Clearly quarantine anything whose license cannot be confirmed.
9. Run the tools twice and verify stable output.
10. Produce `legacy-manifest/audit-summary.md` and a v0.1.0 work log.

Do not claim the entire v0.1.0 milestone is complete; this task covers the upstream-audit slice only.
