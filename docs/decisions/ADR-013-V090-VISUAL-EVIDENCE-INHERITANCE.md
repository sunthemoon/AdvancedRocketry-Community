# ADR-013 — v0.9.0 visual-evidence inheritance and owner acceptance

- Status: `ACCEPTED`
- Date: 2026-09-03
- Decision owner: repository owner `sunthemoon`
- Applies to: `v0.9.0` only
- Expires: `v1.0.0`

## Context

The v0.9.0 feature freeze permits stability, migration, diagnostics, security,
performance, and isolated compatibility changes. It does not change the core
world renderer, HUD, machine screens, station screens, terminal screens, or
ARCE asset set accepted for v0.8.0. The Beta candidate was exercised by four
Forge user-development clients across both supported Forge lanes, with JEI
present and absent, against the exact packaged JAR.

The repository owner explicitly directed that G8 and G9 be confirmed without
repeating the visible-client capture session. The older ADR-011 exception was
limited to v0.8.0 and therefore cannot silently authorize this decision.

## Decision

For v0.9.0 only, the owner accepts the immutable, genuine v0.8.0 screenshots
as the visual baseline for unchanged core UI and rendering. They are combined
with the v0.9.0 exact-JAR compatibility matrix, resource/localization audit,
DataGen result, packaged lifecycle records, and an explicit owner attestation.

No file is copied into the v0.9.0 evidence directory and no screenshot is
represented as newly captured. The v0.9.0 attestation references each original
path and SHA-256. The only new client presentation surface is the optional JEI
Electrolyzer category; both JEI-present clients recorded successful category
registration with exactly one synchronized recipe, while both JEI-absent
clients joined normally.

This decision records a bounded G8 evidence substitution. It does not waive
the technical G1-G7 results, the two-hour soak, the compatibility matrix, the
release checksum, merge reproduction, or correct GitHub pre-release marking.

## Risk and mitigation

- A new exact-candidate screenshot set at multiple GUI scales was not captured,
  so a purely visual regression not surfaced by client startup remains possible.
- Core screen, renderer, generated asset, and source asset paths have no changes
  between the accepted v0.8.0 baseline and the tested v0.9.0 implementation.
- Four exact-JAR client runs cover Forge 47.4.10/47.4.23 and JEI
  present/absent; all joined the packaged server with zero project ERROR/FATAL
  findings and zero unknown recipe-category findings.
- The dedicated resource audit checks both language catalogs, placeholders,
  JSON parsing, case-exact assets, referenced translations, and textual status
  alternatives.
- The residual limitation is published in `KNOWN-ISSUES.md` and explicitly
  accepted by the repository owner.

## Removal condition

v1.0.0 must capture and archive a fresh exact-candidate visible-client record,
including the supported GUI scales and any optional compatibility UI that is
part of the stable claim. This ADR cannot be extended implicitly.
