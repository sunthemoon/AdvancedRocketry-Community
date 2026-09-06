#!/usr/bin/env bash
set -uo pipefail

if [[ "${1:-}" == "--help" ]]; then
  echo 'Usage: bash scripts/check-debian12-dev-host.sh'
  echo 'Read-only diagnostics; nonzero means failed checks, not release readiness.'
  exit 0
fi
if [[ $# -ne 0 ]]; then
  echo 'Unexpected arguments; use --help.' >&2
  exit 2
fi
failures=0

section() { printf '\n===== %s =====\n' "$1"; }
run() {
  printf '$ '; printf '%q ' "$@"; printf '\n'
  if "$@" 2>&1; then
    return 0
  else
    local code=$?
    printf 'CHECK_FAILED: exit=%s\n' "$code"
    failures=$((failures + 1))
  fi
}

section "identity"
run id
run whoami
run pwd

section "os and kernel"
run cat /etc/os-release
run uname -a

section "cpu"
run lscpu

section "memory and swap"
run free -h
run swapon --show

section "disk"
run df -h
run df -i

section "tools"
run java -version
run javac -version
run git --version
run tmux -V
run codex --version
run python3 --version

section "gpu visibility"
if command -v lspci >/dev/null 2>&1; then
  lspci -nnk | grep -A3 -Ei 'VGA|3D|Display' || true
else
  echo "lspci not installed"
fi

section "renderer if a display exists"
if command -v glxinfo >/dev/null 2>&1; then
  run glxinfo -B
else
  echo "glxinfo not installed"
fi

section "tmux sessions"
run tmux ls

section "linger"
if command -v loginctl >/dev/null 2>&1; then
  run loginctl show-user "$(id -un)" -p Linger
fi

section "listening ports"
run ss -lntup

section "recent kernel warnings"
if command -v journalctl >/dev/null 2>&1; then
  journalctl -k --since '-24 hours' 2>/dev/null | grep -Ei 'oom|killed process|i/o error|ext4.*error' || true
fi

section "git worktrees"
if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  run git status --short --branch
  run git worktree list
  if [[ -f ./gradlew ]]; then
    section "gradle wrapper"
    echo 'Wrapper present; not executed by this read-only host diagnostic.'
    echo 'Run ./gradlew --version as a separate trusted-code check.'
  fi
else
  echo "not currently inside a Git worktree"
fi

section "notes"
if [[ "$(id -u)" -eq 0 ]]; then
  echo "FAIL: running as root; use a dedicated development user."
  failures=$((failures + 1))
else
  echo "PASS: non-root user."
fi
if ! swapon --noheadings --show=NAME 2>/dev/null | grep -q .; then
  echo "WARN: no active swap; 8 GiB host is vulnerable to transient OOM under Forge/Codex concurrency."
fi
echo "This script is read-only and does not declare REMOTE_DEV_READY by itself."
java_version="$(java -version 2>&1)"
if [[ "$java_version" != *'version "17.'* ]]; then
  echo 'FAIL: Java 17 was not detected.'
  failures=$((failures + 1))
fi
printf 'Failed checks: %s\n' "$failures"
if (( failures > 0 )); then
  exit 1
fi
