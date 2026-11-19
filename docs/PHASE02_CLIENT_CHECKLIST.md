# PHASE02_CLIENT_CHECKLIST — ParCoolAdapter 客户端人工验证清单

- 文档状态：**IN PROGRESS（首轮已执行，部分通过）**
- 适用阶段：阶段 02 ParCoolAdapter
- 适用版本：ParkourCombat 2.0（阶段 02 实现）
- 关联文档：
  - [PARCOOL_API_AUDIT.md](PARCOOL_API_AUDIT.md)
  - [PHASE02_PREFLIGHT.md](PHASE02_PREFLIGHT.md)
  - [SCAFFOLD_ACCEPTANCE.md](SCAFFOLD_ACCEPTANCE.md)

> 本清单只验证 **客户端适配层状态识别**，不验证 Better Combat 攻击桥接、伤害、词条、动画或任何战斗玩法。
> 阶段 02 已实现；首轮结果见 2.6，未覆盖项仍为 `NOT TESTED`。

---

## 1. 验证目标

1. 六个 ParCool 动作与 FALL 派生状态能被正确识别：
   - FAST_RUN
   - SLIDE
   - VAULT
   - WALL_RUN_HORIZONTAL
   - WALL_RUN_VERTICAL
   - WALL_JUMP
   - FALL（非 ParCool 动作，适配层派生）
2. 开始 / 持续 / 结束 / 取消 / 中断的相位正确。
3. 取消与中断不会被伪装成正常结束；无法确认时输出 `UNKNOWN`。
4. 状态来源与可信度标记正确：
   - `CLIENT_LOCAL`（客户端本地）
   - `PARCOOL_SYNCED`（服务端收到的 ParCool 同步）
   - `VANILLA_DERIVED`（原版物理派生，如 FALL；旧草案中的 `ADAPTER_DERIVED` 同义）
   - `SERVER_NATIVE`（服务端原生，预留）
5. 玩家死亡、切换维度、退出游戏后无残留状态。
6. 客户端不使用动画推断服务端动作状态。

---

## 2. 前置条件

### 2.1 环境

| 项 | 要求 |
|---|---|
| Minecraft | 1.20.1 |
| Forge | 47.4.0 |
| ParCool | 3.4.3.3（锁定） |
| ParkourCombat | 阶段 02 构建产物 |
| Better Combat | 1.9.0+1.20.1（第二遍可选，用于共存检查） |
| Java | 17 |

### 2.2 启动方式

推荐开发客户端：

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon runClient
```

或使用测试整合包加载阶段 02 构建出的 JAR。无论哪种方式，开始前记录：

```text
测试日期：
构建来源 commit：
JAR 文件名 / SHA-256：
runClient 或测试实例：
mods 列表（ParCool / BC / 其他）：
```

### 2.3 测试世界

- 超平坦、和平难度、关闭结构生成。
- 生存模式（保留 ParCool 耐力消耗）；饱食度满。
- 准备：6 格高平台、一面 4×4 墙、一格高栅栏、水池、梯子/藤蔓、船或马、鞘翅。
- 除 ParCool 外，不安时装改动跑酷动作的模组；BC 只在“第二遍共存检查”中启用。

### 2.4 日志与可观测性约定（阶段 02 实现）

客户端适配层必须在**相位变化时**输出 DEBUG 日志（不是每 tick 刷屏），格式至少包含：

```text
[ParkourAdapter] side=CLIENT player=<name> type=<TYPE> phase=<PHASE>
  source=<SOURCE> trust=<TRUST> ticks=<n> serverAuthoritative=<bool>
  inferred=<bool> evidence=<text>
```

FALL 进入/结束额外包含：

```text
[ParkourAdapter] side=... player=... type=FALL phase=<PHASE>
  source=... trust=... serverAuthoritative=<bool> ticks=<n>
  fallStart=<x,y,z> fallDistance=<double> fallVy=<double> endReason=<REASON>
```

要求：

- `PHASE` 取值：`STARTED` / `ACTIVE` / `FINISHED` / `CANCELLED` / `INTERRUPTED` / `UNKNOWN`。
- `SOURCE` 取值：`SERVER_NATIVE` / `PARCOOL_SYNCED` / `CLIENT_LOCAL` / `VANILLA_DERIVED`。
- `TRUST` 取值：`HIGH` / `MEDIUM` / `LOW` / `UNKNOWN`。
- 服务端推断的中断必须带 `inferred=true` 与判定依据。
- 客户端动作在 `[Render thread]`，集成服务端动作在 `[Server thread]`。
- 普通动作生命周期是 **DEBUG**；初始化/重要清理是 **INFO**；异常是 **WARN/ERROR**。
- `run/logs/latest.log` 只保留 INFO，**看不到动作生命周期**；请查看 `run/logs/debug.log`，或开发控制台（`forge.logging.console.level=debug`）。

> 本阶段没有调试命令；以日志为准。
> 如果日志缺少上述字段，相关用例标记为 `BLOCKED`，不能凭感觉判定 PASS。

### 2.5 记录文件

- 动作日志：`run/logs/debug.log`（过滤 `[ParkourAdapter]`）
- 启动/清理日志：`run/logs/latest.log`
- 截图：FALL、墙跑、Vault 各一张（可选）
- 每个用例填“结果 + 实际日志片段 + 备注”

### 2.6 执行记录（两轮，2026-11-19）

#### 第一轮：单侧 adapter（15:03–15:05，构建 `18ea034`）

- FAST_RUN 24 个完整循环、SLIDE 1、VAULT 2、WALL_RUN_HORIZONTAL 2、FALL 21 次 LANDED；CLIENT_LOGOUT 清理成功。
- 无 WALL_RUN_VERTICAL、WALL_JUMP、CANCELLED、INTERRUPTED。
- 无集成服务端 `PARCOOL_SYNCED`（当时只有单侧 adapter）。

#### 第二轮：双 adapter（15:13–15:14，构建 `c1c206b`）

| 类型 | 客户端 S/A/F/I | 服务端 S/A/F/I | 结果 |
|---|---:|---:|---|
| FAST_RUN | 23/23/21/2 | 23/23/21/2 | 双端 PASS |
| SLIDE | 4/4/3/1 | 4/4/3/1 | 双端 PASS |
| VAULT | 8/8/8/0 | 8/8/8/0 | 双端 PASS |
| WALL_RUN_HORIZONTAL | 4/4/4/0 | 4/4/4/0 | 双端 PASS |
| WALL_RUN_VERTICAL | 0 | 0 | NOT TESTED |
| WALL_JUMP | 0 | 0 | NOT TESTED |
| FALL | 36/36 LANDED | 37/37 LANDED | 双端基础落地 PASS |
| CANCELLED | 0 | 0 | NOT TESTED |
| INTERRUPTED | 3（2 FAST_RUN + 1 SLIDE） | 3（同左） | 相位 PASS；元数据修复后待复测 |

- 集成服务端已出现 `source=PARCOOL_SYNCED`、`trust=LOW`、`serverAuthoritative=false`。
- 退出清理双端成功：`CLIENT_LOGOUT` 与 `SERVER LOGOUT`。
- 本轮发现并已修复：
  - `inferred` 反向（客户端 true / 服务端 false）→ `aa96ec9` 改为 `!clientSide` 构造 tracker。
  - 服务端 FALL 错误使用 `PARCOOL_SYNCED/LOW` → 改为 `VANILLA_DERIVED/HIGH`。
- 修复后启动验证通过，但尚未重新跑动作；需确认客户端 INTERRUPTED `inferred=false`、服务端 `inferred=true`、服务端 FALL `VANILLA_DERIVED/HIGH`。
- 证据：[runClient-phase02-adapter-transitions.log](logs/runClient-phase02-adapter-transitions.log)、[runClient-phase02-dual-actions.log](logs/runClient-phase02-dual-actions.log)

---

## 3. 核心用例

> 所有用例默认在**不带 Better Combat** 的客户端执行；第二遍共存检查见第 5 节。
> 结果状态：`PASS` / `FAIL` / `BLOCKED` / `NOT TESTED`。

### TC-01 Fast Run 开始与结束

| 项 | 内容 |
|---|---|
| 前置 | 平地、生存、满饱食度 |
| 步骤 | 1. 原版冲刺；2. 按住 ParCool Fast Run 键直到动作启动；3. 松开按键并停止移动 |
| 预期 | 客户端出现 `FAST_RUN STARTED` → `ACTIVE` → `FINISHED`；`ticks > 0`；`source=CLIENT_LOCAL`；集成服务端出现 `source=PARCOOL_SYNCED`、`trust=LOW`；结束后无残留 active |
| 判定 | 相位顺序正确、无 `CANCELLED`/`UNKNOWN`、无重复 Start |
| 结果 |  |

### TC-02 Slide

| 项 | 内容 |
|---|---|
| 前置 | Fast Run 已激活；移动中 |
| 步骤 | 按 ParCool 的 Crawl/Slide 键进入滑铲，保持到自然结束 |
| 预期 | `SLIDE STARTED` → `ACTIVE` → `FINISHED`；滑铲期间 Fast Run 不残留 active；`ticks` 不超过配置上限附近 |
| 判定 | Slide 与 Fast Run 状态不串线 |
| 结果 |  |

### TC-03 Vault

| 项 | 内容 |
|---|---|
| 前置 | Fast Run；前方一格高栅栏/矮墙 |
| 步骤 | 朝障碍物冲刺并触发 Vault（若配置需要按键则按键） |
| 预期 | `VAULT STARTED` → `ACTIVE` → `FINISHED`，持续约 11 tick 内；不误判为 Fall/Wall Run |
| 判定 | 类型正确、结束清理正确 |
| 结果 |  |

### TC-04 Wall Run — 水平

| 项 | 内容 |
|---|---|
| 前置 | 空中；侧面贴墙；Fast Run |
| 步骤 | 沿墙水平方向跑墙，保持按键，直到离开墙面 |
| 预期 | `WALL_RUN_HORIZONTAL STARTED` → `ACTIVE` → `FINISHED`；不得出现 `WALL_RUN_VERTICAL` |
| 判定 | 两个墙跑类型不混淆 |
| 结果 |  |

### TC-05 Wall Run — 垂直

| 项 | 内容 |
|---|---|
| 前置 | 面对墙；视线略高于水平；Fast Run |
| 步骤 | 跳向墙面并保持跑墙键，向上蹬墙 |
| 预期 | `WALL_RUN_VERTICAL STARTED` → `ACTIVE` → `FINISHED`；不得出现 `WALL_RUN_HORIZONTAL` |
| 判定 | 两个墙跑类型不混淆 |
| 结果 |  |

### TC-06 Wall Jump

| 项 | 内容 |
|---|---|
| 前置 | 空中贴墙；未接地 |
| 步骤 | 按 Wall Jump 键蹬墙跳 |
| 预期 | `WALL_JUMP STARTED` → `FINISHED`（短动作）；不与 Wall Run 重复或冲突 |
| 判定 | 类型正确、无重复 Start |
| 结果 |  |

### TC-07 FALL — 跳跃上升/下降/落地

| 项 | 内容 |
|---|---|
| 前置 | 6 格高平台；生存；无液体 |
| 步骤 | 1. 从平台边缘向前跳；2. 观察上升阶段；3. 下降并落地 |
| 预期 | **上升阶段不得出现 FALL**；下降后（`deltaMovement.y < 0` 且 `fallDistance > 0.5`）出现 `FALL STARTED`；`fall.start` 接近起跳/顶点位置；`fall.distance` 持续增加；`fall.vy` 为负；落地后 `FINISHED`，`endReason=LANDED`，`onGround=true`；客户端 `source=VANILLA_DERIVED`；集成服务端 `serverAuthoritative=true` |
| 判定 | 上升阶段若出现 FALL 即 FAIL；落地后无残留 active |
| 结果 |  |

### TC-08 FALL — 排除场景

| 子项 | 步骤 | 预期 |
|---|---|---|
| TC-08a 水 | 跳入水中 | 不进入 FALL；若已进入，结束原因 `WATER` |
| TC-08b 鞘翅 | 装备鞘翅滑翔 | 不进入 FALL；结束原因 `FLYING` |
| TC-08c 攀爬 | 梯子/藤蔓上下 | 不进入 FALL；结束原因 `CLIMBING` |
| TC-08d 载具 | 船/马/矿车移动 | 不进入 FALL；结束原因 `VEHICLE` |
| TC-08e 创造飞行 | 创造模式飞行 | 不进入 FALL；结束原因 `FLYING` |

判定：每个子项单独记录 PASS/FAIL；不得用“主用例 PASS”代替。

### TC-09 FALL — 传送/落地异常

| 项 | 内容 |
|---|---|
| 步骤 | 下落中传送（末影珍珠或 `/tp`） |
| 预期 | FALL 结束，`endReason=TELEPORT` 或 `UNKNOWN`；不残留 active |
| 判定 | 允许 UNKNOWN，但必须有依据 |
| 结果 |  |

### TC-10 中断观察（客户端）

| 项 | 内容 |
|---|---|
| 前置 | Fast Run 已激活 |
| 步骤 | 在 Fast Run 期间触发另一个跑酷动作（如 Slide 或 Wall Jump） |
| 预期 | 旧动作应为 `FINISHED` 或 `INTERRUPTED`；如果 `INTERRUPTED`，必须记录 `evidence=REPLACED_BY(<TYPE>)` 和 tick；不得出现两个同时 active 且永远不清除 |
| 判定 | 若客户端日志显示精确中断依据，记录为 INTERRUPTED；否则记录实际 FINISHED，不做伪造 |
| 结果 |  |

### TC-11 服务端启发式中断观察

| 项 | 内容 |
|---|---|
| 前置 | 集成服务器日志可见 |
| 步骤 | 同 TC-10 |
| 预期 | 若服务端推断为 `INTERRUPTED`，日志必须带 `inferred=true`、依据和 tick；正常结束必须是 `FINISHED`；无法判断时 `UNKNOWN` |
| 判定 | FINISHED 与 INTERRUPTED 不混合；推断有依据 |
| 结果 |  |

### TC-12 死亡清理

| 项 | 内容 |
|---|---|
| 步骤 | `/kill` 自杀，等待重生 |
| 预期 | 死亡/Clone 后该玩家所有 active 状态清空；日志出现清理记录；重生后新动作可正常识别；无旧 FALL/旧动作残留 |
| 判定 | 清理后 `getActiveActions` 为空 |
| 结果 |  |

### TC-13 切换维度清理

| 项 | 内容 |
|---|---|
| 步骤 | 进入下界传送门，再返回 |
| 预期 | 切换维度后清除旧状态；新维度中新动作正常；无跨维度残留 active |
| 判定 | 维度切换前后状态不串联 |
| 结果 |  |

### TC-14 退出/重进清理

| 项 | 内容 |
|---|---|
| 步骤 | 退出到标题界面，再进入世界 |
| 预期 | 客户端缓存清空；重新进入后无旧状态；新动作正常 |
| 判定 | 无 `Player` 引用泄漏迹象（状态数不增长） |
| 结果 |  |

### TC-15 来源与可信度标记

| 项 | 内容 |
|---|---|
| 步骤 | 在单人游戏中执行 TC-01 与 TC-07 |
| 预期 | 客户端动作：`source=CLIENT_LOCAL`；集成服务端 ParCool 动作：`source=PARCOOL_SYNCED`、`trust=LOW`、`serverAuthoritative=false`；服务端 FALL：`source=VANILLA_DERIVED`、`trust=HIGH`、`serverAuthoritative=true` |
| 判定 | 任何把服务端 ParCool 同步状态标成 HIGH/authoritative 的行为均 FAIL |
| 结果 |  |

---

## 4. 第二遍：与 Better Combat 共存检查

仅验证“安装 BC 后 Adapter 仍正常”，**不测试** BC 攻击桥接。

| 用例 | 内容 | 预期 |
|---|---|---|
| TC-16 | 安装 BC 1.9.0+1.20.1、Player Animator、Cloth Config，重启客户端 | 正常进入主菜单；无缺失依赖 |
| TC-17 | 重复 TC-01（Fast Run） | 识别结果与无 BC 时一致 |
| TC-18 | 重复 TC-07（FALL） | FALL 字段与结束原因一致 |
| TC-19 | 执行一次普通攻击（不验证伤害） | Adapter 状态不因 BC 攻击事件产生虚假跑酷状态 |

---

## 5. 证据收集

每个用例完成后记录：

```text
用例编号：
结果：PASS / FAIL / BLOCKED / NOT TESTED
实际相位序列：
关键日志（复制 [ParkourAdapter] 行）：
截图/录像（可选）：
备注：
```

整体日志收集：

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
Copy-Item 'run\logs\latest.log' "docs\client-evidence\$(Get-Date -Format yyyyMMdd-HHmm)-latest.log"
```

同时记录：

```powershell
git rev-parse HEAD
Get-FileHash 'build\libs\parkourcombat-2.0.0-alpha.1.jar' -Algorithm SHA256
```

---

## 6. 结果汇总模板

| 用例 | 结果 | 备注 |
|---|---|---|
| TC-01 Fast Run |  |  |
| TC-02 Slide |  |  |
| TC-03 Vault |  |  |
| TC-04 Wall Run H |  |  |
| TC-05 Wall Run V |  |  |
| TC-06 Wall Jump |  |  |
| TC-07 Fall 基本 |  |  |
| TC-08a 水 |  |  |
| TC-08b 鞘翅 |  |  |
| TC-08c 攀爬 |  |  |
| TC-08d 载具 |  |  |
| TC-08e 创造飞行 |  |  |
| TC-09 传送 |  |  |
| TC-10 客户端中断 |  |  |
| TC-11 服务端推断 |  |  |
| TC-12 死亡清理 |  |  |
| TC-13 维度清理 |  |  |
| TC-14 退出清理 |  |  |
| TC-15 来源/可信度 |  |  |
| TC-16 BC 共存启动 |  |  |
| TC-17 BC 下 Fast Run |  |  |
| TC-18 BC 下 Fall |  |  |
| TC-19 BC 攻击不影响 Adapter |  |  |

**整体结论**：`PASS` / `PASS WITH KNOWN ISSUES` / `FAIL` / `BLOCKED`（只能由实际测试结果决定）。

---

## 7. 失败记录要求

失败时提供：

1. 用例编号与复现步骤。
2. 预期 vs 实际相位序列。
3. 日志片段（前后各 10 行）。
4. 是否可稳定复现。
5. 客户端/服务端侧别（`[Render thread]` / `[Server thread]`）。
6. 构建 commit 与 JAR SHA-256。

禁止：

- 用动画表现推断服务端状态。
- 把 `FINISHED` 当作 `INTERRUPTED` 或反之。
- 对无法判断的情况强行标记 PASS。
- 修改日志内容。

---

## 8. 完成标准

阶段 02 客户端验证只有在以下条件满足时才可标记 `TESTED`：

- TC-01 至 TC-19 全部有明确结果；
- 所有 FAIL 已记录并可复现；
- 所有 UNKNOWN 都有依据；
- 死亡/维度/退出清理无残留；
- 来源与可信度标记符合 TC-15。

即使客户端清单通过，也只能标记为“客户端验证通过”；服务端权威性、战斗结算与完整功能仍需后续阶段验证，不能直接标记 `VERIFIED`。
