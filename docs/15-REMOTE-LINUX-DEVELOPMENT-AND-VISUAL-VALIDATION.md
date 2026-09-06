# Debian 12 远程开发、持续任务与可视化验证规约

## 1. 结论

Debian 12 服务器并不是“无法做可视化验证”，而是应把验证分成不同可信等级：

| 等级 | 执行位置 | 能证明什么 | 能否放行正式视觉 Gate |
|---|---|---|---:|
| H0 | Linux 无图形环境 | 编译、单测、DataGen、GameTest、专服 | 否 |
| H1 | Xvfb + Mesa LLVMpipe | 客户端能启动、没有明显渲染崩溃、资源基本可见 | 否 |
| H2 | 服务器专服 + 本地真实客户端 | 联机、GUI、玩法、真实 GPU 渲染和多人行为 | **是** |
| H3 | 两台或更多真实客户端 | 乘客、断线、权限、同步、并发交互 | **是** |

H0 对应质量预算中的 A0/A1/S1/S2；H1 对应 V0；H2 的真实 GPU 部分对应
V1；H3 对应 V2。环境只决定可提供哪类证据，不表示任何 Gate 已通过。

推荐长期架构：

```text
Netcup Debian 12
  ├─ Codex CLI / Git worktree
  ├─ Gradle / Java unit tests
  ├─ DataGen / GameTest
  ├─ 打包 Dedicated Server
  ├─ 迁移、强制停服恢复、长时压力测试
  └─ 可选 Xvfb 软件渲染冒烟

本地 Windows 电脑
  ├─ Forge 真实客户端
  ├─ 实体 GPU
  ├─ GUI/天空/OBJ/粒子/大火箭渲染验收
  └─ 第二真实客户端或协作者客户端
```

服务器应成为**权威构建与服务端验证节点**，本地电脑应成为**真实视觉与交互验收节点**。

---

## 2. 参考服务器与实测要求

下列是容量规划用的参考配置，不是仓库已审计宿主机或本机实测结果：

```text
OS: Debian 12
CPU: 4 vCPU
RAM: 8 GiB
Swap: measure before configuration
Disk: measure free space and I/O latency
Visible GPU: measure; software smoke assumes no hardware renderer
```

### 适合

- 单个 Codex 主任务；
- 1–2 个轻量只读/文档分析会话；
- 单个 Gradle 重构建；
- Java 单元测试和资源校验；
- `runData`；
- `runGameTestServer`；
- 单个 2–4 GiB 堆的 Forge 专服；
- 单节点迁移测试、崩溃恢复和 4–12 小时 soak；
- Git worktree 隔离开发；
- Xvfb + LLVMpipe 客户端启动冒烟。

### 不适合

- 多个 `runClient` 同时运行；
- 两个以上 Gradle 重构建同时执行；
- 同时运行大型专服、软件渲染客户端和多个 Codex 写入会话；
- 把软件渲染 FPS 当作真实玩家性能；
- 大量并行 worktree 各自下载一套 Gradle/Minecraft 缓存；
- 在公网开放未保护的 VNC、RCON 或测试服务器管理端口。

### 推荐并发上限

```text
普通开发模式：
  1 个 Codex 写入会话
  + 1 个轻量审核/分析会话
  + 不超过 1 个 Gradle heavy job

专服测试模式：
  1 个 Dedicated Server（Xmx 3G–4G）
  + 监控/日志
  + 不运行 runClient

软件视觉冒烟模式：
  1 个 runClient（Xmx 3G，LLVMpipe 线程 2）
  + Xvfb/x11vnc
  + 暂停其他重构建和大型专服
```

这不是永久性能结论。首次安装后应记录宿主机 CPU steal、磁盘延迟、Gradle 构建时间和服务器 MSPT，再通过 ADR 调整。

---

## 3. 初始安全原则

### 3.1 不以 root 开发

`root` 只执行系统安装和必要配置。创建普通用户：

```bash
adduser arcdev
install -d -o arcdev -g arcdev /srv/arce
install -d -o arcdev -g arcdev /srv/arce/{repo,worktrees,evidence,backups,logs}
```

Codex、Git、Gradle、Minecraft server、Xvfb 和 tmux 全部由 `arcdev` 运行。

### 3.2 先验证密钥登录，再调整 SSH

- 使用 SSH key；
- 保持当前会话不退出；
- 在第二个终端验证 `arcdev` 能登录；
- 然后才考虑关闭 root 密码登录；
- 不把 VNC 5900、RCON、调试端口直接暴露公网；
- 游戏测试优先使用 SSH 本地转发。

### 3.3 公共仓库不要直接挂高权限自托管 GitHub Runner

公共仓库或接收不可信 PR 的仓库，可能被恶意 fork PR 借自托管 runner 读取凭据、修改宿主机或横向访问。默认策略：

- GitHub Actions 继续使用 GitHub-hosted runner；
- Netcup 服务器由维护者手动拉取受信任 commit 做长时测试；
- 不在服务器保存可写整个 GitHub 账户的长期 token；
- 确需 self-hosted runner 时，必须是临时、隔离、无长期秘密、只接受受信任分支的 runner，并单独形成安全 ADR。

---

## 4. Debian 12 基础安装

以 root 执行：

```bash
apt update
apt install -y \
  git curl ca-certificates unzip zip rsync jq \
  tmux htop lsof procps psmisc ripgrep fd-find \
  python3 python3-venv python3-pip \
  openjdk-17-jdk
```

验证：

```bash
java -version
javac -version
git --version
tmux -V
free -h
df -h
```

应看到 Java 17。仓库中的 wrapper 才是 Gradle 版本来源，不安装系统 Gradle：

```bash
cd /srv/arce/repo
./gradlew --version
```

---

## 5. 补充 Swap

先执行 `swapon --show`、`free -h` 和磁盘检查。只有管理员确认无可用
Swap、空间足够且明确授权时，才考虑 **8 GiB 应急 Swap**。不得覆盖已有
swapfile、fstab 或系统调优文件；本仓库诊断脚本不会执行这些配置：

```bash
test ! -e /swapfile || { echo 'Existing /swapfile; stop and inspect.' >&2; exit 1; }
fallocate -l 8G /swapfile
chmod 600 /swapfile
mkswap /swapfile
swapon /swapfile
```

确认：

```bash
swapon --show
free -h
```

持久化前先避免重复行：

```bash
grep -q '^/swapfile ' /etc/fstab || \
  echo '/swapfile none swap sw 0 0' >> /etc/fstab
```

降低主动换页：

```bash
test ! -e /etc/sysctl.d/99-arce-development.conf || { echo 'Existing sysctl file; stop and inspect.' >&2; exit 1; }
cat >/etc/sysctl.d/99-arce-development.conf <<'EOF'
vm.swappiness=10
vm.vfs_cache_pressure=50
EOF
sysctl --system
```

Swap 是防止偶发 OOM 的安全垫，不是内存扩容。若常态使用数 GiB Swap，应减少并行任务或增加内存。

---

## 6. Git 与目录布局

```text
/srv/arce/
├─ repo/                  # 主 checkout，仅总控/集成
├─ worktrees/             # 每个任务独立 worktree
├─ evidence/              # 大日志、视频、性能采样；必要内容再归档进 Git
├─ backups/               # 世界与候选版备份
└─ logs/                  # tmux/systemd/服务器临时日志
```

首次克隆：

```bash
sudo -u arcdev -H bash
cd /srv/arce
git clone git@github.com:sunthemoon/AdvancedRocketry-Community.git repo
cd repo
git status
git remote -v
```

worktree 例子：

```bash
cd /srv/arce/repo
git fetch --all --prune
git switch main
git pull --ff-only

git worktree add \
  /srv/arce/worktrees/AR-1101-destination-id \
  -b feat/AR-1101-destination-id \
  main
```

一个写入型 Codex 窗口只打开一个 worktree。

---

## 7. Codex CLI 与断线恢复

安装和升级使用官方 Linux 安装器：

```bash
curl -fsSL https://chatgpt.com/codex/install.sh | sh
```

重新登录 shell 后验证：

```bash
codex --version
```

进入目标 worktree 后启动：

```bash
cd /srv/arce/worktrees/AR-1101-destination-id
codex
```

Codex 会话与 tmux 是两种不同的恢复层（命令以本机帮助和
[官方 CLI 文档](https://developers.openai.com/codex/cli)为准）：

| 情况 | 恢复方法 |
|---|---|
| 仅 SSH 断开 | `tmux attach -t <session>`，进程仍在 |
| Codex TUI 已退出 | `codex resume` |
| 恢复最近一次聊天 | `codex resume --last`（以本机 CLI 实际帮助为准） |
| 服务器重启 | 重新挂载目录、检查 Git/PROGRESS，再 `codex resume` |
| worktree 被移动/删除 | 不盲目 resume；先恢复原路径或创建新会话读取 HANDOFF |

长期任务的真正恢复点必须是：

```text
Git commit
+ docs/work/<TASK>/PROGRESS.md
+ HANDOFF.md
+ 测试日志
```

不能只依赖聊天历史或 tmux 屏幕。

修改 Codex 配置或行为规约后，验证新行为需完整退出 Codex，并启动新的
非 resume 会话；恢复旧会话不用于证明新配置生效。

---

## 8. tmux 推荐布局

创建或附着同名会话：

```bash
tmux new -As arce-control
```

推荐窗口：

```text
0 control      总控 Codex / 状态文档
1 worker-a     当前实现 worktree
2 reviewer     只读审核或测试
3 build        Gradle 命令和日志
4 server       Dedicated Server
5 monitor      htop / free / iostat / journal
6 visual       可选 Xvfb / x11vnc / runClient
```

常用键：

```text
Ctrl+B, D      detach
Ctrl+B, C      新窗口
Ctrl+B, ,      重命名窗口
Ctrl+B, 0..9   切换窗口
Ctrl+B, [      查看历史输出
```

建议 `~/.tmux.conf`：

```tmux
set -g history-limit 100000
set -g remain-on-exit on
set -g mouse on
set -g status-interval 5
set -g set-clipboard on
setw -g monitor-activity on
set -g visual-activity off
```

重载：

```bash
tmux source-file ~/.tmux.conf
```

### tmux 能保证与不能保证的事情

能保证：

- SSH 超时或本地网络断开后会话继续；
- 重新 attach 后看到原 TUI 和输出；
- 多窗口集中管理。

不能保证：

- VPS 重启后自动恢复内存中的会话；
- OOM、内核崩溃或进程自身崩溃后继续；
- 没有 Git/状态文档时自动恢复任务语义。

检查 systemd 用户 linger：

```bash
loginctl show-user arcdev -p Linger
sudo loginctl enable-linger arcdev
```

启用 linger 后，用户服务不必依赖持续登录会话。对于需要重启自动恢复的**确定性专服或测试脚本**，使用 systemd user service；Codex 交互会话仍以 tmux + Git/PROGRESS 为主。

---

## 9. Gradle 资源限制

仓库当前可能启用 3 GiB Gradle JVM 和并行任务。该服务器建议先使用命令级约束，避免为服务器专用参数污染仓库：

```bash
./gradlew clean test --no-daemon --max-workers=2
./gradlew runData --no-daemon --max-workers=2
./gradlew runGameTestServer --no-daemon --max-workers=2
```

也可在 `~/.gradle/gradle.properties` 设置仅当前用户生效的本地值：

```properties
org.gradle.workers.max=2
org.gradle.daemon=false
org.gradle.caching=true
```

不要同时启动两个 ForgeGradle 重构建。出现 OOM 或长期 Swap 时，先降低到：

```bash
--max-workers=1
```

每次性能证据都记录：

```bash
set -o pipefail
/usr/bin/time -v ./gradlew clean build --no-daemon --max-workers=2 \
  |& tee /srv/arce/evidence/gradle-build-$(date -u +%Y%m%dT%H%M%SZ).log
```

---

## 10. 权威的远程开发测试流程

每个任务按以下顺序：

```text
静态检查
  ↓
Pure Java tests
  ↓
Data/asset validation
  ↓
DataGen + git diff
  ↓
Forge GameTests
  ↓
打包 Dedicated Server
  ↓
保存/重启/强制停止测试
  ↓
本地真实客户端联机
  ↓
人工视觉和多人 Gate
```

基础命令以仓库实际脚本为准，常见组合：

```bash
./gradlew clean build --no-daemon --max-workers=2
./gradlew test --no-daemon --max-workers=2
./gradlew runData --no-daemon --max-workers=2
git diff --exit-code
./gradlew runGameTestServer --no-daemon --max-workers=2
```

成功日志和失败日志都归档；不得只粘贴最后一行 `BUILD SUCCESSFUL`。

---

## 11. 最推荐的可视化方式：服务器跑专服，本地电脑跑客户端

这是 `v1.0+` 最可信、开销最低的方案。

### 服务器端

将测试专服只绑定到 loopback：

```properties
server-ip=127.0.0.1
server-port=25565
online-mode=true
white-list=true
```

### 本地 Windows/Linux/macOS

建立 SSH 隧道：

```bash
ssh -N \
  -L 25565:127.0.0.1:25565 \
  arcdev@SERVER_IP
```

Xshell 也可在“隧道/端口转发”界面建立相同的本地转发。

本地 Minecraft 客户端连接：

```text
localhost:25565
```

优势：

- 服务端代码、世界、迁移和恢复在 Debian 上验证；
- 客户端使用真实显卡；
- 不公开测试端口；
- 可运行两个本地实例或邀请第二位测试者；
- 可用 OBS、RenderDoc（必要时）和正常截图工具留证。

必须保证本地客户端与服务器：

- commit/build 相同；
- mod JAR hash 相同；
- Forge 版本在测试矩阵中；
- 配置和数据包已记录；
- 资源包状态一致。

---

## 12. 可选的软件渲染视觉冒烟

### 12.1 用途边界

Xvfb 提供虚拟 X 显示，Mesa LLVMpipe 使用 CPU 做 OpenGL 软件光栅化。它适合检查：

- `runClient` 能否进入主菜单或世界；
- 客户端专属类是否崩溃；
- 模型/纹理是否明显缺失；
- GUI 是否能打开；
- 基础天空、方块和实体是否可见；
- 自动截图是否能生成。

它不能证明：

- NVIDIA/AMD/Intel 真实驱动兼容；
- FPS 或 1% low；
- 大火箭真实 GPU 渲染成本；
- 显存占用；
- 特定驱动的 shader/OBJ/缓冲区问题；
- 玩家主观可读性与操作体验。

### 12.2 安装

```bash
apt install -y \
  xvfb x11vnc fluxbox xauth \
  mesa-utils libgl1-mesa-dri libglx-mesa0 \
  ffmpeg xdotool \
  libx11-6 libxext6 libxi6 libxrender1 libxtst6 \
  libxrandr2 libxinerama1 libxcursor1 libasound2 libfontconfig1
```

### 12.3 验证显示与渲染器

```bash
bash scripts/start-xvfb-visual-smoke.sh --help
```

实际渲染器必须记录。辅助脚本在私有 Xauthority 下启动 Xvfb，并使用
`-nolisten tcp` 关闭 X TCP 监听，不使用禁用访问控制的 `-ac`。
参数含义见 [X server 手册](https://xorg.freedesktop.org/archive/X11R7.5/doc/man/man1/Xserver.1.html)。
若未确认 llvmpipe，脚本失败，不声称软件环境已可用。

### 12.4 启动可交互 VNC

```bash
# 由普通开发用户交互设置密码；不在命令行或仓库保存明文密码。
install -d -m 700 "$HOME/.vnc"
x11vnc -storepasswd "$HOME/.vnc/arce.pass"
chmod 600 "$HOME/.vnc/arce.pass"
VNC_PASSWORD_FILE="$HOME/.vnc/arce.pass" bash scripts/start-xvfb-visual-smoke.sh
```

本地建立 VNC 隧道：

```bash
ssh -N \
  -L 5900:127.0.0.1:5900 \
  arcdev@SERVER_IP
```

VNC 客户端连接：

```text
localhost:5900
```

不要让 `x11vnc` 监听公网地址。脚本在 tmux 的 visual 窗口前台运行，使用
密码和 loopback 监听；退出时只停止本次启动的子进程，不复用或终止别人的
Xvfb/fluxbox/VNC。客户端 shell 采用脚本输出的 DISPLAY/XAUTHORITY，
不能只设置 DISPLAY 就绕过显示认证。

启动器忽略用户级 x11vnc 配置，避免继承额外监听或启动选项。密码文件和
loopback 选项见 [Debian x11vnc 手册](https://manpages.debian.org/bookworm/x11vnc/x11vnc.1.en.html)。

### 12.5 启动 Forge 客户端

```bash
cd /srv/arce/worktrees/<task>
# 先应用启动器输出的 DISPLAY 和私有 XAUTHORITY 环境。
DISPLAY=:99 \
LIBGL_ALWAYS_SOFTWARE=1 \
GALLIUM_DRIVER=llvmpipe \
LP_NUM_THREADS=2 \
./gradlew runClient --no-daemon --max-workers=1
```

软件客户端运行时不要同时执行大型 Gradle 构建。8 GiB 机器上可先使用客户端 `Xmx3G`，若仓库 run config 更高则通过本地覆盖调整，不直接降低全仓库默认值。

### 12.6 截图和短视频

截图：

```bash
DISPLAY=:99 ffmpeg \
  -f x11grab -video_size 1920x1080 -i :99 \
  -frames:v 1 \
  /srv/arce/evidence/software-visual-$(date -u +%Y%m%dT%H%M%SZ).png
```

30 秒视频：

```bash
DISPLAY=:99 ffmpeg \
  -f x11grab -framerate 20 -video_size 1920x1080 -i :99 \
  -t 30 -c:v libx264 -preset veryfast \
  /srv/arce/evidence/software-visual-$(date -u +%Y%m%dT%H%M%SZ).mp4
```

报告中必须标记：

```yaml
visual_environment: SOFTWARE_RENDERER
renderer: llvmpipe
counts_for_release_visual_gate: false
```

---

## 13. `v1.0` 真实视觉 Gate

最终 stable 版本至少执行：

### 客户端环境

- [ ] 一台真实 GPU 电脑；
- [ ] 推荐再有第二种 GPU 厂商或集显环境；
- [ ] 两个真实 Minecraft 客户端完成多人流程；
- [ ] GUI scale 至少测试 2、3、4；
- [ ] 窗口模式和全屏至少各一次；
- [ ] Forge baseline 与 compatibility lane 至少完成关键冒烟；
- [ ] JEI 有/无各一次；
- [ ] 记录 OS、GPU、驱动、分辨率和 JVM。

### 场景

- [ ] 电解机完整操作；
- [ ] 氧气房间从密闭到破坏再恢复；
- [ ] 宇航服/氧气反馈；
- [ ] 64、512、2048 方块火箭可见性；
- [ ] 组装、加油、登乘、发射；
- [ ] 地球天空、太空、月球天空；
- [ ] 跨维度后火箭和乘客位置；
- [ ] 返回地球；
- [ ] 空间站权限和着陆；
- [ ] 卫星/研究 GUI；
- [ ] 第二玩家掉线重连；
- [ ] 服务端重启后客户端重新进入。

软件渲染完成同样场景可以增加信心，但不能替代以上 Gate。

---

## 14. 专服长期运行与 systemd

交互开发由 tmux 管理。确定性的长期 server/soak 可以使用 systemd user service。

启用 linger：

```bash
sudo loginctl enable-linger arcdev
```

示例用户 unit：

```ini
# ~/.config/systemd/user/arce-test-server.service
[Unit]
Description=ARCE packaged test server
After=network-online.target

[Service]
Type=simple
WorkingDirectory=/srv/arce/evidence/server-current
ExecStart=/usr/bin/bash /srv/arce/evidence/server-current/run.sh nogui
Restart=no
TimeoutStopSec=120
SuccessExitStatus=0 143

[Install]
WantedBy=default.target
```

先核对 Forge 安装器生成的 `run.sh`、参数文件和 `user_jvm_args.txt`，在其中
配置测试所需堆大小；不能假设存在单一启动 JAR。上面的 unit 只是管理员审阅
示例，不由诊断或视觉脚本安装。启用前：

```bash
systemctl --user daemon-reload
systemctl --user enable --now arce-test-server.service
systemctl --user status arce-test-server.service
journalctl --user -u arce-test-server.service -f
```

不把正在做迁移/强制停止测试的 server 设置为无限自动重启，否则会污染恢复阶段证据。

---

## 15. 监控与证据

### 主机采样

```bash
uptime
free -h
swapon --show
df -h
ps -eo pid,ppid,%cpu,%mem,rss,etime,cmd --sort=-%mem | head -30
journalctl -k --since '-1 hour' | grep -Ei 'oom|killed process|i/o error'
```

建议安装并使用：

```bash
apt install -y sysstat
mpstat -P ALL 5
pidstat -rud -p ALL 5
iostat -xz 5
```

### 每轮报告记录

```yaml
host:
  provider: <measured>
  plan: <measured>
  os: <measured>
  vcpu: <measured>
  memory_mib: <measured>
  swap_mib: <measured>
  disk_free_gib: <measured>
  cpu_steal_average: <measured>

run:
  git_commit: <sha>
  jar_sha256: <sha256>
  forge: <version>
  java: <version>
  command: <exact command>
  start_utc: <timestamp>
  end_utc: <timestamp>
  exit_code: <code>
```

---

## 16. 备份与灾难恢复

开始迁移、强制停服和候选版测试前，先正常停止服务器并确认进程已退出。
备份到新目录，包含配置和模组，不能用运行中的文件拷贝声称一致快照：

```bash
backup="$(mktemp -d /srv/arce/backups/server-XXXXXXXX)"
rsync -aH /srv/arce/evidence/server-current/ "$backup/"
```

至少保留：

- 原始输入世界；
- 首次升级后的世界；
- 每个迁移 schema 的 fixture；
- Critical 问题复现世界；
- 发布候选验证世界；
- 对应 JAR hash 和 commit。

不要只保存世界目录而不保存模组、配置、数据包和测试步骤。

---

## 17. 每日运行流程

```text
1. SSH 登录普通用户
2. tmux new -As arce-control
3. git fetch / 检查 worktree 状态
4. 读取 AGENTS、ACTIVE_CONSTRAINTS、TASK、PROGRESS
5. 确认 free/swap/disk
6. 运行 Codex 或测试
7. 每完成一个原子步骤更新 PROGRESS
8. 失败日志复制到 evidence
9. 形成 commit
10. 更新 HANDOFF
11. detach，而不是直接关闭活跃 shell
```

停工前检查：

```bash
tmux ls
git -C /srv/arce/repo worktree list
git -C /srv/arce/worktrees/<task> status --short
free -h
df -h
```

---

## 18. 服务器环境验收

服务器只有全部满足后才标记 `REMOTE_DEV_READY`：

- [ ] 普通用户可 SSH key 登录；
- [ ] root 不用于日常开发；
- [ ] Java 17 和 wrapper 正常；
- [ ] 8 GiB Swap 或等价 OOM 保护已确认；
- [ ] tmux 断开/恢复实测；
- [ ] `loginctl` linger 策略已记录；
- [ ] 仓库 build/test 能执行；
- [ ] `runGameTestServer` 能执行；
- [ ] 打包专服首次启动和重启通过；
- [ ] evidence/backups 目录存在；
- [ ] 没有公网暴露 VNC/RCON；
- [ ] 本地客户端通过 SSH 隧道连入专服；
- [ ] 软件渲染报告明确不计入正式视觉 Gate；
- [ ] 未给公共仓库安装高权限常驻 self-hosted runner。
