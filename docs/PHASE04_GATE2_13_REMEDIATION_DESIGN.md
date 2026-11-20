# PHASE04_GATE2_13_REMEDIATION_DESIGN.md

> Phase 04 Gate 2.13 — C09-M01 修复设计与测试补全  
> 依据：`docs/PHASE04_GATE2_12_INDEPENDENT_AUDIT_REPORT.md`  
> 本文件只定义设计与测试；不修改 Java、测试、Gradle、依赖或第三方 JAR。  
> 当前基线：115 项测试 PASSED；以下修复和测试均未实现。

---

## 0.1 Gate 2.13.1 修订记录

本轮补充依据：

1. 512 限制必须是**去重后的唯一 UUID 数量**；先查重、再判断容量。
2. 同一目标连续攻击无法精确归属，必须保守丢弃 B 的事件，不能猜测。
3. `lateUnknownEventCount` 独立于 `lateEventCount`，用于 lookup 截断的 UNKNOWN。
4. 增加 511/512/513、重复 UUID、parent 去重、UNKNOWN、饱和和 C09-T-H 测试。
5. 增加 S01-S10：10 个生产计数器实际饱和路径，不能用纯函数测试替代。
6. 设计预期新增 44 项，设计预期总数 159；未实现前不得计为 PASSED。

设计结论：**DESIGN_COMPLETE_PENDING_REVIEW**。

---

## 1. Gate 2.12 问题对应表

| 问题 | 严重度 | 设计响应 |
|---|---|---|
| C12-C09-01：recentTargets 缺少 parent UUID | CRITICAL | 加入 requested/observed parent values；引入有界 lookup |
| C12-C09-02：缺少真实 A/B 分发测试 | HIGH | 设计 Tracker 分发测试 A-F |
| C12-CNT-01：detail/truncated 计数器未独立测试 | MEDIUM | 六计数器完整矩阵 |
| C12-LOW-01：lastServerTickAtStart / recordLateForTest | LOW | 删除无用字段；重命名测试 seam |

---

## 2. 当前源码事实

### 2.1 Context 中的 PartEntity 关系

实际字段：

```text
CombatAttackContext.java:62-67
  requestedPartUuids
  requestedPartParentUuids
  observedPartUuids
  observedPartParentUuids
```

语义：

- `requestedPartUuids`：请求解析阶段准入的 PartEntity UUID；
- `requestedPartParentUuids`：part UUID -> parent UUID；
- `observedPartUuids`：事件观察阶段准入的 PartEntity UUID；
- `observedPartParentUuids`：part UUID -> parent UUID。

证据：

```text
CombatAttackSession.finish()
CombatAttackSession.java:335-395
```

### 2.2 recentTargets 当前聚合

```text
CombatAttackTracker.java:323-337
```

当前只加入：

```java
context.requestedTargets()
context.validAttackTargets()
context.observedPartUuids()
```

缺少两个 Map 的 values：

```text
context.requestedPartParentUuids().values()
context.observedPartParentUuids().values()
```

### 2.3 isLateTarget 当前逻辑

```text
CombatAttackTracker.java:216-228
```

当前：

- 检查 target UUID；
- 如果 target 是 PartEntity，检查 parent UUID。

问题：

- 旧攻击 A 的 parent 伤害事件 target 是普通 LivingEntity P；
- P 不是 PartEntity；
- `recentTargets` 没有 P（只存 part C）；
- `isLateTarget` 返回 false；
- Tracker 把 P 交给 B 的 active Session。

这是 C12-C09-01 的根因。

---

## 3. C12-C09-01 最小修复设计

### 3.1 recentTargets 数据源

新增聚合：

```java
result.addAll(context.requestedPartParentUuids().values());
result.addAll(context.observedPartParentUuids().values());
```

不新增任何 UUID 存储；只从已封存 Context 快照读取。

### 3.2 目标 UUID / PartEntity UUID / parent UUID 的关系

| UUID | 含义 | 是否直接进 lookup |
|---|---|---|
| 普通实体 UUID | 普通目标 | 是 |
| PartEntity UUID | 实际 part | 是 |
| parent UUID | part 的父实体关系值 | 是（来自两个 Map values） |
| requested parent | 仅作为 part 关系 | 是，但不独立占预算 |

规则：

- parent UUID 不等于 part UUID；
- parent UUID 只在它是 Map value 时加入；
- 不合成 UUID；
- parent 不额外占用 64 target 预算；
- parent 不去重为 part，反之亦然。

### 3.3 去重

使用 `LinkedHashSet`：

- 同一 UUID 重复 add 只保留一个；
- parent 与普通目标同为 UUID 时自动去重；
- 不重复消耗 512 lookup 容量。

### 3.4 512 上限与截断（去重后唯一 UUID）

`recentTargets` 最多保存 **512 个去重后的唯一 UUID**，必须把 lookup 截断显式表达。

```text
RecentTargetLookup
  Set<UUID> uuids
  boolean truncated
```

构建时逐项调用：

```java
void add(UUID uuid) {
    if (uuid == null || lookup.truncated) return;
    if (lookup.uuids.contains(uuid)) {
        return; // duplicate never consumes capacity
    }
    if (lookup.uuids.size() == 512) {
        lookup.truncated = true;
        return; // no 513th UUID, no overflow storage
    }
    lookup.uuids.add(uuid);
}
```

关键边界：

- 511 个唯一 UUID：`size=511`，`truncated=false`；
- 恰好 512 个唯一 UUID，且没有更多：`size=512`，`truncated=false`；
- 第 513 个**新** UUID：`size==512`，设置 `truncated=true`，不加入；
- 512 满后再次出现已存在 UUID：先 `contains`，跳过，不触发截断；
- parent UUID 与普通目标 UUID 相同：Set 去重，不重复消耗容量；
- 截断后停止所有后续 add，不保存溢出 UUID。

数据源：

```text
requestedTargets
validAttackTargets
observedPartUuids
requestedPartParentUuids.values()
observedPartParentUuids.values()
```

### 3.5 isLateTarget 修复

返回三分：

```text
LATE_MATCHED
NOT_LATE
UNKNOWN_TRUNCATED
```

规则：

1. target UUID 在 lookup -> LATE_MATCHED；
2. target 是 PartEntity 且 parent 在 lookup -> LATE_MATCHED；
3. 未匹配且 `truncated=true` -> UNKNOWN_TRUNCATED；
4. 未匹配且未截断 -> NOT_LATE。

`UNKNOWN_TRUNCATED`：

- 不调用 active Session；
- 不增加 `lateEventCount`；
- 增加独立的 `lateUnknownEventCount`；
- 不修改任何 Context；
- 不保存 UUID；
- 不占 64 预算；
- 不创建 TargetState；
- 不产生 PARTIAL_LATE_EVENT。

### 3.5.1 late 计数器

Tracker：

```text
long lateEventCount
long latePartEventCount
long lateCanceledEventCount
long lateUnknownEventCount
```

语义：

| 计数器 | 语义 | 子集关系 |
|---|---|---|
| `lateEventCount` | 确认命中 recent lookup 的迟到事件 | 总数 |
| `latePartEventCount` | 其中 PartEntity 迟到 | <= lateEventCount |
| `lateCanceledEventCount` | 其中 canceled 迟到 | <= lateEventCount |
| `lateUnknownEventCount` | lookup 截断、无法确认归属 | 不是 lateEvent 子集 |

规则：

- 全部 long；
- `saturatingIncrement`；
- `Long.MAX_VALUE` 保持，不回绕、不变负；
- UNKNOWN_TRUNCATED 不进 lateEvent/part/canceled；
- 重复 UNKNOWN 每次计数；
- ServerStarted/ServerStopping 重置；
- 不保存 UUID。

### 3.6 生成时机

`recentTargets` 只在 Tracker 事件入口生成：

- `onAttackEntity`；
- `onLivingEvent`。

不在每 tick 生成完整 lookup；`onServerTick` 只维护 recent contexts。

### 3.7 sealed Context 读取

- 只读取 `CombatAttackContext` 不可变快照 accessors；
- 不修改 Context；
- 不重新打开 sealed；
- 不写入 Session；
- 不创建 TargetState。

### 3.8 匹配优先级

```text
1. exact target UUID
2. PartEntity parent UUID
3. unknown/truncated diagnostic
4. not late
```

parent 与 exact 同时命中时，exact 优先；语义相同，都是 LATE_MATCHED。

---

## 4. C12-C09-02：真实 Tracker 分发测试设计

### 4.1 测试辅助边界

现有 `CombatAttackTracker` 的 public 事件入口需要 `ServerPlayer`/Entity，单元测试无法安全构造真实 Forge 对象。

设计一个最小 **PACKAGE_PRIVATE_SEAM**：

```text
CombatAttackTrackerTestAccess
  seedRecentContext(UUID playerId, CombatAttackContext sealedContext)
  seedActiveSession(UUID playerId, CombatAttackSession session)
  dispatchAttack(UUID playerId, UUID targetUuid, UUID parentUuid, boolean part, boolean canceled)
  dispatchLiving(UUID playerId, UUID targetUuid, boolean canceled)
  recentLookupForTest(UUID playerId)
```

要求：

- 测试辅助只构造状态和 UUID 输入；
- 真正调用 `CombatAttackTracker` 的同一 `isLateTarget` / `recordLate` / 分发分支；
- 不直接修改 late 计数器；
- 不直接修改 Session 的 valid/coverage；
- 明确标记 `PACKAGE_PRIVATE_SEAM`，不是真实 Forge Event 测试。

如测试辅助无法避免字段注入，必须标记 `FIELD_INJECTION`，不能当作真实生产路径。

### 4.2 测试 A：PartEntity parent 迟到污染防护

流程：

1. 创建 A，观察 PartEntity C，parent P；
2. A finish/seal；
3. seedRecentContext(A)；
4. 创建 B active，请求目标 B；
5. dispatchLiving(A.player, P, false)；
6. 检查。

预期：

```text
Tracker lateEventCount +1
B Session 未接收事件
B targetEventObservations 不变
B validDamage/result/coverage 不变
A sealed Context 不变
B registry targets 不变
无新 Context/TargetState/UUID
```

### 4.3 测试 B：requestedPartParentUuids

A 只通过请求 part C（parent P）建立关系，不观察 C；dispatch late P。  
预期同 A；验证 requested parent 聚合。

### 4.4 测试 C：Part UUID 迟到

dispatch target C（PartEntity UUID）而非 P。  
预期 LATE_MATCHED，B 不变；确认原有能力未退化。

### 4.5 测试 D：无 active Session

seal A，seedRecent，不创建 B；dispatch late P。  
预期：

```text
Tracker late +1
不创建 Session/Context/TargetState
A 不变
```

### 4.6 测试 E：重复迟到

同一 target P 连续 dispatch 两次。  
预期：

```text
lateEventCount +2
不因多监听器/重复检查而只算一次或多算
B 不被污染
```

### 4.7 测试 F：普通 + PartEntity 混合

A Context 同时包含：

```text
普通目标 N
PartEntity C1 parent P1
PartEntity C2 parent P2
```

分别 dispatch N/C1/C2/P1/P2；全部应 LATE_MATCHED。  
检查 lookup size <=512，无重复 UUID。

### 4.8 测试 H / C09-T-H：同一目标连续攻击

流程：

1. A 观察普通实体 P；
2. A seal；
3. B 开始并请求攻击 P；
4. dispatch target P；
5. 检查。

预期：

- P 命中 recent lookup；
- 事件判定为 `LATE_MATCHED`；
- `lateEventCount +1`；
- B 不接收事件；
- A sealed Context 不变；
- 不推断事件属于 A 或 B；
- 不尝试重新分配事件。

设计明确：

Forge 事件没有可靠的 Better Combat attackId。  
系统无法区分：

```text
A 的迟到事件
B 对同一 P 的正常事件
```

因此为了 Session 隔离：

- 允许丢失部分 B 的正常攻击观察；
- 该行为是保守隔离，不是精确攻击归属；
- 不得描述为精确识别；
- 不得为了保留 B 而把事件交给 B。

### 4.9 测试 G：lookup 截断

构造超过 512 个 recent UUID（8 contexts × 多 parent 关系）。  
预期：

- lookup `truncated=true`；
- 未匹配事件不进入 B；
- `lateUnknownEventCount` 增加；
- `lateEventCount` 不增加；
- 不保存溢出 UUID；
- B 不变。

### 4.10 测试分类

| 测试 | 分类 |
|---|---|
| A/B/C/D/E/F/H | PACKAGE_PRIVATE_SEAM（Tracker 分发分支，非 Forge） |
| G（lookup 截断） | PACKAGE_PRIVATE_SEAM |
| UNKNOWN_TRUNCATED 计数 | PACKAGE_PRIVATE_SEAM |
| 真实 Forge 事件 | NOT_TESTED |
| 真实 PartEntity | NOT_TESTED |

---

## 5. C12-CNT-01：六计数器测试矩阵

### 5.1 计数器

```text
targetBudgetOverflowCount
partTargetOverflowCount
overflowEventCount
overflowCanceledEventCount
eventDetailOverflowCount
eventDetailTruncatedTargetCount
```

### 5.2 完整矩阵

| ID | 计数器 | 触发 | 预期 |
|---|---|---|---|
| CNT13-T01 | target | 64 后第 65 普通 | target=1；part=0 |
| CNT13-T02 | target/part | 64 后第 65 Part | target=1；part=1 |
| CNT13-T03 | target | 重复已有目标 | 不增加 |
| CNT13-T04 | target | 同 parent 多个 Part | 每个新 part 1；共享 64 |
| CNT13-T05 | overflowEvent | 未准入普通事件 | overflowEvent+1 |
| CNT13-T06 | overflowCanceled | 未准入 canceled | event+1；canceled+1 |
| CNT13-T07 | eventDetail | 同一目标 9 条 | detailOverflow=1；明细=8 |
| CNT13-T08 | eventDetail | 同目标 10 条 | detailOverflow=2；明细=8 |
| CNT13-T09 | truncatedTarget | 同目标首次第 9 条 | truncatedTarget=1 |
| CNT13-T10 | truncatedTarget | 同目标第 10 条 | 仍为 1 |
| CNT13-T11 | truncatedTarget | 第二目标第 9 条 | 变为 2 |
| CNT13-T12 | subset | part/target，canceled/event | 子集成立 |
| CNT13-T13 | saturation | `saturatingIncrement(Long.MAX_VALUE)` | 保持 MAX，不变负 |
| CNT13-T14 | lateEvent | late 普通 | lateEvent+1 |
| CNT13-T15 | latePart | late Part | lateEvent+1；latePart+1 |
| CNT13-T16 | lateCanceled | late canceled | lateEvent+1；canceled+1 |
| CNT13-T17 | late repeat | 重复 late | 每次 +1；无 UUID |
| CNT13-T18 | late no active | 无 active | Tracker +1；不建 Context |
| CNT13-T19 | 511 唯一 UUID | lookup truncated=false |
| CNT13-T20 | 512 唯一 UUID，无更多 | lookup truncated=false |
| CNT13-T21 | 第 513 新 UUID | truncated=true；不加入 |
| CNT13-T22 | 512 满后重复 UUID | 跳过；不触发 truncated |
| CNT13-T23 | parent 与普通目标同 UUID | 去重；不重复占容量 |
| CNT13-T24 | UNKNOWN_TRUNCATED | lateUnknownEventCount+1；lateEvent 不变 |
| CNT13-T25 | UNKNOWN_TRUNCATED + active B | B 不变；不创建 TargetState |
| CNT13-T26 | lateUnknown 饱和 | 保持 Long.MAX_VALUE |

### 5.3 测试路径

- CNT13-T01..T04：Registry/Session 生产逻辑；
- CNT13-T05..T06：Session.observeLivingEvent + Registry overflow；
- CNT13-T07..T11：Session event detail；
- CNT13-T12：Context 快照；
- CNT13-T13：package-private `saturatingIncrement` 纯函数；
- CNT13-T14..T18：Tracker late dispatch seam。

不得直接设置计数器字段。

### 5.4 十个生产计数器的实际饱和路径测试

本小节测试 **10 个实际生产计数器**，不是只调用 `saturatingIncrement` 纯函数。

计数器：

```text
1. targetBudgetOverflowCount
2. partTargetOverflowCount
3. overflowEventCount
4. overflowCanceledEventCount
5. eventDetailOverflowCount
6. eventDetailTruncatedTargetCount
7. lateEventCount
8. latePartEventCount
9. lateCanceledEventCount
10. lateUnknownEventCount
```

### 5.4.1 测试初始化边界

允许新增 package-private 测试辅助：

```text
CombatTargetRegistry.seedCounterForTest(Counter, long)
CombatAttackTracker.seedLateCounterForTest(Counter, long)
```

限制：

- 只能用于初始化 `Long.MAX_VALUE - 1`；
- 不扩大 public 字段访问权限；
- 不直接把结果写成 Long.MAX_VALUE；
- 递增必须经过生产更新方法：
  - `admit`
  - `recordOverflowEvent`
  - `TargetState.record`
  - `recordLate` / Tracker dispatch
- 测试辅助标记 `PACKAGE_PRIVATE_SEAM`，不是 Forge Event 测试。

### 5.4.2 S01-S10 矩阵

| ID | 计数器 | 生产递增路径 | 初始 | 第 1 次 | 第 2 次 | 子集/附加断言 |
|---|---|---|---|---|---|---|
| CNT13-S01 | targetBudgetOverflowCount | `CombatTargetRegistry.admit` 满 64 后新目标 | MAX-1 | MAX | MAX | part<=target |
| CNT13-S02 | partTargetOverflowCount | 满 64 后新 Part admit | MAX-1 | MAX | MAX | part<=target |
| CNT13-S03 | overflowEventCount | `recordOverflowEvent(false)` 经 Session overflow | MAX-1 | MAX | MAX | canceled<=event |
| CNT13-S04 | overflowCanceledEventCount | `recordOverflowEvent(true)` 经 Session overflow | MAX-1 | MAX | MAX | canceled<=event |
| CNT13-S05 | eventDetailOverflowCount | 目标第 9、10 条事件 `TargetState.record` | MAX-1 | MAX | MAX | 明细仍为 8 |
| CNT13-S06 | eventDetailTruncatedTargetCount | 目标 A 第 9 条首次截断，再目标 B 第 9 条首次截断 | MAX-1 | MAX | MAX | 同一目标第 10 条不再增加 |
| CNT13-S07 | lateEventCount | Tracker late 普通 dispatch | MAX-1 | MAX | MAX | part/canceled<=late |
| CNT13-S08 | latePartEventCount | Tracker late Part dispatch | MAX-1 | MAX | MAX | part<=late |
| CNT13-S09 | lateCanceledEventCount | Tracker late canceled dispatch | MAX-1 | MAX | MAX | canceled<=late |
| CNT13-S10 | lateUnknownEventCount | UNKNOWN_TRUNCATED dispatch | MAX-1 | MAX | MAX | 不增加 lateEvent |

### 5.4.3 每个 S 测试的固定步骤

对每个计数器：

1. 使用对应 production 准备状态（64 目标、8 事件、late lookup 等）；
2. 用 package-private seam 将该计数器初始化为 `Long.MAX_VALUE - 1`；
3. 执行一次对应的**生产递增路径**，断言值变为 `Long.MAX_VALUE`；
4. 再次执行同一生产递增路径，断言仍为 `Long.MAX_VALUE`；
5. 断言值未变为负数；
6. 断言其他计数器语义没有被饱和破坏；
7. 子集关系仍然成立。

### 5.4.4 饱和场景下的子集关系

必须单独断言：

```text
partTargetOverflowCount <= targetBudgetOverflowCount
overflowCanceledEventCount <= overflowEventCount
latePartEventCount <= lateEventCount
lateCanceledEventCount <= lateEventCount
```

测试构造：

- target 和 part 都初始 MAX-1，然后触发一次 Part 超限；
- overflowEvent 和 canceled 都初始 MAX-1，然后触发一次 canceled 超限；
- lateEvent 和 latePart 都初始 MAX-1，然后触发一次 late Part；
- lateEvent 和 lateCanceled 都初始 MAX-1，然后触发一次 late canceled。

断言：

- 两个值都饱和到 MAX；
- 子集仍成立；
- 没有负数；
- 另一个不相关计数器不被错误递增。

### 5.4.5 纯函数测试的地位

`CNT13-T13` 只验证 `saturatingIncrement` 纯函数，不能替代 S01-S10。

S01-S10 必须证明：

- 每个生产字段确实使用饱和递增；
- 递增经过生产更新方法；
- 第二次递增仍保持 MAX；
- 子集在所有饱和场景成立。

### 5.4.6 测试分类与预期总数

| 测试 | 分类 |
|---|---|
| CNT13-T01..T12, T14..T26 | PACKAGE_PRIVATE_SEAM / production path |
| CNT13-T13 | 纯函数 |
| CNT13-S01..S10 | PACKAGE_PRIVATE_SEAM 初始化 + 生产更新路径 |
| C09-T-A..H | PACKAGE_PRIVATE_SEAM Tracker dispatch |
| 真实 Forge | NOT_TESTED |

设计预期新增：

```text
C09 A-H: 8
CNT13 T01-T26: 26
CNT13 S01-S10: 10
合计新增设计：44
现有 115
设计预期总数：159
```

该总数只是设计预期，不是已通过数量；未实现即不得标为通过。

### 5.4.7 验收新增

- 10 个生产计数器各通过实际生产递增路径两次饱和测试；
- 4 组子集在饱和情况下成立；
- 纯函数测试不作为生产计数器证据；
- 原 115 项全部保留并通过。

---

## 6. C12-LOW-01：附带审查

### lastServerTickAtStart

当前字段只赋值，没有读取：

```text
CombatAttackTracker.java:33
```

设计：

- 本次最小修复**删除**该字段；
- 不改变时钟算法；
- 保留 `serverTick` / `lastRawServerTick` / `clockStarted`。

### recordLateForTest

当前名称容易被误认为生产事件路径。

设计：

- 重命名为 `recordLateDiagnosticForTest`；
- 明确 `PACKAGE_PRIVATE_SEAM`；
- 新 A/B 测试必须经过 `isLateTarget`/dispatch，不再直接调用它。

### 其他测试辅助

```text
seedRecentSealedTargetsForTest
resetClockForTest
advanceClockForTest
```

均标记 `FOR_TEST_ONLY`，不作为生产路径证据。

---

## 7. 预计修改文件

### 修改

```text
CombatAttackTracker.java
  - recentTargets 返回 RecentTargetLookup
  - 加入 parent values
  - isLateTarget 三分
  - 删除 lastServerTickAtStart
  - 重命名测试 seam
  - 新增 late 诊断测试支持
```

### 可能新增

```text
CombatRecentTargetLookup.java
  - Set<UUID> uuids
  - boolean truncated
```

### 新增测试

```text
CombatGate213RegressionTest.java
```

### 不修改

```text
CombatAttackSession.java  // H09 已完成；本修复不改
CombatTargetRegistry.java
CombatTargetState.java
BetterCombatCompat.java
BetterCombatAttackAdapter.java
Gradle / 依赖 / 第三方 JAR
Mixin / 网络包
```

---

## 8. 风险分析

| 风险 | 等级 | 缓解 |
|---|---|---|
| parent 聚合使 lookup 超过 512 | HIGH | RecentTargetLookup + truncation |
| 截断后错误把事件交给 B | HIGH | UNKNOWN_TRUNCATED -> 不调用 Session |
| 正常 B 事件在 lookup 截断时被丢弃 | MEDIUM | 保守丢弃并计数；不污染 B |
| 重复 parent/普通 UUID | LOW | Set 去重 |
| 测试 seam 被误当 Forge | MEDIUM | 明确 PACKAGE_PRIVATE_SEAM |
| 删除 lastServerTickAtStart 影响审计 | LOW | 只删未读字段 |

---

## 9. 实施顺序

1. 新增 `RecentTargetLookup`；
2. 修改 recentTargets 聚合 + 512 截断；
3. 修改 isLateTarget 三分；
4. 修改 Tracker 分发：UNKNOWN_TRUNCATED 不调用 Session；
5. 删除 lastServerTickAtStart；
6. 重命名 recordLateForTest；
7. 新增 TestAccess；
8. 实现 A-F/G 测试；
9. 补齐 CNT13 矩阵；
10. compileJava/test/build；
11. 客户端/服务器启动；
12. 重新审计。

---

## 10. 验收标准

- parent UUID 被 recent lookup 覆盖；
- 普通 parent 迟到事件不会进入 B；
- Part UUID late 仍能识别；
- no active 只计数；
- lookup <=512 且 truncated 显式；
- UNKNOWN_TRUNCATED 不调用 Session；
- 不保存 late UUID；
- 不创建 TargetState；
- 六计数器独立测试；
- 十个生产计数器 S01-S10 全部通过实际生产递增路径的双次饱和测试；
- 四组子集关系在饱和情况下仍成立；
- 纯函数测试不作为生产计数器证据；
- 原 115 项全部保留通过；
- 真实 Forge 仍标记 NOT_TESTED。

---

## 11. 状态

```text
DESIGN_COMPLETE_PENDING_REVIEW
```

本阶段只生成设计文档；未修改源码、测试、Gradle、依赖或第三方 JAR；未创建 Git commit；未进入 Phase 05。
