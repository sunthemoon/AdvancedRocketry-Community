# KNOWN-ISSUES — v0.6.0

## Release boundary

- This is an unreleased developer preview for disposable worlds; long-term
  save compatibility is not promised.
- Flight supports only the server-owned Earth and Moon pads. It does not expose
  arbitrary client coordinates or force-load client-selected chunks.
- Only approved BlockEntity adapters move. Unknown third-party BlockEntities
  fail closed.
- Fixed gameplay and persistence limits are documented in
  [`PERFORMANCE.md`](PERFORMANCE.md).

## Accepted evidence limitation

- Two Forge 47.4.10 user-development clients from exact commit
  `6a293f705e939a67b5b617b1dfaa7deef4d6d7b6` joined the exact packaged-server
  candidate simultaneously and received the same shared server marker.
- The committed evidence is privacy-filtered client/server logging. No
  screenshot or two-player video is claimed.
- Repository owner `sunthemoon` accepted this v0.6-only G8 limitation under
  [`ADR-007`](../../decisions/ADR-007-V060-VISUAL-EVIDENCE-ATTESTATION.md).
  It must not be carried into another version without new evidence.

## Reproducibility boundary

Two clean Windows builds and the packaged-server copy are byte-identical.
Linux CI independently audits and executes its own artifact. Cross-platform
byte equality is not claimed unless the uploaded Linux artifact hash matches.

No Critical or High finding remains in the required Earth-Moon flow. Repeat
the affected tests and owner review if the JAR, flight/persistence schema,
security limits, generated resources, filtered logs, provenance, or checksum
inventory changes.
