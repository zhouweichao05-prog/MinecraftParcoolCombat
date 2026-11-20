# PHASE04_GATE2_7_REMEDIATION_PLAN.md

> Phase 04 Gate 2.7 — Gate 2.6 问题修复方案  
> 依据：`docs/PHASE04_GATE2_6_SOURCE_AUDIT.md`  
> 本文件只定义设计；不修改 Java、测试、Gradle、依赖或第三方 JAR。  
> 当前代码仍为 Gate 2.6 审计状态；本方案待 ChatGPT 审查批准后实施。

---

## 1. 基线

```text
项目：D:\MinecraftParcoolCombat
分支：master
HEAD：170ea5bc29c3660525431263d07c3b79a9094f69
已跟踪文件改动：无
Git commit：无
```

当前未解决：

- **C-01 CRITICAL**：PartEntity 可绕过 64 目标上限；
- **H-01 HIGH**：取消 AttackEntity 后 `validAttackTargets` 未移除；
- **H-02 HIGH/MEDIUM**：recent sealed context 没有时间 TTL；
- 其他 MEDIUM/LOW：PRIMARY 残留、canceled damage 旧集合、溢出计数语义、`AttackChain.valid`、`canceledAttackTargets` 暴露问题。

本方案目标：在不扩大 Phase 04 Gate 2 范围的前提下修复上述问题。

---

## 2. C-01：统一 64 目标容量保护

### 2.1 核心设计：统一 TargetState + TargetRegistry

新增内部结构：

```text
CombatTargetState
  UUID targetUuid
  TargetKind kind            // NORMAL, PART
  UUID parentUuid            // nullable; relation only
  boolean requested
  boolean observed
  int attackEntityCount
  int livingAttackCount
  int livingHurtCount
  int livingDamageCount
  int canceledCount
  ChainState chainState      // NONE, VALID, CANCELED, OVERLAP
  TargetEventAccumulator events // <=8
```

```text
CombatTargetRegistry
  LinkedHashMap<UUID, CombatTargetState> targets // max 64
  fixed overflow counters
```

**不再维护**独立的无界：

```text
observedPartUuids
observedPartParentUuids
attackChains
validAttackTargets
canceledAttackTargets
overlapTargets
```

这些状态全部由 `TargetState` 派生或作为其字段。Context 在 finish 时生成**有界不可变快照**。

### 2.2 64 的计数单位

**计数单位 = 一个已准入的 canonical target UUID。**

| 目标类型 | 占用预算 |
|---|---:|
| 普通实体 | 1 |
| PartEntity | 1 |
| 同一 UUID 重复出现 | 0（复用已有状态） |
| 请求 parent、只作为 part 关系 | 0（关系字段，不自动准入） |
| parent 本身被请求或直接观察 | 1（独立目标） |
| parent 不可得的 PartEntity | 1（part 自身；parent=null） |
| 死亡/未找到/未解析请求 | 0（只进入固定上限 resolution 列表） |

明确：

- 一个 parent 不会因为某个 part 出现而自动获得一个独立预算单位；
- 如果 parent 也是 requested/observed target，它自己占 1；
- PartEntity 的 parent UUID 只作为 `parentUuid` 关系保存，不合成新 UUID；
- 不允许把 part UUID 与 parent UUID 视为同一 UUID。

### 2.3 准入规则

统一入口：

```text
admitRequestedNormal(uuid)
admitRequestedPart(partUuid, parentUuid)
admitObservedNormal(uuid)
admitObservedPart(partUuid, parentUuid)
```

规则：

1. 已存在同一 UUID -> 复用，不消耗预算；
2. 新目标且 `targets.size() < 64` -> 创建 `TargetState`；
3. 新目标且 `targets.size() == 64`：
   - 不创建 TargetState；
   - 不创建 chain/list/map；
   - 增加固定计数器；
   - 设置覆盖不完整标志；
4. PartEntity 的新 UUID 同样走第 3 条；
5. parent 不自动准入；
6. 第 65 个 part 不能通过 parent 进入其他集合；
7. 已准入目标在后续事件中只更新其 TargetState，不新增目标预算。

### 2.4 超限时的固定计数器

不保存溢出 UUID，不创建集合。所有计数器使用 **long**，并采用饱和递增：

```java
long saturatingIncrement(long value) {
    return value == Long.MAX_VALUE ? Long.MAX_VALUE : value + 1L;
}
```

不执行 `++` 直接回绕；不做负数运算。

| 计数器 | 统计单位 | 增加条件 | 关系 |
|---|---|---|---|
| `targetBudgetOverflowCount` | 被拒绝的新 canonical target 准入尝试次数 | Registry 已满且传入新 UUID | 包含 part 超限 |
| `partTargetOverflowCount` | 被拒绝的新 PartEntity 准入尝试次数 | 同上，且 kind=PART | `targetBudgetOverflowCount` 的子集 |
| `overflowEventCount` | 未准入目标上的事件数 | 未准入 target 上收到任何事件 | 包含 canceled |
| `overflowCanceledEventCount` | 未准入目标上的 canceled 事件数 | 未准入 target 上收到 canceled | `overflowEventCount` 的子集 |
| `eventDetailOverflowCount` | 已准入目标超过 8 条的明细事件数 | 已有 TargetState 的第 9 条及以后事件 | 不是目标预算溢出的子集 |
| `eventDetailTruncatedTargetCount` | 至少发生一次明细截断的 TargetState 数 | 某 TargetState 第一次 truncated | 每目标最多 1 |

说明：

- 因为不保存溢出 UUID，重复的**未准入**目标尝试会再次增加 `targetBudgetOverflowCount`；
- 已准入目标的重复事件不增加 `targetBudgetOverflowCount`；
- `partTargetOverflowCount` 每次与 `targetBudgetOverflowCount` 同步增加，仍是其子集；
- `overflowCanceledEventCount` 每次与 `overflowEventCount` 同步增加，仍是其子集；
- `eventDetailOverflowCount` 和 `eventDetailTruncatedTargetCount` 独立于目标预算；
- 不使用一个 `eventObservationOverflowCount` 同时代表目标溢出和明细溢出；
- 不记录溢出目标 UUID、chain 或事件列表。

### 2.5 覆盖状态

新增 coverage：

```text
PARTIAL_TARGET_OVERFLOW
```

`finalCoverage()` 优先级：

```text
1. request.truncated() -> PARTIAL_TRUNCATED
2. targetBudgetOverflowCount > 0 -> PARTIAL_TARGET_OVERFLOW
3. partTargetOverflowCount > 0 / observed part / unmatched -> PARTIAL_PART
4. unresolved requested -> PARTIAL_UNRESOLVED
5. else COMPLETE
```

规则：

- 超限目标不得导致 `COMPLETE`；
- 第 65 个 PartEntity 即使没有 TargetState，也必须设置 `PARTIAL_TARGET_OVERFLOW` 或 `PARTIAL_PART`；
- `requestedTargetsComplete` 只在最终 coverage=`COMPLETE` 时为 true；
- finish 重新计算 coverage，禁止在事件中途把 COMPLETE 写死。

### 2.6 所有集合容量表

| 集合/结构 | 最大容量 | 说明 |
|---|---:|---|
| `TargetRegistry.targets` | 64 | canonical target UUID |
| 单目标 `events` | 8 | 每目标明细 |
| `requestedResolutions` | 64 | raw/captured resolution |
| `unresolvedRequestedEntityIds` | 64 | 请求未解析 |
| `validAttackTargets` | 64 | 从 TargetState 派生 |
| `canceledAttackTargets` | 64 | 从 TargetState 派生；不另建无界集合 |
| `observedPartUuids` | 64 | 从 TargetState 派生 |
| `observedPartParentUuids` | 64 | part 关系，不新增预算 |
| `targetEventObservations` | 64 | finish 快照 |
| recent contexts | 8 | 每玩家 |
| recent target lookup | 8 × 64 = 512 | 明确有界；TTL 窗口内 |

最终 context 中所有 UUID 集合都应由 TargetRegistry 快照生成，不能从事件流直接无限添加。

### 2.7 重复 PartEntity 与 parent

- 同一 PartEntity UUID 再次出现：复用 TargetState，不增加 64 预算；
- 同一 parent 的多个不同 PartEntity：每个 part UUID 各占 1，受 64 总预算；
- 同一 parent UUID 作为关系可重复引用，不占新预算；
- parent 不可得：part 占 1，`parentUuid=null`，`partUnmatched`，coverage partial；
- parent 本身是 requested target：parent 占 1；part 另占 1，除非 part UUID 已存在；
- 不创建 `parent -> synthetic UUID`；
- 不把 parent 当作 part 的替代 UUID。

### 2.8 finish 与 coverage

`finish` 从 TargetRegistry 生成：

```text
requestedTargetUuids
observedPartUuids
observedPartParentUuids
validAttackTargets
canceledAttackTargets
targetEventObservations
coverage
```

所有快照复制上限 64，不允许复制溢出目标。

### 2.9 TargetRegistry 唯一数据源

`CombatTargetRegistry` 是目标状态的唯一权威来源。

#### 真正存储

```text
CombatTargetRegistry.targets (LinkedHashMap<UUID, TargetState>, max 64)
TargetState.parentUuid (仅 part 关系)
TargetState.chainState
TargetState counts / per-target event list (max 8)
targetBudgetOverflowCount (long)
partTargetOverflowCount (long)
overflowEventCount (long)
overflowCanceledEventCount (long)
eventDetailOverflowCount (long)
eventDetailTruncatedTargetCount (long)
requestedResolutions (max 64)
unresolvedRequestedEntityIds (max 64)
recent contexts (per player, max 8)
server clock (long serverTick / lastRawServerTick)
```

#### 派生视图

以下不得作为独立可写集合保存；只能由 Registry 快照生成：

```text
requestedTargets
requestedPartUuids
requestedPartParentUuids
observedPartUuids
observedPartParentUuids
validAttackTargets
canceledAttackTargets
historicalAttackTargets count
validDamageTargets
canceledDamageTargetCount
targetEventObservations
targetCoverage
requestedTargetsComplete
```

规则：

1. 每个 canonical target UUID 最多一个 TargetState。
2. 所有目标准入必须通过 Registry 的统一 `admit...` 接口。
3. 禁止任何代码直接向 `validAttackTargets`、`observedPartUuids`、`attackChains`、`overlapTargets` 等集合写入 UUID。
4. `parentUuid` 只作为已准入 PartEntity 的关系字段；不自动准入 parent，也不占额外预算。
5. PartEntity 的 UUID 不能与 parent UUID 混同；禁止推断 `partUuid == parentUuid`。
6. 所有 coverage 计算从 Registry 的 requested/observed/part/overflow/chain 状态得出。
7. Context 只保存 Registry 的有界快照，不保存 Registry 内部可变引用。
8. 所有集合均有明确上限；溢出目标只进固定 long 计数器。
9. `requestedResolutions`/`unresolved` 也不能超过 64；重复 raw ID 不重复占目标预算。
10. 不允许外部维护可与 Registry 独立增长的 UUID 集合。

---

## 3. H-01：取消 AttackEntity 后状态污染

### 3.1 状态职责

`CombatTargetState` 的链状态：

```text
NONE
VALID
CANCELED
OVERLAP
```

职责：

- `attackEntityObserved`/历史计数：是否曾观察到 AttackEntity；
- `chainState`：当前有效/取消/重叠；
- `validAttackTargets`：仅 `chainState == VALID`；
- `canceledAttackTargets`：仅 `chainState == CANCELED`；
- `historicalAttackTargets`：通过 TargetState 的 `attackEntityCount > 0` 派生，不另建集合；
- `attacks` 事件记录：永久保留历史，不代表当前有效。

### 3.2 完整状态转换表

优先级：

```text
CANCELED > OVERLAP > VALID > NONE
```

CANCELED 是单调终态；任何后续 uncanceled 事件都不能覆盖取消事实。

| 当前状态 | 事件 | 下一状态 | 副作用 |
|---|---|---|---|
| NONE | 未取消 AttackEntity | VALID | attackEntityCount++；进入 valid 派生视图 |
| NONE | 取消 AttackEntity | CANCELED | attackEntityCount++；canceledCount++ |
| VALID | 未取消 AttackEntity | OVERLAP | attackEntityCount++；移出 valid；不重新建立有效链 |
| VALID | 取消 AttackEntity | CANCELED | attackEntityCount++；移出 valid；canceledCount++ |
| VALID | LivingAttack/Hurt/Damage 未取消 | VALID | 事件关联 matched；不改变链状态 |
| VALID | LivingAttack/Hurt/Damage 取消 | VALID | 事件记录 canceled；不 positive；valid 派生视图不变 |
| CANCELED | 任意后续 AttackEntity | CANCELED | 只增加历史/取消计数；不恢复 valid |
| CANCELED | 任意后续 Living 事件 | CANCELED | association=ATTACK_CANCELED；不 positive |
| OVERLAP | 取消 AttackEntity | CANCELED | 取消优先级高于 OVERLAP；canceledCount++ |
| OVERLAP | 未取消 AttackEntity | OVERLAP | 增加历史计数；不产生 VALID |
| OVERLAP | Living 未取消 | OVERLAP | 关联 ambiguous；不 positive |
| OVERLAP | Living 取消 | OVERLAP | 事件 canceled；不 positive |
| 任意 | sealed 后迟到事件 | 不修改 | 只诊断，不改变状态 |

补充：

- 历史事件记录永不删除；
- `attackEntityCount` 只增加，不因取消/重叠回退；
- `validAttackTargets` 只能由 `chainState == VALID` 派生；
- `canceledAttackTargets` 只能由 `chainState == CANCELED` 派生；
- 其他 TargetState 不受影响；
- 取消事实不能被 OVERLAP 覆盖；
- 已 sealed Session 不接受迟到事件。

### 3.3 取消转换

有效 AttackEntity 后：

```text
chainState = VALID
validAttackTargets.add
```

同一目标随后 canceled AttackEntity：

```text
chainState = CANCELED
validAttackTargets.remove
canceledAttackTargets.add
historical events remain
```

- 移除是强制步骤；
- 不能只替换 chain 而不更新集合；
- 取消不得删除历史事件记录；
- 不影响其他目标；
- 后续 LivingAttack/Hurt/Damage 关联为 `ATTACK_CANCELED`；
- 后续 uncanceled AttackEntity 不得把 CANCELED 改回 VALID；
- 新 Session 的取消事件不能通过旧 context 修改新 context（sealed/TTL 保护）。

### 3.4 取消的 Living 事件

`LivingAttack/Hurt/Damage` canceled：

- 记录 `canceledAtObservation=true`；
- `eventState=CANCELED`；
- `positiveAtObservation=false`；
- 不进入 `validDamageTargets`；
- 可进入 `canceledDamageTargetCount` / 历史事件记录；
- 不改变其他目标。

### 3.5 历史/有效/取消的 damage 目标

现有 `damageObservedTargets` 语义拆分：

```text
historicalDamageTargets   // 历史观察到任何伤害事件
validDamageTargets        // 未取消且 association=MATCHED
canceledDamageTargets     // 取消事件目标
```

Context 建议暴露：

```text
Set<UUID> validDamageTargets
int historicalDamageTargetCount
int canceledDamageTargetCount
```

可选：

- 如确实需要 canceled UUID，可在 TargetState 快照中通过 `targetEventObservations` 查看；
- 不额外复制完整 `canceledDamageTargets` 集合；
- 旧 `damageObservedTargets` 仅作为兼容别名，明确等价于 validDamageTargets，不得包含 canceled。

### 3.6 字段最小化

- 删除 `AttackChain.valid`（未使用），由 `chainState` 承担；
- `attackChains` 不再单独存在，由 `TargetState.chainState` 承担；
- `validAttackTargets`/`canceledAttackTargets` 可以由 TargetState 派生，不应再维护重复的无界集合；
- `canceledAttackTargets` 如保留 context 字段，必须由 64 上限快照生成，不能直接引用 Session 内部集合。

---

## 4. H-02：recent sealed context TTL 与统一服务器 Tick

### 4.1 统一时间基准

新增 Tracker 内部时钟：

```text
long serverTick              // 本 Tracker 的统一逻辑 tick，从 0 开始
long lastRawServerTick       // 上一次处理的 MinecraftServer#getTickCount 无符号值
long lastServerTickAtStart   // ServerStarted 初始化基准
boolean clockStarted
```

规则：

- 不使用 `Level#getGameTime()`；主世界/下界/末地的 Level 时间不能作为 Tracker 全局时间；
- 不使用 `System.currentTimeMillis`；
- 唯一时间来源是 Tracker 自己的 `long serverTick`；
- 每个实际服务器 tick 只递增一次；
- active Session 的 `serverTick` 和 sealed Context 的 `sealedTick` 都来自同一个 Tracker 时钟；
- `CombatAttackSession.serverTick` 从 `int` 改为 `long`；
- `CombatAttackContext.serverTick` 从 `int` 改为 `long`；
- `CombatAttackContext.sealedTick` 为 `long`；
- `attackTick` 保留为玩家 tick 诊断字段，不用于 TTL。

### 4.2 ServerTickEvent 注册与递增

当前注册：

```text
CombatBridge.java:173-179
@SubscribeEvent
onServerTick(TickEvent.ServerTickEvent event)
if (event.phase != TickEvent.Phase.END) return;
```

规则：

- 只处理 `TickEvent.Phase.END`；
- 不注册第二个 START handler；
- 不因为 START/END 两个阶段各调用一次而双递增；
- 在 Tracker 内部使用 `lastRawServerTick` 去重，不能假设回调天然每 tick 只执行一次。

每次 END：

```java
long raw = Integer.toUnsignedLong(server.getTickCount());
if (!clockStarted) {
    initialize(raw);
    return;
}
if (raw == lastRawServerTick) {
    return; // duplicate END callback for the same server tick
}
long delta = (raw - lastRawServerTick) & 0xFFFFFFFFL;
if (delta < 0x80000000L && delta <= MAX_FORWARD_TICK_JUMP) {
    serverTick = saturatingAdd(serverTick, delta);
    lastRawServerTick = raw;
} else {
    // backward jump or implausibly large forward jump
    resetForTickAnomaly(raw);
}
```

建议：

```text
MAX_FORWARD_TICK_JUMP = 200
```

- 普通 1 tick 前进：`delta=1`，`serverTick += 1`；
- 同一 raw tick 重复回调：不递增；
- unsigned int 自然回绕：`delta` 仍为 1，按前进处理；
- raw 后退/大幅跳跃：视为服务器重启或 tick 异常。

### 4.3 ServerStarted / ServerStopping

`ServerStartedEvent`：

```text
resetForServerStart(rawTickCount)
  clear all active and recent
  serverTick = 0
  lastRawServerTick = raw
  clockStarted = true
```

`ServerStoppingEvent`：

```text
clear all active/recent
clockStarted = false
lastRawServerTick = 0
```

不依赖旧 server 实例的 tick 值。

### 4.4 tick 回退/重启

以下情况：

- `delta >= 0x80000000L`；或
- `delta > MAX_FORWARD_TICK_JUMP`；或
- `ServerStartedEvent` 重置；

处理：

- 清空 active 和 recent；
- 记录 `TICK_REGRESSION` / `TICK_RESET`；
- 不将旧 sealed context 用于新匹配；
- `serverTick` 重置为 0（或 ServerStarted 的新基准）；
- `lastRawServerTick=raw`；
- 不把旧 `sealedTick` 与新 tick 比较。

### 4.5 TTL 边界

```text
RECENT_SEALED_TTL_TICKS = 40
```

严格定义：

```text
age = serverTick - sealedTick
age <= 40  -> 有效
age > 40   -> 过期
```

- 第 40 tick 仍有效；
- 第 41 tick 过期；
- `sealedTick < 0` 视为无有效时间戳；
- `serverTick < sealedTick` 视为异常，不匹配；
- 全部使用 long，不受 int 回绕影响。

### 4.6 sealedTick

`CombatAttackContext` 增加：

```text
long sealedTick
```

`finish` 签名改为：

```java
finish(CombatAttackEndReason reason, long finishTick)
```

- `CombatAttackTracker.finish` 使用 Tracker 当前 `serverTick`；
- `TTL_EXPIRED` 清理使用同一个 tick；
- test seam 显式传入 long tick；
- `sealedTick` 是 context 不可变字段；
- 不允许 Session 用 `player.tickCount` 或 `Level#getGameTime()` 自行生成 sealedTick。

### 4.7 recent 清理

`CombatAttackTracker.onServerTick` 每 tick：

1. 先按 4.2 更新 `serverTick`；
2. 遍历 recent context；
3. 移除 `sealedTick < 0` 的异常 context；
4. 移除 `serverTick < sealedTick` 的异常 context；
5. 移除 `serverTick - sealedTick > 40` 的 context；
6. 保持 recent 最大 8；
7. 清理空 PlayerState；
8. 不修改 active Session 的正常 TTL 规则。

`recentTargets(now)`：

- 只读取 `sealedTick >= 0`；
- 只读取 `now >= sealedTick`；
- 只读取 `now - sealedTick <= 40`；
- 不读取 active；
- 不等待新攻击请求；
- 返回集合上限 8×64=512；
- 不缓存无界集合。

### 4.8 active Session TTL

active Session 使用同一个 `serverTick`：

```text
age = serverTick - session.serverTick
age > 40 -> TTL_EXPIRED
```

`CombatAttackTracker.prepare` 不再使用 `player.getServer().getTickCount()` 直接作为 Session 时间；使用 Tracker 当前 `serverTick`。

### 4.9 同目标/不同目标/orderingUnknown

- 同目标连续攻击在 TTL 内：保留 recent，忽略迟到/重叠，保守 UNKNOWN；
- TTL 后：不再用旧 context 匹配；Forge 无 attackId，source 仍 ambiguous；
- 不同目标：不证明 Better Combat 独占；只是减少关联冲突；
- orderingUnknown：使用同一 TTL，不延长、不提升可信度；
- 迟到事件不得修改旧/新 context；
- TTL 是时间窗，不是来源证明。

### 4.10 cleanup 语义

- recent TTL 清理只处理 sealed context；
- active session 使用 active TTL=40；
- 玩家退出/死亡/维度/停服清空 active+recent；
- tick 回退清空 active+recent；
- 清理不能影响其他玩家；
- 所有清理从服务器线程执行。

---

## 5. 其他问题处理

### 5.1 PRIMARY_PLAYER_ATTACK 残留

方案：

- 从 `CombatDamageSourceClassification` **删除** `PRIMARY_PLAYER_ATTACK`；
- 保留 `AMBIGUOUS_PLAYER_ATTACK`、`OTHER`；
- 更新旧测试 `CombatTargetDamageObservationTest` 中引用 PRIMARY 的测试数据；
- 迁移检查：生产代码不得再引用 PRIMARY；
- 不重新引入独占推断。

如果删除影响过大，最低方案是 `@Deprecated` + 明确注释，但最终推荐删除。

### 5.2 canceled damage 观察目标

- 历史记录保留；
- `positive` 仅在 uncanceled + matched + LIVING_DAMAGE >0；
- canceled damage 不进入 valid damage 集合；
- `historicalDamageTargetCount` 和 `canceledDamageTargetCount` 分开；
- 不把 canceled 事件当作实际生命减少。

### 5.3 溢出计数语义

所有计数器使用 long，并使用 `saturatingIncrement`；不得 `++` 回绕为负数。

| 计数器 | 统计单位 | 增加条件 | 关系 |
|---|---|---|---|
| `targetBudgetOverflowCount` | 被拒绝的新 canonical target 准入尝试次数 | Registry 已满且新 UUID | 包含 part 超限 |
| `partTargetOverflowCount` | 被拒绝的新 PartEntity 准入尝试次数 | 同上且 kind=PART | target 计数子集 |
| `overflowEventCount` | 未准入目标上的事件数 | 未准入 target 收到事件 | 包含 canceled |
| `overflowCanceledEventCount` | 未准入目标上的 canceled 事件数 | 未准入 target 收到 canceled | overflowEvent 子集 |
| `eventDetailOverflowCount` | 已准入目标第 9 条及以后事件数 | 已有 TargetState 明细溢出 | 不是目标预算子集 |
| `eventDetailTruncatedTargetCount` | 发生明细截断的目标数 | 某 TargetState 第一次 truncated | 每目标最多 1 |

- 未准入目标重复出现会重复增加 target 计数，因为不保存 UUID；
- 已准入目标重复事件不增加 target 计数；
- 计数器达到 `Long.MAX_VALUE` 后保持 `Long.MAX_VALUE`，不回绕、不变负；
- 不使用一个 `eventObservationOverflowCount` 同时代表目标溢出和明细溢出。

### 5.4 AttackChain.valid

删除：

- `TargetState.chainState` 已完整表达 NONE/VALID/CANCELED/OVERLAP；
- `valid` 字段没有生产读取；
- 删除后减少状态不一致。

### 5.5 canceledAttackTargets

- Session 内部由 TargetState 派生；
- Context 默认只暴露 `canceledAttackTargetCount`；
- 如需要 UUID，可从 `targetEventObservations` 的 canceled 记录查看；
- 不额外复制完整 canceled UUID 集合；
- 不为公开字段复制大量集合。

---

## 6. 测试设计

> 当前 100 项测试为 PASSED；以下新增测试均 DESIGNED，尚未实现。  
> 不得把 Session test seam 称为真实 Forge 测试。

### 6.1 C-01 测试

| ID | 场景 | 预期 |
|---|---|---|
| C01-T01 | 64 个普通目标 | 64 个 TargetState；无 overflow |
| C01-T02 | 第 65 个普通目标 | 不创建 state；targetBudgetOverflowCount=1；coverage partial overflow |
| C01-T03 | PartEntity 达到 64 | 64 个 part state；关系完整 |
| C01-T04 | 第 65 个 PartEntity | 不创建 state/chain；partTargetOverflowCount=1；coverage partial |
| C01-T05 | 普通实体 + PartEntity 混合至 64 | 共享 64；无额外 state |
| C01-T06 | 重复 PartEntity UUID | 不消耗第二次预算；事件进入已有 state |
| C01-T07 | 同一 parent 多个 PartEntity | 每个 part UUID 最多 1；总计 64 |
| C01-T08 | parent UUID 不可得 | part 占 1；parent=null；unmatched；partial |
| C01-T09 | 容量满后重复已有目标 | 允许更新历史/事件；不新增预算 |
| C01-T10 | 所有集合边界 | targets<=64；events<=8；recent contexts<=8 |
| C01-T11 | finish coverage | 超限/part 不得升级 COMPLETE；requestedTargetsComplete=false |

### 6.2 H-01 测试

| ID | 场景 | 预期 |
|---|---|---|
| H01-T01 | 有效 AttackEntity 后取消 | chain=CANCELED；valid 集合移除；历史保留 |
| H01-T02 | 取消后 LivingAttack | ATTACK_CANCELED；不 positive |
| H01-T03 | 取消后 LivingHurt | ATTACK_CANCELED；不 positive |
| H01-T04 | 取消后 LivingDamage | ATTACK_CANCELED；不 positive |
| H01-T05 | 目标 A 取消，B 正常 | B 不受影响；A 不有效 |
| H01-T06 | 取消后新 Session | 旧取消不修改新 context |
| H01-T07 | 旧 Session 取消事件迟到 | sealed/TTL 拒绝修改 |

### 6.3 H-02 测试

| ID | 场景 | 预期 |
|---|---|---|
| H02-T01 | sealed context TTL 内 | 参与 recentTargets |
| H02-T02 | 超过 40 ticks | 不参与；可清理 |
| H02-T03 | 无新攻击请求，持续 tick | 到 TTL 自动清理 |
| H02-T04 | 8 个 recent context | 容量上限 8 |
| H02-T05 | 同目标连续攻击 TTL 内 | 保守处理；不升级独占 |
| H02-T06 | 不同目标连续攻击 | 不证明独占；可独立匹配 |
| H02-T07 | 玩家退出 | active+recent 清理 |
| H02-T08 | 玩家死亡/维度切换 | active+recent 清理 |
| H02-T09 | 服务器停止 | 全部清理 |
| H02-T10 | tick 回退/重启 | 清空旧状态；不使用旧 tick 匹配 |

### 6.4 其他测试

| ID | 场景 | 预期 |
|---|---|---|
| O-T01 | PRIMARY 不再生产 | 生产代码无 PRIMARY；旧测试数据迁移 |
| O-T02 | canceled damage | 不进入 validDamageTargets；不 positive |
| O-T03 | 溢出计数 | 各计数器独立正确 |
| O-T04 | chainState 转换 | NONE→VALID→CANCELED；不得回 VALID |

### 6.5 2.7.1 必补测试

| ID | 场景 | 预期 |
|---|---|---|
| C01-T12 | Registry 外部无可增长目标集合 | 所有目标快照均来自 Registry；外部集合不可写增长 |
| C01-T13 | 同一 canonical UUID 多次准入 | 只有一个 TargetState；预算不重复消耗 |
| H01-T08 | OVERLAP 后收到 canceled AttackEntity | 状态变 CANCELED；取消优先，不被 OVERLAP 覆盖 |
| H01-T09 | CANCELED 后收到 uncanceled AttackEntity | 仍 CANCELED；不回到 VALID |
| H02-T11 | START/END 两阶段 | 仅 END 更新；同一 raw tick 重复 END 不双递增 |
| H02-T12 | 跨维度切换 | Tracker serverTick 连续；不使用 Level 时间 |
| H02-T13 | TTL 第 40 tick | age=40 仍有效 |
| H02-T14 | TTL 第 41 tick | age=41 过期 |
| H02-T15 | raw tick / long 边界 | unsigned raw 回绕不导致 long 负值；sealedTick 为 long |
| O-T05 | 各溢出计数器单位 | 六个计数器分别正确；子集关系正确 |
| O-T06 | Long.MAX_VALUE 饱和 | 计数器不回绕、不变负 |

所有测试状态：

```text
DESIGNED（本方案）
未实现
未执行
```

现有 100 项测试为 PASSED，但不能替代新增测试。

---

## 7. 性能与内存影响

- `TargetRegistry` 最大 64，查找 O(1)；
- 每目标事件列表最大 8；
- recent contexts 最大 8，每 context 目标快照最大 64；
- recentTargets 最大 512（8×64），只在请求/tick 清理时构建；
- 每 tick 只扫描最多 8 个 recent context，不做实体扫描；
- 不新增网络包、Mixin、世界扫描或高频任务；
- 溢出只更新固定计数器；
- 不为溢出目标保存 UUID/集合；
- Context 快照复制最多 64 个目标；
- 预计内存上限：
  - 64 target states；
  - 8 events/target；
  - 8 recent contexts；
  - 512 recent target lookup entries。

---

## 8. 修改文件清单（计划）

### 新增

```text
CombatTargetState.java
CombatTargetRegistry.java
```

### 修改

```text
CombatAttackSession.java          // int serverTick -> long；TargetState 派生
CombatAttackTracker.java          // long serverTick/raw tick clock；sealedTick；recent TTL
CombatAttackContext.java          // long serverTick/sealedTick；派生快照
CombatAttackSessionRemediationTest.java
CombatTargetCoverage.java
CombatDamageSourceClassification.java
CombatTargetDamageObservationTest.java  // 若删除 PRIMARY
```

### 可能修改

```text
CombatBridge.java                 // finish 传 long sealedTick；END-only tick 调用
CombatTargetEventObservation.java // 无需结构变化时可不动
CombatAttackTrackerTest.java      // 若新增 Tracker 时钟测试
```

### 不修改

```text
BetterCombatCompat.java
BetterCombatAttackAdapter.java
ParCool / Better Combat JAR
Gradle / 依赖
Mixin / 网络包
```

---

## 9. 未解决限制

- Forge 事件没有 attackId；
- 同目标连续攻击无法绝对归属；
- 其他 Mod 同 source 无法绝对区分；
- Handler orderingKnown 仍 false；
- 停服原始 Handler 竞态；
- `isStopped()` 非 volatile；
- 无服务端 Hitbox/距离/视线验证；
- 无 setHealth 后置观测；
- recent TTL 是经验值，不是协议级证明；
- 不同目标也不证明 Better Combat 独占；
- 不能进入 Phase 05。

---

## 10. 实施风险

| 风险 | 等级 | 缓解 |
|---|---|---|
| TargetRegistry 迁移遗漏旧集合 | HIGH | 删除旧集合并用编译错误驱动迁移 |
| canceled 状态未同步 valid 集合 | HIGH | H-01 测试 + 状态机唯一入口 |
| TTL 与 tick 回退处理错误 | HIGH | lastTick/reset 测试 |
| 测试 seam 被误当真实 Forge | MEDIUM | 报告明确标记 DESIGNED/INTEGRATION |
| 删除 PRIMARY 破坏旧测试 | LOW | 更新测试数据，不保留生产用途 |
| recent 512 查找内存 | LOW | 上限明确；TTL 清理 |

---

## 11. 最终状态

```text
READY_FOR_CHATGPT_FINAL_REVIEW
```

当前代码未修复；本文件只定义修复方案。未修改任何源码、测试、Gradle、依赖或第三方 JAR；未创建 Git commit；未进入 Phase 05。
