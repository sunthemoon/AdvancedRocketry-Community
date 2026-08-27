# Provenance records

Store one record per imported file or coherent batch. Files without a valid record must not enter a release JAR.

Use `docs/templates/SOURCE-PROVENANCE-TEMPLATE.md`.

## Recorded batches

- [`v0.0.2-forge-mdk-and-gradle-wrapper.md`](v0.0.2-forge-mdk-and-gradle-wrapper.md)
  records the official Forge MDK bootstrap inputs and Gradle Wrapper component.
  Its evidence is complete, but human license/provenance review remains pending.

Supplemental exact license copies are stored in `docs/licenses/` and mapped in
[`THIRD-PARTY-NOTICES.md`](../../THIRD-PARTY-NOTICES.md). Adding a copy does not
by itself assign `THIRD_PARTY_APPROVED` or complete a release Gate.
