# Planetary presentation — manual cases

Status: **NOT EXECUTED**. These cases require the actual packaged development
artifact, recorded client/driver/Forge/resource-pack identities and screenshots
or audio observations. Headless tests cannot satisfy them. Full V1/V2 execution
remains scheduled under ADR-018, after original machinery/dimensions are built.

| ID | Setup and actions | Observable acceptance |
|---|---|---|
| SKY-V1-01 | Visit Earth, Moon, Mars, Venus and a space station; inspect midday/night where the type permits | Earth unchanged; airless star fields; different dusty and dense planetary skies/fog/sun; no missing texture or unexpected clouds |
| SKY-V1-02 | Travel rapidly between bodies, disconnect, join a different world | No prior-body color/sound, stale world or GPU-buffer leak; unavailable catalog uses fallback |
| SKY-V1-03 | Look through water/lava/powder snow and apply blindness/darkness | Vanilla visibility restrictions remain; sky does not draw over them; ordinary fog returns after effects clear |
| SKY-V1-04 | Change render distance and enter caves/covered rooms | Fog never reveals beyond ordinary visible terrain; additional planetary ambience stops under cover |
| SKY-V1-05 | Stay exposed on Mars/Venus, change ambient volume, enter cover, pause, travel and logout | At most one quiet non-looping added sound; ambient slider works; no repeated restart burst or lingering sound; Moon/space add none |
| SKY-V1-06 | Reload valid custom profile pack, then invalid schema/oversized file, then remove pack | Valid color/profile replacement; invalid candidate retains prior map with one bounded diagnostic; removal restores base resources; GPU buffers recreated without persistent growth |
| SKY-V1-07 | Supply unknown visual_profile or duplicate Level mappings in allowed test data | Conservative fallback, not a different body's presentation; authoritative server environment unchanged |
| SKY-V1-08 | Existing world upgraded from pre-sky artifact, then clean restart | Same Moon/Space identity, positions, terrain/time/light settings and inventories; new effects only where no higher-priority dimension-type override exists |
| SKY-V1-09 | Repeat on two actual GPU families at GUI scales 2/3/4, windowed/fullscreen | No OpenGL errors, star flicker, sky holes, severe horizon seam or sun artifacts; compare frame times/memory to recorded baseline |
| SKY-V2-01 | Two real clients visit different planets and reconnect independently | Each client's presentation matches its own synchronized Level catalog; one client's reload/travel does not alter the other or server rules |

For every case retain tester/date, exact steps, expected/actual result, artifact
SHA256, evidence paths and limitations. Do not prefill PASS.
