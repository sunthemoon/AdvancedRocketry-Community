# Remote Server Run Report — `<run-id>`

## Identity

```yaml
run_id: ""
version: ""
git_commit: ""
jar_sha256: ""
runner: ""
start_utc: ""
end_utc: ""
exit_code: null
```

## Host

```yaml
provider: ""
plan: ""
os: ""
kernel: ""
vcpu: null
memory_mib: null
swap_mib: ""
disk_free_gib: ""
java: ""
forge: ""
```

## Exact command

```bash
# paste exact command
```

## Workload

```yaml
players_real: null
players_simulated: null
rockets_active: null
largest_rocket_blocks: null
atmosphere_volumes: null
stations: null
satellite_tasks: null
duration_minutes: null
restart_count: null
forced_stop_count: null
```

## Results

| Metric | Average | P95 | P99 | Maximum |
|---|---:|---:|---:|---:|
| MSPT | | | | |
| RSS MiB | | | | |
| CPU % | | | | |
| Swap MiB | | | | |
| Disk latency | | | | |

## Integrity

- [ ] No OOM kill
- [ ] No watchdog
- [ ] No unexpected chunk tickets
- [ ] No duplication
- [ ] No permanent entity/player loss
- [ ] Clean shutdown or documented forced stop
- [ ] Restart recovery verified

## Logs and artifacts

```text
server log:
GC/JFR/profile:
host sampling:
world before:
world after:
checksums:
```

## Result

```yaml
result: NOT_RUN
blocking_issues: []
reviewed_by: ""
```
