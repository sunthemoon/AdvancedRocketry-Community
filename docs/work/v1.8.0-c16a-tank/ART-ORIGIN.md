# Pressurized tank original art origin (recorded before generation)

Status: NEW. Author: Advanced Rocketry: Community Edition contributors.
License: repository MIT. No upstream, Minecraft, Forge, LibVulpes, downloaded or
AI-generated bitmap/model input is used. ADR-064 section 12 requires original
C16 art; the legacy tank appearance is not copied or transformed.

Planned original deterministic Java provider `V180TankArt` draws three 16x16
RGBA faces from integer geometry: brushed pale steel housing, dark graphite
top rim, copper transfer couplings and a narrow blue side gauge. Textures:

- `assets/advancedrocketrycommunity/textures/block/pressurized_tank_side.png`
- `assets/advancedrocketrycommunity/textures/block/pressurized_tank_top.png`
- `assets/advancedrocketrycommunity/textures/block/pressurized_tank_bottom.png`

The original registry-independent JSON provider `V180TankData` will reference
these files from a cube model and item model, and generate blockstate, loot,
crafting and tank tags. Provider code and its deterministic tests are owned by
this task. Generated resource writes are reserved for root's integrated DataGen
run. This origin receipt is not a provenance acceptance or a visual Gate result.
Root will add central asset-plan/provenance entries before generating/publishing,
and bind actual output hashes plus independent/visual evidence afterward.
