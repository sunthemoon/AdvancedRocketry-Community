#!/usr/bin/env bash
set -euo pipefail
if [[ "${1:-}" == "--help" ]]; then
  echo 'Usage: ARCE_ROOT=/srv/arce bash scripts/tmux-arce-layout.sh [session]'
  exit 0
fi
if [[ $# -gt 1 || ! "${1:-arce-control}" =~ ^[A-Za-z0-9_-]{1,64}$ ]]; then
  echo 'Session must contain 1-64 letters, digits, underscores or hyphens.' >&2
  exit 2
fi
if [[ "$(id -u)" -eq 0 ]]; then
  echo 'Do not run the development session as root.' >&2
  exit 2
fi
SESSION="${1:-arce-control}"
ROOT="${ARCE_ROOT:-/srv/arce}"
command -v tmux >/dev/null 2>&1 || { echo 'Missing command: tmux' >&2; exit 2; }

if tmux has-session -t "=$SESSION" 2>/dev/null; then
  exec tmux attach -t "=$SESSION"
fi

for directory in repo worktrees evidence logs; do
  [[ -d "$ROOT/$directory" ]] || { echo "Missing directory: $ROOT/$directory" >&2; exit 2; }
done

tmux new-session -d -s "$SESSION" -n control -c "$ROOT/repo"
tmux new-window -t "$SESSION" -n worker-a -c "$ROOT/worktrees"
tmux new-window -t "$SESSION" -n reviewer -c "$ROOT/repo"
tmux new-window -t "$SESSION" -n build -c "$ROOT/repo"
tmux new-window -t "$SESSION" -n server -c "$ROOT/evidence"
tmux new-window -t "$SESSION" -n monitor -c "$ROOT/logs"
tmux new-window -t "$SESSION" -n visual -c "$ROOT/evidence"
tmux send-keys -t "$SESSION:monitor" 'watch -n 5 "uptime; free -h; swapon --show; df -h ."' C-m
tmux select-window -t "$SESSION:control"
exec tmux attach -t "$SESSION"
