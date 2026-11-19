# 依赖清单与版本锁定记录

- 项目：ParkourCombat 2.0
- 记录日期：2026-11-19
- 规则：ParCool 3.4.3.3 与 Better Combat Forge 1.9.0+1.20.1 **永久锁定**；不得升级、降级、替换、修改或重新打包。

## 1. 锁定 JAR 清单

所有 JAR 均为**原始 JAR 的未修改副本**：复制到 `libs/` 前后 SHA-256 一致，未改动内容。

| 角色 | 文件名 | 版本 | 大小 (B) | SHA-256 | 原始路径 | 工程内路径 |
|---|---|---|---|---:|---|---|---|
| ParCool | `ParCool-1.20.1-3.4.3.3.jar` | 3.4.3.3 | 1,154,052 | `2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7` | `D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\ParCool-1.20.1-3.4.3.3.jar` | `libs/ParCool-1.20.1-3.4.3.3.jar` |
| Better Combat Forge | `bettercombat-forge-1.9.0+1.20.1.jar` | 1.9.0+1.20.1 | 1,054,339 | `49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1` | 同实例 `mods\bettercombat-forge-1.9.0+1.20.1.jar` | `libs/bettercombat-forge-1.9.0+1.20.1.jar` |
| Player Animator（BC 强制依赖） | `player-animation-lib-forge-1.0.2-rc1+1.20.jar` | 1.0.2-rc1+1.20 | 181,437 | `90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691` | 同实例 `mods\player-animation-lib-forge-1.0.2-rc1+1.20.jar` | `libs/player-animation-lib-forge-1.0.2-rc1+1.20.jar` |
| Cloth Config（BC 强制依赖） | `cloth-config-11.1.136-forge.jar` | 11.1.136 | 1,181,413 | `1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2` | 同实例 `mods\cloth-config-11.1.136-forge.jar` | `libs/cloth-config-11.1.136-forge.jar` |

## 2. 依赖来源与解析方式

`build.gradle` 使用 `flatDir { dirs 'libs' }` + `fg.deobf(...)`：

```groovy
implementation fg.deobf('local.parcool:ParCool-1.20.1:3.4.3.3')
implementation fg.deobf('local.bettercombat:bettercombat-forge:1.9.0+1.20.1')
implementation fg.deobf('local.playeranimator:player-animation-lib-forge:1.0.2-rc1+1.20')
implementation fg.deobf('local.clothconfig:cloth-config:11.1.136-forge')
```

- 解析结果：ForgeGradle 在 Gradle 缓存中生成 deobf 副本（例如 `.../deobf_dependencies/local/parcool/ParCool-1.20.1/3.4.3.3_mapped_official_1.20.1/`），`libs/` 内原始副本保持不变。
- 未从远程仓库下载任何 ParCool / Better Combat 版本；依赖仅来自 `libs/`。
- 未修改 `mods.toml`、Manifest、类或资源；未重新打包。
- 版本约束写入 `src/main/resources/META-INF/mods.toml`：
  - `parcool`：`[3.4.3.3]`（必需，AFTER）
  - `bettercombat`：`[1.9.0+1.20.1]`（可选，AFTER）
  - `forge`：`[47.4.0,47.5)`
  - `minecraft`：`[1.20.1,1.21)`

## 3. 运行时依赖关系（审计结论）

来自实际 `mods.toml`（见 `BETTERCOMBAT_API_AUDIT.md` / `ANIMATION_COMPATIBILITY_REPORT.md`）：

- Better Combat 1.9.0+1.20.1 必需：
  - `playeranimator >= 1`
  - `cloth_config >= 11.0.0`
- ParCool 3.4.3.3 仅必需 Forge / Minecraft；Player Animator 不是其依赖。
- 因此 `runServer` / `runClient` 需要同时提供 Better Combat、Player Animator、Cloth Config；本工程均以锁定版本放入 `libs/`。

## 4. 校验命令

```powershell
Get-ChildItem 'D:\MinecraftParcoolCombat\libs' |
  ForEach-Object { "{0} {1} {2}" -f $_.Name, $_.Length, (Get-FileHash $_.FullName -Algorithm SHA256).Hash }
```

结果必须与上表一致。任何差异都意味着依赖被替换，必须停止并报告。
