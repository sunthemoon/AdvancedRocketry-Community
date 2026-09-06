# v1.0.0 development issues and limits

**No stable v1.0 distribution is approved. Use backed-up world copies.**

## Unresolved behavior and verification risks

- Initial native connection attempts intermittently time out in retained local
  development sessions. Same-process Direct Connection succeeds in some
  recorded retries; the cause is unresolved. Do not assume authentication,
  hardware load or a timeout increase is the fix. Candidate triage is required.
  See the [console observations](../../work/v1.0.0-console-presentation/VERIFICATION.md).
- Passenger readiness/retry and immediate logout cleanup have focused Forge
  regressions and [controlled native queue evidence](../../work/v1.0.0-native-reconnect/VERIFICATION.md)
  on the recorded logout artifact. Genuine adjacent entity-chunk storage
  arrival order and native expiry behavior remain unverified. The instrumented
  run retains server lag warnings and cannot establish performance budgets.
- Final-candidate migration, dedicated restart, compatibility, security,
  full visual/player-flow and performance acceptance are incomplete. There is
  no independent installation report, reviewer signature or release approval.

These are unresolved observations/coverage gaps, not an assertion that every
item is a reproduced product defect or that no other defects exist. See
[GATE-STATUS](GATE-STATUS.md) before considering release readiness.

## Compatibility and support boundaries

- Minecraft 1.20.1 / Java 17 / Forge 47.4.10 is the target. Forge 47.4.23 is
  a compatibility lane, not automatic support for arbitrary Forge versions.
- Flight protocol 5 requires matching peers. Old protocol-4 visual sessions
  are historical observations, not compatibility evidence for this channel.
- JEI testing covers the explicitly recorded client version/combinations;
  arbitrary modpacks and server-side JEI installation are not established.
- No direct 1.12.2 world import, downgrade, dynamic-dimension expansion,
  full legacy machine tree, warp, terraforming or classic feature parity.
- The planned 1.0.x maintenance line accepts compatible defect, security and
  migration fixes. It does not authorize breaking changes to valued worlds.

File a minimal reproduction with the exact JAR hash and relevant logs. Never
include passwords, tokens or launcher account data in a report.
