# PHASE04_GATE2_10_REMEDIATION_DESIGN.md

> Phase 04 Gate 2.10 — Gate 2.9 独立审查后的修复设计  
> 2.10.1 修订：C09-M01 关联规则、Tracker 迟到计数、`PARTIAL_LATE_EVENT` 语义  
> 依据：`docs/PHASE04_GATE2_9_INDEPENDENT_AUDIT.md`  
> 本文件只定义设计与测试；不修改 Java、测试、Gradle、依赖或第三方 JAR。  
> 当前基线：108 项测试 PASSED；以下新设计均未实现。

---

## 0. Gate 2.10.1 修订摘要

本修订推翻原 2.10 中“late 事件降低当前 active Context coverage”的设计。

原因：

- Forge 事件没有 Better Combat attackId；
- 仅凭 active Session、target 相同或 recent sealed 目标匹配，不能证明 late 事件属于当前攻击；
- 把 late 事件归入 B 会污染 B；
- 修改 A 的 sealed Context 不可能；
- 因此 late 事件只进入 **Tracker 级诊断计数**，不修改任何 Context。

本修订保留 H09-H01 修复设计不变。

---

## 1. 当前源码逻辑分析

### H09-H01

`CombatAttackSession.finish` 的伤害汇总只检查：

```java
if (!event.canceledAtObservation()
        && event.association() == MATCHED_UNCANCELLED_CHAIN) {
    anyMatchedDamage = true;
    anyPositive |= event.positiveAtObservation();
    validDamage.add(state.targetUuid());
}
```

没有检查目标最终：

```java
state.chainState() == CombatTargetState.ChainState.VALID
```

因此：

```text
AttackEntity(A)
-> LivingDamage(A, positive)
-> canceled AttackEntity(A)
-> finish
```

仍可能得到 `validDamageTargets` / `DAMAGE_AMOUNT_OBSERVED_POSITIVE`。

### C09-M01

原实现 `CombatAttackSession.observeAttackTarget` 在 recent sealed 目标早退：

```java
if (recentSealedTargets.contains(targetUuid)
        || (parentUuid != null && recentSealedTargets.contains(parentUuid))) {
    lateEventCount++;
    return;
}
```

早退发生在 PartEntity coverage 更新前，导致当前 active Context 可能 COMPLETE。

2.10.1 的新结论：

- 无法可靠判断 late 事件属于 A 还是 B；
- 因此不修改 B 的 coverage；
- 也不修改 A 的 sealed Context；
- 改为 Tracker 诊断计数。

---

## 2. H09-H01 修复设计（不变）

### 2.1 原则

- 保留历史事件；
- 最终有效伤害必须基于目标最终 chainState；
- 最终 CANCELED/OVERLAP 目标不得进入 `validDamageTargets`；
- 不得贡献 `anyMatchedDamage` / `anyPositive`；
- 不影响其他目标；
- 不修改伤害结算。

### 2.2 修复位置

```text
src/main/java/com/MrGhost/parkourCombat/combat/CombatAttackSession.java
```

方法：

```java
finish(CombatAttackEndReason endReason, long finishTick)
```

### 2.3 修复逻辑

在事件汇总处增加：

```java
boolean targetCurrentValid =
        state.chainState() == CombatTargetState.ChainState.VALID;
```

仅当：

```java
targetCurrentValid
&& !event.canceledAtObservation()
&& event.association() == MATCHED_UNCANCELLED_CHAIN
```

才允许：

```text
anyMatchedDamage = true
anyPositive |= event.positiveAtObservation()
validDamage.add(state.targetUuid())
```

历史事件仍保留在：

```text
state.events()
CombatAttackContext.targetEventObservations
```

### 2.4 结果语义

| 场景 | chainState | validDamage | result |
|---|---|---|---|
| 有效 AttackEntity + positive damage | VALID | 包含 | DAMAGE_AMOUNT_OBSERVED_POSITIVE |
| 有效 damage 后 cancel | CANCELED | 不含 | TARGET_OBSERVED |
| 零 damage 后 cancel | CANCELED | 不含 | TARGET_OBSERVED |
| 多 damage 后 cancel | CANCELED | 不含 | TARGET_OBSERVED |
| A cancel，B 有效 positive | A CANCELED；B VALID | 仅 B | B 可 positive；A 不 |
| CANCELED 后 uncanceled AttackEntity | CANCELED | 不含 | TARGET_OBSERVED |
| sealed 后迟到 | 不修改 | 不修改 | 不修改 |

`TARGET_OBSERVED` 不代表实际生命值减少。

---

## 3. C09-M01 修复设计（2.10.1）

### 3.1 迟到事件归属规则

场景：

```text
Attack A
-> Context A sealed
-> Attack B starts
-> Late PartEntity event belonging to A arrives
```

可靠关联依据不存在：

- Forge 事件没有 Better Combat attackId；
- active Session B 存在不等于事件属于 B；
- target UUID 在 recent sealed 目标中不等于事件属于 A；
- target 与 B 的请求集合重叠也不能证明属于 B。

因此：

1. 迟到事件不得仅因 active Session 存在而归入 B；
2. 迟到事件不得修改 B 的 coverage/validDamage/attack chain/result；
3. 迟到事件不得修改 A 的 sealed Context；
4. 迟到事件不得重新打开 A 或创建 Context；
5. 迟到事件只进入 **Tracker 诊断计数**；
6. 不保存迟到目标 UUID。

### 3.2 判断与入口

`CombatAttackTracker` 在把事件交给 active Session 前检查：

```text
target UUID 或 parent UUID 是否在 recent sealed target lookup 中？
```

是：

- 视为 `AMBIGUOUS_LATE_EVENT`；
- 增加 Tracker late 计数；
- 不调用 active Session 的 observe 方法；
- 不修改任何 Context。

否：

- 保持现有行为，交给当前 active Session 关联。

即使 target 同时出现在 B 的请求/攻击链中，也按 late 诊断处理；宁可丢失观察，不污染 B。

### 3.3 无 active Session

无 active Session：

- Tracker 直接增加 late 计数；
- 不创建 Session/Context；
- 不保存 UUID；
- 不进入任何目标状态。

### 3.4 Tracker 迟到计数

`CombatAttackTracker`：

```text
long lateEventCount
long latePartEventCount
long lateCanceledEventCount
```

规则：

- 每次迟到事件计数一次；
- 重复迟到事件重复计数；
- PartEntity 同时增加 event + part；
- canceled 同时增加 canceled；
- `latePartEventCount <= lateEventCount`；
- `lateCanceledEventCount <= lateEventCount`；
- `saturatingIncrement`；
- `Long.MAX_VALUE` 后保持，不回绕、不变负；
- ServerStarted/ServerStopping 重置为 0；
- 不保存 UUID；
- 不进入 Context。

`latePartObserved` 不再保留；由：

```text
latePartEventCount > 0
```

派生。

### 3.5 `PARTIAL_LATE_EVENT` 语义

`PARTIAL_LATE_EVENT` 只表示：

```text
当前 Context 存在已可靠关联的迟到观察，无法保证完整观察范围。
```

当前 Forge 事件没有可靠 attackId，因此：

- 当前实现不产生 `PARTIAL_LATE_EVENT`；
- 迟到事件只进入 Tracker 诊断；
- 不把 B 降级；
- 不把 A 重新标记；
- 未来若有可靠 attackId，再允许对相应 Context 使用。

本次不新增/生产 `PARTIAL_LATE_EVENT` 枚举值，除非后续可靠关联机制获批。

### 3.6 为什么不是把 B 降级

如果无法证明 late 事件属于 B：

- 降级 B 会错误影响独立攻击上下文；
- 不降级 B 但记录 Tracker 诊断，保留“存在无法归属迟到事件”的信息；
- A 已 sealed，不能修改；
- 这比伪造归属更安全。

因此：

- B 的 coverage 保持其自身请求的解析结果；
- B 的 result 不被 late A 事件改变；
- late 信息进入独立诊断；
- COMPLETE 只表示 B 的请求窗口自身可解析，不表示服务器没有迟到事件。

### 3.7 sealed 不可变性

- A 的 Context 是 record 快照；
- finish 后 sealed；
- Tracker 不调用 A；
- 不重新打开；
- 不修改 A 的 targetEventObservations/validDamage/coverage；
- late 计数不进入 A。

### 3.8 预算与容量

late 事件在 Tracker 层计数：

- 不调用 TargetRegistry；
- 不占 64 目标预算；
- 不增加 recentTargets/context 目标集合；
- 不保存 UUID；
- 不增加无界集合。

### 3.9 结果矩阵

| 场景 | B/当前 Context | A sealed | Tracker late | 新 UUID | 预算 |
|---|---|---|---|---|---|
| 正常 B，无迟到 | 原结果/coverage | 不变 | 0 | 无 | 不变 |
| B active，late A Part | B 不变 | 不变 | +1；part+1 | 不保存 | 不变 |
| 无 active Session | 不创建 | 不变 | +1 | 不保存 | 不变 |
| late A 与 B 同 target | B 不变 | 不变 | +1 | 不保存 | 不变 |
| 已知 Part 重复 | B 不变 | 不变 | 每次 +1 | 不保存 | 不变 |
| parent 已知、part 未知 | B 不变 | 不变 | +1；part+1 | 不保存 | 不变 |
| parent 不可得 | B 不变 | 不变 | +1；part+1 | 不保存 | 不变 |
| 64 目标后 late Part | B 不变 | 不变 | +1 | 不保存 | 不突破 64 |

---

## 4. 六个目标/事件计数器与 late 计数

### 4.1 六个计数器

| 计数器 | 类型 | 单位 | 触发 |
|---|---|---|---|
| `targetBudgetOverflowCount` | long | 被拒绝的新 canonical target 准入尝试次数 | Registry 满 + 新 UUID |
| `partTargetOverflowCount` | long | 被拒绝的新 PartEntity 准入尝试次数 | 同上且 PART |
| `overflowEventCount` | long | 未准入目标上的事件数 | 未准入 target 收到事件 |
| `overflowCanceledEventCount` | long | 未准入 target 上的 canceled 事件数 | 同上且 canceled |
| `eventDetailOverflowCount` | long | 已准入目标第 9 条及以后事件数 | TargetState 明细溢出 |
| `eventDetailTruncatedTargetCount` | long | 发生明细截断的 TargetState 数 | 每目标第一次 truncated |

### 4.2 Tracker late 计数

```text
lateEventCount
latePartEventCount
lateCanceledEventCount
```

与六个 target/event 计数器分开，不混用：

- late 不是 64 预算溢出；
- late 不增加 targetEventObservations；
- late 不增加 context 目标集合；
- late 计数器不保存 UUID。

### 4.3 测试矩阵

| ID | 触发方式 | 预期 |
|---|---|---|
| CNT-T01 | 64 后第 65 普通目标 | target=1；part=0 |
| CNT-T02 | 64 后第 65 Part | target=1；part=1 |
| CNT-T03 | 未准入目标普通事件 | overflowEvent=1；canceled=0 |
| CNT-T04 | 未准入目标 canceled 事件 | overflowEvent=1；canceled=1 |
| CNT-T05 | 同一目标 9 条事件 | detailOverflow=1；truncatedTarget=1 |
| CNT-T06 | 两个目标各 9 条 | detailOverflow=2；truncatedTarget=2 |
| CNT-T07 | 已准入目标重复事件 | target 不增加 |
| CNT-T08 | 重复未准入目标事件 | 按尝试计数；不保存 UUID |
| CNT-T09 | 全部子集 | part<=target；overflowCanceled<=overflowEvent |
| CNT-T10 | `saturatingIncrement(Long.MAX_VALUE)` | 保持 MAX，不变负 |
| CNT-T11 | late 普通事件 | lateEvent+1；part 不变 |
| CNT-T12 | late Part 事件 | lateEvent+1；part+1 |
| CNT-T13 | late canceled 事件 | event+1；canceled+1 |
| CNT-T14 | late 重复事件 | 每次 +1；不保存 UUID |
| CNT-T15 | late 无 active Session | Tracker +1；不创建 Context |

所有测试必须通过真实 admission/event/Tracker 路径触发，不能直接设置字段；饱和测试通过 package-private 纯函数验证。

---

## 5. 新增测试设计

> 全部 `DESIGNED`；当前未实现、未执行。现有 108 项测试为 PASSED 基线。

### 5.1 H09-H01

| ID | 场景 | 预期 |
|---|---|---|
| H09-T01 | AttackEntity(A) -> LivingDamage(A,>0) -> canceled AttackEntity(A) | 历史两条存在；A CANCELED；validDamage 不含 A；result=TARGET_OBSERVED |
| H09-T02 | 取消前零伤害 | TARGET_OBSERVED；validDamage 不含 A |
| H09-T03 | 取消前多个伤害 | 历史事件全部保留；validDamage 不含 A |
| H09-T04 | A 取消；B 有效 positive | A 不 valid；B valid；B 可 positive |
| H09-T05 | 取消后 uncanceled AttackEntity | A 仍 CANCELED |
| H09-T06 | sealed Context 迟到事件 | 旧 Context 快照不变 |

### 5.2 C09-M01（2.10.1 修正）

| ID | 场景 | 预期 |
|---|---|---|
| C09-T01 | B active，late A Part | B coverage/result 不变；Tracker lateEvent+1；part+1 |
| C09-T02 | 无 active Session，late Part | Tracker+1；不创建 Context/UUID |
| C09-T03 | late A 与 B 同 target | B 不变；Tracker+1；不重开 A |
| C09-T04 | 已知 Part 重复 | 每次 Tracker+1；不保存 UUID |
| C09-T05 | parent 已知、part 未知 | Tracker+1；part+1；不占预算 |
| C09-T06 | parent 不可得 | Tracker+1；part+1；不伪造 parent |
| C09-T07 | 64 目标后 late Part | targets<=64；Tracker+1；B 不变 |
| C09-T08 | 迟到不重开/不改历史 | A/B 快照和 chain 不变 |
| C09-T09 | late 计数子集 | part<=lateEvent；canceled<=lateEvent |
| C09-T10 | late 饱和 | `Long.MAX_VALUE` 保持 |

### 5.3 既有测试影响

- `recentSealedTargetIsIgnoredByNewSession` 当前断言 Session lateEventCount；2.10.1 后 Tracker 拦截，Session 不再看到该事件，需改为 Tracker 测试。
- 原 108 项不得删除/跳过；若断言与新设计冲突，应更新断言但仍验证安全行为。
- H09 修复可能改变“先 damage 后 cancel”的最终 result，应更新/新增测试。

---

## 6. 预计修改文件

### 修改

```text
CombatAttackSession.java    // H09 filter only
CombatAttackTracker.java    // late diagnostic counters + interception
```

### 可能修改

```text
CombatAttackContext.java    // 若删除/保留 legacy late fields
CombatTargetCoverage.java   // 2.10.1 不新增 PARTIAL_LATE_EVENT
```

### 新增测试

```text
CombatGate210RegressionTest.java
```

### 需要更新测试

```text
CombatGate28RegressionTest.java
CombatAttackSessionRemediationTest.java
```

### 不修改

```text
CombatTargetRegistry.java / CombatTargetState.java
BetterCombatCompat.java / BetterCombatAttackAdapter.java
Gradle / 依赖 / 第三方 JAR
Mixin / 网络包
```

---

## 7. 副作用与风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| H09 过滤过宽导致历史丢失 | HIGH | 只过滤 valid/positive 汇总，不删事件 |
| late 事件错误归入 B | HIGH | Tracker 层拦截，不调用 Session |
| 丢失 late 观察 | MEDIUM | Tracker 计数保留诊断 |
| late 计数溢出 | LOW | long + saturation，无 UUID |
| 现有测试断言 lateEventCount | MEDIUM | 更新为 Tracker 断言 |
| 测试 seam 被误当真实 Forge | MEDIUM | 明确 DESIGNED/集成边界 |

---

## 8. 状态区分

```text
DESIGNED：本文件全部设计
IMPLEMENTED：无（本阶段禁止改代码）
COMPILED：否（本阶段不编译新代码）
TESTED：仅既有 108 项 PASSED 基线
CLIENT-TESTED：非本阶段
SERVER-TESTED：非本阶段
MULTIPLAYER-TESTED：非本阶段
```

未执行任何真实 Forge、PartEntity、多人或停服测试。

---

## 9. 最终状态

```text
READY_FOR_CHATGPT_REVIEW
```

本阶段只更新设计报告；未修改源码、测试、Gradle、依赖或第三方 JAR；未创建 Git commit；未进入 Phase 05。
