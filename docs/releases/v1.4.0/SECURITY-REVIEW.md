# v1.4 scoped authority and input review

This records development boundaries and tests, not a final candidate security
verdict. Reports and artifact attribution are in [REQUIREMENT-MAP](REQUIREMENT-MAP.md).

- The server owns travel permission, discovery, route/fuel quotes, station
  membership and physical landing. Public star-map metadata does not authorize
  arrival. A discovered gas giant still has no surface; an orbit station is a
  separate authorized destination.
- Console handlers validate player/menu ownership, distance/state, selected
  target and catalog generation. Stale data is rejected/refreshed; menu quotes
  do not scan terrain or force arbitrary chunks. Network-independent GameTests
  do not prove the entire real-client packet path.
- Body/route readers bound raw UTF-8 bytes, nesting, duplicate keys, lexical
  types and counts before candidate publication. Rejected paired reload retains
  the old catalogs/cache; no partially valid subset is silently installed.
- Persistent bindings prevent remove/re-add or cross-restart identity reuse
  after adoption. This does not recover configuration history from before the
  ledger existed, nor make an operator-supplied data pack untrusted executable
  code isolation.
- Research stays owner-private while discoveries are world-shared. Mission
  price/reward/target snapshots survive concurrent claims, removal and reload.
  Permission checks and idempotency do not depend on a client-supplied balance.
- Ordered acknowledged saves and bounded replay prevent a completed paid
  receipt from paying again in the tested recovery states. Unknown/future/full
  stores are retained or refused. Unsupported atomic replacement is a failed
  save, not success. This guarantee is not extended to terminal chunk contents.
- Client sky resources are bounded and cannot change server atmosphere/damage.
  Rejected profile reload retains the last valid map. Missing profiles or an
  unusable physical context use conservative rendering fallback; side separation
  is checked in builds and packaged dedicated runs.

## Verification and remaining work

DATA/MAP/ENV/NAV/DISC/MIG reports retain actual validation, admission, ownership,
replay, failure-injection and byte-readback results plus independent review.
Current short build/GameTests are listed in [TEST-REPORT](TEST-REPORT.md);
earlier artifacts are not upgraded into candidate certification.

Remaining: real malicious/stale packet sequences with two clients, final
candidate-wide C2S assessment, integration/provider combinations and inherited
cross-file recovery scope. Source hard limits are not denial-of-service latency
measurements. Arbitrary third-party mod code is not sandboxed by these APIs.

The MIG-03 test observer uses a loopback debugging connection for owned
disposable servers. It is not a production listener or deployment requirement.
On observer timeout/failure it can detach before parent cleanup; such attempts
are not accepted as continuously held cuts. Do not expose a debug port on an
ordinary public server. This handoff performs no remote or credentialed testing.
