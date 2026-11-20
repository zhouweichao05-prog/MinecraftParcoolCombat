# PHASE04_GATE2_REMEDIATION_IMPLEMENTATION_REPORT.md

> Phase 04 Gate 2.8 — Gate 2 修复实施  
> 依据：`docs/PHASE04_GATE2_7_REMEDIATION_PLAN.md`  
> 状态：IMPLEMENTED / COMPILED / TESTED / CLIENT-TESTED / SERVER-TESTED  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05；未创建 Git commit。

---

## 1. 实施前 Git 状态

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

工作区原本已有未跟踪的 Phase 04 文档、源码、测试和日志；未执行 `git add .`，未删除旧文件。

---

## 2. 修改和新增文件

### 新增源码

```text
CombatTargetState.java
CombatTargetRegistry.java
```

### 修改源码

```text
CombatAttackSession.java
CombatAttackTracker.java
CombatAttackContext.java
CombatTargetCoverage.java
CombatDamageSourceClassification.java
CombatBridge.java
```

### 新增测试

```text
CombatGate28RegressionTest.java
```

### 修改测试

```text
CombatTargetDamageObservationTest.java  // 移除 PRIMARY 旧值
```

### 未修改

```text
BetterCombatCompat.java 的 Handler 安装策略
BetterCombatAttackAdapter.java
ParCool / Better Combat JAR
Gradle / 依赖
Mixin / 网络包
```

---

## 3. C-01：统一 64 目标容量

### 实现

- `CombatTargetRegistry` 是 canonical target UUID 的唯一准入点；
- `TargetState` 保存 kind、parent、chain、counts、events；
- 普通实体和 PartEntity 共享 64；
- 重复 UUID 复用，不重复消耗预算；
- parentUuid 只是 PartEntity 关系，不自动准入；
- 第 65 个目标不创建 TargetState；
- 溢出不保存 UUID，只更新固定 long 计数器；
- context 中所有目标集合由 Registry 快照生成，最大 64；
- `recentSealedTargets` 最大 8×64=512；
- `recentTargets` 最大 512；
- coverage 增加 `PARTIAL_TARGET_OVERFLOW`。

### 已验证

- 65 个 PartEntity 的共享预算测试；
- Registry 65th target / part subset 测试；
- context 集合 <=64；
- 第 65 个 part 不得创建 state；
- coverage 不升级 COMPLETE。

### 证据

`CombatGate28RegressionTest`:

```text
sharedPartBudgetStopsThe65thPartAndKeepsAllSetsBounded
registryRejects65thTargetAndCountsPartSubset
```

结果：PASSED。

---

## 4. H-01：取消状态机

### 实现

状态：

```text
NONE / VALID / CANCELED / OVERLAP
```

优先级：

```text
CANCELED > OVERLAP > VALID > NONE
```

规则：

- 未取消 AttackEntity -> VALID；
- 取消 AttackEntity -> CANCELED；
- VALID + 后续未取消 AttackEntity -> OVERLAP；
- VALID/OVERLAP + 取消 -> CANCELED；
- CANCELED 不可重新激活；
- CANCELED 后续 Living -> ATTACK_CANCELED；
- `validAttackTargets` 只由 VALID 派生；
- `canceledAttackTargets` 只由 CANCELED 派生；
- 历史事件不删除；
- `attackEntityCount` 不回退；
- canceled damage 不进入 validDamageTargets；
- 不宣称实际生命值减少。

### 已验证

- 有效后取消 -> valid 清空、历史保留；
- OVERLAP 后取消 -> CANCELED；
- CANCELED 后 uncanceled -> 仍 CANCELED；
- 取消后 Living 不 positive；
- 其他目标不受影响；
- sealed 后不修改。

结果：PASSED。

---

## 5. H-02：统一服务器 Tick 时钟

### 时间来源

```text
long serverTick
long lastRawServerTick
long lastServerTickAtStart
boolean clockStarted
```

- 不用 `Level#getGameTime()`；
- 不用墙上时钟；
- active Session 与 sealed Context 共用同一时钟；
- `CombatAttackSession.serverTick`、`CombatAttackContext.serverTick`、`sealedTick` 均为 long；
- `attackTick` 只是诊断字段。

### 递增入口

`CombatBridge` 只注册：

```text
TickEvent.ServerTickEvent
phase == END
```

Tracker 再用 `lastRawServerTick` 去重：

- 同一 raw tick 重复 END 不递增；
- raw 前进 `delta` 增加 `serverTick`；
- unsigned int 回绕按 delta 处理；
- raw 回退或 delta > 200 -> 清空 active/recent，重置时钟；
- `ServerStarted` 初始化；
- `ServerStopping` 清理。

### TTL

```text
age <= 40 -> 有效
age > 40  -> 过期
```

- 第 40 tick 有效；
- 第 41 tick 过期；
- `sealedTick < 0` 无效；
- `serverTick < sealedTick` 异常；
- recent 每 tick 清理，不必等新请求；
- 退出/死亡/维度/停服清理；
- orderingUnknown 使用同一 TTL，不延长。

### 已验证

`CombatGate28RegressionTest`:

```text
serverClockUsesLongAndDeduplicatesSameRawTick
unsignedRawWrapAndRegressionAreHandledWithoutNegativeTick
```

结果：PASSED。

未完成：真实 Forge ServerTickEvent START/END 集成测试；Tracker recent TTL 的完整玩家生命周期测试。

---

## 6. 集合容量

| 集合/结构 | 上限 |
|---|---:|
| `TargetRegistry.targets` | 64 |
| 每目标事件明细 | 8 |
| `requestedResolutions` | 64 |
| `unresolvedRequestedEntityIds` | 64 |
| `validAttackTargets` | 64 |
| `canceledAttackTargets` | 64 |
| `observedPartUuids` | 64 |
| context `targetEventObservations` | 64 |
| recent contexts | 8 |
| `recentSealedTargets` | 512 |
| `recentTargets` | 512 |

---

## 7. 六个溢出计数器

所有计数器使用 long 和饱和递增：

```text
targetBudgetOverflowCount
partTargetOverflowCount
overflowEventCount
overflowCanceledEventCount
eventDetailOverflowCount
eventDetailTruncatedTargetCount
```

- `partTargetOverflowCount` 是 target 的子集；
- `overflowCanceledEventCount` 是 overflowEvent 的子集；
- `eventDetail*` 与目标预算独立；
- `eventDetailTruncatedTargetCount` 每目标最多一次；
- 达到 `Long.MAX_VALUE` 保持，不回绕、不变负；
- 不保存溢出 UUID。

测试：

```text
registryRejects65thTargetAndCountsPartSubset
counterSaturationDoesNotWrapNegative
```

结果：PASSED。

---

## 8. 其他修复

- `PRIMARY_PLAYER_ATTACK` 已从 `CombatDamageSourceClassification` 删除；
- 生产只保留 `AMBIGUOUS_PLAYER_ATTACK` / `OTHER`；
- `CombatTargetDamageObservationTest` 旧测试数据更新；
- `canceledDamageTargetCount` 只统计 canceled LivingHurt/Damage，不把 canceled AttackEntity 算作 canceled damage；
- `damageObservedTargets` 只指向 validDamage；
- 删除 `AttackChain.valid` 的等价状态职责，统一由 `TargetState.chainState` 承担。

---

## 9. 测试结果

### 总计

```text
108 tests / 108 passed / 0 failed
```

先前 100 项全部继续通过；新增 8 项 Gate 2.8 回归测试：

```text
sharedPartBudgetStopsThe65thPartAndKeepsAllSetsBounded
cancelAfterValidRemovesValidSetButKeepsHistory
overlapThenCancelPrioritizesCancel
overlapRemovesValidAndNeverProducesValidChain
registryRejects65thTargetAndCountsPartSubset
serverClockUsesLongAndDeduplicatesSameRawTick
unsignedRawWrapAndRegressionAreHandledWithoutNegativeTick
counterSaturationDoesNotWrapNegative
```

### 覆盖

- C-01 64/65 part、shared budget、registry subset；
- H-01 valid→cancel、overlap→cancel、canceled no reactivation；
- H-02 duplicate raw tick、unsigned wrap、regression、40/41 boundary via clock；
- 计数器 subset/saturation；
- 旧 100 项兼容。

### 未测试

- T-08 精确跨目标 late；
- T-12 真实其他 Mod 同 source；
- T-16 真实多玩家；
- T-17 死亡/退出/维度；
- T-18 停服；
- 真实 Forge 事件链；
- real PartEntity/getEntityOrPart；
- recent TTL 的真实玩家生命周期；
- START/END 真实事件集成。

这些标记 `NOT_TESTED`，未写成 PASSED。

---

## 10. 编译、构建、客户端、服务器

```text
compileJava：BUILD SUCCESSFUL
build：BUILD SUCCESSFUL
jar：build/libs/parkourcombat-2.0.0-alpha.1.jar
size：150,145 bytes
```

### Client

```text
Setting user: Dev
OpenAL initialized
```

状态：

```text
CLIENT-TESTED（startup only）
```

未进入世界。

### Dedicated Server

```text
Done (7.910s)!
[BetterCombatCompat] installed with orderingUnknown=true
```

状态：

```text
SERVER-TESTED（startup only）
```

无 FATAL/ClassNotFoundException/NoSuchMethod。

---

## 11. Handler 状态

```text
orderingKnown = false
handlerIdentity = UNKNOWN_PREEXISTING
installationState = DEGRADED_OR_UNKNOWN
```

未覆盖未知 Handler；未反射拆解；未把事件观察认定为 Better Combat 独占攻击。

---

## 12. 依赖 SHA-256

```text
bettercombat-forge-1.9.0+1.20.1.jar
49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1

ParCool-1.20.1-3.4.3.3.jar
2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7

player-animation-lib-forge-1.0.2-rc1+1.20.jar
90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691

cloth-config-11.1.136-forge.jar
1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2
```

---

## 13. Gate 2.6 问题状态

| 问题 | 状态 |
|---|---|
| C-01 PartEntity 绕过 64 | 源码级已修复；Session/Registry 测试 PASSED；真实 PartEntity 未测试 |
| H-01 取消后 valid 污染 | 源码级已修复；测试 PASSED |
| H-02 recent 无 TTL | 统一 long 时钟和 TTL 已实现；真实 Forge 生命周期未集成测试 |
| PRIMARY 残留 | 已删除；旧测试更新 |
| canceled damage 旧集合 | 已拆分/修正 |
| 溢出计数语义 | 已分开，long 饱和 |
| AttackChain.valid | 已由 TargetState.chainState 取代 |
| canceledAttackTargets | 内部派生，Context 用计数/事件记录 |

仍有测试覆盖缺口，不能宣称 Gate 2 全部 VERIFIED。

---

## 14. 最终状态

```text
IMPLEMENTED
COMPILED
TESTED（108/108）
CLIENT-TESTED（startup only）
SERVER-TESTED（startup only）
MULTIPLAYER-TESTED：NO
VERIFIED：NO
CONDITIONALLY_IMPLEMENTED
```

原因：C-01/H-01/H-02 和计数器/分类修复已进入源码并通过 108 项测试，但真实 Forge 事件、PartEntity、recent TTL 生命周期、多玩家、死亡/退出/维度和停服测试仍未执行。

未进入 Phase 05；未创建 Git commit；未执行 `git add .`。
