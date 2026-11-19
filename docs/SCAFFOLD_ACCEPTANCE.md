# SCAFFOLD_ACCEPTANCE — 最小 Forge 工程验收报告

- 日期：2026-11-19
- 阶段：工程骨架（不含 ParCoolAdapter、不含战斗系统、不含 Mixin）
- 工作目录：`D:\MinecraftParcoolCombat`
- 结论标记：**PASS** / **FAIL** / **NOT TESTED**

> 本阶段严格按用户决策执行：只创建最小 Forge 工程、依赖配置、主 Mod 类、资源、基础文档与 Git 初始化。
> **未实现**跑酷适配、攻击系统、词条、网络包或任何 Mixin。

---

## 1. 创建内容

| 路径 | 说明 |
|---|---|
| `settings.gradle` | Gradle 8.8 / ForgeGradle 插件仓库 / 工程名 |
| `build.gradle` | Forge 1.20.1-47.4.0、official mappings、Java 17、runClient/runServer、本地 `libs/` 依赖 |
| `gradle.properties` | 固定 JDK 17、版本锁定参数、mod 元数据 |
| `gradlew.bat` / `gradlew` / `gradle/wrapper/*` | Gradle 8.8 wrapper（由本机 Gradle 8.8 生成） |
| `src/main/java/com/MrGhost/parkourCombat/ParkourCombat.java` | 只含 `@Mod` 入口，构造器为空 |
| `src/main/resources/META-INF/mods.toml` | modId `parkourcombat`；ParCool `[3.4.3.3]` 必需；BC `[1.9.0+1.20.1]` 可选 |
| `src/main/resources/pack.mcmeta` | pack_format 15（1.20.1） |
| `libs/*.jar` | 4 个锁定 JAR 的未修改副本（详见 `docs/DEPENDENCIES.md`） |
| `docs/DEPENDENCIES.md` | 文件名、版本、大小、SHA-256、来源 |
| `docs/logs/` | 构建与运行日志 |
| `README.md`、`.gitignore` | 基础项目文档与忽略规则 |

---

## 2. 环境核验

| 项 | 实际值 | 状态 |
|---|---|---|
| Java（构建/运行） | 17.0.12 (`C:\Program Files\Java\jdk-17`) | **PASS** |
| Gradle | 8.8（wrapper 生成后固定） | **PASS** |
| Forge | 1.20.1-47.4.0 | **PASS** |
| Minecraft | 1.20.1 | **PASS** |
| Mod ID / Package | `parkourcombat` / `com.MrGhost.parkourCombat` | **PASS** |

`gradle.properties` 中固定：

```properties
org.gradle.java.home=C:/Program Files/Java/jdk-17
minecraft_version=1.20.1
forge_version=47.4.0
mapping_channel=official
mapping_version=1.20.1
```

---

## 3. 依赖核验

`libs/` 中的 JAR 均为**原始 JAR 的未修改副本**，SHA-256 与来源完全一致：

| 文件 | SHA-256 | 状态 |
|---|---|---|
| `ParCool-1.20.1-3.4.3.3.jar` | `2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7` | **PASS** |
| `bettercombat-forge-1.9.0+1.20.1.jar` | `49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1` | **PASS** |
| `player-animation-lib-forge-1.0.2-rc1+1.20.jar` | `90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691` | **PASS** |
| `cloth-config-11.1.136-forge.jar` | `1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2` | **PASS** |

依赖通过 `flatDir { dirs 'libs' }` + `fg.deobf(...)` 解析；Gradle 生成 deobf 副本，原始 JAR 未被改动。
完整清单见 `docs/DEPENDENCIES.md`。

---

## 4. 构建结果

命令（工作目录 `D:\MinecraftParcoolCombat`）：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon build
```

| 任务 | 结果 | 证据 |
|---|---|---|
| `compileJava` | **PASS** | `BUILD SUCCESSFUL in 21s`（`docs/logs/compileJava.log`） |
| `build` | **PASS** | `BUILD SUCCESSFUL in 15s`（最终重跑；首次为 16s，`docs/logs/build.log`） |
| 产物 | `build/libs/parkourcombat-2.0.0-alpha.1.jar`（2,137 B） | `jar tf` 确认含 `META-INF/mods.toml`、`pack.mcmeta`、`ParkourCombat.class` |
| Manifest | `Implementation-Version: 2.0.0-alpha.1` | 构建产物内 `META-INF/MANIFEST.MF` |

> 说明：开发运行（classes 目录）中 FML 显示 `parkourcombat 0.0NONE`，这是 `${file.jarVersion}` 在 dev 环境无 JAR Manifest 时的正常表现；构建出的 JAR 版本为 `2.0.0-alpha.1`。

---

## 5. runServer 验收

命令与日志：

```powershell
.\gradlew.bat --no-daemon runServer
```

日志：`docs/logs/runServer.log`、`docs/logs/runServer.latest.log`（运行目录：`run/`）。

### 结果：**PASS（启动级）**

- 日志行：`Done (16.652s)! For help, type "help"`。
- 已加载模组（debug 日志）：
  - `{parkourcombat}`（本模组）
  - `{parcool} 3.4.3.3`
  - `{bettercombat} 1.9.0+1.20.1`
  - `{playeranimator} 1.0.2-rc1+1.20`
  - `{cloth_config} 11.1.136`
- 服务端完成了 ParCool 服务端配置生成与 Better Combat `WeaponRegistry` 编码。

### 已知非致命问题（均已定位）

1. **首次启动 `server.properties` 缺失**：`Failed to load properties from file: server.properties`，随后服务端自动生成默认文件并继续启动。第二次启动不再出现。
2. **ParCool 指南书引用 Patchouli**：
   - `Couldn't parse element loot_tables:parcool:grant_parcool_guide`
   - `Parsing error loading recipe parcool:parcool_guide`
   - 原因：测试环境未安装 Patchouli；这是 ParCool 的可选依赖缺失，不是 ParCool/BC 加载失败。
3. **开发环境 Mixin refmap 问题（已修复）**：
   - 首次 `runServer` 在 ParCool `EntityMixin` 处失败：`@Inject ... could not find any targets matching '...m_20192_()F'`。
   - 原因：锁定的生产 JAR 使用 SRG refmap，dev 运行使用 official mappings。
   - 修复：仅在 `runClient`/`runServer` 的**开发运行配置**中增加：
     ```groovy
     property 'mixin.env.remapRefMap', 'true'
     property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
     ```
   - 该修改只影响 dev 运行参数，**未修改任何 JAR**。
4. 服务端在 `Done` 后由测试脚本强制结束（`taskkill /T /F`），未验证控制台 `stop` 的优雅关闭流程。

---

## 6. runClient 验收

命令与日志：

```powershell
.\gradlew.bat --no-daemon runClient
```

日志：`docs/logs/runClient.log`、`docs/logs/runClient.latest.log`。

### 结果：**PASS（启动/主菜单级）**

关键日志：

```
[Render thread/INFO] [net.minecraft.client.Minecraft]: Setting user: Dev
[Render thread/INFO] [com.mojang.blaze3d.audio.Library]: OpenAL initialized ...
[Render thread/INFO] [net.minecraft.client.sounds.SoundEngine/SOUNDS]: Sound engine started
[Render thread/INFO] [net.minecraft.client.renderer.texture.TextureAtlas]: Created: 1024x512x4 minecraft:textures/atlas/blocks.png-atlas
```

- 客户端成功打开窗口、初始化 OpenAL、加载资源包并进入主菜单。
- `FATAL` / `Mixin apply failed` / `Exception` 搜索为空。
- 已加载 `parkourcombat`、`parcool 3.4.3.3`、`bettercombat 1.9.0+1.20.1`、`playeranimator`、`cloth_config`。
- 客户端在进入主菜单后被测试脚本强制结束；**未进入世界**。

非致命警告：Forge 版本检查提示 `47.4.0` 相对 `47.4.28` 过期（按锁定规则不升级）；少量原版音效/Shader 警告。

---

## 7. 验收矩阵

| 验收项 | 结果 | 说明 |
|---|---|---|
| 工程骨架创建 | **PASS** | 最小工程，无空模块 |
| 依赖版本锁定与哈希 | **PASS** | 4 个 JAR 哈希与原始来源一致 |
| `compileJava` | **PASS** | 21s |
| `build` | **PASS** | 16s，生成 2,137 B JAR |
| `runServer` | **PASS（启动级）** | `Done (16.652s)`，5 个模组加载 |
| `runClient` | **PASS（主菜单级）** | 窗口/音频/资源加载成功，未进世界 |
| 客户端跑酷玩法 | **NOT TESTED** | 本阶段未实现 Adapter；按决策 5 由用户人工验证 |
| ParCoolAdapter | **NOT IMPLEMENTED** | 按本阶段禁止项，未提前实现 |
| 战斗/词条/网络/Mixin | **NOT IMPLEMENTED** | 未触碰 |
| 整体功能 VERIFIED | **NO** | 仅骨架验收，不能标记为 VERIFIED |

---

## 8. Git 状态

- 已执行 `git init`，并创建首次提交：
  - `04082120d9ed14ee4c7fa2a8f996f1a967dff271`
  - `Initial commit: ParkourCombat 2.0 Forge scaffold`
- 首次提交包含 32 个文件：工程骨架、`libs/` 锁定 JAR、5 份阶段 01 审计报告、依赖清单、验收报告与 `docs/logs/` 日志。
- 提交前已检查 `.gitignore`、敏感文件与构建产物；`build/`、`run/`、`.gradle/` 未提交。
- 提交后工作区干净。
- 补充核查文档（`docs/PHASE02_PREFLIGHT.md` 与本文件的 Git 状态更新）将在后续文档提交中记录。

---

## 9. 下一阶段阻塞项 / 待确认

以下不是骨架阻塞项，但会影响 ParCoolAdapter 的可靠性，必须在实现前明确：

1. **ParCool 客户端取消/中断的运行时行为**：`TryToStart` / `TryToContinue` 的取消逻辑来自静态代码与字节码审计，尚未在真实客户端跑酷动作中验证。
2. **ParCool 服务端状态是客户端同步镜像**（`CONFIRMED` 审计结论）：服务端 `Start` 包不重新执行 `canStart`；Adapter 必须带 `syncedFromClient` 标记，不能单独作为战斗权威。
3. **服务端无法区分自然结束与客户端取消**：`FINISHED` 与 `CANCELLED` 的差异只存在于客户端；服务端 `INTERRUPTED` 只能启发式推断并标记。
4. **`FALL` 为派生状态**：ParCool 无 `Fall` 动作类；需按决策 2 实现（尚未实现）。
5. **客户端人工验证尚未执行**：按决策 5，需要用户按清单进入客户端执行跑酷动作并提供日志/截图。

---

## 10. 复现命令

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon build
.\gradlew.bat --no-daemon runServer
.\gradlew.bat --no-daemon runClient
```

---

## 11. 结论

- 最小 Forge 工程已建立，锁定依赖与哈希已记录，`compileJava` / `build` 通过。
- `runServer` 到达 `Done`，`runClient` 到达主菜单；均为**启动级**验收。
- 未实现任何跑酷适配、战斗逻辑或 Mixin，符合本阶段范围限制。
- 工程骨架已提交；补充核查见 `docs/PHASE02_PREFLIGHT.md`。等待用户审查确认后，再进入 ParCoolAdapter 实现阶段。
