#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--help" ]]; then
  echo 'Usage: VNC_PASSWORD_FILE=<private password file> bash scripts/start-xvfb-visual-smoke.sh'
  echo 'Run in tmux. The foreground launcher stops only its own children on exit.'
  echo 'Overrides: DISPLAY_NUM, RESOLUTION, VNC_PORT, ARCE_LOG_DIR, LP_NUM_THREADS.'
  exit 0
fi
if [[ $# -ne 0 || "$(id -u)" -eq 0 ]]; then
  echo 'Run without arguments as a non-root development user; see --help.' >&2
  exit 2
fi
DISPLAY_NUM="${DISPLAY_NUM:-99}"
RESOLUTION="${RESOLUTION:-1920x1080x24}"
VNC_PORT="${VNC_PORT:-5900}"
LP_NUM_THREADS="${LP_NUM_THREADS:-2}"
if [[ ! "$DISPLAY_NUM" =~ ^[1-9][0-9]{0,2}$ ||
      ! "$RESOLUTION" =~ ^[1-9][0-9]{2,3}x[1-9][0-9]{2,3}x24$ ||
      ! "$VNC_PORT" =~ ^[1-9][0-9]{3,4}$ ||
      ! "$LP_NUM_THREADS" =~ ^[1-8]$ ]]; then
  echo 'Invalid display, resolution, port or software-renderer thread limit.' >&2
  exit 2
fi
if (( VNC_PORT < 1024 || VNC_PORT > 65535 )); then
  echo 'VNC_PORT must be an unprivileged port (1024-65535).' >&2
  exit 2
fi
for cmd in Xvfb fluxbox x11vnc glxinfo xauth mcookie mktemp; do
  command -v "$cmd" >/dev/null 2>&1 || { echo "Missing command: $cmd" >&2; exit 2; }
done
if [[ ! -f "${VNC_PASSWORD_FILE:-}" || ! -r "$VNC_PASSWORD_FILE" ]]; then
  echo 'Set VNC_PASSWORD_FILE to a private file created using x11vnc -storepasswd.' >&2
  exit 2
fi
if [[ -e "/tmp/.X${DISPLAY_NUM}-lock" || -e "/tmp/.X11-unix/X${DISPLAY_NUM}" ]]; then
  echo "Display :$DISPLAY_NUM is occupied; do not reuse or stop another session." >&2
  exit 2
fi
umask 077
LOG_DIR="${ARCE_LOG_DIR:-/srv/arce/logs}"
mkdir -p "$LOG_DIR"
RUN_DIR="$(mktemp -d "$LOG_DIR/visual-${DISPLAY_NUM}.XXXXXX")"
export DISPLAY=":$DISPLAY_NUM"
export XAUTHORITY="$RUN_DIR/Xauthority"
export LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS
children=()
cleanup() {
  local result=$?
  trap - EXIT
  for pid in "${children[@]}"; do kill "$pid" 2>/dev/null || :; done
  for pid in "${children[@]}"; do wait "$pid" 2>/dev/null || :; done
  rm -f -- "$XAUTHORITY"
  exit "$result"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
touch "$XAUTHORITY"
xauth -f "$XAUTHORITY" add "$DISPLAY" . "$(mcookie)"
Xvfb "$DISPLAY" -screen 0 "$RESOLUTION" -nolisten tcp -auth "$XAUTHORITY" \
  +extension GLX +render -noreset >"$RUN_DIR/xvfb.txt" 2>&1 &
children+=("$!")
ready=false
for attempt in {1..10}; do
  kill -0 "${children[0]}" 2>/dev/null || { echo "Xvfb failed; see $RUN_DIR/xvfb.txt" >&2; exit 1; }
  if glxinfo -B >"$RUN_DIR/renderer.txt" 2>&1; then ready=true; break; fi
  sleep 1
done
if [[ "$ready" != true ]] || ! grep -qi llvmpipe "$RUN_DIR/renderer.txt"; then
  echo "Software renderer was not verified; see $RUN_DIR/renderer.txt" >&2
  exit 1
fi
fluxbox >"$RUN_DIR/fluxbox.txt" 2>&1 &
children+=("$!")
x11vnc -norc -display "$DISPLAY" -auth "$XAUTHORITY" -localhost -forever -shared \
  -rfbauth "$VNC_PASSWORD_FILE" -rfbport "$VNC_PORT" >"$RUN_DIR/x11vnc.txt" 2>&1 &
children+=("$!")
sleep 1
for pid in "${children[@]}"; do
  kill -0 "$pid" 2>/dev/null || { echo "Visual stack failed; see $RUN_DIR" >&2; exit 1; }
done
cat "$RUN_DIR/renderer.txt"
printf 'V0 software smoke only; not a real-GPU Gate. Logs: %s\n' "$RUN_DIR"
printf 'Tunnel: ssh -N -L %s:127.0.0.1:%s arcdev@SERVER_IP\n' "$VNC_PORT" "$VNC_PORT"
printf 'Client shell: export DISPLAY=%q XAUTHORITY=%q LIBGL_ALWAYS_SOFTWARE=1 GALLIUM_DRIVER=llvmpipe LP_NUM_THREADS=%q\n' "$DISPLAY" "$XAUTHORITY" "$LP_NUM_THREADS"
echo 'Then run ./gradlew runClient --no-daemon --max-workers=1 in the selected worktree.'
echo 'Keep this foreground launcher running in tmux; Ctrl+C stops this stack.'
wait -n "${children[@]}"
echo "A visual-stack process stopped; see $RUN_DIR" >&2
exit 1
