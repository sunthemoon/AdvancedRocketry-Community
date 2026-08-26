# SECURITY.md

## Supported versions

Until `v1.0.0`, only the latest published pre-release is considered for security-sensitive fixes. Old test builds may be closed without patches.

## Report privately when possible

Security-sensitive issues include:

- item, fluid, block, passenger, rocket, or station duplication;
- remote crash or packet amplification;
- arbitrary chunk loading;
- bypass of station ownership or launch permissions;
- oversized NBT/packet memory exhaustion;
- save corruption with a reliable reproduction;
- server-side acceptance of forged client state.

Do not publish a working exploit before maintainers have had an opportunity to investigate.

## Include

- exact mod version and Forge version;
- dedicated server or singleplayer;
- minimal mod list;
- steps to reproduce;
- logs/crash report;
- world backup or minimal GameTest structure when possible;
- whether the exploit survives restart;
- estimated impact.

## Non-security reports

Visual issues, ordinary crashes without an exploit, balance concerns, and feature requests should use the normal issue templates.

## Disclosure handling

Maintainers should:

1. acknowledge the report;
2. reproduce with a minimal environment;
3. classify severity;
4. add a regression test;
5. fix without silently weakening validation;
6. publish release notes after a patched build exists.

No response-time guarantee is made for this volunteer project.
