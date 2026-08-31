# Provenance records

Store one record per imported file or coherent batch. Files without a valid record must not enter a release JAR.

Use `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md`.

## Recorded batches

- [`v0.0.2-forge-mdk-and-gradle-wrapper.md`](v0.0.2-forge-mdk-and-gradle-wrapper.md)
  records the official Forge MDK bootstrap inputs and Gradle Wrapper component.
- [`v0.1.0-minimal-content.json`](v0.1.0-minimal-content.json) records the
  approved minimal MIT asset import; its generated-resource inventory is frozen
  in [`v0.1.0-generated-resources.json`](v0.1.0-generated-resources.json).
- [`v0.2.0-electrolyzer.md`](v0.2.0-electrolyzer.md) records the behavior-only
  upstream reference, zero copied assets, runtime texture IDs, and owner G0
  approval for the Electrolyzer slice.

Supplemental exact license copies are stored in `docs/licenses/` and mapped in
[`THIRD-PARTY-NOTICES.md`](../../THIRD-PARTY-NOTICES.md). Adding a copy does not
by itself assign `THIRD_PARTY_APPROVED` or complete a release Gate.
