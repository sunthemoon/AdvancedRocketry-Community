# KNOWN-ISSUES — v0.5.0

## Release boundary

- Rockets are static same-dimension entities. Launch, flight, fuel use,
  destination selection, and Earth–Moon travel are not implemented.
- Only explicitly approved BlockEntity adapters may move. Vanilla chest and
  barrel contents are supported; unknown third-party BlockEntities fail closed.
- Disassembly restores the captured origin only and refuses occupied or
  unloaded destinations.
- The visual payload intentionally excludes BlockEntity data. Inventory and
  other authoritative state remain server-only.
- Test worlds are disposable; long-term compatibility is not promised.

## Runtime observations

- The visible client was a Forge 47.4.10 user-development launch of the exact
  tested implementation, connected to the packaged-JAR server. It was not a
  separately packaged client installation.
- The client log contains inherited warnings for three v0.2 recipe categories
  that Forge does not recognize. Recipe execution, rocket rendering, network
  synchronization, and server persistence were unaffected.
- The manual bundle uses one visible client. Same-region two-request exclusion,
  tracking synchronization, and concurrent transaction behavior are covered by
  deterministic unit/GameTests rather than a second graphical recording.
- One final local GameTest attempt saw the inherited closed-door atmosphere
  test read a transient `seedSky=true` immediately after cross-dimension block
  placement and failed. The unchanged rerun passed all 34 required tests. CI is
  retained as the independent acceptance run; recurrence blocks the merge.

## Reproducibility boundary

Two clean Windows builds and the packaged-server copy are byte-identical.
Linux CI independently audits and executes its artifact. Cross-platform byte
equality is not claimed unless the uploaded Linux artifact hash also matches.

Repeat the affected tests and owner review if the JAR, snapshot schema, scan or
network limits, transaction/recovery logic, generated resources, screenshot,
filtered logs, provenance record, or checksum inventory changes.
