# Known issues and limits — v0.9.0 Beta 1

- No new v0.9.0 screenshot set was captured. The repository owner accepted the
  immutable genuine v0.8.0 screenshots for unchanged core visuals, together
  with four exact-JAR v0.9.0 client runs and the resource audit. ADR-013 records
  this bounded substitution and requires fresh multi-scale evidence for v1.0.0.
- The JAR is byte-identical across two clean builds on the recorded Windows
  environment. Linux CI separately audits and runs its own JAR; cross-platform
  byte-for-byte equality is not claimed.
- Forge `47.4.23` is an advisory lane. Forge `47.4.10` remains the Beta release
  baseline. Other Forge builds and arbitrary modpacks are not supported claims.
- JEI compatibility is limited to the recorded `15.56.0.205` client build.
  JEI emits an external warning before it reloads the synchronized ARCE recipe;
  both tested JEI clients then report exactly one Electrolyzer recipe.
- The four-cell client matrix used unauthenticated Forge user-development
  clients on a loopback-only disposable server. Offline mode is test-only and
  is not supported for public-server authentication bypass.
- The two-hour headless soak uses four concurrent Minecraft status-protocol
  clients. It exercises server connection handling and the maximum combined
  authority state, but does not measure client rendering performance.
- The supported valued-world upgrade starts at accepted v0.8.0. Earlier schema
  fixtures remain regression inputs, not a promise to preserve every earlier
  disposable test world. Downgrade and direct 1.12.2 world loading are blocked.
- Satellites remain bounded logical missions rather than orbiting entities, and
  the Beta does not promise dynamic dimensions, warp, terraforming, asteroid
  mining, orbital weapons, or universal modpack compatibility.

The candidate audit has zero known Critical or High findings. New duplication,
permission bypass, arbitrary chunk loading, remote crash, or save-corruption
reports should follow [`SECURITY.md`](../../../SECURITY.md) and include the JAR
SHA-256 plus a minimal reproduction.
