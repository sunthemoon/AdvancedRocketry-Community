# Codex Prompt — Debian 12 远程开发节点只读审计

在任务指定的 Debian 12 服务器上执行；没有主机访问上下文时只准备测试单。
除非 TASK 明确授权，不修改系统配置。诊断脚本不执行 wrapper，不据此宣称
Gradle 已验证；wrapper、tmux 往返和客户端测试需要分别授权、执行与记录。

读取：

```text
docs/15-REMOTE-LINUX-DEVELOPMENT-AND-VISUAL-VALIDATION.md
docs/17-V1PLUS-QUALITY-BUDGETS.md
scripts/check-debian12-dev-host.sh
```

## 只读检查

- OS/kernel/virtualization/CPU；
- memory/swap/disk/inodes；
- CPU steal 和磁盘延迟；
- Java 17、Git、tmux、Codex CLI、wrapper；
- 当前用户是否 root；
- 目录 owner/permissions；
- tmux detach/attach；
- linger 状态；
- 活跃端口与公网暴露；
- Git worktree 状态；
- Gradle 缓存和并发设置；
- 是否安装高权限 self-hosted runner；
- 是否有可见 GPU；
- Xvfb/LLVMpipe 可用性（仅在已安装时检查）；
- evidence/backups/logs 目录；
- 最近 OOM/I/O error。

运行：

```bash
bash scripts/check-debian12-dev-host.sh
```

不得自动：

- 修改 SSH；
- 关闭防火墙；
- 创建/覆盖 swap；
- 安装 runner；
- 把 VNC 暴露公网；
- 以 root 启动 Codex/Gradle/Minecraft；
- 把软件渲染标为正式视觉通过。

输出 `docs/work/<TASK>/REMOTE-HOST-AUDIT.md`，把每项标为：

```text
PASS
WARN
FAIL
NOT_INSTALLED
HUMAN_ACTION_REQUIRED
```
