# 12 — Sources / 参考来源

> 资料核对日期：2026-08-26
>
> 本页用于让开发者和 Codex知道哪些外部事实需要重新核对。版本、许可证和平台规则可能变化，公开发布前应再次检查。

## Original Advanced Rocketry

- Repository: https://github.com/Advanced-Rocketry/AdvancedRocketry
- Primary reference branch: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12
- License: https://github.com/Advanced-Rocketry/AdvancedRocketry/blob/1.12/LICENSE
- Java tree: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12/src/main/java/zmaster587/advancedRocketry
- Asset tree: https://github.com/Advanced-Rocketry/AdvancedRocketry/tree/1.12/src/main/resources/assets/advancedrocketry

Observed at review time:

- default branch shown as `1.12`;
- exact `1.12` branch tip verified as `c5cd5af62fc07cd4e0d24f06a16033f181c47c04` on 2026-08-26;
- root repository is detected as MIT;
- original MIT notice contains `Copyright (c) 2017`;
- repository describes rockets, planets/moons, XML planet configuration, atmospheres, stations, satellites, asteroid mining and terraforming.

Future source and asset audits must continue to use the locked commit above unless an explicit update is reviewed and recorded.

## Forge 1.20.1

- Downloads and versions: https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html
- Getting started / Java 17: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/
- Mod files / `mods.toml`: https://docs.minecraftforge.net/en/1.20.1/gettingstarted/modfiles/
- Registries: https://docs.minecraftforge.net/en/1.20.1/concepts/registries/
- SavedData: https://docs.minecraftforge.net/en/1.20.1/datastorage/saveddata/
- Codecs: https://docs.minecraftforge.net/en/1.20.1/datastorage/codecs/
- Networking SimpleImpl: https://docs.minecraftforge.net/en/1.20.1/networking/simpleimpl/
- Data generation: https://docs.minecraftforge.net/en/1.20.1/datagen/
- GameTest: https://docs.minecraftforge.net/en/1.20.1/misc/gametest/

At review time:

- recommended Forge 1.20.1: `47.4.10`;
- latest Forge 1.20.1: `47.4.23`;
- Forge 1.20.1 prerequisites specify Java 17;
- `DeferredRegister` is the recommended registration approach;
- dynamic registry objects are generally data-driven rather than arbitrary runtime registration.

## GitHub licensing and repository setup

- Licensing a repository: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository
- Adding a license: https://docs.github.com/en/communities/setting-up-your-project-for-healthy-contributions/adding-a-license-to-a-repository
- MIT overview: https://choosealicense.com/licenses/mit/

Important operational point:

- a public repository without an explicit license is not automatically open source;
- MIT requires preservation of the copyright and license notice.

## Minecraft naming and disclaimer

- Usage Guidelines: https://www.minecraft.net/en-us/usage-guidelines
- EULA: https://www.minecraft.net/en-us/eula

The Usage Guidelines request a prominent disclaimer similar to:

> NOT AN OFFICIAL MINECRAFT PRODUCT. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT.

They also restrict presenting “Minecraft” as the primary/dominant project brand and using official branding in a way that appears official.

## Recheck checklist before public release

- [ ] Original upstream license and branch still match
- [ ] Exact upstream commit recorded
- [ ] Forge recommended/latest versions rechecked
- [ ] Forge documentation still targets Java 17 for 1.20.1
- [ ] Minecraft Usage Guidelines rechecked
- [ ] Every third-party dependency and asset license recorded
- [ ] Public README/description statements remain accurate
