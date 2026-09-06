# v1.0+ 外部技术来源

> 本页只记录制定服务器与工具流程时使用的外部技术依据。项目实现仍以仓库代码、Forge 1.20.1 文档和实际测试为准。

## Forge 1.20.1

- Forge 1.20.1 documentation: <https://docs.minecraftforge.net/en/1.20.1/>
- Getting Started / run configurations: <https://docs.minecraftforge.net/en/1.20.1/gettingstarted/>
- GameTest: <https://docs.minecraftforge.net/en/1.20.1/misc/gametest/>
- Networking: <https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/>
- SavedData: <https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/>
- Codecs: <https://docs.minecraftforge.net/en/1.20.1/datastorage/codecs/>

## Codex

- Codex CLI: <https://developers.openai.com/codex/cli>
- Projects and chats / resume: <https://developers.openai.com/codex/projects>
- Non-interactive mode: <https://developers.openai.com/codex/non-interactive-mode>
- Subagents: <https://developers.openai.com/codex/subagents>

Codex 的命令和参数可能更新。执行前以本机 `codex --help`、`codex resume --help` 和官方当前文档为准。

本地 CLI 帮助可验证安装版本；配置/规约变更的行为验证必须重启并开启
新的非 resume 会话。以上链接不证明某个服务器已经安装或测试过 Codex。

## Debian / tmux / systemd

- Debian 12 tmux manual: <https://manpages.debian.org/bookworm/tmux/tmux.1.en.html>
- systemd loginctl: <https://www.freedesktop.org/software/systemd/man/latest/loginctl.html>
- systemd-run: <https://www.freedesktop.org/software/systemd/man/latest/systemd-run.html>

## Headless graphics

- Xvfb manual: <https://www.x.org/releases/current/doc/man/man1/Xvfb.1.xhtml>
- X server access control / transport options: <https://xorg.freedesktop.org/archive/X11R7.5/doc/man/man1/Xserver.1.html>
- Debian 12 x11vnc authentication / loopback options: <https://manpages.debian.org/bookworm/x11vnc/x11vnc.1.en.html>
- Mesa LLVMpipe: <https://docs.mesa3d.org/drivers/llvmpipe.html>
- GLFW Linux platform notes: <https://www.glfw.org/docs/latest/compile.html>

## SSH 与 GitHub Actions

- OpenSSH server configuration: <https://man.openbsd.org/sshd_config>
- GitHub self-hosted runner security: <https://docs.github.com/en/actions/hosting-your-own-runners/managing-self-hosted-runners/about-self-hosted-runners>

公共仓库的自托管 runner 具有较高风险；本项目默认不让来自不受信任 fork 的代码在长期服务器凭据环境中执行。
