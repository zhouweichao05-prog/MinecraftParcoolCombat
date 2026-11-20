# PHASE04_GATE2_6_SOURCE_AUDIT.md

> Phase 04 Gate 2.6 — 独立源码审查  
> 审查对象：`D:\MinecraftParcoolCombat`  
> 审查性质：只读；未修改源码、测试、Gradle、依赖或第三方 JAR。  
> 最终结论：**AUDIT_FAIL**

---

## 1. 审查前 Git 状态与依赖哈希

### Git

```text
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无（git diff --stat 为空）
Git commit：无
```

工作区全部为未跟踪文件，包括已有 Phase 04 计划/报告/日志、`src/main/java/.../combat/`、`src/main/java/.../compat/` 和 `src/test/java/...`。  
未执行 `git add`、`git commit`、`git reset`。

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

与上一轮记录一致，未发现依赖替换。

---

## 2. 实际检查的文件

### 源码

```text
src/main/java/com/MrGhost/parkourCombat/combat/
  CombatAttackSession.java
  CombatAttackTracker.java
  CombatAttackContext.java
  CombatBridge.java
  CombatDamageSourceKind.java
  CombatDamageSourceClassification.java
  CombatEventType.java
  CombatEventState.java
  CombatEventAggregateState.java
  CombatDamageAssociation.java
  CombatEventObservation.java
  CombatTargetEventObservation.java
  CombatTargetMatch.java
  CombatTargetResolution.java
  CombatTargetCoverage.java
  CombatAttackResult.java
  CombatAttackPhase.java
  CombatAttackEndReason.java

src/main/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatCompat.java
  BetterCombatAttackAdapter.java
```

### 测试

```text
src/test/java/com/MrGhost/parkourCombat/combat/
  CombatAttackSessionRemediationTest.java
  CombatAttackRequestTest.java
  CombatTargetDamageObservationTest.java

src/test/java/com/MrGhost/parkourCombat/compat/bettercombat/
  BetterCombatAttackAdapterTest.java
```

### 已有测试结果

```text
100 tests / 0 failures
CombatAttackSessionRemediationTest: 24 tests
```

---

## 3. C1：PartEntity coverage 审查

### 通过项

- `requestedPartUuids` 与 `observedPartUuids` 分离：
  - `CombatAttackSession.java:40-43`
- `requestedPartParentUuids` 与 `observedPartParentUuids` 分离：
  - `CombatAttackSession.java:41-43`
- PartEntity 实际观察立即降低 coverage：
  - `CombatAttackSession.java:227-236`
- parent UUID 不可得时记录 `observedPartUnmatched`，不伪造 UUID：
  - `CombatAttackSession.java:229-231`
- parent match 不升级普通完整目标：
  - `CombatAttackSession.java:227-236`
- finish 重新计算 coverage：
  - `CombatAttackSession.java:506-517`
- `requestedTargetsComplete` 以最终 coverage 为准：
  - `CombatAttackSession.java:425-426`
- 重复 part / 混合普通目标：
  - 使用 `LinkedHashSet`/`LinkedHashMap`；C1 测试覆盖普通+part 混合。

### 结论

**C1：PASS（源码层面）**。  
未找到 parent match 保持 COMPLETE 的路径。

限制：

- C1 测试使用 UUID test seam，未实际构造 `PartEntity` 和 `ServerLevel.getEntityOrPart`。
- 真实 PartEntity API 路径未实机测试。

---

## 4. C2：伤害来源分类审查

### 通过项

- `CombatBridge` 三类伤害事件全部传入：
  ```text
  AMBIGUOUS_PLAYER_ATTACK
  ```
  证据：
  - `CombatBridge.java:205-235`
- `CombatAttackSession` 生产事件固定：
  ```text
  CombatDamageSourceKind.DIRECT_PLAYER_MELEE
  CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK
  betterCombatExclusiveProven = false
  ```
  证据：
  - `CombatAttackSession.java:406-417`
- `CombatEventObservation` 构造器拒绝 `true`：
  - `CombatEventObservation.java:20-28`
- `MATCHED_UNCANCELLED_CHAIN` 只用于 association，不设置独占：
  - `CombatAttackSession.java:329-350`
- `healthReductionVerified` 仍为 false：
  - `CombatAttackSession.java:497`
- 未发现通过 amount、事件顺序或目标匹配设置独占来源的路径。

### 问题

`CombatDamageSourceClassification` 枚举仍保留：

```java
PRIMARY_PLAYER_ATTACK
```

证据：

```text
CombatDamageSourceClassification.java:3-6
```

生产代码不再使用它；这是兼容旧测试/旧类型的残留值，不是当前生产路径。  
建议后续移除或标为 `@Deprecated`，避免未来误用。

### 结论

**C2：PASS（当前生产路径）**。  
无 PRIMARY 生产路径；无独占证明；无实际生命值减少证明。

---

## 5. C3：Forge 事件关联审查

### 通过项

- 监听器：
  - `CombatBridge.java:185-236`
  - `LOWEST` + `receiveCanceled=true`。
- 未取消 AttackEntity 才建立有效链：
  - `CombatAttackSession.java:267-285`
- AttackEntity 取消后链失效：
  - `CombatAttackSession.java:248-264`
- 取消后不能被普通 AttackEntity 重新激活：
  - `CombatAttackSession.java:267-272`
- 无 AttackEntity 的伤害为 `NO_ATTACK_EVENT`：
  - `CombatAttackSession.java:329-340`
- 横扫次级目标因不匹配 requested/parent 被忽略：
  - `CombatAttackSession.java:305-307`
- 取消事件保留状态：
  - `CombatAttackSession.java:248-264`, `:347-350`
- 取消 LivingAttack/Hurt/Damage 不 positive：
  - `CombatAttackSession.java:342-343`
- amount 只记录，不求和：
  - `CombatEventObservation` / `TargetAccumulator`。

### 已确认 HIGH 问题：取消 AttackEntity 后 `validAttackTargets` 未移除

`CombatAttackSession.observeAttackTarget` 在未取消时加入：

```text
CombatAttackSession.java:278-280
validAttackTargets.add(targetUuid);
```

当同一目标随后出现**已取消**的 AttackEntityEvent：

```text
CombatAttackSession.java:248-259
```

代码替换了 `attackChains` 并设置 canceled，但没有：

```java
validAttackTargets.remove(targetUuid);
```

结果：

- `CombatAttackContext.attackObservedTargets` 仍可能包含已取消目标；
- 旧的 `validAttackTargets` 集合与实际有效链不一致；
- 下游若只读取 `attackObservedTargets`，可能把已取消链当作有效观察目标。

同类问题：

- `damageObservedTargets` 在 canceled 的 Living 事件上仍会加入：
  - `CombatAttackSession.java:342-345`
  这不是当前 result 的直接 positive 来源，但旧字段语义不干净。

### 结论

**C3：CONDITIONALLY_PASS / HIGH 状态污染**。  
取消事件本身不 positive，但 `attackObservedTargets` 可能保留已取消目标。

---

## 6. C4：Session 归属审查

### 通过项

- 新请求结束旧 active：
  - `CombatAttackTracker.java:35-41`
- sealed 后 finish 返回 null：
  - `CombatAttackSession.java:420-426`
- 旧 sealed 目标迟到事件被忽略：
  - `CombatAttackSession.java:218-225`, `:308-310`
- 不重新打开 context：
  - `CombatAttackSession.java:420-426`
- 不同目标不会因请求集合之外的事件进入当前 session：
  - `CombatAttackSession.java:305-307`

### 已确认 MEDIUM/HIGH 问题：recent sealed 目标没有时间过期

`CombatAttackTracker`：

```text
CombatAttackTracker.java:187-216
```

- `recent` 固定最大 8 个 context；
- `recentTargets` 从所有 recent context 汇总目标：
  - `CombatAttackTracker.java:202-211`
- `onServerTick` 只对 active session 做 TTL：
  - `CombatAttackTracker.java:107-125`
- recent context 没有时间 TTL，只在新 context 超过 8 个时被挤出；
- 玩家在线且停止攻击时，recent 会一直保留。

影响：

- 同一目标在 8 次新攻击前可能持续被当作 recent sealed；
- 迟到事件会一直被忽略，导致 UNKNOWN；
- 内存虽然按 8 个 context 有界，但没有时间过期规则，也不符合“明确过期规则”。

### 已确认 HIGH 问题：recent 目标集合可被 PartEntity 放大

`recentTargets` 聚合：

```java
context.observedPartUuids()
context.observedPartParentUuids().values()
```

而 `observedPartUuids` 本身没有 64 个上限（见 C5），因此 recent 集合可以被大量 PartEntity 放大。

### 同目标/同 tick

- 同目标连续攻击：当前被 recent sealed 拦截，这是保守行为；
- 不同目标：不会自动关联；
- 同 tick：没有事件序号/攻击 ID，不能绝对区分；当前依赖场景和目标集合；
- 未发现重新打开 sealed。

### 结论

**C4：CONDITIONALLY_PASS / MEDIUM-HIGH**。  
sealed 保护存在，但 recent 缺少时间过期，且受 C5 PartEntity 容量问题影响。

---

## 7. C5：容量与截断审查

### 常量与基础行为

```text
MAX_OBSERVATION_TARGETS = 64
MAX_OBSERVATIONS_PER_TARGET = 8
```

证据：

```text
CombatAttackSession.java:26-27
```

`targetEvents` 第 65 个目标不创建：

```text
CombatAttackSession.java:395-405
```

每目标超过 8 条只计数/truncated：

```text
CombatAttackSession.java:605-621
```

`CombatTargetEventObservation` 拒绝超过 8 条：

```text
CombatTargetEventObservation.java:27-41
```

### CRITICAL：PartEntity/parent 可绕过目标数量上限

这是本次审查的阻断问题。

`targetEvents` 有 64 上限，但以下集合没有独立 64 上限：

```text
CombatAttackSession.java:42-51
  observedPartUuids
  observedPartParentUuids
  validAttackTargets
  canceledAttackTargets
  overlapTargets
  attackChains
```

`isRelevantAttackTarget` 只要 parent UUID 在 requestedTargets/requestedPartUuids 中就返回 true：

```text
CombatAttackSession.java:369-374
```

因此请求一个 parent P 后，攻击 P 的任意 PartEntity C1..Cn 都被视为相关：

```text
CombatAttackSession.java:215-285
```

每次 part 事件：

- `observedPartUuids.add(partUuid)`；
- `observedPartParentUuids.put(...)`；
- `attackChains.put(partUuid, ...)`；
- `validAttackTargets.add(partUuid)`；
- 可能 `canceledAttackTargets` / `overlapTargets`。

这些集合不受 `targetEvents` 的 64 上限约束。  
第 65 个 part 仍会进入这些集合，即使 `targetEvents` 拒绝创建观察对象。

影响：

- C5“PartEntity / parent 不能绕过目标数量上限”不成立；
- 潜在无界 target UUID 状态；
- recentTargets 进一步放大；
- 不能声称容量隔离完整。

### 其他容量问题

- `eventObservationOverflowCount` 只在 `targetEvents` 上限触发时增加；
- 每目标超过 8 条时只设置 `damageObservationTruncated`，不增加 `eventObservationOverflowCount`；
- `targetEvents` 的 64 上限包含 AttackEntity 目标，实际上是“事件目标”，不是严格的“伤害观察目标”；
- 这些是语义问题，C5 的主阻断是 part 集合绕过。

### 结论

**C5：FAIL（CRITICAL）**。  
第 65 个普通目标受控，但第 65 个 PartEntity 可绕过并扩大多个集合。

---

## 8. Handler 与线程审查

### 当前状态

```text
BetterCombatCompat.java:91-101
wrappedOrderingKnown = false
handlerIdentity = UNKNOWN_PREEXISTING
installationState = DEGRADED_OR_UNKNOWN
```

实际日志：

```text
[BetterCombatCompat] installed with orderingUnknown=true
```

未发现：

- 自动覆盖未知 Handler；
- 反射拆解未知包装链；
- 绕过未知保护。

### 通过项

- `HandlerWrapper` 只替换 C2S AttackRequest；
- 正常路径原始 Handler 调用一次：
  - `BetterCombatCompat.java:169-180`
- 原始异常原样抛出；
- 停服检测跳过原始 Handler：
  - `BetterCombatCompat.java:131-137`
- `server.tell(TickTask)` 保持；
- 停服竞态仍存在；
- `isStopped()` 非 volatile。

### 结论

**Handler/线程：PASS（有已知限制）**。  
没有新引入的 Handler 覆盖或线程所有权破坏；但 `recentTargets` 没有过期，且 C5 集合可被 part 放大。

---

## 9. 100 项测试的实际覆盖情况

### 统计

```text
总测试：100
CombatAttackSessionRemediationTest：24
旧测试：76
失败：0
```

### H-32～H-37

| ID | 实际测试 | 结果 |
|---|---|---|
| H-32 | `orderingUnknownFullChainIsNotAnimationCandidate` | PASS |
| H-33 | `noEventsWithUnknownOrderingRemainsUnknown` | PASS |
| H-34 | `multipleEventsAreBoundedAndCanceledCountsRemain` | PASS |
| H-35 | `uncanceledThenCanceledEventKeepsBothAndAggregateMixed` | PASS |
| H-36 | `multipleEventsAreBoundedAndCanceledCountsRemain` | PASS |
| H-37 | `knownOrderingFullChainIsOnlyAnimationCandidate` / unknown 测试 | PASS（候选 API，不是真实动画） |

### T-01～T-18

| ID | 状态 | 说明 |
|---|---|---|
| T-01 | PASS（Session） | 完整链；source ambiguous |
| T-02 | PASS | 无 AttackEntity 不进入主链 |
| T-03 | PASS | 取消 AttackEntity 无伤害 |
| T-04 | PASS | LivingAttack 取消 |
| T-05 | PASS | LivingHurt 取消 |
| T-06 | PASS | LivingDamage 取消 |
| T-07 | PASS | 零伤害/无敌帧观察 |
| T-08 | NOT_TESTED | 精确跨目标 late 未构造 |
| T-09 | PASS | recent sealed 同目标 |
| T-10 | PASS | sealed 不重开 |
| T-11 | PASS（Session 简化） | 次级目标被忽略，不是真实横扫 |
| T-12 | PARTIAL | source 全 ambiguous；无真实其他 Mod 同 source |
| T-13 | PASS（UUID seam） | PartEntity parent coverage |
| T-14 | PASS（UUID seam） | part + normal |
| T-15 | PASS | 65 目标普通目标 |
| T-16 | PARTIAL | 两独立 Session；非真实多人 |
| T-17 | NOT_TESTED | 死亡/退出/维度 |
| T-18 | NOT_TESTED | 停服 |

### 测试质量问题

1. `otherModSameSourceRemainsAmbiguous` **没有真实“其他 Mod”注入**，只验证所有 direct 事件为 ambiguous。名称强于实际覆盖。
2. `sweepSecondaryTargetDoesNotEnterPrimaryTarget` 用普通 UUID 模拟 secondary，不是实际 `Player.attack` 横扫。
3. `partParentMatch...` 使用 UUID test seam，不是实际 `PartEntity` / `getEntityOrPart`。
4. `targetObservationLimit...` 只验证 `targetEvents`，**没有覆盖 PartEntity 绕过**。
5. `knownOrdering...` 通过 test seam 设置 `orderingKnown=true`，生产不可达。
6. 没有 Tracker 真实生命周期测试；
7. 没有 Bridge Forge 事件监听测试；
8. 没有测试“有效 AttackEntity 后取消”的 `validAttackTargets` 污染；
9. 没有测试 recent 过期；
10. 没有测试第 65 个 PartEntity。

### 结论

**测试：CONDITIONALLY_PASS**。  
100 个测试通过，但不能证明 C5 part 容量、Tracker/Bridge 生命周期和真实 Forge 事件。

---

## 10. 问题清单（按严重度）

### CRITICAL

#### C-01 PartEntity 绕过 64 目标上限

- 文件：`CombatAttackSession.java`
- 方法：`observeAttackTarget(UUID, boolean, UUID, boolean)`
- 位置：`42-51`, `227-285`, `369-374`, `395-405`
- 事实：
  - parent 请求时任意 part 事件都通过 relevance；
  - part 加入 `observedPartUuids`、`attackChains`、`validAttackTargets` 等；
  - 这些集合没有独立 64 上限；
  - 只有 `targetEvents` 有 64 上限。
- 影响：容量边界失效，可能无界状态；C5 验收失败。

### HIGH

#### H-01 取消 AttackEntity 后有效观察集合未移除

- 文件：`CombatAttackSession.java`
- 方法：`observeAttackTarget(UUID, ...)`
- 位置：`248-259`, `278-280`
- 事实：先有效 AttackEntity 加入 `validAttackTargets`；后取消只替换 chain，不移除 valid 集合。
- 影响：`CombatAttackContext.attackObservedTargets` 可能保留已取消目标；下游可能误判。

#### H-02 recent sealed 目标没有时间过期

- 文件：`CombatAttackTracker.java`
- 方法：`recentTargets`, `onServerTick`
- 位置：`107-125`, `187-216`
- 事实：recent 只按 8 个 context 淘汰，无 TTL；active 才有 TTL。
- 影响：同目标迟到事件长时间被忽略；recent 保留旧目标；与“明确过期规则”不符。

### MEDIUM

#### M-01 recentTargets 受 PartEntity 集合放大

- 文件：`CombatAttackTracker.java`
- 方法：`recentTargets`
- 位置：`202-211`
- 事实：聚合 `observedPartUuids`/parents，而它们受 C-01 影响。
- 影响：recent 集合可能异常大。

#### M-02 C2 残留 PRIMARY 枚举

- 文件：`CombatDamageSourceClassification.java`
- 位置：`3-6`
- 事实：生产不使用，但枚举仍暴露 `PRIMARY_PLAYER_ATTACK`。
- 影响：未来代码误用风险；当前无生产路径。

#### M-03 取消伤害仍加入旧 `damageObservedTargets`

- 文件：`CombatAttackSession.java`
- 位置：`342-345`
- 事实：`matched` 时不检查 `canceled`， canceled 事件也加入旧集合。
- 影响：旧字段语义不干净；新 result 已不 positive，但下游可能读旧字段。

#### M-04 `eventObservationOverflowCount` 不等价于每目标溢出

- 文件：`CombatAttackSession.java`
- 位置：`395-405`, `616-621`
- 事实：第 9 条只设 `damageObservationTruncated`，不增加 overflow count。
- 影响：计数语义不完整；不是阻断，但影响诊断准确性。

#### M-05 测试超出实际覆盖

- 文件：`CombatAttackSessionRemediationTest.java`
- 测试：`otherModSameSourceRemainsAmbiguous`, `sweepSecondaryTargetDoesNotEnterPrimaryTarget`, `partParent...`, `targetObservationLimit...`
- 事实：没有真实其他 Mod、真实横扫、真实 PartEntity、PartEntity 超限。
- 影响：测试通过不能证明对应真实行为。

### LOW

#### L-01 旧 context 字段与新字段重复

- 文件：`CombatAttackContext.java`
- 位置：`31-33`, `62-66`
- 事实：保留 `partTargetUuids`/`partParentUuids`/`partTargetUnmatched` 与新的 requested/observed 字段。
- 影响：下游可能读取旧字段，语义混淆。

#### L-02 `AttackChain.valid` 字段未使用

- 文件：`CombatAttackSession.java`
- 位置：`573-588`
- 事实：构造和保存，但没有读取。
- 影响：维护噪声。

#### L-03 `canceledAttackTargets` 未进入 context

- 文件：`CombatAttackSession.java`
- 位置：`46`, `249-259`
- 事实：内部记录，但 context 没有对应字段。
- 影响：诊断不可见。

---

## 11. 与 Gate 2.5 设计不一致之处

1. **C5 目标上限**：设计要求 PartEntity/parent 不能绕过 64 目标限制；实现只限制 `targetEvents`，未限制 part 相关集合。**不一致，阻断。**
2. **C4 过期规则**：设计要求 recent sealed 目标有明确容量和过期规则；当前只有 8 context 容量，无时间过期。**部分不一致。**
3. **C3 有效性**：设计要求取消 AttackEntity 链失效；内部 chain 失效，但 `validAttackTargets`/旧字段未清除。**部分不一致。**
4. **C2**：设计不允许 PRIMARY 生产；生产路径符合，但枚举残留。**非阻断**。
5. **C1**：requested/observed 分离和 coverage 重新计算符合设计。

---

## 12. 尚未测试的内容及影响

- 真实 `ServerPlayer`/Forge 事件链；
- 真实 `PartEntity` / `Entity#getEntityOrPart`；
- 真实横扫；
- 真实其他 Mod 同 DamageSource；
- 目标死亡/玩家退出/维度；
- 停服瞬间请求；
- 多玩家；
- Better Combat 缺失启动；
- Tracker 生命周期；
- Bridge 监听器实际运行线程；
- PartEntity 绕过 64 目标；
- 取消后 `validAttackTargets` 污染。

影响：

- 不能声明 C3/C4/C5 已完成真实集成验证；
- C5 的 CRITICAL 问题未被测试捕获；
- 不能进入 Phase 05。

---

## 13. 是否建议进入下一阶段实机测试

**不建议直接进入 Phase 05。**

建议顺序：

1. 先修复 C-01（PartEntity 容量绕过）。
2. 修复 H-01（取消 AttackEntity 后清除 valid 观察）。
3. 修复 H-02（recent 目标过期）。
4. 增加真实 PartEntity/Tracker/Bridge 事件测试。
5. 再执行客户端/专用服务器/多人实机测试。
6. 重新审计后再讨论 Phase 05。

---

## 14. 最终结论

# `AUDIT_FAIL`

原因：

- **CRITICAL**：PartEntity 可绕过 64 目标上限，破坏 C5；
- **HIGH**：取消 AttackEntity 后有效观察集合未清除；
- **HIGH/MEDIUM**：recent sealed 目标无时间过期；
- 测试未覆盖上述路径。

因此源码不能判定为与 Gate 2.5 设计完全一致；不进入 Phase 05。  
本次审计未修改任何源码、测试、Gradle、依赖或第三方 JAR，未创建 Git commit。
