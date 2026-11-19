# PHASE_02_TEST_PLAN — ParCoolAdapter 测试计划

- 阶段：02
- 状态：**PLANNED（尚未执行）**
- 前置：阶段 02 代码实现完成并通过编译
- 关联：
  - [PHASE_02_IMPLEMENTATION_PLAN.md](PHASE_02_IMPLEMENTATION_PLAN.md)
  - [PHASE_02_API_EVIDENCE.md](PHASE_02_API_EVIDENCE.md)
  - [PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md)

> 本文件定义测试方法与预期结果，不代表测试已执行。未执行项必须标记 `NOT TESTED`。

---

## 1. 测试层级

| 层级 | 执行者 | 目的 |
|---|---|---|
| 单元测试 | AI | 纯状态机/映射/清理逻辑 |
| 编译与构建 | AI | 代码可编译、JAR 可生成 |
| 服务端启动 | AI | adapter 在专用服务器加载、无类加载崩溃 |
| 客户端人工 | 用户 | 真实跑酷动作、FALL、取消/中断、清理 |
| 多人 | 用户 | 双客户端 + 专用服务器同步与隔离 |

---

## 2. 单元测试

纯 Java 测试，不启动 Minecraft；通过输入快照驱动 `ParkourActionTracker`、`FallStateMachine`/`MotionTracker`。

### 2.1 动作状态机

| 编号 | 用例 | 预期 |
|---|---|---|
| U-01 | Start.Pre → 下一 tick `isDoing=true` | `STARTED` → `ACTIVE`；ticks 递增 |
| U-02 | 连续 10 tick `isDoing=true` | `ACTIVE`，ticks=10 |
| U-03 | 正常 Finish.Pre | `FINISHED`，active=false，清理 |
| U-04 | 客户端 TryToStart 被取消 | `CANCELLED`，active=false，未进入 ACTIVE |
| U-05 | 客户端 TryToContinue 被取消后 Finish | `CANCELLED` 而非 `FINISHED` |
| U-06 | A active，B Start，A 在 1 tick 内 Finish | A `INTERRUPTED`，evidence=`REPLACED_BY(B)` |
| U-07 | 服务端 A active，B Start，A 1 tick 内 Finish | A `INTERRUPTED`，`inferred=true` |
| U-08 | 服务端 A active，`isDoing=false` 2 tick 且 B active | A `INTERRUPTED`，evidence=`SYNC_LOST_REPLACED_BY(B)` |
| U-09 | 服务端 A active，`isDoing=false` 2 tick 且无 B | A `UNKNOWN`，evidence=`SYNC_LOST` |
| U-10 | 无 Start 的 Finish | `UNKNOWN`，evidence=`ORPHAN_FINISH` |
| U-11 | 重复 Start 同一动作 | 不重复创建；证据记录 `DUPLICATE_START` |
| U-12 | 两动作同时 active | 各自独立；不互相覆盖 |
| U-13 | `WALL_RUN` 聚合查询 | H 或 V active → 聚合 active；两者都无 → 无状态 |
| U-14 | H/V 同时 active | 两个具体状态同时保留；聚合 evidence=`BOTH` |
| U-15 | 状态来源/可信度 | 服务端 ParCool=LOW/PARCOOL_SYNCED；客户端=CLIENT_LOCAL；FALL=VANILLA_DERIVED |

### 2.2 FALL 状态机

| 编号 | 用例 | 预期 |
|---|---|---|
| F-01 | 平地站立 | NOT_FALLING，不创建 FALL |
| F-02 | 跳跃上升（y>0） | NOT_FALLING |
| F-03 | 跳跃下降（y<0，fallDistance>0.5） | FALLING；startPosition 记录 |
| F-04 | 下落中 sampled 数据 | fallDistance 增加、vy 为负、duration 递增 |
| F-05 | 落地 | FALL_END，`endReason=LANDED`，onGround=true |
| F-06 | 入水 | FALL_END，`WATER` |
| F-07 | 入岩浆 | FALL_END，`LAVA` |
| F-08 | 鞘翅飞行 | FALL_END，`FLYING` |
| F-09 | 攀爬 | FALL_END，`CLIMBING` |
| F-10 | 载具 | FALL_END，`VEHICLE` |
| F-11 | 单 tick 位移 >64 或维度切换 | `TELEPORT` / `DIMENSION_CHANGE` |
| F-12 | 死亡/退出 | `DEATH` / `LOGOUT`，清理 |
| F-13 | FALL_END 保留 40 tick | 到期回 NOT_FALLING |
| F-14 | 服务端 FALL | source=VANILLA_DERIVED、trust=HIGH、serverAuthoritative=true |
| F-15 | 客户端 FALL | source=VANILLA_DERIVED、serverAuthoritative=false |

### 2.3 清理与内存

| 编号 | 用例 | 预期 |
|---|---|---|
| C-01 | 玩家退出 | 该 UUID 所有状态移除 |
| C-02 | 死亡/Clone | 清理，不复制旧状态 |
| C-03 | 维度切换 | 清理；下一 tick 重建 |
| C-04 | 世界卸载 | 该维度玩家状态清理 |
| C-05 | 服务器停止 | 全部清空 |
| C-06 | 客户端 LoggingOut/Clone | 本地清空 |
| C-07 | 1000 次开始/结束循环 | 状态数不增长（无泄漏） |
| C-08 | 无界缓存检查 | 每玩家最多 7 动作 + 1 FALL；无历史列表 |

### 2.4 版本守卫

| 编号 | 用例 | 预期 |
|---|---|---|
| V-01 | ParCool 版本 3.4.3.3 | adapter 启用 |
| V-02 | ParCool 版本不匹配 | 记录错误并禁用 adapter，不猜测 API |

---

## 3. 编译与构建

```powershell
Set-Location 'D:\MinecraftParcoolCombat'
$env:JAVA_HOME='C:\Program Files\Java\jdk-17'
.\gradlew.bat --no-daemon test
.\gradlew.bat --no-daemon compileJava
.\gradlew.bat --no-daemon build
```

预期：

- 单元测试全部通过。
- `compileJava` / `build` 成功。
- 构建 JAR 不包含第三方类；`jarJar` 仍 SKIPPED。
- 不新增 Mixin 配置。

---

## 4. 服务端测试

### S-01 仅 ParCool + 本模组

- 启动 `runServer`。
- 预期：到达 `Done`；adapter 初始化；无 ClassNotFound / NoSuchMethod。
- 无客户端时无法验证动作识别；标记相关项 NOT TESTED。

### S-02 ParCool + Better Combat + PlayerAnimator + Cloth Config

- 预期：到达 `Done`；adapter 与 BC 共存；无 Mixin 失败。
- 仍不验证 BC 攻击桥接（本阶段不做）。

### S-03 版本守卫

- 仅在测试环境替换 ParCool 版本（**不得修改锁定 JAR**；使用临时测试副本），预期 adapter 禁用并记录明确错误。
- 若无法安全构造该测试，标记 NOT TESTED。

### S-04 专用服务器生命周期

- 玩家登录/退出/死亡（如可行）后，日志无残留状态；无玩家时缓存为空。
- 无客户端时部分项 NOT TESTED。

---

## 5. 客户端人工测试

按 [PHASE02_CLIENT_CHECKLIST.md](PHASE02_CLIENT_CHECKLIST.md) 执行 TC-01～TC-19：

- TC-01～06：Fast Run、Slide、Vault、Wall Run H/V、Wall Jump。
- TC-07～09：FALL 基本、排除场景、传送。
- TC-10～11：客户端/服务端中断。
- TC-12～15：死亡、维度、退出清理、来源/可信度。
- TC-16～19：与 BC 共存。

预期日志格式见实施计划第 9 节；每条用例记录 PASS/FAIL/BLOCKED/NOT TESTED 与日志片段。

---

## 6. 多人测试

前置：专用服务器 + 两个客户端（A/B）。

| 编号 | 场景 | 预期 |
|---|---|---|
| M-01 | A 执行 Fast Run，B 观察 | A 客户端 CLIENT_LOCAL；服务端 PARCOOL_SYNCED；B 不产生 A 的本地状态 |
| M-02 | A/B 各自执行动作 | 状态按 UUID 隔离，不串用 |
| M-03 | A 退出再登录 | A 旧状态清除；新状态正常 |
| M-04 | A 死亡重生 | 旧状态清除；不复制 |
| M-05 | A 切维度 | 旧维度状态清除；新维度正常 |
| M-06 | 服务端重启 | 缓存全部清空 |
| M-07 | 高延迟/丢包（可选） | 服务端状态不伪装精确；超时→UNKNOWN/INTERRUPTED 有依据 |

本阶段没有新增网络包；多人测试只验证 ParCool 现有同步与 adapter 的侧别隔离。

---

## 7. 结果记录模板

| 编号 | 结果 | 证据 | 备注 |
|---|---|---|---|
| U-01 |  |  |  |
| ... |  |  |  |
| TC-01 |  |  |  |
| M-01 |  |  |  |

结果取值：`PASS` / `FAIL` / `BLOCKED` / `NOT TESTED`。

证据：

- `docs/logs/` 下的日志。
- `[ParkourAdapter]` 关键行。
- 客户端截图/录像（可选）。
- 构建 commit 与 JAR SHA-256。

---

## 8. 通过标准

阶段 02 可标记 `TESTED` 的前提：

1. 单元测试全部 PASS。
2. `compileJava` / `build` PASS。
3. `runServer` 有/无 BC 均 `Done`。
4. 客户端 TC-01～TC-19 有明确结果；FAIL 全部记录。
5. 死亡/退出/维度清理无残留。
6. 来源/可信度符合设计；服务端 ParCool 状态不得标 HIGH/authoritative。
7. 不出现无依据的 `FINISHED`/`INTERRUPTED`。

即使以上全部通过，也只能标记“阶段 02 客户端/服务端验证通过”；整体功能 `VERIFIED` 仍需后续战斗阶段验收。
