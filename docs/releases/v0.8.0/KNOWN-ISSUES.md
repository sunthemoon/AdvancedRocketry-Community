# Known issues — v0.8.0

- The complete Satellite Terminal sequence is an ordered pre-candidate
  screenshot set rather than continuous video. ADR-011 limits this evidence
  substitution to v0.8.0; final-candidate multiplayer screenshots and logs are
  separately bound to the accepted JAR.
- One preliminary concurrent Forge user-development client launch timed out
  while another OpenGL client was active. That attempt is excluded. Both
  accepted initial and post-restart cycles connected two clients
  simultaneously and shut them down cleanly.
- The accepted candidate is byte-reproducible across two clean builds on the
  same Windows environment. Cross-platform byte-for-byte equality with Linux
  CI is not claimed.
- v0.8.0 supplies one logical data-satellite type and one combined Satellite
  Terminal. Satellites do not render as orbiting entities and do not hold
  chunks loaded.
- Offline progress follows persisted server game time. Stopping the server
  does not synthesize wall-clock mission progress.
- Saves containing a future satellite, mission, research, or terminal schema
  are preserved but blocked. Save downgrade is unsupported.
- The developer preview has no public release tag and must not be presented as
  stable.
