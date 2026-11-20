#!/usr/bin/env bash
# Read-only Debian acceptance, apart from the explicitly requested new host JSON.
# Usage: bash ref_acceptance.sh PROVIDER PLAN EVIDENCE BACKUPS HOST_JSON RUN_JSON [REPO]
# RUN_JSON supplies docs/15 section 15's run fields. RCON_PORT defaults to 25575;
# set it to the effective server.properties value. Never pass client credentials.
set -u
export LC_ALL=C
if [[ $# -lt 6 || $# -gt 7 ]]; then
    printf 'Usage: %s PROVIDER PLAN EVIDENCE BACKUPS HOST_JSON RUN_JSON [REPO]\n' "$0" >&2
    exit 2
fi
provider=$1 plan=$2 evidence=$3 backups=$4 output=$5 run_json=$6
repo=${7:-"$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/../.." && pwd)"}
failed=0
report() { printf '%s | %s | %s\n' "$1" "$2" "$3"; [[ $1 != FAIL ]] || failed=1; }
if [[ $(id -u) == 0 ]]; then
    report FAIL normal-user 'uid=0; rerun as the ordinary campaign user; no host record written'
    exit 1
fi
report PASS normal-user "uid=$(id -u) user=$(id -un)"
if [[ -s $HOME/.ssh/authorized_keys ]]; then
    if command -v ssh-keygen >/dev/null 2>&1 && ssh-keygen -l -f "$HOME/.ssh/authorized_keys" >/dev/null 2>&1; then
        report PASS ssh-key-config "$HOME/.ssh/authorized_keys contains parseable public keys (configuration only)"
    else
        report MANUAL ssh-key-config 'Validate authorized_keys and effective SSH configuration with the administrator'
    fi
else
    report MANUAL ssh-key-config 'Check effective AuthorizedKeysFile/AuthorizedKeysCommand with the administrator'
fi
report MANUAL ssh-key-login 'From a second terminal: ssh -o PreferredAuthentications=publickey -o PasswordAuthentication=no USER@HOST'
java_version=$(java -version 2>&1)
if [[ $java_version =~ version\ \"17\. ]]; then
    report PASS java17 "${java_version%%$'\n'*}"
else
    report FAIL java17 "${java_version%%$'\n'*}"
fi
if [[ -f $repo/gradlew && -f $repo/gradle/wrapper/gradle-wrapper.jar ]]; then
    report PASS wrapper-files "$repo/gradlew and wrapper JAR exist"
else
    report FAIL wrapper-files 'Gradle wrapper files are missing'
fi
report MANUAL wrapper-execution "cd '$repo' && ./gradlew --offline --version (under the campaign schedule)"
swap_kib=$(awk '/^SwapTotal:/ {print $2}' /proc/meminfo)
if [[ $swap_kib =~ ^[0-9]+$ ]] && (( swap_kib >= 8388608 )); then
    report PASS swap "SwapTotal=${swap_kib} KiB"
else
    report MANUAL swap "SwapTotal=${swap_kib:-unknown} KiB; record and review equivalent OOM protection, or provision 8 GiB swap"
fi
if command -v tmux >/dev/null 2>&1; then
    report PASS tmux "$(tmux -V)"
else
    report FAIL tmux 'tmux not found'
fi
report MANUAL tmux-resume 'tmux new -s arce-check; detach with Ctrl-b d; reconnect SSH; tmux attach -t arce-check'
linger=$(loginctl show-user "$(id -un)" -p Linger 2>&1)
if [[ $linger == Linger=yes || $linger == Linger=no ]]; then
    report PASS linger "$linger (policy recorded; neither value chosen automatically)"
else
    report MANUAL linger "$linger; administrator must record the policy"
fi
for directory in "$evidence" "$backups"; do
    if [[ -d $directory ]]; then
        report PASS directory "$directory exists"
    else
        report FAIL directory "$directory is missing"
    fi
done
rcon=${RCON_PORT:-25575}
if [[ ! $rcon =~ ^[0-9]+$ ]] || (( rcon < 1 || rcon > 65535 )); then
    report FAIL ports 'RCON_PORT must be a valid port'
elif command -v ss >/dev/null 2>&1; then
    if listeners=$(ss -H -ltn 2>&1); then
        public=$(printf '%s\n' "$listeners" | awk -v rcon="$rcon" '
            {address=$4; port=address; sub(/^.*:/,"",port);
             if ((port>=5900 && port<=5999) || port==rcon) {
                 if (address !~ /^127\./ && address !~ /^\[::1\]:/ && address !~ /^::1:/) print address
             }}')
        if [[ -z $public ]]; then
            report PASS ports "ss -H -ltn: no non-loopback listener on VNC 5900-5999 or RCON $rcon"
        else
            report FAIL ports "non-loopback listeners: $public"
        fi
    else
        report MANUAL ports "ss failed: $listeners"
    fi
else
    report MANUAL ports 'ss missing; inspect TCP listeners and firewall rules'
fi
report MANUAL effective-ports 'Check server.properties rcon.port and VNC configuration; rerun with RCON_PORT=<effective port> if different'
report MANUAL build-test "cd '$repo' && ./gradlew --offline clean build test (machine lock required)"
report MANUAL gametest "cd '$repo' && ./gradlew --offline runGameTestServer (machine lock required)"
report MANUAL server-restart 'Under the campaign lock: bash run.sh nogui; stop; bash run.sh nogui; compare saved state and logs'
report MANUAL client-tunnel 'ssh -N -L 25565:127.0.0.1:25565 USER@HOST; connect the local client to localhost:25565'
report MANUAL visual-level 'Record Xvfb/LLVMpipe as V0 only; real-GPU V1/V2 evidence is separate'
report MANUAL runner-privilege 'Administrator: systemctl list-unit-files "actions.runner*"; inspect service users, privileges and public-PR policy'
if command -v python3 >/dev/null 2>&1; then
    if python3 -B "$repo/scripts/run_campaign_observer.py" host "$output" --provider "$provider" --plan "$plan" \
        --disk "$evidence" --run-json "$run_json"; then
        report PASS host-record "$output (measured host plus supplied run fields)"
    else
        report FAIL host-record 'Host record could not be written; preserve the error above'
    fi
else
    report FAIL host-record 'python3 missing; no installation attempted'
fi
report MANUAL remote-dev-ready 'Read all MANUAL evidence before an owner decides readiness; this script does not mark a Gate'
exit "$failed"
