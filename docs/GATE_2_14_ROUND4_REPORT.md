# GATE_2_14_ROUND4_REPORT.md

> Gate 2.14 Round 4 — 定点补测与矩阵审计  
> 分支：gate-2.14-test-fixes  
> 最终：CONDITIONALLY_IMPLEMENTED  
> 未进入 Phase 05

---

## 1. 本轮修改

### 测试代码

```text
CombatGate214CompletionTest.java
```

新增/改写：

```text
mixedSourceLookup511512513BoundaryThroughTracker
duplicateParentDoesNotConsumeMixedLookupCapacity
subsetRelationsHaveExplicitOrdinaryValues
```

### 生产代码

本轮未修改生产代码；沿用 Gate 2.14 已提交的：

```text
CombatRecentTargetLookup
CombatAttackTracker 共享 dispatch
C09 parent UUID 聚合
H09 VALID 过滤
```

---

## 2. 四组子集关系

| 关系 | 普通值 | 饱和值 | 状态 |
|---|---|---|---|
| part <= target | S02 | S02/S04 | PASS |
| canceled <= overflow | subsetRelations... / S04 | S04 | PASS |
| latePart <= late | subsetRelations... | S08 | PASS |
| lateCanceled <= late | subsetRelations... | S09 | PASS |

普通值断言明确：

```text
target=1, part=1
overflow=1, canceled=1
late=2, latePart=1, lateCanceled=1
```

饱和值通过生产路径触发，`Long.MAX_VALUE` 保持，不回绕。

---

## 3. 混合 UUID 容量

通过 Tracker recentTargets 真实聚合：

```text
7 × (63 normal + 1 part + 1 parent) = 455
+ 54 normal + part + parent = 511 -> truncated=false
+ 55 normal + part + parent = 512 -> truncated=false
+ 56 normal + part + parent = 513 -> truncated=true, size=512
```

另测：

```text
7 × 65 = 455
+ 55 normal + part(parent=normal) = 56
total = 511, truncated=false
```

重复 parent 与普通 UUID 去重，不占额外槽位。

---

## 4. 设计矩阵

| 项目 | 状态 | 证据 |
|---|---|---|
| C09 parent lookup | PASS | parentLateEventDoesNotReachActiveB |
| C09 512 truncation | PASS | mixedSourceLookup511512513..., lookup513... |
| C09 UNKNOWN | PASS | truncatedLookupUsesUnknownCounter... |
| C09 no active | PASS | noActiveSessionOnlyCountsDiagnostics |
| H09 prior damage cancel | PASS | priorPositiveDamageThenCancel... |
| H09 other target | PASS | cancelingOneTargetDoesNotAffect... |
| S01 target sat | PASS | s01... |
| S02 part sat | PASS | s02... |
| S03 overflow sat | PASS | s03... Session |
| S04 canceled sat | PASS | s04... Session |
| S05 detail sat | PASS | s05... Session |
| S06 truncated target | PASS | s06... Session |
| S07 late event sat | PASS | s07... |
| S08 late part sat | PASS | s08... |
| S09 late canceled sat | PASS | s09... |
| S10 unknown sat | PASS | s10... |
| 511/512/513 lookup | PASS | mixedSource... |
| C09 A-H full | PARTIAL | A/B/C/D/E/F/H 部分覆盖；G 截断覆盖 |
| CNT13-T01..T26 | PARTIAL | T01-T12 部分；T13 纯函数；其余缺失 |
| 真实 Forge/PartEntity | NOT_TESTED | 未执行 |
| Multiplayer/生命周期/停服 | NOT_TESTED | 未执行 |

原设计 159 项仍合理，但本轮实际 142 项；缺 17 项：

- CNT13 部分独立测试；
- C09 部分完整组合；
- 四组子集的全部独立拆分；
- 真实 Forge/PartEntity/多人/生命周期。

这些不得标记 PASS。

---

## 5. 测试结果

```text
总计：142/142 passed / 0 failed
```

测试类：

```text
CombatGate214CompletionTest        18
CombatGate214RegressionTest         9
CombatGate210RegressionTest         7
CombatGate28RegressionTest          8
CombatAttackSessionRemediationTest 24
其他基线                            76
总计                               142
```

相比上一版 139 增加 3 项。

---

## 6. 构建与启动

```text
compileJava：BUILD SUCCESSFUL
test：142/142 passed
build：BUILD SUCCESSFUL
jar：157,458 bytes
```

此前启动证据：

```text
CLIENT-STARTED（Setting user: Dev，12:57）
SERVER-STARTED（Done 8.459s，12:56）
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
```

本轮未重新执行真实攻击链。

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

## 8. 最终状态

```text
IMPLEMENTED
COMPILED
UNIT-TESTED（142/142）
CLIENT-STARTED
SERVER-STARTED
CLIENT-TESTED：NO
SERVER-TESTED：NO
MULTIPLAYER-TESTED：NO
VERIFIED：NO
CONDITIONALLY_IMPLEMENTED
```

尚未完成设计 159 项矩阵；真实 Forge/PartEntity/多人/生命周期未验证。
