# v1.0.0 multiplayer evidence

**No complete multiplayer acceptance exists for the recorded logout artifact.**

- [Station interaction](../../work/v1.0.0-station-duo/VERIFICATION.md) uses two
  real local clients on `c860542e...` (flight protocol 4), including station
  route selection and requested countdown cancellation. It does not establish
  the entire station invitation/ownership/security matrix.
- [Passenger continued use](../../work/v1.0.0-passenger-continued-use/VERIFICATION.md)
  uses two real clients on `b41db06e...` (protocol 5): explicit LEAVE,
  reconnect without returning to the old seat, BOARD, ordinary loader fueling
  and Moon-to-Earth return. The final rocket has two mounted players, fuel 618
  and its 17 diamonds; the loader retains 118 units. This is fixture-assisted
  gameplay, not a full fresh survival installation walkthrough.
- [Readiness](../../work/v1.0.0-passenger-readiness/VERIFICATION.md) and
  [logout](../../work/v1.0.0-passenger-logout/VERIFICATION.md) add later fixes
  not covered by those older two-client sessions.
- [Controlled native reconnect](../../work/v1.0.0-native-reconnect/VERIFICATION.md)
  runs both real clients on `569f41ab...`. Each waits through controlled false
  readiness replies and recovers once; an owner disconnected while pending
  is removed from the queue without moving and subsequently rejoins. Normal
  shutdown preserves the complete rocket projection. This is a debugger-assisted
  correctness case, not actual storage-delay, expiry or performance acceptance.

The native development sessions use private loopback/offline fixtures, not
public-server authentication bypass. They do not prove final-candidate online
authentication. Connection timeout observations remain unresolved; see
[KNOWN-ISSUES](KNOWN-ISSUES.md). Full hashes are in
[evidence-index.json](evidence-index.json).

Final acceptance needs two real candidate clients, owner/non-owner permissions,
flight disconnect/rejoin, shared rocket/seat state, station and satellite
operations, and the version's authentication/whitelist evidence. Record each
client and server identity, normal shutdown, failures and resulting saved
state separately. Do not count FakePlayer checks as V2.
