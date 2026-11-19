# PHASE02_PREFLIGHT — 阶段 02 前置核查记录

- 日期：2026-11-19
- 适用阶段：ParCoolAdapter 实现前
- 首次提交：`04082120d9ed14ee4c7fa2a8f996f1a967dff271`
- 结论标记：**PASS** / **PARTIALLY_CONFIRMED** / **NOT TESTED**

---

## 1. Better Combat 声明为 OPTIONAL 的原因与缺少时的加载安全性

### 配置证据

`src/main/resources/META-INF/mods.toml`：

```toml
[[dependencies.parkourcombat]]
modId = "bettercombat"
mandatory = false
versionRange = "[1.9.0+1.20.1]"
ordering = "AFTER"
side = "BOTH"
```

原因：

- 本阶段（阶段 02）只实现 ParCoolAdapter，**没有引用 Better Combat 的任何类或 API**。
- 若把 BC 设为 mandatory，则只安装 ParCool 的服务器/客户端会无法启动，违背“Adapter 可独立于 BC 工作”的目标。
- 未来阶段真正实现 Better Combat 桥接时，再根据实际代码引用决定是否改为 mandatory。

### 代码证据

- `ParkourCombat.java` 仅导入 `net.minecraftforge.fml.common.Mod`，无 `net.bettercombat` 引用。
- 构建产物 `build/libs/parkourcombat-2.0.0-alpha.1.jar` 中只有 `com/MrGhost/parkourCombat/ParkourCombat.class` 与本模组资源，无 BC 类。

### 运行证据（无 Better Combat）

**专用服务器（干净世界）**：

- 测试方式：生产服务器仅保留 `ParCool-1.20.1-3.4.3.3.jar` + `parkourcombat-2.0.0-alpha.1.jar`，移除 BC、PlayerAnimator、Cloth Config、Patchouli，新建世界 `nobc_world`。
- 日志：`docs/logs/noBetterCombat-server.log`
- 结果：`Done (2.141s)!`
- 加载：`{parcool} 3.4.3.3`、`{parkourcombat} 2.0.0-alpha.1`
- 没有 `MISSING`、没有 `Unidentified mapping`；只有 Patchouli 缺失导致的两条指南书数据错误（见第 5 节）。

**客户端（临时工程副本，无 BC）**：

- 测试方式：复制工程到临时目录，删除 `build.gradle` 中 BC/PlayerAnimator/Cloth Config 依赖，仅保留 ParCool，运行 `runClient`。
- 日志：`docs/logs/noBetterCombat-client.log`
- 结果：`Setting user: Dev`、`Sound engine started`，进入主菜单；加载 `{parkourcombat}` 与 `{parcool}`，没有 BC；无 `FATAL`、无 Mixin 失败。

**结论：PASS**。缺少 Better Combat 时，本模组在客户端与服务端均可加载；阶段 02 不依赖 BC。

---

## 2. Player Animator / Cloth Config 依赖声明

### Better Combat 的强制依赖（实际 `mods.toml`）

从 `libs/bettercombat-forge-1.9.0+1.20.1.jar!/META-INF/mods.toml` 读取：

```
line 62: modId="playeranimator"
   mandatory=true
   versionRange="[1,)"
   ordering="AFTER"
   side="BOTH"

line 68: modId="cloth_config"
   mandatory=true
   versionRange="[11.0.0,)"
   ordering="AFTER"
   side="BOTH"
```

### 本工程的声明与版本

`build.gradle`：

```groovy
implementation fg.deobf('local.bettercombat:bettercombat-forge:1.9.0+1.20.1')
implementation fg.deobf('local.playeranimator:player-animation-lib-forge:1.0.2-rc1+1.20')
implementation fg.deobf('local.clothconfig:cloth-config:11.1.136-forge')
```

- Player Animator `1.0.2-rc1+1.20` 满足 BC 的 `[1,)`。
- Cloth Config `11.1.136` 满足 BC 的 `[11.0.0,)`。
- 实际运行（`docs/logs/runServer.latest.log`、`runClient.latest.log`）中 BC 加载成功，没有缺失依赖错误。

### 为什么不在本模组的 `mods.toml` 中声明它们

- 本模组代码当前没有直接引用 Player Animator 或 Cloth Config。
- 它们通过 BC 的 `mods.toml` 被 BC 声明为强制依赖；只要 BC 存在，Forge 会校验。
- 若 BC 不存在，本模组也不需要它们。
- 因此由 BC 声明、本模组不重复声明是正确的。

### 注意

`build.gradle` 目前对 Player Animator / Cloth Config 使用 `implementation`（编译期可见），但本模组未使用其 API。这不会导致打包（第 3 节），但属于比必要更宽的 scope；若后续希望严格收紧，可改为 `runtimeOnly`（需要另行评审与重新构建，本阶段未改）。

**结论：PASS（版本满足、运行时加载成功）；编译 scope 可后续收紧。**

---

## 3. 本地依赖是否会重复打包 / 运行时是否正确加载

### 不打包

`build/libs/parkourcombat-2.0.0-alpha.1.jar` 内容（`jar tf`）：

```
META-INF/
META-INF/MANIFEST.MF
META-INF/mods.toml
com/
com/MrGhost/
com/MrGhost/parkourCombat/
com/MrGhost/parkourCombat/ParkourCombat.class
pack.mcmeta
```

- JAR 大小 2,137 B。
- 无 `dev/kosmx`、`net/bettercombat`、`com/alrex`、`net/minecraft`、`net/minecraftforge` 条目。
- `build.log` 显示 `jarJar SKIPPED`、`reobfJarJar SKIPPED`；`build.gradle` 没有 `shadow` 插件、没有 `jarJar` 配置。

### 运行时加载

开发运行日志（`runServer.debug.log` / `runClient.debug.log`）显示各模组作为**独立 mod 文件**加载：

```
Found valid mod file ParCool-1.20.1-3.4.3.3_mapped_official_1.20.1.jar with {parcool} ...
Found valid mod file bettercombat-forge-1.9.0+1.20.1_mapped_official_1.20.1.jar with {bettercombat} ...
Found valid mod file player-animation-lib-forge-1.0.2-rc1+1.20_mapped_official_1.20.1.jar with {playeranimator} ...
Found valid mod file cloth-config-11.1.136-forge_mapped_official_1.20.1.jar with {cloth_config} ...
Found valid mod file main with {parkourcombat} ...
```

- `fg.deobf` 只生成 deobf 副本供开发运行，不把类写入本模组 JAR。
- 发布 JAR 不包含第三方类，也不在 `mods.toml` 中声明内嵌依赖。

**结论：PASS**。

---

## 4. `mixin.env.remapRefMap` 的必要性与作用范围

### 为什么需要

ParCool 生产 JAR 的 refmap（`parcool.refmap.json`）把 Mixin 目标映射为 **SRG 名**：

```json
{
  "mappings": {
    "com/alrex/parcool/mixin/common/EntityMixin": {
      "getEyeHeight()F": "Lnet/minecraft/world/entity/Entity;m_20192_()F",
      ...
    }
  }
}
```

开发运行使用 official mappings，`Entity` 的方法是 `getEyeHeight()`，不存在 `m_20192_()`。因此 Mixin 首次运行失败：

```
InvalidInjectionException: @Inject annotation on onGetEyeHeight could not find any targets matching
'Lnet/minecraft/world/entity/Entity;m_20192_()F' ... Using refmap parcool.refmap.json
```

（该失败的完整记录与修复过程见 `docs/SCAFFOLD_ACCEPTANCE.md` 第 5 节。）

修复：在 **dev run 配置**中启用 refmap 重映射：

```groovy
property 'mixin.env.remapRefMap', 'true'
property 'mixin.env.refMapRemappingFile', "${projectDir}/build/createSrgToMcp/output.srg"
```

映射文件实际内容：

```
MD: net/minecraft/world/entity/Entity/m_20192_ ()F net/minecraft/world/entity/Entity/getEyeHeight ()F
```

### 作用范围

- `build.gradle` 中仅出现在 `client` 与 `server` 的 run 配置（第 29-30、43-44 行）。
- 不进入 `processResources`、不写入 `mods.toml`、不进入构建 JAR。
- 生产运行不需要：生产环境使用 SRG 方法名，refmap 原本就是 SRG，匹配正常。

**结论：PASS。必要且仅作用于开发运行配置。**

---

## 5. Patchouli 缺失时 ParCool 报错的影响范围

### 实际日志（无 Patchouli）

`docs/logs/runServer.latest.log` / `noBetterCombat-server.log`：

```
Couldn't parse element loot_tables:parcool:grant_parcool_guide
  Expected name to be an item, was unknown string 'patchouli:guide_book'
Parsing error loading recipe parcool:parcool_guide
  Unknown item 'patchouli:guide_book'
```

只有这两条资源解析错误；服务端仍到达 `Done`。

### 资源位置

- `data/parcool/loot_tables/grant_parcool_guide.json`：奖励 `patchouli:guide_book`（带 `patchouli:book=parcool:parcool_guide` NBT）。
- `data/parcool/recipes/parcool_guide.json`：合成结果 `patchouli:guide_book`。
- `data/parcool/advancements/grant_parcool_guide.json`：奖励上述战利品表。
- 指南书内容全部位于 `assets/parcool/patchouli_books/parcool_guide/**`。

### 核心动作是否受影响

- ParCool 代码中的 `parcool_guide` 引用只有：
  - `Items.java`：`PARCOOL_GUIDE = ITEMS.register("parcool_guide", () -> new Item(...))`（普通 `Item`，不继承 Patchouli 类）；
  - `CreativeTabs.java`：创造模式标签页展示该物品。
- 没有动作类、状态机、事件或网络代码引用 `parcool_guide` / `grant_parcool_guide` / `patchouli`。
- 服务端在缺失 Patchouli 时成功生成 `world/serverconfig/parcool-server.toml`（包含全部 27 个动作权限），说明核心动作系统已初始化。
- 阶段 01 中带 Patchouli 的服务端启动为零错误（见 `docs/SCAFFOLD_ACCEPTANCE.md` 引用的阶段 01 记录）。

### 影响结论

- **CONFIRMED**：缺失 Patchouli 只导致“指南书”战利品表/配方解析失败；指南书物品本身仍注册，但无法正常打开（没有 Patchouli 书内容）。
- **CONFIRMED**：核心跑酷动作、状态同步、配置生成不受影响。
- 残余影响：日志会出现两条 ERROR；若希望零错误，可安装 Patchouli（可选依赖，不加入锁定依赖集）。

**结论：PASS（仅指南书内容受影响，核心跑酷不受影响）。**

---

## 6. 核查结论汇总

| 核查项 | 结论 | 证据 |
|---|---|---|
| BC optional 原因与无 BC 加载 | **PASS** | `mods.toml`；无 BC 服务端 `Done (2.141s)`；无 BC 客户端主菜单；`docs/logs/noBetterCombat-*.log` |
| Player Animator / Cloth Config 声明 | **PASS** | BC `mods.toml` 强制依赖；版本满足；运行加载成功 |
| 本地依赖不重复打包、运行时独立加载 | **PASS** | JAR 仅 2,137 B/仅本模组类；`jarJar SKIPPED`；debug 日志独立 mod 文件 |
| `mixin.env` 必要性与 dev-only | **PASS** | ParCool refmap SRG→official 差异；映射文件；仅 run 配置 |
| Patchouli 缺失影响范围 | **PASS** | 仅两条指南书资源错误；核心动作/配置正常 |

本文件只记录核查，不包含阶段 02 代码变更。
