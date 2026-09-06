# Visual Validation Report — `<version>`

## 1. Candidate identity

```yaml
version: ""
git_commit: ""
jar_sha256: ""
forge: ""
java: ""
reporter: ""
date_utc: ""
```

## 2. Environment

```yaml
environment_class: V0 | V1 | V2
os: ""
cpu: ""
gpu: ""
gpu_driver: ""
resolution: ""
gui_scale: ""
renderer: ""
mods_and_versions: []
software_renderer: null
counts_for_release_visual_gate: false
```

`V0`（Xvfb/LLVMpipe）必须填写 `counts_for_release_visual_gate: false`。

## 3. Baseline

| Scene | Average FPS | 1% low | Client memory | Notes |
|---|---:|---:|---:|---|
| Forge empty scene | | | | |
| ARCE Earth base | | | | |

## 4. Rocket rendering

| Rocket size | First appearance stall | Average FPS | 1% low | Cache size | Pass |
|---:|---:|---:|---:|---:|---|
| 64 blocks | | | | | |
| 512 blocks | | | | | |
| 2048 blocks | | | | | |

## 5. Required scenes

- [ ] Electrolyzer GUI
- [ ] Oxygen/atmosphere feedback
- [ ] Rocket assembly
- [ ] Fuel/loading UI
- [ ] Launch and passenger
- [ ] Earth sky
- [ ] Space
- [ ] Moon sky
- [ ] Return transfer
- [ ] Station
- [ ] Satellite/research
- [ ] Resource reload
- [ ] Reconnect

## 6. Multiplayer sync

| Step | Client A | Client B | Server truth | Pass |
|---|---|---|---|---|
| Board rocket | | | | |
| Launch | | | | |
| Disconnect/reconnect | | | | |
| Dimension transfer | | | | |

## 7. Errors

```text
OpenGL errors:
Missing textures/models:
Client exceptions:
Visual desync:
```

## 8. Evidence

```text
screenshots:
videos:
client logs:
server logs:
performance captures:
```

## 9. Result

```yaml
result: NOT_RUN
release_visual_gate: NOT_STARTED
blocking_issues: []
reviewed_by: ""
```
