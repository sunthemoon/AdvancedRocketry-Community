# TEST-REPORT — <version/build>

## Environment

```yaml
os:
cpu:
memory:
java:
minecraft:
forge:
mod:
commit:
```

## Automated command results

| Command | Exit | Duration | Report |
|---|---:|---:|---|
| `./gradlew clean build` | | | |
| `./gradlew test` | | | |
| `./gradlew runData` | | | |
| `git diff --exit-code` | | | |
| `./gradlew runGameTestServer` | | | |

## Tests

| ID | Layer | Result | Notes |
|---|---|---|---|
| | Unit | | |
| | Codec/NBT | | |
| | GameTest | | |
| | Dedicated | | |
| | Restart | | |
| | Security | | |
| | Performance | | |

## Failures and skips

Every failure/skip must have:

```text
test:
reason:
owner:
blocking:
follow-up:
```

## Log review

```text
project ERROR count:
project WARN count:
accepted warnings:
```

## Conclusion

```yaml
automated_gate: PASS|FAIL
blocking_issues: []
```
