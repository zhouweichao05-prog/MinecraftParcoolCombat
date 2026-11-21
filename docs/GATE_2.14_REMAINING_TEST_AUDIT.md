# GATE_2.14_REMAINING_TEST_AUDIT.md

> Gate 2.14 — 剩余测试盘点与人工/自动化分工  
> 分支：`gate-2.14-test-fixes`  
> HEAD：`df70a74019476168b7ad5326849b1188dd2400a0`  
> 当前测试基线：142/142 passed  
> 本报告只做盘点，不修改源码、测试或 Git 状态。

---

## 1. 核实结果

当前工作区：

```text
分支：gate-2.14-test-fixes
HEAD：df70a74019476168b7ad5326849b1188dd2400a0
已跟踪文件：无未提交修改
未跟踪：仅运行日志
```

当前测试：

```text
142/142 passed
```

测试类：

```text
CombatGate214CompletionTest          18
CombatGate214RegressionTest           9
CombatGate210RegressionTest           7
CombatGate28RegressionTest            8
CombatAttackSessionRemediationTest   24
其他基线                              76
总计                                 142
```

Gate 2.13.1 设计目标为 159 项，但 159 不是必须逐项对应的物理测试数；应按设计约束逐项判定。

---

## 2. 已由当前自动化覆盖的部分

### 2.1 S01-S10

| 编号 | 当前测试 | 状态 |
|---|---|---|
| S01 | `s01TargetBudgetOverflowSaturatesThroughAdmit` | PASS |
| S02 | `s02PartBudgetOverflowSaturatesThroughAdmit` | PASS |
| S03 | `s03OverflowEventSaturatesThroughSessionProductionPath` | PASS |
| S04 | `s04OverflowCanceledSaturatesThroughSessionProductionPath` | PASS |
| S05 | `s05EventDetailOverflowSaturatesThroughSessionProductionPath` | PASS |
| S06 | `s06TruncatedTargetCounterUsesSessionAddEventProductionPath` | PASS |
| S06 saturation | `s06TruncatedTargetCounterSaturatesThroughSessionProductionPath` | PASS |
| S07 | `s07LateEventSaturatesThroughTrackerDispatch` | PASS |
| S08 | `s08LatePartSaturatesThroughTrackerDispatch` | PASS |
| S09 | `s09LateCanceledSaturatesThroughTrackerDispatch` | PASS |
| S10 | `s10LateUnknownSaturatesThroughTruncatedDispatch` | PASS |

### 2.2 容量边界

| 项目 | 测试 | 状态 |
|---|---|---|
| 511/512 唯一 UUID | `lookup511And512AreNotTruncated` | PASS |
| 513 截断 | `lookup513TruncatesWithoutStoring` | PASS |
| 重复不占容量 | `lookupDuplicateDoesNotConsumeCapacityOrTruncate` | PASS |
| parent/普通同 UUID 去重 | `parentAndNormalUUIDShareOneLookupSlot` | PASS |
| 混合来源 511/512/513 | `mixedSourceLookup511512513BoundaryThroughTracker` | PASS |
| 混合 parent 重复 | `duplicateParentDoesNotConsumeMixedLookupCapacity` | PASS |

### 2.3 C09 隔离

当前通过：

```text
parentLateEventDoesNotReachActiveB
requestedPartParentIsIncludedInLookup
repeatedPartLateCountsEachObservation
noActiveSessionOnlyCountsDiagnostics
sameTargetConsecutiveAttackIsConservativelyLate
truncatedLookupUsesUnknownCounterNotConfirmedLate
```

这些是 `PACKAGE_PRIVATE_SEAM`，不是真实 Forge Event 测试。

### 2.4 H09

当前通过：

```text
priorPositiveDamageThenCancelDoesNotProduceValidDamage
priorZeroDamageThenCancelDoesNotProducePositive
cancelingOneTargetDoesNotAffectAnotherValidTarget
canceledChainCannotBeReactivated
```

---

## 3. 仍未完成的项目

### 3.1 真实 Forge 事件链

| ID | 目标 | 当前 | 缺口 |
|---|---|---|---|
| C09-REAL-01 | 真实 AttackEntityEvent 普通目标 | NOT_TESTED | 无真实 Forge 事件对象 |
| C09-REAL-02 | 真实 LivingAttack/Hurt/Damage 链 | NOT_TESTED | 无真实事件序列 |
| C09-REAL-03 | 真实 PartEntity / getEntityOrPart | NOT_TESTED | 无真实世界/实体 |
| C09-REAL-04 | A/B parent late 真实分发 | NOT_TESTED | 当前是 package seam |
| C09-REAL-05 | 无 active Session 真实事件 | NOT_TESTED | 当前是 seam |
| H09-REAL-01 | 真实伤害后取消 | NOT_TESTED | 当前是 Session 单元路径 |

### 3.2 生命周期与多人

| ID | 目标 | 当前 |
|---|---|---|
| LIFE-01 | 玩家死亡/重生清理 | NOT_TESTED |
| LIFE-02 | 玩家退出清理 | NOT_TESTED |
| LIFE-03 | 维度切换清理 | NOT_TESTED |
| LIFE-04 | 服务器停止竞态 | NOT_TESTED |
| MP-01 | 两人攻击隔离 | NOT_TESTED |
| SYNC-01 | 客户端/专用服务器同步 | NOT_TESTED |

### 3.3 Parkour 动作与攻击观察

| ID | 场景 | 当前 |
|---|---|---|
| PARK-01 | FAST_RUN 中攻击 | NOT_TESTED |
| PARK-02 | SLIDE 中攻击 | NOT_TESTED |
| PARK-03 | VAULT 中攻击 | NOT_TESTED |
| PARK-04 | WALL_RUN 中攻击 | NOT_TESTED |
| PARK-05 | WALL_JUMP 中攻击 | NOT_TESTED |
| PARK-06 | FALL 中攻击 | NOT_TESTED |

### 3.4 CNT13 剩余语义

当前已有覆盖：

- 64/65 目标；
- Part 共享预算；
- overflow event/canceled；
- 第 9/10 条详情；
- 首次截断；
- 511/512/513；
- 计数器饱和。

仍需人工/集成确认：

- 真实多目标事件顺序下的 `eventDetailTruncatedTargetCount`；
- 真实多目标与 PartEntity 混合的 detail 截断；
- 真实 ServerTickEvent START/END 生命周期；
- `lateUnknownEventCount` 在真实 Tick/Context 清理下的行为。

---

## 4. 责任分类

### HUMAN（人工游戏测试）

- PARK-01 至 PARK-06；
- 真实普通怪物攻击观察；
- 真实 PartEntity（末影龙/多部件实体）；
- 玩家死亡/重生/登出；
- 维度切换；
- 服务器停止；
- 两客户端多人隔离；
- 客户端/专用服务器同步。

### AUTOMATED（自动化）

- 当前 S01-S10 的单元/生产路径测试已覆盖；
- 511/512/513 和父 UUID 去重已覆盖；
- 建议补充：
  - 真实 Forge 事件包装测试（可用测试事件总线/fake level）；
  - Tracker 的 `ServerTickEvent` START/END 生命周期集成测试；
  - 多人 UUID 隔离测试。

### SOURCE_REVIEW（源码审查）

- H09 最终 VALID 过滤；
- C09 recentTargets 五类 UUID 聚合；
- `UNKNOWN_TRUNCATED` 不进入 active Session；
- `LATE_MATCHED` 不进入 active Session；
- `NOT_LATE` 才进入；
- recent lookup 512 去重截断；
- Handler `orderingKnown=false`；
- 线程所有权和停服竞态。

### DEFERRED（可暂缓）

- 真实多人延迟/网络抖动；
- 极端服务器 tick 回退/重启；
- 大规模 PartEntity 压测；
- 真实 Forge 事件的性能基线。

---

## 5. 人工测试指南

> 所有测试先备份世界；开启 DEBUG 日志，记录 `CombatBridge`/`BetterCombatCompat`/异常。
> 需要第二名玩家时注明。

### PARK-01 FAST_RUN

1. 目的：FAST_RUN 状态下发起 Better Combat 攻击，确认服务端请求和上下文。
2. 准备：创造/生存世界、剑、僵尸、ParCool FAST_RUN 可用。
3. 步骤：疾跑进入 FAST_RUN；对僵尸左键攻击；记录 3 次。
4. 正常：攻击正常，日志出现 prepared/finished；无异常；攻击不被错误取消。
5. 失败：崩溃、攻击被吞、无请求日志、上下文污染到下一次。
6. 记录：动作状态、攻击日志、截图/日志。
7. 第二玩家：可选。

### PARK-02 SLIDE

同上，使用 SLIDE；确认滑铲期间攻击不改变动作或伤害。失败条件：滑铲被中断、攻击异常、日志缺失。

### PARK-03 VAULT

翻越时攻击；确认翻越动作和攻击都完成；失败：卡住、重复攻击、无上下文。

### PARK-04 WALL_RUN

沿墙跑并攻击；确认水平/垂直 wall run 状态和攻击请求；失败：动作被攻击打断或请求丢失。

### PARK-05 WALL_JUMP

蹬墙跳时攻击；确认跳跃和攻击；失败：掉下墙、攻击无记录。

### PARK-06 FALL

从高处下落时攻击；确认 FALL 状态与攻击观察；失败：FALL 状态丢失、落地后上下文未结束。

### COMBAT-01 普通攻击

持剑攻击僵尸 5 次；记录每次 prepared/finished 和伤害事件；确认无重复上下文。

### COMBAT-02 PartEntity

攻击末影龙/多部件实体；记录 PartEntity/parent 日志；确认攻击不被拒绝；未验证真实 API 时标记 NOT_TESTED。

### LIFE-01 死亡/重生

攻击后立即死亡/重生；确认旧攻击上下文结束；重生后新攻击不继承旧目标。失败：旧 context 污染新攻击。

### LIFE-02 登出/登入

攻击后退出；重新登录；确认无旧 active context。失败：新登录后立即出现旧攻击。

### LIFE-03 维度切换

攻击后切维度；确认旧 context 清理；新维度攻击正常。失败：跨维度目标污染。

### SYNC-01 客户端/专用服务器

在专用服务器重复 COMBAT-01/PARK-01；确认服务端日志和客户端无不同结论；失败：客户端/服务器不一致。

### MP-01 多人隔离

两名玩家同时攻击不同目标；确认各自上下文不交叉。失败：A 的目标进入 B 的 context，或 B 的覆盖变化。

---

## 6. 自动化与源码审查清单

### 仍应由 DeepSeek 完成的自动化

1. 真实 Forge 事件对象集成测试（非 package seam）。
2. ServerTickEvent START/END 真实生命周期。
3. 真实 Tracker recent TTL 与 active cleanup。
4. 多人 UUID 隔离。
5. `eventDetailTruncatedTargetCount` 的真实多目标事件顺序。
6. 真实 PartEntity 的 parent UUID 聚合（需实际世界）。

### 源码审查

- C09 `recentTargets` 五类来源；
- H09 三条件；
- `UNKNOWN_TRUNCATED` 分支；
- 512 lookup 逻辑；
- Handler ordering 状态；
- 生命周期清理；
- 线程所有权；
- 停服竞态。

---

## 7. 可延期项目

可以暂缓而不阻止 Phase 05 设计/开发前置工作：

- 真实多人延迟/抖动；
- 大规模 PartEntity 压测；
- 极端 tick 回退/重启；
- 性能基线；
- 非关键 visual/动画候选。

但以下不应延期到 Phase 05 伤害/特殊攻击功能前：

- 真实 Forge 事件链；
- 真实 PartEntity parent 隔离；
- 多目标/多人隔离；
- 死亡/退出/维度清理；
- 客户端/服务器一致性。

---

## 8. 最终处理建议

### A. 用户可以立即进入游戏执行的测试

- PARK-01 至 PARK-06；
- COMBAT-01；
- COMBAT-02（末影龙/多部件）；
- LIFE-01/02/03；
- SYNC-01；
- MP-01（需第二名玩家）。

### B. 仍须 DeepSeek 完成的自动化/源码审查

- 真实 Forge 事件集成测试；
- ServerTickEvent START/END；
- Tracker TTL/active cleanup；
- 真实 PartEntity；
- 多目标 detail 截断；
- 多人 UUID 隔离；
- H09/C09 最终源码复核。

### C. 可延期

- 真实网络抖动；
- 大规模压测；
- 性能基线；
- 非关键视觉候选。

---

## 9. Phase 05 结论

**Gate 2.14 可以在保留延期项目的情况下结束当前测试扩展阶段，但不能直接进入 Phase 05 的功能实现。**

原因：

- 单元测试已覆盖 C09/H09 核心状态和计数器；
- 真实 Forge、PartEntity、多人、生命周期仍是 `NOT_TESTED`；
- Phase 05 若依赖真实攻击归属、特殊攻击触发或多人隔离，必须先完成人工测试和真实 Forge 集成测试；
- 只做 Phase 05 设计/接口规划可以继续，但不得宣称 Gate 2.14 VERIFIED。

会阻止 Phase 05 功能开放的项目：

1. 真实 Forge 事件链；
2. 真实 PartEntity parent 隔离；
3. 多目标/多人隔离；
4. 死亡/退出/维度/停服清理；
5. 客户端/服务器一致性。

仅阻止后续伤害倍率/词条/多人功能开放、但不阻止基础设计的项目：

- 大规模性能；
- 极端 tick 异常；
- visual/animation candidate 细节。
