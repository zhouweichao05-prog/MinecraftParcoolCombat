# PHASE04_GATE2_14_IMPLEMENTATION_REPORT.md

> Phase 04 Gate 2.14 — C09-M01 修复与测试补全实施  
> 依据：`docs/PHASE04_GATE2_13_REMEDIATION_DESIGN.md`  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05。

---

## 1. 修改文件

### 生产代码（当前分支已包含）

```text
CombatRecentTargetLookup.java
CombatAttackTracker.java
CombatTargetRegistry.java   // package-private counter seeding
```

### 本轮实际修改测试

```text
CombatGate210RegressionTest.java
CombatGate214CompletionTest.java
```

### 生产代码最小重构

`CombatAttackTracker` 生产入口与测试入口现在共享：

```text
dispatchAttack
dispatchLiving
isLateTarget
recordLate
```

未改变 C09/H09 语义。

### 未修改

```text
CombatAttackSession 的 H09 条件
CombatTargetState
BetterCombatCompat / BetterCombatAttackAdapter
Gradle / 依赖 / 第三方 JAR
```

---

## 2. 本轮测试修复

### S06

已改为真实 `CombatAttackSession.observeLivingEvent` -> `addEvent` 生产路径：

- A 前 8 条；
- A 第 9 条 -> `eventDetailTruncatedTargetCount=1`；
- A 第 10/11 条不重复；
- B 前 8 条 + 第 9 条 -> 计数再 +1；
- final = 2。

不使用 `Long.MAX_VALUE - 1` 初始化。

### 512 边界

`lookupDuplicateDoesNotConsumeCapacityOrTruncate` 已覆盖：

- 511 唯一 UUID + 重复 -> 511，未截断；
- 第 512 个 -> 512，未截断；
- 第 513 个 -> truncated，未保存；
- 截断后重复已有 UUID -> 不改变容量/状态。

### parent/普通 UUID 混合

`parentAndNormalUUIDShareOneLookupSlot` 已改为真实 Context：

- 普通目标 UUID N；
- PartEntity C parent N；
- Tracker recent lookup；
- N 与 C 各占一个位置，N 不重复计数。

### 旧测试名称

`eventDetailAndTargetOverflowCountersAreIndependent`
已重命名为：

```text
targetBudgetAndOverflowEventCountersAreIndependent
```

### 子集关系

S02/S04/S08/S09 覆盖：

```text
part <= target
overflowCanceled <= overflowEvent
latePart <= lateEvent
lateCanceled <= lateEvent
```

---

## 3. 测试结果

```text
原有 115 项：保留并通过
Gate 2.14 新增：23 项
当前总计：138/138 passed / 0 failed
```

设计目标为 159；实际缺少 21 项：

- 部分 C09 A-H 完整断言；
- 部分 CNT13-T 独立测试；
- 部分 S 组合；
- 真实 Forge/PartEntity/多人/生命周期测试。

---

## 4. 构建与启动

```text
compileJava：BUILD SUCCESSFUL
test：138/138 passed
build：BUILD SUCCESSFUL
jar：157,396 bytes
```

```text
CLIENT-STARTED（Setting user: Dev，12:57）
SERVER-STARTED（Done 8.459s，12:56）
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
```

启动不是战斗链验证。

---

## 5. Git 与推送

```text
仓库：https://github.com/zhouweichao05-prog/MinecraftParcoolCombat.git
分支：gate-2.14-test-fixes
测试提交：b3826b6
说明：test: complete Gate 2.14 production-path coverage
远程推送：SUCCESS
PUSHED_TO_REMOTE：YES
```

当前工作区仅有未跟踪日志：

```text
docs/logs/test-gate214-fixes.log
```

已跟踪源码/测试均已提交。

---

## 6. 依赖 SHA-256

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

## 7. 最终状态

```text
IMPLEMENTED
COMPILED
TESTED（138/138）
CLIENT-STARTED
SERVER-STARTED
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
PUSHED_TO_REMOTE
CONDITIONALLY_IMPLEMENTED
```

S06 生产路径、512 边界和 parent 去重已补测；设计 159 项目标仍未全部完成，不能标记 VERIFIED。
