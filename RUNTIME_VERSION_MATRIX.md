# RUNTIME_VERSION_MATRIX — ParkourCombat 2.0 环境版本矩阵

- 审计日期：2026-11-19
- 审计对象：本机实际存在的 Minecraft / Forge / 锁定 Mod JAR
- 审计方式：读取实际文件、`mods.toml`、`MANIFEST.MF`、版本 JSON、SHA-256 校验、`javap` 字节码与 Fernflower 反编译
- 结论标记：**CONFIRMED** / **PARTIALLY_CONFIRMED** / **UNCONFIRMED**

> 重要前提：工程目录 `D:\MinecraftParcoolCombat` 在本轮审计开始时为空目录（无 `.git`、无 `build.gradle`、无 `src`）。本报告审计的是本机 **DeceasedCraft_CB** 整合包实例中的真实 JAR；这是本机唯一存在的 ParCool 3.4.3.3 / Better Combat 1.9.0 运行环境。

---

## 1. 平台版本矩阵

| 项目 | 实际值 | 状态 | 证据 |
|---|---|---|---|
| Minecraft | 1.20.1 | **CONFIRMED** | `D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\PCL\Setup.ini`：`VersionVanillaName:1.20.1`、`Info:正式版 1.20.1, Forge 47.4.0`；版本 JSON `javaVersion.majorVersion = 17`；Forge 库版本 `1.20.1-47.4.0` |
| Forge | 47.4.0 | **CONFIRMED** | `Setup.ini`：`VersionForge:47.4.0`；`libraries\net\minecraftforge\forge\1.20.1-47.4.0\` 下存在 `forge-1.20.1-47.4.0-client.jar`（4,826,828 B）与 `forge-1.20.1-47.4.0-universal.jar`（2,776,081 B）；版本 JSON 中 `fmlloader:1.20.1-47.4.0` 等库 |
| Java（安装） | 17.0.12 (Oracle JDK 17) | **CONFIRMED** | `C:\Program Files\Java\jdk-17\bin\java.exe -version` → `17.0.12+8-LTS-286` |
| Java（当前 `JAVA_HOME`） | 23.0.1 | **CONFIRMED（与实际要求不符）** | `$env:JAVA_HOME = D:\JDK\jdk-23.0.1`；Gradle 8.8 启动日志显示 `JVM: 23.0.1` |
| Gradle | 8.8 | **CONFIRMED（本机分布存在，未在 PATH）** | `D:\McMod\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat --version` → `Gradle 8.8`；`where.exe gradle` 无结果 |
| Gradle 运行 JVM | 23.0.1 | **CONFIRMED（需要修正）** | 同一 `--version` 输出：`JVM: 23.0.1`。ForgeGradle 6.0.54 / Forge 1.20.1 工具链要求 Java 17，构建前必须将 Gradle JVM 固定到 JDK 17 |
| ForgeGradle | 6.0.54 | **CONFIRMED** | `D:\McMod\.gradle\caches\modules-2\files-2.1\net.minecraftforge.gradle\ForgeGradle\6.0.54\`；Forge userdev 缓存含 `1.20.1-47.4.0_mapped_official_1.20.1` |
| 运行中的整合包实例 | DeceasedCraft_CB | **CONFIRMED** | `D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\`（PCL 启动器实例，`DeceasedCraft_CB.jar` 23,028,853 B） |

### 版本矩阵结论

- **CONFIRMED**：本机确实存在 Minecraft 1.20.1 + Forge 47.4.0 + Java 17 客户端运行环境。
- **CONFIRMED**：Gradle 8.8 与 ForgeGradle 6.0.54 的构建缓存已存在，Forge 47.4.0 的 userdev 依赖已缓存（可离线/半离线复用）。
- **PARTIALLY_CONFIRMED**：当前 shell 的 `JAVA_HOME` 指向 JDK 23，Gradle 也跑在 JDK 23 上。尚未在 JDK 17 下实际执行过本项目构建（工程尚不存在）。
- **CONFIRMED（启动级）**：Forge 1.20.1-47.4.0 专用服务器已在本机安装并成功启动；ParCool 3.4.3.3、BC 1.9.0+1.20.1、PlayerAnimator 1.0.2-rc1、Cloth Config 11.1.136、Compatibility Addon 1.1 同时加载并到达 `Done`（含 Patchouli 的第二轮零错误）。详见 `DEVELOPMENT_ENVIRONMENT_REPORT.md` 第 4 节。玩家联机与玩法仍为 NOT TESTED。

---

## 2. 锁定 Mod JAR 清单与哈希

所有路径均位于：
`D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods\`

| 角色 | 文件名 | 大小 (B) | SHA-256 | 版本来源 | 状态 |
|---|---|---|---|---|---|
| ParCool | `ParCool-1.20.1-3.4.3.3.jar` | 1,154,052 | `2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7` | `MANIFEST.MF`: `Implementation-Version: 3.4.3.3`；`mods.toml`: `${file.jarVersion}` | **CONFIRMED** |
| Better Combat | `bettercombat-forge-1.9.0+1.20.1.jar` | 1,054,339 | `49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1` | `mods.toml`: `version="1.9.0+1.20.1"`（Manifest 无版本字段） | **CONFIRMED** |
| Player Animator | `player-animation-lib-forge-1.0.2-rc1+1.20.jar` | 181,437 | `90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691` | `mods.toml`: `version="1.0.2-rc1+1.20"` | **CONFIRMED** |
| ParCool Compatibility Addon | `ParCool-CompatibilityAddon-1.20.1-3.4.1.5-1.1.jar` | 24,155 | `F7CF79F065B076F03CA42E7ABB33CB00AA3ED8CA0743296DC25549C2EFBC3871` | `MANIFEST.MF`: `Implementation-Version: 1.1`；`mods.toml`: `${file.jarVersion}` | **CONFIRMED** |
| Cloth Config（BC 强制依赖） | `cloth-config-11.1.136-forge.jar` | 1,181,413 | `1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2` | 文件名；`mods.toml` 由 BC 声明依赖 `[11.0.0,)` | **CONFIRMED** |

### 关键 Manifests

ParCool（`ParCool-1.20.1-3.4.3.3.jar!/META-INF/MANIFEST.MF`）：

```
Manifest-Version: 1.0
Specification-Title: ParCool!
Specification-Vendor: alRex
Specification-Version: 3.4.3.3
Implementation-Title: ParCool
Implementation-Version: 3.4.3.3
Implementation-Vendor: alRex
TweakClass: org.spongepowered.asm.launch.MixinTweaker
MixinConfigs: parcool.mixins.json
```

Better Combat（`bettercombat-forge-1.9.0+1.20.1.jar!/META-INF/MANIFEST.MF`）：

```
Manifest-Version: 1.0
MixinConfigs: bettercombat.mixins.json
```

ParCool Compatibility Addon（1.1）：

```
Specification-Version: 1.1
Implementation-Title: ParCool-CompatibilityAddon
Implementation-Version: 1.1
MixinConfigs: parcool_compat_addon.mixins.json
```

Player Animator：

```
Manifest-Version: 1.0
MixinConfigs: playerAnimator-common.mixins.json
```

### 版本锁定核验

- **CONFIRMED**：上述四个 JAR 的文件名版本与实际 `MANIFEST.MF` / `mods.toml` 声明一致，没有文件名与内部版本不符的情况。
- **CONFIRMED**：审计中未在运行实例的 `mods` 目录发现第二份 ParCool 或 Better Combat JAR。
- **CONFIRMED**：Gradle 缓存中存在另一份 ParCool（CurseMaven 坐标 `curse.maven:parcool-482378:7760589`，deobf 文件名 `parcool-482378-7760589_mapped_official_1.20.1.jar`），其 Manifest 同样为 **3.4.3.3**，版本未漂移；它不是运行实例加载对象。
- **PARTIALLY_CONFIRMED**：ParCool 3.4.3.3 与 Compatibility Addon 1.1 的实际组合——专用服务器启动加载成功（Addon 的客户端 Mixin 未在服务端应用，无崩溃），但客户端动画表现未测试。

---

## 3. 依赖声明（来自实际 `mods.toml`）

### ParCool 3.4.3.3

| 依赖 | 必需 | 版本范围 | 侧 |
|---|---|---|---|
| forge | 是 | `[43,)` | BOTH |
| minecraft | 是 | `[1.20,1.21)` | BOTH |
| feathers | 否 | `[0.1,)` | BOTH |
| paraglider | 否 | `[20.1.1,)` | BOTH |
| betterthirdperson | 否 | `[1.9.0,)` | BOTH |
| shouldersurfing | 否 | `[1.20.1-4.11.0,)` | BOTH |
| epicfight | 否 | `[20.7.4,)` | BOTH |

### Better Combat 1.9.0+1.20.1

| 依赖 | 必需 | 版本范围 | 侧 |
|---|---|---|---|
| forge | 是 | `[47.1,)` | BOTH |
| minecraft | 是 | `[1.20,)` | BOTH |
| playeranimator | 是 | `[1,)` | BOTH（AFTER） |
| cloth_config | 是 | `[11.0.0,)` | BOTH（AFTER） |

### Player Animator 1.0.2-rc1+1.20

| 依赖 | 必需 | 版本范围 | 侧 |
|---|---|---|---|
| minecraft | 是 | `[1.20,)` | BOTH |

### ParCool Compatibility Addon 1.1

| 依赖 | 必需 | 版本范围 | 侧 |
|---|---|---|---|
| forge | 是 | `[39,)` | BOTH |
| minecraft | 是 | `[1.20.1,1.21)` | BOTH |
| parcool | 是 | `[3.4.1.5,)` | BOTH（BEFORE） |
| tacz | 否 | `[1.0.2,)` | BOTH |
| bettercombat | 否 | `[1.8.6+1.20.1,)` | BOTH（BEFORE） |
| carryon | 否 | `[2.1.2.7,)` | BOTH |
| playeranimator | 否 | `[1.0.2-rc1+1.20,)` | BOTH |
| paraglider | 否 | `[1.7.0.5,)` | BOTH |
| pointblank | 否 | `[1.9.6,)` | BOTH |
| realcamera | 否 | `[0.5.3,)` | CLIENT |
| firstperson | 否 | `[2.5.0,)` | CLIENT |

### 依赖结论

- **CONFIRMED**：Better Combat 在 Forge 端要求 `cloth_config >= 11.0.0` 与 `playeranimator >= 1`，实际实例均满足（`cloth-config-11.1.136-forge.jar`、`player-animation-lib-forge-1.0.2-rc1+1.20.jar`）。
- **CONFIRMED**：Compatibility Addon 1.1 对 ParCool 的最低要求是 `3.4.1.5`，因此 3.4.3.3 在元数据层面被允许。
- **PARTIALLY_CONFIRMED**：Addon 1.1 的编译目标是 ParCool 3.4.1.5 API/类结构；其与 3.4.3.3 的二进制兼容性仅由元数据允许，尚未做运行验证（其引用 `Animation.shouldCancelAnimation`，该方法在 3.4.3.3 中存在，见 `ANIMATION_COMPATIBILITY_REPORT.md`）。

---

## 4. 可复现验证命令

以下命令均在审计时实际执行并得到上述结果。

### 4.1 文件与哈希

```powershell
$mods='D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\mods'
Get-Item "$mods\ParCool-1.20.1-3.4.3.3.jar","$mods\bettercombat-forge-1.9.0+1.20.1.jar",
        "$mods\player-animation-lib-forge-1.0.2-rc1+1.20.jar",
        "$mods\ParCool-CompatibilityAddon-1.20.1-3.4.1.5-1.1.jar" |
  ForEach-Object { Get-FileHash $_.FullName -Algorithm SHA256 }
```

### 4.2 读取 mods.toml / Manifest

```powershell
# 任选其一：用压缩工具查看
tar -xOf "ParCool-1.20.1-3.4.3.3.jar" META-INF/MANIFEST.MF
tar -xOf "ParCool-1.20.1-3.4.3.3.jar" META-INF/mods.toml
```

### 4.3 平台版本

```powershell
Get-Content 'D:\PCL 正式版 2.10.3\dc\.minecraft\versions\DeceasedCraft_CB\PCL\Setup.ini'
& 'C:\Program Files\Java\jdk-17\bin\java.exe' -version
& 'D:\McMod\.gradle\wrapper\dists\gradle-8.8-bin\dl7vupf4psengwqhwktix4v1\gradle-8.8\bin\gradle.bat' --version
```

---

## 5. 风险与待办

1. **CONFIRMED（风险）**：`JAVA_HOME` 与 Gradle 守护进程当前为 JDK 23；使用 ForgeGradle 6.0.54 构建 1.20.1 项目时必须固定 JDK 17（建议 `org.gradle.java.home` 或项目级 `JAVA_HOME`），否则可能出现工具链不兼容。
2. **UNCONFIRMED**：工程目录为空，尚无 `gradle-wrapper.properties`；Gradle 8.8 的 wrapper 生成与 ForgeGradle 6.0.54 的实际构建结果尚未验证。
3. **CONFIRMED（启动级）**：专用服务器已完成安装与启动验证（`Done (1.915s)`，第二轮零 ERROR）；但这只是 mod 加载验证，未进行玩家登录/玩法测试，因此相关玩法功能仍不得标记 `SERVER-TESTED` 或 `VERIFIED`。
