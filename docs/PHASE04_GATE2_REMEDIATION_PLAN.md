# PHASE04_GATE2_REMEDIATION_PLAN.md

> Phase 04 Gate 2.4 — 修复方案最终设计审查  
> 依据：`PHASE04_GATE2_POST_IMPLEMENTATION_AUDIT.md`  
> 本文件只定义修复设计，不修改任何 Java 源码、测试、Gradle 配置、依赖或第三方 JAR。  
> 当前实现：`CONDITIONALLY_READY`  
> 本方案：`READY_FOR_CHATGPT_REVIEW`

---

## 1. 基线

```text
项目：D:\MinecraftParcoolCombat
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

未跟踪文件包括 Phase 04 计划、报告、审计、源码、测试和日志。

锁定依赖 JAR：

```text
bettercombat-forge-1.9.0+1.20.1.jar
size: 1,054,339
sha256: 49C69DCCF2C641246B4E3DECA81EBAC21F0EE119E81FC713B838B8E33569EEA1

ParCool-1.20.1-3.4.3.3.jar
size: 1,154,052
sha256: 2DDEF8752B5A1428035B95F29E62C81E33B80D37851681E1B928D8A01615C6E7

player-animation-lib-forge-1.0.2-rc1+1.20.jar
size: 181,437
sha256: 90D9965CB9EFDBDA29FDC5610BE3914CF7008BF5C392FF34F7AB25F96A852691

cloth-config-11.1.136-forge.jar
size: 1,181,413
sha256: 1E895E85CF5B1E1905EF3178EC155C8BADFE22A1577B92C09143A5AA1F4CE0F2
```

---

## 2. 当前状态与修复分级

| 项目 | 当前 | 本次计划 |
|---|---|---|
| Better Combat C2S Handler 桥接 | 已实现 | 保持 |
| 有界请求解析 | 已实现 | 保持 |
| raw ID → UUID / PartEntity 解析 | 已实现 | 修补 coverage |
| Session/Tracker/清理 | 已实现 | 修补归属与上限 |
| 事件观察 | 已实现 | 修补关联与来源分类 |
| 独立目标上限 | 未实现 | 计划新增 |
| Session/Tracker/Bridge 事件测试 | 缺失 | 计划新增 |
| Handler audited identity | 不可达 | 保持 UNKNOWN；不得伪造 |
| 服务端空间验证 | 无 | 不在本阶段 |
| 实际生命值减少 | 无 | 不在本阶段 |

---

## 3. C1：PartEntity 与 parent coverage

### 3.1 根因

当前 `CombatAttackSession.observeAttackTarget` 只在 `partTargetUuids` 非空时降低 coverage：

```text
CombatAttackSession.java:164-185
```

但请求目标是 parent、事件目标是 PartEntity 时：

```text
CombatAttackSession.java:225-247
```

`matchTarget` 返回 `PARENT_MATCH`，`partTargetUuids` 仍为空，因此：

- `targetCoverage` 可能保持 `COMPLETE`；
- `requestedTargetsComplete` 可能保持 true；
- PartEntity 事件被错误当成普通完整覆盖。

### 3.2 修复设计

拆分请求与观察数据：

```text
requestedPartUuids
requestedPartParentUuids
observedPartUuids
observedPartParentUuids
observedPartUnmatched
```

迁移：

```text
partTargetUuids -> requestedPartUuids
partParentUuids -> requestedPartParentUuids
```

规则：

1. 实际事件 target 是 PartEntity：
   - 记录 `observedPartUuids`；
   - 若 `part.getParent() != null`，记录 `observedPartParentUuids`；
   - 无论 parent 是否匹配，coverage 至少 `PARTIAL_PART`；
   - `requestedTargetsComplete=false`。
2. 请求 parent、事件 part：
   - 可以记 `PARENT_MATCH`，但仍必须记录 observed part；
   - 不得把 part 事件当作普通实体完整覆盖。
3. 请求 part、事件 part：
   - `PART_MATCH`；
   - `PARTIAL_PART`。
4. 请求 part、伤害事件 parent：
   - 通过 observed/requested part-parent 关系关联；
   - 不把 part UUID 改写成 parent UUID。
5. parent 不可得：
   - `observedPartUnmatched=true`；
   - `PART_TARGET_UNMATCHED`；
   - 不伪造 UUID；
   - coverage `PARTIAL_PART`。
6. finish 时基于 requested + observed 状态重新计算 coverage，不能只依赖 prepare。
7. `requestedTargetsComplete` 仅当无 requested/observed part、无截断、无未解析、全部普通存活目标。

### 3.3 禁止

- 把 PartEntity UUID 等同 parent UUID；
- 因 `PARENT_MATCH` 升级为普通完整目标；
- parent 不可得时猜测 UUID；
- 用 part 事件维持 `COMPLETE`。

### 3.4 验收

- 请求 parent + 事件 part：`PARTIAL_PART`、complete=false。
- 请求 part + 事件 parent：part-parent 明确，coverage partial。
- part 无 parent：`PART_TARGET_UNMATCHED`，coverage partial。
- part 与普通目标混合：coverage partial，normal 观察保留，但不称完整。

---

## 4. C2：DamageSource 分类统一规则

### 4.1 根因

当前桥接层对所有伤害事件传入：

```java
PRIMARY_PLAYER_ATTACK
```

证据：

```text
CombatBridge.java:205-235
```

仅凭：

```text
directEntity == player
getEntity() == player
source.is(DamageTypes.PLAYER_ATTACK)
```

不能证明：

- 伤害属于当前 Better Combat 攻击；
- Better Combat 是独占来源；
- 其他 Mod 没有使用相同 source 追加伤害。

### 4.2 四个独立结论

不得让一个枚举同时承担来源、关联、会话归属和来源证明。

```java
enum CombatDamageSourceKind {
    DIRECT_PLAYER_MELEE,
    OTHER
}

enum CombatDamageSourceClassification {
    AMBIGUOUS_PLAYER_ATTACK,
    OTHER
}

enum CombatDamageAssociation {
    MATCHED_UNCANCELLED_CHAIN,
    ATTACK_CANCELED,
    NO_ATTACK_EVENT,
    COLLATERAL,
    LATE_AFTER_SEALED,
    OVERLAP_AMBIGUOUS,
    UNKNOWN
}

boolean betterCombatExclusiveProven; // 当前永远 false
```

`PRIMARY_PLAYER_ATTACK` 不再作为生产分类。  
`AMBIGUOUS_PLAYER_ATTACK` 是直接玩家近战来源的保守分类。

四个结论：

1. **观察到玩家攻击事件**：见 `AttackEntityEvent`。
2. **观察到匹配伤害事件**：`MATCHED_UNCANCELLED_CHAIN`。
3. **与当前请求有较强关联**：匹配链 + Handler 顺序已知 + 无 overlap + coverage 完整。
4. **证明由 Better Combat 独占产生**：**当前不可证明**。

所有直接玩家近战：

```text
CombatDamageSourceKind.DIRECT_PLAYER_MELEE
CombatDamageSourceClassification.AMBIGUOUS_PLAYER_ATTACK
betterCombatExclusiveProven = false
```

### 4.3 关联规则

- 有未取消 AttackEntityEvent、目标/part-parent 确定、窗口内、无 overlap：
  -> `MATCHED_UNCANCELLED_CHAIN`。
- AttackEntityEvent 已取消：
  -> `ATTACK_CANCELED`。
- 无匹配 AttackEntity：
  -> `NO_ATTACK_EVENT`。
- 目标不在请求/观察链：
  -> `COLLATERAL`。
- sealed 后：
  -> `LATE_AFTER_SEALED`，只诊断。
- 新旧 Session 目标重叠：
  -> `OVERLAP_AMBIGUOUS`。

即使关联为 `MATCHED_UNCANCELLED_CHAIN`：

```text
CombatDamageSourceClassification = AMBIGUOUS_PLAYER_ATTACK
betterCombatExclusiveProven = false
```

### 4.4 其他 Mod 同 source

- 无法绝对区分；
- 不升级为 Better Combat 独占；
- 同目标交错 -> `OVERLAP_AMBIGUOUS`；
- 无 AttackEntity -> `NO_ATTACK_EVENT`；
- 第一条匹配链也不代表独占；
- 不得用 amount 做 Phase 05 倍率。

### 4.5 结果影响

`DAMAGE_EVENT_OBSERVED` / `DAMAGE_AMOUNT_OBSERVED_POSITIVE` 是**事件观察结果**，不等于：

- Better Combat 独占；
- 最终伤害；
- 实际生命值减少。

规则：

- `MATCHED_UNCANCELLED_CHAIN` 的 Hurt/Damage -> `DAMAGE_EVENT_OBSERVED`；
- 有 positive 观察 -> `DAMAGE_AMOUNT_OBSERVED_POSITIVE`；
- 不升级 `betterCombatExclusiveProven`；
- `NO_ATTACK_EVENT` / `OVERLAP_AMBIGUOUS` / `ATTACK_CANCELED` 不升级 positive；
- 无 AttackEntity -> `TARGET_OBSERVED` 或 `UNKNOWN`；
- Phase 05 不得把 amount 当最终伤害。

### 4.6 验收

- 所有直接玩家近战保持 `AMBIGUOUS_PLAYER_ATTACK`；
- `betterCombatExclusiveProven` 永远 false；
- 其他 Mod 同 source 不升级独占；
- 第一条链不被当作独占证明；
- 无 AttackEntity 不成为主链。

---

## 5. C3：严格事件关联与取消规则

### 5.1 正常顺序

```text
C2S AttackRequest
  -> server task
  -> Player.attack(target)
  -> AttackEntityEvent(target)
  -> LivingEntity.hurt(target)
  -> LivingAttackEvent(target)
  -> LivingHurtEvent(target)
  -> LivingDamageEvent(target)
```

PartEntity：

```text
AttackEntityEvent target 可能是 PartEntity
LivingAttack/Hurt/Damage target 可能是 parent
```

### 5.1.1 同一目标可多次事件

同一目标可能出现多次：

- AttackEntityEvent（同玩家连续攻击、取消后再次攻击）；
- LivingAttackEvent（不同攻击/Mod 追加）；
- LivingHurtEvent（多段伤害/追加伤害）；
- LivingDamageEvent（多段结算）。

因此不能只用一个状态字段覆盖全部事件。  
每个目标使用有界事件列表，每次事件独立记录：

```text
eventType
eventOrdinal
observationTick
canceled
amountAtObservation
positiveAtObservation
association
sourceClassification
```

同一目标最多保留 8 条；超出只增加计数/truncated。  
取消记录不得被后续事件覆盖。

### 5.2 每个事件的独立状态

```java
enum CombatEventState {
    NOT_OBSERVED,
    OBSERVED,
    CANCELED,
    WINDOW_INCOMPLETE,
    ASSOCIATION_UNCERTAIN
}
```

分别记录：

```text
attackEntityState
livingAttackState
livingHurtState
livingDamageState
```

`NOT_OBSERVED` 不是 MISS，也不是攻击失败。

### 5.3 AttackEntityEvent 取消

观察到未取消：

```text
attackEntityState = OBSERVED
chain.valid = true
```

观察到已取消：

```text
attackEntityState = CANCELED
chain.valid = false
```

规则：

- 取消的 AttackEntityEvent 不得创建可用于主链关联的有效链；
- 可保留 `canceledAttackTargets` 诊断；
- 后续无伤害 -> target observed + canceled；
- 后续其他 Mod 同 source 伤害：
  - association=`ATTACK_CANCELED`；
  - source classification=`AMBIGUOUS_PLAYER_ATTACK`；
  - 不 positive；
  - 不重新激活取消攻击；
- 未观察 -> `NOT_OBSERVED`，不推断 MISS/拒绝。

### 5.4 关联条件

session 维护：

```text
attackChainsByTarget
observedPartToParent
observedAttackTargets
canceledAttackTargets
```

`AttackEntityEvent`：

- 普通 target -> valid chain；
- PartEntity -> PART_CHAIN + observed part/parent；
- canceled -> CANCELED，不进入有效 observedAttackTargets。

`LivingAttack/Hurt/Damage`：

1. 找 exact target 的有效 chain；
2. 找不到时找 observed part 的 parent chain；
3. 找不到 -> `NO_ATTACK_EVENT`/`COLLATERAL`；
4. 找到但 canceled -> `ATTACK_CANCELED`；
5. 找到且有效 -> `MATCHED_UNCANCELLED_CHAIN`；
6. 事件取消 -> `CANCELED`，不 positive；
7. sealed 后 -> `LATE_AFTER_SEALED`，只诊断；
8. 新旧 session 重叠 -> `OVERLAP_AMBIGUOUS`。

### 5.5 无 AttackEntity 的伤害

- 不建立有效链；
- association=`NO_ATTACK_EVENT`；
- 可保存诊断；
- 不参与 positive；
- 无 AttackEntity 时 result 不能是 `NO_ATTACK`；应为 `UNKNOWN` 或 `TARGET_OBSERVED`（无主伤害）。

### 5.6 横扫次级目标

- 无对应 AttackEntity；
- 即使 target 在 requested 集合，也只能 `NO_ATTACK_EVENT`/`COLLATERAL`；
- 不并入主目标；
- 不产生新 attackId；
- 不参与 positive。

### 5.7 取消/零伤害/无敌帧

| 情况 | 状态 | 结果 |
|---|---|---|
| AttackEntity 取消 | CANCELED | 无效链 |
| LivingAttack 取消 | CANCELED | 不 positive |
| LivingHurt 取消 | CANCELED | 不 positive |
| LivingDamage 取消 | CANCELED | 不 positive |
| LivingDamage amount 0 | OBSERVED | positive=false |
| 无敌帧 | 可能只有 Attack/LivingAttack | 不推断生命减少 |
| 无后续事件 | NOT_OBSERVED | 不推断 MISS |

结果只能是 `TARGET_OBSERVED` / `DAMAGE_EVENT_OBSERVED` / `UNKNOWN`，不能 HIT/HEALTH_REDUCED。

---

## 6. C4：Session 归属可信度

### 6.1 当前限制

Forge 事件没有 Better Combat attack ID。  
`CombatAttackTracker.java:83-104` 只把事件交给攻击者当前 active session。

### 6.2 保守归属

每个 session：

```text
sessionGeneration / attackId
attackChains
overlapRisk
```

新请求：

- 旧 active 标记 `INTERRUPTED_BY_NEW_REQUEST`；
- 旧 context finalize/sealed；
- 旧目标集合进入有界 recent（8）；
- 新 session 建立；
- 重叠目标 -> `overlapRisk=true`。

事件到达：

- target 在旧 sealed 且当前 requested：
  - association=`OVERLAP_AMBIGUOUS`；
  - 不升级主链。
- target 只在旧 sealed：
  - 忽略/诊断；
  - 不修改当前 context。
- target 只在新 session 且有匹配 AttackEntity：
  - 记录主链；
  - source classification 仍 `AMBIGUOUS_PLAYER_ATTACK`；
  - 不能证明 Better Combat 独占。
- 连续攻击不同目标：
  - 不同目标只减少关联冲突；
  - 不证明来源是 Better Combat；
  - 不得据此升级来源。
- 连续攻击相同目标：
  - `OVERLAP_AMBIGUOUS`；
  - 不升级 positive。
- 新 session 后旧事件迟到：
  - 旧 context sealed，不能修改；
  - 当前 session 只记 `LATE_AFTER_SEALED` 诊断；
  - 不重开；
  - 不扩大窗口。

### 6.3 无法彻底解决

- 无 attackId；
- 同目标/相邻 tick 两次攻击无法绝对区分；
- 不同目标也不能证明来源独占；
- 同 source 其他 Mod 无法绝对区分；
- 只能 `OVERLAP_AMBIGUOUS` / `AMBIGUOUS_PLAYER_ATTACK` 降级；
- sealed 后迟到事件只能诊断，不能修改旧/新 context。

Phase 05 不得把上述状态当确定 Better Combat 攻击。

---

## 7. C5：独立观察目标上限

### 7.1 根因

当前目标上限依赖 requestedTargets/partParentUuids，`CombatAttackSession.java:36-44` 没有独立检查 `damageObservations.size()`。

### 7.2 修复

```text
MAX_OBSERVATION_TARGETS = 64
MAX_OBSERVATIONS_PER_TARGET = 8
```

新 target 前：

```text
if (!damageObservations.containsKey(targetId)
    && damageObservations.size() >= 64) {
    damageTargetOverflowCount++;
    damageObservationTruncated = true;
    return;
}
```

记录：

```text
damageTargetOverflowCount
damageObservationTruncated
```

规则：

- 不为第 65 个目标创建 List/Map；
- 不加入主链/诊断 target sets；
- 已有目标仍每目标最多 8 条；
- 不累计金额；
- 不扩容。

---

## 8. 数据模型修订

### `CombatAttackSession`

新增：

```text
requestedPartUuids
requestedPartParentUuids
observedPartUuids
observedPartParentUuids
observedPartUnmatched
attackChains
observedAttackTargets
canceledAttackTargets
overlapRisk
damageTargetOverflowCount
attackEntityState
livingAttackState
livingHurtState
livingDamageState
```

迁移：

```text
partTargetUuids -> requestedPartUuids
partParentUuids -> requestedPartParentUuids
```

### `CombatAttackContext`

新增：

```text
observedPartUuids
observedPartParentUuids
observedPartUnmatched
damageTargetOverflowCount
attackEntityState
livingAttackState
livingHurtState
livingDamageState
CombatDamageSourceKind sourceKind
CombatDamageSourceClassification sourceClassification
CombatDamageAssociation damageAssociation
boolean betterCombatExclusiveProven
Map<UUID, CombatTargetEventObservation> targetEventObservations
int attackEntityEventCount
int livingAttackEventCount
int livingHurtEventCount
int livingDamageEventCount
int canceledEventCount
int eventObservationOverflowCount
```

保留：

```text
healthReductionVerified = false
```

### `CombatEventObservation`（单次事件）

```text
targetUuid
CombatEventType eventType
int eventOrdinal
int observationTick
boolean canceledAtObservation
Float amountAtObservation             // AttackEntityEvent 可为 null
boolean positiveAtObservation         // 仅伤害事件有意义
CombatDamageSourceKind sourceKind     // AttackEntity 可为 OTHER/UNKNOWN
CombatDamageSourceClassification sourceClassification
CombatDamageAssociation association
CombatEventState eventState
boolean betterCombatExclusiveProven   // 永远 false
```

`CombatEventType`：

```text
ATTACK_ENTITY
LIVING_ATTACK
LIVING_HURT
LIVING_DAMAGE
```

### `CombatTargetEventObservation`（单目标有界记录）

```text
UUID targetUuid
List<CombatEventObservation> observations   // immutable, <=8
int totalEventCount                         // 包含被截断的事件
int attackEntityCount
int livingAttackCount
int livingHurtCount
int livingDamageCount
int canceledCount
boolean truncated
CombatEventAggregateState aggregateState
```

`CombatEventAggregateState`：

```text
NOT_OBSERVED
OBSERVED_VALID
OBSERVED_CANCELED
MIXED
TRUNCATED
ASSOCIATION_UNCERTAIN
UNKNOWN
```

规则：

- 同一目标可出现多次 AttackEntityEvent/LivingAttackEvent/LivingHurtEvent/LivingDamageEvent；
- 每次事件独立记录，不覆盖；
- 取消记录不能丢失；
- 每目标最多保存 8 条；
- 超过 8 条只增加 `totalEventCount`/`canceledCount` 等计数并设置 `truncated`；
- 计数无集合增长；
- 不合并、不求和、不跨目标累计；
- `amountAtObservation` 只是观察值，不是最终伤害；
- `aggregateState` 由记录和计数推导，不是替代单次事件状态。

### `CombatDamageSourceClassification`

新生产值：

```text
AMBIGUOUS_PLAYER_ATTACK
OTHER
```

`PRIMARY_PLAYER_ATTACK` 不再产生。  
所有直接玩家近战均为 `AMBIGUOUS_PLAYER_ATTACK`；`betterCombatExclusiveProven=false`。

### `CombatTargetCoverage`

```text
COMPLETE
PARTIAL_TRUNCATED
PARTIAL_UNRESOLVED
PARTIAL_PART
UNKNOWN
```

observed part/observedPartUnmatched -> `PARTIAL_PART`。

---

## 9. 测试矩阵

> 当前只有数据类/解码器测试通过。  
> 以下 Session/Tracker/Bridge 测试均为 `DESIGNED`，尚未实现。

### 9.1 基础测试

| ID | 输入 | 预期 |
|---|---|---|
| T-01 | AttackEntity(A) → LivingAttack(A) → LivingHurt(A) → LivingDamage(A,>0) | matched chain；`AMBIGUOUS_PLAYER_ATTACK`；positive 观察；Phase05 不可用于伤害 |
| T-02 | LivingHurt(A) 无 AttackEntity | `NO_ATTACK_EVENT`；不 positive；`TARGET_OBSERVED`/`UNKNOWN` |
| T-03 | AttackEntity(A,canceled) → 无伤害 | chain canceled；`TARGET_OBSERVED`；不 positive |
| T-04 | AttackEntity(A) → LivingAttack(A,canceled) | event canceled；不 positive |
| T-05 | AttackEntity(A) → LivingHurt(A,canceled) | event canceled；不 positive |
| T-06 | AttackEntity(A) → LivingDamage(A,canceled/0) | 不 positive |
| T-07 | AttackEntity(A) → LivingAttack(A) 无敌帧 | `TARGET_OBSERVED`；不推断生命减少 |
| T-08 | 请求 A 完成 → 新请求 B → A 晚到 | `LATE_AFTER_SEALED`；不修改旧/新 |
| T-09 | 连续请求同 A | `OVERLAP_AMBIGUOUS`；不升级 positive |
| T-10 | finish(A) 后事件 A | sealed；只诊断 |
| T-11 | AttackEntity(A) → 横扫 B 无 AttackEntity | B `COLLATERAL/NO_ATTACK_EVENT`；不并入 A |
| T-12 | AttackEntity(A) → 其他 Mod 同 source 伤害 A | `AMBIGUOUS_PLAYER_ATTACK`；不升级独占 |
| T-13 | 请求 parent P，AttackEntity(part C) → LivingHurt(P) | `PARTIAL_PART`；PARENT_MATCH；part 观察 |
| T-14 | 请求 A + 请求 part C，混合 | coverage partial；normal/part 分开 |
| T-15 | 65 个目标 | 第 65 个 overflow；不创建 entry |
| T-16 | 两玩家同时 | 按 UUID 隔离 |
| T-17 | 死亡/退出/维度 | 清理；sealed；不重开 |
| T-18 | 停服请求/调度 | skip/UNKNOWN；不 inline 访问 Tracker |

### 9.2 必补定向测试

| ID | 输入 | 预期 |
|---|---|---|
| T-19 | AttackEntity(A,canceled) → 无伤害 | `ATTACK_CANCELED`；无有效链 |
| T-20 | AttackEntity(A,canceled) → 其他 Mod 同 source 伤害 A | `ATTACK_CANCELED`；不 positive |
| T-21 | AttackEntity(A) 未取消 → 无 LivingAttack | `MATCHED`；LivingAttack=`NOT_OBSERVED`；不推断失败 |
| T-22 | AttackEntity(A) → LivingAttack(A,canceled) | LA=`CANCELED`；不 positive |
| T-23 | AttackEntity(A) → LivingHurt(A,canceled) | LH=`CANCELED`；不 positive |
| T-24 | AttackEntity(A) → LivingDamage(A,canceled) | LD=`CANCELED`；不 positive |
| T-25 | 同目标 BC 攻击与其他 Mod 同 source 交错 | 首链 matched；后续 `OVERLAP_AMBIGUOUS`；仍 `AMBIGUOUS_PLAYER_ATTACK` |
| T-26 | 不同目标 BC 与其他 Mod 伤害交错 | 按目标隔离；不证明来源独占 |
| T-27 | 新 Session 后旧 Session 事件迟到 | `LATE_AFTER_SEALED`；不修改旧/新 |
| T-28 | orderingKnown=false，完整正向链 | `MATCHED`；window incomplete；Phase05 不可最高可信 |
| T-29 | orderingKnown=false，无任何事件 | `NOT_OBSERVED`；`UNKNOWN` |
| T-30 | PartEntity parent 匹配但窗口不完整 | `PARTIAL_PART`；window incomplete；`UNKNOWN/PARTIAL` |
| T-31 | 64 目标后第 65 目标 | damageTargetOverflowCount++；`damageObservationTruncated=true` |
| H-32 | Handler 顺序未知 + 完整事件链 | 记录链；窗口不完整；只能日志/诊断；**禁止跑酷特殊动画** |
| H-33 | Handler 顺序未知 + 无任何攻击事件 | `NOT_OBSERVED`；`UNKNOWN`；不得当 MISS |
| H-34 | 同一目标多次 LivingHurtEvent | 每次独立记录；计数/取消保留；不求和 |
| H-35 | 同一目标先未取消、后取消事件 | 两条记录；aggregate=MIXED；取消不丢失；不 positive |
| H-36 | 同一目标超过 8 条事件 | 前 8 条保留；后续只增计数/truncated；不扩容 |
| H-37 | 普通攻击动画 vs 跑酷特殊动画候选 | 普通 BC 动画照常；特殊动画要求 Handler known；当前禁止 |

### 9.3 测试类

```text
CombatAttackSessionTest
CombatAttackTrackerTest
CombatBridgeEventTest
CombatPartTargetTest
CombatDamageAssociationTest
CombatAttackCapacityTest
CombatLifecycleTest
```

### 9.4 当前已有

```text
CombatAttackRequestTest 4 passed
CombatTargetDamageObservationTest 2 passed
BetterCombatAttackAdapterTest 3 passed
Total suite 76/76 passed
```

这些不覆盖 Session/Tracker/Bridge 事件链。

---

## 10. Phase 05 可信度矩阵

### 10.1 独立轴

```text
请求来源：CLIENT_REPORTED / SERVER_OBSERVED_REQUEST_BOUNDARY
目标解析：COMPLETE / PARTIAL_* / UNKNOWN
AttackEvent：NOT_OBSERVED / OBSERVED_UNCANCELLED / CANCELED / WINDOW_INCOMPLETE / ASSOCIATION_UNCERTAIN
DamageAssociation：MATCHED / ATTACK_CANCELED / NO_ATTACK_EVENT / COLLATERAL / LATE_AFTER_SEALED / OVERLAP_AMBIGUOUS / UNKNOWN
SourceProvenance：AMBIGUOUS_PLAYER_ATTACK / OTHER；betterCombatExclusiveProven=false
HandlerOrdering：UNKNOWN（当前生产）；KNOWN 不可达
HealthChange：UNVERIFIED
```

### 10.2 使用规则

#### 仅 HUD/日志

- 所有目标解析等级；
- 所有 AttackEvent 状态；
- 所有 DamageAssociation；
- 所有 source classification；
- HealthChange=UNVERIFIED。

#### 动画候选规则

必须区分两种动画：

1. **普通 Better Combat 攻击动画**  
   由 Better Combat / Player Animator 自己播放。  
   ParkourCombat 的 context 不控制、不重放、不替代该动画。

2. **跑酷特殊攻击动画**  
   依赖“当前请求与事件链”的关联，属于 Phase 05 的候选。  
   当前 `HandlerOrderingTrust=UNKNOWN`，不得触发。

事件链完整性与关联可靠性是两个问题：

- 完整链：观察到 AttackEntity/LivingAttack/LivingHurt/LivingDamage；
- 关联可靠：还需要 Handler 顺序已知、目标匹配、无 overlap、无 late、coverage 完整。

完整链不等于属于当前 Better Combat 请求。

| HandlerOrdering | 事件链 | 关联 | 普通 BC 动画 | 跑酷特殊动画 | 伤害/范围/目标数量 |
|---|---|---|---|---|---|
| UNKNOWN（当前） | 完整 | 观察到的 matched 候选 | BC 自行播放 | **禁止** | 禁止 |
| UNKNOWN（当前） | 无事件 | 无 | BC 自行播放 | 禁止 | 禁止 |
| UNKNOWN（当前） | 取消/部分/overlap | 任意 | BC 自行播放 | 禁止 | 禁止 |
| KNOWN（未来独立审查） | 完整 | matched + 完整 coverage + 无 overlap | BC 自行播放 | 仅可进入独立审查候选 | 仍禁止 |
| KNOWN（未来独立审查） | 任意不完整 | 任意 | BC 自行播放 | 禁止 | 禁止 |

当前生产环境：

```text
HandlerOrderingTrust = UNKNOWN
ParkourSpecialAnimationCandidate = false
```

因此：

- 正向事件只能用于日志/诊断；
- 不得触发依赖当前请求的跑酷特殊攻击动画；
- 不得用事件关联触发伤害强化、倍率、目标范围或目标数量；
- 不得为了 `orderingKnown=true` 覆盖未知 Handler；
- 未来若通过真实证据确认 Handler 身份，再单独审查更高可信度动画路径。

#### 不能用于伤害倍率/范围/目标数量

- 所有 amount；
- 所有 AMBIGUOUS_PLAYER_ATTACK；
- 所有 PARTIAL_*；
- 所有 ATTACK_CANCELED / NO_ATTACK_EVENT / OVERLAP_AMBIGUOUS / LATE_AFTER_SEALED；
- 所有 HealthChange=UNVERIFIED。

#### 必须 UNKNOWN

- AttackEvent NOT_OBSERVED；
- WINDOW_INCOMPLETE；
- ASSOCIATION_UNCERTAIN；
- DamageAssociation UNKNOWN；
- TargetResolution UNKNOWN；
- HandlerOrdering UNKNOWN 且无可确认正向链；
- sealed 后迟到；
- 停服/异常。

### 10.3 当前生产

```text
HandlerOrdering = UNKNOWN
SourceProvenance = AMBIGUOUS_PLAYER_ATTACK
betterCombatExclusiveProven = false
HealthChange = UNVERIFIED
```

因此：

- 当前没有最高可信度数据；
- 正向事件可记录，但不能解释为窗口完整；
- 无事件必须 UNKNOWN；
- 不得声称 Better Combat 独占来源；
- 不得声称实际生命值减少。

### 10.4 Handler 顺序未知的明确限制

```text
HandlerOrderingTrust = UNKNOWN（当前生产）
```

影响：

1. 不允许跑酷特殊攻击动画使用事件关联结果。
2. 普通 Better Combat 攻击动画仍由 Better Combat 播放，与本桥接无关。
3. 不允许用事件关联触发伤害强化、倍率、目标范围或目标数量。
4. 不得覆盖未知 Handler 来获得 `orderingKnown=true`。
5. 不得把 `AUDITED_BETTER_COMBAT_ORIGINAL` 写成已实现。
6. 正向事件与无事件的可信度不同：
   - 正向：可记录观察，但窗口不完整；
   - 无事件：必须 UNKNOWN，不能解释为未发生。
7. 未来只有真实证据确认 Handler 身份和顺序后，才能单独审查更高可信度路径。

---

## 11. 无法彻底解决的限制

1. Forge 事件没有 Better Combat attackId。
2. 同目标连续攻击无法绝对归属。
3. 其他 Mod 可使用相同 playerAttack source。
4. 不同目标也不能证明来源独占。
5. Handler 身份无法公开验证；AUDITED 分支不可达。
6. 停服期间原始 Handler 内部 execute 竞态。
7. isStopped 非 volatile。
8. 无服务端空间验证。
9. 无 setHealth 后置观测，无法证明生命减少。
10. 目标截断/未解析/PartEntity 不能恢复完整集合。
11. feint 无 C2S，不可观测。
12. sealed 后迟到事件只能诊断。

---

## 12. 人工实机测试

1. 普通剑攻击僵尸：完整事件链。
2. 空挥：rawCount=0。
3. 连续不同目标。
4. 连续相同目标。
5. 横扫多目标。
6. 末影龙 part/head。
7. 目标死亡/玩家退出/维度切换。
8. 无敌帧/创造模式/取消事件。
9. 其他 Mod 同 source 追加伤害。
10. 停服瞬间请求。
11. 两玩家并发。
12. Better Combat 缺失启动。
13. 集成服务器反复启动。
14. 专用服务器停止/重启。
15. 普通 Better Combat 动画与跑酷特殊动画候选的区分。
16. 同一目标多次事件/超过 8 条记录。

这些尚未执行，不能写 PASSED。

---

## 13. 验收标准

必须：

- C1：PartEntity 事件始终降低 coverage；parent match 不产生 COMPLETE。
- C2：所有直接玩家近战保留 AMBIGUOUS_PLAYER_ATTACK；betterCombatExclusiveProven 永远 false；其他 Mod 同 source 不升级。
- C3：匹配主链必须有未取消 AttackEntity；取消/无 AttackEntity 不成为主链；同一目标多次事件独立记录，取消不丢失；sealed 不修改。
- C4：不同目标不提升来源可信度；同目标/重叠/迟到保持 AMBIGUOUS/UNKNOWN；不重开 context。
- C5：damage observation targets <=64；第 65 个只增加 overflow 计数；每目标最多 8 条。
- Handler orderingUnknown：无事件只能 UNKNOWN；不得触发跑酷特殊攻击动画；普通 BC 动画保持独立；不得产生确定性 NO_ATTACK。
- 同一目标事件记录：每次事件独立；不超过 8 条；超出 truncated；不求和；不称实际生命值减少。
- Session/Tracker/Bridge 事件测试实现并通过。
- 集成/专用服务器启动；客户端目标/part 实机。

不可接受：

- 把 PartEntity 当普通完整目标；
- 把无 AttackEntity 的伤害当主链；
- 把其他 Mod 同 source 当确定 Better Combat；
- 把第一条匹配链当独占证明；
- 把 sealed context 重新打开；
- 声称实际生命值减少；
- 声称空间验证；
- 为测试放宽边界。

---

## 14. 最终状态

当前实现：**CONDITIONALLY_READY**  
本修复方案：**READY_FOR_CHATGPT_REVIEW**

本次只修改本修复方案文档；未修改 Java 源码、测试、Gradle、依赖或第三方 JAR；未创建 Git commit；未进入 Phase 05。
