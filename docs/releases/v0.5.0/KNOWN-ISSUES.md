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

- The visible client was a Forge 47.4.10 user-development launch connected to
  the prior packaged candidate. The final candidate differs only in the
  GameTest scheduling method described below; all rocket runtime classes and
  resources are unchanged. It was not a separately packaged client install.
- The client log contains inherited warnings for three v0.2 recipe categories
  that Forge does not recognize. Recipe execution, rocket rendering, network
  synchronization, and server persistence were unaffected.
- The manual bundle uses one visible client. Same-region two-request exclusion,
  tracking synchronization, and concurrent transaction behavior are covered by
  deterministic unit/GameTests rather than a second graphical recording.
- A clean local run and the first PR run showed that the inherited closed-door
  atmosphere GameTest read `seedSky=true` in the same tick as fresh Moon block
  placement. The test now waits one normal server tick for the heightmap; no
  production decision, assertion, or timeout was relaxed. Clean local GameTests
  pass 34/34, and the corrected PR run remains the merge gate.
- The final candidate's packaged start/restart and rocket recovery flow was
  rerun. Carry-forward of the visible client evidence is explicit in
  `manual-evidence.json`.

## Reproducibility boundary

Two clean Windows builds and the packaged-server copy are byte-identical.
Linux CI independently audits and executes its artifact. Cross-platform byte
equality is not claimed unless the uploaded Linux artifact hash also matches.

Repeat the affected tests and owner review if the JAR, snapshot schema, scan or
network limits, transaction/recovery logic, generated resources, screenshot,
filtered logs, provenance record, or checksum inventory changes.
