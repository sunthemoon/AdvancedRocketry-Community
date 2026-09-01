# INSTALLATION — v0.6.0 Earth-Moon round trip

> **Unreleased developer preview.** Use only disposable worlds. Long-term save
> compatibility is not promised for this build.

## Requirements

| Component | Requirement |
|---|---|
| Minecraft | Java Edition 1.20.1 |
| Loader | Forge 47.4.10 verification baseline |
| Java | 17 |
| Project JAR | `advancedrocketry-community-1.20.1-0.6.0-dev.jar` |
| Optional dependencies | None |

Verified Windows candidate SHA-256:

```text
cb8d34e797a57e94a1efb595af8dace6f40072cf0d96715a3d8db73a3518668d
```

Install this exact JAR in both client and dedicated-server `mods/`
directories. Do not mix snapshots. The server is authoritative for fuel,
flight planning, launch, transfer, landing, passenger identity, disassembly,
and recovery.

## Milestone content

- Fuel Loader and Rocket Fuel Cell with bounded, exact fuel accounting.
- Fixed Earth/Moon destination console and server-validated flight intent.
- Countdown, ascent, transfer, descent, landing, cancellation, and return.
- Durable cross-dimension recovery journal with one authoritative rocket.
- Passenger UUID/seat recovery and exact post-flight disassembly.
- Operator inspection and recovery commands under `/arce rocket`.

Only the fixed Earth-Moon route is supported. Arbitrary planets, stations,
satellites, warp, terraforming, orbital lasers, and space elevators remain out
of scope.
