# PHASE04_GATE2_11_IMPLEMENTATION_REPORT.md

> Phase 04 Gate 2.11 — Gate 2.10.1 修复实施  
> 依据：`docs/PHASE04_GATE2_10_REMEDIATION_DESIGN.md`  
> 状态：IMPLEMENTED / COMPILED / TESTED / CLIENT-TESTED / SERVER-TESTED  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05；未创建 Git commit。

---

## 1. 修改文件清单

### 修改源码

```text
CombatAttackSession.java
CombatAttackTracker.java
```

### 新增测试

```text
CombatGate210RegressionTest.java
```

### 未修改

```text
CombatTargetRegistry.java
CombatTargetState.java
CombatAttackContext.java
CombatTargetCoverage.java
BetterCombatCompat.java
BetterCombatAttackAdapter.java
Gradle / 依赖 / 第三方 JAR
```

---

## 2. H09-H01 实施结果

`CombatAttackSession.finish` 的有效伤害筛选增加：

```java
state.chainState() == CombatTargetState.ChainState.VALID
&& !event.canceledAtObservation()
&& event.association() == MATCHED_UNCANCELLED_CHAIN
```

结果：

- 历史事件保留；
- CANCELED 目标不进入 `validDamageTargets`；
- CANCELED 不贡献 `anyMatchedDamage` / `anyPositive`；
- OVERLAP 同样不贡献；
- 其他有效目标不受影响；
- 不修改真实伤害结算；
- 不把 positive 解释为实际生命值减少。

测试：

```text
priorPositiveDamageThenCancelDoesNotProduceValidDamage
priorZeroDamageThenCancelDoesNotProducePositive
cancelingOneTargetDoesNotAffectAnotherValidTarget
canceledChainCannotBeReactivated
```

结果：PASSED。

---

## 3. C09-M01 实施结果

### Tracker late 拦截

`CombatAttackTracker` 在事件分发给 active Session 前检查：

```text
target UUID / parent UUID 是否命中 recent sealed 目标
```

命中：

- `lateEventCount++`；
- PartEntity 同时 `latePartEventCount++`；
- canceled 同时 `lateCanceledEventCount++`；
- 不调用 Session.observe；
- 不创建/修改 Context；
- 不创建 TargetState；
- 不保存 UUID；
- 不占 64 目标预算。

无 active Session 但存在 recent PlayerState 时同样只计数。

### 计数器

```text
long lateEventCount
long latePartEventCount
long lateCanceledEventCount
```

- long；
- `saturatingIncrement`；
- 子集关系成立；
- `ServerStarted` / `clearAll` 重置；
- 不进入 Context；
- 不产生 `PARTIAL_LATE_EVENT`。

### 事件入口审计

已检查：

```text
onAttackEntity
onLivingEvent (LivingAttack/Hurt/Damage)
```

两者都先检查 late，再交给 active Session。没有其他目标观察入口。

结论：

- H09 修复路径正确；
- C09 的实际 Forge 事件入口未集成测试；
- Tracker late 计数器测试使用 package-private test seam，不等同真实 Forge 分发。

---

## 4. 原六个计数器

实现保持：

```text
targetBudgetOverflowCount
partTargetOverflowCount
overflowEventCount
overflowCanceledEventCount
eventDetailOverflowCount
eventDetailTruncatedTargetCount
```

本轮新增测试验证：

- 65 普通目标 -> target overflow；
- target overflow 后 unadmitted 事件；
- canceled overflow；
- target/part 子集；
- 不与 detail overflow 混用。

未完整覆盖：

```text
eventDetailOverflowCount 独立触发
eventDetailTruncatedTargetCount 独立触发
六计数器完整单位矩阵
Long.MAX_VALUE 真实字段饱和
```

这些仍为 NOT_TESTED / PARTIAL。

---

## 5. 三个 late 计数器

测试：

```text
trackerLateCountersAreSubsetsAndReset
```

验证：

- `lateEventCount=3`；
- `latePartEventCount=2`；
- `lateCanceledEventCount=1`；
- 子集关系；
- `clearAll` 重置为 0。

限制：

- 测试调用 package-private `recordLateForTest`，不是实际 Forge Event 分发；
- 没有真实 ServerPlayer/PartEntity；
- 没有验证 active B 不被污染的实际事件路径。

这些必须标记 **NOT_TESTED（真实 Forge）**。

---

## 6. 测试结果

```text
原有 108 项：全部保留并通过
新增：7 项
总计：115/115 passed / 0 failed
```

新增测试：

```text
priorPositiveDamageThenCancelDoesNotProduceValidDamage
priorZeroDamageThenCancelDoesNotProducePositive
cancelingOneTargetDoesNotAffectAnotherValidTarget
canceledChainCannotBeReactivated
eventDetailAndTargetOverflowCountersAreIndependent
canceledOverflowEventIncrementsCanceledSubset
trackerLateCountersAreSubsetsAndReset
```

---

## 7. 编译与构建

```text
compileJava：BUILD SUCCESSFUL
test：BUILD SUCCESSFUL
build：BUILD SUCCESSFUL
jar：build/libs/parkourcombat-2.0.0-alpha.1.jar
size：150,826 bytes
```

---

## 8. 客户端与专用服务器

### Client

```text
Setting user: Dev
OpenAL initialized
```

状态：

```text
CLIENT-TESTED（startup only）
```

### Dedicated Server

```text
Done (7.426s)!
[BetterCombatCompat] installed with orderingUnknown=true
```

状态：

```text
SERVER-TESTED（startup only）
```

无 FATAL/ClassNotFoundException/NoSuchMethod。

启动成功不代表真实攻击/Forge 事件链验证。

---

## 9. Handler 状态

```text
orderingKnown = false
handlerIdentity = UNKNOWN_PREEXISTING
installationState = DEGRADED_OR_UNKNOWN
```

未覆盖未知 Handler；未反射拆解；未把事件观察认定为 Better Combat 独占攻击。

---

## 10. 依赖 SHA-256

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

## 11. Git 状态

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

未执行 `git add` / `git commit`。源码、测试、文档、日志仍为未跟踪文件。

---

## 12. Gate 2.9 问题状态

| 问题 | 状态 |
|---|---|
| H09-H01 取消前 damage 仍有效 | 源码已修复，4 项测试 PASSED |
| C09-M01 late Part coverage | 改为 Tracker 诊断；源码已实现；真实 Forge 测试 NOT_TESTED |
| 六个原计数器 | 部分测试通过；detail/饱和未完整覆盖 |
| 三个 late 计数器 | 计数/子集/重置测试通过；真实事件路径 NOT_TESTED |
| PARTIAL_LATE_EVENT | 未新增/未生产，符合 2.10.1 |

---

## 13. 未验证内容

- 真实 Forge AttackEntityEvent/Living 事件链；
- 真实 PartEntity/getEntityOrPart；
- active B + late A 的实际污染测试；
- 无 active Session late 事件；
- 真实 recent TTL 玩家生命周期；
- 死亡/退出/维度/停服；
- 多人；
- 六计数器完整单位矩阵；
- eventDetailTruncatedTargetCount 独立测试；
- 真实 Long.MAX_VALUE 字段饱和；
- 真实 START/END ServerTickEvent。

以上全部 **NOT_TESTED**，不得写成 PASSED。

---

## 14. 最终状态

```text
IMPLEMENTED
COMPILED
TESTED（115/115）
CLIENT-TESTED（startup only）
SERVER-TESTED（startup only）
MULTIPLAYER-TESTED：NO
VERIFIED：NO
CONDITIONALLY_IMPLEMENTED
```

H09-H01 已源码修复并通过回归；C09-M01 已按 2.10.1 改为 Tracker 诊断，但真实 Forge 分发测试和完整计数器测试仍缺失。未进入 Phase 05；未创建 Git commit。
