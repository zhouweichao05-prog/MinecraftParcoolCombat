# DEVELOPMENT_ENVIRONMENT_REPORT — 开发环境审计报告

- 审计日期：2026-11-19
- 工程目录：`D:\MinecraftParcoolCombat`
- 结论标记：**CONFIRMED** / **PARTIALLY_CONFIRMED** / **UNCONFIRMED**

---

## 1. 工程目录现状（审计开始与结束时一致）

| 检查项 | 结果 | 状态 |
|---|---|---|
| `D:\MinecraftParcoolCombat` 是否存在 | 是，存在但为**空目录**（含隐藏文件检查） | **CONFIRMED** |
| `build.gradle` / `settings.gradle` / `gradle.properties` | 不存在 | **CONFIRMED** |
| `src/`、`gradle/`、`gradlew` | 不存在 | **CONFIRMED** |
| `.git` 仓库 | 不存在（`git status` → `fatal: not a git repository`） | **CONFIRMED** |
| `AI_PROJECT_RULES.md` | **未在本机找到**：对 `D:\` 与 `C:\` 执行 `dir /s /b ...\AI_PROJECT_RULES.md` 均无输出（退出码 1） | **CONFIRMED（文件缺失）** |
| 项目文档 | 无 | **CONFIRMED** |

> **重要说明**：任务要求“首先完整阅读 AI_PROJECT_RULES.md”，但该文件在本机不存在（工作区为空）。本次审计依据的是用户在对话中提供的规则全文（与 goal objective 中的文本一致），并已在执行中遵守“不写功能代码、不改 Gradle/mods.toml、不改原始 JAR、不建 Mixin”的限制。若后续需要以文件形式落盘，必须由用户明确指示（本轮未创建该文件）。

本次审计结束时工作区新增的只有 5 份报告 Markdown（本文件与其余四份），**没有**任何 Java 源码、Gradle 文件、Mixin 或对原始 JAR 的修改。

---

## 2. Java 与 Gradle

| 项 | 值 | 状态 | 证据 |
|---|---|---|---|
| JDK 17 | `C:\Program Files\Java\jdk-17`（17.0.12+8-LTS-286） | **CONFIRMED** | `java.exe -version` |
| 当前 `JAVA_HOME` | `D:\JDK\jdk-23.0.1`（23.0.1） | **CONFIRMED** | 环境变量 |
| Gradle 8.8 | `D:\McMod\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8` | **CONFIRMED** | `gradle.bat --version` |
| Gradle 运行 JVM | 23.0.1 | **CONFIRMED** | 同一输出：`JVM: 23.0.1` |
| `GRADLE_USER_HOME` | `D:\McMod\.gradle` | **CONFIRMED** | 环境变量；缓存内有 ForgeGradle 6.0.54 |
| ForgeGradle | 6.0.54（已缓存） | **CONFIRMED** | `...\modules-2\files-2.1\net.minecraftforge.gradle\ForgeGradle\6.0.54\` |
| Forge userdev 1.20.1-47.4.0 | 已缓存 | **CONFIRMED** | `...\forge_gradle\minecraft_user_repo\net\minecraftforge\forge\1.20.1-47.4.0_mapped_official_1.20.1\` |

### 构建环境风险

1. **CONFIRMED（必须修正）**：`JAVA_HOME` 与 Gradle 守护进程当前使用 **JDK 23**。Forge 1.20.1 / ForgeGradle 6.0.54 的标准工具链是 **JDK 17**；在 JDK 23 下运行构建可能失败或出现非预期行为。下一阶段必须在项目级固定 JDK 17（例如 `org.gradle.java.home=C:\\Program Files\\Java\\jdk-17` 或构建时设置 `JAVA_HOME`）。
2. **PARTIALLY_CONFIRMED**：ForgeGradle 6.0.54 与 Forge 47.4.0 userdev 已缓存，具备离线复用条件；但尚未在空工程上实际生成 wrapper、解析依赖并编译（本阶段禁止修改 Gradle，故未验证）。
3. **CONFIRMED**：`gradle` 不在 PATH；必须使用现有 wrapper 分布或生成新的 Gradle 8.8 wrapper。

---

## 3. 依赖来源与坐标方案

### 3.1 本机实际 JAR（版本锁定）

| 依赖 | 路径 | SHA-256 |
|---|---|---|
| ParCool 3.4.3.3 | `D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\ParCool-1.20.1-3.4.3.3.jar` | `2DDEF875...15C6E7` |
| Better Combat 1.9.0+1.20.1 | 同目录 `bettercombat-forge-1.9.0+1.20.1.jar` | `49C69DCC...69EEA1` |
| Player Animator 1.0.2-rc1+1.20 | 同目录 `player-animation-lib-forge-1.0.2-rc1+1.20.jar` | `90D9965C...A852691` |
| ParCool Compatibility Addon 1.1 | 同目录 `ParCool-CompatibilityAddon-1.20.1-3.4.1.5-1.1.jar` | `F7CF79F0...BC3871` |
| Cloth Config 11.1.136 | 同目录 `cloth-config-11.1.136-forge.jar` | `1E895E85...4CE0F2` |

### 3.2 推荐依赖声明方式（下一阶段执行，本阶段未修改任何 Gradle）

- **首选：本地文件依赖**（与锁定 JAR 完全一致，不受远程版本漂移影响）：

  ```groovy
  dependencies {
      compileOnly files('libs/ParCool-1.20.1-3.4.3.3.jar')
      compileOnly files('libs/bettercombat-forge-1.9.0+1.20.1.jar')
      compileOnly files('libs/player-animation-lib-forge-1.0.2-rc1+1.20.jar')
      // 运行期需要 cloth-config / playeranimator 时放入 run/mods
  }
  ```

  - 建议把上述 JAR 复制到工程 `libs/`（复制不是修改原 JAR；需用户确认后再执行）。
- **可选：CurseMaven 坐标**：
  - ParCool：`curse.maven:parcool-482378:7760589` 在本机 Gradle 缓存中确实存在，且解压后 Manifest 为 `3.4.3.3`（**CONFIRMED**）。
  - Better Combat：本次**未验证**其 CurseMaven/其他 Maven 坐标；不得凭记忆填写，**UNCONFIRMED**。
  - Player Animator / Cloth Config：未验证可靠 Maven 坐标；**UNCONFIRMED**。
- **不建议**：使用 `latest.release`、动态版本、或任何会改变锁定版本的仓库解析策略。

### 3.3 运行期依赖

- BC 运行必须：`playeranimator >= 1`、`cloth_config >= 11.0.0`（`mods.toml` 强制）。
- ParCool 运行不强制 Player Animator。
- Compatibility Addon 运行必须 ParCool >= 3.4.1.5。
- 专用服务器测试已证明：上述 5 个 JAR（ParCool、BC、PlayerAnimator、Cloth Config、Addon）+ Patchouli 可以共同启动。

---

## 4. 专用服务器可用性（实际启动测试）

### 4.1 安装

- 来源：`https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.4.0/forge-1.20.1-47.4.0-installer.jar`
- 安装器大小：6,056,587 B；SHA-256：`F3F57465E2CBDC328193F77BA7F0C94CB64405F31ED556753D9275E8BDC06E2C`
- 安装命令：`java -jar installer.jar --installServer`
- 结果：`The server installed successfully`（退出码 0）
- 生成的服务器主 JAR：`forge-1.20.1-47.4.0-server.jar`（4,826,828 B，SHA-256 `C57F937F2D393F5411F45A1160D3D34BAEA19321A5975300A3AB62BFD1F4EBB3`）
- 安装目录：`%TEMP%\pc_server`（临时目录，不属于工程；未触碰原整合包）

### 4.2 启动测试 1（5 个核心 JAR，无 Patchouli）

- 启动参数：`java -Xmx2G -Xms1G @user_jvm_args.txt @libraries/net/minecraftforge/forge/1.20.1-47.4.0/win_args.txt nogui`
- JVM：Java 17.0.12
- `server.properties`：`online-mode=false`、`server-ip=127.0.0.1`、`server-port=25599`、`level-type=minecraft\:flat`
- 日志开头：`ModLauncher running: args [--launchTarget, forgeserver, --fml.forgeVersion, 47.4.0, --fml.mcVersion, 1.20.1, ...]`
- 结果：`[Server thread/INFO] [minecraft/DedicatedServer]: Done (2.413s)!`

> 启动日志同时出现 `[forge] Found status: OUTDATED Current: 47.4.0 Target: 47.4.28`。按项目规则，Forge 环境也固定为 47.4.0，**不因该提示升级**。

**加载的 mod（FML debug 日志原文）**：

```
Found valid mod file bettercombat-forge-1.9.0+1.20.1.jar with {bettercombat} mods - versions {1.9.0+1.20.1}
Found valid mod file ParCool-1.20.1-3.4.3.3.jar with {parcool} mods - versions {3.4.3.3}
Found valid mod file ParCool-CompatibilityAddon-1.20.1-3.4.1.5-1.1.jar with {parcool_compat_addon} mods - versions {1.1}
Found valid mod file player-animation-lib-forge-1.0.2-rc1+1.20.jar with {playeranimator} mods - versions {1.0.2-rc1+1.20}
```

**Run 1 的非致命错误（已定位原因）**：

- `Couldn't parse element loot_tables:parcool:grant_parcool_guide` → `unknown string 'patchouli:guide_book'`
- `Parsing error loading recipe parcool:parcool_guide` → `Unknown item 'patchouli:guide_book'`

原因：测试环境未安装 Patchouli；ParCool 的指南书战利品表/配方引用了 Patchouli 物品。**不是** ParCool/BC 加载失败。

### 4.3 启动测试 2（加入 Patchouli，验证无错误）

- 追加 JAR：`Patchouli-1.20.1-85-FORGE.jar`
- 结果：`Done (1.915s)!`；`ERROR|Exception|Caused by` 搜索结果为空（**零错误**）
- 关键行：

  ```
  [Server thread/INFO] [ne.be.lo.WeaponRegistry/]: Encoded Weapon Attribute registry size: 8234 bytes (uncompressed), 616 bytes (compressed)...
  [patchouli/]: Sending reload packet to clients
  ```

- 生成的配置证明各模组完成初始化：
  - `config/bettercombat/server.json5`、`config/bettercombat/fallback_compatibility.json`（BC）
  - `world/serverconfig/parcool-server.toml`（ParCool，包含全部 27 个动作权限与体力配置）
  - `world/serverconfig/forge-server.toml`

### 4.4 结论

| 结论 | 状态 |
|---|---|
| Forge 1.20.1-47.4.0 专用服务器可在 Java 17 下安装并启动 | **CONFIRMED** |
| ParCool 3.4.3.3 + BC 1.9.0 + PlayerAnimator 1.0.2-rc1 + Cloth Config 11.1.136 + Addon 1.1 可同时加载并到达 `Done` | **CONFIRMED** |
| 上述组合在专用服务器启动阶段没有客户端类加载崩溃 | **CONFIRMED** |
| 专用服务器上的实际玩家登录、ParCool 动作同步、BC 攻击结算 | **UNCONFIRMED / NOT TESTED**（本次只做启动级验证，没有客户端） |
| 完整 DeceasedCraft_CB 整合包（数百个 mod）在专用服务器上的表现 | **UNCONFIRMED / NOT TESTED** |

> 测试服务器在 `Done` 后是被强制结束的（`Stop-Process`），不是控制台 `stop`；仅用于启动验证，未做存档/关闭流程验证。

---

## 5. 客户端环境

| 项 | 值 | 状态 |
|---|---|---|
| 客户端实例 | DeceasedCraft_CB（PCL） | **CONFIRMED** |
| 客户端 Java | 版本 JSON `javaVersion.majorVersion = 17` | **CONFIRMED** |
| 客户端实际启动参数 | 未检查 PCL 启动配置的 Java 选择 | **UNCONFIRMED** |
| Minecraft 客户端启动测试 | 未执行 | **NOT TESTED** |

> **CONFIRMED**：本报告不声称任何客户端画面/玩法验证。

---

## 6. 已知缺口与下一阶段前置条件

1. **阻塞项（需用户确认）**：`D:\MinecraftParcoolCombat` 为空，尚无 Forge MDK 工程；`AI_PROJECT_RULES.md` 不存在于本机。需要用户确认：
   - 是否由我在下一阶段创建工程骨架（Forge MDK 1.20.1-47.4.0 / Gradle 8.8 / Java 17）？
   - 是否把 5 个锁定 JAR 复制到工程 `libs/`（版本锁定，不修改原 JAR）？
   - 是否将 `AI_PROJECT_RULES.md` 落盘到工程根目录？
2. **CONFIRMED**：构建前必须把 Gradle JVM 固定为 JDK 17；当前 `JAVA_HOME` 为 JDK 23。
3. **PARTIALLY_CONFIRMED**：ForgeGradle 6.0.54 与 Forge 47.4.0 userdev 已有缓存，但未在空工程上实际构建过；首次构建可能需要网络补齐依赖。
4. **UNCONFIRMED**：Better Combat 的公开 Maven 坐标、Player Animator / Cloth Config 的公开坐标——不要编造；优先本地 JAR。
5. **UNCONFIRMED**：客户端实机动画/玩法验证、专用服务器玩家联机验证。
6. **CONFIRMED**：本轮未修改 Gradle、未修改 mods.toml、未修改原始 JAR、未创建 Java 功能代码或 Mixin。

---

## 7. 复现命令汇总

```powershell
# 工程目录状态
Get-ChildItem D:\MinecraftParcoolCombat -Force
git -C D:\MinecraftParcoolCombat status

# 规则文件搜索（本次结果：未找到）
cmd /c "dir /s /b D:\AI_PROJECT_RULES.md 2>nul & dir /s /b C:\AI_PROJECT_RULES.md 2>nul"

# Java / Gradle
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -version
& 'D:\McMod\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat' --version

# 专用服务器（本次审计使用）
$d="$env:TEMP\pc_server"
java -jar "$d\installer.jar" --installServer
# 将 5 个核心 JAR 放入 $d\mods，写 eula.txt=true 与 server.properties
java -Xmx2G -Xms1G "@user_jvm_args.txt" "@libraries/net/minecraftforge/forge/1.20.1-47.4.0/win_args.txt" nogui
```

---

## 8. 结论汇总

- **CONFIRMED**：Java 17.0.12、Gradle 8.8、ForgeGradle 6.0.54、Forge 47.4.0 userdev 均已具备；但默认 `JAVA_HOME` 是 JDK 23，必须修正。
- **CONFIRMED**：Forge 1.20.1-47.4.0 专用服务器可安装启动；5 个锁定 JAR（ParCool 3.4.3.3、BC 1.9.0、PlayerAnimator 1.0.2-rc1、Cloth Config 11.1.136、Addon 1.1）在服务端加载成功，第二次干净启动零错误。
- **CONFIRMED**：当前工程目录为空、无 Git、无 `AI_PROJECT_RULES.md` 文件；本阶段未创建任何代码或构建文件。
- **PARTIALLY_CONFIRMED**：构建链尚未在真实工程上跑通（等下一阶段初始化工程后验证）。
- **UNCONFIRMED**：客户端实机验证、专用服务器玩家联机验证、完整整合包服务器验证。
