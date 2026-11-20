# PHASE04_GATE2_9_INDEPENDENT_AUDIT.md

> Phase 04 Gate 2.9 — Gate 2.8 修复后的独立源码审查  
> 审查性质：只读；未修改源码、测试、Gradle、依赖或第三方 JAR。  
> 最终结论：**GATE_2_8_REJECTED**

---

## 1. Git 与工作区

```text
项目：D:\MinecraftParcoolCombat
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

工作区为未跟踪文件；未执行 `git add`、`commit`、`reset`、`checkout`。

### 未跟踪文件

包括：

```text
docs/PHASE04_GATE2_*.md
docs/logs/...
src/main/java/com/MrGhost/parkourCombat/combat/
src/main/java/com/MrGhost/parkourCombat/compat/
src/test/java/com/MrGhost/parkourCombat/combat/
src/test/java/com/MrGhost/parkourCombat/compat/
```

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

与既定值一致。

---

## 2. 实际检查文件

### 源码

```text
CombatAttackSession.java
CombatAttackTracker.java
CombatAttackContext.java
CombatTargetRegistry.java
CombatTargetState.java
CombatTargetCoverage.java
CombatDamageSourceClassification.java
CombatBridge.java
CombatTargetEventObservation.java
CombatEventObservation.java
BetterCombatCompat.java
```

### 测试

```text
CombatGate28RegressionTest.java
CombatAttackSessionRemediationTest.java
CombatAttackRequestTest.java
CombatTargetDamageObservationTest.java
BetterCombatAttackAdapterTest.java
```

现有测试结果：

```text
108 tests / 0 failures
```

---

## 3. C-01：TargetRegistry 审查

### 结论：CONDITIONAL PASS

### 已验证

- `CombatTargetRegistry.MAX_TARGETS = 64`：
  - `CombatTargetRegistry.java:13`
- Registry `admit` 是唯一创建 TargetState 的入口：
  - `CombatTargetRegistry.java:28-46`
- 普通目标和 PartEntity 都走同一个 `targets` map：
  - `CombatTargetRegistry.java:28-46`
- 第 65 个目标返回 null，不创建状态：
  - `CombatTargetRegistry.java:37-44`
- 目标状态最多 64：
  - `CombatTargetRegistry.java:16`
- Context 的目标集合由 Registry 快照生成：
  - `CombatAttackSession.java:335-470`
- `observedPartUuids`、`requestedPartUuids`、`validAttackTargets` 等来自 TargetState：
  - `CombatAttackSession.java:335-420`
- 事件详情每目标最多 8：
  - `CombatTargetRegistry.MAX_EVENTS_PER_TARGET`
  - `CombatTargetState.record`
- recent contexts 8：
  - `CombatAttackTracker.RECENT_LIMIT`
- recentSealedTargets 512：
  - `CombatAttackSession.MAX_RECENT_SEALED_TARGETS = 8 * 64`
- recentTargets 查询上限 512：
  - `CombatAttackTracker.recentTargets`
- 溢出只进 long 计数器，不保存 UUID：
  - `CombatTargetRegistry.java:37-44`

### 发现的问题

#### C09-M01：late PartEntity 事件不会降低 coverage

- 文件：`CombatAttackSession.java`
- 方法：`observeAttackTarget(UUID, boolean, UUID, boolean)`
- 位置：`209-225`
- 事实：

```java
if (recentSealedTargets.contains(targetUuid)
        || (parentUuid != null && recentSealedTargets.contains(parentUuid))) {
    lateEventCount++;
    return;
}
```

这个早退发生在 PartEntity 的 observed/coverage 更新之前。  
如果请求 parent，随后收到属于 recent sealed 的 PartEntity 事件：

- 事件被当作 late 忽略；
- `targetCoverage` 可能仍是 `COMPLETE`；
- `lateEventCount > 0` 使最终 result 为 UNKNOWN；
- 但 context 的 coverage 没有反映该 PartEntity 观察。

影响：

- 结果不会错误变成 no-attack；
- 但 coverage 与“实际出现过 part 事件”不一致；
- 属于 coverage 语义缺陷，不是 64 容量漏洞。

等级：**MEDIUM**。

### C-01 通过范围

- 64 canonical target 容量已统一；
- PartEntity 不再绕过容量；
- 溢出不保存 UUID；
- context 目标集合有界；
- `partTargetOverflowCount <= targetBudgetOverflowCount`：
  - Registry 在同一 admission 失败路径同时递增，成立。

---

## 4. H-01：攻击链状态机审查

### 结论：FAIL

### 已验证

`CombatTargetState.ChainState`：

```text
NONE / VALID / CANCELED / OVERLAP
```

转换入口集中在 `CombatAttackSession.observeAttackTarget`：

- 未取消 -> VALID：
  - `CombatAttackSession.java:224-245`
- 取消 -> CANCELED：
  - `CombatAttackSession.java:212-223`
- 已有 VALID/OVERLAP + 未取消 -> OVERLAP：
  - `CombatAttackSession.java:225-233`
- CANCELED + 后续事件 -> 保持 CANCELED：
  - `CombatAttackSession.java:223-231`

`validAttackTargets` 在 finish 中只从 `chainState == VALID` 派生：

- `CombatAttackSession.java:382-390`

`canceledAttackTargets` 只从 `chainState == CANCELED` 派生：

- `CombatAttackSession.java:390-393`

历史事件保留在 TargetState 的 events 列表；`attackEntityCount` 只增不减。

### 已确认 HIGH 问题

#### H09-H01：取消后，取消前的 matched damage 仍可进入 validDamageTargets 和 positive result

- 文件：`CombatAttackSession.java`
- 方法：`finish(CombatAttackEndReason, long)`
- 位置：`396-409`、`426-430`
- 事实：

`validDamage` 的加入条件是：

```java
if (!event.canceledAtObservation()
        && event.association() == MATCHED_UNCANCELLED_CHAIN) {
    anyMatchedDamage = true;
    anyPositive |= event.positiveAtObservation();
    validDamage.add(state.targetUuid());
}
```

这里没有检查：

```java
state.chainState() == ChainState.VALID
```

因此：

1. 目标先有有效 AttackEntity；
2. 随后产生 LivingDamage（matched，positive）；
3. 随后收到 canceled AttackEntity，链变 CANCELED；
4. finish 时仍会：
   - 把该目标加入 `validDamageTargets`；
   - 设置 `anyMatchedDamage=true`；
   - 可能得到 `DAMAGE_AMOUNT_OBSERVED_POSITIVE`。

这违反 H-01：

- `validDamageTargets` 应为当前有效目标；
- canceled 链不得继续产生有效伤害结论；
- 取消事实不得被 historical damage 绕过。

现有 `cancelAfterValid` 测试只验证 `validAttackTargets`，没有先产生 damage，因此没有捕获该路径。

等级：**HIGH / blocking**。

### 其他 H-01 检查

- CANCELED 不会回到 VALID；
- OVERLAP + cancel -> CANCELED；
- 其他目标独立；
- sealed Context 不被迟到事件修改；
- `attackEntityCount` 不回退；
- 历史事件未删除。

但上述 `validDamage` 路径使 H-01 不能判定 PASS。

---

## 5. H-02：服务器时钟与 TTL 审查

### 结论：CONDITIONAL PASS

### 已验证

- Tracker 维护：

```text
long serverTick
long lastRawServerTick
long lastServerTickAtStart
boolean clockStarted
```

- `CombatAttackSession.serverTick`、`CombatAttackContext.serverTick`、`sealedTick` 为 long。
- `CombatBridge.onServerTick` 只处理 `TickEvent.Phase.END`：
  - `CombatBridge.java:173-179`
- Tracker `advanceRaw` 使用：

```java
long delta = (raw - lastRawServerTick) & 0xFFFFFFFFL;
```

- 同一 raw tick -> delta=0 -> 不递增；
- 正常前进 -> `serverTick += delta`；
- delta > 200 -> 清空 active/recent；
- raw 回退 -> 同样清空；
- ServerStarted 初始化；
- ServerStopping 清理；
- recent TTL：

```text
age = serverTick - sealedTick
age <= 40 有效
age > 40 过期
```

- `onServerTick` 每 tick 对 recent 执行 `removeIf`；
- active Session 使用同一个 `serverTick`；
- 没有 `Level#getGameTime()` 或系统时间。

### 未验证/风险

- 真实 Forge `ServerTickEvent` START/END 集成没有测试；只通过 Tracker test seam 验证。
- 没有真实玩家的 recent TTL 生命周期测试。
- 没有验证实际服务器 tick 回退/重启时的 active/recent 完整清理。
- `lastServerTickAtStart` 当前没有读取，只是记录；LOW。
- `CombatAttackTracker.recentTargets` 最大 512，但真实 recent TTL 没有 Integration 测试。

### H-02 结论

源码路径基本符合设计，测试不足。判定 **CONDITIONAL PASS**，不是无条件 PASS。

---

## 6. 六个计数器审查

### 结论：CONDITIONAL PASS

已确认：

- 六个字段均为 long：
  - `CombatTargetRegistry.java:17-22`
- `saturatingIncrement`：
  - `CombatTargetRegistry.java:118`
- `partTargetOverflowCount <= targetBudgetOverflowCount`：
  - 同一 admission 失败路径同步递增
- `overflowCanceledEventCount <= overflowEventCount`：
  - 同一 `recordOverflowEvent` 路径
- `eventDetailOverflowCount` 与目标预算独立
- `eventDetailTruncatedTargetCount` 由 `incrementTruncatedTargetCount` 每次目标首次截断增加
- 不保存溢出 UUID

测试不足：

- 只测了 target/part subset 和 `Long.MAX_VALUE` 饱和；
- 没有测试 overflowEvent/overflowCanceled 的独立触发；
- 没有测试 eventDetailOverflow 与 eventDetailTruncatedTarget 的区分；
- 没有测试所有六个计数器的单位和完整子集关系。

结论：源码实现合理，但测试覆盖不足，只能是 **CONDITIONAL PASS**。

---

## 7. 测试覆盖审查

### 结果

```text
108 tests / 0 failures
CombatGate28RegressionTest: 8 tests
CombatAttackSessionRemediationTest: 24 tests
```

### 真实覆盖

`CombatGate28RegressionTest` 覆盖：

- 65 个 PartEntity 共享预算；
- 取消后 valid 清空（无 prior damage）；
- OVERLAP 后取消；
- OVERLAP 不产生 validDamage；
- Registry 65th target / part subset；
- clock duplicate/unsigned/regression；
- 计数器饱和。

### 未覆盖 / 覆盖不足

- 65 个普通目标的独立测试（现有依赖 100 项中的旧测试）；
- 普通实体与 PartEntity 混合 64 的完整边界；
- 重复 PartEntity UUID；
- 同一 parent 多个 PartEntity；
- parent 不可得；
- 容量满后重复已有目标；
- **取消前已有 matched damage 后取消**（H09-H01）；
- late PartEntity coverage；
- ServerTickEvent START/END 真实事件；
- TTL 第 40/41 tick 的真实 recent Context；
- 六个计数器完整单位/子集；
- 真实 PartEntity / getEntityOrPart；
- 真实 Forge Tracker/Bridge 生命周期；
- 多人、死亡、退出、维度、停服。

测试仍使用 Session/Registry test seam，不能替代真实 Forge 事件或 PartEntity 测试。

---

## 8. 问题清单

### HIGH

#### H09-H01（blocking）

- 文件：`CombatAttackSession.java`
- 方法：`finish`
- 位置：`396-409`
- 取消前 matched damage 仍进入 validDamageTargets/positive result，未检查当前 chainState。
- 影响：H-01 失败；canceled target 仍可能产生有效伤害结论。

### MEDIUM

#### C09-M01

- 文件：`CombatAttackSession.java`
- 方法：`observeAttackTarget`
- 位置：`209-225`
- late PartEntity 被忽略但不降低 coverage；coverage 可能保持 COMPLETE。
- 影响：coverage 语义与 late 观察不一致；result 仍 UNKNOWN，不会产生 no-attack。

#### T09-M01

- 测试文件：`CombatGate28RegressionTest.java`
- 缺少 H-01 prior-damage-then-cancel、真实 TTL、六计数器完整覆盖。
- 影响：108 通过不能证明 H-01/H-02 全部行为。

### LOW

#### L09-L01

- `CombatAttackTracker.lastServerTickAtStart` 只赋值未读取。
- 位置：`CombatAttackTracker.java:33` 等。
- 影响：维护噪声。

#### L09-L02

- `CombatTargetRegistry.recordTruncatedTarget` 未使用。
- 位置：`CombatTargetRegistry.java:75` 左右。
- 影响：死代码。

#### L09-L03

- Context 仍有 legacy 字段与新的 valid/canceled 字段并存。
- 位置：`CombatAttackContext.java`。
- 影响：下游可能误读旧字段。

---

## 9. 已验证与未验证

### 已验证

- Registry 64 canonical target 容量；
- 第 65 个目标不创建 TargetState；
- Context 目标快照 <=64；
- CANCELED 状态不能由普通后续事件恢复 VALID；
- OVERLAP 后取消 -> CANCELED；
- long serverTick，重复 raw 不递增，unsigned wrap/regression 算法；
- TTL 常量与 recent 清理代码路径；
- 六个计数器 long/saturation 实现；
- 108 项单元测试通过；
- Handler `orderingKnown=false` 未改变。

### 未验证

- 真实 Forge 事件链；
- 真实 PartEntity/getEntityOrPart；
- 真实 recent TTL 生命周期；
- 真实 ServerTickEvent START/END；
- 多人、死亡、退出、维度、停服；
- 取消前 damage 后取消的 H-01 路径；
- 六计数器所有实际触发路径。

---

## 10. 是否与 Gate 2.7.1 设计不一致

是，存在 **H-01 不一致**：

- 设计：`validDamageTargets` 应为当前有效未取消的目标；
- 实现：只检查事件自身取消状态和 association，不检查当前 `chainState`；
- 取消前的 matched damage 仍可进入 valid/positive。

C-01 容量设计基本一致；H-02 时钟/TTL 源码基本一致，但测试不足。

---

## 11. 最终结论

# `GATE_2_8_REJECTED`

原因：

- **HIGH / blocking**：H-01 的 `validDamageTargets` 和 positive result 未排除已取消链；
- 108 项测试没有覆盖“先 damage、后 cancel”的关键路径；
- C-01 late PartEntity coverage 有 MEDIUM 语义问题；
- H-02 和计数器只有源码级/有限测试，缺少真实集成验证。

因此不能接受 Gate 2.8 为完成状态，也不能进入 Phase 05。

本次审查未修改任何源码、测试、Gradle、依赖或第三方 JAR，未创建 Git commit。
