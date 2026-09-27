# v1.4 pending real-player acceptance

**Not executed for this handoff.** These are candidate test procedures, not
passing results. Record exact host/client JARs, packs/config, GPU/driver, players,
world-copy identity, commands, media and failures before assigning outcomes.
Existing PCL authentication evidence is not repeated or reclassified as v1.4
gameplay evidence.

| Case | Procedure and observations to record |
|---|---|
| MT140-01 planetary progression | On a backed-up copy, obtain satellite/rocket equipment, research Mars/Venus, visit both, restart and return; record terrain, inventory, fuel and environment |
| MT140-02 shared knowledge | Use two real clients with separate accounts; record locked-before/available-after discovery, private reward balances, concurrent captured fees and repeated claims |
| MT140-03 access remains separate | Try a private station before/after shared discovery as member/nonmember; confirm denied surface selection for gas giant and no invented Level |
| MT140-04 protection and oxygen | Compare cold/heat/pressure/sunlight in open sky, equipment and supplied sealed rooms; track oxygen separately and inspect player messages |
| MT140-05 navigation | Exercise star-map nodes, station list, quotes, focus and reload while open; test en_us/zh_cn, small/large windows and GUI scales without clipping |
| MT140-06 presentation | Visit Earth/Moon/Mars/Venus/shared Space; capture sky/sun/stars/fog at day/night; test water/lava/blindness/darkness, resource reload and unavailable profiles |
| MT140-07 transition lifecycle | Rapidly change worlds, reconnect and reload profiles; check no previous-body geometry/fog leaks, sound stops under cover and cooldown is retained |
| MT140-08 pack operations | On a copy, test invalid paired reload, removed/restored body plus dependent routes/targets, unavailable destinations and retained station/progress identities |
| MT140-09 upgrade | Use separately inventoried representative candidate upgrade worlds; compare stable IDs, station orbits, rockets/cargo, research and providers; preserve originals and failures |

Use the detailed [navigation cases](../../work/v1.4.0-nav/MANUAL-CASES.md),
[sky cases](../../work/v1.4.0-sky/MANUAL-CASES.md) and
[discovery cases](../../work/v1.4.0-discovery/MANUAL-CASES.md) for controls and
specific observations. Extend these with inherited acceptance, rather than
treating nine procedures as a complete campaign. No arbitrary destructive tests
on original worlds; storage cuts belong in owned disposable fixtures.

The existing unit/FakePlayer/operator-command runs are useful development
evidence, not these real-player results. V1 needs real GPU evidence on at least
two hardware categories; V2 needs at least two real clients. Scheduling follows
ADR-018; no SSH, remote, GPU, multiplayer or load session is launched here.
