# PHASE04_GATE2_12_INDEPENDENT_AUDIT_REPORT.md

> Phase 04 Gate 2.12 — Gate 2.11 修复后的独立源码审计  
> 审查性质：只读；未修改源码、测试、Gradle、依赖或第三方 JAR。  
> 最终结论：**GATE_2_11_REJECTED**

---

## 1. 审计结论

# `GATE_2_11_REJECTED`

阻断原因：

- **CRITICAL**：`CombatAttackTracker.recentTargets` 没有包含 PartEntity parent UUID；旧攻击 A 的 parent 伤害事件可能绕过 late 拦截，污染新攻击 B。
- **HIGH**：C09 的测试只调用 package-private `recordLateForTest`，没有真实 A/B 事件分发、parent UUID 拦截或 active Session 污染测试。
- **MEDIUM**：六个原计数器的 eventDetail/truncated 边界没有独立测试。

H09-H01 本身已正确修复。

---

## 2. Git 与依赖

### Git

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

Gate 2.11 源码、测试、文档仍为未跟踪文件。

### 依赖 SHA-256

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

## 3. H09-H01 审计

### 结论：PASS

### 源码证据

`CombatAttackSession.finish` 当前有效伤害汇总：

```java
if (state.chainState() == CombatTargetState.ChainState.VALID
        && !event.canceledAtObservation()
        && event.association() == CombatDamageAssociation.MATCHED_UNCANCELLED_CHAIN) {
    anyMatchedDamage = true;
    anyPositive |= event.positiveAtObservation();
    validDamage.add(state.targetUuid());
}
```

### 验证

- 所有进入 `validDamageTargets` 的目标必须最终 `chainState == VALID`；
- CANCELED 不进入 valid；
- OVERLAP 不进入 valid；
- canceled event 不进入 valid；
- `anyMatchedDamage` / `anyPositive` 只在 VALID 时设置；
- 历史事件仍保留在 TargetState events 和 Context targetEventObservations；
- 其他目标独立；
- sealed 后 finish 返回 null/不再修改；
- 没有发现其他把 CANCELED/OVERLAP 加入 validDamage 的生产路径。

### 测试

```text
priorPositiveDamageThenCancelDoesNotProduceValidDamage
priorZeroDamageThenCancelDoesNotProducePositive
cancelingOneTargetDoesNotAffectAnotherValidTarget
canceledChainCannotBeReactivated
```

通过，且最终 Context 断言有效。

**H09-H01：PASS。**

---

## 4. C09-M01 审计

### 结论：FAIL（CRITICAL）

### 事件入口

`CombatBridge`：

```text
onAttackEntity -> TRACKER.onAttackEntity
onLivingAttack -> TRACKER.onLivingEvent
onLivingHurt -> TRACKER.onLivingEvent
onLivingDamage -> TRACKER.onLivingEvent
```

没有 Bridge 直接调用 Session。

`CombatAttackTracker.onAttackEntity`：

```text
state = players.get(attacker)
if isLateTarget(state, target) -> recordLate; return
if state.active -> observeAttackTarget
```

`CombatAttackTracker.onLivingEvent`：

```text
state = players.get(attacker)
if isLateTarget(state, target) -> recordLate; return
if state.active -> observeLivingEvent
```

顺序本身正确。

### CRITICAL：recentTargets 缺少 PartEntity parent UUID

文件：

```text
CombatAttackTracker.java
```

方法：

```text
recentTargets(PlayerState)
```

位置：

```text
323-337
```

实际聚合：

```java
result.addAll(context.requestedTargets());
result.addAll(context.validAttackTargets());
result.addAll(context.observedPartUuids());
```

缺少：

```text
context.observedPartParentUuids().values()
context.requestedPartParentUuids().values()
```

`isLateTarget`：

```text
216-228
```

只检查：

- target UUID；
- 如果 target 是 PartEntity，则检查其 parent UUID。

但如果旧攻击 A 的目标是 PartEntity，迟到事件是：

```text
LivingDamage(parent)
```

`LivingEntity` parent 不是 PartEntity，`isLateTarget` 无法通过 `part.getParent()` 找到关系；`recent` 也没有 parent UUID，因此不会命中 late。

结果：

- 迟到伤害事件被交给 B 的 active Session；
- B 的目标可能与 A 的 parent 相同或相关；
- B 的 validDamage/result 可能被污染；
- 这违反 Gate 2.10.1 的 A/B 隔离。

### 复现条件

```text
A: 请求/观察 PartEntity C (parent P)
A sealed
B starts
late LivingDamage(P)
```

如果 P 不在 recentTargets：

- `isLateTarget` 返回 false；
- Tracker 调用 B.observeLivingEvent(P);
- B 可能记录 association/validDamage。

### 其他 C09 问题

1. `recentTargets` 也不包含：
   - `context.canceledAttackTargets`（context 没有该集合，仅计数）；
   - 任何非 valid 的历史目标；
   - 迟到 canceled 旧目标可能不被识别。
2. `CombatAttackSession` 自身的 recent 检查仍存在，但 Tracker 已经漏掉 parent，无法弥补。
3. `recordLateForTest` 是 package-private seam，直接调用 `recordLate(part,canceled)`，没有经过 `isLateTarget`、没有 ServerPlayer、没有 active B，也不覆盖 parent UUID 命中。
4. 没有真实 A/B 污染测试。
5. 没有无 active Session 的真实事件测试。

### 测试覆盖

`trackerLateCountersAreSubsetsAndReset` 只验证计数器算术和 clearAll，不验证 late 识别。

因此：

**C09-M01：FAIL（CRITICAL）。**

---

## 5. 六个原计数器审计

### 结论：CONDITIONAL PASS

### targetBudgetOverflowCount

- 声明：`CombatTargetRegistry.java:17`
- 所属：CombatTargetRegistry
- 增加：`CombatTargetRegistry.admit` 满 64 分支
- 单位：被拒绝的新 canonical target 准入尝试
- long/saturation：是
- 子集：包含 partTargetOverflowCount
- 测试：`sharedPartBudgetStopsThe65thPartAndKeepsAllSetsBounded`、`registryRejects65thTargetAndCountsPartSubset`
- 真实触发：通过 Test seam Session/Registry，但 Registry 方法是生产逻辑

### partTargetOverflowCount

- 同 admission 路径，kind=PART；
- 与 target 同步递增，子集关系成立；
- 测试通过。

### overflowEventCount

- 增加：`CombatTargetRegistry.recordOverflowEvent`
- 触发：`CombatAttackSession.observeLivingEvent` 在 `targetBudgetOverflowed` 且 state 不存在时
- long/saturation：是
- 测试：`eventDetailAndTargetOverflowCountersAreIndependent`
- 覆盖不足：只测一次普通，不测重复/多种事件入口。

### overflowCanceledEventCount

- 同 `recordOverflowEvent`，canceled=true；
- 测试：`canceledOverflowEventIncrementsCanceledSubset`
- 子集成立。

### eventDetailOverflowCount

- 增加：`CombatTargetState.record` 第 9 条及以后 -> `registry.recordDetailOverflow`
- 当前**没有独立测试**。
- `CombatGate210RegressionTest` 没有 9 条事件详情测试。
- 结论：NOT_TESTED。

### eventDetailTruncatedTargetCount

- 增加：`CombatAttackSession.addEvent` 首次 truncated 时 `registry.incrementTruncatedTargetCount`
- 当前**没有独立测试**。
- 结论：NOT_TESTED。

### 其他

- 全部 long；
- 饱和递增；
- 不保存 UUID；
- 但 `eventDetailOverflowCount` 与 `eventDetailTruncatedTargetCount` 的区分没有测试证明。

---

## 6. 测试覆盖矩阵

| 项目 | 类型 | 结论 |
|---|---|---|
| H09 正伤害后取消 | DIRECT_PRODUCTION_PATH（Session finish） | PASSED |
| H09 零伤害后取消 | DIRECT | PASSED |
| H09 A取消/B有效 | DIRECT | PASSED |
| H09 CANCELED 重激活 | DIRECT | PASSED |
| H09 OVERLAP 不有效 | DIRECT/旧测试 | PASSED |
| C09 late 计数 | PACKAGE_PRIVATE_SEAM `recordLateForTest` | 仅计数器，不是事件拦截 |
| C09 A/B 污染 | NOT_TESTED | 缺失 |
| C09 parent UUID 命中 | NOT_TESTED | 缺失 |
| C09 无 active | NOT_TESTED | 缺失 |
| target 64/65 | REGISTRY/SESSION SEAM | PASSED |
| part 64/65 | SESSION SEAM | PASSED |
| overflowEvent/canceled | SESSION SEAM | PASSED（有限） |
| eventDetailOverflow | NOT_TESTED | 缺失 |
| eventDetailTruncatedTarget | NOT_TESTED | 缺失 |
| Long.MAX 真实计数器 | 只看纯函数 | 非生产路径 |
| 真实 Forge 事件 | NOT_TESTED | 缺失 |

---

## 7. 构建和启动

沿用本次实施结果：

```text
compileJava：BUILD SUCCESSFUL
test：115/115 passed
build：BUILD SUCCESSFUL
jar：150,826 bytes
```

启动：

```text
CLIENT-STARTED：Setting user / OpenAL
SERVER-STARTED：Done (7.426s)
```

严格区分：

```text
COMPILED：YES
TESTED：115 unit/session tests
CLIENT-STARTED：YES
SERVER-STARTED：YES
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
```

启动成功不是攻击链验证。

---

## 8. 问题列表

### CRITICAL

#### C12-C09-01

- 文件/方法：`CombatAttackTracker.recentTargets`
- 位置：`323-337`
- 缺失 `observedPartParentUuids().values()` / `requestedPartParentUuids().values()`
- 影响：旧 PartEntity 攻击的 parent 伤害事件不被识别为 late，污染 active B。
- 建议：加入 parent values，并增加 A/B parent late 测试。

### HIGH

#### C12-C09-02

- 文件：`CombatGate210RegressionTest.java`
- `trackerLateCountersAreSubsetsAndReset` 只调用 `recordLateForTest`
- 未覆盖真实 Tracker late 识别、parent、active B、无 active。
- 建议：增加可构造的 Tracker 测试路径或真实 Forge 集成测试；不能把当前测试当真实拦截验证。

### MEDIUM

#### C12-CNT-01

- `eventDetailOverflowCount`、`eventDetailTruncatedTargetCount` 无独立测试；
- 六计数器完整单位/子集矩阵未覆盖。

### LOW

- `lastServerTickAtStart` 只赋值。
- `recordLateForTest` 是测试 seam，容易被误认为生产路径。

---

## 9. 未验证内容

- 真实 Forge AttackEntity/Living 事件链；
- 真实 PartEntity/getEntityOrPart；
- parent UUID 的 A/B late 场景；
- 无 active Session 真实事件；
- recent TTL 完整玩家生命周期；
- 多人、死亡、退出、维度、停服；
- 六计数器完整边界；
- 真实 Long.MAX 字段饱和；
- ServerTickEvent START/END 集成。

---

## 10. 下一步建议

不进入 Phase 05。

最小修复顺序：

1. 在 `CombatAttackTracker.recentTargets` 加入：
   ```java
   context.observedPartParentUuids().values()
   context.requestedPartParentUuids().values()
   ```
2. 增加 A 为 PartEntity、B active、late parent damage 的测试。
3. 增加无 active Session late 测试。
4. 补齐 eventDetailOverflow / eventDetailTruncatedTarget 测试。
5. 再提交 Gate 2.12 审查。

本次未修改任何源码、测试、Gradle、依赖或第三方 JAR，未创建 Git commit。
