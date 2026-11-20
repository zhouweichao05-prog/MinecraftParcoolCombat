# PHASE04_GATE2_14_IMPLEMENTATION_REPORT.md

> Phase 04 Gate 2.14 — C09-M01 修复与测试补全实施  
> 依据：`docs/PHASE04_GATE2_13_REMEDIATION_DESIGN.md`  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05；未创建 Git commit。

---

## 1. 实际修改

### 新增源码

```text
CombatRecentTargetLookup.java
```

### 修改源码

```text
CombatAttackTracker.java
CombatTargetRegistry.java   // package-private counter seeding for tests
```

### 新增测试

```text
CombatGate214RegressionTest.java
CombatGate214CompletionTest.java
```

### 修改测试

```text
CombatGate210RegressionTest.java
  recordLateForTest -> recordLateDiagnosticForTest
```

未修改 `CombatAttackSession` 的 H09 逻辑、Handler、Gradle、依赖或第三方 JAR。

---

## 2. C09-M01 修复

### parent UUID 聚合

`CombatAttackTracker.recentTargets` 现在聚合：

```text
requestedTargets
validAttackTargets
observedPartUuids
requestedPartParentUuids.values()
observedPartParentUuids.values()
```

### 512 去重上限

`CombatRecentTargetLookup`：

- 先查重；
- 已存在不消耗容量；
- 唯一 UUID 数量为 512 时设置 truncated；
- 不加入第 513 个；
- 不保存溢出 UUID。

### isLateTarget 三分

```text
LATE_MATCHED
NOT_LATE
UNKNOWN_TRUNCATED
```

UNKNOWN_TRUNCATED 只增加 `lateUnknownEventCount`，不调用 active Session。

### Tracker 计数

```text
lateEventCount
latePartEventCount
lateCanceledEventCount
lateUnknownEventCount
```

long、饱和递增、生命周期重置、不保存 UUID。

---

## 3. 测试结果

```text
原有 115 项：保留并通过
Gate 2.14 新增：23 项
总计：138/138 passed / 0 failed
```

### 本轮新增 14 项

```text
s01TargetBudgetOverflowSaturatesThroughAdmit
s02PartBudgetOverflowSaturatesThroughAdmit
s03OverflowEventSaturatesThroughRecordOverflowEvent
s04OverflowCanceledSaturatesThroughRecordOverflowEvent
s05EventDetailOverflowSaturatesThroughTargetStateRecord
s06TruncatedTargetCounterSaturatesThroughTwoTargets
s07LateEventSaturatesThroughTrackerDispatch
s08LatePartSaturatesThroughTrackerDispatch
s09LateCanceledSaturatesThroughTrackerDispatch
s10LateUnknownSaturatesThroughTruncatedDispatch
lookup511And512AreNotTruncated
lookup513TruncatesWithoutStoring
lookupDuplicateDoesNotConsumeCapacityOrTruncate
parentAndNormalUUIDShareOneLookupSlot
```

### 仍然缺失

设计目标 159，实际 138；缺少 21 项：

- C09 A-H 的部分完整断言；
- CNT13-T01～T26 全部独立测试；
- 部分 S 测试的拆分组合；
- 四组饱和子集的独立测试；
- 真实 Forge Event；
- 真实 PartEntity；
- Multiplayer / 生命周期 / 停服。

这些为 `PARTIAL` 或 `NOT_TESTED`，不能写成 PASSED。

---

## 4. 编译、测试、构建

```text
compileJava：BUILD SUCCESSFUL
test：138/138 passed
build：BUILD SUCCESSFUL
jar：build/libs/parkourcombat-2.0.0-alpha.1.jar
size：157,396 bytes
```

---

## 5. Client / Server

```text
Client：Setting user: Dev
CLIENT-STARTED
CLIENT-TESTED：NO
```

```text
Server：Done (9.043s)
SERVER-STARTED
SERVER-TESTED：NO
```

启动不代表战斗链验证。

---

## 6. Handler

```text
orderingKnown = false
handlerIdentity = UNKNOWN_PREEXISTING
installationState = DEGRADED_OR_UNKNOWN
```

---

## 7. 依赖 SHA-256

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

## 8. Git

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

未执行 git add / commit。

---

## 9. 最终状态

```text
IMPLEMENTED
COMPILED
TESTED（138/138）
CLIENT-STARTED：YES
SERVER-STARTED：YES
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
CONDITIONALLY_IMPLEMENTED
```

S01-S10 和 511/512/513 边界已实现；设计目标 159 项尚未全部完成。
