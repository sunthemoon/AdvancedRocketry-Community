# Provenance records

Store one record per imported file or coherent batch. Files without a valid record must not enter a release JAR.

Use `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md`.

## Recorded batches

- [v1.0.0 Stable Core stabilization](v1.0.0-stable-core.md) records
  community-authored tests, harnesses and metadata changes; review is pending.

- [v1.0+ planning input](v1.0.0-v1plus-planning.md) records the user-supplied
  development documentation package, its hashes and reference-only boundaries;
  this is not an upstream asset import or release approval.

- [`v0.0.2-forge-mdk-and-gradle-wrapper.md`](v0.0.2-forge-mdk-and-gradle-wrapper.md)
  records the official Forge MDK bootstrap inputs and Gradle Wrapper component.
- [`v0.1.0-minimal-content.json`](v0.1.0-minimal-content.json) records the
  approved minimal MIT asset import; its generated-resource inventory is frozen
  in [`v0.1.0-generated-resources.json`](v0.1.0-generated-resources.json).
- [`v0.2.0-electrolyzer.md`](v0.2.0-electrolyzer.md) records the behavior-only
  upstream reference, zero copied assets, runtime texture IDs, and owner G0
  approval for the Electrolyzer slice.
- [`v0.4.0-atmosphere.md`](v0.4.0-atmosphere.md) tracks the community-authored
  atmosphere/life-support slice and currently records zero copied source files
  or binary assets.
- [`v0.7.0-space-station.md`](v0.7.0-space-station.md) records the
  community-authored Space Station slice, its zero-copy G0 approval, and the
  exact five-file generated-resource inventory.

Supplemental exact license copies are stored in `docs/licenses/` and mapped in
[`THIRD-PARTY-NOTICES.md`](../../THIRD-PARTY-NOTICES.md). Adding a copy does not
by itself assign `THIRD_PARTY_APPROVED` or complete a release Gate.
