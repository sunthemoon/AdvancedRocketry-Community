# Known issues — v0.7.0

- No screenshot or video is claimed. Owner acceptance uses the scoped ADR-009
  attestation, actual two-client logs, packaged station evidence, and automated
  authority checks.
- Local dual-window Forge user-development launches intermittently timed out
  during login while another OpenGL client rendered in the foreground.
  Minimizing the connected client allowed the accepted run to join both
  players simultaneously and shut down cleanly. This has not been reproduced
  with packaged production launcher clients or separate machines.
- The accepted candidate is byte-reproducible across two clean builds on the
  same Windows environment. Cross-platform byte-for-byte equality with Linux
  CI is not claimed.
- Station regions are fixed at 512×512 blocks on a 1,024-block grid. Stations
  cannot move or resize in v0.7.0.
- Station deletion removes the generated platform after an operator-visible
  backup audit record; it is not a substitute for an external world backup.
- Space uses the existing bounded vacuum and simplified environment profile;
  complex orbital mechanics, station rotation, and full artificial gravity
  are outside this milestone.
- Saves containing a future station schema are preserved but blocked. Save
  downgrade is unsupported.
- The developer preview has no public release tag and must not be presented as
  stable.
